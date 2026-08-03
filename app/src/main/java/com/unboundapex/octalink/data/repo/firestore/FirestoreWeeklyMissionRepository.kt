package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.unboundapex.octalink.data.SessionGym
import com.unboundapex.octalink.data.repo.WeeklyMissionRepository
import com.unboundapex.octalink.data.schema.Collections
import com.unboundapex.octalink.data.schema.WeeklyMissionDoc
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant

/**
 * Firestore 기반 [WeeklyMissionRepository].
 * 경로: `gymSettings/weeklyMission` — 싱글톤 doc.
 *
 * 미설정 상태(doc 없음) 도 정상 — observe 가 null 방출 → UI placeholder.
 */
class FirestoreWeeklyMissionRepository : WeeklyMissionRepository {
    private val db = Firebase.firestore

    /**
     * 체육관별 주간 미션 doc 참조를 호출 시점의 [SessionGym.gymId] 로 구성.
     * gymId 가 설정돼 있으면 `gyms/{gymId}/settings/weeklyMission`, 비어 있으면(마이그레이션 전)
     * 기존 전역 경로 `gymSettings/weeklyMission` 로 폴백해 현재 동작을 보존한다.
     */
    private fun ref() = SessionGym.gymId?.takeIf { it.isNotBlank() }?.let { gid ->
        db.collection(Collections.GYMS).document(gid)
            .collection(Collections.GYM_SETTINGS_SUB)
            .document(Collections.WEEKLY_MISSION_DOC_ID)
    } ?: db.collection(Collections.GYM_SETTINGS).document(Collections.WEEKLY_MISSION_DOC_ID)

    override fun observe(): Flow<WeeklyMissionDoc?> = callbackFlow {
        val sub = ref().addSnapshotListener { snap, err ->
            if (err != null) {
                android.util.Log.e("OctaLink.WeeklyMission", "observe error", err)
                close(err)
                return@addSnapshotListener
            }
            trySend(snap?.toWeeklyMissionDoc())
        }
        awaitClose { sub.remove() }
    }

    override suspend fun update(text: String, byMemberName: String) {
        val data = mapOf(
            "text" to text,
            "updatedAt" to FieldValue.serverTimestamp(),
            "updatedByName" to byMemberName,
        )
        // upsert. 첫 호출 시 doc 생성, 이후 호출은 동일 doc 갱신.
        ref().set(data).await()
    }
}

private fun DocumentSnapshot.toWeeklyMissionDoc(): WeeklyMissionDoc? {
    if (!exists()) return null
    val text = getString("text") ?: return null
    val ts = get("updatedAt") as? Timestamp
    return WeeklyMissionDoc(
        text = text,
        updatedAt = ts?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) }
            ?: Instant.EPOCH,
        updatedByName = getString("updatedByName").orEmpty(),
    )
}
