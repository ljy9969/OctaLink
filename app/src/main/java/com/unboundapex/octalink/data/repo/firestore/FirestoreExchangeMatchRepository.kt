package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.unboundapex.octalink.data.repo.ExchangeMatchRepository
import com.unboundapex.octalink.data.schema.Collections
import com.unboundapex.octalink.data.schema.ExchangeMatchDoc
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore 기반 [ExchangeMatchRepository]. 조회는 직접, 생성·전이는 Cloud Function 위임.
 */
class FirestoreExchangeMatchRepository : ExchangeMatchRepository {
    private val db = Firebase.firestore
    private val col = db.collection(Collections.EXCHANGE_MATCHES)
    private val functions = Firebase.functions("asia-northeast3")

    override fun observeMyDuels(memberId: String): Flow<List<ExchangeMatchDoc>> =
        col.whereArrayContains("participantIds", memberId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshotsAsList()

    override fun observeGymDuels(gymId: String): Flow<List<ExchangeMatchDoc>> =
        col.whereArrayContains("gymIds", gymId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshotsAsList()

    override suspend fun requestDuel(opponentMemberId: String) {
        call("requestDuel", mapOf("opponentMemberId" to opponentMemberId))
    }

    override suspend fun approveDuel(matchId: String) {
        call("approveDuel", mapOf("matchId" to matchId))
    }

    override suspend fun rejectDuel(matchId: String) {
        call("rejectDuel", mapOf("matchId" to matchId))
    }

    override suspend fun proposeDuelSlots(matchId: String, slots: List<String>) {
        call("proposeDuelSlots", mapOf("matchId" to matchId, "slots" to slots))
    }

    override suspend fun scheduleDuel(matchId: String, time: String, place: String) {
        call("scheduleDuel", mapOf("matchId" to matchId, "time" to time, "place" to place))
    }

    override suspend fun recordResult(matchId: String, winnerMemberId: String?, isDraw: Boolean) {
        call("recordDuelResult", buildMap {
            put("matchId", matchId)
            put("isDraw", isDraw)
            if (!isDraw && winnerMemberId != null) put("winnerMemberId", winnerMemberId)
        })
    }

    private suspend fun call(name: String, data: Map<String, Any?>) {
        functions.getHttpsCallable(name).call(data).await()
    }

    private fun Query.snapshotsAsList(): Flow<List<ExchangeMatchDoc>> = callbackFlow {
        val sub = addSnapshotListener { snap, err ->
            if (err != null) {
                android.util.Log.w("OctaLink.Exchange", "query error", err)
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snap?.documents?.mapNotNull { it.toExchangeMatchDoc() }.orEmpty())
        }
        awaitClose { sub.remove() }
    }
}
