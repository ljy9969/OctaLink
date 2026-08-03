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
import com.unboundapex.octalink.data.schema.PostVisibility
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
            .whereEqualTo("gymId", com.unboundapex.octalink.data.SessionGym.gymId ?: "")
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
        mentionedMemberIds: List<String>,
    ): PostDoc {
        val ref = col.document()
        // 미디어(이미지/영상) + 멘션 조합이면 비공개(승인 대기) — 멘션된 회원 전원 승인 필요.
        // 멘션만 있고 미디어 없으면 일반 PUBLIC (단순 호명, 프라이버시 이슈 없음).
        val hasMedia = imageUrl != null || videoUrl != null
        val effectiveMentions = mentionedMemberIds.distinct().filter { it != authorId }
        val isPending = hasMedia && effectiveMentions.isNotEmpty()
        val data = mapOf(
            "id" to ref.id,
            "gymId" to (com.unboundapex.octalink.data.SessionGym.gymId ?: ""),
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
            "mentionedMemberIds" to effectiveMentions,
            "visibility" to if (isPending) PostVisibility.PENDING_APPROVAL.name else PostVisibility.PUBLIC.name,
            "pendingApprovalFrom" to if (isPending) effectiveMentions else emptyList(),
            "rejectedBy" to emptyList<String>(),
        )
        ref.set(data).await()
        // 서버 timestamp 포함된 doc 재조회
        val snap = ref.get().await()
        return snap.toPostDoc() ?: error("create 후 posts/${ref.id} 조회 실패")
    }

    override suspend fun approveMention(postId: String, memberId: String) {
        val ref = col.document(postId)
        // 트랜잭션 — 동시에 여러 멘션이 승인할 때 pending 셋 race 차단.
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val pending = (snap.get("pendingApprovalFrom") as? List<*>)
                ?.filterIsInstance<String>().orEmpty()
            val remaining = pending - memberId
            val updates = mutableMapOf<String, Any>(
                "pendingApprovalFrom" to remaining,
            )
            if (remaining.isEmpty()) {
                // 모두 승인 → PUBLIC 전이.
                updates["visibility"] = PostVisibility.PUBLIC.name
            }
            tx.update(ref, updates)
            null
        }.await()
    }

    override suspend fun rejectMention(postId: String, memberId: String) {
        val ref = col.document(postId)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val pending = (snap.get("pendingApprovalFrom") as? List<*>)
                ?.filterIsInstance<String>().orEmpty()
            tx.update(
                ref,
                mapOf(
                    "visibility" to PostVisibility.REJECTED.name,
                    "rejectedBy" to FieldValue.arrayUnion(memberId),
                    // REJECTED 후엔 추가 승인 의미 없음 — 빈 셋으로.
                    "pendingApprovalFrom" to (pending - memberId),
                ),
            )
            null
        }.await()
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
