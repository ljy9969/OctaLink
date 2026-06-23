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
    /** 표시 + TTS 용 — 이 프레임의 모든 위반 (프레임 기반 + 이번 펀치의 일시 cue). */
    val activeCues: Set<PostureCheck> = emptySet(),
    /** 점수용 프레임 기반 위반 (가드/턱/중심). 매 포즈 프레임 표본화. */
    val frameViolations: Set<PostureCheck> = emptySet(),
    /** 이번 프레임 완료된 스트레이트 중 신전 부족 수. */
    val poorExtensionStrikes: Int = 0,
    /** 이번 프레임 완료된 라이트(오른손) 중 골반 회전 부족 수. */
    val poorHipStrikes: Int = 0,
    /** 이번 프레임 완료된 펀치 중 반대손 가드가 내려간 수. */
    val offHandDropStrikes: Int = 0,
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
        const val BASELINE_EMA = 0.25f
        /**
         * 손목이 기준에서 이만큼(어깨폭 배수) 이상 멀어지는 "순간" 펀치 1회 카운트 (rising edge).
         * 완전 복귀를 기다리지 않으므로 빠른 연타도 잡힘. 낮출수록 민감(오탐↑).
         */
        const val STRIKE_OUT = 0.5f
        /** 손목이 기준에서 이만큼 이내로 돌아오면 가드 복귀 → 다음 펀치 카운트 재무장(rearm). */
        const val STRIKE_RETURN = 0.3f
        /** 스트레이트 분류: 카운트 시점 팔꿈치 각도(도) 이 값 이상. */
        const val STRAIGHT_ELBOW = 138f
        /** "좋은 신전" — 미만이면 신전 부족 스트레이트. */
        const val GOOD_EXTENSION = 155f
        /** 어퍼컷 분류: 손목 상승량(어깨폭 배수) 이 값 이상 + 수평보다 큼. */
        const val UPPERCUT_RISE = 0.3f
        /** 훅 분류: 손목 수평 이동량(어깨폭 배수) 이 값 이상. */
        const val HOOK_HORIZONTAL = 0.4f
        /** 가드 다운: 양 손목이 어깨선보다 이만큼(정규화 y) 아래. */
        const val GUARD_DOWN_MARGIN = 0.06f
        /** 턱 들림: 코가 귀선보다 이만큼(정규화 y) 위. */
        const val CHIN_UP_MARGIN = 0.04f
        /** 중심 무너짐: 어깨 중심이 골반 중심에서 좌우로 이만큼(어깨폭 배수) 벗어남. (보수적 = 큰 기울기만) */
        const val BALANCE_LEAN = 0.55f
        /** 골반 회전 부족: 라이트(오른손) 칠 때 골반 좌우 이동이 이 값(어깨폭 배수) 미만. (보수적 = 거의 0 일 때만) */
        const val HIP_ROTATION_MIN = 0.05f
        /** 반대손 가드 다운: 펀치 시 반대손 손목이 어깨선보다 이만큼 아래. */
        const val OFF_HAND_MARGIN = 0.08f
        /** 리커버리 지연: 펀치 후 이 시간(ms) 안에 손이 가드로 안 돌아오면 경고. */
        const val RECOVERY_MS = 1000L
        /** 팔꿈치 벌어짐: 손 올린 가드 중 팔꿈치가 어깨에서 좌우로 이만큼(어깨폭 배수) 벌어짐. */
        const val ELBOW_FLARE = 0.7f
        /** 어깨 긴장(으쓱): 어깨–귀 세로 간격(어깨폭 배수)이 이 값 미만(어깨가 올라옴). */
        const val SHOULDER_GAP_MIN = 0.32f
        /** 머리 중심 이탈: 코가 몸통 중심선에서 좌우로 이만큼(어깨폭 배수) 벗어남. */
        const val HEAD_OFFLINE = 0.38f
        /** 스탠스 좁음: 발목 간격(어깨폭 배수)이 이 값 미만. */
        const val STANCE_NARROW = 0.75f
        /** 무릎 뻣뻣: 무릎 각도(도)가 양다리 이 값 이상(거의 직선). */
        const val KNEE_STRAIGHT = 172f
        // 회피 동작(머리 궤적) — 코가 기준에서 나가는 순간 카운트, 방향으로 분류.
        /** 머리가 기준에서 이만큼(어깨폭 배수) 나가면 회피 1회. */
        const val HEAD_OUT = 0.35f
        /** 머리가 기준 이내로 돌아오면 재무장. */
        const val HEAD_RETURN = 0.2f
        /** 더킹: 머리 하강량(어깨폭 배수) 이 값 이상. */
        const val DUCK_DROP = 0.28f
        /** 슬립: 머리 좌우 이동량(어깨폭 배수) 이 값 이상. */
        const val SLIP_LATERAL = 0.28f
        /** 위빙: 하강 + 좌우 동시 — 좌우 성분 최소치. */
        const val WEAVE_LATERAL = 0.22f
        /** 관절 신뢰도 — 미만이면 판정 스킵. */
        const val MIN_VIS = 0.5f
    }

    private enum class Arm { LEFT, RIGHT }

    /** 한 팔의 펀치 추적 상태 (rising-edge 카운트 + 리커버리 타이밍). */
    private class ArmState {
        var baseX = Float.NaN
        var baseY = Float.NaN
        /** true = 다음 펀치 카운트 가능. 카운트 후 가드 복귀까지 false(중복 방지). */
        var armed = true
        /** 마지막 펀치 카운트 시각(ms). 리커버리 지연 판정용. */
        var lastStrikeMs = 0L
        /** 마지막 펀치 후 손이 가드로 복귀했는지. */
        var recovered = true
        /** 이번 펀치의 리커버리 지연을 이미 경고했는지 (1회만). */
        var recoveryWarned = false
    }

    /** 머리(코) 회피 궤적 추적 — rising-edge 카운트. */
    private class HeadState {
        var baseX = Float.NaN
        var baseY = Float.NaN
        var armed = true
    }

    private val left = ArmState()
    private val right = ArmState()
    private val head = HeadState()
    /** 골반 중심 x 기준(EMA) — 라이트 칠 때 회전(좌우 이동) 판정용. */
    private var hipBaseX = Float.NaN

    fun reset() {
        listOf(left, right).forEach {
            it.baseX = Float.NaN; it.baseY = Float.NaN; it.armed = true
            it.lastStrikeMs = 0L; it.recovered = true; it.recoveryWarned = false
        }
        head.baseX = Float.NaN; head.baseY = Float.NaN; head.armed = true
        hipBaseX = Float.NaN
    }

    fun process(frame: PoseFrame): ShadowFrameResult {
        if (frame.points.isEmpty()) return ShadowFrameResult(frame = frame, posed = false)
        val shoulderW = frame.shoulderWidth()?.takeIf { it > 1e-3f }
            ?: return ShadowFrameResult(frame = frame, posed = true)

        // 골반 중심 x 추적(EMA) — 펀치 여부와 무관하게 천천히 따라감.
        val hipMidX = hipMidX(frame)
        if (hipMidX != null) {
            hipBaseX = if (hipBaseX.isNaN()) hipMidX else hipBaseX + (hipMidX - hipBaseX) * 0.1f
        }
        val hipShiftNorm = if (hipMidX != null && !hipBaseX.isNaN()) abs(hipMidX - hipBaseX) / shoulderW else 0f

        val strikes = mutableListOf<Technique>()
        var poorExt = 0
        var poorHip = 0
        var offHand = 0
        val strikeCues = mutableSetOf<PostureCheck>()

        updateArm(left, Arm.LEFT, frame, shoulderW,
            PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.LEFT_ELBOW, PoseLandmarks.LEFT_WRIST, hipShiftNorm)
            ?.let { (tech, ext, hip) ->
                strikes += tech
                if (ext) { poorExt++; strikeCues += PostureCheck.POOR_EXTENSION }
                if (hip) { poorHip++; strikeCues += PostureCheck.POOR_HIP_ROTATION }
                // 왼손 펀치 → 오른손(반대손) 가드 유지 확인.
                if (isWristDropped(frame, PoseLandmarks.RIGHT_WRIST)) { offHand++; strikeCues += PostureCheck.OFF_HAND_DROP }
            }
        updateArm(right, Arm.RIGHT, frame, shoulderW,
            PoseLandmarks.RIGHT_SHOULDER, PoseLandmarks.RIGHT_ELBOW, PoseLandmarks.RIGHT_WRIST, hipShiftNorm)
            ?.let { (tech, ext, hip) ->
                strikes += tech
                if (ext) { poorExt++; strikeCues += PostureCheck.POOR_EXTENSION }
                if (hip) { poorHip++; strikeCues += PostureCheck.POOR_HIP_ROTATION }
                if (isWristDropped(frame, PoseLandmarks.LEFT_WRIST)) { offHand++; strikeCues += PostureCheck.OFF_HAND_DROP }
            }

        // 회피 동작(더킹/슬립/위빙) — 머리 궤적으로 감지, 펀치처럼 카운트.
        detectHeadMove(frame, shoulderW)?.let { strikes += it }

        val frameViolations = mutableSetOf<PostureCheck>()
        if (isGuardDown(frame)) frameViolations += PostureCheck.GUARD_DOWN
        if (isChinUp(frame)) frameViolations += PostureCheck.CHIN_UP
        if (isBalanceLost(frame, shoulderW)) frameViolations += PostureCheck.BALANCE_LOSS
        if (isElbowFlared(frame, shoulderW)) frameViolations += PostureCheck.ELBOW_FLARE
        if (isShoulderShrug(frame, shoulderW)) frameViolations += PostureCheck.SHOULDER_SHRUG
        if (isHeadOffline(frame, shoulderW)) frameViolations += PostureCheck.HEAD_OFFLINE
        if (isStanceNarrow(frame, shoulderW)) frameViolations += PostureCheck.STANCE_NARROW
        if (areKneesStraight(frame)) frameViolations += PostureCheck.KNEES_STRAIGHT
        if (checkRecovery(left, frame.timestampMs) || checkRecovery(right, frame.timestampMs)) {
            frameViolations += PostureCheck.SLOW_RECOVERY
        }

        return ShadowFrameResult(
            frame = frame,
            posed = true,
            newStrikes = strikes,
            activeCues = frameViolations + strikeCues,
            frameViolations = frameViolations,
            poorExtensionStrikes = poorExt,
            poorHipStrikes = poorHip,
            offHandDropStrikes = offHand,
        )
    }

    private fun hipMidX(frame: PoseFrame): Float? {
        val lh = frame.point(PoseLandmarks.LEFT_HIP) ?: return null
        val rh = frame.point(PoseLandmarks.RIGHT_HIP) ?: return null
        if (minOf(lh.visibility, rh.visibility) < Thresholds.MIN_VIS) return null
        return (lh.x + rh.x) / 2f
    }

    /**
     * rising-edge 펀치 검출. 손목이 가드 근처면 기준 추종 + 재무장, 기준에서 [STRIKE_OUT] 넘게
     * 튀어나가는 순간(무장 상태) 1회 카운트. 완전 복귀를 기다리지 않아 빠른 펀치도 잡힘.
     * @return 카운트된 펀치 (종류, 신전부족여부) 또는 null.
     */
    /** @return (펀치 종류, 신전부족, 골반회전부족) 또는 null. */
    private fun updateArm(
        s: ArmState,
        arm: Arm,
        frame: PoseFrame,
        shoulderW: Float,
        shoulder: Int,
        elbow: Int,
        wrist: Int,
        hipShiftNorm: Float,
    ): Triple<Technique, Boolean, Boolean>? {
        val w = frame.point(wrist) ?: return null
        val e = frame.point(elbow) ?: return null
        if (w.visibility < Thresholds.MIN_VIS || e.visibility < Thresholds.MIN_VIS) return null

        if (s.baseX.isNaN()) { s.baseX = w.x; s.baseY = w.y; return null }

        val dx = (w.x - s.baseX) / shoulderW
        val dy = (w.y - s.baseY) / shoulderW   // 음수 = 위로 이동
        val dispNorm = hypot(w.x - s.baseX, w.y - s.baseY) / shoulderW

        // 가드 근처 — 기준 추종 + 재무장 + 리커버리 완료.
        if (dispNorm < Thresholds.STRIKE_RETURN) {
            s.baseX += (w.x - s.baseX) * Thresholds.BASELINE_EMA
            s.baseY += (w.y - s.baseY) * Thresholds.BASELINE_EMA
            s.armed = true
            s.recovered = true
            return null
        }

        // 튀어나가는 순간 카운트 (무장 상태에서 1회).
        if (s.armed && dispNorm >= Thresholds.STRIKE_OUT) {
            s.armed = false
            s.lastStrikeMs = frame.timestampMs
            s.recovered = false
            s.recoveryWarned = false
            val elbowAngle = frame.angleDeg(shoulder, elbow, wrist) ?: 0f
            val tech = classify(arm, elbowAngle, dx, dy)
            val poorExt = (tech == Technique.JAB || tech == Technique.STRAIGHT) &&
                elbowAngle < Thresholds.GOOD_EXTENSION
            // 골반 회전 부족 — 라이트(오른손 파워펀치)인데 골반 좌우 이동이 거의 없을 때.
            val poorHip = tech == Technique.STRAIGHT && hipShiftNorm < Thresholds.HIP_ROTATION_MIN
            return Triple(tech, poorExt, poorHip)
        }
        return null
    }

    /**
     * 회피 동작 검출 — 코가 기준에서 [HEAD_OUT] 넘게 나가는 순간 1회 분류.
     *  - 하강 우세 → 더킹, 좌우 우세 → 슬립, 하강+좌우 동시 → 위빙.
     */
    private fun detectHeadMove(frame: PoseFrame, shoulderW: Float): Technique? {
        val n = frame.point(PoseLandmarks.NOSE) ?: return null
        if (n.visibility < Thresholds.MIN_VIS) return null
        if (head.baseX.isNaN()) { head.baseX = n.x; head.baseY = n.y; return null }

        val dx = (n.x - head.baseX) / shoulderW
        val dy = (n.y - head.baseY) / shoulderW   // 양수 = 아래로(하강)
        val disp = hypot(n.x - head.baseX, n.y - head.baseY) / shoulderW

        if (disp < Thresholds.HEAD_RETURN) {
            head.baseX += (n.x - head.baseX) * Thresholds.BASELINE_EMA
            head.baseY += (n.y - head.baseY) * Thresholds.BASELINE_EMA
            head.armed = true
            return null
        }
        if (head.armed && disp >= Thresholds.HEAD_OUT) {
            head.armed = false
            val drop = dy
            val lat = abs(dx)
            return when {
                drop >= Thresholds.DUCK_DROP && lat >= Thresholds.WEAVE_LATERAL -> Technique.WEAVE
                drop >= Thresholds.DUCK_DROP && drop >= lat -> Technique.DUCK
                lat >= Thresholds.SLIP_LATERAL -> Technique.SLIP
                else -> null
            }
        }
        return null
    }

    /** 펀치 후 가드 복귀가 [RECOVERY_MS] 넘게 지연되면 1회 true. */
    private fun checkRecovery(s: ArmState, nowMs: Long): Boolean {
        if (s.recovered || s.recoveryWarned || s.lastStrikeMs == 0L) return false
        if (nowMs - s.lastStrikeMs > Thresholds.RECOVERY_MS) {
            s.recoveryWarned = true
            return true
        }
        return false
    }

    private fun classify(arm: Arm, elbowAngle: Float, dx: Float, dy: Float): Technique {
        val rise = -dy            // 위로 이동량(양수면 상승)
        val horiz = abs(dx)
        return when {
            elbowAngle >= Thresholds.STRAIGHT_ELBOW ->
                if (arm == Arm.LEFT) Technique.JAB else Technique.STRAIGHT
            rise >= Thresholds.UPPERCUT_RISE && rise > horiz -> Technique.UPPERCUT
            horiz >= Thresholds.HOOK_HORIZONTAL -> Technique.HOOK
            else -> if (arm == Arm.LEFT) Technique.JAB else Technique.STRAIGHT
        }
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

    /** 중심 무너짐 — 어깨 중심이 골반 중심보다 좌우로 크게 벗어남(상체가 한쪽으로 기욺). */
    private fun isBalanceLost(frame: PoseFrame, shoulderW: Float): Boolean {
        val ls = frame.point(PoseLandmarks.LEFT_SHOULDER) ?: return false
        val rs = frame.point(PoseLandmarks.RIGHT_SHOULDER) ?: return false
        val lh = frame.point(PoseLandmarks.LEFT_HIP) ?: return false
        val rh = frame.point(PoseLandmarks.RIGHT_HIP) ?: return false
        if (minOf(ls.visibility, rs.visibility, lh.visibility, rh.visibility) < Thresholds.MIN_VIS) return false
        val shoulderMidX = (ls.x + rs.x) / 2f
        val hipMidX = (lh.x + rh.x) / 2f
        return abs(shoulderMidX - hipMidX) / shoulderW > Thresholds.BALANCE_LEAN
    }

    /** 손목이 어깨선보다 아래로 내려갔는지 (반대손 가드 다운 판정용). */
    private fun isWristDropped(frame: PoseFrame, wrist: Int): Boolean {
        val ls = frame.point(PoseLandmarks.LEFT_SHOULDER) ?: return false
        val rs = frame.point(PoseLandmarks.RIGHT_SHOULDER) ?: return false
        val w = frame.point(wrist) ?: return false
        if (minOf(ls.visibility, rs.visibility, w.visibility) < Thresholds.MIN_VIS) return false
        val shoulderY = (ls.y + rs.y) / 2f
        return w.y > shoulderY + Thresholds.OFF_HAND_MARGIN
    }

    /** 팔꿈치 벌어짐 — 손이 가드(어깨 높이 위)인데 팔꿈치가 어깨에서 좌우로 크게 벌어짐. */
    private fun isElbowFlared(frame: PoseFrame, shoulderW: Float): Boolean {
        fun flared(shoulder: Int, elbow: Int, wrist: Int): Boolean {
            val s = frame.point(shoulder) ?: return false
            val el = frame.point(elbow) ?: return false
            val w = frame.point(wrist) ?: return false
            if (minOf(s.visibility, el.visibility, w.visibility) < Thresholds.MIN_VIS) return false
            // 손이 가드(어깨선 위)일 때만 평가.
            if (w.y > s.y) return false
            return abs(el.x - s.x) / shoulderW > Thresholds.ELBOW_FLARE
        }
        return flared(PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.LEFT_ELBOW, PoseLandmarks.LEFT_WRIST) ||
            flared(PoseLandmarks.RIGHT_SHOULDER, PoseLandmarks.RIGHT_ELBOW, PoseLandmarks.RIGHT_WRIST)
    }

    /** 어깨 긴장(으쓱) — 어깨–귀 세로 간격이 좁음(어깨가 귀 쪽으로 올라옴). */
    private fun isShoulderShrug(frame: PoseFrame, shoulderW: Float): Boolean {
        val ls = frame.point(PoseLandmarks.LEFT_SHOULDER) ?: return false
        val rs = frame.point(PoseLandmarks.RIGHT_SHOULDER) ?: return false
        val le = frame.point(PoseLandmarks.LEFT_EAR) ?: return false
        val re = frame.point(PoseLandmarks.RIGHT_EAR) ?: return false
        if (minOf(ls.visibility, rs.visibility, le.visibility, re.visibility) < Thresholds.MIN_VIS) return false
        val shoulderY = (ls.y + rs.y) / 2f
        val earY = (le.y + re.y) / 2f
        // 어깨가 귀보다 아래(정상). 간격이 작아지면 어깨가 올라온 것.
        return (shoulderY - earY) / shoulderW < Thresholds.SHOULDER_GAP_MIN
    }

    /** 머리 중심 이탈 — 코가 몸통(어깨·골반) 중심선에서 좌우로 크게 벗어남. */
    private fun isHeadOffline(frame: PoseFrame, shoulderW: Float): Boolean {
        val nose = frame.point(PoseLandmarks.NOSE) ?: return false
        val ls = frame.point(PoseLandmarks.LEFT_SHOULDER) ?: return false
        val rs = frame.point(PoseLandmarks.RIGHT_SHOULDER) ?: return false
        if (minOf(nose.visibility, ls.visibility, rs.visibility) < Thresholds.MIN_VIS) return false
        val midX = (ls.x + rs.x) / 2f
        return abs(nose.x - midX) / shoulderW > Thresholds.HEAD_OFFLINE
    }

    /** 스탠스 좁음 — 발목 간격이 어깨폭 대비 좁음. (발목 안 보이면 스킵) */
    private fun isStanceNarrow(frame: PoseFrame, shoulderW: Float): Boolean {
        val la = frame.point(PoseLandmarks.LEFT_ANKLE) ?: return false
        val ra = frame.point(PoseLandmarks.RIGHT_ANKLE) ?: return false
        if (minOf(la.visibility, ra.visibility) < Thresholds.MIN_VIS) return false
        return abs(la.x - ra.x) / shoulderW < Thresholds.STANCE_NARROW
    }

    /** 무릎 뻣뻣 — 양 무릎 각도(엉덩이-무릎-발목)가 거의 직선. (다리 안 보이면 스킵) */
    private fun areKneesStraight(frame: PoseFrame): Boolean {
        val l = frame.angleDeg(PoseLandmarks.LEFT_HIP, PoseLandmarks.LEFT_KNEE, PoseLandmarks.LEFT_ANKLE)
        val r = frame.angleDeg(PoseLandmarks.RIGHT_HIP, PoseLandmarks.RIGHT_KNEE, PoseLandmarks.RIGHT_ANKLE)
        if (l == null || r == null) return false
        // 무릎/발목 신뢰도 확인.
        val lk = frame.point(PoseLandmarks.LEFT_KNEE)?.visibility ?: 0f
        val rk = frame.point(PoseLandmarks.RIGHT_KNEE)?.visibility ?: 0f
        if (minOf(lk, rk) < Thresholds.MIN_VIS) return false
        return l >= Thresholds.KNEE_STRAIGHT && r >= Thresholds.KNEE_STRAIGHT
    }
}
