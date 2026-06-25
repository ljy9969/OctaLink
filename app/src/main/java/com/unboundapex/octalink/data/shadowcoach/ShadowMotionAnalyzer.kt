package com.unboundapex.octalink.data.shadowcoach

import kotlin.math.abs
import kotlin.math.atan2
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
         * (3D) 손목이 기준에서 이만큼(3D 어깨폭 배수) 멀어지는 "순간" 펀치 1회 카운트(rising edge).
         * world 좌표(미터) 기준이라 2D 보다 스케일이 큼. 낮출수록 민감(오탐↑).
         */
        const val STRIKE_OUT = 0.9f
        /** (3D) 손목이 기준 이내로 돌아오면 가드 복귀 → 펀치 확정 + 재무장. */
        const val STRIKE_RETURN = 0.5f
        /** 펀치 시작 후 복귀가 없어도 이 시간(ms) 지나면 정점값으로 확정(길게 뻗고 멈춘 경우). */
        const val STRIKE_FINALIZE_MS = 600L
        /** 스트레이트 분류: 정점 3D 팔꿈치 각도(도) 이 값 이상. */
        const val STRAIGHT_ELBOW = 150f
        /** "좋은 신전" — 정점 팔꿈치각 이 값 미만이면 신전 부족 스트레이트. */
        const val GOOD_EXTENSION = 160f
        /** 어퍼컷 분류: 손목 상승량(3D 어깨폭 배수) 이 값 이상 + 전방/수평보다 우세. */
        const val UPPERCUT_RISE = 0.5f
        /** 훅 분류: 손목 수평 이동량(3D 어깨폭 배수) 이 값 이상. */
        const val HOOK_LATERAL = 0.6f
        /** (3D) 가드 다운: 양 손목이 어깨선보다 이만큼(3D 어깨폭 배수) 아래(world y). */
        const val GUARD_DOWN_MARGIN = 0.20f
        /** (3D) 턱 들림: 코가 귀선보다 이만큼(3D 어깨폭 배수) 위(world y). */
        const val CHIN_UP_MARGIN = 0.10f
        /** (3D) 중심 무너짐: 어깨 중심이 골반 중심에서 **몸 기준 좌우**로 이만큼(어깨폭 배수) 벗어남.
         *  (전후 기울기는 정상 복싱 자세라 제외 — 좌우 성분만. 보수적 = 큰 기울기만) */
        const val BALANCE_LEAN = 0.55f
        /** (3D) 골반 회전 부족: 라이트(오른손) 칠 때 골반 회전각 변화(라디안)가 이 값 미만. (~9°) */
        const val HIP_ROTATION_MIN_RAD = 0.15f
        /** (3D) 반대손 가드 다운: 펀치 시 반대손 손목이 어깨선보다 이만큼(어깨폭 배수) 아래(world y). */
        const val OFF_HAND_MARGIN = 0.30f
        /** 리커버리 지연: 펀치 후 이 시간(ms) 안에 손이 가드로 안 돌아오면 경고. */
        const val RECOVERY_MS = 1000L
        /** (3D) 팔꿈치 벌어짐: 손 올린 가드 중 팔꿈치가 어깨에서 수평(xz)으로 이만큼(어깨폭 배수) 벌어짐. */
        const val ELBOW_FLARE = 0.7f
        /** (3D) 어깨 긴장(으쓱): 어깨–귀 세로 간격(world y, 어깨폭 배수)이 이 값 미만(어깨가 올라옴). */
        const val SHOULDER_GAP_MIN = 0.32f
        /** (3D) 머리 중심 이탈: 코가 몸통 중심선에서 **몸 기준 좌우**로 이만큼(어깨폭 배수) 벗어남. */
        const val HEAD_OFFLINE = 0.38f
        /** (3D) 스탠스 좁음: 발목 수평(xz) 간격(어깨폭 배수)이 이 값 미만. (블레이드 앞뒤 간격 포함) */
        const val STANCE_NARROW = 0.75f
        /** 무릎 뻣뻣: 무릎 각도(도)가 양다리 이 값 이상(거의 직선). */
        const val KNEE_STRAIGHT = 172f
        // (3D) 회피 동작(머리 궤적) — 코가 기준에서 나가는 순간 카운트, 몸 기준 방향으로 분류.
        /** 머리가 기준에서 이만큼(어깨폭 배수, 몸 기준 좌우+상하) 나가면 회피 1회. */
        const val HEAD_OUT = 0.35f
        /** 머리가 기준 이내로 돌아오면 재무장. */
        const val HEAD_RETURN = 0.2f
        /** 더킹: 머리 하강량(world y, 어깨폭 배수) 이 값 이상. */
        const val DUCK_DROP = 0.28f
        /** 슬립: 머리 **몸 기준 좌우** 이동량(어깨폭 배수) 이 값 이상. */
        const val SLIP_LATERAL = 0.28f
        /** 위빙: 하강 + 좌우 동시 — 좌우 성분 최소치. */
        const val WEAVE_LATERAL = 0.22f
        /** 관절 신뢰도 — 미만이면 판정 스킵. */
        const val MIN_VIS = 0.5f
    }

    private enum class Arm { LEFT, RIGHT }

    /**
     * 한 팔의 펀치 추적 상태. 좌표는 3D world(미터).
     *
     * 검출 흐름: 손목이 가드에서 [STRIKE_OUT] 넘게 나가는 순간 펀치 **시작**(아직 카운트 X) →
     * 그 사이 정점(최대 팔꿈치각·전방·상승·수평) 추적 → 가드 복귀 또는 [STRIKE_FINALIZE_MS]
     * 경과 시 **정점값으로 분류·신전 판정 후 카운트**. (나가는 순간이 아닌 정점 기준이라 정확.)
     */
    private class ArmState {
        var baseX = Float.NaN
        var baseY = Float.NaN
        var baseZ = Float.NaN
        /** true = 다음 펀치 시작 가능. 시작 후 가드 복귀까지 false(중복 방지). */
        var armed = true
        /** 펀치 진행 중(시작됨, 미확정). */
        var inStrike = false
        var strikeStartMs = 0L
        var peakElbow = 0f
        var peakFwd = 0f
        var peakLat = 0f
        var peakRise = 0f
        /** 마지막 펀치 시작 시각(ms). 리커버리 지연 판정용. */
        var lastStrikeMs = 0L
        /** 마지막 펀치 후 손이 가드로 복귀했는지. */
        var recovered = true
        /** 이번 펀치의 리커버리 지연을 이미 경고했는지 (1회만). */
        var recoveryWarned = false
    }

    /** 머리(코) 회피 궤적 추적 — rising-edge 카운트. 좌표는 3D world(미터). */
    private class HeadState {
        var baseX = Float.NaN
        var baseY = Float.NaN
        var baseZ = Float.NaN
        var armed = true
    }

    private val left = ArmState()
    private val right = ArmState()
    private val head = HeadState()
    /** (3D) 골반 회전각 기준(EMA, 라디안) — 라이트 칠 때 회전량 판정용. */
    private var hipBaseAngle = Float.NaN

    fun reset() {
        listOf(left, right).forEach {
            it.baseX = Float.NaN; it.baseY = Float.NaN; it.baseZ = Float.NaN; it.armed = true
            it.inStrike = false; it.strikeStartMs = 0L
            it.peakElbow = 0f; it.peakFwd = 0f; it.peakLat = 0f; it.peakRise = 0f
            it.lastStrikeMs = 0L; it.recovered = true; it.recoveryWarned = false
        }
        head.baseX = Float.NaN; head.baseY = Float.NaN; head.baseZ = Float.NaN; head.armed = true
        hipBaseAngle = Float.NaN
    }

    fun process(frame: PoseFrame): ShadowFrameResult {
        if (frame.points.isEmpty()) return ShadowFrameResult(frame = frame, posed = false)
        // 펀치/골반은 3D world 좌표 사용. world 가 없으면(미지원) 자세 프레임 체크만 수행.
        val world3dW = frame.worldShoulderWidth()?.takeIf { it > 1e-4f }

        // (3D) 골반 회전각 추적(EMA) — 골반 벡터(좌→우)의 수평면(xz) 방향. 펀치와 무관하게 천천히 따라감.
        var hipRotDelta = 0f
        val hipAngle = hipRotationAngle(frame)
        if (hipAngle != null) {
            hipBaseAngle = if (hipBaseAngle.isNaN()) hipAngle else hipBaseAngle + (hipAngle - hipBaseAngle) * 0.1f
            hipRotDelta = abs(hipAngle - hipBaseAngle)
        }

        val strikes = mutableListOf<Technique>()
        var poorExt = 0
        var poorHip = 0
        var offHand = 0
        val strikeCues = mutableSetOf<PostureCheck>()

        // 펀치 검출 + 자세/회피 모두 3D world 좌표 기준. world 없으면(미지원) 분석 스킵.
        val frameViolations = mutableSetOf<PostureCheck>()
        if (world3dW != null) {
            updateArm(left, Arm.LEFT, frame, world3dW,
                PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.LEFT_ELBOW, PoseLandmarks.LEFT_WRIST, hipRotDelta)
                ?.let { (tech, ext, hip) ->
                    strikes += tech
                    if (ext) { poorExt++; strikeCues += PostureCheck.POOR_EXTENSION }
                    if (hip) { poorHip++; strikeCues += PostureCheck.POOR_HIP_ROTATION }
                    if (isWristDropped(frame, world3dW, PoseLandmarks.RIGHT_WRIST)) { offHand++; strikeCues += PostureCheck.OFF_HAND_DROP }
                }
            updateArm(right, Arm.RIGHT, frame, world3dW,
                PoseLandmarks.RIGHT_SHOULDER, PoseLandmarks.RIGHT_ELBOW, PoseLandmarks.RIGHT_WRIST, hipRotDelta)
                ?.let { (tech, ext, hip) ->
                    strikes += tech
                    if (ext) { poorExt++; strikeCues += PostureCheck.POOR_EXTENSION }
                    if (hip) { poorHip++; strikeCues += PostureCheck.POOR_HIP_ROTATION }
                    if (isWristDropped(frame, world3dW, PoseLandmarks.LEFT_WRIST)) { offHand++; strikeCues += PostureCheck.OFF_HAND_DROP }
                }

            detectHeadMove(frame, world3dW)?.let { strikes += it }

            if (isGuardDown(frame, world3dW)) frameViolations += PostureCheck.GUARD_DOWN
            if (isChinUp(frame, world3dW)) frameViolations += PostureCheck.CHIN_UP
            if (isBalanceLost(frame, world3dW)) frameViolations += PostureCheck.BALANCE_LOSS
            if (isElbowFlared(frame, world3dW)) frameViolations += PostureCheck.ELBOW_FLARE
            if (isShoulderShrug(frame, world3dW)) frameViolations += PostureCheck.SHOULDER_SHRUG
            if (isHeadOffline(frame, world3dW)) frameViolations += PostureCheck.HEAD_OFFLINE
            if (isStanceNarrow(frame, world3dW)) frameViolations += PostureCheck.STANCE_NARROW
            if (areKneesStraight(frame)) frameViolations += PostureCheck.KNEES_STRAIGHT
        }
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

    /**
     * (3D) 골반 회전각 — 골반 벡터(왼→오른 엉덩이)를 수평면(xz)에 투영한 방향(라디안).
     * 정면 squared 면 ≈0, 오른쪽이 앞으로 돌면 부호가 바뀜. world 없으면 null.
     */
    private fun hipRotationAngle(frame: PoseFrame): Float? {
        val lh = frame.worldPoint(PoseLandmarks.LEFT_HIP) ?: return null
        val rh = frame.worldPoint(PoseLandmarks.RIGHT_HIP) ?: return null
        if (minOf(lh.visibility, rh.visibility) < Thresholds.MIN_VIS) return null
        val dx = rh.x - lh.x
        val dz = rh.z - lh.z
        return atan2(dz, dx)
    }

    /**
     * (3D) rising-edge 펀치 검출. 손목 3D 좌표가 가드 근처면 기준 추종 + 재무장, 기준에서
     * [STRIKE_OUT](3D 어깨폭 배수) 넘게 튀어나가는 순간 1회 카운트. 전방(깊이) 성분으로 스트레이트
     * 분류가 정면에서도 정확.
     * @param hipRotDelta 현재 골반 회전각 변화량(라디안) — 라이트 골반회전 판정용.
     * @return (펀치 종류, 신전부족, 골반회전부족) 또는 null.
     */
    private fun updateArm(
        s: ArmState,
        arm: Arm,
        frame: PoseFrame,
        shoulderW: Float,
        shoulder: Int,
        elbow: Int,
        wrist: Int,
        hipRotDelta: Float,
    ): Triple<Technique, Boolean, Boolean>? {
        val w = frame.worldPoint(wrist) ?: return null
        val e = frame.worldPoint(elbow) ?: return null
        if (w.visibility < Thresholds.MIN_VIS || e.visibility < Thresholds.MIN_VIS) return null

        if (s.baseX.isNaN()) { s.baseX = w.x; s.baseY = w.y; s.baseZ = w.z; return null }

        val ddx = w.x - s.baseX
        val ddy = w.y - s.baseY
        val ddz = w.z - s.baseZ
        val dispNorm = mag3(ddx, ddy, ddz) / shoulderW
        val elbowAngle = frame.angle3Deg(shoulder, elbow, wrist) ?: 0f

        // 펀치 방향을 "몸 기준 좌표"로 분해 — 옆으로 선(블레이드) 스탠스에서도 스트레이트/훅 구분이 정확.
        // 어깨선(수평면 xz 투영)을 좌우축, 거기에 수직인 방향을 전방축으로 보고 손목 변위를 투영.
        // 어깨선이 불명확하면(정면 등 길이 0) 카메라축으로 폴백 → 정면에선 기존과 동일.
        val rise = -ddy / shoulderW
        val ls = frame.worldPoint(PoseLandmarks.LEFT_SHOULDER)
        val rs = frame.worldPoint(PoseLandmarks.RIGHT_SHOULDER)
        val sLen = if (ls != null && rs != null) hypot(rs.x - ls.x, rs.z - ls.z) else 0f
        val fwd: Float
        val lat: Float
        if (ls != null && rs != null && sLen > 1e-4f) {
            val ux = (rs.x - ls.x) / sLen; val uz = (rs.z - ls.z) / sLen   // 좌우(어깨) 단위축
            val lateralM = ddx * ux + ddz * uz                            // 어깨선 방향(훅 성분)
            val forwardM = ddx * (-uz) + ddz * ux                         // 어깨선 수직(스트레이트 성분)
            lat = abs(lateralM) / shoulderW
            fwd = abs(forwardM) / shoulderW
        } else {
            lat = abs(ddx) / shoulderW
            fwd = abs(ddz) / shoulderW
        }

        // 가드 근처 — 진행 중 펀치 정점값으로 확정 + 기준 추종 + 재무장 + 리커버리 완료.
        if (dispNorm < Thresholds.STRIKE_RETURN) {
            val result = if (s.inStrike) finalizeStrike(s, arm, hipRotDelta) else null
            s.inStrike = false
            s.baseX += (w.x - s.baseX) * Thresholds.BASELINE_EMA
            s.baseY += (w.y - s.baseY) * Thresholds.BASELINE_EMA
            s.baseZ += (w.z - s.baseZ) * Thresholds.BASELINE_EMA
            s.armed = true
            s.recovered = true
            return result
        }

        // 펀치 시작 — 나가는 순간(무장). 아직 카운트 X, 정점 추적 시작.
        if (!s.inStrike && s.armed && dispNorm >= Thresholds.STRIKE_OUT) {
            s.inStrike = true
            s.armed = false
            s.strikeStartMs = frame.timestampMs
            s.lastStrikeMs = frame.timestampMs
            s.recovered = false
            s.recoveryWarned = false
            s.peakElbow = elbowAngle; s.peakFwd = fwd; s.peakLat = lat; s.peakRise = rise
            return null
        }

        // 펀치 진행 — 정점 추적, 타임아웃 시 정점값으로 확정.
        if (s.inStrike) {
            s.peakElbow = maxOf(s.peakElbow, elbowAngle)
            s.peakFwd = maxOf(s.peakFwd, fwd)
            s.peakLat = maxOf(s.peakLat, lat)
            s.peakRise = maxOf(s.peakRise, rise)
            if (frame.timestampMs - s.strikeStartMs > Thresholds.STRIKE_FINALIZE_MS) {
                val result = finalizeStrike(s, arm, hipRotDelta)
                s.inStrike = false // armed 은 가드 복귀 시까지 false 유지(중복 방지)
                return result
            }
        }
        return null
    }

    /** 펀치 정점값으로 종류·신전·골반 판정. */
    private fun finalizeStrike(s: ArmState, arm: Arm, hipRotDelta: Float): Triple<Technique, Boolean, Boolean> {
        val tech = classify(arm, s.peakElbow, forward = s.peakFwd, lateral = s.peakLat, rise = s.peakRise)
        val poorExt = (tech == Technique.JAB || tech == Technique.STRAIGHT) &&
            s.peakElbow < Thresholds.GOOD_EXTENSION
        val poorHip = tech == Technique.STRAIGHT && hipRotDelta < Thresholds.HIP_ROTATION_MIN_RAD
        return Triple(tech, poorExt, poorHip)
    }

    /** 해당 팔이 펀치 진행 중인지 — 자세 체크(팔꿈치 벌어짐)에서 펀치 동작 오탐 제외용. */
    private fun isArmStriking(arm: Arm): Boolean = if (arm == Arm.LEFT) left.inStrike else right.inStrike

    /**
     * (3D) 어깨선의 수평면(xz) 단위 좌우축 (ux,uz). 좌→오른 어깨 방향. 가시성/길이 부족 시 null.
     * 펀치·중심·머리 판정에서 변위를 "몸 기준 좌우 vs 전방"으로 분해하는 기준축.
     */
    private fun bodyLateralAxis(frame: PoseFrame): Pair<Float, Float>? {
        val ls = frame.worldPoint(PoseLandmarks.LEFT_SHOULDER) ?: return null
        val rs = frame.worldPoint(PoseLandmarks.RIGHT_SHOULDER) ?: return null
        if (minOf(ls.visibility, rs.visibility) < Thresholds.MIN_VIS) return null
        val sx = rs.x - ls.x; val sz = rs.z - ls.z
        val len = hypot(sx, sz)
        if (len < 1e-4f) return null
        return (sx / len) to (sz / len)
    }

    /** 수평 변위(dx,dz)의 몸 기준 좌우 성분 크기. [axis]=좌우 단위축. */
    private fun lateralComp(dx: Float, dz: Float, axis: Pair<Float, Float>): Float =
        abs(dx * axis.first + dz * axis.second)

    /**
     * (3D) 회피 동작 검출 — 코(world)가 기준에서 [HEAD_OUT] 넘게 나가는 순간 1회 분류.
     *  - 하강(world y) 우세 → 더킹, 몸 기준 좌우 우세 → 슬립, 하강+좌우 동시 → 위빙.
     */
    private fun detectHeadMove(frame: PoseFrame, shoulderW: Float): Technique? {
        val n = frame.worldPoint(PoseLandmarks.NOSE) ?: return null
        if (n.visibility < Thresholds.MIN_VIS) return null
        if (head.baseX.isNaN()) { head.baseX = n.x; head.baseY = n.y; head.baseZ = n.z; return null }

        val ddx = n.x - head.baseX
        val ddy = n.y - head.baseY    // 양수 = 아래로(하강)
        val ddz = n.z - head.baseZ
        val axis = bodyLateralAxis(frame)
        val lat = (if (axis != null) lateralComp(ddx, ddz, axis) else hypot(ddx, ddz)) / shoulderW
        val drop = ddy / shoulderW
        val disp = hypot(lat, drop)   // 몸 기준 머리 변위(좌우+상하).

        if (disp < Thresholds.HEAD_RETURN) {
            head.baseX += ddx * Thresholds.BASELINE_EMA
            head.baseY += ddy * Thresholds.BASELINE_EMA
            head.baseZ += ddz * Thresholds.BASELINE_EMA
            head.armed = true
            return null
        }
        if (head.armed && disp >= Thresholds.HEAD_OUT) {
            head.armed = false
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

    /**
     * (3D) 펀치 분류 — 성분은 **몸 기준 좌표**(어깨폭 배수). [forward] 어깨선 수직(앞으로 뻗음),
     * [lateral] 어깨선 방향(좌우 스윙), [rise] 상방. 블레이드 스탠스에서도 스탠스 각도와 무관.
     *  - 팔꿈치 펴짐 + 전방 우세 → 스트레이트(왼팔=잽 / 오른팔=라이트)
     *  - 상방 우세 → 어퍼, 좌우 우세 → 훅.
     */
    private fun classify(arm: Arm, elbowAngle: Float, forward: Float, lateral: Float, rise: Float): Technique {
        val straight = if (arm == Arm.LEFT) Technique.JAB else Technique.STRAIGHT
        return when {
            elbowAngle >= Thresholds.STRAIGHT_ELBOW && forward >= lateral && forward >= rise -> straight
            rise >= Thresholds.UPPERCUT_RISE && rise >= lateral && rise >= forward -> Technique.UPPERCUT
            lateral >= Thresholds.HOOK_LATERAL -> Technique.HOOK
            else -> straight
        }
    }

    /** (3D) 가드 다운 — 양 손목이 어깨선보다 world y 로 [GUARD_DOWN_MARGIN]·어깨폭 만큼 아래. */
    private fun isGuardDown(frame: PoseFrame, shoulderW: Float): Boolean {
        val ls = frame.worldPoint(PoseLandmarks.LEFT_SHOULDER) ?: return false
        val rs = frame.worldPoint(PoseLandmarks.RIGHT_SHOULDER) ?: return false
        val lw = frame.worldPoint(PoseLandmarks.LEFT_WRIST) ?: return false
        val rw = frame.worldPoint(PoseLandmarks.RIGHT_WRIST) ?: return false
        if (minOf(ls.visibility, rs.visibility, lw.visibility, rw.visibility) < Thresholds.MIN_VIS) return false
        val shoulderY = (ls.y + rs.y) / 2f
        val margin = Thresholds.GUARD_DOWN_MARGIN * shoulderW
        return (lw.y > shoulderY + margin) && (rw.y > shoulderY + margin)
    }

    /** (3D) 턱 들림 — 코가 귀선보다 world y 로 위로 [CHIN_UP_MARGIN]·어깨폭 이상. */
    private fun isChinUp(frame: PoseFrame, shoulderW: Float): Boolean {
        val nose = frame.worldPoint(PoseLandmarks.NOSE) ?: return false
        val le = frame.worldPoint(PoseLandmarks.LEFT_EAR) ?: return false
        val re = frame.worldPoint(PoseLandmarks.RIGHT_EAR) ?: return false
        if (minOf(nose.visibility, le.visibility, re.visibility) < Thresholds.MIN_VIS) return false
        val earY = (le.y + re.y) / 2f
        return (earY - nose.y) / shoulderW > Thresholds.CHIN_UP_MARGIN
    }

    /**
     * (3D) 중심 무너짐 — 어깨 중심이 골반 중심에서 **몸 기준 좌우**로 크게 벗어남(옆으로 기욺).
     * 전후(앞으로 숙임)는 정상 복싱 자세라 좌우 성분만 평가.
     */
    private fun isBalanceLost(frame: PoseFrame, shoulderW: Float): Boolean {
        val ls = frame.worldPoint(PoseLandmarks.LEFT_SHOULDER) ?: return false
        val rs = frame.worldPoint(PoseLandmarks.RIGHT_SHOULDER) ?: return false
        val lh = frame.worldPoint(PoseLandmarks.LEFT_HIP) ?: return false
        val rh = frame.worldPoint(PoseLandmarks.RIGHT_HIP) ?: return false
        if (minOf(ls.visibility, rs.visibility, lh.visibility, rh.visibility) < Thresholds.MIN_VIS) return false
        val axis = bodyLateralAxis(frame) ?: return false
        val dx = (ls.x + rs.x) / 2f - (lh.x + rh.x) / 2f
        val dz = (ls.z + rs.z) / 2f - (lh.z + rh.z) / 2f
        return lateralComp(dx, dz, axis) / shoulderW > Thresholds.BALANCE_LEAN
    }

    /** (3D) 손목이 어깨선보다 world y 로 아래로 내려갔는지 (반대손 가드 다운 판정용). */
    private fun isWristDropped(frame: PoseFrame, shoulderW: Float, wrist: Int): Boolean {
        val ls = frame.worldPoint(PoseLandmarks.LEFT_SHOULDER) ?: return false
        val rs = frame.worldPoint(PoseLandmarks.RIGHT_SHOULDER) ?: return false
        val w = frame.worldPoint(wrist) ?: return false
        if (minOf(ls.visibility, rs.visibility, w.visibility) < Thresholds.MIN_VIS) return false
        val shoulderY = (ls.y + rs.y) / 2f
        return (w.y - shoulderY) / shoulderW > Thresholds.OFF_HAND_MARGIN
    }

    /**
     * (3D) 팔꿈치 벌어짐 — 손이 가드(어깨 위)인데 팔꿈치가 어깨에서 수평(xz)으로 크게 벌어짐.
     * **펀치 진행 중인 팔은 제외** — 어퍼/훅은 팔꿈치가 정상적으로 벌어지므로 오탐 방지(가드 흐트러짐만 잡음).
     */
    private fun isElbowFlared(frame: PoseFrame, shoulderW: Float): Boolean {
        fun flared(arm: Arm, shoulder: Int, elbow: Int, wrist: Int): Boolean {
            if (isArmStriking(arm)) return false // 펀치 동작 중엔 평가 안 함
            val s = frame.worldPoint(shoulder) ?: return false
            val el = frame.worldPoint(elbow) ?: return false
            val w = frame.worldPoint(wrist) ?: return false
            if (minOf(s.visibility, el.visibility, w.visibility) < Thresholds.MIN_VIS) return false
            // 손이 가드(어깨선 위 = world y 가 더 작음)일 때만 평가.
            if (w.y > s.y) return false
            return hypot(el.x - s.x, el.z - s.z) / shoulderW > Thresholds.ELBOW_FLARE
        }
        return flared(Arm.LEFT, PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.LEFT_ELBOW, PoseLandmarks.LEFT_WRIST) ||
            flared(Arm.RIGHT, PoseLandmarks.RIGHT_SHOULDER, PoseLandmarks.RIGHT_ELBOW, PoseLandmarks.RIGHT_WRIST)
    }

    /** (3D) 어깨 긴장(으쓱) — 어깨–귀 world y 간격이 좁음(어깨가 귀 쪽으로 올라옴). */
    private fun isShoulderShrug(frame: PoseFrame, shoulderW: Float): Boolean {
        val ls = frame.worldPoint(PoseLandmarks.LEFT_SHOULDER) ?: return false
        val rs = frame.worldPoint(PoseLandmarks.RIGHT_SHOULDER) ?: return false
        val le = frame.worldPoint(PoseLandmarks.LEFT_EAR) ?: return false
        val re = frame.worldPoint(PoseLandmarks.RIGHT_EAR) ?: return false
        if (minOf(ls.visibility, rs.visibility, le.visibility, re.visibility) < Thresholds.MIN_VIS) return false
        val shoulderY = (ls.y + rs.y) / 2f
        val earY = (le.y + re.y) / 2f
        // 어깨가 귀보다 아래(정상, world y 큼). 간격이 작아지면 어깨가 올라온 것.
        return (shoulderY - earY) / shoulderW < Thresholds.SHOULDER_GAP_MIN
    }

    /** (3D) 머리 중심 이탈 — 코가 어깨 중심에서 **몸 기준 좌우**로 크게 벗어남. */
    private fun isHeadOffline(frame: PoseFrame, shoulderW: Float): Boolean {
        val nose = frame.worldPoint(PoseLandmarks.NOSE) ?: return false
        val ls = frame.worldPoint(PoseLandmarks.LEFT_SHOULDER) ?: return false
        val rs = frame.worldPoint(PoseLandmarks.RIGHT_SHOULDER) ?: return false
        if (minOf(nose.visibility, ls.visibility, rs.visibility) < Thresholds.MIN_VIS) return false
        val axis = bodyLateralAxis(frame) ?: return false
        val dx = nose.x - (ls.x + rs.x) / 2f
        val dz = nose.z - (ls.z + rs.z) / 2f
        return lateralComp(dx, dz, axis) / shoulderW > Thresholds.HEAD_OFFLINE
    }

    /** (3D) 스탠스 좁음 — 발목 수평(xz) 간격이 어깨폭 대비 좁음. (발목 안 보이면 스킵) */
    private fun isStanceNarrow(frame: PoseFrame, shoulderW: Float): Boolean {
        val la = frame.worldPoint(PoseLandmarks.LEFT_ANKLE) ?: return false
        val ra = frame.worldPoint(PoseLandmarks.RIGHT_ANKLE) ?: return false
        if (minOf(la.visibility, ra.visibility) < Thresholds.MIN_VIS) return false
        return hypot(la.x - ra.x, la.z - ra.z) / shoulderW < Thresholds.STANCE_NARROW
    }

    /** (3D) 무릎 뻣뻣 — 양 무릎 3D 각도(엉덩이-무릎-발목)가 거의 직선. (다리 안 보이면 스킵) */
    private fun areKneesStraight(frame: PoseFrame): Boolean {
        val l = frame.angle3Deg(PoseLandmarks.LEFT_HIP, PoseLandmarks.LEFT_KNEE, PoseLandmarks.LEFT_ANKLE)
        val r = frame.angle3Deg(PoseLandmarks.RIGHT_HIP, PoseLandmarks.RIGHT_KNEE, PoseLandmarks.RIGHT_ANKLE)
        if (l == null || r == null) return false
        // 무릎 신뢰도 확인.
        val lk = frame.worldPoint(PoseLandmarks.LEFT_KNEE)?.visibility ?: 0f
        val rk = frame.worldPoint(PoseLandmarks.RIGHT_KNEE)?.visibility ?: 0f
        if (minOf(lk, rk) < Thresholds.MIN_VIS) return false
        return l >= Thresholds.KNEE_STRAIGHT && r >= Thresholds.KNEE_STRAIGHT
    }
}
