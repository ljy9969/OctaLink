package com.unboundapex.octalink.data.tournament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.Match
import com.unboundapex.octalink.data.Member
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.MatchDoc
import com.unboundapex.octalink.data.schema.MemberDoc
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
import kotlinx.coroutines.launch

/**
 * 토너먼트 한 회차의 전체 상태. UI 가 collectAsState() 로 구독.
 *
 * 영속화: `tournaments/{tournamentId}` + `tournaments/{tournamentId}/matches/{matchId}` 서브컬렉션.
 * 이 ViewModel 은 한 시점에 *하나의 토너먼트* 만 활성 — `_currentTournamentId` 가 결정.
 * 초기 active 는 [TournamentRepository.observeAll] 의 최신 doc (drawAt DESC, take first).
 */
data class TournamentUiState(
    val initialized: Boolean = false,
    val round1: List<Match> = emptyList(),
    val round2: List<Match> = emptyList(),
    val final: Match = Match("?", "?"),
    val weightClass: WeightClass? = null,
    val beltGroup: Belt? = null,
    val finished: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TournamentViewModel : ViewModel() {
    private val tournamentsRepo = RepositoryProvider.tournaments
    private val membersRepo = RepositoryProvider.members

    /**
     * 활성 토너먼트 id. null = 아직 추첨 안 됨(초기 화면) 또는 [reset] 호출 직후.
     * 앱 시작 시 [observeAll] 의 최신 doc 으로 자동 채워짐.
     */
    private val _currentTournamentId = MutableStateFlow<String?>(null)

    /**
     * 화면이 특정 토너먼트로 진입했을 때(예: 히스토리에서 특정 카드 클릭) override.
     * non-null 이면 init 의 "최신 자동 선택" 로직을 우회하고 이 id 가 강제 사용됨.
     * null 로 재설정하면 다시 자동 선택 모드로 복귀.
     */
    private val _overrideTournamentId = MutableStateFlow<String?>(null)

    /**
     * observeAll 의 최신 list 캐시. [selectTournament] / [reset] 에서 즉시 [pickActive] 재계산용.
     * (observeAll 의 다음 emit 을 기다리지 않고 동기적으로 결정 가능.)
     */
    private val _allTournaments = MutableStateFlow<List<TournamentDoc>>(emptyList())

    /**
     * 특정 토너먼트로 강제 진입. 히스토리에서 카드 탭 시 호출.
     * null 전달 시 override 해제 + active(latest unfinished) 로 복귀.
     */
    fun selectTournament(tournamentId: String?) {
        _overrideTournamentId.value = tournamentId
        if (tournamentId != null) {
            _currentTournamentId.value = tournamentId
        } else {
            // 히스토리 → 활성 보드 복귀. 캐시된 list 로 동기 결정.
            _currentTournamentId.value = pickActive(_allTournaments.value)
        }
    }

    /**
     * 자동 active 선택 규칙.
     *  1) 진행중(finishedAt == null) 토너먼트가 있으면 그게 active.
     *  2) 없으면 가장 최근 완료된 토너먼트 (히스토리에 남아있어도 챔피언 카드 / 트리 노출 — 다음 추첨 전까지).
     *  3) 토너먼트 자체가 없으면 null → EmptyState.
     *
     * observeAll 정렬은 drawAt DESC 가정 — firstOrNull 이 곧 최신.
     */
    private fun pickActive(tournaments: List<TournamentDoc>): String? {
        tournaments.firstOrNull { it.finishedAt == null }?.let { return it.id }
        return tournaments.firstOrNull()?.id
    }

    /**
     * 매치 결과 기록 시 `resolvedByMasterId` 로 들어갈 작성자 uid. UI 가 세션에서 받아 주입.
     * null 이면 빈 문자열로 저장 — 룰 검증에 영향 없는 단순 audit 필드.
     */
    @Volatile
    var currentUserId: String? = null

    /** 회원 id → MemberDoc 매핑. UI Match 는 name 기반이라 MatchDoc 의 memberId 와 양방향 변환에 사용. */
    private val membersById: StateFlow<Map<String, MemberDoc>> =
        membersRepo.observeAll()
            .map { list -> list.associateBy { it.id } }
            .catch { e ->
                android.util.Log.e("OctaLink.Tournament", "members flow error", e)
                emit(emptyMap())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * 활성 토너먼트의 raw 데이터 — [observeById] + [observeMatches] 단일 collector 가 populate.
     * UI 의 `state` 와 매치 갱신 메서드의 `(round, slotIndex)` → matchId 매핑 양쪽이 같은 source 공유.
     * `state` 가 [SharingStarted.WhileSubscribed] 라 UI 비활성 시 stop 되더라도 이 raw 캐시는 따로 안 멈춤.
     */
    private val _tournament = MutableStateFlow<TournamentDoc?>(null)
    private val _matches = MutableStateFlow<List<MatchDoc>>(emptyList())

    init {
        // 앱 시작 시 active 토너먼트 선택 — finishedAt == null 중 최신 (= [pickActive]).
        // 외부에서 doc 이 삭제되면(콘솔 등) currentTournamentId 도 자동 클리어 + 재선택.
        viewModelScope.launch {
            tournamentsRepo.observeAll()
                .catch { e ->
                    android.util.Log.e("OctaLink.Tournament", "observeAll error", e)
                    emit(emptyList())
                }
                .collect { tournaments ->
                    _allTournaments.value = tournaments
                    val override = _overrideTournamentId.value
                    val curId = _currentTournamentId.value
                    when {
                        // 화면이 특정 토너먼트(히스토리 진입 등) 로 진입한 상태 — 자동 선택 우회.
                        override != null -> {
                            // 외부에서 삭제된 경우만 정리 → active 자동 선택으로 복귀.
                            if (tournaments.none { it.id == override }) {
                                _overrideTournamentId.value = null
                                _currentTournamentId.value = pickActive(tournaments)
                            }
                        }
                        // 초기 진입 또는 reset 직후 — active 자동 선택.
                        curId == null -> _currentTournamentId.value = pickActive(tournaments)
                        // 현재 선택된 토너먼트가 외부에서 삭제됨 → 그 다음 active 로 자동 전환.
                        tournaments.none { it.id == curId } ->
                            _currentTournamentId.value = pickActive(tournaments)
                        // 현재 선택된 토너먼트가 *방금 finished 상태로 전이* 한 경우는 의도적으로
                        // 유지 — ChampionBanner / 폭죽 연출을 위해. 사용자가 새 추첨 / 초기화 / 뒤로가기
                        // 등 명시적 액션으로 다른 토너먼트로 이동하기 전까지는 그 자리에 둠.
                    }
                }
        }
        // 활성 토너먼트 변경 시 doc + matches 단일 listener 유지. VM lifetime 동안 항상 활성.
        viewModelScope.launch {
            _currentTournamentId
                .flatMapLatest { id ->
                    if (id == null) flowOf<Pair<TournamentDoc?, List<MatchDoc>>>(null to emptyList())
                    else combine(
                        tournamentsRepo.observeById(id),
                        tournamentsRepo.observeMatches(id),
                    ) { t, ms -> t to ms }
                }
                .catch { e ->
                    android.util.Log.e("OctaLink.Tournament", "tournament/matches flow error", e)
                    emit(null to emptyList())
                }
                .collect { (t, ms) ->
                    _tournament.value = t
                    _matches.value = ms
                }
        }
    }

    val state: StateFlow<TournamentUiState> = combine(
        _tournament, _matches, membersById,
    ) { t, ms, members -> buildUiState(t, ms, members) }
        .catch { e ->
            android.util.Log.e("OctaLink.Tournament", "state flow error", e)
            emit(TournamentUiState())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TournamentUiState())

    fun beltOf(name: String): Belt? = membersById.value.values.firstOrNull { it.name == name }?.belt

    /**
     * 회원 풀에서 추첨. 인원수에 따라 시작 라운드 자동 결정.
     * - 2명: 결승 직행
     * - 3~4명: 4강 시작 (round2)
     * - 5~8명: 8강 시작 (round1, 부족분 부전승)
     *
     * 부전승은 이 함수에서 사전 해결해 한 batch (`tournamentsRepo.create`) 로 영속화 — 룰 검증/네트워크 왕복 최소화.
     */
    fun draw(members: List<Member>, weightClass: WeightClass?, beltGroup: Belt?) {
        viewModelScope.launch {
            runCatching {
                val pool = members.shuffled()
                val matches = buildInitialMatches(pool)
                if (matches.isEmpty()) {
                    android.util.Log.w("OctaLink.Tournament", "draw: pool size ${pool.size} — skip")
                    return@runCatching
                }
                // 진행 중 토너먼트 자동 정리 — 히스토리 누적 방지. 완료된 토너먼트는 보존(히스토리에 남김).
                // 같은 도장에서 두 토너먼트가 동시에 "active" 상태가 의미 없는 도메인 가정 — 새 추첨은 곧
                // 기존 미완료 폐기를 의미. BracketDrawScreen 의 명시적 "추첨하기" 버튼이 이미 deliberate 액션.
                val curId = _currentTournamentId.value
                val cur = _tournament.value
                if (curId != null && cur?.id == curId && cur.finishedAt == null) {
                    tournamentsRepo.delete(curId)
                }
                val titleParts = listOfNotNull(weightClass?.displayName, beltGroup?.displayName)
                val title = if (titleParts.isEmpty()) "전체 추첨" else titleParts.joinToString(" ")
                val tournament = tournamentsRepo.create(title, weightClass, beltGroup, matches)
                _currentTournamentId.value = tournament.id
            }.onFailure { android.util.Log.e("OctaLink.Tournament", "draw FAILED", it) }
        }
    }

    /**
     * 현재 활성 토너먼트가 *미완료* 상태일 때만 Firestore 에서 삭제 — 히스토리 보존 가드.
     * 완료된 토너먼트는 삭제하지 않고 [pickActive] 로 그 다음 미완료를 active 로 전환 (없으면 null → EmptyState).
     * 완료 토너먼트의 명시적 삭제는 추후 히스토리 화면의 per-card 액션으로 분리 예정.
     * 삭제 후 [observeAll] 이 emit → init 의 collect 가 그 다음 active 로 자동 전환.
     */
    fun reset() {
        val curId = _currentTournamentId.value ?: return
        val cur = _tournament.value
        viewModelScope.launch {
            runCatching {
                if (cur != null && cur.id == curId && cur.finishedAt != null) {
                    // 완료된 토너먼트 — 삭제 차단. 캐시된 list 에서 그 다음 미완료를 즉시 선택.
                    _currentTournamentId.value = pickActive(_allTournaments.value)
                } else {
                    tournamentsRepo.delete(curId)
                    _currentTournamentId.value = null
                }
            }.onFailure { android.util.Log.e("OctaLink.Tournament", "reset FAILED", it) }
        }
    }

    /** 8강 매치 승자 지정 → 4강 슬롯 갱신. 다운스트림 승자는 리셋. */
    fun setRound1Winner(matchIdx: Int, winnerName: String) {
        val tournamentId = _currentTournamentId.value ?: return
        val winnerId = idByName(winnerName) ?: run {
            android.util.Log.w("OctaLink.Tournament", "setRound1Winner: unknown winner '$winnerName'")
            return
        }
        val matches = _matches.value
        val r1Doc = matches.firstOrNull { it.round == TournamentRound.EIGHT && it.slotIndex == matchIdx } ?: return
        val r2Idx = matchIdx / 2
        val isFirstOfPair = matchIdx % 2 == 0
        val r2Doc = matches.firstOrNull { it.round == TournamentRound.FOUR && it.slotIndex == r2Idx } ?: return
        val finalDoc = matches.firstOrNull { it.round == TournamentRound.FINAL }
        val byUser = currentUserId.orEmpty()

        viewModelScope.launch {
            runCatching {
                tournamentsRepo.setMatchWinner(tournamentId, r1Doc.id, winnerId, byUser)
                val newRed = if (isFirstOfPair) winnerId else r2Doc.redMemberId
                val newBlue = if (!isFirstOfPair) winnerId else r2Doc.blueMemberId
                val resetR2 = r2Doc.winnerMemberId != null
                tournamentsRepo.setMatchSlots(tournamentId, r2Doc.id, newRed, newBlue, resetR2)
                if (resetR2 && finalDoc != null) {
                    val newFinalRed = if (r2Idx == 0) null else finalDoc.redMemberId
                    val newFinalBlue = if (r2Idx == 1) null else finalDoc.blueMemberId
                    tournamentsRepo.setMatchSlots(tournamentId, finalDoc.id, newFinalRed, newFinalBlue, resetWinner = true)
                }
            }.onFailure { android.util.Log.e("OctaLink.Tournament", "setRound1Winner FAILED", it) }
        }
    }

    /** 4강 매치 승자 지정 → 결승 슬롯 갱신. 결승 승자는 리셋. */
    fun setRound2Winner(matchIdx: Int, winnerName: String) {
        val tournamentId = _currentTournamentId.value ?: return
        val winnerId = idByName(winnerName) ?: return
        val matches = _matches.value
        val r2Doc = matches.firstOrNull { it.round == TournamentRound.FOUR && it.slotIndex == matchIdx } ?: return
        val finalDoc = matches.firstOrNull { it.round == TournamentRound.FINAL } ?: return
        val byUser = currentUserId.orEmpty()

        viewModelScope.launch {
            runCatching {
                tournamentsRepo.setMatchWinner(tournamentId, r2Doc.id, winnerId, byUser)
                val newRed = if (matchIdx == 0) winnerId else finalDoc.redMemberId
                val newBlue = if (matchIdx == 1) winnerId else finalDoc.blueMemberId
                tournamentsRepo.setMatchSlots(
                    tournamentId, finalDoc.id, newRed, newBlue,
                    resetWinner = finalDoc.winnerMemberId != null,
                )
            }.onFailure { android.util.Log.e("OctaLink.Tournament", "setRound2Winner FAILED", it) }
        }
    }

    /** 결승 승자 = 챔피언. [TournamentRepository.finish] 로 finishedAt + champion 기록. */
    fun setFinalWinner(winnerName: String) {
        val tournamentId = _currentTournamentId.value ?: return
        val winnerId = idByName(winnerName) ?: return
        val matches = _matches.value
        val finalDoc = matches.firstOrNull { it.round == TournamentRound.FINAL } ?: return
        val byUser = currentUserId.orEmpty()

        viewModelScope.launch {
            runCatching {
                tournamentsRepo.setMatchWinner(tournamentId, finalDoc.id, winnerId, byUser)
                tournamentsRepo.finish(tournamentId, winnerId)
            }.onFailure { android.util.Log.e("OctaLink.Tournament", "setFinalWinner FAILED", it) }
        }
    }

    // ─────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────

    private fun idByName(name: String): String? =
        membersById.value.values.firstOrNull { it.name == name }?.id

    private fun buildUiState(
        tournament: TournamentDoc?,
        matches: List<MatchDoc>,
        members: Map<String, MemberDoc>,
    ): TournamentUiState {
        if (tournament == null) return TournamentUiState()
        val nameOf: (String?) -> String = { id -> id?.let { members[it]?.name } ?: "?" }
        val r1Docs = matches.filter { it.round == TournamentRound.EIGHT }.sortedBy { it.slotIndex }
        val r2Docs = matches.filter { it.round == TournamentRound.FOUR }.sortedBy { it.slotIndex }
        val finalDoc = matches.firstOrNull { it.round == TournamentRound.FINAL }
        return TournamentUiState(
            initialized = true,
            round1 = r1Docs.map { docToMatch(it, nameOf) },
            round2 = r2Docs.map { docToMatch(it, nameOf) },
            final = finalDoc?.let { docToMatch(it, nameOf) } ?: Match("?", "?"),
            weightClass = tournament.weightClass,
            beltGroup = tournament.beltGroup,
            finished = tournament.finishedAt != null,
        )
    }

    private fun docToMatch(doc: MatchDoc, nameOf: (String?) -> String): Match = Match(
        red = nameOf(doc.redMemberId),
        blue = nameOf(doc.blueMemberId),
        // 부전승 / 결정된 매치 모두 winnerMemberId 가 채워짐. UI 는 winner != null 로 표시 분기.
        winner = doc.winnerMemberId?.let { nameOf(it) },
    )

    /**
     * 인원수별 초기 매치 슬롯 생성. 부전승은 한 번에 사전 해결.
     *
     * 부전승 규칙:
     *  - 한 매치에서 한쪽만 null → 다른 쪽 자동 승자
     *  - 더블 부전승(null/null) → 승자 없음, 상위 슬롯도 null 로 비워둠
     *  - 4강에서 더블 부전승이면 결승 슬롯도 null (실제 경기가 4강에서 결정 필요)
     */
    private fun buildInitialMatches(pool: List<Member>): List<MatchDoc> {
        val n = pool.size
        if (n <= 1) return emptyList()
        val ids: List<String?> = pool.map { it.id }
        val result = mutableListOf<MatchDoc>()

        when {
            n == 2 -> {
                result += matchDoc(TournamentRound.FINAL, 0, ids[0], ids[1])
            }
            n in 3..4 -> {
                val padded: List<String?> = ids + List(4 - n) { null }
                val r2 = (0..1).map { idx ->
                    autoResolveBye(
                        matchDoc(TournamentRound.FOUR, idx, padded[idx * 2], padded[idx * 2 + 1])
                    )
                }
                result += r2
                result += matchDoc(
                    TournamentRound.FINAL, 0,
                    red = r2[0].winnerMemberId, blue = r2[1].winnerMemberId,
                )
            }
            else -> {
                val capped = ids.take(8)
                val padded: List<String?> = capped + List(8 - capped.size) { null }
                val r1 = (0..3).map { idx ->
                    autoResolveBye(
                        matchDoc(TournamentRound.EIGHT, idx, padded[idx * 2], padded[idx * 2 + 1])
                    )
                }
                result += r1
                val r2 = (0..1).map { idx ->
                    val upTop = r1[idx * 2]
                    val upBot = r1[idx * 2 + 1]
                    val topResolved = upTop.winnerMemberId != null || isDoubleBye(upTop)
                    val botResolved = upBot.winnerMemberId != null || isDoubleBye(upBot)
                    var m = matchDoc(
                        TournamentRound.FOUR, idx,
                        red = upTop.winnerMemberId, blue = upBot.winnerMemberId,
                    )
                    // 상위 둘 다 결정난 상태에서만 4강 부전승 자동 해결 — 미결 실경기를 부전승으로 잘못 판정 회피.
                    if (topResolved && botResolved) m = autoResolveBye(m)
                    m
                }
                result += r2
                result += matchDoc(
                    TournamentRound.FINAL, 0,
                    red = r2[0].winnerMemberId, blue = r2[1].winnerMemberId,
                )
            }
        }
        return result
    }

    private fun matchDoc(round: TournamentRound, slotIndex: Int, red: String?, blue: String?): MatchDoc =
        MatchDoc(
            id = "", tournamentId = "", round = round, slotIndex = slotIndex,
            redMemberId = red, blueMemberId = blue, winnerMemberId = null,
            resolvedAt = null, resolvedByMasterId = null,
        )

    private fun autoResolveBye(m: MatchDoc): MatchDoc = when {
        m.redMemberId != null && m.blueMemberId == null -> m.copy(winnerMemberId = m.redMemberId)
        m.redMemberId == null && m.blueMemberId != null -> m.copy(winnerMemberId = m.blueMemberId)
        else -> m
    }

    private fun isDoubleBye(m: MatchDoc): Boolean =
        m.redMemberId == null && m.blueMemberId == null
}
