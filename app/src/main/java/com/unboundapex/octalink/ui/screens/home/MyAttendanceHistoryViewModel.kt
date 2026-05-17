package com.unboundapex.octalink.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/**
 * 홈 → "내 주간 출석률" 카드 → [com.unboundapex.octalink.ui.screens.home.MyAttendanceHistoryScreen] 데이터 소스.
 *
 * **로그인 본인의 출석 기록만** 노출 — [observeFor] 가 받은 memberId 로
 * `attendance.observeByMember(memberId)` 구독 → 다른 회원 데이터 일절 포함 안 됨.
 *
 * 페이징 범위: 올해 1월 ~ 이번 달 (이후 미래 / 작년 이전 차단).
 */
class MyAttendanceHistoryViewModel : ViewModel() {
    private val attendance = RepositoryProvider.attendance
    private val seoul = ZoneId.of("Asia/Seoul")

    /** 오늘(KST) — 캘린더에서 "오늘" 셀 강조용. */
    val today: LocalDate = LocalDate.now(seoul)

    /** 페이징 가능한 가장 이른 달 — 올해 1월 1일. */
    val earliestMonth: LocalDate = LocalDate.of(today.year, 1, 1)

    /** 페이징 가능한 가장 늦은 달 — 이번 달 1일. */
    val latestMonth: LocalDate = today.withDayOfMonth(1)

    private val _memberId = MutableStateFlow<String?>(null)
    fun observeFor(memberId: String?) { _memberId.value = memberId }

    /** 현재 표시 중인 달 (월의 첫째 날). 초기값 이번 달. */
    private val _selectedMonth = MutableStateFlow(latestMonth)
    val selectedMonth: StateFlow<LocalDate> = _selectedMonth.asStateFlow()

    /**
     * 선택된 달의 본인 출석 일자 셋 — 캘린더 셀 채색용.
     * 본인 attendance 전체 stream × 선택 달 변경 시 자동 갱신.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val attendedDatesInMonth: StateFlow<Set<LocalDate>> =
        combine(
            _memberId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList())
                else attendance.observeByMember(id)
            },
            _selectedMonth,
        ) { docs, month ->
            docs.asSequence()
                .filter { it.classDate.year == month.year && it.classDate.month == month.month }
                .map { it.classDate }
                .toSet()
        }
            .catch { e ->
                android.util.Log.e("OctaLink.MyAttendance", "attendedDatesInMonth error", e)
                emit(emptySet())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 이전 달로 이동 — [earliestMonth] 보다 이전이면 무시. */
    fun prevMonth() {
        val candidate = _selectedMonth.value.minusMonths(1)
        if (!candidate.isBefore(earliestMonth)) {
            _selectedMonth.value = candidate
        }
    }

    /** 다음 달로 이동 — [latestMonth] 보다 이후면 무시. */
    fun nextMonth() {
        val candidate = _selectedMonth.value.plusMonths(1)
        if (!candidate.isAfter(latestMonth)) {
            _selectedMonth.value = candidate
        }
    }
}
