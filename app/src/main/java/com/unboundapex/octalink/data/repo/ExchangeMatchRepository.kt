package com.unboundapex.octalink.data.repo

import com.unboundapex.octalink.data.schema.ExchangeMatchDoc
import kotlinx.coroutines.flow.Flow

/**
 * 교류전(결투) 저장소 — `exchangeMatches/{id}`.
 * 생성·전이는 모두 서버 함수(requestDuel/approveDuel/…) 호출 위임 (rules 가 클라 직접 쓰기 차단).
 * 조회만 Firestore 직접 (양쪽 체육관 APPROVED 회원 read 허용).
 */
interface ExchangeMatchRepository {
    /** 내가 참가자(요청/상대)인 교류전 목록. */
    fun observeMyDuels(memberId: String): Flow<List<ExchangeMatchDoc>>

    /** 우리 체육관이 관련된 교류전 목록 (운영진 승인/일정/결과용). */
    fun observeGymDuels(gymId: String): Flow<List<ExchangeMatchDoc>>

    suspend fun requestDuel(opponentMemberId: String)
    suspend fun approveDuel(matchId: String)
    suspend fun rejectDuel(matchId: String)
    suspend fun proposeDuelSlots(matchId: String, slots: List<String>)
    suspend fun scheduleDuel(matchId: String, date: String, time: String, place: String)
    suspend fun recordResult(matchId: String, winnerMemberId: String?, isDraw: Boolean)
}
