package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.unboundapex.octalink.data.repo.AttendanceRepository
import com.unboundapex.octalink.data.schema.AttendanceDoc
import com.unboundapex.octalink.data.schema.Collections
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Firestore 기반 [AttendanceRepository].
 *
 * 경로: `members/{memberId}/attendance/{classDate}` — classDate ISO 문자열이 doc ID.
 *
 * 동료 출석 조회는 `collectionGroup("attendance")` + `whereEqualTo("classDate", ...)` —
 * 모든 회원의 서브컬렉션을 한 번에 스캔. classDate single-field index 가 자동 생성됨
 * (firestore.indexes.json 에 명시).
 */
class FirestoreAttendanceRepository : AttendanceRepository {
    private val db = Firebase.firestore
    private val membersCol = db.collection(Collections.MEMBERS)

    /** 특정 회원의 attendance 서브컬렉션 reference. */
    private fun memberAttendance(memberId: String) =
        membersCol.document(memberId).collection(Collections.ATTENDANCE)

    override fun observeByDate(classDate: LocalDate): Flow<List<AttendanceDoc>> = callbackFlow {
        android.util.Log.d("OctaLink.Attendance", "observeByDate start: $classDate")
        val sub = db.collectionGroup(Collections.ATTENDANCE)
            .whereEqualTo("classDate", classDate.toString())
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("OctaLink.Attendance", "observeByDate snapshot error", err)
                    close(err)
                    return@addSnapshotListener
                }
                val docs = snap?.documents?.mapNotNull { it.toAttendanceDoc() }.orEmpty()
                android.util.Log.d(
                    "OctaLink.Attendance",
                    "observeByDate emit: rawSize=${snap?.documents?.size}, parsedSize=${docs.size}, classDate=$classDate",
                )
                trySend(docs)
            }
        awaitClose {
            android.util.Log.d("OctaLink.Attendance", "observeByDate close: $classDate")
            sub.remove()
        }
    }

    override fun observeByMember(memberId: String): Flow<List<AttendanceDoc>> = callbackFlow {
        val sub = memberAttendance(memberId)
            .orderBy("classDate", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull { it.toAttendanceDoc() }.orEmpty())
            }
        awaitClose { sub.remove() }
    }

    override suspend fun checkIn(
        memberId: String,
        classDefId: String,
        classDate: LocalDate,
    ): AttendanceDoc {
        val docId = classDate.toString()
        val data = mapOf(
            "id" to docId,
            "memberId" to memberId,
            "classDefId" to classDefId,
            "classDate" to docId,
            "checkInAt" to FieldValue.serverTimestamp(),
            "verified" to false,
        )
        memberAttendance(memberId).document(docId).set(data).await()
        // 작성 직후 1회 조회해서 서버 timestamp 포함된 doc 반환
        val snap = memberAttendance(memberId).document(docId).get().await()
        return snap.toAttendanceDoc()
            ?: error("checkIn 후 attendance/$docId 조회 실패")
    }

    override suspend fun cancelCheckIn(memberId: String, classDate: LocalDate) {
        memberAttendance(memberId).document(classDate.toString()).delete().await()
    }
}
