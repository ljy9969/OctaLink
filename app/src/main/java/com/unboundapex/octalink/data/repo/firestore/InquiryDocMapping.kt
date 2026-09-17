package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.unboundapex.octalink.data.schema.InquiryCategory
import com.unboundapex.octalink.data.schema.InquiryDoc
import com.unboundapex.octalink.data.schema.InquiryStatus
import java.time.Instant

/** Firestore Map → [InquiryDoc]. enum 은 `.name`, [Instant] 는 [Timestamp]. */
internal fun DocumentSnapshot.toInquiryDoc(): InquiryDoc? {
    val authorId = getString("authorId") ?: return null
    val text = getString("text") ?: return null
    return InquiryDoc(
        id = id,
        gymId = getString("gymId") ?: "",
        authorId = authorId,
        authorName = getString("authorName").orEmpty(),
        category = runCatching { InquiryCategory.valueOf(getString("category") ?: "") }
            .getOrDefault(InquiryCategory.QUESTION),
        text = text,
        status = runCatching { InquiryStatus.valueOf(getString("status") ?: "PENDING") }
            .getOrDefault(InquiryStatus.PENDING),
        answer = getString("answer"),
        answeredBy = getString("answeredBy"),
        createdAt = (get("createdAt") as? Timestamp)?.let {
            Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong())
        } ?: Instant.EPOCH,
        answeredAt = (get("answeredAt") as? Timestamp)?.let {
            Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong())
        },
    )
}
