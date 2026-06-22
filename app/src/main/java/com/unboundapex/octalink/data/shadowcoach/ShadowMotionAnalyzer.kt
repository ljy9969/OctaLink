package com.unboundapex.octalink.data.shadowcoach

/**
 * 한 프레임 분석 결과 — ViewModel 이 세션에 누적.
 *
 * @param posed 이 프레임에서 사람 포즈가 잡혔는지.
 * @param newReps 직전 프레임 이후 완료된 잽(펀치) 횟수 (보통 0 또는 1).
 * @param activeCues 이 프레임에서 위반 중인 자세 항목 (실시간 코칭 칩 + 표본화).
 * @param transientPoorExtension 이번에 완료된 rep 이 신전 부족이었는지 (rep 단위 1회성).
 */
data class ShadowFrameResult(
    val frame: PoseFrame,
    val posed: Boolean,
    val newReps: Int = 0,
    val activeCues: Set<PostureCheck> = emptySet(),
    val transientPoorExtension: Boolean = false,
)

/**
 * 규칙 기반 쉐도우 동작/자세 분석기. 온디바이스 [PoseFrame] 스트림을 받아 잽 카운트 + 자세 체크.
 *
 * **MVP 단순화**: 2D 관절만으로 좌/우 팔의 신전→수축 사이클을 "잽"으로 카운트 (스탠스 판별 없이
 * 양팔 합산). 스트레이트/훅 구분, 깊이(z) 정밀 판정은 Phase 2+.
 *
 * 모든 임계값은 [Thresholds] 상수 — **실기기 테스트로 튜닝 필요** (체형·카메라 거리·각도 영향).
 * 인스턴스는 한 세션 동안 상태를 유지 (rep 상태머신). 새 세션마다 새로 생성하거나 [reset].
 */
class ShadowMotionAnalyzer {

    object Thresholds {
        /** 팔이 "접힘(가드)" 으로 보는 팔꿈치 각도 상한(도). */
        const val ELBOW_FLEXED_MAX = 100f
        /** 팔이 "펴짐(펀치 정점)" 으로 보는 팔꿈치 각도 하한(도). */
        const val ELBOW_EXTENDED_MIN = 150f
        /** "좋은 신전" 으로 보는 정점 각도 — 미만이면 신전 부족 rep. */
        const val ELBOW_GOOD_EXTENSION = 162f
        /** 한 rep(펴짐→다시 접힘) 최대 허용 시간(ms). 초과 시 펀치 아닌 동작으로 간주. */
        const val REP_MAX_MS = 1400L
        /** 가드 다운 판정: 양 손목이 어깨선보다 이만큼(정규화 y) 아래일 때. */
        const val GUARD_DOWN_MARGIN = 0.06f
        /** 턱 들림 판정: 코가 귀선보다 이만큼(정규화 y) 위일 때 (위를 봄). */
        const val CHIN_UP_MARGIN = 0.04f
        /** 관절 신뢰도 — 미만이면 해당 판정 스킵. */
        const val MIN_VIS = 0.5f
    }

    /** 한 팔의 펀치 상태머신. */
    private class ArmState {
        var reachedExtension = false
        var peakAngle = 0f
        var extendStartMs = 0L
    }

    private val left = ArmState()
    private val right = ArmState()

    fun reset() {
        listOf(left, right).forEach {
            it.reachedExtension = false; it.peakAngle = 0f; it.extendStartMs = 0L
        }
    }

