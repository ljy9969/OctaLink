package com.unboundapex.octalink.data.repo.inmemory

import com.unboundapex.octalink.data.repo.CommentRepository
import com.unboundapex.octalink.data.schema.CommentDoc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 코멘트 in-memory 저장소. Phase 1 토글 시 사용.
 * 시드: 이지연(CREATOR, id=pool-f01) 앞으로 관장 김파시 / 코치 박 코멘트 3건 — Profile 진입 즉시 표시.
 */
class InMemoryCommentRepository(
    seed: List<CommentDoc> = MockCommentsSeed.demoComments(),
) : CommentRepository {
    private val _comments = MutableStateFlow(seed)

    override fun observeByMember(toMemberId: String): Flow<List<CommentDoc>> =
        _comments.map { list ->
            list.filter { it.toMemberId == toMemberId }
                .sortedByDescending { it.classDate }
        }

    override suspend fun create(
        toMemberId: String,
        byMasterId: String,
        byMasterName: String,
        text: String,
        classDate: LocalDate,
    ): CommentDoc {
        val doc = CommentDoc(
            id = UUID.randomUUID().toString(),
            toMemberId = toMemberId,
            byMasterId = byMasterId,
            byMasterName = byMasterName,
            text = text,
            classDate = classDate,
            createdAt = Instant.now(),
        )
        _comments.value = _comments.value + doc
        return doc
    }

    override suspend fun delete(toMemberId: String, commentId: String) {
        _comments.value = _comments.value.filterNot {
            it.toMemberId == toMemberId && it.id == commentId
        }
    }
}

internal object MockCommentsSeed {
    fun demoComments(): List<CommentDoc> {
        val now = Instant.now()
        val today = LocalDate.now()
        return listOf(
            CommentDoc(
                id = "seed-c1",
                toMemberId = "pool-f01",
                byMasterId = "pool-master",
                byMasterName = "관장 김파시",
                text = "리드 잽 후 체중 이동을 반박자 늦춰보세요. 카운터 위험이 줄어듭니다.",
                classDate = today.minusDays(2),
                createdAt = now.minusSeconds(3600 * 48),
            ),
            CommentDoc(
                id = "seed-c2",
                toMemberId = "pool-f01",
                byMasterId = "pool-coach-park",
                byMasterName = "코치 박",
                text = "샌드백 라운드 후반에 가드가 내려갑니다. 마지막 30초 의식적으로 올리기.",
                classDate = today.minusDays(4),
                createdAt = now.minusSeconds(3600 * 96),
            ),
            CommentDoc(
                id = "seed-c3",
                toMemberId = "pool-f01",
                byMasterId = "pool-master",
                byMasterName = "관장 김파시",
                text = "테이크다운 디펜스 시 골반 각도 좋아졌습니다. 그대로 유지.",
                classDate = today.minusDays(7),
                createdAt = now.minusSeconds(3600 * 168),
            ),
        )
    }
}
