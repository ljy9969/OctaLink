package com.unboundapex.octalink.data.shadowcoach

/**
 * Shadow Coach 가 인식하는 타격 기술. MVP 는 [JAB] 만 감지, 이후 단계에서 확장.
 *
 * 인식은 온디바이스 [PoseLandmarker] 출력(33개 관절 좌표)에 대한 **규칙 기반 휴리스틱** —
 * 각 기술의 판정 기준(손목 속도 / 팔 신전각 / 어깨·골반 회전 등)을 관절 기하로 계산.
 * (기획서의 "1,000~5,000 샘플 학습 모델"은 Phase 2+ 과제, MVP 는 휴리스틱.)
 */
enum class Technique(
    val displayName: String,
    val enabledInMvp: Boolean,
) {
    JAB("잽", enabledInMvp = true),
    CROSS("스트레이트", enabledInMvp = false),
    LEAD_HOOK("훅", enabledInMvp = false),
    LOW_KICK("로우킥", enabledInMvp = false),
    ;

    companion object {
        /** MVP 에서 실제로 감지/카운트하는 기술 목록. */
        val mvpEnabled: List<Technique> get() = entries.filter { it.enabledInMvp }
    }
}
