package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.repo.PostCommentRepository
import com.unboundapex.octalink.data.schema.Collections
import com.unboundapex.octalink.data.schema.PostCommentDoc
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore 기반 [PostCommentRepository].
 *
 * 경로: `posts/{postId}/comments/{commentId}` 서브컬렉션.
 * 카운트 집계는 collectionGroup("comments") 한 listener 로 전 글 한꺼번에 (postId 별로 그룹핑).
 */
class FirestorePostCommentRepository : PostCommentRepository {
    private val db = Firebase.firestore
    private fun commentsCol(postId: String) =
        db.collection(Collections.POSTS).document(postId).collection(Collections.POST_COMMENTS)

    override fun observeForPost(postId: String): Flow<List<PostCommentDoc>> = callbackFlow {
        val sub = commentsCol(postId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("OctaLink.PostComments", "observeForPost error postId=$postId", err)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toPostCommentDoc() }.orEmpty()
                trySend(list)
            }
        awaitClose { sub.remove() }
    }

    override fun observeCommentCounts(): Flow<Map<String, Int>> = callbackFlow {
        // collectionGroup 으로 전 댓글 doc 을 가져와서 postId 별로 카운트.
        // 글 수가 적은 도장 커뮤니티 가정 — 댓글 총수 ~수백건 수준이면 무리 없음.
        // 향후 폭증 시 각 글마다 댓글 카운트 필드를 doc 에 캐싱하는 방향으로 전환.
        val sub = db.collectionGroup(Collections.POST_COMMENTS)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("OctaLink.PostComments", "observeCommentCounts error", err)
                    close(err)
                    return@addSnapshotListener
                }
                val counts = snap?.documents
                    ?.mapNotNull { it.getString("postId") }
                    ?.groupingBy { it }
                    ?.eachCount()
                    .orEmpty()
                trySend(counts)
            }
        awaitClose { sub.remove() }
    }

    override suspend fun create(
        postId: String,
        authorId: String,
        authorName: String,
        authorBelt: Belt,
        body: String,
    ): PostCommentDoc {
        val ref = commentsCol(postId).document()
        val data = mapOf(
            "id" to ref.id,
            "postId" to postId,
            "authorId" to authorId,
            "authorName" to authorName,
            "authorBelt" to authorBelt.name,
            "body" to body,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        ref.set(data).await()
        val snap = ref.get().await()
        return snap.toPostCommentDoc()
            ?: error("create 후 posts/$postId/comments/${ref.id} 조회 실패")
    }

    override suspend fun update(postId: String, commentId: String, body: String) {
        commentsCol(postId).document(commentId).update(
            mapOf(
                "body" to body,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    override suspend fun toggleLike(postId: String, commentId: String, memberId: String) {
        val ref = commentsCol(postId).document(commentId)
        val snap = ref.get().await()
        val current = (snap.get("likedBy") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val update = if (memberId in current) {
            mapOf("likedBy" to FieldValue.arrayRemove(memberId))
        } else {
            mapOf("likedBy" to FieldValue.arrayUnion(memberId))
        }
        ref.update(update).await()
    }

    override suspend fun delete(postId: String, commentId: String) {
        commentsCol(postId).document(commentId).delete().await()
    }
}
