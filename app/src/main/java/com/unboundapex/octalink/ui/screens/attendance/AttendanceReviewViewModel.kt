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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

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
