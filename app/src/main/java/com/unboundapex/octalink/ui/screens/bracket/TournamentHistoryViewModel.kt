package com.unboundapex.octalink.ui.screens.bracket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.MatchDoc
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.TournamentDoc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 토너먼트 한 건의 히스토리 카드용 view-data — TournamentDoc 단독으로는 부족한
 * 우승자 이름·벨트, 참가자 수, 본인 참가 여부를 미리 조인해 둠.
 */
data class TournamentHistoryItem(
    val tournament: TournamentDoc,
    val championName: String?,
    val championBelt: Belt?,
    val participantCount: Int,
    val isMyParticipation: Boolean,
)

/**
 * 토너먼트 히스토리 화면 데이터 소스.
 *
 * 전체 토너먼트 list × 각각의 matches × members 풀을 한 번의 combine 으로 묶어
 * [items] 단일 StateFlow 로 노출. listener 수는 N(=토너먼트 수)+2 — 작은 도장 가정
 * (수십 회 이하) 으로 페이지네이션 불필요.
 *
 * 본인 참가 여부는 [observeForMember] 로 set 된 memberId 기준. session 의 member.id 가
 * 바뀌면 (로그인/탈퇴) UI 가 재호출.
 */
class TournamentHistoryViewModel : ViewModel() {
    private val tournamentsRepo = RepositoryProvider.tournaments
    private val membersRepo = RepositoryProvider.members

    private val _myMemberId = MutableStateFlow<String?>(null)
    fun observeForMember(memberId: String?) { _myMemberId.value = memberId }

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<TournamentHistoryItem>> =
        combine(
            tournamentsRepo.observeAll(),
            membersRepo.observeAll().map { list -> list.associateBy { it.id } },
            _myMemberId,
        ) { tournaments, membersById, myId -> Triple(tournaments, membersById, myId) }
            .flatMapLatest { (tournaments, membersById, myId) ->
                if (tournaments.isEmpty()) flowOf(emptyList())
                else combine(
                    tournaments.map { t ->
                        tournamentsRepo.observeMatches(t.id).map { ms -> t to ms }
                    }
                ) { pairs: Array<Pair<TournamentDoc, List<MatchDoc>>> ->
                    pairs.map { (t, ms) -> buildItem(t, ms, membersById, myId) }
                }
            }
            .catch { e ->
                android.util.Log.e("OctaLink.TournamentHistory", "items flow error", e)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 히스토리 카드별 명시적 삭제 — 완료/미완료 무관 단발 삭제. 관장만 가능 (Rules 강제).
     * 카드 위의 confirm 다이얼로그를 사용자가 통과한 뒤 호출.
     * `tournamentsRepo.delete` 는 writeBatch — 매치 전체 + tournament doc 동시 제거.
     */
    fun delete(tournamentId: String) {
        viewModelScope.launch {
            runCatching { tournamentsRepo.delete(tournamentId) }
                .onFailure {
                    android.util.Log.e("OctaLink.TournamentHistory", "delete FAILED: $tournamentId", it)
                }
        }
    }

    private fun buildItem(
        tournament: TournamentDoc,
        matches: List<MatchDoc>,
        membersById: Map<String, MemberDoc>,
        myId: String?,
    ): TournamentHistoryItem {
        // 참가자 = 모든 매치의 red/blue 에서 등장한 distinct memberId 셋.
        // 부전승 슬롯(null) 은 자연 제외. 결승만 있는 토너먼트(2명)도 정확히 2명 카운트.
        val participantIds: Set<String> = matches.asSequence()
            .flatMap { sequenceOf(it.redMemberId, it.blueMemberId) }
            .filterNotNull()
            .toSet()
        val championMember = tournament.champion?.let { membersById[it] }
        return TournamentHistoryItem(
            tournament = tournament,
            championName = championMember?.name,
            championBelt = championMember?.belt,
            participantCount = participantIds.size,
            isMyParticipation = myId != null && myId in participantIds,
        )
    }
}
