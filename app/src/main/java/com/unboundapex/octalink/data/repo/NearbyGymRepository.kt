package com.unboundapex.octalink.data.repo

/**
 * 교류전 지도 — 우리 체육관 주변 격투기 체육관(서버 `nearbyGyms`, 카카오 로컬 검색).
 * 카카오 운영정책상 결과를 앱·DB 에 저장하지 않고 화면에서만 쓴다.
 */
data class NearbyGym(
    val placeId: String,
    val name: String,
    /** 업종 끝 두 단계 (예: "복싱,권투 > 복싱,권투장"). */
    val category: String,
    /** 대표 종목 — 0 복싱, 1 주짓수·유도, 2 MMA·격투기(킥복싱·무에타이·레슬링 등). */
    val sportGroup: Int,
    val district: String,
    val address: String,
    /** 우리 체육관에서의 거리(m). */
    val distanceM: Int,
    val lat: Double,
    val lng: Double,
    /** OctaLink 가입 체육관이면 그 gymId — 파란 점 · 관원에게 바로 신청. */
    val memberGymId: String?,
    /** 교류 요청 수(전체) · 그중 우리 체육관 회원 수 · 내가 요청했는지. */
    val requestCount: Int,
    val myGymRequestCount: Int,
    val requestedByMe: Boolean,
) {
    val sportLabel: String get() = SPORT_LABELS.getOrElse(sportGroup) { "격투기" }
    val kakaoMapUrl: String get() = "https://place.map.kakao.com/$placeId"

    companion object {
        val SPORT_LABELS = listOf("복싱", "주짓수·유도", "MMA·격투기")
    }
}

data class NearbyGymsResult(
    /** 우리 체육관 위치가 아직 등록되지 않음 → 지도 대신 안내. */
    val needsLocation: Boolean,
    val radiusKm: Int,
    val places: List<NearbyGym>,
)

data class GymInterestResult(val requested: Boolean, val count: Int, val myGymCount: Int)

interface NearbyGymRepository {
    /** 우리 체육관 반경 [radiusKm](1·2·3) 안의 격투기 체육관, 가까운 순. */
    suspend fun nearby(radiusKm: Int): NearbyGymsResult

    /** 아직 OctaLink 를 쓰지 않는 체육관에 교류 요청 토글(이미 요청했으면 취소). */
    suspend fun toggleInterest(placeId: String, name: String): GymInterestResult
}

/** callable 응답(Map) → [NearbyGymsResult]. 숫자는 Int/Long/Double 어느 쪽으로 와도 처리. */
fun parseNearbyGymsResult(data: Map<*, *>): NearbyGymsResult {
    fun Any?.num(): Number? = this as? Number
    val places = (data["places"] as? List<*>).orEmpty().mapNotNull { raw ->
        val p = raw as? Map<*, *> ?: return@mapNotNull null
        NearbyGym(
            placeId = p["placeId"] as? String ?: return@mapNotNull null,
            name = p["name"] as? String ?: return@mapNotNull null,
            category = p["category"] as? String ?: "",
            sportGroup = p["sportGroup"].num()?.toInt() ?: 2,
            district = p["district"] as? String ?: "",
            address = p["address"] as? String ?: "",
            distanceM = p["distanceM"].num()?.toInt() ?: 0,
            lat = p["lat"].num()?.toDouble() ?: return@mapNotNull null,
            lng = p["lng"].num()?.toDouble() ?: return@mapNotNull null,
            memberGymId = (p["memberGymId"] as? String)?.takeIf { it.isNotBlank() },
            requestCount = p["count"].num()?.toInt() ?: 0,
            myGymRequestCount = p["myGymCount"].num()?.toInt() ?: 0,
            requestedByMe = p["requestedByMe"] == true,
        )
    }
    return NearbyGymsResult(
        needsLocation = data["needsLocation"] == true,
        radiusKm = data["radiusKm"].num()?.toInt() ?: 1,
        places = places,
    )
}

fun parseGymInterestResult(data: Map<*, *>): GymInterestResult = GymInterestResult(
    requested = data["requested"] == true,
    count = (data["count"] as? Number)?.toInt() ?: 0,
    myGymCount = (data["myGymCount"] as? Number)?.toInt() ?: 0,
)
