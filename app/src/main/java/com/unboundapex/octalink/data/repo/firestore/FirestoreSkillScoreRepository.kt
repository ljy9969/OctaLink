package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.unboundapex.octalink.data.SkillSet
import com.unboundapex.octalink.data.repo.SkillScoreRepository
import com.unboundapex.octalink.data.schema.Collections
import com.unboundapex.octalink.data.schema.SkillScoreDoc
import com.unboundapex.octalink.data.schema.SkillScoreStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore 기반 [SkillScoreRepository].
 *
 * 경로: `members/{memberId}/skillScores/{scoreId}` — Firestore 자동 doc ID.
 * 관장 리뷰 큐는 `collectionGroup("skillScores")` 로 전 회원 PROPOSED 를 한 번에 스캔
 * (firestore.indexes.json + rules 의 recursive wildcard 필요).
 */
class FirestoreSkillScoreRepository : SkillScoreRepository {
    private val db = Firebase.firestore
    private val membersCol = db.collection(Collections.MEMBERS)

    private fun memberScores(memberId: String) =
        membersCol.document(memberId).collection(Collections.SKILL_SCORES)

    override fun observeByMember(memberId: String): Flow<List<SkillScoreDoc>> = callbackFlow {
        val sub = memberScores(memberId)
            .orderBy("evaluatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("OctaLink.SkillScore", "observeByMember error: $memberId", err)
                    close(err)
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toSkillScoreDoc() }.orEmpty())
            }
        awaitClose { sub.remove() }
    }

    override fun observeLatestApproved(memberId: String): Flow<SkillScoreDoc?> = callbackFlow {
        val sub = memberScores(memberId)
            .whereEqualTo("status", SkillScoreStatus.APPROVED.name)
            .orderBy("evaluatedAt", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("OctaLink.SkillScore", "observeLatestApproved error: $memberId", err)
                    close(err)
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.firstOrNull()?.toSkillScoreDoc())
            }
        awaitClose { sub.remove() }
    }

    override fun observePendingAcrossAllMembers(): Flow<List<SkillScoreDoc>> = callbackFlow {
        val sub = db.collectionGroup(Collections.SKILL_SCORES)
            .whereEqualTo("status", SkillScoreStatus.PROPOSED.name)
            .orderBy("evaluatedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("OctaLink.SkillScore", "observePendingAcrossAllMembers error", err)
                    close(err)
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toSkillScoreDoc() }.orEmpty())
            }
        awaitClose { sub.remove() }
    }

    override suspend fun propose(
        memberId: String,
        byUserId: String,
        skills: SkillSet,
    ): SkillScoreDoc {
        val ref = memberScores(memberId).document()
        val data = mapOf(
            "id" to ref.id,
            "memberId" to memberId,
            "byUserId" to byUserId,
            "striking" to skills.striking.toDouble(),
            "grappling" to skills.grappling.toDouble(),
            "stamina" to skills.stamina.toDouble(),
            "technique" to skills.technique.toDouble(),
            "mental" to skills.mental.toDouble(),
            "speed" to skills.speed.toDouble(),
            "evaluatedAt" to FieldValue.serverTimestamp(),
            "status" to SkillScoreStatus.PROPOSED.name,
            "reviewedByMasterId" to null,
            "reviewedAt" to null,
        )
        ref.set(data).await()
        val snap = ref.get().await()
        return snap.toSkillScoreDoc()
            ?: error("propose 후 members/$memberId/skillScores/${ref.id} 조회 실패")
    }

    override suspend fun setStatus(
        memberId: String,
        scoreId: String,
        reviewedByMasterId: String,
        newStatus: SkillScoreStatus,
    ) {
        require(newStatus != SkillScoreStatus.PROPOSED) {
            "setStatus 는 APPROVED/REJECTED 로의 전이만 허용"
        }
        memberScores(memberId).document(scoreId).update(
            mapOf(
                "status" to newStatus.name,
                "reviewedByMasterId" to reviewedByMasterId,
                "reviewedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    override suspend fun delete(memberId: String, scoreId: String) {
        memberScores(memberId).document(scoreId).delete().await()
    }
}
