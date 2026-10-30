package com.unboundapex.octalink.ui.screens.exchange

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.geo.GeoDistance
import com.unboundapex.octalink.data.repo.NearbyGym
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.ExchangeMatchDoc
import com.unboundapex.octalink.data.schema.GymDoc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 교류전 지도 반경 선택지(km). 기본 1km. */
val RADIUS_OPTIONS_KM = listOf(1, 2, 3)

/** 신청 가능한(OctaLink 가입) 체육관 한 줄 — 우리 체육관과의 거리(둘 다 위치가 있을 때만). */
data class MemberGymRow(val gym: GymDoc, val distanceM: Double?)

/** 주변 체육관(카카오) 로딩 상태. */
sealed interface NearbyState {
    data object Loading : NearbyState
    /** 우리 체육관 위치 미등록 — 지도·반경 검색 불가. */
    data object NeedsLocation : NearbyState
    data class Error(val message: String) : NearbyState
    data class Loaded(val places: List<NearbyGym>) : NearbyState
}

/**
 * 교류전 화면 상태. [bind] 로 현재 회원(id/gymId/운영진 여부) 주입.
 * 상대 체육관 찾기(지도·반경·주변 체육관·교류 요청) + 내 교류전 + (운영진) 우리 체육관 교류전.
 * 가입 체육관 관원 목록·결투 신청은 [ExchangeGymViewModel](별도 페이지).
 */
class ExchangeViewModel : ViewModel() {
    private val gymsRepo = RepositoryProvider.gyms
    private val exRepo = RepositoryProvider.exchangeMatches
    private val nearbyRepo = RepositoryProvider.nearbyGyms

    var myMemberId: String? = null; private set
    var isStaff by mutableStateOf(false); private set

    private val _gymId = MutableStateFlow<String?>(null)
    private val _memberId = MutableStateFlow<String?>(null)

    fun bind(memberId: String, gymId: String, staff: Boolean) {
        myMemberId = memberId
        isStaff = staff
        _memberId.value = memberId
        _gymId.value = gymId
    }

    /** 전체 체육관 — 우리 체육관 위치 · 신청 가능한 체육관 · 일정 다이얼로그 장소 라벨. */
    val allGyms: StateFlow<List<GymDoc>> =
        gymsRepo.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myGym: StateFlow<GymDoc?> =
        combine(allGyms, _gymId) { gyms, id -> gyms.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 신청 가능한 체육관 — 반경과 상관없이 전부, 가까운 순(위치 없는 체육관은 뒤, 이름순). */
    val memberGyms: StateFlow<List<MemberGymRow>> =
        combine(allGyms, _gymId) { gyms, myId ->
            val mine = gyms.firstOrNull { it.id == myId }
            gyms.filter { it.id != myId }.map { g ->
                val d = if (mine?.hasLocation == true && g.hasLocation) {
                    GeoDistance.meters(mine.lat!!, mine.lng!!, g.lat!!, g.lng!!)
                } else null
                MemberGymRow(g, d)
            }.sortedWith(compareBy<MemberGymRow>({ it.distanceM == null }, { it.distanceM }, { it.gym.name }))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _radiusKm = MutableStateFlow(RADIUS_OPTIONS_KM.first())
    val radiusKm: StateFlow<Int> = _radiusKm.asStateFlow()
    fun setRadius(km: Int) { if (km in RADIUS_OPTIONS_KM) _radiusKm.value = km }

    private val _nearby = MutableStateFlow<NearbyState>(NearbyState.Loading)
    val nearby: StateFlow<NearbyState> = _nearby.asStateFlow()
    private val reloadTick = MutableStateFlow(0)
    fun retryNearby() { reloadTick.update { it + 1 } }

    init {
        // 체육관이 정해진 뒤, 반경이 바뀔 때마다(또는 다시 시도) 서버 nearbyGyms 호출 — 이전 요청은 취소.
        viewModelScope.launch {
            combine(_gymId.filterNotNull(), _radiusKm, reloadTick) { _, r, _ -> r }.collectLatest { r ->
                _nearby.value = NearbyState.Loading
                _nearby.value = runCatching { nearbyRepo.nearby(r) }.fold(
                    onSuccess = { if (it.needsLocation) NearbyState.NeedsLocation else NearbyState.Loaded(it.places) },
                    onFailure = { NearbyState.Error(it.message ?: "주변 체육관을 불러오지 못했어요.") },
                )
            }
        }
    }

    /** 아직 OctaLink 전인 체육관에 교류 요청 토글 — 결과 숫자로 목록 갱신. */
    fun toggleInterest(place: NearbyGym) = viewModelScope.launch {
        runCatching { nearbyRepo.toggleInterest(place.placeId, place.name) }
            .onSuccess { r ->
                _nearby.update { s ->
                    if (s !is NearbyState.Loaded) s else NearbyState.Loaded(s.places.map {
                        if (it.placeId != place.placeId) it
                        else it.copy(requestedByMe = r.requested, requestCount = r.count, myGymRequestCount = r.myGymCount)
                    })
                }
                _message.value = if (r.requested) "교류 요청을 남겼어요." else "교류 요청을 취소했어요."
            }
            .onFailure { _message.value = it.message ?: "요청에 실패했어요." }
    }

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

    fun approve(id: String) = run("승인했어요.") { exRepo.approveDuel(id) }
    fun reject(id: String) = run("반려/취소했어요.") { exRepo.rejectDuel(id) }
    fun propose(id: String, slots: List<String>) =
        run("가능한 일정을 제시했어요.") { exRepo.proposeDuelSlots(id, slots) }
    fun schedule(id: String, time: String, place: String) =
        run("일정을 확정했어요.") { exRepo.scheduleDuel(id, time, place) }
    fun recordResult(id: String, winnerId: String?, draw: Boolean) =
        run("결과를 기록했어요.") { exRepo.recordResult(id, winnerId, draw) }
}
