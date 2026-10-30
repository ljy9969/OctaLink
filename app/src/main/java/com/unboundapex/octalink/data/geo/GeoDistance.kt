package com.unboundapex.octalink.data.geo

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 체육관 사이 거리 계산·표시 — 교류전 지도(우리 체육관 기준 거리). */
object GeoDistance {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** 두 좌표(WGS84) 사이 직선거리(m) — 하버사인. */
    fun meters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** "360m"(1km 미만, 10m 단위) · "1.2km"(10km 미만, 소수 한 자리) · "11km". */
    fun label(meters: Double): String {
        val m10 = (meters / 10).roundToInt() * 10
        if (m10 < 1_000) return "${m10}m"
        val km = meters / 1_000
        if (km >= 10) return "${km.roundToInt()}km"
        val oneDecimal = String.format(Locale.US, "%.1f", km)
        return oneDecimal.removeSuffix(".0") + "km"
    }
}
