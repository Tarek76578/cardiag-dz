package dz.cardiag.app.core.road

import kotlin.math.PI
import kotlin.math.cos

/**
 * Pixel dimensions of the map drawing area. Kept as a Compose-free
 * data class so projection math stays unit-testable on the JVM.
 */
data class MapSize(val widthPx: Float, val heightPx: Float)

/**
 * Pixel coordinates relative to the top-left of the map drawing area.
 * Compose-free so it can be asserted in plain JVM tests.
 */
data class MapScreenPoint(val x: Float, val y: Float)

/** Minimum allowed zoom level for the in-app map widget. */
const val MAP_MIN_ZOOM: Float = 1f

/** Maximum allowed zoom level for the in-app map widget. */
const val MAP_MAX_ZOOM: Float = 8f

/**
 * Internal: pixels-per-degree at zoom = 1. Chosen so that the default
 * view comfortably covers the whole country of Algeria while leaving
 * room for context (neighboring North-African coastline).
 */
private const val BASE_PIXELS_PER_DEGREE: Float = 4f

/**
 * Pure helpers that project latitude/longitude pairs onto a flat pixel
 * canvas. The widget itself is rendered with Compose `Canvas`, but the
 * math lives here in a Compose-free `object` so it can be unit-tested
 * on the JVM without an Android dependency.
 *
 * Projection: equirectangular plate-carrée with a cos(centerLat)
 * correction so the map does not stretch horizontally near the poles.
 * The correction is clamped to a small positive floor to keep the math
 * finite when the user happens to pan near ±90°.
 */
object MapProjection {

    /**
     * Project a (latitude, longitude) world coordinate onto the given
     * pixel [size], centered on ([centerLat], [centerLon]) at the given
     * [zoom] (1..[MAP_MAX_ZOOM]). Returns `null` if any input is invalid
     * or if the canvas has no usable area, so callers can fall back to a
     * "no location" rendering rather than draw garbage pixels.
     */
    fun worldToScreen(
        latitude: Double,
        longitude: Double,
        centerLat: Double,
        centerLon: Double,
        zoom: Float?,
        size: MapSize
    ): MapScreenPoint? {
        if (size.widthPx <= 0f || size.heightPx <= 0f) return null
        if (latitude.isNaN() || latitude.isInfinite()) return null
        if (longitude.isNaN() || longitude.isInfinite()) return null
        if (centerLat.isNaN() || centerLat.isInfinite()) return null
        if (centerLon.isNaN() || centerLon.isInfinite()) return null
        if (latitude < -90.0 || latitude > 90.0) return null

        val effectiveZoom = (zoom ?: MAP_MIN_ZOOM).coerceIn(MAP_MIN_ZOOM, MAP_MAX_ZOOM)
        val ppd = BASE_PIXELS_PER_DEGREE * effectiveZoom
        val dLat = latitude - centerLat
        val dLon = longitude - centerLon
        val latCorr = cos(centerLat * PI / 180.0)
        // Keep cos() bounded away from zero so latitudes close to ±90°
        // do not collapse the horizontal axis.
        val safeLatCorr = if (latCorr < 0.01) 0.01 else latCorr
        val x = size.widthPx / 2f + (dLon * ppd * safeLatCorr).toFloat()
        val y = size.heightPx / 2f - (dLat * ppd).toFloat()
        return MapScreenPoint(x = x, y = y)
    }
}
