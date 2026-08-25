package dz.cardiag.app.core

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CanonicalVehicleRow(
    val id: String,
    @SerialName("make_name") val makeName: String,
    @SerialName("model_name") val modelName: String,
    @SerialName("model_year") val modelYear: Int,
    @SerialName("engine_name") val engineName: String? = null,
    @SerialName("engine_year") val engineYear: Int? = null,
    @SerialName("engine_displacement") val displacementCc: Double? = null,
    @SerialName("engine_cylinders") val cylinders: Double? = null,
    @SerialName("engine_power_hp") val powerHp: Double? = null,
    val transmission: String? = null,
    val drivetrain: String? = null,
    @SerialName("fuel_type") val fuelType: String? = null
)

class VehicleRepository {
    private val supabase = SupabaseClient.client

    suspend fun getModelYearVehicles(modelName: String, year: Int? = null): List<CanonicalVehicleRow> {
        require(modelName.isNotBlank())
        return supabase.from("vehicle_catalog_canonical").select(
            Columns.list(
                "id", "make_name", "model_name", "model_year", "engine_name", "engine_year",
                "engine_displacement", "engine_cylinders", "engine_power_hp", "transmission",
                "drivetrain", "fuel_type"
            )
        ) {
            filter {
                eq("model_name", modelName.trim())
                year?.let { eq("model_year", it) }
            }
        }.decodeList()
    }
}
