package com.unboundapex.octalink.data.repo.inmemory

import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.repo.PostRepository
import com.unboundapex.octalink.data.schema.PostDoc
import com.unboundapex.octalink.data.schema.PostTag
import com.unboundapex.octalink.data.schema.PostVisibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.UUID

/**
 * 커뮤니티 글 in-memory 저장소. Phase 1 토글 시 사용.
 * 시드: 시연용 3건 (공지/기록/팁) — 가입 후 메인 진입 즉시 카드가 보이도록.
 */
class InMemoryPostRepository(
    seed: List<PostDoc> = MockPostsSeed.demoPosts(),
) : PostRepository {
    private val _posts = MutableStateFlow(seed)
    private val posts = _posts.asStateFlow()

    override fun observeAll(): Flow<List<PostDoc>> = posts

    override suspend fun create(
        authorId: String,
        authorName: String,
        authorBelt: Belt,
        title: String,
        body: String,
        tag: PostTag,
        imageUrl: String?,
        videoUrl: String?,
        mentionedMemberIds: List<String>,
    ): PostDoc {
        val hasMedia = imageUrl != null || videoUrl != null
        val effectiveMentions = mentionedMemberIds.distinct().filter { it != authorId }
        val isPending = hasMedia && effectiveMentions.isNotEmpty()
        val doc = PostDoc(
            id = UUID.randomUUID().toString(),
            authorId = authorId,
            authorName = authorName,
            authorBelt = authorBelt,
            title = title,
            body = body,
            tag = tag,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            createdAt = Instant.now(),
            mentionedMemberIds = effectiveMentions,
            visibility = if (isPending) PostVisibility.PENDING_APPROVAL else PostVisibility.PUBLIC,
            pendingApprovalFrom = if (isPending) effectiveMentions else emptyList(),
        )
        _posts.value = _posts.value + doc
        return doc
    }

    override suspend fun approveMention(postId: String, memberId: String) {
        _posts.value = _posts.value.map { p ->
            if (p.id != postId) p
            else {
                val remaining = p.pendingApprovalFrom - memberId
                p.copy(
                    pendingApprovalFrom = remaining,
                    visibility = if (remaining.isEmpty()) PostVisibility.PUBLIC else p.visibility,
                )
            }
        }
    }

    override suspend fun rejectMention(postId: String, memberId: String) {
        _posts.value = _posts.value.map { p ->
            if (p.id != postId) p
            else p.copy(
                visibility = PostVisibility.REJECTED,
                rejectedBy = (p.rejectedBy + memberId).distinct(),
                pendingApprovalFrom = p.pendingApprovalFrom - memberId,
            )
        }
    }

    override suspend fun toggleLike(postId: String, memberId: String) {
        _posts.value = _posts.value.map {
            if (it.id != postId) it
            else {
                val updated = if (memberId in it.likedBy) it.likedBy - memberId
                else it.likedBy + memberId
                it.copy(likedBy = updated)
            }
        }
    }

    override suspend fun update(postId: String, title: String, body: String, tag: PostTag) {
        _posts.value = _posts.value.map {
            if (it.id != postId) it else it.copy(title = title, body = body, tag = tag)
        }
    }

    override suspend fun delete(postId: String) {
        _posts.value = _posts.value.filterNot { it.id == postId }
    }
}

internal object MockPostsSeed {
    fun demoPosts(): List<PostDoc> {
        val now = Instant.now()
        return listOf(
            PostDoc(
                id = "seed-1",
                authorId = "pool-master",
                authorName = "관장 김파시",
                authorBelt = Belt.BLACK,
                title = "",
                body = "이번 주 금요일 스파링 데이입니다. 마우스피스 꼭 챙겨오세요.",
                tag = PostTag.NOTICE,
                createdAt = now.minusSeconds(3600 * 6),
            ),
            PostDoc(
                id = "seed-2",
                authorId = "pool-f01",
                authorName = "이지연",
                authorBelt = Belt.WHITE,
                title = "",
                body = "오늘 첫 스파링 했습니다. 잽 거리감 잡는 게 제일 어려웠어요.",
                tag = PostTag.RECORD,
                createdAt = now.minusSeconds(3600 * 9),
            ),
            PostDoc(
                id = "seed-3",
                authorId = "pool-l01",
                authorName = "박정호",
                authorBelt = Belt.BROWN,
                title = "",
                body = "샌드백 칠 때 발 위치 영상으로 찍어보니 자세 다 무너져 있더라구요. 추천합니다.",
                tag = PostTag.TIP,
                createdAt = now.minusSeconds(3600 * 24),
            ),
        )
    }
}
