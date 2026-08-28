@file:Suppress("InlinedApi")

package dz.cardiag.app

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dz.cardiag.app.core.CorrelationFinding
import dz.cardiag.app.core.CorrelationObservation
import dz.cardiag.app.core.DiagnosticCorrelation
import dz.cardiag.app.core.DiagnosticMeasurementInsert
import dz.cardiag.app.core.DiagnosticService
import dz.cardiag.app.core.ObdService
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ObdPidCatalog(
    val id: String,
    val pid: String,
    val name: String,
    val mode: Int = 1,
    @SerialName("data_type") val dataType: String? = null,
    val unit: String? = null,
    val formula: String? = null,
    @SerialName("min_value") val minValue: Double? = null,
    @SerialName("max_value") val maxValue: Double? = null,
    @SerialName("description_fr") val descriptionFr: String? = null,
    @SerialName("description_ar") val descriptionAr: String? = null
)

data class LiveValue(val pid: ObdPidCatalog, val value: Double?, val raw: String?, val error: String? = null)

class LiveDataProActivity : ComponentActivity() {
    private val obd = ObdService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialDtc = intent.getStringExtra("dtc_code")
        val modelId = intent.getStringExtra("model_id")
        val modelName = intent.getStringExtra("model_name") ?: "Véhicule"
        setContent { LiveDataProScreen(obd, initialDtc, modelId, modelName) }
    }

    override fun onDestroy() {
        obd.disconnect()
        super.onDestroy()
    }
}

