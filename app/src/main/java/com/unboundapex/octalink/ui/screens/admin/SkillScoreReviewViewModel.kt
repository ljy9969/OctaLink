package com.unboundapex.octalink.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.SkillSet
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.SkillScoreDoc
import com.unboundapex.octalink.data.schema.SkillScoreStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 관장 전용 — 코치 제안(PROPOSED) 스킬 점수 검토 큐.
 *
 * 워크플로:
 *   1. 코치가 [SkillScoreProposeScreen] 에서 propose → status=PROPOSED
 *      (관장은 같은 화면에서 directApprove 로 PROPOSED 단계 생략 → 이 큐에 안 잡힘)
 *   2. 이 ViewModel 이 collectionGroup 으로 전 회원 PROPOSED 만 모음
 *   3. 관장이 [approve] / [reject] → status 전이
 *   4. [approve] / [reject] 후 [com.unboundapex.octalink.data.schema.MemberDoc.skills] 스냅샷 재계산
 *      ([getCanonicalApproved] — 관장 직접 평가 우선)
 *
 * 비정규화 — SkillScoreDoc 자체엔 memberName/byUserName 없어서 members 풀과 join 해
 * 표시용 이름 매핑을 한 번에 만들어둠 (작은 N, 페이지네이션 불필요).
 */
data class PendingReviewItem(
    val score: SkillScoreDoc,
    val memberName: String,
    val byUserName: String,
)

class SkillScoreReviewViewModel : ViewModel() {
    private val skillScores = RepositoryProvider.skillScores
    private val members = RepositoryProvider.members

    val pending: StateFlow<List<PendingReviewItem>> =
        combine(
            skillScores.observePendingAcrossAllMembers(),
            members.observeAll(),
        ) { scores, allMembers ->
            val byId = allMembers.associateBy { it.id }
            scores.map { s ->
                PendingReviewItem(
                    score = s,
                    memberName = byId[s.memberId]?.name ?: "(알 수 없는 회원)",
                    byUserName = byId[s.byUserId]?.name ?: "(알 수 없음)",
                )
            }
        }
            .catch { e ->
                android.util.Log.e("OctaLink.SkillReview", "pending flow error", e)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun approve(item: PendingReviewItem, reviewedByMasterId: String) {
        transition(item, reviewedByMasterId, SkillScoreStatus.APPROVED)
    }

    fun reject(item: PendingReviewItem, reviewedByMasterId: String) {
        transition(item, reviewedByMasterId, SkillScoreStatus.REJECTED)
    }

    private fun transition(
        item: PendingReviewItem,
        reviewedByMasterId: String,
        newStatus: SkillScoreStatus,
    ) {
        viewModelScope.launch {
            runCatching {
                skillScores.setStatus(
                    memberId = item.score.memberId,
                    scoreId = item.score.id,
                    reviewedByMasterId = reviewedByMasterId,
                    newStatus = newStatus,
                )
                // 회원 프로필 6축 스냅샷의 단일 원본은 [MemberDoc.skills]. status 만 바꾸면
                // ProfileScreen 의 HexagonSkillChart 가 갱신 안 됨. setStatus 후 *정식* 스냅샷
                // (관장 직접 평가 우선 → 코치 제안→승인 fallback) 을 다시 계산해 백필.
                // REJECTED 도 직전 canonical 이 바뀔 수 있으므로 동일하게 재계산.
                refreshMemberSkillsSnapshot(item.score.memberId)
            }.onFailure {
                android.util.Log.e("OctaLink.SkillReview", "transition FAILED", it)
            }
        }
    }

    private suspend fun refreshMemberSkillsSnapshot(memberId: String) {
        val canonical = skillScores.getCanonicalApproved(memberId) ?: return
        members.updateProfile(
            memberId = memberId,
            skills = SkillSet(
                striking = canonical.striking,
                grappling = canonical.grappling,
                stamina = canonical.stamina,
                technique = canonical.technique,
                mental = canonical.mental,
                speed = canonical.speed,
            ),
        )
    }
}
