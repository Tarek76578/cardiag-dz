package dz.cardiag.app.core

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.call.body
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class DiagnosticSessionInsert(
    @SerialName("vehicle_model_id") val vehicleModelId: String? = null,
    val complaint: String? = null,
    val language: String = "fr",
    val status: String = "created"
)

@Serializable
data class DiagnosticSession(val id: String, @SerialName("user_id") val userId: String? = null)

class DiagnosticService {
    private val supabase = SupabaseClient.client

    suspend fun createSession(vehicleModelId: String?, complaint: String, language: String): DiagnosticSession {
        supabase.auth.currentUserOrNull() ?: error("Authentication required")
        return withTimeout(10_000) {
            supabase.from("diagnostic_sessions").insert(DiagnosticSessionInsert(vehicleModelId, complaint.ifBlank { null }, language)) {
                select(Columns.list("id", "user_id"))
            }.decodeSingle()
        }
    }

    suspend fun diagnose(
        sessionId: String,
        codes: List<String> = emptyList(),
        symptoms: JsonObject = buildJsonObject {},
        measurements: JsonObject = buildJsonObject {},
        vehicle: JsonObject = buildJsonObject {},
        language: String = "fr"
    ): JsonObject {
        require(language == "ar" || language == "fr") { "language must be ar or fr" }
        val payload = buildJsonObject {
            put("session_id", sessionId)
            put("codes", JsonArray(codes.map { JsonPrimitive(it.uppercase()) }))
            put("symptoms", symptoms)
            put("measurements", measurements)
            put("vehicle", vehicle)
            put("language", language)
        }
        var last: Throwable? = null
        repeat(3) { attempt ->
            try {
                return withTimeout(25_000) {
                    val response = supabase.functions.invoke(function = "diagnose", body = payload)
                    response.body<JsonObject>()
                }
            } catch (e: Throwable) {
                last = e
                if (attempt < 2) delay(500L * (attempt + 1))
            }
        }
        throw IllegalStateException("Diagnostic service unavailable", last)
    }

    suspend fun runDiagnostic(
        vehicleModelId: String?, complaint: String, language: String,
        codes: List<String> = emptyList(), symptoms: JsonObject = buildJsonObject {},
        measurements: JsonObject = buildJsonObject {}, vehicle: JsonObject = buildJsonObject {}
    ): JsonObject {
        val session = createSession(vehicleModelId, complaint, language)
        return diagnose(session.id, codes, symptoms, measurements, vehicle, language)
    }
}