    fun process(frame: PoseFrame): ShadowFrameResult {
        if (frame.points.isEmpty()) {
            return ShadowFrameResult(frame = frame, posed = false)
        }
        var reps = 0
        var poorExt = false

        // 좌/우 팔 rep 판정.
        val l = updateArm(
            left, frame,
            shoulder = PoseLandmarks.LEFT_SHOULDER,
            elbow = PoseLandmarks.LEFT_ELBOW,
            wrist = PoseLandmarks.LEFT_WRIST,
        )
        val r = updateArm(
            right, frame,
            shoulder = PoseLandmarks.RIGHT_SHOULDER,
            elbow = PoseLandmarks.RIGHT_ELBOW,
            wrist = PoseLandmarks.RIGHT_WRIST,
        )
        reps += l.first + r.first
        if (l.second || r.second) poorExt = true

        // 자세 체크 (프레임 단위).
        val cues = mutableSetOf<PostureCheck>()
        if (isGuardDown(frame)) cues += PostureCheck.GUARD_DOWN
        if (isChinUp(frame)) cues += PostureCheck.CHIN_UP

        return ShadowFrameResult(
            frame = frame,
            posed = true,
            newReps = reps,
            activeCues = cues,
            transientPoorExtension = poorExt,
        )
    }

    /** @return (완료된 rep 수, 그 rep 이 신전부족이었는지). */
    private fun updateArm(
        s: ArmState,
        frame: PoseFrame,
        shoulder: Int,
        elbow: Int,
        wrist: Int,
    ): Pair<Int, Boolean> {
        // 신뢰도 부족하면 상태 유지(판정 보류).
        val vw = frame.point(wrist)?.visibility ?: 0f
        val ve = frame.point(elbow)?.visibility ?: 0f
        if (vw < Thresholds.MIN_VIS || ve < Thresholds.MIN_VIS) return 0 to false
        val angle = frame.angleDeg(shoulder, elbow, wrist) ?: return 0 to false

        if (!s.reachedExtension) {
            // 가드(접힘) 상태에서 펴짐 정점에 도달하면 extend 시작 마킹.
            if (angle >= Thresholds.ELBOW_EXTENDED_MIN) {
                s.reachedExtension = true
                s.peakAngle = angle
                s.extendStartMs = frame.timestampMs
            }
            return 0 to false
        }

        // 펴진 상태 — 정점 갱신.
        if (angle > s.peakAngle) s.peakAngle = angle

        // 다시 접히면 rep 완료.
        if (angle <= Thresholds.ELBOW_FLEXED_MAX) {
            val dt = frame.timestampMs - s.extendStartMs
            val peak = s.peakAngle
            // 상태 리셋.
            s.reachedExtension = false
            s.peakAngle = 0f
            return if (dt in 1..Thresholds.REP_MAX_MS) {
                1 to (peak < Thresholds.ELBOW_GOOD_EXTENSION)
            } else {
                // 너무 느린 동작 — 펀치로 안 셈.
                0 to false
            }
        }
        return 0 to false
    }

    private fun isGuardDown(frame: PoseFrame): Boolean {
        val ls = frame.point(PoseLandmarks.LEFT_SHOULDER) ?: return false
        val rs = frame.point(PoseLandmarks.RIGHT_SHOULDER) ?: return false
        val lw = frame.point(PoseLandmarks.LEFT_WRIST) ?: return false
        val rw = frame.point(PoseLandmarks.RIGHT_WRIST) ?: return false
        if (minOf(ls.visibility, rs.visibility, lw.visibility, rw.visibility) < Thresholds.MIN_VIS) return false
        val shoulderY = (ls.y + rs.y) / 2f
        // y 는 아래로 증가 — 손목 y 가 어깨 y 보다 margin 이상 크면(아래) 가드 내려감.
        val leftDown = lw.y > shoulderY + Thresholds.GUARD_DOWN_MARGIN
        val rightDown = rw.y > shoulderY + Thresholds.GUARD_DOWN_MARGIN
        return leftDown && rightDown
    }

    private fun isChinUp(frame: PoseFrame): Boolean {
        val nose = frame.point(PoseLandmarks.NOSE) ?: return false
        val le = frame.point(PoseLandmarks.LEFT_EAR) ?: return false
        val re = frame.point(PoseLandmarks.RIGHT_EAR) ?: return false
        if (minOf(nose.visibility, le.visibility, re.visibility) < Thresholds.MIN_VIS) return false
        val earY = (le.y + re.y) / 2f
        // 위를 보면 코가 귀선보다 위(y 작음)로 올라감.
        return (earY - nose.y) > Thresholds.CHIN_UP_MARGIN
    }
}
