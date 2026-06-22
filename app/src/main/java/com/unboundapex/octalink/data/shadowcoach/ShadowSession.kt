package com.unboundapex.octalink.data.shadowcoach

/**
 * 실시간 자세 체크 항목 — 기획서 "자세 평가 엔진" 의 항목을 MVP 범위로 축소.
 * 각 항목은 한 프레임에서 위반(issue) 여부를 휴리스틱으로 판정 → 실시간 피드백 + 감점.
 */
enum class PostureCheck(
    val displayName: String,
    /** 위반 시 실시간으로 띄울 짧은 코칭 문구. */
    val cue: String,
    val enabledInMvp: Boolean,
) {
    GUARD_DOWN("가드", "가드를 올리세요", enabledInMvp = true),
    CHIN_UP("턱", "턱을 당기세요", enabledInMvp = true),
    POOR_EXTENSION("팔 신전", "팔을 끝까지 뻗으세요", enabledInMvp = true),
    POOR_HIP_ROTATION("골반 회전", "골반 회전을 더 주세요", enabledInMvp = false),
    BALANCE_LOSS("중심", "중심을 잡으세요", enabledInMvp = false),
    ;

    companion object {
        val mvpEnabled: List<PostureCheck> get() = entries.filter { it.enabledInMvp }
    }
}

/**
 * 한 번의 쉐도우 세션 누적 결과. 세션 종료 시 요약 카드 + (이후 단계) Gemini 코칭 리포트 입력.
 *
 * 점수 체계: 자세 항목별 위반 비율로 100점 만점 환산 (위반 프레임 비율이 낮을수록 고득점).
 * MVP 는 단순 평균, 이후 단계에서 항목 가중치/구간 분석으로 정교화.
 */
data class ShadowSession(
    /** 기술별 감지 횟수 (예: JAB → 42). */
    val techniqueCounts: Map<Technique, Int> = emptyMap(),
    /** 자세 항목별 (위반 프레임 수, 평가된 전체 프레임 수). */
    val postureSamples: Map<PostureCheck, PostureSample> = emptyMap(),
    /** 세션 길이 (ms). */
    val durationMs: Long = 0L,
    /** 분석된 총 프레임 수 (포즈가 잡힌 프레임). */
    val analyzedFrames: Int = 0,
) {
    val totalStrikes: Int get() = techniqueCounts.values.sum()

    /** 항목별 준수율(0~100). 평가 프레임이 없으면 null. */
    fun compliancePercent(check: PostureCheck): Int? {
        val s = postureSamples[check] ?: return null
        if (s.evaluated == 0) return null
        val ok = s.evaluated - s.violations
        return (ok * 100f / s.evaluated).toInt().coerceIn(0, 100)
    }

    /** 100점 만점 종합 점수 — MVP 는 평가 가능한 자세 항목 준수율의 단순 평균. */
    fun overallScore(): Int {
        val vals = PostureCheck.mvpEnabled.mapNotNull { compliancePercent(it) }
        if (vals.isEmpty()) return 0
        return (vals.sum() / vals.size).coerceIn(0, 100)
    }
}

/** 한 자세 항목의 누적 표본. */
data class PostureSample(
    val violations: Int = 0,
    val evaluated: Int = 0,
) {
    fun record(violated: Boolean): PostureSample =
        PostureSample(violations + if (violated) 1 else 0, evaluated + 1)
}
