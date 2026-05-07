package com.teamposse.striking.data.tournament

import androidx.lifecycle.ViewModel
import com.teamposse.striking.data.Belt
import com.teamposse.striking.data.Match
import com.teamposse.striking.data.Member
import com.teamposse.striking.data.WeightClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 토너먼트 한 회차의 전체 상태. 메모리 전용.
 * 추후 Firestore `tournaments/{id}` + matches 서브컬렉션 매핑.
 */
data class TournamentUiState(
    val initialized: Boolean = false,
    val round1: List<Match> = emptyList(),
    val round2: List<Match> = emptyList(),
    val final: Match = Match("?", "?"),
    val weightClass: WeightClass? = null,
    val beltGroup: Belt? = null,
)

class TournamentViewModel : ViewModel() {
    private val _state = MutableStateFlow(TournamentUiState())
    val state: StateFlow<TournamentUiState> = _state.asStateFlow()

    private val membersByName = mutableMapOf<String, Member>()

    fun beltOf(name: String): Belt? = membersByName[name]?.belt

    /**
     * 회원 풀에서 추첨. 인원수에 따라 시작 라운드 자동 결정.
     * - 2명: 결승 직행
     * - 3~4명: 4강 시작 (round2)
     * - 5~8명: 8강 시작 (round1, 부족분 부전승)
     */
    fun draw(members: List<Member>, weightClass: WeightClass?, beltGroup: Belt?) {
        membersByName.clear()
        members.forEach { membersByName[it.name] = it }

        val pool = members.shuffled()
        val n = pool.size
        val r1 = mutableListOf<Match>()
        val r2 = mutableListOf<Match>()
        var fin = Match("?", "?")

        when {
            n <= 1 -> Unit
            n == 2 -> fin = Match(pool[0].name, pool[1].name)
            n in 3..4 -> {
                val padded = pool.map { it.name } + List(4 - n) { "?" }
                r2.addAll(padded.chunked(2).map { Match(it[0], it[1]) })
            }
            else -> {
                val capped = pool.take(8)
                val padded = capped.map { it.name } + List(8 - capped.size) { "?" }
                r1.addAll(padded.chunked(2).map { Match(it[0], it[1]) })
                r2.addAll(List(2) { Match("?", "?") })
            }
        }

        _state.value = TournamentUiState(
            initialized = true,
            round1 = r1,
            round2 = r2,
            final = fin,
            weightClass = weightClass,
            beltGroup = beltGroup,
        )
        autoResolveByes()
    }

    fun reset() {
        membersByName.clear()
        _state.value = TournamentUiState()
    }

    /** 8강 매치의 승자 지정. 자동으로 4강 슬롯에 진출. 다운스트림 winner 자동 리셋. */
    fun setRound1Winner(matchIdx: Int, winner: String) {
        _state.update { s ->
            val r1 = s.round1.toMutableList()
            r1[matchIdx] = r1[matchIdx].copy(winner = winner)

            val r2idx = matchIdx / 2
            val isFirstOfPair = matchIdx % 2 == 0
            val r2 = s.round2.toMutableList()
            val current = r2[r2idx]
            r2[r2idx] = if (isFirstOfPair) current.copy(red = winner, winner = null)
            else current.copy(blue = winner, winner = null)

            // round2 winner가 있었다면 final 슬롯도 리셋
            val newFinal = if (current.winner != null) {
                if (r2idx == 0) s.final.copy(red = "?", winner = null)
                else s.final.copy(blue = "?", winner = null)
            } else s.final

            s.copy(round1 = r1, round2 = r2, final = newFinal)
        }
        // 짝꿍 round1 매치가 더블 바이라면 이 시점에 round2 슬롯도 부전승 advance
        propagateRound2Bye(matchIdx / 2)
    }

    /** 4강 매치의 승자 지정. 자동으로 결승 슬롯에 진출. 다운스트림 final winner 리셋. */
    fun setRound2Winner(matchIdx: Int, winner: String) {
        _state.update { s ->
            val r2 = s.round2.toMutableList()
            r2[matchIdx] = r2[matchIdx].copy(winner = winner)
            val newFinal = if (matchIdx == 0) s.final.copy(red = winner, winner = null)
            else s.final.copy(blue = winner, winner = null)
            s.copy(round2 = r2, final = newFinal)
        }
    }

    fun setFinalWinner(winner: String) {
        _state.update { s -> s.copy(final = s.final.copy(winner = winner)) }
    }

    /**
     * 부전승 자동 처리: 한쪽이 "?"인 매치 + 상위 매치가 모두 결착된 상태일 때만 자동 진출.
     * 상위 round1 매치가 진짜 경기(둘 다 실명)인데 미결정이면 round2의 "?"는 부전승이 아니라 "대기 중".
     */
    private fun autoResolveByes() {
        val initialR1 = _state.value.round1
        initialR1.forEachIndexed { idx, m ->
            if (m.winner != null) return@forEachIndexed
            val redIsBye = m.red == "?"
            val blueIsBye = m.blue == "?"
            if (redIsBye != blueIsBye) {
                val winner = if (redIsBye) m.blue else m.red
                if (winner != "?") setRound1Winner(idx, winner)
            }
        }
        _state.value.round2.indices.forEach { idx -> propagateRound2Bye(idx) }
    }

    private fun propagateRound2Bye(idx: Int) {
        val s = _state.value
        val m = s.round2.getOrNull(idx) ?: return
        if (m.winner != null) return

        val upTop = s.round1.getOrNull(idx * 2)
        val upBot = s.round1.getOrNull(idx * 2 + 1)
        val topResolved = upTop == null ||
            upTop.winner != null ||
            (upTop.red == "?" && upTop.blue == "?")
        val botResolved = upBot == null ||
            upBot.winner != null ||
            (upBot.red == "?" && upBot.blue == "?")
        if (!topResolved || !botResolved) return

        val redIsBye = m.red == "?"
        val blueIsBye = m.blue == "?"
        if (redIsBye != blueIsBye) {
            val winner = if (redIsBye) m.blue else m.red
            if (winner != "?") setRound2Winner(idx, winner)
        }
    }
}
