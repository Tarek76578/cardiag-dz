package dz.cardiag.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class DtcGuide(
    val id: String? = null,
    val code: String,
    val system: String? = null,
    val category: String? = null,
    val severity: String? = null,
    @SerialName("description_fr") val descriptionFr: String? = null,
    @SerialName("causes_fr") val causesFr: String? = null,
    @SerialName("diagnostic_steps_fr") val diagnosticStepsFr: String? = null,
    @SerialName("repair_summary_fr") val repairSummaryFr: String? = null
)

@Serializable
data class DiagnosticSession(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("vehicle_model_id") val vehicleModelId: String? = null,
    val language: String = "fr",
    val status: String = "created",
    val complaint: String? = null
)

class GuidedDiagnosisActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modelId = intent.getStringExtra("model_id")
        val modelName = intent.getStringExtra("model_name") ?: "Véhicule"
        val initialCode = intent.getStringExtra("dtc_code")?.trim()?.uppercase().orEmpty()
        setContent { GuidedDiagnosisScreen(modelId, modelName, initialCode) }
    }
}

@Composable
private fun GuidedDiagnosisScreen(modelId: String?, modelName: String, initialCode: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(initialCode) }
    var guide by remember { mutableStateOf<DtcGuide?>(null) }
    var loading by remember { mutableStateOf(false) }
    var aiLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var aiResult by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }

    fun load() {
        val normalized = code.trim().uppercase()
        if (normalized.isBlank()) {
            error = "Entrez un code DTC, par exemple P0301."
            guide = null
            searched = false
            return
        }
        scope.launch {
            loading = true
            error = null
            guide = null
            aiResult = null
            searched = true
            runCatching {
                SupabaseClient.client.from("diagnostic_codes").select(
                    Columns.list("id", "code", "system", "category", "severity", "description_fr", "causes_fr", "diagnostic_steps_fr", "repair_summary_fr")
                ) { filter { eq("code", normalized) } }.decodeList<DtcGuide>().firstOrNull()
            }.onSuccess {
                guide = it
                if (it == null) error = "Code $normalized introuvable dans la base DTC."
            }.onFailure {
                error = "Impossible de charger le DTC depuis Supabase: ${it.message ?: "erreur réseau"}"
            }
            loading = false
        }
    }

    fun runAi() {
        val normalized = code.trim().uppercase()
        if (normalized.isBlank() || guide == null) {
            error = "Chargez d'abord un DTC valide."
            return
        }
        scope.launch {
            aiLoading = true
            error = null
            aiResult = null
            runCatching {
                val user = SupabaseClient.client.gotrue.currentSessionOrNull()?.user
                    ?: error("AUTH_REQUIRED")
                val session = SupabaseClient.client.from("diagnostic_sessions").insert(
                    DiagnosticSession(userId = user.id, vehicleModelId = modelId, language = "fr", status = "running")
                ) { select() }.decodeSingle<DiagnosticSession>()
                val sessionId = session.id ?: error("SESSION_CREATE_FAILED")
                val response = SupabaseClient.client.functions.invoke(
                    function = "diagnose",
                    body = buildJsonObject {
                        put("session_id", sessionId)
                        put("codes", kotlinx.serialization.json.buildJsonArray { add(normalized) })
                        put("symptoms", buildJsonObject { put("source", "guided_dtc") })
                        put("measurements", buildJsonObject {})
                        put("vehicle", buildJsonObject {
                            put("model_id", modelId ?: "")
                            put("model_name", modelName)
                        })
                        put("language", "fr")
                    }
                )
                val raw = response.bodyAsText()
                val json = Json.parseToJsonElement(raw).jsonObject
                if (json["ok"]?.toString() != "true") {
                    val message = json["error"]?.toString()?.trim('"') ?: "AI diagnostic failed"
                    error(message)
                }
                raw
            }.onSuccess { aiResult = it }
                .onFailure { error = when (it.message) { "AUTH_REQUIRED" -> "Connectez-vous pour utiliser le diagnostic AI."; "SESSION_CREATE_FAILED" -> "Impossible de créer la session de diagnostic."; else -> it.message ?: "Erreur du moteur AI." } }
            aiLoading = false
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Diagnostic guidé") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(modelName, style = MaterialTheme.typography.titleLarge)
                Text(if (modelId != null) "Véhicule sélectionné • contexte actif" else "Diagnostic sans OBD • aucun adaptateur requis", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Diagnostic sans OBD", style = MaterialTheme.typography.titleMedium); Text("Entrez un DTC connu pour consulter la base CarDiag, puis lancez l'analyse AI. Aucun ELM327 n'est nécessaire pour cette étape.", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            item { OutlinedTextField(value = code, onValueChange = { code = it.uppercase(); if (searched) { searched = false; guide = null; aiResult = null; error = null } }, modifier = Modifier.fillMaxWidth(), label = { Text("Code DTC") }, placeholder = { Text("Ex. P0301") }, singleLine = true) }
            item { Button(onClick = ::load, enabled = code.isNotBlank() && !loading && !aiLoading, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Chargement…" else "Rechercher le DTC") } }
            item { OutlinedButton(onClick = { context.startActivity(Intent(context, ObdScannerActivity::class.java).apply { putExtra("model_id", modelId); putExtra("model_name", modelName); putExtra("dtc_code", code.ifBlank { null }) }) }, enabled = !loading && !aiLoading, modifier = Modifier.fillMaxWidth()) { Text("Passer au diagnostic OBD / Live Data") } }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            guide?.let { d ->
                item { DtcCard("${d.code} • ${d.system ?: "Système inconnu"}", d.descriptionFr ?: "Description non disponible") }
                item { DtcCard("Sévérité / catégorie", listOfNotNull(d.severity, d.category).joinToString(" • ").ifBlank { "Non renseigné" }) }
                item { DtcCard("Causes probables", d.causesFr ?: "Non renseignées") }
                item { DtcCard("Étapes de diagnostic", d.diagnosticStepsFr ?: "Non renseignées") }
                item { DtcCard("Réparation", d.repairSummaryFr ?: "Non renseignée") }
                item { Button(onClick = ::runAi, enabled = !aiLoading && !loading, modifier = Modifier.fillMaxWidth()) { Text(if (aiLoading) "Analyse AI en cours…" else "Analyser avec CarDiag AI") } }
            }
            aiResult?.let { raw ->
                item { AiResultCard(raw) }
            }
        }
    }
}

@Composable private fun DtcCard(title: String, body: String) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body) } } }

@Composable private fun AiResultCard(raw: String) {
    val obj = runCatching { Json.parseToJsonElement(raw).jsonObject["diagnosis"]?.jsonObject }.getOrNull()
    val summary = obj?.get("summary")?.toString()?.trim('"') ?: "Analyse AI reçue."
    val severity = obj?.get("severity")?.toString()?.trim('"') ?: "unknown"
    val confidence = obj?.get("confidence")?.toString()?.trim('"') ?: "-"
    val causes = obj?.get("likely_causes")?.toString() ?: "[]"
    val tests = obj?.get("recommended_tests")?.toString() ?: "[]"
    val repairs = obj?.get("repair_guidance")?.toString() ?: "[]"
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("CarDiag AI", style = MaterialTheme.typography.titleLarge)
        Text(summary, style = MaterialTheme.typography.bodyLarge)
        Text("Sévérité: $severity • Confiance: $confidence")
        Text("Causes probables: $causes")
        Text("Tests recommandés: $tests")
        Text("Conseils de réparation: $repairs")
        Text("L'AI assiste le diagnostic; les mesures OBD et les contrôles physiques restent nécessaires avant remplacement d'une pièce.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}
