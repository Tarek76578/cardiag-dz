package dz.cardiag.app.core.road

/**
 * Provider abstraction for the Road Assistant. The app ships an [OfflineRoadDataProvider]
 * that returns curated, generic categories so the user can browse the screen and
 * use category labels in Arabic and French without us fabricating any specific
 * business, address or phone number. Real providers (Overpass, HERE, Mapbox, etc.)
 * can be plugged in later by implementing this interface; the screen layer
 * is unaware of the source.
 */
interface LocationProvider {
    /** Returns the most recent coarse fix, or null if no fix is available. */
    suspend fun lastKnown(): CoarseLocation?

    /** Returns the current fix or null if unavailable/timeout. */
    suspend fun current(timeoutMs: Long = 8_000L): CoarseLocation?
}

interface NearbySearchProvider {
    val displayName: String
    val isLive: Boolean
    suspend fun search(
        center: CoarseLocation,
        categories: Set<ServiceCategory>,
        radiusMeters: Int = 5_000,
        language: String
    ): NearbyResult
}

interface HazardsProvider {
    val displayName: String
    val isLive: Boolean
    suspend fun fetch(
        center: CoarseLocation,
        language: String
    ): HazardsResult
}

/** Aggregated result that the screen renders. */
data class RoadAssistantSnapshot(
    val location: CoarseLocation?,
    val services: List<NearbyService>,
    val hazards: List<RoadHazard>,
    val servicesSource: String,
    val hazardsSource: String,
    val servicesLive: Boolean,
    val hazardsLive: Boolean
)
