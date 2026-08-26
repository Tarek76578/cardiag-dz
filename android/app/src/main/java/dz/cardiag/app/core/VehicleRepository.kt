package dz.cardiag.app.core

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class VehicleYearProfileRpc(
    val model_year_id: String,
    val model_year: Int,
    val generation: JsonElement? = null,
    val engines: JsonElement? = null,
    val trims: JsonElement? = null,
    val specifications: JsonElement? = null,
    val ecus: JsonElement? = null,
    val diagnostics: JsonElement? = null,
)

class VehicleRepository {
    private val supabase = SupabaseClient.client

    suspend fun getVehicleProfileByYear(modelYearId: String): VehicleYearProfileRpc? {
        require(modelYearId.isNotBlank())
        return runCatching {
            supabase.from("get_vehicle_profile_by_year").select(
                Columns.list("model_year_id", "model_year", "generation", "engines", "trims", "specifications", "ecus", "diagnostics")
            ) { filter { eq("model_year_id", modelYearId) } }
                .decodeSingleOrNull<VehicleYearProfileRpc>()
        }.getOrNull()
    }

    suspend fun getVehicleYearIds(modelId: String): List<Pair<String, Int>> {
        require(modelId.isNotBlank())
        return supabase.from("vehicle_model_years")
            .select(Columns.list("id", "model_year")) { filter { eq("model_id", modelId) } }
            .decodeList<VehicleYearIdRow>()
            .sortedByDescending { it.modelYear }
            .map { it.id to it.modelYear }
    }

    @Serializable
    private data class VehicleYearIdRow(
        val id: String,
        @kotlinx.serialization.SerialName("model_year") val modelYear: Int,
    )
}
