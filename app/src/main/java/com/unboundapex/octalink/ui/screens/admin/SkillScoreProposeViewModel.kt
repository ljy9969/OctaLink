package com.unboundapex.octalink.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.SkillSet
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.MembershipStatus
import com.unboundapex.octalink.data.schema.Role
import com.unboundapex.octalink.data.schema.SkillScoreDoc
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

/**
 * AdminScreen "스킬 점수 입력 (제안 → 관장 검토)" 진입점.
 *
 * 워크플로:
 *  1. 회원 풀(APPROVED, MASTER 제외) 표시
 *  2. 회원 선택 → 기존 스킬 점수 시계열(PROPOSED/APPROVED/REJECTED 모두) + "+ 새 점수 제안" 액션
 *  3. 제안 다이얼로그: 6축 슬라이더 → submit() → status=PROPOSED 로 새 doc 생성 (관장 리뷰 대기)
 *
 * 권한: 라우팅에서 isStaff 가드. Rules 가 byUserId == auth.uid + status == 'PROPOSED' 강제.
 *
 * 디자인 결정 — 코멘트와 달리 점수는 자유 작성이 아닌 데이터이므로 본인 작성건이라도 삭제는 비노출
 * (오기록 정정은 관장의 REJECT 후 재제안 흐름으로 유도).
 */
class SkillScoreProposeViewModel : ViewModel() {
    private val membersRepo = RepositoryProvider.members
    private val scoresRepo = RepositoryProvider.skillScores

    /** 평가 대상 풀: APPROVED + (MEMBER/COACH/CREATOR). MASTER 는 추첨/평가 대상 아님. */
    val members: StateFlow<List<MemberDoc>> =
        membersRepo.observeByStatus(MembershipStatus.APPROVED)
            .map { list ->
                list.filter { it.role != Role.MASTER }.sortedBy { it.name }
            }
            .catch { e ->
                android.util.Log.e("OctaLink.SkillScore", "members flow error", e)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedMemberId = MutableStateFlow<String?>(null)
    val selectedMemberId: StateFlow<String?> = _selectedMemberId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedScores: StateFlow<List<SkillScoreDoc>> =
        _selectedMemberId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList())
                else scoresRepo.observeByMember(id)
            }
            .catch { e ->
                android.util.Log.e("OctaLink.SkillScore", "selectedScores flow error", e)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectMember(memberId: String) { _selectedMemberId.value = memberId }
    fun clearSelection() { _selectedMemberId.value = null }

    /** 새 스킬 점수 제안. status=PROPOSED 로 작성 → 관장이 검토 후 APPROVED/REJECTED. */
    fun propose(memberId: String, byUserId: String, skills: SkillSet) {
        viewModelScope.launch {
            runCatching { scoresRepo.propose(memberId, byUserId, skills) }
                .onFailure { android.util.Log.e("OctaLink.SkillScore", "propose FAILED", it) }
        }
    }
}
