package dz.cardiag.app.core.road

/**
 * Default map center + helpers used by the in-app interactive map widget.
 *
 * The CarDiag app targets the Algerian market, so when the device has no
 * usable GPS fix we fall back to a country-level default (Algiers) rather
 * than centering on the prime meridian / (0, 0) which would be visually
 * confusing and far away from any of the app's users.
 *
 * These values are pure constants and helpers so they can be unit-tested
 * on the JVM without an Android dependency.
 */
object MapDefaults {
    /** Algiers, Algeria — a country-level default. */
    const val DEFAULT_LATITUDE: Double = 36.7538
    const val DEFAULT_LONGITUDE: Double = 3.0588

    /** Human-readable name of the default fallback region. */
    const val DEFAULT_REGION_NAME: String = "Algeria"

    /**
     * Returns a valid map center (latitude, longitude) to use for the
     * interactive map. If [location] is null, contains NaN coordinates,
     * or sits exactly on (0, 0) (commonly a sentinel for "no fix"),
     * the country default is returned. Otherwise the supplied fix is
     * used as-is so the map follows the user's actual position.
     */
    fun effectiveMapCenter(location: CoarseLocation?): Pair<Double, Double> {
        val lat = location?.latitude
        val lon = location?.longitude
        return if (lat == null || lon == null ||
            lat.isNaN() || lon.isNaN() ||
            lat == 0.0 && lon == 0.0
        ) {
            DEFAULT_LATITUDE to DEFAULT_LONGITUDE
        } else {
            lat to lon
        }
    }
}
