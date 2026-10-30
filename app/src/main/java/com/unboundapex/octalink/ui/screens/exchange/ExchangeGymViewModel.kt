package com.unboundapex.octalink.ui.screens.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.ExchangeMatchStatus
import com.unboundapex.octalink.data.schema.GymDoc
import com.unboundapex.octalink.data.schema.PublicProfileDoc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 가입 체육관 → 관원 신청 페이지 상태. [bind] 로 내 회원 id · 우리 체육관 · 상대 체육관 주입.
 * 상대 체육관 관원(publicProfiles) 목록 + 이미 진행 중인 상대(중복 신청 막기) + 결투 신청.
 */
class ExchangeGymViewModel : ViewModel() {
    private val gymsRepo = RepositoryProvider.gyms
    private val ppRepo = RepositoryProvider.publicProfiles
    private val exRepo = RepositoryProvider.exchangeMatches

    private val _memberId = MutableStateFlow<String?>(null)
    private val _myGymId = MutableStateFlow<String?>(null)
    private val _targetGymId = MutableStateFlow<String?>(null)

    fun bind(memberId: String, myGymId: String, targetGymId: String) {
        _memberId.value = memberId
        _myGymId.value = myGymId
        _targetGymId.value = targetGymId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val targetGym: StateFlow<GymDoc?> =
        _targetGymId.flatMapLatest { id -> if (id.isNullOrBlank()) flowOf(null) else gymsRepo.observeById(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val myGym: StateFlow<GymDoc?> =
        _myGymId.flatMapLatest { id -> if (id.isNullOrBlank()) flowOf(null) else gymsRepo.observeById(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val directory: StateFlow<List<PublicProfileDoc>> =
        _targetGymId.flatMapLatest { gid ->
            if (gid.isNullOrBlank()) flowOf(emptyList()) else ppRepo.observeByGym(gid)
        }
            // 체급 오름차순(페더→헤비 = enum ordinal). 동일 체급은 벨트·이름 순으로 안정 정렬.
            .map { list ->
                list.sortedWith(
                    compareBy({ it.weightClass.ordinal }, { it.belt.ordinal }, { it.name })
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 이미 진행 중(미종료)인 교류전 상대 id — 중복 신청 버튼 비활성용. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeOpponentIds: StateFlow<Set<String>> =
        _memberId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else exRepo.observeMyDuels(id) }
            .map { duels ->
                duels.filter {
                    it.status != ExchangeMatchStatus.COMPLETED &&
                        it.status != ExchangeMatchStatus.REJECTED &&
                        it.status != ExchangeMatchStatus.CANCELLED
                }.flatMap { listOf(it.requesterMemberId, it.opponentMemberId) }.toSet()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    fun request(opponentId: String) = viewModelScope.launch {
        runCatching { exRepo.requestDuel(opponentId) }
            .onSuccess { _message.value = "결투를 신청했어요." }
            .onFailure { _message.value = it.message ?: "요청에 실패했어요." }
    }
}
