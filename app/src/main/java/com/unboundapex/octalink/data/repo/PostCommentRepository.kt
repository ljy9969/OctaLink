package com.unboundapex.octalink.data.repo

import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.schema.PostCommentDoc
import kotlinx.coroutines.flow.Flow

/**
 * 글 한 건의 댓글([PostCommentDoc]) 영속화 추상화.
 *
 * Firestore 경로: `posts/{postId}/comments/{commentId}` — 서브컬렉션.
 * 글 단위 listener 라 글이 100개여도 펼친 글만 listener 활성화 가능 (호출자 제어).
 *
 * 권한 정책 (Firestore rules):
 *  - read: 모든 APPROVED 회원
 *  - create: 모든 APPROVED, authorId 위조 차단
 *  - update: 작성자 본인 전용
 *  - delete: 작성자 본인 + 관장급(MASTER/CREATOR) — 모더레이션. 코치 제외 ([PostDoc] 정책과 동일)
 */
interface PostCommentRepository {
    /** 특정 글의 댓글 — createdAt ASC (오래된 → 최신). 펼쳤을 때 자연스러운 대화 흐름. */
    fun observeForPost(postId: String): Flow<List<PostCommentDoc>>

    /**
     * 모든 글의 댓글 수 집계 — Map<postId, count>. 카드 목록에서 "댓글 N" 배지에 사용.
     * collectionGroup 쿼리. 매 doc 변경 시 emit (실시간).
     */
    fun observeCommentCounts(): Flow<Map<String, Int>>

    /**
     * 댓글 작성. id 는 Firestore 자동 생성. 작성 시점이 createdAt.
     */
    suspend fun create(
        postId: String,
        authorId: String,
        authorName: String,
        authorBelt: Belt,
        body: String,
    ): PostCommentDoc

    /** 본문 수정. updatedAt 갱신. 권한은 rules 에서 검증 (작성자 전용). */
    suspend fun update(postId: String, commentId: String, body: String)

    /** 좋아요 토글 — likedBy 에 memberId 추가/제거. */
    suspend fun toggleLike(postId: String, commentId: String, memberId: String)

    /** 삭제. 권한은 rules 에서 검증 (작성자 + 관장급). */
    suspend fun delete(postId: String, commentId: String)
}
