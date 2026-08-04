package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.unboundapex.octalink.data.repo.GymRepository
import com.unboundapex.octalink.data.schema.Collections
import com.unboundapex.octalink.data.schema.GymDoc
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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
            if (err != null) { close(); return@addSnapshotListener }
            trySend(snap?.toGymDoc())
        }
        awaitClose { sub.remove() }
    }

    override fun observeAll(): Flow<List<GymDoc>> = callbackFlow {
        val sub = col.addSnapshotListener { snap, err ->
            if (err != null) {
                // 규칙 미배포/권한 오류 등에도 앱이 죽지 않게 빈 목록으로 폴백 (가입 화면 가드와 함께).
                android.util.Log.w("OctaLink.Gym", "observeAll error → empty", err)
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snap?.documents?.mapNotNull { it.toGymDoc() }.orEmpty())
        }
        awaitClose { sub.remove() }
    }
}
