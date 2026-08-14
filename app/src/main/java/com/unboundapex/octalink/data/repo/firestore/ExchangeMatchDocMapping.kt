package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.schema.ExchangeMatchDoc
import com.unboundapex.octalink.data.schema.ExchangeMatchStatus
import java.time.Instant

/** [ExchangeMatchDoc] 읽기 매핑. 쓰기·전이는 서버 함수 전용 — read-only. */
internal fun DocumentSnapshot.toExchangeMatchDoc(): ExchangeMatchDoc? {
    val requesterMemberId = getString("requesterMemberId") ?: return null
    val opponentMemberId = getString("opponentMemberId") ?: return null
    fun ts(k: String) = (get(k) as? Timestamp)
        ?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) } ?: Instant.EPOCH
    return ExchangeMatchDoc(
        id = id,
        requesterMemberId = requesterMemberId,
        requesterGymId = getString("requesterGymId") ?: "",
        requesterName = getString("requesterName") ?: "",
        opponentMemberId = opponentMemberId,
        opponentGymId = getString("opponentGymId") ?: "",
        opponentName = getString("opponentName") ?: "",
        weightClass = getString("weightClass")?.let { runCatching { WeightClass.valueOf(it) }.getOrNull() },
        status = runCatching { ExchangeMatchStatus.valueOf(getString("status") ?: "") }
            .getOrDefault(ExchangeMatchStatus.REQUESTED),
        opponentApproved = getBoolean("opponentApproved") ?: false,
        requesterGymApproved = getBoolean("requesterGymApproved") ?: false,
        opponentGymApproved = getBoolean("opponentGymApproved") ?: false,
        scheduledDate = getString("scheduledDate"),
        scheduledTime = getString("scheduledTime"),
        scheduledBand = getString("scheduledBand"),
        requesterSlots = (get("requesterSlots") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        opponentSlots = (get("opponentSlots") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        place = getString("place"),
        winnerMemberId = getString("winnerMemberId"),
        isDraw = getBoolean("isDraw") ?: false,
        createdAt = ts("createdAt"),
        updatedAt = ts("updatedAt"),
    )
}
