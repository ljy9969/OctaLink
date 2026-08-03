package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.schema.MatchDoc
import com.unboundapex.octalink.data.schema.TournamentDoc
import com.unboundapex.octalink.data.schema.TournamentRound
import java.time.Instant

/**
 * [TournamentDoc] / [MatchDoc] ↔ Firestore Map 직렬화.
 *
 *  - enum 은 `.name` (Belt/WeightClass/TournamentRound)
 *  - 미정 슬롯(redMemberId/blueMemberId) 은 null 로 저장 — Firestore가 누락된 필드와 null 을 모두 받아주므로
 *    화면에서 "?" 부전승 처리는 Firestore 가 아닌 클라이언트 UI 변환 단계에서 수행
 *  - drawAt/resolvedAt 등 [Instant] → [Timestamp]
 */
internal fun DocumentSnapshot.toTournamentDoc(): TournamentDoc? {
    val title = getString("title") ?: return null
    val drawAt = (get("drawAt") as? Timestamp)?.let {
        Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong())
    } ?: return null
    val weightClass = getString("weightClass")?.let { s ->
        runCatching { WeightClass.valueOf(s) }.getOrNull()
    }
    val beltGroup = getString("beltGroup")?.let { s ->
        runCatching { Belt.valueOf(s) }.getOrNull()
    }
    return TournamentDoc(
        id = id,
        gymId = getString("gymId") ?: "",
        title = title,
        weightClass = weightClass,
        beltGroup = beltGroup,
        drawAt = drawAt,
        finishedAt = (get("finishedAt") as? Timestamp)?.let {
            Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong())
        },
        champion = getString("champion"),
    )
}

internal fun TournamentDoc.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "gymId" to gymId,
    "title" to title,
    "weightClass" to weightClass?.name,
    "beltGroup" to beltGroup?.name,
    "drawAt" to Timestamp(drawAt.epochSecond, drawAt.nano),
    "finishedAt" to finishedAt?.let { Timestamp(it.epochSecond, it.nano) },
    "champion" to champion,
)

internal fun DocumentSnapshot.toMatchDoc(tournamentId: String): MatchDoc? {
    val roundName = getString("round") ?: return null
    val round = runCatching { TournamentRound.valueOf(roundName) }.getOrNull() ?: return null
    val slotIndex = getLong("slotIndex")?.toInt() ?: return null
    return MatchDoc(
        id = id,
        tournamentId = tournamentId,
        round = round,
        slotIndex = slotIndex,
        redMemberId = getString("redMemberId"),
        blueMemberId = getString("blueMemberId"),
        winnerMemberId = getString("winnerMemberId"),
        resolvedAt = (get("resolvedAt") as? Timestamp)?.let {
            Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong())
        },
        resolvedByMasterId = getString("resolvedByMasterId"),
    )
}

internal fun MatchDoc.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "tournamentId" to tournamentId,
    "round" to round.name,
    "slotIndex" to slotIndex,
    "redMemberId" to redMemberId,
    "blueMemberId" to blueMemberId,
    "winnerMemberId" to winnerMemberId,
    "resolvedAt" to resolvedAt?.let { Timestamp(it.epochSecond, it.nano) },
    "resolvedByMasterId" to resolvedByMasterId,
)
