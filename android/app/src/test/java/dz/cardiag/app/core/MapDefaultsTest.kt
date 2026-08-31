package dz.cardiag.app.core

import dz.cardiag.app.core.road.CoarseLocation
import dz.cardiag.app.core.road.MapDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the GPS-map default-center helper. The widget falls
 * back to a country-level (Algiers) center whenever the device has no
 * usable fix, and otherwise uses the real coordinates so the map
 * follows the driver.
 */
class MapDefaultsTest {

    @Test
    fun `null location returns Algiers default`() {
        val (lat, lon) = MapDefaults.effectiveMapCenter(null)
        assertEquals(MapDefaults.DEFAULT_LATITUDE, lat, 0.0)
        assertEquals(MapDefaults.DEFAULT_LONGITUDE, lon, 0.0)
    }

    @Test
    fun `NaN coordinates return Algiers default`() {
        val fix = CoarseLocation(
            latitude = Double.NaN,
            longitude = 3.0,
            accuracyMeters = 10.0,
            capturedAtEpochMs = 0L,
            source = "test"
        )
        val (lat, lon) = MapDefaults.effectiveMapCenter(fix)
        assertEquals(MapDefaults.DEFAULT_LATITUDE, lat, 0.0)
        assertEquals(MapDefaults.DEFAULT_LONGITUDE, lon, 0.0)
    }

    @Test
    fun `zero-zero returns Algiers default`() {
        val fix = CoarseLocation(
            latitude = 0.0,
            longitude = 0.0,
            accuracyMeters = 10.0,
            capturedAtEpochMs = 0L,
            source = "test"
        )
        val (lat, lon) = MapDefaults.effectiveMapCenter(fix)
        assertEquals(MapDefaults.DEFAULT_LATITUDE, lat, 0.0)
        assertEquals(MapDefaults.DEFAULT_LONGITUDE, lon, 0.0)
    }

    @Test
    fun `valid location passes through`() {
        val fix = CoarseLocation(
            latitude = 35.6971,
            longitude = -0.6308,
            accuracyMeters = 8.0,
            capturedAtEpochMs = 1L,
            source = "test"
        )
        val (lat, lon) = MapDefaults.effectiveMapCenter(fix)
        assertEquals(35.6971, lat, 0.0)
        assertEquals(-0.6308, lon, 0.0)
    }
}
