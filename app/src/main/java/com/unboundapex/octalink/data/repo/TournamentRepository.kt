package com.unboundapex.octalink.data.repo

import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.schema.MatchDoc
import com.unboundapex.octalink.data.schema.TournamentDoc
import kotlinx.coroutines.flow.Flow

/**
 * 토너먼트 ([TournamentDoc] + [MatchDoc]) 영속화 추상화.
 *
 * Firestore 경로:
 *   - `tournaments/{tournamentId}`              상위 컨테이너
 *   - `tournaments/{tournamentId}/matches/{matchId}` 매치 서브컬렉션
 *
 * 권한 (Security Rules):
 *   - read: APPROVED 회원 누구나 (대진표 가시 필요)
 *   - create/update/delete: 운영진(`isStaff`) — 추첨/대진 진행/정정
 *
 * 추첨 시 매치 슬롯은 한꺼번에 [create] 로 batch 작성.
 * 대진 진행은 [setMatchWinner] 로 매치 한 건씩 갱신 (상위 라운드 진출은 클라이언트가 계산 후 추가 호출).
 */
interface TournamentRepository {
    /** 토너먼트 목록 — drawAt DESC (최신 추첨 먼저). 히스토리 화면 / 진행 중 목록 공용. */
    fun observeAll(): Flow<List<TournamentDoc>>

    /** 단일 토너먼트 메타. null 이면 삭제됨. */
    fun observeById(tournamentId: String): Flow<TournamentDoc?>

    /** 토너먼트 내 매치들 — slotIndex ASC, round 는 enum ordinal 순서. */
    fun observeMatches(tournamentId: String): Flow<List<MatchDoc>>

    /**
     * 추첨 결과 영속화 — 토너먼트 doc + 초기 매치들을 한 batch 로 작성 (부분 실패 방지).
     * id 는 Firestore 자동 생성 (tournamentId, 각 matchId).
     *
     * @param matches 초기 라운드 슬롯. tournamentId 는 호출 시점엔 비어있어도 됨 — 구현에서 채워줌.
     *                slotIndex, round, redMemberId, blueMemberId 만 의미 있음. winnerMemberId 는 부전승 처리 시만.
     * @return 생성된 [TournamentDoc] (서버 timestamp 반영).
     */
    suspend fun create(
        title: String,
        weightClass: WeightClass?,
        beltGroup: Belt?,
        matches: List<MatchDoc>,
    ): TournamentDoc

    /**
     * 매치 결과 기록. resolvedAt 은 서버 timestamp.
     * 상위 라운드 슬롯 자동 채우기는 클라이언트(ViewModel) 책임 — 별도 호출.
     */
    suspend fun setMatchWinner(
        tournamentId: String,
        matchId: String,
        winnerMemberId: String,
        byMasterId: String,
    )

    /**
     * 매치의 red/blue 슬롯 갱신 — 상위 라운드 슬롯에 승자를 채울 때 사용.
     * winnerMemberId 가 이미 있으면 결과 리셋 (다운스트림 재계산 시).
     */
    suspend fun setMatchSlots(
        tournamentId: String,
        matchId: String,
        redMemberId: String?,
        blueMemberId: String?,
        resetWinner: Boolean = false,
    )

    /** 토너먼트 종료 — 우승자 확정 + finishedAt 기록. 이후 히스토리 화면 노출. */
    suspend fun finish(
        tournamentId: String,
        championMemberId: String,
    )

    /** 토너먼트 + 매치 서브컬렉션 일괄 삭제. 운영진만. */
    suspend fun delete(tournamentId: String)
}
