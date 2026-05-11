package com.unboundapex.octalink.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.MembershipStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * AdminScreen 의 "회원 가입 승인" 큐 — PENDING 회원 목록 + 승인/거부 액션.
 *
 * 권한 사전 검증은 화면에서 (관장+창조자에게만 카드 노출). Firestore 환경에선 보안 규칙
 * 이 동일 검증을 강제하지만, mock 단계에선 호출자(VM 사용처) 가 책임.
 */
class MemberApprovalViewModel : ViewModel() {
    private val members = RepositoryProvider.members

    val pending: StateFlow<List<MemberDoc>> =
        members.observeByStatus(MembershipStatus.PENDING)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun approve(memberId: String) {
        viewModelScope.launch { members.setStatus(memberId, MembershipStatus.APPROVED) }
    }

    fun reject(memberId: String) {
        viewModelScope.launch { members.setStatus(memberId, MembershipStatus.REJECTED) }
    }
}