@Composable
private fun LiveDataProScreen(obd: ObdService, initialDtc: String?, modelId: String?, modelName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Prêt — connectez un ELM327") }
    var pids by remember { mutableStateOf<List<ObdPidCatalog>>(emptyList()) }
    var values by remember { mutableStateOf<Map<String, LiveValue>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var findings by remember { mutableStateOf<List<CorrelationFinding>>(emptyList()) }

    fun refreshDevices() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            (context as? ComponentActivity)?.requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 4201
            )
            return
        }
        devices = obd.bondedDevices()
    }

    fun loadPidCatalog() {
        scope.launch {
            runCatching {
                SupabaseClient.client.from("obd_pids")
                    .select(Columns.list("id", "pid", "name", "mode", "data_type", "unit", "formula", "min_value", "max_value", "description_fr", "description_ar"))
                    .decodeList<ObdPidCatalog>()
                    .filter { it.mode == 1 }
                    .sortedBy { it.pid }
            }.onSuccess { list ->
                pids = list
                status = "${list.size} PID disponibles depuis CarDiag"
            }.onFailure { status = "Catalogue PID indisponible: ${it.message}" }
        }
    }

    fun connect(device: BluetoothDevice) {
        scope.launch {
            busy = true
            status = "Connexion à ${device.name ?: device.address}…"
            runCatching { obd.connect(device) }.onSuccess {
                connected = true
                status = it
                loadPidCatalog()
                runCatching {
                    sessionId = DiagnosticService().createSession(
                        modelId, null, "OBD Live Data Pro — $modelName", "fr"
                    ).id
                }.onFailure {
                    status = "OBD connecté; session Supabase non créée: ${it.message}"
                }
            }.onFailure { status = it.message ?: "Échec de connexion" }
            busy = false
        }
    }

    suspend fun readAllOnce() {
        val next = values.toMutableMap()
        val requested = pids.take(20)
        var ok = 0
        requested.forEach { pid ->
            runCatching { parsePid(pid, obd.readMode01Pid(pid.pid)) }
                .onSuccess { (number, raw) ->
                    next[pid.id] = LiveValue(pid, number, raw)
                    ok++
                }
                .onFailure { next[pid.id] = LiveValue(pid, null, null, it.message) }
        }
        values = next
        status = "$ok/${requested.size} PID lus • ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}"
    }

    fun readAll() {
        scope.launch {
            busy = true
            runCatching { readAllOnce() }.onFailure { status = it.message ?: "Lecture impossible" }
            busy = false
        }
    }

    fun persistSnapshot() {
        scope.launch {
            val id = sessionId ?: runCatching {
                DiagnosticService().createSession(modelId, null, "OBD Live Data Pro — $modelName", "fr").id
            }.getOrNull()
            if (id == null) {
                status = "Impossible de créer la session Supabase"
                return@launch
            }
            sessionId = id
            busy = true
            runCatching {
                val rows = values.values.filter { it.value != null }.map { v ->
                    DiagnosticMeasurementInsert(id, v.pid.id, v.pid.name, v.value, v.raw, v.pid.unit, "obd")
                }
                DiagnosticService().saveMeasurements(id, rows)
            }.onSuccess {
                status = "${values.values.count { it.value != null }} mesures enregistrées • session ${id.take(8)}…"
            }.onFailure { status = "Échec sauvegarde mesures: ${it.message}" }
            busy = false
        }
    }

    fun runCorrelation() {
        scope.launch {
            busy = true
            val code = initialDtc?.trim()?.uppercase()
            if (code.isNullOrBlank()) {
                status = "Aucun DTC fourni pour la corrélation"
                busy = false
                return@launch
            }
            val observations = values.values.filter { it.value != null }.map {
                CorrelationObservation(it.pid.pid, it.value!!, it.pid.unit, it.pid.minValue, it.pid.maxValue)
            }
            findings = DiagnosticCorrelation.correlate(code, observations)
            val id = sessionId ?: runCatching {
                DiagnosticService().createSession(modelId, null, "DTC $code — $modelName", "fr").id
            }.getOrNull()
            if (id != null) {
                sessionId = id
                runCatching {
                    val measurements = values.values.filter { it.value != null }.map { v ->
                        DiagnosticMeasurementInsert(id, v.pid.id, v.pid.name, v.value, v.raw, v.pid.unit, "obd")
                    }
                    DiagnosticService().saveMeasurements(id, measurements)
                    DiagnosticService().diagnose(
                        id,
                        codes = listOf(code),
                        measurements = buildJsonObject {
                            values.values.filter { it.value != null }.forEach { v ->
                                put(v.pid.pid, v.value!!)
                            }
                        },
                        vehicle = buildJsonObject {
                            put("model_id", modelId ?: "")
                            put("model_name", modelName)
                        },
                        language = "fr"
                    )
                }.onSuccess { status = "Corrélation $code terminée • session ${id.take(8)}…" }
                    .onFailure { status = "Corrélation locale terminée; service diagnostic indisponible: ${it.message}" }
            } else {
                status = "Corrélation locale terminée; session Supabase indisponible"
            }
            busy = false
        }
    }

    LaunchedEffect(Unit) { refreshDevices() }
    LaunchedEffect(connected) {
        if (connected) {
            while (true) {
                if (!busy && pids.isNotEmpty()) runCatching { readAllOnce() }
                delay(2000)
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Live Data Pro") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("OBD-II Telemetry", style = MaterialTheme.typography.headlineSmall)
                        Text("PID catalogue + valeurs ECU + plage normale", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(status, style = MaterialTheme.typography.labelLarge)
                        if (!initialDtc.isNullOrBlank()) Text("DTC ciblé: ${initialDtc.uppercase()}", color = MaterialTheme.colorScheme.primary)
                        sessionId?.let { Text("Session: ${it.take(8)}…", color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
            if (!connected) {
                item { Button(onClick = ::refreshDevices, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Actualiser Bluetooth") } }
                items(devices) { device ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(device.name ?: "ELM327", style = MaterialTheme.typography.titleMedium)
                                Text(device.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(onClick = { connect(device) }, enabled = !busy) { Text("Connecter") }
                        }
                    }
                }
                if (devices.isEmpty()) item { Text("لا يوجد ELM327 مقترن. اربطه من إعدادات Bluetooth أولًا.") }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ::readAll, enabled = !busy && pids.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Actualiser") }
                        OutlinedButton(onClick = ::persistSnapshot, enabled = !busy && values.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Sauvegarder") }
                    }
                }
                if (!initialDtc.isNullOrBlank()) item {
                    Button(onClick = ::runCorrelation, enabled = !busy && values.values.any { it.value != null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Analyser ${initialDtc.uppercase()} avec Live Data")
                    }
                }
                if (findings.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Corrélation diagnostic", style = MaterialTheme.typography.titleLarge)
                        findings.forEach { FindingCard(it) }
                    }
                }
                items(pids.take(20)) { pid -> PidCard(values[pid.id] ?: LiveValue(pid, null, null)) }
            }
        }
    }
}

private fun parsePid(pid: ObdPidCatalog, raw: String): Pair<Double?, String?> {
    val bytes = raw.replace(Regex("\\s|\\r|\\n|>"), "").uppercase()
    val marker = "41${pid.pid.padStart(2, '0').uppercase()}"
    val idx = bytes.indexOf(marker)
    require(idx >= 0) { "PID ${pid.pid} not returned" }
    val payload = bytes.substring(idx + marker.length).take(8)
    require(payload.length >= 2) { "Empty PID payload" }
    val a = payload.substring(0, 2).toInt(16)
    val b = if (payload.length >= 4) payload.substring(2, 4).toInt(16) else 0
    val c = if (payload.length >= 6) payload.substring(4, 6).toInt(16) else 0
    val value = when (pid.pid.uppercase()) {
        "04" -> a * 100.0 / 255.0
        "05" -> a - 40.0
        "06", "07", "08", "09" -> a / 1.28 - 100.0
        "0A" -> a * 3.0
        "0B" -> a.toDouble()
        "0C" -> (a * 256.0 + b) / 4.0
        "0D" -> a.toDouble()
        "0F" -> a - 40.0
        "10" -> (a * 256.0 + b) / 100.0
        "11" -> a * 100.0 / 255.0
        "1F" -> (a * 256.0 + b).toDouble()
        "2F" -> a * 100.0 / 255.0
        "46" -> a - 40.0
        "5C" -> a - 40.0
        "5E" -> (a * 256.0 + b) / 20.0
        else -> (a * 256.0 + b + c / 256.0)
    }
    return value to raw
}

@Composable
private fun PidCard(value: LiveValue) {
    val p = value.pid
    val number = value.value
    val outOfRange = number != null && ((p.minValue != null && number < p.minValue) || (p.maxValue != null && number > p.maxValue))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text("PID ${p.pid} • Mode ${p.mode}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (number == null) "—" else "%.1f %s".format(java.util.Locale.US, number, p.unit ?: ""),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Text(
                if (outOfRange) "⚠ Valeur hors plage normale" else "Normal: ${p.minValue ?: "—"} → ${p.maxValue ?: "—"} ${p.unit ?: ""}",
                color = if (outOfRange) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            value.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun FindingCard(finding: CorrelationFinding) {
    val color = if (finding.severity == "medium" || finding.severity == "high") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(finding.title, style = MaterialTheme.typography.titleMedium)
                Text("${finding.confidence}%", color = color)
            }
            Text(finding.reason)
            Text("PID: ${finding.supportingPids.joinToString()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
