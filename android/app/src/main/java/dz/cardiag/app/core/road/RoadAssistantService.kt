package dz.cardiag.app.core.road

/**
 * Aggregates location, nearby-search and hazards providers into a single
 * snapshot the UI can render. The default implementation is fully offline
 * and returns no fabricated businesses, addresses or phone numbers: it
 * exposes the curated categories and lets the user hand off to a real
 * map / search app for actual nearby results.
 *
 * The service is intentionally provider-agnostic. Real Overpass / HERE /
 * Mapbox providers can replace [OfflineRoadDataProvider] by implementing
 * [NearbySearchProvider] / [HazardsProvider] and supplying them here.
 */
class RoadAssistantService(
    private val locationProvider: LocationProvider,
    private val nearbyProvider: NearbySearchProvider = OfflineNearbyProvider(),
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

/** Default offline provider that returns the curated category descriptions. */
class OfflineNearbyProvider : NearbySearchProvider {
    override val displayName: String = OfflineRoadDataProvider.providerDisplayName
    override val isLive: Boolean = OfflineRoadDataProvider.isLive

    override suspend fun search(
        center: CoarseLocation,
        categories: Set<ServiceCategory>,
        radiusMeters: Int,
        language: String
    ): NearbyResult {
        // Honest return: no fake businesses, addresses, phone numbers or
        // ratings. The screen will render the category descriptions and
        // hand off to a real map / search app.
        val lang = if (language == "ar") "ar" else "fr"
        val services = categories.map { category ->
            val description = OfflineRoadDataProvider.categoryDescriptions[category.key]?.get(lang)
                ?: OfflineRoadDataProvider.categoryDescriptions[category.key]?.get("fr")
                ?: category.key
            NearbyService(
                id = "category-${category.key}",
                name = description ?: category.key,
                category = category,
                latitude = center.latitude,
                longitude = center.longitude,
                distanceMeters = null,
                address = null,
                phone = null,
                openingHours = null,
                rating = null,
                source = displayName
            )
        }
        return NearbyResult.Success(services)
    }
}

class OfflineHazardsProvider : HazardsProvider {
    override val displayName: String = OfflineRoadDataProvider.providerDisplayName
    override val isLive: Boolean = OfflineRoadDataProvider.isLive

    override suspend fun fetch(center: CoarseLocation, language: String): HazardsResult {
        // Live hazard data must never be invented. The offline provider
        // intentionally returns no hazards; the screen shows a clear
        // "no live data source" state.
        return HazardsResult.Success(emptyList())
    }
}
