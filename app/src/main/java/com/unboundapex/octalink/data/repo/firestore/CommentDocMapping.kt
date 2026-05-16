package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.unboundapex.octalink.data.schema.CommentDoc
import java.time.Instant
import java.time.LocalDate

/**
 * [CommentDoc] ↔ Firestore Map 직렬화.
 *  - [LocalDate] → ISO-8601 String ("yyyy-MM-dd")
 *  - [Instant] → [Timestamp]
 */
internal fun DocumentSnapshot.toCommentDoc(): CommentDoc? {
    val toMemberId = getString("toMemberId") ?: return null
    val byMasterId = getString("byMasterId") ?: return null
    val text = getString("text") ?: return null
    return CommentDoc(
        id = id,
        toMemberId = toMemberId,
        byMasterId = byMasterId,
        byMasterName = getString("byMasterName").orEmpty(),
        text = text,
        classDate = runCatching {
            LocalDate.parse(getString("classDate") ?: "")
        }.getOrDefault(LocalDate.now()),
        createdAt = (get("createdAt") as? Timestamp)?.let {
            Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong())
        } ?: Instant.EPOCH,
    )
}
