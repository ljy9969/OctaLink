package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.unboundapex.octalink.data.repo.InquiryRepository
import com.unboundapex.octalink.data.schema.Collections
import com.unboundapex.octalink.data.schema.InquiryCategory
import com.unboundapex.octalink.data.schema.InquiryDoc
import com.unboundapex.octalink.data.schema.InquiryStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore 기반 [InquiryRepository].
 *
 * 경로: `inquiries/{id}` — 단일 컬렉션(멀티테넌트 gymId 필터). 비공개 — read 는 작성자 본인 +
 * 운영진만(Firestore rules). 답변 게시는 운영자.
 */
class FirestoreInquiryRepository : InquiryRepository {
    private val db = Firebase.firestore
    private val col = db.collection(Collections.INQUIRIES)

    override fun observeMine(authorId: String): Flow<List<InquiryDoc>> = callbackFlow {
        val sub = col
            .whereEqualTo("gymId", com.unboundapex.octalink.data.SessionGym.gymId ?: "")
            .whereEqualTo("authorId", authorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("OctaLink.Inquiry", "observeMine snapshot error", err)
                    close()
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toInquiryDoc() }.orEmpty())
            }
        awaitClose { sub.remove() }
    }

    override fun observeAllForGym(): Flow<List<InquiryDoc>> = callbackFlow {
        val sub = col
            .whereEqualTo("gymId", com.unboundapex.octalink.data.SessionGym.gymId ?: "")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("OctaLink.Inquiry", "observeAllForGym snapshot error", err)
                    close()
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toInquiryDoc() }.orEmpty())
            }
        awaitClose { sub.remove() }
    }

    override suspend fun create(
        authorId: String,
        authorName: String,
        category: InquiryCategory,
        text: String,
    ): InquiryDoc {
        val ref = col.document()
        val data = mapOf(
            "id" to ref.id,
            "gymId" to (com.unboundapex.octalink.data.SessionGym.gymId ?: ""),
            "authorId" to authorId,
            "authorName" to authorName,
            "category" to category.name,
            "text" to text,
            "status" to InquiryStatus.PENDING.name,
            "answer" to null,
            "answeredBy" to null,
            "createdAt" to FieldValue.serverTimestamp(),
            "answeredAt" to null,
        )
        ref.set(data).await()
        val snap = ref.get().await()
        return snap.toInquiryDoc() ?: error("create 후 inquiries/${ref.id} 조회 실패")
    }

    override suspend fun postAnswer(inquiryId: String, answer: String, answeredBy: String) {
        col.document(inquiryId).update(
            mapOf(
                "answer" to answer,
                "answeredBy" to answeredBy,
                "status" to InquiryStatus.ANSWERED.name,
                "answeredAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    override suspend fun delete(inquiryId: String) {
        col.document(inquiryId).delete().await()
    }
}
