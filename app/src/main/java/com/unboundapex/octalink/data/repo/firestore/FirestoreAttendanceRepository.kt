package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
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
    private val functions = Firebase.functions("asia-northeast3")

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
                    close()
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
                if (err != null) { close(); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull { it.toAttendanceDoc() }.orEmpty())
            }
        awaitClose { sub.remove() }
    }

    override fun observeSince(classDate: LocalDate): Flow<List<AttendanceDoc>> = callbackFlow {
        android.util.Log.d("OctaLink.Attendance", "observeSince start: >= $classDate")
        val sub = db.collectionGroup(Collections.ATTENDANCE)
            .whereGreaterThanOrEqualTo("classDate", classDate.toString())
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.e("OctaLink.Attendance", "observeSince snapshot error", err)
                    close()
                    return@addSnapshotListener
                }
                val docs = snap?.documents?.mapNotNull { it.toAttendanceDoc() }.orEmpty()
                trySend(docs)
            }
        awaitClose { sub.remove() }
    }

    /**
     * 체크인 — Cloud Function `recordAttendance` 호출.
     *
     * 클라이언트 직접 Firestore write 는 rules 에서 차단되고 (`allow create: if false`),
     * Function 이 서버 시각/role/슬롯 윈도우 (`[start-30분, start+10분]`) 검증 후
     * admin SDK 로 doc 생성. 따라서 [memberId] / [classDate] 파라미터는 무시되고
     * 서버가 callerUid + KST 오늘 날짜로 강제. (시그니처는 interface 호환 유지)
     */
    override suspend fun checkIn(
        memberId: String,
        classDefId: String,
        classDate: LocalDate,
    ): AttendanceDoc {
        functions
            .getHttpsCallable("recordAttendance")
            .call(mapOf("classDefId" to classDefId))
            .await()
        // Cloud Function 이 set 한 doc 을 다시 읽어서 반환 — 서버 timestamp 포함.
        val docId = classDate.toString()
        val snap = memberAttendance(memberId).document(docId).get().await()
        return snap.toAttendanceDoc()
            ?: error("checkIn 후 attendance/$docId 조회 실패")
    }

    /**
     * 체크인 취소 — Cloud Function `cancelAttendance` 호출.
     * 본인 doc 만 admin SDK 로 삭제 (rules `allow delete: if false`).
     */
    override suspend fun cancelCheckIn(memberId: String, classDate: LocalDate) {
        functions
            .getHttpsCallable("cancelAttendance")
            .call(emptyMap<String, Any>())
            .await()
    }

    override suspend fun setVerified(memberId: String, classDate: LocalDate, verified: Boolean) {
        memberAttendance(memberId).document(classDate.toString())
            .update("verified", verified).await()
    }
}
