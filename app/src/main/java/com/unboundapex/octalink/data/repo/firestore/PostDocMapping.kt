package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.schema.PostDoc
import com.unboundapex.octalink.data.schema.PostTag
import java.time.Instant

/**
 * [PostDoc] ↔ Firestore Map 직렬화. enum 은 `.name`, [Instant] 는 [Timestamp].
 * imageUrl 은 Firebase Storage download URL String. likedBy 는 String 배열.
 */
internal fun PostDoc.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "authorId" to authorId,
    "authorName" to authorName,
    "authorBelt" to authorBelt.name,
    "title" to title,
    "body" to body,
    "tag" to tag.name,
    "imageUrl" to imageUrl,
    "likedBy" to likedBy,
    "createdAt" to Timestamp(createdAt.epochSecond, createdAt.nano),
)

@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.toPostDoc(): PostDoc? {
    val authorId = getString("authorId") ?: return null
    val authorName = getString("authorName") ?: return null
    val tagName = getString("tag") ?: return null
    return PostDoc(
        id = id,
        authorId = authorId,
        authorName = authorName,
        authorBelt = runCatching { Belt.valueOf(getString("authorBelt") ?: "") }.getOrDefault(Belt.UNKNOWN),
        title = getString("title").orEmpty(),
        body = getString("body").orEmpty(),
        tag = runCatching { PostTag.valueOf(tagName) }.getOrDefault(PostTag.RECORD),
        imageUrl = getString("imageUrl"),
        likedBy = (get("likedBy") as? List<String>) ?: emptyList(),
        createdAt = (get("createdAt") as? Timestamp)?.let {
            Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong())
        } ?: Instant.EPOCH,
    )
}
