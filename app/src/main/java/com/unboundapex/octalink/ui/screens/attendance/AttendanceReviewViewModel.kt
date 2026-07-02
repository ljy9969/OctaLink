package com.unboundapex.octalink.ui.screens.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.AttendanceDoc
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.MembershipStatus
import com.unboundapex.octalink.data.schema.Role
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 운영진 출결 검토 — 회원 한 명을 선택하면 그 회원의 attendance 시계열을 보여준다.
 *
 * 화면 흐름:
 *  1. 좌측: APPROVED 회원 목록 (이름순)
 *  2. 회원 선택 → [selectedMemberId] 변경 → [selectedAttendance] flow 가 갱신
 *  3. 우측: 선택 회원의 attendance 일자별 list (DESC) + verified 토글 / 삭제 액션
 *
 * 권한: AdminScreen / AttendanceScreen 운영진 모드 카드에서만 진입하므로 호출자 책임.
 * Firestore Rules 가 collectionGroup + 직접 path 모두 isStaff/isApproved 로 더블 검증.
 */
/**
 * 주간 출석률 기준(목표) 일수 — 이만큼 출석하면 100%. 홈 "내 주간 출석률" 카드와 회원별
 * 리뷰 화면 공용 분모. (도장 실제 운영은 월~토지만, 출석률 기준은 주 5일로 둠.)
 */
const val WEEKLY_ATTENDANCE_TARGET: Int = 5

class AttendanceReviewViewModel : ViewModel() {
    private val attendance = RepositoryProvider.attendance
    private val members = RepositoryProvider.members

    /**
     * 검토 대상 회원 풀 (APPROVED).
     *
     * MASTER(관장) 만 출결 검토 대상에서 제외 — 도장 운영자 역할이지 출석 통계 추적 대상이 아님.
     * COACH 는 본인이 직접 운동/스파링 참여, CREATOR(앱 제작자) 도 본인 출석 추적 의향 있음.
     */
    val approvedMembers: StateFlow<List<MemberDoc>> =
        members.observeByStatus(MembershipStatus.APPROVED)
            .map { list -> list.filter { it.role != Role.MASTER } }
            .catch { e ->
                android.util.Log.e("OctaLink.AttendanceReview", "approvedMembers flow error", e)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 이번 주/연중 출석 집계 — 단일 collectionGroup 쿼리로 한 번에 가져와 client-side 파생.
     *
     * 쿼리 시작점 = min(weekStart, yearStart) — 주가 연도를 가로질러도(예: 1월 첫 주가 전년 12월)
     * 모든 derivation 누락 없이 커버.
     *
     * 주간 출석률 분모 = [WEEKLY_ATTENDANCE_TARGET] (목표 일수).
     */
    private val seoul = ZoneId.of("Asia/Seoul")
    private val today: LocalDate = LocalDate.now(seoul)
    val weekStart: LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val monthStart: LocalDate = today.withDayOfMonth(1)
    private val yearStart: LocalDate = today.withDayOfMonth(1).withMonth(1)
    private val queryStart: LocalDate = if (weekStart.isBefore(yearStart)) weekStart else yearStart

    /** 활성도 캘린더 페이징 범위 — 올해 1월(하한) ~ 이번 달(상한). */
    val minMonth: LocalDate = yearStart
    val maxMonth: LocalDate = monthStart

    private val _displayedMonth = MutableStateFlow(monthStart)
    val displayedMonth: StateFlow<LocalDate> = _displayedMonth.asStateFlow()

    fun goPrevMonth() {
        val cur = _displayedMonth.value
        if (!cur.isAfter(minMonth)) return
        _displayedMonth.value = cur.minusMonths(1)
    }

    fun goNextMonth() {
        val cur = _displayedMonth.value
        if (!cur.isBefore(maxMonth)) return
        _displayedMonth.value = cur.plusMonths(1)
    }

    private val recentAttendance: StateFlow<List<AttendanceDoc>> =
        attendance.observeSince(queryStart)
            .catch { e ->
                android.util.Log.e("OctaLink.AttendanceReview", "recentAttendance flow error", e)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 회원별 이번 주 출석 일수 (월요일~토요일 누적, 최대 6). MemberPicker 카드 우측 % 계산. */
    val weeklyCountByMember: StateFlow<Map<String, Int>> =
        recentAttendance
            .map { docs ->
                docs.filter { !it.classDate.isBefore(weekStart) }
                    .groupingBy { it.memberId }.eachCount()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * 올해 일자별 출석 건수 — 1월~이번 달 [MonthCalendar] heatmap 들이 공유하는 단일 맵.
     *
     * 표시 대상 회원 풀([approvedMembers])과 동일하게 집계 — MASTER(관장) 본인 체크인이나
     * APPROVED 가 아닌 회원의 attendance 가 히트맵에는 잡히는데 뱃지에는 0 으로 표시되는
     * 모순을 회피. UI 일관성 보장.
     */
    val monthlyCountByDate: StateFlow<Map<LocalDate, Int>> =
        combine(recentAttendance, approvedMembers) { docs, members ->
            val visibleIds = members.mapTo(HashSet()) { it.id }
            docs.asSequence()
                .filter { it.memberId in visibleIds }
                .filter { it.classDate.year == yearStart.year }
                .groupingBy { it.classDate }.eachCount()
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _selectedMemberId = MutableStateFlow<String?>(null)
    val selectedMemberId: StateFlow<String?> = _selectedMemberId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedAttendance: StateFlow<List<AttendanceDoc>> =
        _selectedMemberId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList())
                else attendance.observeByMember(id)
            }
            .catch { e ->
                android.util.Log.e("OctaLink.AttendanceReview", "selectedAttendance flow error", e)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectMember(memberId: String) {
        _selectedMemberId.value = memberId
    }

    fun clearSelection() {
        _selectedMemberId.value = null
    }

    fun toggleVerified(memberId: String, classDate: LocalDate, verified: Boolean) {
        viewModelScope.launch {
            runCatching { attendance.setVerified(memberId, classDate, verified) }
                .onFailure { android.util.Log.e("OctaLink.AttendanceReview", "setVerified FAILED", it) }
        }
    }

    fun deleteAttendance(memberId: String, classDate: LocalDate) {
        viewModelScope.launch {
            runCatching { attendance.cancelCheckIn(memberId, classDate) }
                .onFailure { android.util.Log.e("OctaLink.AttendanceReview", "delete FAILED", it) }
        }
    }
}
