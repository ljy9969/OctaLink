package com.unboundapex.octalink.data.shadowcoach

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * MediaPipe PoseLandmarker(온디바이스 33관절 추정) LIVE_STREAM 래퍼.
 *
 * CameraX [ImageProxy] 스트림을 받아 비동기 추론 → 결과를 [PoseFrame] 으로 변환해 [onResult] 콜백.
 * GPU delegate 우선, 실패 시 CPU 폴백. 모델은 `assets/pose_landmarker_lite.task`.
 *
 * 좌표계: 결과 landmark 는 **분석 비트맵(회전 보정 후)** 기준 normalized [0,1]. 전면 카메라는
 * 미리보기가 좌우 반전이라 오버레이 단계에서 x 를 1-x 로 뒤집어 정합 ([mirrorX] 플래그 전달).
 */
class PoseLandmarkerHelper(
    private val context: Context,
    private val onResult: (PoseFrame) -> Unit,
    private val onError: (String) -> Unit = {},
) {
    private var landmarker: PoseLandmarker? = null
    @Volatile private var closed = false

    fun setup() {
        try {
            createWith(Delegate.GPU)
        } catch (e: Exception) {
            Log.w(TAG, "GPU delegate 실패 → CPU 폴백", e)
            runCatching { createWith(Delegate.CPU) }
                .onFailure {
                    Log.e(TAG, "PoseLandmarker 초기화 실패", it)
                    onError("자세 인식 엔진을 시작하지 못했어요. (${it.message})")
                }
        }
    }

    private fun createWith(delegate: Delegate) {
        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(delegate)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, _ -> handleResult(result) }
            .setErrorListener { e -> Log.e(TAG, "추론 에러", e) }
            .build()
        landmarker = PoseLandmarker.createFromOptions(context, options)
    }

    /**
     * CameraX 프레임 1장 분석 큐잉. [imageProxy] 는 RGBA_8888 출력 가정.
     * 호출자는 분석 직후 반드시 [ImageProxy.close] 할 것 (이 함수가 닫음).
     */
    fun analyze(imageProxy: ImageProxy) {
        val lm = landmarker
        if (lm == null || closed) { imageProxy.close(); return }
        try {
            val bitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees
            val rotated = if (rotation != 0) rotateBitmap(bitmap, rotation) else bitmap
            val mpImage = BitmapImageBuilder(rotated).build()
            lm.detectAsync(mpImage, imageProxy.imageInfo.timestamp / 1_000_000) // ns → ms
        } catch (e: Exception) {
            Log.w(TAG, "프레임 분석 실패(스킵)", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun handleResult(result: PoseLandmarkerResult) {
        if (closed) return
        val poses = result.landmarks()
        val tsMs = result.timestampMs()
        if (poses.isEmpty()) {
            onResult(PoseFrame(points = emptyList(), timestampMs = tsMs))
            return
        }
        val pts = poses[0].map { lm ->
            PosePoint(
                x = lm.x(),
                y = lm.y(),
                z = lm.z(),
                visibility = lm.visibility().orElse(0f),
            )
        }
        // 3D world landmarks(미터, 골반 원점) — 깊이 기반 분석용. 없으면 빈 리스트.
        val worldPts = result.worldLandmarks().getOrNull(0)?.map { lm ->
            PosePoint(
                x = lm.x(),
                y = lm.y(),
                z = lm.z(),
                visibility = lm.visibility().orElse(0f),
            )
        } ?: emptyList()
        onResult(PoseFrame(points = pts, timestampMs = tsMs, world = worldPts))
    }

    fun close() {
        closed = true
        runCatching { landmarker?.close() }
        landmarker = null
    }

    private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    companion object {
        private const val TAG = "ShadowCoach.Pose"
        const val MODEL_ASSET = "pose_landmarker_lite.task"
    }
}
