package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.unboundapex.octalink.data.repo.CommentRepository
import com.unboundapex.octalink.data.schema.Collections
import com.unboundapex.octalink.data.schema.CommentDoc
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Firestore 기반 [CommentRepository].
 *
 * 경로: `members/{toMemberId}/comments/{commentId}` — 수신 회원 서브컬렉션.
 * 정렬: classDate DESC.
 */
class FirestoreCommentRepository : CommentRepository {
    private val db = Firebase.firestore
    private val membersCol = db.collection(Collections.MEMBERS)

    private fun memberComments(toMemberId: String) =
        membersCol.document(toMemberId).collection(Collections.COMMENTS)

    override fun observeByMember(toMemberId: String): Flow<List<CommentDoc>> = callbackFlow {
        android.util.Log.d("OctaLink.Comments", "observeByMember start: $toMemberId")
        val sub = memberComments(toMemberId)
            .orderBy("classDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("OctaLink.Comments", "observeByMember snapshot error", err)
                    close(err)
                    return@addSnapshotListener
                }
                val docs = snap?.documents?.mapNotNull { it.toCommentDoc() }.orEmpty()
                trySend(docs)
            }
        awaitClose { sub.remove() }
    }

    override suspend fun create(
        toMemberId: String,
        byMasterId: String,
        byMasterName: String,
        text: String,
        classDate: LocalDate,
    ): CommentDoc {
        val ref = memberComments(toMemberId).document()
        val data = mapOf(
            "id" to ref.id,
            "toMemberId" to toMemberId,
            "byMasterId" to byMasterId,
            "byMasterName" to byMasterName,
            "text" to text,
            "classDate" to classDate.toString(),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        ref.set(data).await()
        val snap = ref.get().await()
        return snap.toCommentDoc()
            ?: error("create 후 members/$toMemberId/comments/${ref.id} 조회 실패")
    }

    override suspend fun delete(toMemberId: String, commentId: String) {
        memberComments(toMemberId).document(commentId).delete().await()
    }
}
