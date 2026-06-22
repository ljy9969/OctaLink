package com.unboundapex.octalink.data.shadowcoach

import kotlin.math.abs
import kotlin.math.hypot

/**
 * 한 프레임 분석 결과 — ViewModel 이 세션에 누적.
 *
 * @param posed 이 프레임에서 사람 포즈가 잡혔는지.
 * @param newStrikes 직전 프레임 이후 완료된 펀치들 (종류별). 보통 0~2개(양팔 동시 완료 드묾).
 * @param activeCues 이 프레임에서 위반 중인 자세 항목 (실시간 코칭 + 표본화).
 * @param poorExtensionStrikes 이번 프레임에 완료된 스트레이트 중 신전 부족이었던 수.
 */
data class ShadowFrameResult(
    val frame: PoseFrame,
    val posed: Boolean,
    val newStrikes: List<Technique> = emptyList(),
    val activeCues: Set<PostureCheck> = emptySet(),
    val poorExtensionStrikes: Int = 0,
)

/**
 * 규칙 기반 쉐도우 동작/자세 분석기.
 *
 * **펀치 검출(궤적 기반)**: 팔별로 손목이 가드(기준) 위치에서 멀리 나갔다가 다시 돌아오면 한 펀치로
 * 보고, 그 사이 기록한 (팔꿈치 정점 각도 / 손목 수평 이동 / 손목 상승)으로 종류를 분류.
 *  - 팔꿈치가 충분히 펴짐  → 스트레이트 (왼팔=[Technique.JAB], 오른팔=[Technique.STRAIGHT])
 *  - 손목이 크게 상승        → [Technique.UPPERCUT]
 *  - 팔 안 펴고 수평 스윙     → [Technique.HOOK]
 *
 * 모든 거리는 **어깨 폭으로 정규화** (카메라 거리·체형 보정). 임계값은 [Thresholds] — 실기기 튜닝 대상.
 * 인스턴스는 한 세션 동안 상태 유지. 새 세션마다 [reset].
 */
class ShadowMotionAnalyzer {

    object Thresholds {
        /** 가드(기준) 위치 EMA 추종 계수 — 클수록 빨리 따라감. */
        const val BASELINE_EMA = 0.2f
        /** 손목이 기준에서 이만큼(어깨폭 배수) 이상 멀어지면 펀치 시작으로 봄. */
        const val STRIKE_OUT = 0.65f
        /** 손목이 기준에서 이만큼 이내로 돌아오면 펀치 종료(복귀)로 봄. */
        const val STRIKE_RETURN = 0.32f
        /** 한 펀치(나감→복귀) 최대 허용 시간(ms). 초과 시 펀치 아닌 동작으로 폐기. */
        const val STRIKE_MAX_MS = 1300L
        /** 스트레이트 분류: 팔꿈치 정점 각도(도) 이 값 이상. */
        const val STRAIGHT_ELBOW = 150f
        /** "좋은 신전" — 미만이면 신전 부족 스트레이트. */
        const val GOOD_EXTENSION = 162f
        /** 어퍼컷 분류: 손목 상승량(어깨폭 배수) 이 값 이상 + 수평보다 큼. */
        const val UPPERCUT_RISE = 0.35f
        /** 훅 분류: 손목 수평 이동량(어깨폭 배수) 이 값 이상. */
        const val HOOK_HORIZONTAL = 0.45f
        /** 가드 다운: 양 손목이 어깨선보다 이만큼(정규화 y) 아래. */
        const val GUARD_DOWN_MARGIN = 0.06f
        /** 턱 들림: 코가 귀선보다 이만큼(정규화 y) 위. */
        const val CHIN_UP_MARGIN = 0.04f
        /** 관절 신뢰도 — 미만이면 판정 스킵. */
        const val MIN_VIS = 0.5f
    }

    private enum class Arm { LEFT, RIGHT }

    /** 한 팔의 펀치 궤적 추적 상태. */
    private class ArmState {
        var baseX = Float.NaN
        var baseY = Float.NaN
        var striking = false
        var startMs = 0L
        var peakElbow = 0f
        var peakHoriz = 0f
        var peakRise = 0f
    }

    private val left = ArmState()
    private val right = ArmState()

    fun reset() {
        listOf(left, right).forEach {
            it.baseX = Float.NaN; it.baseY = Float.NaN; it.striking = false
            it.startMs = 0L; it.peakElbow = 0f; it.peakHoriz = 0f; it.peakRise = 0f
        }
    }

