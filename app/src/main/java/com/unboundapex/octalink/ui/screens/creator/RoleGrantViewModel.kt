package com.unboundapex.octalink.ui.screens.creator

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.MembershipStatus
import com.unboundapex.octalink.data.schema.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 창조자 단독 — 회원 역할 부여 + 개발용 테스트 시드 VM.
 *
 * 표시 대상: APPROVED 회원 중 CREATOR 본인 제외. 체급(내림차순) → 벨트(내림차순) 정렬.
 */
class RoleGrantViewModel : ViewModel() {
    private val members = RepositoryProvider.members
    private val functions = Firebase.functions("asia-northeast3")

    val grantable: StateFlow<List<MemberDoc>> =
        members.observeByStatus(MembershipStatus.APPROVED)
            .map { list ->
                list.filter { it.role != Role.CREATOR }
                    .sortedWith(
                        compareByDescending<MemberDoc> { it.weightClass.ordinal }
                            .thenByDescending { it.belt.ordinal }
                    )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun grantRole(memberId: String, newRole: Role) {
        viewModelScope.launch { members.setRole(memberId, newRole) }
    }

    /** 개발용 시드 결과 메시지 — 버튼 클릭 후 UI 에 표시. */
    private val _seedStatus = MutableStateFlow<String?>(null)
    val seedStatus: StateFlow<String?> = _seedStatus.asStateFlow()

    /**
     * Cloud Function `seedTestData` 호출 — 테스트 회원 5명 + 오늘 출석 시드.
     * CREATOR 만 호출 가능 (server-side 검증). 멱등이라 여러 번 호출해도 안전.
     */
    fun seedTestData() {
        _seedStatus.value = "시드 생성 중..."
        viewModelScope.launch {
            runCatching {
                functions.getHttpsCallable("seedTestData")
                    .call(emptyMap<String, Any>())
                    .await()
            }.onSuccess { result ->
                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any>
                val createdMembers = data?.get("createdMembers")
                val createdAttendance = data?.get("createdAttendance")
                _seedStatus.value =
                    "성공 — 회원 ${createdMembers}건, 출석 ${createdAttendance}건 생성"
                Log.i("OctaLink.Seed", "seedTestData success: $data")
            }.onFailure { e ->
                _seedStatus.value = "실패: ${e.message}"
                Log.e("OctaLink.Seed", "seedTestData failed", e)
            }
        }
    }
}
