package com.unboundapex.octalink.data

import com.unboundapex.octalink.data.schema.isCreator
import com.unboundapex.octalink.data.session.SessionState

/**
 * AI 코치의 맞춤 루틴 (Phase 1 비공개 베타) 접근 게이트.
 *
 *  - CREATOR : 항상 통과 (역할 기반).
 *  - 회원: `MemberDoc.aiRoutineBeta == true` 인 경우만 통과.
 *
 * `aiRoutineBeta` 필드는 `completeSignup` Cloud Function 이 가입 시점에 자동 부여 —
 * 현재 grants 수가 12 미만이면 새 회원에게 true. 선착순 12명 도달 후 신규 회원은 false.
 *
 * Firestore rules `canUseAiRoutine()` + Cloud Function `generateWeeklyRoutine` 도 동일 게이트.
 */
fun SessionState.canUseAiRoutine(): Boolean =
    role.isCreator || (member?.aiRoutineBeta == true)
