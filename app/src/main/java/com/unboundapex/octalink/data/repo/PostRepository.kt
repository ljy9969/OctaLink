package com.unboundapex.octalink.data.repo

import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.schema.PostDoc
import com.unboundapex.octalink.data.schema.PostTag
import kotlinx.coroutines.flow.Flow

/**
 * 커뮤니티 글 ([PostDoc]) 영속화 추상화.
 *
 * Firestore 경로: `posts/{postId}` — 단순 컬렉션 (sharding 없음, 글 수가 ~1만 이하 가정).
 * 이미지는 별도 Firebase Storage 에 업로드 후 download URL 만 doc 에 저장.
 *
 * 정렬: client-side 에서 NOTICE 우선 + createdAt DESC. composite index 회피.
 */
interface PostRepository {
    /** 모든 글 — createdAt DESC. NOTICE 우선 정렬은 호출자(ViewModel) 가 처리. */
    fun observeAll(): Flow<List<PostDoc>>

    /**
     * 글 작성. id 는 Firestore 자동 생성. 작성 시점이 createdAt.
     * imageUrl 은 사전 Storage 업로드 후 호출자가 전달.
     */
    suspend fun create(
        authorId: String,
        authorName: String,
        authorBelt: Belt,
        title: String,
        body: String,
        tag: PostTag,
        imageUrl: String? = null,
    ): PostDoc

    /** 좋아요 토글 — likedBy 에 memberId 추가/제거. */
    suspend fun toggleLike(postId: String, memberId: String)

    /** 글 삭제 — 작성자 본인 또는 운영진. Storage 이미지 같이 정리는 호출자 책임. */
    suspend fun delete(postId: String)
}
