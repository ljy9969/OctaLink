package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.unboundapex.octalink.data.repo.PublicProfileRepository
import com.unboundapex.octalink.data.schema.Collections
import com.unboundapex.octalink.data.schema.PublicProfileDoc
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Firestore 기반 [PublicProfileRepository]. 읽기 전용. */
class FirestorePublicProfileRepository : PublicProfileRepository {
    private val db = Firebase.firestore
    private val col = db.collection(Collections.PUBLIC_PROFILES)

    override fun observeByGym(gymId: String): Flow<List<PublicProfileDoc>> = callbackFlow {
        if (gymId.isBlank()) { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        val sub = col.whereEqualTo("gymId", gymId).addSnapshotListener { snap, err ->
            if (err != null) {
                android.util.Log.w("OctaLink.PublicProfile", "observeByGym error", err)
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snap?.documents?.mapNotNull { it.toPublicProfileDoc() }.orEmpty())
        }
        awaitClose { sub.remove() }
    }

    override fun observeById(uid: String): Flow<PublicProfileDoc?> = callbackFlow {
        val sub = col.document(uid).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.toPublicProfileDoc())
        }
        awaitClose { sub.remove() }
    }
}
