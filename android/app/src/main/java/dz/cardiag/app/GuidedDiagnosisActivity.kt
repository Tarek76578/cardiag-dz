package dz.cardiag.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DtcGuide(
    val code: String,
    val system: String? = null,
    val category: String? = null,
    val severity: String? = null,
    val description: String? = null,
    val causes: String? = null,
    @SerialName("diagnostic_steps") val diagnosticSteps: String? = null,
    @SerialName("repair_summary") val repairSummary: String? = null
)

class GuidedDiagnosisActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GuidedDiagnosisScreen() }
    }
}

@Composable
private fun GuidedDiagnosisScreen() {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("P0301") }
    var guide by remember { mutableStateOf<DtcGuide?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                SupabaseClient.client.from("diagnostic_codes")
                    .select(Columns.list("code", "system", "category", "severity", "description", "causes", "diagnostic_steps", "repair_summary")) {
                        filter { eq("code", code.trim().uppercase()) }
                    }
                    .decodeList<DtcGuide>()
                    .firstOrNull()
            }.onSuccess { guide = it; if (it == null) error = "DTC not found" }
                .onFailure { error = it.message ?: "Unable to load DTC" }
            loading = false
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Guided Diagnosis") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("DTC → Symptoms → Tests → Diagnosis", style = MaterialTheme.typography.titleLarge)
                Text("Start with a fault code, then follow the diagnostic path.")
            }
            item {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("DTC code") },
                    singleLine = true
                )
            }
            item {
                Button(onClick = ::load, enabled = code.isNotBlank() && !loading) {
                    Text(if (loading) "Loading…" else "Start diagnosis")
                }
            }
            if (loading) item { CircularProgressIndicator() }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            guide?.let { d ->
                item { DtcCard("${d.code} • ${d.system ?: "Unknown system"}", d.description ?: "No description") }
                item { DtcCard("Severity / Category", listOfNotNull(d.severity, d.category).joinToString(" • ").ifBlank { "Not specified" }) }
                item { DtcCard("Likely causes", d.causes ?: "Not specified") }
                item { DtcCard("Diagnostic steps", d.diagnosticSteps ?: "Not specified") }
                item { DtcCard("Repair summary", d.repairSummary ?: "Not specified") }
                item { Text("Next: connect the selected vehicle and compare live OBD PIDs against this diagnostic path.") }
            }
        }
    }
}

@Composable
private fun DtcCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body)
        }
    }
}
