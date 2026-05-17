package com.unboundapex.octalink.data.repo.inmemory

import com.unboundapex.octalink.data.SkillSet
import com.unboundapex.octalink.data.repo.SkillScoreRepository
import com.unboundapex.octalink.data.schema.SkillScoreDoc
import com.unboundapex.octalink.data.schema.SkillScoreStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/**
 * 스킬 점수 in-memory 저장소. Phase 1 토글 시 사용.
 * 시드: 이지연(CREATOR, id=pool-f01) 의 APPROVED 1건 — Profile 진입 즉시 hexagon 차트가 채워지도록.
 */
class InMemorySkillScoreRepository(
    seed: List<SkillScoreDoc> = MockSkillScoresSeed.demoScores(),
) : SkillScoreRepository {
    private val _scores = MutableStateFlow(seed)

    override fun observeByMember(memberId: String): Flow<List<SkillScoreDoc>> =
        _scores.map { list ->
            list.filter { it.memberId == memberId }
                .sortedByDescending { it.evaluatedAt }
        }

    override fun observeCanonicalApproved(memberId: String): Flow<SkillScoreDoc?> =
        _scores.map { list ->
            list.filter { it.memberId == memberId && it.status == SkillScoreStatus.APPROVED }
                .maxByOrNull { it.evaluatedAt }
        }

    override suspend fun getCanonicalApproved(memberId: String): SkillScoreDoc? =
        _scores.value
            .filter { it.memberId == memberId && it.status == SkillScoreStatus.APPROVED }
            .maxByOrNull { it.evaluatedAt }

    override fun observePendingAcrossAllMembers(): Flow<List<SkillScoreDoc>> =
        _scores.map { list ->
            list.filter { it.status == SkillScoreStatus.PROPOSED }
                .sortedBy { it.evaluatedAt }
        }

    override suspend fun propose(
        memberId: String,
        byUserId: String,
        skills: SkillSet,
    ): SkillScoreDoc {
        val doc = SkillScoreDoc(
            id = UUID.randomUUID().toString(),
            memberId = memberId,
            byUserId = byUserId,
            striking = skills.striking,
            grappling = skills.grappling,
            stamina = skills.stamina,
            technique = skills.technique,
            mental = skills.mental,
            speed = skills.speed,
            evaluatedAt = Instant.now(),
            status = SkillScoreStatus.PROPOSED,
        )
        _scores.value = _scores.value + doc
        return doc
    }

    override suspend fun directApprove(
        memberId: String,
        byUserId: String,
        skills: SkillSet,
    ): SkillScoreDoc {
        val now = Instant.now()
        val doc = SkillScoreDoc(
            id = UUID.randomUUID().toString(),
            memberId = memberId,
            byUserId = byUserId,
            striking = skills.striking,
            grappling = skills.grappling,
            stamina = skills.stamina,
            technique = skills.technique,
            mental = skills.mental,
            speed = skills.speed,
            evaluatedAt = now,
            status = SkillScoreStatus.APPROVED,
            reviewedByMasterId = byUserId,
            reviewedAt = now,
        )
        _scores.value = _scores.value + doc
        return doc
    }

    override suspend fun setStatus(
        memberId: String,
        scoreId: String,
        reviewedByMasterId: String,
        newStatus: SkillScoreStatus,
    ) {
        require(newStatus != SkillScoreStatus.PROPOSED) {
            "setStatus 는 APPROVED/REJECTED 로의 전이만 허용 (PROPOSED 는 propose() 가 생성)"
        }
        val now = Instant.now()
        _scores.value = _scores.value.map { s ->
            if (s.memberId == memberId && s.id == scoreId && s.status == SkillScoreStatus.PROPOSED) {
                s.copy(
                    status = newStatus,
                    reviewedByMasterId = reviewedByMasterId,
                    reviewedAt = now,
                )
            } else s
        }
    }

    override suspend fun delete(memberId: String, scoreId: String) {
        _scores.value = _scores.value.filterNot {
            it.memberId == memberId && it.id == scoreId
        }
    }

    override suspend fun rejectAllPending(memberId: String, byMasterId: String) {
        val now = Instant.now()
        _scores.value = _scores.value.map { s ->
            if (s.memberId == memberId && s.status == SkillScoreStatus.PROPOSED) {
                s.copy(
                    status = SkillScoreStatus.REJECTED,
                    reviewedByMasterId = byMasterId,
                    reviewedAt = now,
                )
            } else s
        }
    }
}

internal object MockSkillScoresSeed {
    fun demoScores(): List<SkillScoreDoc> {
        val now = Instant.now()
        return listOf(
            SkillScoreDoc(
                id = "seed-skill-1",
                memberId = "pool-f01",      // 이지연 (CREATOR)
                byUserId = "pool-master",   // 관장 김파시
                striking = 0.62f,
                grappling = 0.48f,
                stamina = 0.71f,
                technique = 0.55f,
                mental = 0.68f,
                speed = 0.74f,
                evaluatedAt = now.minusSeconds(3600 * 24 * 7),
                status = SkillScoreStatus.APPROVED,
                reviewedByMasterId = "pool-master",
                reviewedAt = now.minusSeconds(3600 * 24 * 6),
            ),
        )
    }
}
