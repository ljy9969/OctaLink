package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.unboundapex.octalink.data.schema.GymDoc
import java.time.Instant

/**
 * [GymDoc] ↔ Firestore Map 직렬화 (`gyms/{gymId}`).
 * 쓰기는 CREATOR/서버만 (Firestore rules). 클라이언트는 읽기 전용.
 */

internal fun GymDoc.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "branch" to branch,
    "joinCode" to joinCode,
    "staffMemberIds" to staffMemberIds,
    "createdAt" to Timestamp(createdAt.epochSecond, createdAt.nano),
)

internal fun DocumentSnapshot.toGymDoc(): GymDoc? {
    val name = getString("name") ?: return null
    return GymDoc(
        id = id,
        name = name,
        branch = getString("branch"),
        joinCode = getString("joinCode"),
        staffMemberIds = (get("staffMemberIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        createdAt = (get("createdAt") as? Timestamp)
            ?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) }
            ?: Instant.EPOCH,
    )
}
