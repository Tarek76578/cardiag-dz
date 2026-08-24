package dz.cardiag.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.DiagnosticService
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AiSymptomDiagnosisActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modelId = intent.getStringExtra("model_id")
        val modelName = intent.getStringExtra("model_name") ?: "Véhicule"
        setContent { AiSymptomDiagnosisScreen(modelId, modelName) }
    }
}

@Composable
private fun AiSymptomDiagnosisScreen(modelId: String?, modelName: String) {
    val scope = rememberCoroutineScope()
    var complaint by remember { mutableStateOf("") }
    var whenHappens by remember { mutableStateOf("") }
    var engineState by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<JsonObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("AI Diagnosis") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(modelName, style = MaterialTheme.typography.headlineSmall)
                Text("Diagnostic sans OBD • analyse des symptômes", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { OutlinedTextField(complaint, { complaint = it; error = null }, Modifier.fillMaxWidth(), minLines = 4, label = { Text("Quel est le problème ?") }, placeholder = { Text("Ex. Le moteur tremble au ralenti et démarre difficilement") }) }
            item { OutlinedTextField(whenHappens, { whenHappens = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Quand cela arrive ?") }, placeholder = { Text("À froid, à chaud, en accélération, au ralenti…") }) }
            item { OutlinedTextField(engineState, { engineState = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Que fait le moteur ?") }, placeholder = { Text("Perte de puissance, bruit, cale, fumée…") }) }
            item {
                Button(onClick = {
                    scope.launch {
                        loading = true; error = null; result = null
                        runCatching {
                            DiagnosticService().runDiagnostic(
                                vehicleModelId = modelId, userVehicleId = null, complaint = complaint.trim(), language = "fr", codes = emptyList(),
                                symptoms = buildJsonObject { put("source", "symptom_only"); put("when_happens", whenHappens); put("engine_state", engineState) },
                                measurements = buildJsonObject {},
                                vehicle = buildJsonObject { put("model_id", modelId ?: ""); put("model_name", modelName); put("complaint", complaint.trim()) }
                            )
                        }.onSuccess { response -> result = response["diagnosis"] as? JsonObject }
                         .onFailure { error = it.message ?: "Erreur du moteur AI" }
                        loading = false
                    }
                }, enabled = complaint.isNotBlank() && !loading, Modifier.fillMaxWidth()) {
                    Text(if (loading) "Analyse en cours…" else "Analyser avec CarDiag AI")
                }
            }
            error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
            result?.let { diagnosis -> item { ProfessionalReport(diagnosis) } }
        }
    }
}

@Composable
private fun ProfessionalReport(d: JsonObject) {
    ReportCard("Résumé", d["summary"]?.jsonPrimitive?.content ?: "Analyse terminée.")
    ReportCard("Niveau de risque", "${d["severity"]?.jsonPrimitive?.content ?: "unknown"} • confiance ${d["confidence"]?.jsonPrimitive?.content ?: "-"}%")
    ReportList("Causes probables", d, "likely_causes")
    ReportList("Tests à effectuer", d, "recommended_tests")
    ReportList("Comment réparer", d, "repair_guidance")
    ReportList("Sécurité", d, "safety_notes")
    ReportCard("Prochain meilleur test", d["next_best_test"]?.jsonPrimitive?.content ?: "Confirmer avec des mesures réelles.")
    ReportCard("Incertitude", d["uncertainty"]?.jsonPrimitive?.content ?: "Le diagnostic doit être confirmé.")
}

@Composable
private fun ReportCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodyLarge) } }
}

@Composable
private fun ReportList(title: String, d: JsonObject, key: String) {
    val values = d[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    if (values.isEmpty()) return
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); values.forEachIndexed { i, value -> Text("${i + 1}. $value", style = MaterialTheme.typography.bodyLarge) } } }
}
