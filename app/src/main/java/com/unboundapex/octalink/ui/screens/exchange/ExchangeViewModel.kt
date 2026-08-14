package com.unboundapex.octalink.ui.screens.exchange

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.ExchangeMatchDoc
import com.unboundapex.octalink.data.schema.GymDoc
import com.unboundapex.octalink.data.schema.PublicProfileDoc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 교류전 화면 상태. [bind] 로 현재 회원(id/gymId/운영진 여부) 주입.
 * 명단(다른 체육관 publicProfiles) + 내 교류전 + (운영진) 우리 체육관 교류전.
 */
class ExchangeViewModel : ViewModel() {
    private val gymsRepo = RepositoryProvider.gyms
    private val ppRepo = RepositoryProvider.publicProfiles
    private val exRepo = RepositoryProvider.exchangeMatches

    var myMemberId: String? = null; private set
    var isStaff by mutableStateOf(false); private set

    private val _gymId = MutableStateFlow<String?>(null)
    private val _memberId = MutableStateFlow<String?>(null)
    private val _selectedTargetGymId = MutableStateFlow<String?>(null)
    val selectedTargetGymId: StateFlow<String?> = _selectedTargetGymId.asStateFlow()

    fun bind(memberId: String, gymId: String, staff: Boolean) {
        myMemberId = memberId
        isStaff = staff
        _memberId.value = memberId
        _gymId.value = gymId
    }

    fun selectTargetGym(id: String?) { _selectedTargetGymId.value = id }

    /** 내 체육관을 제외한 다른 체육관 목록. */
    val otherGyms: StateFlow<List<GymDoc>> =
        combine(gymsRepo.observeAll(), _gymId) { gyms, myGym ->
            gyms.filter { it.id != myGym }.sortedBy { it.name }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 전체 체육관 — 일정 다이얼로그 장소 옵션(양측 체육관) 라벨 해석용. */
    val allGyms: StateFlow<List<GymDoc>> =
        gymsRepo.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val directory: StateFlow<List<PublicProfileDoc>> =
        _selectedTargetGymId.flatMapLatest { gid ->
            if (gid.isNullOrBlank()) flowOf(emptyList()) else ppRepo.observeByGym(gid)
        }
            // 체급 오름차순(페더→헤비 = enum ordinal). 동일 체급은 벨트·이름 순으로 안정 정렬.
            .map { list ->
                list.sortedWith(
                    compareBy({ it.weightClass.ordinal }, { it.belt.ordinal }, { it.name })
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val myDuels: StateFlow<List<ExchangeMatchDoc>> =
        _memberId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else exRepo.observeMyDuels(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val gymDuels: StateFlow<List<ExchangeMatchDoc>> =
        _gymId.flatMapLatest { gid ->
            if (gid == null) flowOf(emptyList()) else exRepo.observeGymDuels(gid)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    private fun run(ok: String, block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }
            .onSuccess { _message.value = ok }
            .onFailure { _message.value = it.message ?: "요청에 실패했어요." }
    }

    fun request(opponentId: String) = run("결투를 신청했어요.") { exRepo.requestDuel(opponentId) }
    fun approve(id: String) = run("승인했어요.") { exRepo.approveDuel(id) }
    fun reject(id: String) = run("반려/취소했어요.") { exRepo.rejectDuel(id) }
    fun schedule(id: String, d: String, t: String, p: String) =
        run("일정을 정했어요.") { exRepo.scheduleDuel(id, d, t, p) }
    fun recordResult(id: String, winnerId: String?, draw: Boolean) =
        run("결과를 기록했어요.") { exRepo.recordResult(id, winnerId, draw) }
}
