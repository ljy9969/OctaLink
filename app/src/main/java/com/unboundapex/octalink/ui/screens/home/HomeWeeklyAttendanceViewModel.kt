package com.unboundapex.octalink.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 홈 "내 주간 출석률" 카드의 목표 일수(분모). 도장 운영일
 * ([com.unboundapex.octalink.ui.screens.attendance.GYM_DAYS_PER_WEEK], 6일)과는 별개로,
 * 개인 주간 출석 목표를 5일로 둔다 — 주 5일 출석 시 100%. (REVIEW 화면 직원용 카드는 운영일 6 유지)
 */
const val HOME_WEEKLY_ATTENDANCE_TARGET: Int = 5

/**
 * 홈 화면 "내 주간 출석률" 카드용 — 현재 사용자 본인의 이번 주(월~토) 출석 일수.
 *
 *  - 분자: 본인 attendance 중 classDate >= weekStart 인 distinct 날짜 수
 *  - 분모: [HOME_WEEKLY_ATTENDANCE_TARGET] (5일 목표)
 *  - 백분율: capped * 100 / 5
 *
 * Firestore snapshot listener 기반이라 본인 체크인 시 즉시 갱신.
 */
class HomeWeeklyAttendanceViewModel : ViewModel() {
    private val attendance = RepositoryProvider.attendance
    private val seoul = ZoneId.of("Asia/Seoul")

    /** 이번 주 시작(월요일). 화면 생애주기 동안 고정 — 자정 넘김은 다음 진입에 자연 반영. */
    val weekStart: LocalDate = LocalDate.now(seoul)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private val _memberId = MutableStateFlow<String?>(null)
    fun observeFor(memberId: String?) { _memberId.value = memberId }

    @OptIn(ExperimentalCoroutinesApi::class)
    val weeklyCount: StateFlow<Int> =
        _memberId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList())
                else attendance.observeByMember(id)
            }
            .map { docs ->
                docs.asSequence()
                    .filter { !it.classDate.isBefore(weekStart) }
                    .map { it.classDate }
                    .toSet()
                    .size
            }
            .catch { e ->
                android.util.Log.e("OctaLink.HomeAttendance", "weeklyCount flow error", e)
                emit(0)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
