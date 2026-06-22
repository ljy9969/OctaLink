package com.unboundapex.octalink.data.shadowcoach

/**
 * Shadow Coach 가 인식하는 동작.
 *
 * 인식은 온디바이스 [PoseLandmarker] 출력(33개 관절 좌표)에 대한 **궤적·각도 휴리스틱** —
 * 손목 궤적(나갔다 돌아옴) + 팔꿈치 신전각 + 손목 상승/수평 이동으로 펀치 종류를 분류.
 *
 * 좌/우 구분: 정파(오른손잡이) 가정 — 왼팔 스트레이트 = [JAB], 오른팔 스트레이트 = [STRAIGHT](라이트).
 * 훅/어퍼는 좌·우 공통 카운트. 2D 한계로 오분류가 있을 수 있어 임계값은 실기기 튜닝 대상.
 *
 * 더킹/스웨이/위빙(머리·몸통 회피)은 펀치와 다른 검출 체계라 후속 단계.
 */
enum class Technique(
    val displayName: String,
    val enabledInMvp: Boolean,
) {
    JAB("잽", enabledInMvp = true),
    STRAIGHT("라이트", enabledInMvp = true),
    HOOK("훅", enabledInMvp = true),
    UPPERCUT("어퍼", enabledInMvp = true),
    LOW_KICK("로우킥", enabledInMvp = false),
    ;

    companion object {
        /** MVP 에서 실제로 감지/카운트하는 동작 목록 (표시 순서). */
        val mvpEnabled: List<Technique> get() = entries.filter { it.enabledInMvp }
    }
}
