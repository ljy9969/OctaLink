package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.schema.PostCommentDoc
import java.time.Instant

/**
 * [PostCommentDoc] ↔ Firestore Map. postId 는 부모 경로에 있지만 collectionGroup 쿼리에서
 * 빠른 식별 위해 doc 안에도 같이 저장 (rules 의 authorId 와 동일한 비정규화 의도).
 */
internal fun PostCommentDoc.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "postId" to postId,
    "authorId" to authorId,
    "authorName" to authorName,
    "authorBelt" to authorBelt.name,
    "body" to body,
    "createdAt" to Timestamp(createdAt.epochSecond, createdAt.nano),
    "likedBy" to likedBy,
    "updatedAt" to updatedAt?.let { Timestamp(it.epochSecond, it.nano) },
)

@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.toPostCommentDoc(): PostCommentDoc? {
    val postId = getString("postId") ?: return null
    val authorId = getString("authorId") ?: return null
    val authorName = getString("authorName") ?: return null
    return PostCommentDoc(
        id = id,
        postId = postId,
        authorId = authorId,
        authorName = authorName,
        authorBelt = runCatching { Belt.valueOf(getString("authorBelt") ?: "") }.getOrDefault(Belt.UNKNOWN),
        body = getString("body").orEmpty(),
        createdAt = (get("createdAt") as? Timestamp)?.let {
            Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong())
        } ?: Instant.EPOCH,
        likedBy = (get("likedBy") as? List<String>) ?: emptyList(),
        updatedAt = (get("updatedAt") as? Timestamp)?.let {
            Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong())
        },
    )
}
