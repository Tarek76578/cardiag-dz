package dz.cardiag.app.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun DiagnosticReportScreen(report: DiagnosticReport, onBack: () -> Unit, onShare: (() -> Unit)? = null) {
    Scaffold(topBar = { TopAppBar(title = { Text("Diagnostic Report", fontWeight = FontWeight.ExtraBold) }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ReportSection("Vehicle") { Text(report.scan.vehicleName, fontWeight = FontWeight.Bold); report.scan.vin?.let { Text("VIN: $it") }; report.scan.ecu?.let { Text("ECU: $it") } } }
            item { ReportSection("Scan") { Text("${DateFormat.getDateTimeInstance().format(Date(report.createdAtEpochMs))}"); Text("Source: ${report.scan.source}"); Text("DTCs: ${report.scan.dtcs.size}") } }
            item { Text("Diagnostic findings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
            items(report.scan.dtcs, key = { it.code }) { dtc ->
                ReportSection(dtc.code) { Text(dtc.description.ifBlank { "Description unavailable" }); Text("Severity: ${dtc.severity}"); if (dtc.symptoms.isNotEmpty()) Text("Symptoms: ${dtc.symptoms.joinToString(" • ")}"); if (dtc.causes.isNotEmpty()) Text("Causes: ${dtc.causes.joinToString(" • ")}"); if (dtc.recommendations.isNotEmpty()) Text("Recommendations: ${dtc.recommendations.joinToString(" • ")}") }
            }
            if (report.scan.liveData.isNotEmpty()) {
                item { Text("Live Data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
                items(report.scan.liveData, key = { it.name }) { pid -> ListItem(headlineContent = { Text(pid.name) }, trailingContent = { Text("${pid.value} ${pid.unit}".trim()) }) }
            }
            report.diagnosisSummary?.let { summary -> item { ReportSection("Diagnosis") { Text(summary) } } }
            if (report.recommendations.isNotEmpty()) item { ReportSection("Recommendations") { report.recommendations.forEach { Text("• $it") } } }
            onShare?.let { share -> item { Button(onClick = share, modifier = Modifier.fillMaxWidth()) { Text("Share Report") } } }
        }
    }
}

@Composable private fun ReportSection(title: String, content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); content() }) } }
