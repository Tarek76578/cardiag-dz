package dz.cardiag.app.core

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

@Serializable
data class VehicleModelYearRow(
    val id: String,
    val modelId: String,
    val generationId: String? = null,
    val modelYear: Int,
    val market: String? = null,
    val dataStatus: String? = null
)

@Serializable
data class VehicleGenerationRow(
    val id: String,
    val modelId: String,
    val name: String,
    val code: String? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val bodyType: String? = null,
    val platformCode: String? = null,
    val descriptionFr: String? = null,
    val descriptionAr: String? = null,
    val imageUrl: String? = null
)

@Serializable
data class VehicleEngineRow(
    val id: String,
    val generationId: String,
    val name: String,
    val engineCode: String? = null,
    val fuelType: String = "unknown",
    val displacementCc: Int? = null,
    val cylinders: Int? = null,
    val aspiration: String? = null,
    val injectionType: String? = null,
    val powerHp: Double? = null,
    val powerKw: Double? = null,
    val torqueNm: Double? = null,
    val transmissionTypes: List<String> = emptyList(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val notesFr: String? = null,
    val notesAr: String? = null
)

@Serializable
data class VehicleTrimRow(
    val id: String,
    val generationId: String,
    val engineId: String? = null,
    val name: String,
    val code: String? = null,
    val drivetrain: String? = null,
    val transmission: String? = null,
    val doors: Int? = null,
    val seats: Int? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val market: String? = null
)

@Serializable
data class VehicleSpecificationRow(
    val id: String,
    val generationId: String,
    val engineId: String? = null,
    val trimId: String? = null,
    val key: String,
    val valueText: String? = null,
    val valueNumber: Double? = null,
    val unit: String? = null
)

@Serializable
data class VehicleEcuRow(
    val id: String,
    val generationId: String,
    val engineId: String? = null,
    val ecuId: String,
    val required: Boolean = true,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val notes: String? = null
)

@Serializable
data class EcuModuleRow(
    val id: String,
    val manufacturer: String? = null,
    val name: String,
    val family: String? = null,
    val ecuType: String = "other",
    val partNumbers: List<String> = emptyList(),
    val protocols: List<String> = emptyList()
)

data class VehicleYearProfile(
    val year: Int,
    val generation: VehicleGenerationRow?,
    val engines: List<VehicleEngineRow>,
    val trims: List<VehicleTrimRow>,
    val specifications: List<VehicleSpecificationRow>,
    val ecus: List<Pair<VehicleEcuRow, EcuModuleRow>>
)

class VehicleRepository {
    private val supabase = SupabaseClient.client

    suspend fun getVehicleProfile(modelId: String): List<VehicleYearProfile> {
        require(modelId.isNotBlank())

        val years = supabase.from("vehicle_model_years").select(
            Columns.list("id", "model_id", "generation_id", "model_year", "market", "data_status")
        ) { filter { eq("model_id", modelId) } }.decodeList<VehicleModelYearRow>()

        val generations = supabase.from("vehicle_generations").select(
            Columns.list("id", "model_id", "name", "code", "year_from", "year_to", "body_type", "platform_code", "description_fr", "description_ar", "image_url")
        ) { filter { eq("model_id", modelId) } }.decodeList<VehicleGenerationRow>()
        val generationById = generations.associateBy { it.id }

        val result = mutableListOf<VehicleYearProfile>()
        for (year in years.distinctBy { it.modelYear }) {
            val generation = year.generationId?.let(generationById::get)
            val generationId = year.generationId
            val engines = if (generationId == null) emptyList() else supabase.from("vehicle_engines").select(
                Columns.list("id", "generation_id", "name", "engine_code", "fuel_type", "displacement_cc", "cylinders", "aspiration", "injection_type", "power_hp", "power_kw", "torque_nm", "transmission_types", "year_from", "year_to", "notes_fr", "notes_ar")
            ) { filter { eq("generation_id", generationId) } }.decodeList<VehicleEngineRow>()

            val trims = if (generationId == null) emptyList() else supabase.from("vehicle_trims").select(
                Columns.list("id", "generation_id", "engine_id", "name", "code", "drivetrain", "transmission", "doors", "seats", "year_from", "year_to", "market")
            ) { filter { eq("generation_id", generationId); eq("market", "DZ") } }.decodeList<VehicleTrimRow>()

            val specifications = if (generationId == null) emptyList() else supabase.from("vehicle_specifications").select(
                Columns.list("id", "generation_id", "engine_id", "trim_id", "key", "value_text", "value_number", "unit")
            ) { filter { eq("generation_id", generationId) } }.decodeList<VehicleSpecificationRow>()

            val ecuLinks = if (generationId == null) emptyList() else supabase.from("vehicle_ecus").select(
                Columns.list("id", "generation_id", "engine_id", "ecu_id", "required", "year_from", "year_to", "notes")
            ) { filter { eq("generation_id", generationId) } }.decodeList<VehicleEcuRow>()
            val ecus = ecuLinks.mapNotNull { link ->
                val ecu = runCatching {
                    supabase.from("ecu_modules").select(
                        Columns.list("id", "manufacturer", "name", "family", "ecu_type", "part_numbers", "protocols")
                    ) { filter { eq("id", link.ecuId) } }.decodeSingle<EcuModuleRow>()
                }.getOrNull()
                ecu?.let { link to it }
            }

            result += VehicleYearProfile(year.modelYear, generation, engines, trims, specifications, ecus)
        }
        return result.sortedByDescending { it.year }
    }
}
