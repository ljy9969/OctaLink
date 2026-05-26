package com.unboundapex.octalink.data

import com.unboundapex.octalink.data.schema.isCreator
import com.unboundapex.octalink.data.session.SessionState

/**
 * AI 코치의 맞춤 루틴 (Phase 1 베타) 접근 허용 회원 uid 셋.
 *
 * 비용 통제 + 추천 품질 검증 단계라 일반 회원은 진입 차단. 화이트리스트에 명시된 uid 와
 * `CREATOR` 역할만 통과. Firestore rules / Cloud Function 의 화이트리스트와 동일하게 유지 필요.
 *
 * 정식 오픈 시 이 set 비우고 [SessionState.canUseAiRoutine] 의 조건도 단순화.
 */
internal val AI_ROUTINE_BETA_UIDS: Set<String> = setOf(
    "kakao:4892939648",
)

/** Home 카드 노출 + 상세 화면 진입 + EmptyRoutineCard CTA 표시 게이트. */
fun SessionState.canUseAiRoutine(): Boolean =
    role.isCreator || (member?.id in AI_ROUTINE_BETA_UIDS)
