package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.unboundapex.octalink.data.schema.RoutineDay
import com.unboundapex.octalink.data.schema.RoutineDrill
import com.unboundapex.octalink.data.schema.WeeklyRoutineDoc
import java.time.Instant

/**
 * Firestore `members/{memberId}/recommendations/{weekId}` doc ↔ [WeeklyRoutineDoc] 매핑.
 *
 * Cloud Function 이 작성한 doc 의 필드 형태 (camelCase) 그대로 읽는다.
 */

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toWeeklyRoutineDoc(): WeeklyRoutineDoc? {
    if (!exists()) return null
    val weekId = getString("weekId") ?: id
    val ts = get("generatedAt") as? Timestamp
    val focus = (get("focusSkills") as? List<String>) ?: emptyList()
    val refIds = (get("referencedCommentIds") as? List<String>) ?: emptyList()
    val daysRaw = (get("days") as? List<Map<String, Any?>>) ?: emptyList()
    val feedback = getString("weeklyFeedback").orEmpty()

    val days = daysRaw.map { d ->
        RoutineDay(
            day = d["day"] as? String ?: "",
            title = d["title"] as? String ?: "",
            drills = ((d["drills"] as? List<Map<String, Any?>>) ?: emptyList()).map { drill ->
                // `youtubeQuery` 우선, 없으면 옛 `exerciseDbKeyword` (Phase 1 초기 doc 호환).
                val query = drill["youtubeQuery"] as? String
                    ?: drill["exerciseDbKeyword"] as? String
                    ?: ""
                RoutineDrill(
                    koName = drill["koName"] as? String ?: "",
                    youtubeQuery = query,
                    desc = drill["desc"] as? String ?: "",
                    sets = drill["sets"] as? String ?: "",
                    durationMin = (drill["durationMin"] as? Number)?.toInt() ?: 0,
                    targetAxis = drill["targetAxis"] as? String ?: "",
                    videoId = drill["videoId"] as? String,
                    done = drill["done"] as? Boolean ?: false,
                    skipped = drill["skipped"] as? Boolean ?: false,
                )
            },
        )
    }

    return WeeklyRoutineDoc(
        weekId = weekId,
        generatedAt = ts?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) }
            ?: Instant.EPOCH,
        focusSkills = focus,
        referencedCommentIds = refIds,
        days = days,
        weeklyFeedback = feedback,
    )
}
