package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.firestore.DocumentSnapshot
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.schema.PublicProfileDoc

/** [PublicProfileDoc] 읽기 매핑 (`publicProfiles/{uid}`). 쓰기는 서버 트리거 전용 — read-only. */
internal fun DocumentSnapshot.toPublicProfileDoc(): PublicProfileDoc? {
    val gymId = getString("gymId") ?: return null
    val name = getString("name") ?: return null
    return PublicProfileDoc(
        id = id,
        gymId = gymId,
        name = name,
        gender = getString("gender"),
        weightClass = runCatching { WeightClass.valueOf(getString("weightClass") ?: "") }
            .getOrDefault(WeightClass.LIGHT),
        belt = runCatching { Belt.valueOf(getString("belt") ?: "") }.getOrDefault(Belt.WHITE),
        avatarId = getString("avatarId") ?: "",
        careerStartYm = getString("careerStartYm"),
        exchangeWins = (getLong("exchangeWins") ?: 0L).toInt(),
        exchangeLosses = (getLong("exchangeLosses") ?: 0L).toInt(),
        exchangeDraws = (getLong("exchangeDraws") ?: 0L).toInt(),
    )
}
