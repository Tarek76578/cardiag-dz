package dz.cardiag.app.core

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VehicleModelYearRow(val id: String, @SerialName("model_id") val modelId: String, @SerialName("generation_id") val generationId: String? = null, @SerialName("model_year") val modelYear: Int, val market: String? = null, @SerialName("data_status") val dataStatus: String? = null)
@Serializable
data class VehicleYearEngineRow(@SerialName("model_year_id") val modelYearId: String, @SerialName("engine_id") val engineId: String, val market: String? = null, @SerialName("data_status") val dataStatus: String? = null)
@Serializable
data class VehicleGenerationRow(val id: String, @SerialName("model_id") val modelId: String, val name: String, val code: String? = null, @SerialName("year_from") val yearFrom: Int? = null, @SerialName("year_to") val yearTo: Int? = null, @SerialName("body_type") val bodyType: String? = null, @SerialName("platform_code") val platformCode: String? = null, @SerialName("description_fr") val descriptionFr: String? = null, @SerialName("description_ar") val descriptionAr: String? = null, @SerialName("image_url") val imageUrl: String? = null)
@Serializable
data class VehicleEngineRow(val id: String, @SerialName("generation_id") val generationId: String, val name: String, @SerialName("engine_code") val engineCode: String? = null, @SerialName("fuel_type") val fuelType: String = "unknown", @SerialName("displacement_cc") val displacementCc: Int? = null, val cylinders: Int? = null, val aspiration: String? = null, @SerialName("injection_type") val injectionType: String? = null, @SerialName("power_hp") val powerHp: Double? = null, @SerialName("power_kw") val powerKw: Double? = null, @SerialName("torque_nm") val torqueNm: Double? = null, @SerialName("transmission_types") val transmissionTypes: List<String> = emptyList(), @SerialName("year_from") val yearFrom: Int? = null, @SerialName("year_to") val yearTo: Int? = null, @SerialName("notes_fr") val notesFr: String? = null, @SerialName("notes_ar") val notesAr: String? = null)
@Serializable
data class VehicleTrimRow(val id: String, @SerialName("generation_id") val generationId: String, @SerialName("engine_id") val engineId: String? = null, val name: String, val code: String? = null, val drivetrain: String? = null, val transmission: String? = null, val doors: Int? = null, val seats: Int? = null, @SerialName("year_from") val yearFrom: Int? = null, @SerialName("year_to") val yearTo: Int? = null, val market: String? = null)
@Serializable
data class VehicleSpecificationRow(val id: String, @SerialName("generation_id") val generationId: String, @SerialName("engine_id") val engineId: String? = null, @SerialName("trim_id") val trimId: String? = null, val key: String, @SerialName("value_text") val valueText: String? = null, @SerialName("value_number") val valueNumber: Double? = null, val unit: String? = null)
@Serializable
data class VehicleEcuRow(val id: String, @SerialName("generation_id") val generationId: String, @SerialName("engine_id") val engineId: String? = null, @SerialName("ecu_id") val ecuId: String, val required: Boolean = true, @SerialName("year_from") val yearFrom: Int? = null, @SerialName("year_to") val yearTo: Int? = null, val notes: String? = null)
@Serializable
data class EcuModuleRow(val id: String, val manufacturer: String? = null, val name: String, val family: String? = null, @SerialName("ecu_type") val ecuType: String = "other", @SerialName("part_numbers") val partNumbers: List<String> = emptyList(), val protocols: List<String> = emptyList())
@Serializable
data class DiagnosticCodeVehicleRow(val id: String, @SerialName("code_id") val codeId: String, @SerialName("model_id") val modelId: String? = null, @SerialName("generation_id") val generationId: String? = null, @SerialName("engine_id") val engineId: String? = null, @SerialName("ecu_id") val ecuId: String? = null, val applicability: String = "confirmed", @SerialName("notes_fr") val notesFr: String? = null, @SerialName("notes_ar") val notesAr: String? = null)
@Serializable
data class DiagnosticCodeRow(val id: String, val code: String, val system: String? = null, @SerialName("title_fr") val titleFr: String? = null, @SerialName("title_ar") val titleAr: String? = null, @SerialName("description_fr") val descriptionFr: String? = null, @SerialName("description_ar") val descriptionAr: String? = null, val severity: String? = null, val category: String? = null, @SerialName("causes_fr") val causesFr: String? = null, @SerialName("causes_ar") val causesAr: String? = null, @SerialName("diagnostic_steps_fr") val diagnosticStepsFr: String? = null, @SerialName("diagnostic_steps_ar") val diagnosticStepsAr: String? = null, @SerialName("repair_summary_fr") val repairSummaryFr: String? = null, @SerialName("repair_summary_ar") val repairSummaryAr: String? = null)

