package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.unboundapex.octalink.data.repo.GymRepository
import com.unboundapex.octalink.data.schema.Collections
import com.unboundapex.octalink.data.schema.GymDoc
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore 기반 [GymRepository]. `gyms/{gymId}`.
 * 쓰기는 CREATOR/서버만(rules). 클라이언트는 읽기 전용.
 */
class FirestoreGymRepository : GymRepository {
    private val db = Firebase.firestore
    private val col = db.collection(Collections.GYMS)

    override fun observeById(gymId: String): Flow<GymDoc?> = callbackFlow {
        if (gymId.isBlank()) { trySend(null); awaitClose { }; return@callbackFlow }
        val sub = col.document(gymId).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.toGymDoc())
        }
        awaitClose { sub.remove() }
    }

    override fun observeAll(): Flow<List<GymDoc>> = callbackFlow {
        val sub = col.addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.documents?.mapNotNull { it.toGymDoc() }.orEmpty())
        }
        awaitClose { sub.remove() }
    }

    override suspend fun findByJoinCode(joinCode: String): GymDoc? {
        val normalized = joinCode.trim().uppercase()
        if (normalized.isEmpty()) return null
        val snap = col.whereEqualTo("joinCode", normalized).limit(1).get().await()
        return snap.documents.firstOrNull()?.toGymDoc()
    }
}
