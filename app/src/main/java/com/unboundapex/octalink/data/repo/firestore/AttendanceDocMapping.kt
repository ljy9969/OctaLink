package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.unboundapex.octalink.data.schema.AttendanceDoc
import java.time.Instant
import java.time.LocalDate

/**
 * [AttendanceDoc] ↔ Firestore Map.
 *
 * 경로 규약 (Schema.kt Collections): `members/{memberId}/attendance/{classDate}`
 * - doc id = classDate ISO 문자열 (예: "2026-05-12") — 하루 1 체크인 idempotent
 * - classDate 필드: ISO 문자열 ("yyyy-MM-dd")
 * - checkInAt: Firestore [Timestamp]
 * - memberId: 부모 doc 의 uid (write 시 Security Rules 가 검증)
 */

internal fun AttendanceDoc.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "memberId" to memberId,
    "classDefId" to classDefId,
    "classDate" to classDate.toString(),
    "checkInAt" to Timestamp(checkInAt.epochSecond, checkInAt.nano),
    "checkInLat" to checkInLat,
    "checkInLng" to checkInLng,
    "verified" to verified,
)

internal fun DocumentSnapshot.toAttendanceDoc(): AttendanceDoc? {
    val memberId = getString("memberId") ?: return null
    val classDateStr = getString("classDate") ?: return null
    val classDate = runCatching { LocalDate.parse(classDateStr) }.getOrNull() ?: return null
    val checkInAt = (get("checkInAt") as? Timestamp)?.let {
        Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong())
    } ?: return null
    return AttendanceDoc(
        id = id,
        memberId = memberId,
        classDefId = getString("classDefId") ?: "default",
        classDate = classDate,
        checkInAt = checkInAt,
        checkInLat = getDouble("checkInLat"),
        checkInLng = getDouble("checkInLng"),
        verified = getBoolean("verified") ?: false,
    )
}
