package com.unboundapex.octalink.data.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoDistanceTest {
    @Test
    fun meters_gangnamToHongdae_about11km() {
        // 강남역(37.4979, 127.0276) ↔ 홍대입구역(37.5572, 126.9245) 직선거리 ≈ 11.2km
        val m = GeoDistance.meters(37.4979, 127.0276, 37.5572, 126.9245)
        assertEquals(11_200.0, m, 200.0)
    }

    @Test
    fun meters_samePoint_isZero() {
        assertEquals(0.0, GeoDistance.meters(37.5, 127.0, 37.5, 127.0), 0.001)
    }

    @Test
    fun label_metersUnder1km_roundedTo10m() {
        assertEquals("360m", GeoDistance.label(355.0))
        assertEquals("40m", GeoDistance.label(37.0))
    }

    @Test
    fun label_kmUnder10_oneDecimal_dropsTrailingZero() {
        assertEquals("1.2km", GeoDistance.label(1_234.0))
        assertEquals("2km", GeoDistance.label(2_000.0))
        assertEquals("1km", GeoDistance.label(999.6))
    }

    @Test
    fun label_10kmAndOver_wholeKm() {
        assertEquals("11km", GeoDistance.label(11_234.0))
    }
}
