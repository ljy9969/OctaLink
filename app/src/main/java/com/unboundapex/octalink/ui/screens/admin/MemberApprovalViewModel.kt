package com.unboundapex.octalink.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.SkillSet
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.MembershipStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * AdminScreen 의 관장/창조자용 회원 관리:
 *  1. 가입 승인 큐 (PENDING)
 *  2. 미등급 벨트 큐 (APPROVED + Belt.UNKNOWN) — 관장이 벨트/체급 지정
 *
 * 권한 사전 검증은 화면에서 (관장+창조자에게만 카드 노출). Firestore 환경에선 보안 규칙이
 * 동일 검증을 강제하지만, mock 단계에선 호출자(VM 사용처)가 책임.
 */
class MemberApprovalViewModel : ViewModel() {
    private val members = RepositoryProvider.members

    val pending: StateFlow<List<MemberDoc>> =
        members.observeByStatus(MembershipStatus.PENDING)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** APPROVED 회원 중 belt == UNKNOWN — 관장이 정식 벨트를 지정해줘야 하는 대상. */
    val unknownBelt: StateFlow<List<MemberDoc>> =
        members.observeByStatus(MembershipStatus.APPROVED)
            .map { list -> list.filter { it.belt == Belt.UNKNOWN } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun approve(memberId: String) {
        viewModelScope.launch { members.setStatus(memberId, MembershipStatus.APPROVED) }
    }

    fun reject(memberId: String) {
        viewModelScope.launch { members.setStatus(memberId, MembershipStatus.REJECTED) }
    }

    /**
     * 회원의 벨트 지정 (관장 권한). 체급은 회원 본인이 직접 변경하므로 여기 포함 안 함.
     */
    fun assignBelt(memberId: String, belt: Belt) {
        viewModelScope.launch {
            members.updateProfile(memberId = memberId, belt = belt)
        }
    }

    /** APPROVED 회원 중 점수가 입력된 회원 + 미입력 회원 모두 — 스킬 평가용 풀. */
    val approvedMembers: StateFlow<List<MemberDoc>> =
        members.observeByStatus(MembershipStatus.APPROVED)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 회원 스킬 점수 입력/갱신 (관장 권한). */
    fun assignSkills(memberId: String, skills: SkillSet) {
        viewModelScope.launch {
            members.updateProfile(memberId = memberId, skills = skills)
        }
    }
}
