package dz.cardiag.app

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lightweight value type for vehicle catalog rows. Shared between the
 * UI (Home, Garage, Vehicle profile) and the offline cache so that
 * no parallel DTO exists.
 */
@Serializable
data class UiModel(
    val id: String,
    val name: String,
    val imageUrl: String? = null
)

/** A make in the vehicle catalog. */
@Serializable
data class ExactMake(val id: String, val name: String)

/** A vehicle model row from the catalog. */
@Serializable
data class ExactVehicle(
    val id: String,
    @SerialName("make_id") val makeId: String,
    val name: String,
    @SerialName("year_from") val yearFrom: Int? = null,
    @SerialName("year_to") val yearTo: Int? = null,
    val generation: String? = null,
    @SerialName("image_url") val imageUrl: String? = null
)
