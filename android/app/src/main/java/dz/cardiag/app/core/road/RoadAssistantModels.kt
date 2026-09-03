package dz.cardiag.app.core.road

import kotlinx.serialization.Serializable

/**
 * Coarse location used by the Road Assistant. We deliberately keep this
 * provider-agnostic and avoid persisting precise coordinates; the only
 * state we keep in memory is the last known coarse fix so the user
 * doesn't see "unknown" after a brief GPS dropout.
 */
@Serializable
data class CoarseLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val capturedAtEpochMs: Long,
    val source: String
)

/** Reason a nearby-search request did not return data. */
enum class NearbyFailure {
    LOCATION_PERMISSION_DENIED,
    LOCATION_UNAVAILABLE,
    LOCATION_TIMEOUT,
    NETWORK_UNAVAILABLE,
    PROVIDER_ERROR,
    NO_RESULTS
}

@Serializable
data class NearbyService(
    val id: String,
    val name: String,
    val category: ServiceCategory,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double?,
    val address: String? = null,
    val phone: String? = null,
    val openingHours: String? = null,
    val rating: Double? = null,
    /** External source this result came from (overpass, here, etc). */
    val source: String
)

enum class ServiceCategory(val key: String) {
    MECHANIC("mechanic"),
    AUTO_ELECTRICIAN("auto_electrician"),
    ROADSIDE_ASSISTANCE("roadside_assistance"),
    SPARE_PARTS("spare_parts"),
    FUEL_STATION("fuel_station"),
    HOSPITAL("hospital"),
    TOWING("towing"),
    OTHER("other");

    companion object {
        // Compatibility aliases used by the interactive map's legacy category filter.
        val GARAGE: ServiceCategory get() = MECHANIC
        val DEPANNAGE: ServiceCategory get() = ROADSIDE_ASSISTANCE
        val PARTS: ServiceCategory get() = SPARE_PARTS
        val TIRE: ServiceCategory get() = SPARE_PARTS

        fun fromKey(key: String?): ServiceCategory =
            entries.firstOrNull { it.key == key } ?: OTHER
    }
}

@Serializable
sealed interface NearbyResult {
    @Serializable
    data class Success(val services: List<NearbyService>) : NearbyResult

    @Serializable
    data class Failure(val failure: NearbyFailure, val message: String? = null) : NearbyResult
}

enum class HazardKind(val key: String) {
    ACCIDENT("accident"),
    ROAD_CLOSURE("road_closure"),
    OBSTACLE("obstacle"),
    BROKEN_DOWN_VEHICLE("broken_down_vehicle"),
    POTHOLE("pothole"),
    OTHER_HAZARD("other");

    companion object {
        fun fromKey(key: String?): HazardKind =
            entries.firstOrNull { it.key == key } ?: OTHER_HAZARD
    }
}

@Serializable
data class RoadHazard(
    val id: String,
    val kind: HazardKind,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val reportedAtEpochMs: Long,
    val source: String
)

@Serializable
sealed interface HazardsResult {
    @Serializable
    data class Success(val hazards: List<RoadHazard>) : HazardsResult

    @Serializable
    data class Failure(val failure: NearbyFailure, val message: String? = null) : NearbyResult
}
