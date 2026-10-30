package com.unboundapex.octalink.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyGymParseTest {
    private fun place(vararg extra: Pair<String, Any?>) = mapOf(
        "placeId" to "123", "name" to "강남복싱", "category" to "복싱,권투 > 복싱,권투장",
        "sportGroup" to 0, "district" to "강남구", "address" to "서울 강남구 강남대로 1",
        "distanceM" to 360L, "lat" to 37.4979, "lng" to 127, // 정수로 온 좌표도 처리
        "memberGymId" to null, "count" to 2, "myGymCount" to 1, "requestedByMe" to true,
    ) + extra

    @Test
    fun parsesPlaces_numbersOfAnyType() {
        val r = parseNearbyGymsResult(mapOf("needsLocation" to false, "radiusKm" to 1, "places" to listOf(place())))
        assertFalse(r.needsLocation)
        assertEquals(1, r.radiusKm)
        val g = r.places.single()
        assertEquals("123", g.placeId)
        assertEquals(360, g.distanceM)
        assertEquals(127.0, g.lng, 0.0)
        assertNull(g.memberGymId)
        assertEquals(2, g.requestCount)
        assertEquals(1, g.myGymRequestCount)
        assertTrue(g.requestedByMe)
        assertEquals("복싱", g.sportLabel)
        assertEquals("https://place.map.kakao.com/123", g.kakaoMapUrl)
    }

    @Test
    fun skipsPlacesWithoutIdOrCoords_keepsMemberGymId() {
        val r = parseNearbyGymsResult(mapOf("places" to listOf(
            place("placeId" to null),
            place("lat" to null),
            place("placeId" to "9", "memberGymId" to "octagon"),
        )))
        assertEquals(listOf("9"), r.places.map { it.placeId })
        assertEquals("octagon", r.places.single().memberGymId)
    }

    @Test
    fun needsLocation_andInterestResult() {
        assertTrue(parseNearbyGymsResult(mapOf("needsLocation" to true, "places" to emptyList<Any>())).needsLocation)
        assertEquals(
            GymInterestResult(requested = true, count = 3, myGymCount = 2),
            parseGymInterestResult(mapOf("requested" to true, "count" to 3L, "myGymCount" to 2)),
        )
    }
}
