package dz.cardiag.app.core

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.call.body
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
data class DiagnosticSession(
    val id: String,
    @SerialName("user_id") val userId: String? = null
)

class DiagnosticService {
    private val supabase = SupabaseClient.client

    suspend fun createSession(
        vehicleModelId: String?,
        complaint: String,
        language: String
    ): DiagnosticSession {
        supabase.auth.currentUserOrNull()
            ?: error("Authentication required")

        return supabase.from("diagnostic_sessions")
            .insert(
                DiagnosticSessionInsert(
                    vehicleModelId = vehicleModelId,
                    complaint = complaint,
                    language = language
                )
            ) {
                select(Columns.list("id", "user_id"))
            }
            .decodeSingle<DiagnosticSession>()
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
            put("codes", JsonArray(codes.map { JsonPrimitive(it) }))
            put("symptoms", symptoms)
            put("measurements", measurements)
            put("vehicle", vehicle)
            put("language", language)
        }

        val response = supabase.functions.invoke(
            function = "diagnose",
            body = payload
        )
        return response.body<JsonObject>()
    }

    suspend fun runDiagnostic(
        vehicleModelId: String?,
        complaint: String,
        language: String,
        codes: List<String> = emptyList(),
        symptoms: JsonObject = buildJsonObject {},
        measurements: JsonObject = buildJsonObject {},
        vehicle: JsonObject = buildJsonObject {}
    ): JsonObject {
        val session = createSession(vehicleModelId, complaint, language)
        return diagnose(
            sessionId = session.id,
            codes = codes,
            symptoms = symptoms,
            measurements = measurements,
            vehicle = vehicle,
            language = language
        )
    }
}
