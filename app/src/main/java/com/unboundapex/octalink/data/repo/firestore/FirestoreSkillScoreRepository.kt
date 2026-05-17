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

    /**
     * 6축 점수 저장 정밀도 — 슬라이더 raw float (예: 0.5086712837219238) 을 0.01 단위로 truncate.
     * UI 의 `(value * 100).toInt()` 표시(0~100 정수) 와 1:1 매칭. Firestore doc 가독성/일관성 목적.
     */
    private fun Float.toFirestoreScore(): Double =
        kotlin.math.floor(this.toDouble() * 100) / 100

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

    /** APPROVED + evaluatedAt DESC 의 첫 doc 이 곧 canonical. 단순 최신 우선. */
    override fun observeCanonicalApproved(memberId: String): Flow<SkillScoreDoc?> = callbackFlow {
        val sub = memberScores(memberId)
            .whereEqualTo("status", SkillScoreStatus.APPROVED.name)
            .orderBy("evaluatedAt", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("OctaLink.SkillScore", "observeCanonicalApproved error: $memberId", err)
                    close(err)
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.firstOrNull()?.toSkillScoreDoc())
            }
        awaitClose { sub.remove() }
    }

    override suspend fun getCanonicalApproved(memberId: String): SkillScoreDoc? {
        val snap = memberScores(memberId)
            .whereEqualTo("status", SkillScoreStatus.APPROVED.name)
            .orderBy("evaluatedAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
        return snap.documents.firstOrNull()?.toSkillScoreDoc()
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
            "striking" to skills.striking.toFirestoreScore(),
            "grappling" to skills.grappling.toFirestoreScore(),
            "stamina" to skills.stamina.toFirestoreScore(),
            "technique" to skills.technique.toFirestoreScore(),
            "mental" to skills.mental.toFirestoreScore(),
            "speed" to skills.speed.toFirestoreScore(),
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

    override suspend fun directApprove(
        memberId: String,
        byUserId: String,
        skills: SkillSet,
    ): SkillScoreDoc {
        val ref = memberScores(memberId).document()
        // evaluatedAt == reviewedAt (단일 트랜잭션 시점). Firestore 서버 timestamp.
        val data = mapOf(
            "id" to ref.id,
            "memberId" to memberId,
            "byUserId" to byUserId,
            "striking" to skills.striking.toFirestoreScore(),
            "grappling" to skills.grappling.toFirestoreScore(),
            "stamina" to skills.stamina.toFirestoreScore(),
            "technique" to skills.technique.toFirestoreScore(),
            "mental" to skills.mental.toFirestoreScore(),
            "speed" to skills.speed.toFirestoreScore(),
            "evaluatedAt" to FieldValue.serverTimestamp(),
            "status" to SkillScoreStatus.APPROVED.name,
            "reviewedByMasterId" to byUserId,
            "reviewedAt" to FieldValue.serverTimestamp(),
        )
        ref.set(data).await()
        val snap = ref.get().await()
        return snap.toSkillScoreDoc()
            ?: error("directApprove 후 members/$memberId/skillScores/${ref.id} 조회 실패")
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

    override suspend fun rejectAllPending(memberId: String, byMasterId: String) {
        val pending = memberScores(memberId)
            .whereEqualTo("status", SkillScoreStatus.PROPOSED.name)
            .get()
            .await()
        if (pending.isEmpty) return
        val batch = db.batch()
        val updates = mapOf(
            "status" to SkillScoreStatus.REJECTED.name,
            "reviewedByMasterId" to byMasterId,
            "reviewedAt" to FieldValue.serverTimestamp(),
        )
        pending.documents.forEach { batch.update(it.reference, updates) }
        batch.commit().await()
    }
}
