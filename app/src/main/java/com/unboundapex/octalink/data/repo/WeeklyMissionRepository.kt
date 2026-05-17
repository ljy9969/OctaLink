package com.unboundapex.octalink.data.repo

import com.unboundapex.octalink.data.schema.WeeklyMissionDoc
import kotlinx.coroutines.flow.Flow

/**
 * 도장 주간 미션 ([WeeklyMissionDoc]) 영속화 추상화.
 *
 * Firestore 경로: `gymSettings/weeklyMission` — 싱글톤 doc.
 *  - read: 모든 APPROVED (회원 누구나 홈 카드에서 봄)
 *  - write: 운영진 (isStaff) — Firestore rules 강제
 *
 * doc 미존재 시 [observe] 는 `null` 방출 → UI 는 default placeholder 표시.
 */
interface WeeklyMissionRepository {
    /** 주간 미션 doc 실시간 구독. 미설정 시 null. */
    fun observe(): Flow<WeeklyMissionDoc?>

    /**
     * 주간 미션 갱신 — text/updatedAt/updatedByName 전체 덮어쓰기 (upsert).
     * 권한 검증은 Firestore rules.
     */
    suspend fun update(text: String, byMemberName: String)
}