    fun process(frame: PoseFrame): ShadowFrameResult {
        if (frame.points.isEmpty()) return ShadowFrameResult(frame = frame, posed = false)
        val shoulderW = frame.shoulderWidth()?.takeIf { it > 1e-3f }
            ?: return ShadowFrameResult(frame = frame, posed = true)

        val strikes = mutableListOf<Technique>()
        var poorExt = 0

        updateArm(left, Arm.LEFT, frame, shoulderW,
            PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.LEFT_ELBOW, PoseLandmarks.LEFT_WRIST)
            ?.let { (tech, poor) -> strikes += tech; if (poor) poorExt++ }
        updateArm(right, Arm.RIGHT, frame, shoulderW,
            PoseLandmarks.RIGHT_SHOULDER, PoseLandmarks.RIGHT_ELBOW, PoseLandmarks.RIGHT_WRIST)
            ?.let { (tech, poor) -> strikes += tech; if (poor) poorExt++ }

        val cues = mutableSetOf<PostureCheck>()
        if (isGuardDown(frame)) cues += PostureCheck.GUARD_DOWN
        if (isChinUp(frame)) cues += PostureCheck.CHIN_UP

        return ShadowFrameResult(
            frame = frame,
            posed = true,
            newStrikes = strikes,
            activeCues = cues,
            poorExtensionStrikes = poorExt,
        )
    }

    /** @return 완료된 펀치 (종류, 신전부족여부) 또는 null. */
    private fun updateArm(
        s: ArmState,
        arm: Arm,
        frame: PoseFrame,
        shoulderW: Float,
        shoulder: Int,
        elbow: Int,
        wrist: Int,
    ): Pair<Technique, Boolean>? {
        val w = frame.point(wrist) ?: return null
        val e = frame.point(elbow) ?: return null
        if (w.visibility < Thresholds.MIN_VIS || e.visibility < Thresholds.MIN_VIS) return null

        if (s.baseX.isNaN()) { s.baseX = w.x; s.baseY = w.y; return null }

        val dispNorm = hypot(w.x - s.baseX, w.y - s.baseY) / shoulderW
        val elbowAngle = frame.angleDeg(shoulder, elbow, wrist) ?: 0f

        if (!s.striking) {
            // 기준(가드) 위치 추종 — 손목이 기준 근처에서 천천히 움직일 때만 갱신.
            if (dispNorm < Thresholds.STRIKE_RETURN) {
                s.baseX += (w.x - s.baseX) * Thresholds.BASELINE_EMA
                s.baseY += (w.y - s.baseY) * Thresholds.BASELINE_EMA
            }
            if (dispNorm >= Thresholds.STRIKE_OUT) {
                s.striking = true
                s.startMs = frame.timestampMs
                s.peakElbow = elbowAngle
                s.peakHoriz = abs(w.x - s.baseX) / shoulderW
                s.peakRise = (s.baseY - w.y) / shoulderW
            }
            return null
        }

        // 펀치 진행 — 정점 갱신.
        if (elbowAngle > s.peakElbow) s.peakElbow = elbowAngle
        s.peakHoriz = maxOf(s.peakHoriz, abs(w.x - s.baseX) / shoulderW)
        s.peakRise = maxOf(s.peakRise, (s.baseY - w.y) / shoulderW)

        val dt = frame.timestampMs - s.startMs
        if (dispNorm <= Thresholds.STRIKE_RETURN) {
            // 복귀 → 펀치 완료. 분류.
            val tech = classify(arm, s)
            val poor = (tech == Technique.JAB || tech == Technique.STRAIGHT) &&
                s.peakElbow < Thresholds.GOOD_EXTENSION
            s.striking = false
            return if (dt in 1..Thresholds.STRIKE_MAX_MS) tech to poor else null
        }
        if (dt > Thresholds.STRIKE_MAX_MS) {
            s.striking = false // 너무 느림 — 폐기
        }
        return null
    }

    private fun classify(arm: Arm, s: ArmState): Technique = when {
        s.peakElbow >= Thresholds.STRAIGHT_ELBOW ->
            if (arm == Arm.LEFT) Technique.JAB else Technique.STRAIGHT
        s.peakRise >= Thresholds.UPPERCUT_RISE && s.peakRise > s.peakHoriz -> Technique.UPPERCUT
        s.peakHoriz >= Thresholds.HOOK_HORIZONTAL -> Technique.HOOK
        else -> if (arm == Arm.LEFT) Technique.JAB else Technique.STRAIGHT
    }

    private fun isGuardDown(frame: PoseFrame): Boolean {
        val ls = frame.point(PoseLandmarks.LEFT_SHOULDER) ?: return false
        val rs = frame.point(PoseLandmarks.RIGHT_SHOULDER) ?: return false
        val lw = frame.point(PoseLandmarks.LEFT_WRIST) ?: return false
        val rw = frame.point(PoseLandmarks.RIGHT_WRIST) ?: return false
        if (minOf(ls.visibility, rs.visibility, lw.visibility, rw.visibility) < Thresholds.MIN_VIS) return false
        val shoulderY = (ls.y + rs.y) / 2f
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
        return (earY - nose.y) > Thresholds.CHIN_UP_MARGIN
    }
}
