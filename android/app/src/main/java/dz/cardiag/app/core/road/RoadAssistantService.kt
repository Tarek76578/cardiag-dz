package dz.cardiag.app.core.road

/**
 * Aggregates the real device location, live nearby-services and hazards
 * providers into the snapshot rendered by Road Assistant. Nearby services
 * now use OpenStreetMap/Overpass by default; no business data is fabricated.
 */
class RoadAssistantService(
    private val locationProvider: LocationProvider,
    private val nearbyProvider: NearbySearchProvider = OverpassNearbyProvider(),
    private val hazardsProvider: HazardsProvider = OfflineHazardsProvider()
) {

    suspend fun snapshot(
        categories: Set<ServiceCategory>,
        radiusMeters: Int = 5_000,
        language: String
    ): RoadAssistantSnapshot {
        val location = runCatching { locationProvider.current() }.getOrNull()
        if (location == null) {
            return RoadAssistantSnapshot(
                location = null,
                services = emptyList(),
                hazards = emptyList(),
                servicesSource = nearbyProvider.displayName,
                hazardsSource = hazardsProvider.displayName,
                servicesLive = nearbyProvider.isLive,
                hazardsLive = hazardsProvider.isLive
            )
        }
        val nearby = when (val r = nearbyProvider.search(location, categories, radiusMeters, language)) {
            is NearbyResult.Success -> r.services
            is NearbyResult.Failure -> emptyList()
        }
        val hazards = when (val r = hazardsProvider.fetch(location, language)) {
            is HazardsResult.Success -> r.hazards
            is HazardsResult.Failure -> emptyList()
        }
        return RoadAssistantSnapshot(
            location = location,
            services = nearby,
            hazards = hazards,
            servicesSource = nearbyProvider.displayName,
            hazardsSource = hazardsProvider.displayName,
            servicesLive = nearbyProvider.isLive,
            hazardsLive = hazardsProvider.isLive
        )
    }
}

class OfflineHazardsProvider : HazardsProvider {
    override val displayName: String = OfflineRoadDataProvider.providerDisplayName
    override val isLive: Boolean = OfflineRoadDataProvider.isLive

    override suspend fun fetch(center: CoarseLocation, language: String): HazardsResult =
        HazardsResult.Success(emptyList())
}
