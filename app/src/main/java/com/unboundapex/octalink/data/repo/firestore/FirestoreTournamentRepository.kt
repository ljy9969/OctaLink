package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.repo.TournamentRepository
import com.unboundapex.octalink.data.schema.Collections
import com.unboundapex.octalink.data.schema.MatchDoc
import com.unboundapex.octalink.data.schema.TournamentDoc
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore 기반 [TournamentRepository].
 *
 * 경로:
 *   - `tournaments/{tournamentId}`
 *   - `tournaments/{tournamentId}/matches/{matchId}`
 *
 * 추첨 작성은 writeBatch — 토너먼트 doc + 매치들을 원자적으로 커밋 (부분 실패 방지).
 * 삭제도 batch — 매치 전체 + 토너먼트 doc 동시 제거.
 */
class FirestoreTournamentRepository : TournamentRepository {
    private val db = Firebase.firestore
    private val tournamentsCol = db.collection(Collections.TOURNAMENTS)

    private fun matchesCol(tournamentId: String) =
        tournamentsCol.document(tournamentId).collection(Collections.MATCHES)

    override fun observeAll(): Flow<List<TournamentDoc>> = callbackFlow {
        val sub = tournamentsCol
            .whereEqualTo("gymId", com.unboundapex.octalink.data.SessionGym.gymId ?: "")
            .orderBy("drawAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("OctaLink.Tournament", "observeAll error", err)
                    close(err)
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toTournamentDoc() }.orEmpty())
            }
        awaitClose { sub.remove() }
    }

    override fun observeById(tournamentId: String): Flow<TournamentDoc?> = callbackFlow {
        val sub = tournamentsCol.document(tournamentId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("OctaLink.Tournament", "observeById error: $tournamentId", err)
                    close(err)
                    return@addSnapshotListener
                }
                trySend(snap?.toTournamentDoc())
            }
        awaitClose { sub.remove() }
    }

    override fun observeMatches(tournamentId: String): Flow<List<MatchDoc>> = callbackFlow {
        val sub = matchesCol(tournamentId)
            .orderBy("round")
            .orderBy("slotIndex")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("OctaLink.Tournament", "observeMatches error: $tournamentId", err)
                    close(err)
                    return@addSnapshotListener
                }
                val docs = snap?.documents?.mapNotNull { it.toMatchDoc(tournamentId) }.orEmpty()
                trySend(docs)
            }
        awaitClose { sub.remove() }
    }

    override suspend fun create(
        title: String,
        weightClass: WeightClass?,
        beltGroup: Belt?,
        matches: List<MatchDoc>,
    ): TournamentDoc {
        val tRef = tournamentsCol.document()
        val tournamentId = tRef.id

        val batch = db.batch()
        // 토너먼트 doc — drawAt 은 서버 timestamp
        batch.set(tRef, mapOf(
            "id" to tournamentId,
            "gymId" to (com.unboundapex.octalink.data.SessionGym.gymId ?: ""),
            "title" to title,
            "weightClass" to weightClass?.name,
            "beltGroup" to beltGroup?.name,
            "drawAt" to FieldValue.serverTimestamp(),
            "finishedAt" to null,
            "champion" to null,
        ))
        // 매치 doc 들 — id 미지정 시 자동 생성
        matches.forEach { m ->
            val mRef = if (m.id.isNotEmpty()) matchesCol(tournamentId).document(m.id)
            else matchesCol(tournamentId).document()
            batch.set(mRef, m.copy(id = mRef.id, tournamentId = tournamentId).toFirestoreMap())
        }
        batch.commit().await()

        val snap = tRef.get().await()
        return snap.toTournamentDoc()
            ?: error("create 후 tournaments/$tournamentId 조회 실패")
    }

    override suspend fun setMatchWinner(
        tournamentId: String,
        matchId: String,
        winnerMemberId: String,
        byMasterId: String,
    ) {
        matchesCol(tournamentId).document(matchId).update(
            mapOf(
                "winnerMemberId" to winnerMemberId,
                "resolvedAt" to FieldValue.serverTimestamp(),
                "resolvedByMasterId" to byMasterId,
            )
        ).await()
    }

    override suspend fun setMatchSlots(
        tournamentId: String,
        matchId: String,
        redMemberId: String?,
        blueMemberId: String?,
        resetWinner: Boolean,
    ) {
        val data = mutableMapOf<String, Any?>(
            "redMemberId" to redMemberId,
            "blueMemberId" to blueMemberId,
        )
        if (resetWinner) {
            data["winnerMemberId"] = null
            data["resolvedAt"] = null
            data["resolvedByMasterId"] = null
        }
        matchesCol(tournamentId).document(matchId).update(data).await()
    }

    override suspend fun finish(tournamentId: String, championMemberId: String) {
        tournamentsCol.document(tournamentId).update(
            mapOf(
                "finishedAt" to FieldValue.serverTimestamp(),
                "champion" to championMemberId,
            )
        ).await()
    }

    override suspend fun delete(tournamentId: String) {
        // 매치 전체 조회 → batch 로 삭제 (최대 500 op/batch — 한 토너먼트 매치는 8강 7개 미만)
        val matches = matchesCol(tournamentId).get().await()
        val batch = db.batch()
        matches.documents.forEach { batch.delete(it.reference) }
        batch.delete(tournamentsCol.document(tournamentId))
        batch.commit().await()
    }
}
