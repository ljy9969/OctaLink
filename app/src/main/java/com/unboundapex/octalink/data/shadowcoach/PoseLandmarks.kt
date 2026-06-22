package com.unboundapex.octalink.data.shadowcoach

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 한 관절 점. MediaPipe PoseLandmarker 의 normalized landmark (x,y ∈ [0,1], 화면 비율 좌표) 매핑.
 * [z] 는 카메라 기준 상대 깊이(작을수록 가까움, 단위 불명확 — 보조 신호로만), [visibility] 0~1 신뢰도.
 */
data class PosePoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 0f,
)

/**
 * MediaPipe Pose 의 33개 관절 인덱스. PoseLandmarker 출력 배열 인덱스와 1:1.
 * 휴리스틱 엔진이 이 상수로 필요한 관절만 꺼내 각도·속도를 계산.
 *
 * 참고: <https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker>
 */
object PoseLandmarks {
    const val NOSE = 0
    const val LEFT_EYE_INNER = 1
    const val LEFT_EYE = 2
    const val LEFT_EYE_OUTER = 3
    const val RIGHT_EYE_INNER = 4
    const val RIGHT_EYE = 5
    const val RIGHT_EYE_OUTER = 6
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val MOUTH_LEFT = 9
    const val MOUTH_RIGHT = 10
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_PINKY = 17
    const val RIGHT_PINKY = 18
    const val LEFT_INDEX = 19
    const val RIGHT_INDEX = 20
    const val LEFT_THUMB = 21
    const val RIGHT_THUMB = 22
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32

    const val COUNT = 33

    /**
     * 사람 스켈레톤 연결선 (오버레이 그리기용) — (시작 인덱스, 끝 인덱스) 쌍.
     * 얼굴 세부점은 생략하고 몸통·팔·다리 위주.
     */
    val CONNECTIONS: List<Pair<Int, Int>> = listOf(
        // 팔
        LEFT_SHOULDER to LEFT_ELBOW, LEFT_ELBOW to LEFT_WRIST,
        RIGHT_SHOULDER to RIGHT_ELBOW, RIGHT_ELBOW to RIGHT_WRIST,
        // 어깨/골반 몸통
        LEFT_SHOULDER to RIGHT_SHOULDER,
        LEFT_SHOULDER to LEFT_HIP, RIGHT_SHOULDER to RIGHT_HIP,
        LEFT_HIP to RIGHT_HIP,
        // 다리
        LEFT_HIP to LEFT_KNEE, LEFT_KNEE to LEFT_ANKLE,
        RIGHT_HIP to RIGHT_KNEE, RIGHT_KNEE to RIGHT_ANKLE,
    )
}

/**
 * 한 프레임의 포즈 — 33개 점. 휴리스틱 계산용 기하 헬퍼 포함.
 * 인덱스 접근이 범위를 벗어나거나 점이 없으면 null 안전.
 */
data class PoseFrame(
    val points: List<PosePoint>,
    /** 프레임 캡처 시각 (ms, monotonic) — 속도 계산용. */
    val timestampMs: Long,
) {
    fun point(index: Int): PosePoint? = points.getOrNull(index)

    /**
     * 세 점 a-b-c 가 이루는 각도(도). b 가 꼭짓점. 팔 신전각(어깨-팔꿈치-손목) 등에 사용.
     * 점이 없으면 null.
     */
    fun angleDeg(a: Int, b: Int, c: Int): Float? {
        val pa = point(a) ?: return null
        val pb = point(b) ?: return null
        val pc = point(c) ?: return null
        val v1x = pa.x - pb.x; val v1y = pa.y - pb.y
        val v2x = pc.x - pb.x; val v2y = pc.y - pb.y
        val dot = v1x * v2x + v1y * v2y
        val mag = (hypot(v1x, v1y) * hypot(v2x, v2y))
        if (mag < 1e-6f) return null
        val cos = (dot / mag).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }

    /** 두 점 사이 거리 (normalized 좌표 기준). */
    fun distance(a: Int, b: Int): Float? {
        val pa = point(a) ?: return null
        val pb = point(b) ?: return null
        return hypot(pa.x - pb.x, pa.y - pb.y)
    }

    /** 어깨 폭 — 거리 정규화 기준 척도 (사용자/카메라 거리 보정용). */
    fun shoulderWidth(): Float? = distance(PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER)

    /** 두 점의 y 차이 (정규화). 화면 좌표라 y 가 아래로 증가 — 양수면 a 가 b 보다 위. */
    fun yDelta(upper: Int, lower: Int): Float? {
        val pu = point(upper) ?: return null
        val pl = point(lower) ?: return null
        return pl.y - pu.y
    }

    companion object {
        /** 신뢰도 임계값 — 이 미만 visibility 점은 계산에서 제외 권장. */
        const val MIN_VISIBILITY = 0.5f
    }
}

/** 정규화 좌표 거리 헬퍼 (프레임 밖에서도 사용). */
internal fun dist(a: PosePoint, b: PosePoint): Float = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

internal fun absF(v: Float): Float = abs(v)
