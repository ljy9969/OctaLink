package com.unboundapex.octalink.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.SkillSet
import com.unboundapex.octalink.data.repo.RepositoryProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ProfileScreen 의 6축 hexagon 차트 — 본인의 정식 APPROVED 점수를 [skillScores] 컬렉션에서
 * 직접 구독. [com.unboundapex.octalink.data.schema.MemberDoc.skills] 스냅샷에 의존하지 않아
 * Firebase 콘솔에서 점수 doc 을 수동 삭제/수정해도 listener 가 emit → 차트 즉시 반영.
 *
 * 우선순위는 [com.unboundapex.octalink.data.repo.SkillScoreRepository.observeCanonicalApproved]
 * 가 결정 (관장 직접 평가 > 코치 제안→승인). APPROVED 가 하나도 없으면 [SkillSet.EMPTY] (빈 차트).
 */
class MySkillsViewModel : ViewModel() {
    private val scoresRepo = RepositoryProvider.skillScores

    private val _memberId = MutableStateFlow<String?>(null)

    fun observeFor(memberId: String?) {
        _memberId.value = memberId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val skills: StateFlow<SkillSet> =
        _memberId
            .flatMapLatest { id ->
                if (id == null) flowOf(null)
                else scoresRepo.observeCanonicalApproved(id)
            }
            .map { doc ->
                if (doc == null) SkillSet.EMPTY
                else SkillSet(
                    striking = doc.striking,
                    grappling = doc.grappling,
                    stamina = doc.stamina,
                    technique = doc.technique,
                    mental = doc.mental,
                    speed = doc.speed,
                )
            }
            .catch { e ->
                android.util.Log.e("OctaLink.Skills", "skills flow error", e)
                emit(SkillSet.EMPTY)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkillSet.EMPTY)
}
