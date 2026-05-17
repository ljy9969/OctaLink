package com.unboundapex.octalink.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.MatchDoc
import com.unboundapex.octalink.data.schema.TournamentDoc
import com.unboundapex.octalink.data.schema.TournamentRound
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 본인의 완료된 토너먼트 누적 전적.
 *
 * 진행 중(`finishedAt == null`) 토너먼트는 집계 제외 — 결과 미확정. 챔피언이 결정된
 * 토너먼트만 카운트해서 "N전 X우승 Y준우승" 의 의미가 명확.
 */
data class TournamentRecord(
    val participated: Int = 0,
    val wins: Int = 0,
    val runnerUps: Int = 0,
)

/**
 * ProfileScreen 의 "토너먼트 전적" 카드 데이터 소스.
 *
 * 완료된 토너먼트만 집계:
 *  - 참가 = 해당 토너먼트의 매치 중 본인 memberId 가 red/blue 슬롯에 등장
 *  - 우승 = [TournamentDoc.champion] == 본인 memberId
 *  - 준우승 = 우승 아님 AND 결승(FINAL) 매치에 본인이 들어가 있음 (= 결승 패자)
 *
 * 도장 규모 가정상 토너먼트 수 N 은 작아서 N+1 listener (observeAll + 각 observeMatches)
 * 는 무시 가능. 늘어나면 finished 만 별도 인덱스 / 캐시 도입 검토.
 */
class MyTournamentRecordViewModel : ViewModel() {
    private val tournamentsRepo = RepositoryProvider.tournaments

    private val _memberId = MutableStateFlow<String?>(null)
    fun observeForMember(memberId: String?) { _memberId.value = memberId }

    @OptIn(ExperimentalCoroutinesApi::class)
    val record: StateFlow<TournamentRecord> = _memberId
        .flatMapLatest { id ->
            if (id == null) flowOf(TournamentRecord())
            else tournamentsRepo.observeAll()
                .flatMapLatest { tournaments ->
                    val finished = tournaments.filter { it.finishedAt != null }
                    if (finished.isEmpty()) flowOf(TournamentRecord())
                    else combine(
                        finished.map { t ->
                            tournamentsRepo.observeMatches(t.id).map { ms -> t to ms }
                        }
                    ) { pairs: Array<Pair<TournamentDoc, List<MatchDoc>>> ->
                        buildRecord(id, pairs.toList())
                    }
                }
        }
        .catch { e ->
            android.util.Log.e("OctaLink.MyTournamentRecord", "record flow error", e)
            emit(TournamentRecord())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TournamentRecord())

    private fun buildRecord(
        memberId: String,
        items: List<Pair<TournamentDoc, List<MatchDoc>>>,
    ): TournamentRecord {
        var participated = 0
        var wins = 0
        var runnerUps = 0
        items.forEach { (t, ms) ->
            val participantIds: Set<String> = ms.asSequence()
                .flatMap { sequenceOf(it.redMemberId, it.blueMemberId) }
                .filterNotNull()
                .toSet()
            if (memberId !in participantIds) return@forEach
            participated++
            if (t.champion == memberId) {
                wins++
            } else {
                val finalMatch = ms.firstOrNull { it.round == TournamentRound.FINAL }
                if (finalMatch != null &&
                    (finalMatch.redMemberId == memberId || finalMatch.blueMemberId == memberId)
                ) {
                    runnerUps++
                }
            }
        }
        return TournamentRecord(participated, wins, runnerUps)
    }
}
