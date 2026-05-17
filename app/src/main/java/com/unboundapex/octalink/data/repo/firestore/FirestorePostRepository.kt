package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.repo.PostRepository
import com.unboundapex.octalink.data.schema.Collections
import com.unboundapex.octalink.data.schema.PostDoc
import com.unboundapex.octalink.data.schema.PostTag
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore 기반 [PostRepository].
 *
 * 경로: `posts/{postId}` — 단일 컬렉션. 글 수가 많지 않다는 가정 (~1만건). 페이지네이션이
 * 필요해지면 `limit()` + `startAfter()` 추가.
 *
 * 정렬: createdAt DESC. NOTICE 우선 정렬은 client-side(ViewModel) 에서 처리.
 */
class FirestorePostRepository : PostRepository {
    private val db = Firebase.firestore
    private val col = db.collection(Collections.POSTS)

    override fun observeAll(): Flow<List<PostDoc>> = callbackFlow {
        val sub = col
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("OctaLink.Posts", "observeAll snapshot error", err)
                    close(err)
                    return@addSnapshotListener
                }
                val docs = snap?.documents?.mapNotNull { it.toPostDoc() }.orEmpty()
                trySend(docs)
            }
        awaitClose { sub.remove() }
    }

    override suspend fun create(
        authorId: String,
        authorName: String,
        authorBelt: Belt,
        title: String,
        body: String,
        tag: PostTag,
        imageUrl: String?,
        videoUrl: String?,
    ): PostDoc {
        val ref = col.document()
        val data = mapOf(
            "id" to ref.id,
            "authorId" to authorId,
            "authorName" to authorName,
            "authorBelt" to authorBelt.name,
            "title" to title,
            "body" to body,
            "tag" to tag.name,
            "imageUrl" to imageUrl,
            "videoUrl" to videoUrl,
            "likedBy" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        ref.set(data).await()
        // 서버 timestamp 포함된 doc 재조회
        val snap = ref.get().await()
        return snap.toPostDoc() ?: error("create 후 posts/${ref.id} 조회 실패")
    }

    override suspend fun toggleLike(postId: String, memberId: String) {
        val ref = col.document(postId)
        val snap = ref.get().await()
        val current = (snap.get("likedBy") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val update = if (memberId in current) {
            mapOf("likedBy" to FieldValue.arrayRemove(memberId))
        } else {
            mapOf("likedBy" to FieldValue.arrayUnion(memberId))
        }
        ref.update(update).await()
    }

    override suspend fun update(postId: String, title: String, body: String, tag: PostTag) {
        // 부분 업데이트 — authorId/createdAt/imageUrl/likedBy 는 미변경 → Firestore rule
        // (authorId/createdAt 불변 조건) 통과.
        col.document(postId).update(
            mapOf(
                "title" to title,
                "body" to body,
                "tag" to tag.name,
            )
        ).await()
    }

    override suspend fun delete(postId: String) {
        col.document(postId).delete().await()
    }
}
