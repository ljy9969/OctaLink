package com.unboundapex.octalink.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.ExchangeMatchStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * 홈 "교류전" 카드 배지용 — **들어온 결투 신청** 개수.
 *
 * 표시 기간: 신청 들어옴(REQUESTED)부터 **교류전 일정 당일까지**.
 *  - REQUESTED / APPROVED: 항상 카운트
 *  - SCHEDULED: 일정 날짜(scheduledDate, ISO "yyyy-MM-dd")가 오늘(KST) 이후거나
 *    오늘이면 카운트(=일정 당일까지), 지나면 제외
 *  - COMPLETED / REJECTED / CANCELLED: 제외
 *
 * 대상(내가 신청자가 아님):
 *  - 내가 상대(opponent)인 결투
 *  - (운영진) 우리 체육관이 요청측/상대측으로 얽힌 결투
 */
class HomeExchangeViewModel : ViewModel() {
    private val exRepo = RepositoryProvider.exchangeMatches
    private val memberId = MutableStateFlow<String?>(null)
    private val gymId = MutableStateFlow<String?>(null)
    private val staff = MutableStateFlow(false)

    fun bind(memberId: String?, gymId: String?, isStaff: Boolean) {
        this.memberId.value = memberId
        this.gymId.value = gymId
        this.staff.value = isStaff
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val myDuels = memberId.flatMapLatest { id ->
        if (id.isNullOrBlank()) flowOf(emptyList()) else exRepo.observeMyDuels(id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val gymDuels = combine(gymId, staff) { g, s -> g to s }.flatMapLatest { (g, s) ->
        if (g.isNullOrBlank() || !s) flowOf(emptyList()) else exRepo.observeGymDuels(g)
    }

    val incomingCount: StateFlow<Int> =
        combine(myDuels, gymDuels, memberId, gymId, staff) { my, gym, mid, gid, isStaff ->
            val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
            // 내 교류전 + 우리 체육관 교류전 병합(중복 id 제거) 후, 들어온 결투를 상태별로 카운트.
            (my + gym).associateBy { it.id }.values.count { d ->
                val involved = d.requesterMemberId != mid && (
                    mid == d.opponentMemberId ||
                        (isStaff && (gid == d.requesterGymId || gid == d.opponentGymId))
                    )
                involved && when (d.status) {
                    ExchangeMatchStatus.REQUESTED, ExchangeMatchStatus.APPROVED -> true
                    ExchangeMatchStatus.MATCHED, ExchangeMatchStatus.SCHEDULED -> {
                        // 일정 당일까지 — scheduledDate 가 오늘 이전이 아니면(오늘 포함) 유지.
                        val sd = d.scheduledDate?.let {
                            runCatching { java.time.LocalDate.parse(it) }.getOrNull()
                        }
                        sd != null && !sd.isBefore(today)
                    }
                    else -> false // COMPLETED / REJECTED / CANCELLED
                }
            }
        }
            .catch { e ->
                android.util.Log.e("OctaLink.HomeExchange", "incoming flow error", e)
                emit(0)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
