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
/** 도장 정기 운영 일수 (월~토, 일요일 휴무). 주간 출석률 분모. */
const val GYM_DAYS_PER_WEEK: Int = 6

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
     * 이번 주/이번 달 출석 집계 — 단일 collectionGroup 쿼리로 한 번에 가져와 client-side 파생.
     *
     * 쿼리 시작점 = min(weekStart, monthStart) — 주가 월을 가로질러도(예: 월초 며칠이 전월)
     * 두 derivation 모두 누락 없이 커버.
     *
     * 도장 운영 6일/주 (월~토, 일요일 휴무) — 출석률 분모 [GYM_DAYS_PER_WEEK].
     */
    private val seoul = ZoneId.of("Asia/Seoul")
    private val today: LocalDate = LocalDate.now(seoul)
    val weekStart: LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val monthStart: LocalDate = today.withDayOfMonth(1)
    private val queryStart: LocalDate = if (weekStart.isBefore(monthStart)) weekStart else monthStart

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
     * 이번 달 일자별 출석 건수 — MonthCalendar heatmap.
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
                .filter { it.classDate.year == monthStart.year && it.classDate.month == monthStart.month }
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
