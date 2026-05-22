package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.unboundapex.octalink.data.repo.WeeklyRoutineRepository
import com.unboundapex.octalink.data.schema.Collections
import com.unboundapex.octalink.data.schema.WeeklyRoutineDoc
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.IsoFields

/**
 * Firestore + Cloud Function 결합 [WeeklyRoutineRepository] 구현.
 *
 *  - `observeCurrentWeek` : 현재 ISO 주의 doc 을 실시간 구독.
 *  - `generate`           : Cloud Function `generateWeeklyRoutine` 호출 → Firestore 갱신.
 *  - `setDrillState`      : 드릴 단위 done/skipped 토글 (Phase 2).
 *
 * 주키 계산은 클라이언트/서버 동일 로직 — KST 기준 ISO 8601. 둘이 어긋나면 같은 주가
 * 다른 doc 으로 분기될 수 있어, 양쪽 모두 KST 단일 기준.
 */
class FirestoreWeeklyRoutineRepository : WeeklyRoutineRepository {
    private val db = Firebase.firestore
    private val functions = Firebase.functions("asia-northeast3")

    override fun observeCurrentWeek(memberId: String): Flow<WeeklyRoutineDoc?> = callbackFlow {
        val weekId = currentIsoWeekKey()
        val ref = db.collection(Collections.MEMBERS)
            .document(memberId)
            .collection(Collections.WEEKLY_ROUTINES)
            .document(weekId)

        val sub = ref.addSnapshotListener { snap, err ->
            if (err != null) {
                android.util.Log.e(TAG, "observeCurrentWeek error", err)
                close(err)
                return@addSnapshotListener
            }
            trySend(snap?.toWeeklyRoutineDoc())
        }
        awaitClose { sub.remove() }
    }

    override suspend fun generate(memberId: String, force: Boolean) {
        functions
            .getHttpsCallable("generateWeeklyRoutine")
            .call(mapOf("memberId" to memberId, "force" to force))
            .await()
    }

    override suspend fun setDrillState(
        memberId: String,
        weekId: String,
        dayIndex: Int,
        drillIndex: Int,
        done: Boolean,
        skipped: Boolean,
    ) {
        val ref = db.collection(Collections.MEMBERS)
            .document(memberId)
            .collection(Collections.WEEKLY_ROUTINES)
            .document(weekId)

        // days 배열 전체 update — Firestore 가 배열 내 항목 단위 patch 를 지원하지 않으므로
        // 전체 읽기 → 수정 → 쓰기. 동일 회원이 두 기기에서 동시에 토글하는 경우는 무시 가능.
        val snap = ref.get().await()
        @Suppress("UNCHECKED_CAST")
        val daysRaw = (snap.get("days") as? List<Map<String, Any?>>)?.toMutableList() ?: return
        if (dayIndex !in daysRaw.indices) return

        val day = daysRaw[dayIndex].toMutableMap()
        @Suppress("UNCHECKED_CAST")
        val drills = (day["drills"] as? List<Map<String, Any?>>)?.toMutableList() ?: return
        if (drillIndex !in drills.indices) return

        val drill = drills[drillIndex].toMutableMap()
        drill["done"] = done
        drill["skipped"] = skipped
        drills[drillIndex] = drill
        day["drills"] = drills
        daysRaw[dayIndex] = day

        ref.update("days", daysRaw).await()
    }

    companion object {
        private const val TAG = "OctaLink.WeeklyRoutine"
    }
}

/**
 * KST 기준 현재 ISO 8601 주키 — Cloud Function 의 `isoWeekKey()` 와 동일 결과.
 * 형식: `"yyyy-W##"` (예: `"2026-W22"`).
 */
internal fun currentIsoWeekKey(now: ZonedDateTime = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))): String {
    val week = now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val year = now.get(IsoFields.WEEK_BASED_YEAR)
    return "$year-W${week.toString().padStart(2, '0')}"
}

