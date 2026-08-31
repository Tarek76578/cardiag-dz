package dz.cardiag.app.core

import dz.cardiag.app.core.road.MAP_MAX_ZOOM
import dz.cardiag.app.core.road.MAP_MIN_ZOOM
import dz.cardiag.app.core.road.MapProjection
import dz.cardiag.app.core.road.MapSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the equirectangular map projection used by
 * [dz.cardiag.app.ui.components.InteractiveMapView]. We verify that
 * the projection is deterministic, NaN-safe, rejects out-of-range
 * coordinates, clamps zoom into the allowed range, and that cardinal
 * directions map to the expected pixel offsets.
 */
class MapProjectionTest {

    private val size = MapSize(widthPx = 800f, heightPx = 600f)
    private val centerLat = 36.75
    private val centerLon = 3.05

    @Test
    fun `center coordinates project to canvas center`() {
        val p = MapProjection.worldToScreen(
            latitude = centerLat,
            longitude = centerLon,
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 1f,
            size = size
        )
        assertNotNull(p)
        // Equirectangular at zoom=1: ppd = 4, so 0,0 offset -> 400, 300.
        assertEquals(400f, p!!.x, 0.5f)
        assertEquals(300f, p.y, 0.5f)
    }

    @Test
    fun `north of center projects above center`() {
        val p = MapProjection.worldToScreen(
            latitude = 37.75,
            longitude = centerLon,
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 1f,
            size = size
        )
        assertNotNull(p)
        assertTrue(
            "expected y < 300 (north is up), got ${p!!.y}",
            p.y < 300f
        )
    }

    @Test
    fun `east of center projects right of center`() {
        val p = MapProjection.worldToScreen(
            latitude = centerLat,
            longitude = 4.05,
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 1f,
            size = size
        )
        assertNotNull(p)
        assertTrue(
            "expected x > 400 (east is right), got ${p!!.x}",
            p.x > 400f
        )
    }

    @Test
    fun `zero-size canvas returns null`() {
        val p = MapProjection.worldToScreen(
            latitude = centerLat,
            longitude = centerLon,
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 1f,
            size = MapSize(widthPx = 0f, heightPx = 0f)
        )
        assertNull(p)
    }

    @Test
    fun `negative canvas dimensions return null`() {
        val p = MapProjection.worldToScreen(
            latitude = centerLat,
            longitude = centerLon,
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 1f,
            size = MapSize(widthPx = -1f, heightPx = 100f)
        )
        assertNull(p)
    }

    @Test
    fun `NaN latitude returns null`() {
        val p = MapProjection.worldToScreen(
            latitude = Double.NaN,
            longitude = centerLon,
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 1f,
            size = size
        )
        assertNull(p)
    }

    @Test
    fun `NaN longitude returns null`() {
        val p = MapProjection.worldToScreen(
            latitude = centerLat,
            longitude = Double.NaN,
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 1f,
            size = size
        )
        assertNull(p)
    }

    @Test
    fun `latitude above 90 returns null`() {
        val p = MapProjection.worldToScreen(
            latitude = 91.0,
            longitude = centerLon,
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 1f,
            size = size
        )
        assertNull(p)
    }

    @Test
    fun `latitude below -90 returns null`() {
        val p = MapProjection.worldToScreen(
            latitude = -91.0,
            longitude = centerLon,
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 1f,
            size = size
        )
        assertNull(p)
    }

    @Test
    fun `zoom below minimum is clamped not rejected`() {
        val p = MapProjection.worldToScreen(
            latitude = centerLat,
            longitude = centerLon,
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 0.1f,
            size = size
        )
        assertNotNull("zoom 0.1 should clamp to $MAP_MIN_ZOOM, not return null", p)
        // At the clamped minimum zoom the projected point is still the
        // canvas center, regardless of the original zoom value.
        assertEquals(400f, p!!.x, 0.5f)
        assertEquals(300f, p.y, 0.5f)
    }

    @Test
    fun `zoom above maximum is clamped not rejected`() {
        val p = MapProjection.worldToScreen(
            latitude = centerLat,
            longitude = centerLon,
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 20f,
            size = size
        )
        assertNotNull("zoom 20 should clamp to $MAP_MAX_ZOOM, not return null", p)
        // Clamped maximum zoom still centers the point on the canvas.
        assertEquals(400f, p!!.x, 0.5f)
        assertEquals(300f, p.y, 0.5f)
    }

    @Test
    fun `null zoom falls back to minimum zoom`() {
        val p = MapProjection.worldToScreen(
            latitude = centerLat,
            longitude = centerLon,
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = null,
            size = size
        )
        assertNotNull(p)
        assertEquals(400f, p!!.x, 0.5f)
        assertEquals(300f, p.y, 0.5f)
    }
}