data class VehicleYearProfile(val year: Int, val generation: VehicleGenerationRow?, val engines: List<VehicleEngineRow>, val trims: List<VehicleTrimRow>, val specifications: List<VehicleSpecificationRow>, val ecus: List<Pair<VehicleEcuRow, EcuModuleRow>>, val diagnostics: List<DiagnosticCodeRow>)

class VehicleRepository {
    private val supabase = SupabaseClient.client

    suspend fun getVehicleProfile(modelId: String): List<VehicleYearProfile> {
        require(modelId.isNotBlank())
        val years = supabase.from("vehicle_model_years").select(Columns.list("id", "model_id", "generation_id", "model_year", "market", "data_status")) { filter { eq("model_id", modelId) } }.decodeList<VehicleModelYearRow>()
        val generations = supabase.from("vehicle_generations").select(Columns.list("id", "model_id", "name", "code", "year_from", "year_to", "body_type", "platform_code", "description_fr", "description_ar", "image_url")) { filter { eq("model_id", modelId) } }.decodeList<VehicleGenerationRow>()
        val generationById = generations.associateBy { it.id }
        return years.sortedByDescending { it.modelYear }.map { year ->
            val generationId = year.generationId
            val generation = generationId?.let(generationById::get)
            val yearLinks = supabase.from("vehicle_year_engines").select(Columns.list("model_year_id", "engine_id", "market", "data_status")) { filter { eq("model_year_id", year.id) } }.decodeList<VehicleYearEngineRow>()
            val engines = yearLinks.map { it.engineId }.distinct().mapNotNull { id -> runCatching { supabase.from("vehicle_engines").select(Columns.list("id", "generation_id", "name", "engine_code", "fuel_type", "displacement_cc", "cylinders", "aspiration", "injection_type", "power_hp", "power_kw", "torque_nm", "transmission_types", "year_from", "year_to", "notes_fr", "notes_ar")) { filter { eq("id", id) } }.decodeSingle<VehicleEngineRow>() }.getOrNull() }
            val engineIds = engines.map { it.id }.toSet()
            val trims = if (generationId == null) emptyList() else supabase.from("vehicle_trims").select(Columns.list("id", "generation_id", "engine_id", "name", "code", "drivetrain", "transmission", "doors", "seats", "year_from", "year_to", "market")) { filter { eq("generation_id", generationId); eq("market", "DZ") } }.decodeList<VehicleTrimRow>().filter { (it.yearFrom == null || year.modelYear >= it.yearFrom) && (it.yearTo == null || year.modelYear <= it.yearTo) && (it.engineId == null || it.engineId in engineIds) }
            val trimIds = trims.map { it.id }.toSet()
            val specifications = if (generationId == null) emptyList() else supabase.from("vehicle_specifications").select(Columns.list("id", "generation_id", "engine_id", "trim_id", "key", "value_text", "value_number", "unit")) { filter { eq("generation_id", generationId) } }.decodeList<VehicleSpecificationRow>().filter { (it.engineId == null || it.engineId in engineIds) && (it.trimId == null || it.trimId in trimIds) }
            val ecuLinks = if (generationId == null) emptyList() else supabase.from("vehicle_ecus").select(Columns.list("id", "generation_id", "engine_id", "ecu_id", "required", "year_from", "year_to", "notes")) { filter { eq("generation_id", generationId) } }.decodeList<VehicleEcuRow>().filter { (it.engineId == null || it.engineId in engineIds) && (it.yearFrom == null || year.modelYear >= it.yearFrom) && (it.yearTo == null || year.modelYear <= it.yearTo) }
            val ecus = ecuLinks.mapNotNull { link -> runCatching { supabase.from("ecu_modules").select(Columns.list("id", "manufacturer", "name", "family", "ecu_type", "part_numbers", "protocols")) { filter { eq("id", link.ecuId) } }.decodeSingle<EcuModuleRow>() }.getOrNull()?.let { link to it } }
            val diagnosticLinks = supabase.from("diagnostic_code_vehicles").select(Columns.list("id", "code_id", "model_id", "generation_id", "engine_id", "ecu_id", "applicability", "notes_fr", "notes_ar")) { filter { eq("model_id", modelId) } }.decodeList<DiagnosticCodeVehicleRow>().filter { (it.generationId == null || it.generationId == generationId) && (it.engineId == null || it.engineId in engineIds) }
            val diagnostics = diagnosticLinks.mapNotNull { link -> runCatching { supabase.from("diagnostic_codes").select(Columns.list("id", "code", "system", "title_fr", "title_ar", "description_fr", "description_ar", "severity", "category", "causes_fr", "causes_ar", "diagnostic_steps_fr", "diagnostic_steps_ar", "repair_summary_fr", "repair_summary_ar")) { filter { eq("id", link.codeId) } }.decodeSingle<DiagnosticCodeRow>() }.getOrNull() }.distinctBy { it.id }
            VehicleYearProfile(year.modelYear, generation, engines, trims, specifications, ecus, diagnostics)
        }
    }
}
