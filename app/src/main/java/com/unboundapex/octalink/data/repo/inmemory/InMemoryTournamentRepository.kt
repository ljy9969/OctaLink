package com.unboundapex.octalink.data.repo.inmemory

import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.repo.TournamentRepository
import com.unboundapex.octalink.data.schema.MatchDoc
import com.unboundapex.octalink.data.schema.TournamentDoc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/**
 * 토너먼트 in-memory 저장소. Phase 1 토글 시 사용. 시드는 비워둠 —
 * 추첨 시점에 [create] 호출로 생성되는 게 정상 흐름이므로 미리 만들어둘 도큐먼트가 없음.
 *
 * 매치는 tournamentId 키로 같은 in-memory 맵에 보관. ViewModel 이 두 flow 를 zip 해서 사용.
 */
class InMemoryTournamentRepository : TournamentRepository {
    private val _tournaments = MutableStateFlow<List<TournamentDoc>>(emptyList())
    private val _matches = MutableStateFlow<List<MatchDoc>>(emptyList())

    override fun observeAll(): Flow<List<TournamentDoc>> =
        _tournaments.map { list -> list.sortedByDescending { it.drawAt } }

    override fun observeById(tournamentId: String): Flow<TournamentDoc?> =
        _tournaments.map { list -> list.firstOrNull { it.id == tournamentId } }

    override fun observeMatches(tournamentId: String): Flow<List<MatchDoc>> =
        _matches.map { list ->
            list.filter { it.tournamentId == tournamentId }
                .sortedWith(compareBy({ it.round.ordinal }, { it.slotIndex }))
        }

    override suspend fun create(
        title: String,
        weightClass: WeightClass?,
        beltGroup: Belt?,
        matches: List<MatchDoc>,
    ): TournamentDoc {
        val tournamentId = UUID.randomUUID().toString()
        val drawAt = Instant.now()
        val doc = TournamentDoc(
            id = tournamentId,
            title = title,
            weightClass = weightClass,
            beltGroup = beltGroup,
            drawAt = drawAt,
        )
        val seededMatches = matches.map { m ->
            m.copy(
                id = m.id.ifEmpty { UUID.randomUUID().toString() },
                tournamentId = tournamentId,
            )
        }
        _tournaments.value = _tournaments.value + doc
        _matches.value = _matches.value + seededMatches
        return doc
    }

    override suspend fun setMatchWinner(
        tournamentId: String,
        matchId: String,
        winnerMemberId: String,
        byMasterId: String,
    ) {
        val now = Instant.now()
        _matches.value = _matches.value.map { m ->
            if (m.tournamentId == tournamentId && m.id == matchId) {
                m.copy(
                    winnerMemberId = winnerMemberId,
                    resolvedAt = now,
                    resolvedByMasterId = byMasterId,
                )
            } else m
        }
    }

    override suspend fun setMatchSlots(
        tournamentId: String,
        matchId: String,
        redMemberId: String?,
        blueMemberId: String?,
        resetWinner: Boolean,
    ) {
        _matches.value = _matches.value.map { m ->
            if (m.tournamentId == tournamentId && m.id == matchId) {
                m.copy(
                    redMemberId = redMemberId,
                    blueMemberId = blueMemberId,
                    winnerMemberId = if (resetWinner) null else m.winnerMemberId,
                    resolvedAt = if (resetWinner) null else m.resolvedAt,
                    resolvedByMasterId = if (resetWinner) null else m.resolvedByMasterId,
                )
            } else m
        }
    }

    override suspend fun finish(tournamentId: String, championMemberId: String) {
        val now = Instant.now()
        _tournaments.value = _tournaments.value.map { t ->
            if (t.id == tournamentId) t.copy(finishedAt = now, champion = championMemberId) else t
        }
    }

    override suspend fun delete(tournamentId: String) {
        _tournaments.value = _tournaments.value.filterNot { it.id == tournamentId }
        _matches.value = _matches.value.filterNot { it.tournamentId == tournamentId }
    }
}
