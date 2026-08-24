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
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    var error by remember { mutableStateOf<String?>(null) }
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
            searched = true
            runCatching {
                SupabaseClient.client.from("diagnostic_codes").select(
                    Columns.list(
                        "id", "code", "system", "category", "severity",
                        "description_fr", "causes_fr", "diagnostic_steps_fr", "repair_summary_fr"
                    )
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

    Scaffold(topBar = { TopAppBar(title = { Text("Diagnostic guidé") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(modelName, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (modelId != null) "Véhicule sélectionné • contexte actif"
                    else "Diagnostic sans OBD • aucun adaptateur requis",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Diagnostic sans OBD", style = MaterialTheme.typography.titleMedium)
                        Text("Entrez un code DTC déjà connu pour obtenir sa description, ses causes, les étapes de contrôle et la réparation. Aucun ELM327 n'est nécessaire pour cette partie.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.uppercase()
                        if (searched) {
                            searched = false
                            guide = null
                            error = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Code DTC") },
                    placeholder = { Text("Ex. P0301") },
                    singleLine = true
                )
            }

            item {
                Button(
                    onClick = ::load,
                    enabled = code.isNotBlank() && !loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (loading) "Chargement…" else "Lancer le diagnostic") }
            }

            item {
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(context, ObdScannerActivity::class.java).apply {
                            putExtra("model_id", modelId)
                            putExtra("model_name", modelName)
                            putExtra("dtc_code", code.ifBlank { null })
                        })
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Passer au diagnostic OBD / Live Data") }
            }

            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }

            guide?.let { d ->
                item { DtcCard("${d.code} • ${d.system ?: "Système inconnu"}", d.descriptionFr ?: "Description non disponible") }
                item { DtcCard("Sévérité / catégorie", listOfNotNull(d.severity, d.category).joinToString(" • ").ifBlank { "Non renseigné" }) }
                item { DtcCard("Causes probables", d.causesFr ?: "Non renseignées") }
                item { DtcCard("Étapes de diagnostic", d.diagnosticStepsFr ?: "Non renseignées") }
                item { DtcCard("Réparation", d.repairSummaryFr ?: "Non renseignée") }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Parcours CarDiag", style = MaterialTheme.typography.titleMedium)
                            Text("DTC → causes → contrôles → réparation. Pour les valeurs ECU réelles، استخدم OBD / Live Data.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DtcCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body)
        }
    }
}
