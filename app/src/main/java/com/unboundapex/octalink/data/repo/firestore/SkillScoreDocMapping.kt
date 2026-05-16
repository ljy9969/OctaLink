package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.unboundapex.octalink.data.schema.SkillScoreDoc
import com.unboundapex.octalink.data.schema.SkillScoreStatus
import java.time.Instant

/**
 * [SkillScoreDoc] ↔ Firestore Map 직렬화.
 *  - enum 은 `.name` (예: "PROPOSED" / "APPROVED" / "REJECTED")
 *  - 점수 6축은 [Double] 로 저장 (Firestore Number 자연 타입). 읽을 때 Float 캐스팅.
 *  - [Instant] → [Timestamp]
 *  - reviewedByMasterId/reviewedAt — APPROVED/REJECTED 전이 후에만 값이 채워지고 그 전엔 null
 */
internal fun DocumentSnapshot.toSkillScoreDoc(): SkillScoreDoc? {
    val memberId = getString("memberId") ?: return null
    val byUserId = getString("byUserId") ?: return null
    val evaluatedAt = (get("evaluatedAt") as? Timestamp)?.let {
        Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong())
    } ?: return null
    val status = runCatching {
        SkillScoreStatus.valueOf(getString("status") ?: "")
    }.getOrDefault(SkillScoreStatus.PROPOSED)
    return SkillScoreDoc(
        id = id,
        memberId = memberId,
        byUserId = byUserId,
        striking = (getDouble("striking") ?: 0.0).toFloat(),
        grappling = (getDouble("grappling") ?: 0.0).toFloat(),
        stamina = (getDouble("stamina") ?: 0.0).toFloat(),
        technique = (getDouble("technique") ?: 0.0).toFloat(),
        mental = (getDouble("mental") ?: 0.0).toFloat(),
        speed = (getDouble("speed") ?: 0.0).toFloat(),
        evaluatedAt = evaluatedAt,
        status = status,
        reviewedByMasterId = getString("reviewedByMasterId"),
        reviewedAt = (get("reviewedAt") as? Timestamp)?.let {
            Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong())
        },
    )
}
