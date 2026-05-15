package com.unboundapex.octalink.ui.screens.creator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.MembershipStatus
import com.unboundapex.octalink.data.schema.Role
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 창조자 단독 — 회원 역할 부여 VM.
 *
 * 표시 대상: APPROVED 회원 중 CREATOR 본인 제외. 체급(내림차순) → 벨트(내림차순) 정렬.
 */
class RoleGrantViewModel : ViewModel() {
    private val members = RepositoryProvider.members

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
}
