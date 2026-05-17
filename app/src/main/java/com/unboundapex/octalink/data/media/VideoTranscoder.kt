package com.unboundapex.octalink.data.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 영상 720p 재인코딩 — 업로드 전 단계. Media3 Transformer (MediaCodec 하드웨어 가속) 기반.
 *
 * 효과:
 *  - 해상도: 긴 변 720p 로 다운스케일 (이미 720p 이하면 [Presentation.createForHeight] 가 업스케일 안 함).
 *  - 비디오: H.264, Transformer 기본 비트레이트 (~720p 4Mbps 권장값).
 *  - 오디오: AAC.
 *  - 컨테이너: mp4.
 *
 * 효과 결과: phone-native 1080p 영상이 통상 50-70% 사이즈 감축. 90초 클립 기준 ~5-10초 처리 (mid-range 디바이스).
 *
 * 실패 시 [ExportException] throw — caller([VideoUploader]) 가 원본 그대로 fallback 진행 결정.
 * Transformer API 는 main thread 호출 강제 — 내부에서 [Dispatchers.Main] 으로 switch.
 *
 * Media3 1.4.x 의 Transformer / Effects / Presentation 은 `@UnstableApi` — Google 이 API 안정성 보증 안 함.
 * 후속 메이저 업데이트 시 signature 변경 가능성 있음. 그 때 마이그레이션 가이드 따라 갱신.
 */
@UnstableApi
object VideoTranscoder {
    private const val TARGET_HEIGHT = 720
    private const val TAG = "OctaLink.VideoTranscoder"

    /**
     * @return 재인코딩된 mp4 파일의 `file://` URI. caller 가 업로드 후 캐시 cleanup 책임.
     * @throws ExportException 코덱 초기화 실패 / 형식 미지원 / 디스크 부족 등.
     */
    suspend fun transcodeTo720p(context: Context, input: Uri): Uri = withContext(Dispatchers.Main) {
        val outputDir = File(context.cacheDir, "post_uploads").apply { mkdirs() }
        val outputFile = File(outputDir, "transcoded_${UUID.randomUUID()}.mp4")

        suspendCancellableCoroutine { cont ->
            val mediaItem = MediaItem.fromUri(input)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(
                    Effects(
                        /* audioProcessors = */ emptyList(),
                        /* videoEffects = */ listOf(Presentation.createForHeight(TARGET_HEIGHT)),
                    )
                )
                .build()

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) {
                            android.util.Log.i(
                                TAG,
                                "transcode 완료: ${outputFile.length() / 1024}KB, duration=${exportResult.durationMs}ms",
                            )
                            cont.resume(Uri.fromFile(outputFile))
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        android.util.Log.e(TAG, "transcode 실패: ${exportException.message}", exportException)
                        outputFile.delete()
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()

            try {
                transformer.start(editedMediaItem, outputFile.absolutePath)
            } catch (e: Throwable) {
                android.util.Log.e(TAG, "transcode 시작 실패", e)
                outputFile.delete()
                if (cont.isActive) cont.resumeWithException(e)
            }

            cont.invokeOnCancellation {
                runCatching { transformer.cancel() }
                outputFile.delete()
            }
        }
    }
}
