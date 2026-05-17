package com.unboundapex.octalink.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.isClosed
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.MembershipStatus
import com.unboundapex.octalink.data.schema.Role
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/**
 * 홈 "오늘 체육관 활성도" 카드 데이터 소스 — REVIEW MonthCalendar 와 동일 분모 정책 사용.
 *
 * 분자: 오늘(KST) attendance.observeByDate 결과 중 visible 회원(=MASTER 제외 APPROVED) 의 출석 건 수
 * 분모: APPROVED 회원 중 MASTER 제외 (도장 운영자라 출석 대상 아님)
 * 휴무일(일요일/공휴일) 은 [HomeGymActivity.isClosed] true → UI 는 "오늘은\n휴무" 로 분기
 */
data class HomeGymActivity(
    val attendCount: Int,
    val totalMembers: Int,
    val isClosed: Boolean,
) {
    /** 백분율 (0~100). 분모 0 이면 0. */
    val percent: Int
        get() = if (totalMembers <= 0) 0
        else (attendCount.coerceAtMost(totalMembers) * 100 / totalMembers)
}

class HomeGymActivityViewModel : ViewModel() {
    private val attendance = RepositoryProvider.attendance
    private val members = RepositoryProvider.members
    private val today: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
    private val todayClosed: Boolean = isClosed(today)

    val state: StateFlow<HomeGymActivity> =
        combine(
            attendance.observeByDate(today),
            members.observeByStatus(MembershipStatus.APPROVED),
        ) { docs, allApproved ->
            val visibleIds = allApproved.asSequence()
                .filter { it.role != Role.MASTER }
                .mapTo(HashSet()) { it.id }
            val count = docs.count { it.memberId in visibleIds }
            HomeGymActivity(
                attendCount = count,
                totalMembers = visibleIds.size,
                isClosed = todayClosed,
            )
        }
            .catch { e ->
                android.util.Log.e("OctaLink.HomeActivity", "state flow error", e)
                emit(HomeGymActivity(0, 0, todayClosed))
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                HomeGymActivity(0, 0, todayClosed),
            )
}
