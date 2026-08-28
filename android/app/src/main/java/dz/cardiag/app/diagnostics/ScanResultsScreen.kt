package dz.cardiag.app.diagnostics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun ScanResultsScreen(
    result: ScanResult,
    onDtc: (ScanDtc) -> Unit,
    onGuidedDiagnosis: (ScanDtc) -> Unit,
    onReport: (ScanResult) -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Scan Results", fontWeight = FontWeight.ExtraBold) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (result.hasFaults) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(18.dp)) {
                        Icon(if (result.hasFaults) Icons.Default.Warning else Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(if (result.hasFaults) "${result.dtcs.size} faults detected" else "No faults detected", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                            Text(result.vehicleName)
                            result.vin?.takeIf { it.isNotBlank() }?.let { Text("VIN: $it", style = MaterialTheme.typography.bodySmall) }
                            Text(DateFormat.getDateTimeInstance().format(Date(result.timestampEpochMs)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (result.ecu != null) item { AssistChip(onClick = {}, label = { Text("ECU: ${result.ecu}") }) }
            items(result.dtcs, key = { it.code }) { dtc ->
                Card(Modifier.fillMaxWidth().clickable { onDtc(dtc) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(dtc.code, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text(dtc.description.ifBlank { "Description unavailable" })
                        Text("Severity: ${dtc.severity}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (dtc.symptoms.isNotEmpty()) Text("Symptoms: ${dtc.symptoms.joinToString(" • ")}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onDtc(dtc) }) { Text("View DTC") }
                            TextButton(onClick = { onGuidedDiagnosis(dtc) }) { Text("Guided Diagnosis") }
                        }
                    }
                }
            }
            item { Button(onClick = { onReport(result) }, modifier = Modifier.fillMaxWidth()) { Text("Create Diagnostic Report") } }
        }
    }
}
