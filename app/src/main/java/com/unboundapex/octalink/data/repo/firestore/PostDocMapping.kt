package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.schema.PostDoc
import com.unboundapex.octalink.data.schema.PostTag
import com.unboundapex.octalink.data.schema.PostVisibility
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
    "videoUrl" to videoUrl,
    "likedBy" to likedBy,
    "createdAt" to Timestamp(createdAt.epochSecond, createdAt.nano),
    "mentionedMemberIds" to mentionedMemberIds,
    "visibility" to visibility.name,
    "pendingApprovalFrom" to pendingApprovalFrom,
    "rejectedBy" to rejectedBy,
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
        videoUrl = getString("videoUrl"),
        likedBy = (get("likedBy") as? List<String>) ?: emptyList(),
        createdAt = (get("createdAt") as? Timestamp)?.let {
            Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong())
        } ?: Instant.EPOCH,
        mentionedMemberIds = (get("mentionedMemberIds") as? List<String>) ?: emptyList(),
        // 기존 doc 호환 — visibility 필드 없으면 PUBLIC 기본.
        visibility = runCatching {
            PostVisibility.valueOf(getString("visibility") ?: "PUBLIC")
        }.getOrDefault(PostVisibility.PUBLIC),
        pendingApprovalFrom = (get("pendingApprovalFrom") as? List<String>) ?: emptyList(),
        rejectedBy = (get("rejectedBy") as? List<String>) ?: emptyList(),
    )
}
