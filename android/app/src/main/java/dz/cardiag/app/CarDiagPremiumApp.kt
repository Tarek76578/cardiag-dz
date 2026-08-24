package dz.cardiag.app

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PremiumVehicle(
    val id: String,
    @SerialName("make_id") val makeId: String,
    val name: String,
    @SerialName("year_from") val yearFrom: Int? = null,
    @SerialName("year_to") val yearTo: Int? = null,
    val generation: String? = null,
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class PremiumMake(val id: String, val name: String)

@androidx.compose.runtime.Composable
fun CarDiagPremiumApp() {
    CarDiagExactApp()
}
