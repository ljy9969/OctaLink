package com.unboundapex.octalink.data.repo

import com.unboundapex.octalink.data.schema.WeeklyRoutineDoc
import kotlinx.coroutines.flow.Flow

/**
 * AI 가 생성한 주간 보강 루틴 영속화 추상화.
 *
 * Firestore 경로: `members/{memberId}/recommendations/{weekId}` —
 * doc id 는 ISO 주키 ("2026-W22"). Cloud Function `generateWeeklyRoutine` 이 doc 을
 * 만들고 클라이언트는 read + observe + manual trigger.
 */
interface WeeklyRoutineRepository {
    /** 이번 주 (KST 기준) 루틴 doc 실시간 구독. 미생성 시 null. */
    fun observeCurrentWeek(memberId: String): Flow<WeeklyRoutineDoc?>

    /**
     * Cloud Function `generateWeeklyRoutine` 호출 — 캐시가 있으면 그대로,
     * `force = true` 면 재생성. Firestore 에 결과 저장 → observe 가 후속 갱신.
     */
    suspend fun generate(memberId: String, force: Boolean = false)

    /** 드릴 done/skipped 상태 토글 — Phase 2 피드백 루프용. */
    suspend fun setDrillState(
        memberId: String,
        weekId: String,
        dayIndex: Int,
        drillIndex: Int,
        done: Boolean,
        skipped: Boolean,
    )
}
