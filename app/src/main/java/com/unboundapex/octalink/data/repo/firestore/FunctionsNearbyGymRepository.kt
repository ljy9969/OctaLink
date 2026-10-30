package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import com.unboundapex.octalink.data.repo.GymInterestResult
import com.unboundapex.octalink.data.repo.NearbyGymRepository
import com.unboundapex.octalink.data.repo.NearbyGymsResult
import com.unboundapex.octalink.data.repo.parseGymInterestResult
import com.unboundapex.octalink.data.repo.parseNearbyGymsResult
import kotlinx.coroutines.tasks.await

/** [NearbyGymRepository] — Cloud Function `nearbyGyms` / `toggleGymInterest` 호출. */
class FunctionsNearbyGymRepository : NearbyGymRepository {
    private val functions = Firebase.functions("asia-northeast3")

    override suspend fun nearby(radiusKm: Int): NearbyGymsResult =
        parseNearbyGymsResult(call("nearbyGyms", mapOf("radiusKm" to radiusKm)))

    override suspend fun toggleInterest(placeId: String, name: String): GymInterestResult =
        parseGymInterestResult(call("toggleGymInterest", mapOf("placeId" to placeId, "name" to name)))

    private suspend fun call(name: String, data: Map<String, Any?>): Map<*, *> =
        functions.getHttpsCallable(name).call(data).await().data as? Map<*, *>
            ?: error("응답이 비어 있어요.")
}
