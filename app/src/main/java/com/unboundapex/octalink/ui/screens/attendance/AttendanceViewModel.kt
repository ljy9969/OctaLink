package com.unboundapex.octalink.ui.screens.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.AttendanceDoc
import com.unboundapex.octalink.data.schema.MemberDoc
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * AttendanceScreen 의 데이터 합성 — 오늘 출석 + 회원 마스터 join.
 *
 * collectionGroup 쿼리(attendance) + members.observeAll() 두 Flow 를 combine 해서
 * UI 가 한 번에 받을 수 있는 [TodayPeer] 리스트로 변환.
 */
class AttendanceViewModel : ViewModel() {
    private val attendance = RepositoryProvider.attendance
    private val members = RepositoryProvider.members

    private val today: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))

    /** 오늘 출석한 회원 + 회원 정보 (이름/벨트/아바타) merged. checkInAt 시각 기준 정렬. */
    val todayPeers: StateFlow<List<TodayPeer>> =
        combine(
            attendance.observeByDate(today),
            members.observeAll(),
        ) { docs, memberList ->
            val byId = memberList.associateBy { it.id }
            val peers = docs.mapNotNull { doc -> byId[doc.memberId]?.let { TodayPeer(doc, it) } }
                .sortedBy { it.attendance.checkInAt }
            android.util.Log.d(
                "OctaLink.Attendance",
                "combine: attendance=${docs.size}, members=${memberList.size}, peers=${peers.size}; " +
                    "missingJoin=${docs.filter { it.memberId !in byId }.map { it.memberId }}",
            )
            peers
        }.catch { e ->
            // Firestore 쿼리 실패(권한/인덱스/네트워크) 시 UI 가 크래시 대신 빈 목록으로 graceful
            android.util.Log.e("OctaLink.Attendance", "todayPeers flow error", e)
            emit(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun checkIn(memberId: String, classDefId: String) {
        android.util.Log.d("OctaLink.Attendance", "checkIn called: memberId=$memberId, date=$today")
        viewModelScope.launch {
            runCatching {
                attendance.checkIn(memberId, classDefId, today)
            }.onSuccess { doc ->
                android.util.Log.i(
                    "OctaLink.Attendance",
                    "checkIn success: id=${doc.id}, classDate=${doc.classDate}",
                )
            }.onFailure { e ->
                android.util.Log.e("OctaLink.Attendance", "checkIn FAILED", e)
            }
        }
    }

    fun cancelCheckIn(memberId: String) {
        android.util.Log.d("OctaLink.Attendance", "cancelCheckIn called: memberId=$memberId, date=$today")
        viewModelScope.launch {
            runCatching {
                attendance.cancelCheckIn(memberId, today)
            }.onSuccess {
                android.util.Log.i("OctaLink.Attendance", "cancelCheckIn success")
            }.onFailure { e ->
                android.util.Log.e("OctaLink.Attendance", "cancelCheckIn FAILED", e)
            }
        }
    }
}

/** UI 단위 — 출석 doc + 회원 마스터 정보 merge. */
data class TodayPeer(
    val attendance: AttendanceDoc,
    val member: MemberDoc,
)
