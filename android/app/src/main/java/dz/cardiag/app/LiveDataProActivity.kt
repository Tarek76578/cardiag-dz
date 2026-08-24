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
import dz.cardiag.app.core.ObdService
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ObdPidCatalog(
    val id: String,
    val pid: String,
    val name: String,
    val mode: Int = 1,
    @SerialName("data_type") val dataType: String? = null,
    val unit: String? = null,
    val min_value: Double? = null,
    val max_value: Double? = null,
    val description_fr: String? = null,
    val description_ar: String? = null
)

data class LiveValue(val pid: ObdPidCatalog, val value: Double?, val raw: String?, val error: String? = null)

class LiveDataProActivity : ComponentActivity() {
    private val obd = ObdService()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LiveDataProScreen(obd) }
    }
    override fun onDestroy() { obd.disconnect(); super.onDestroy() }
}

@Composable
private fun LiveDataProScreen(obd: ObdService) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Prêt — connectez un ELM327") }
    var pids by remember { mutableStateOf<List<ObdPidCatalog>>(emptyList()) }
    var values by remember { mutableStateOf<Map<String, LiveValue>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }

    fun refreshDevices() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            (context as? ComponentActivity)?.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 4201)
            return
        }
        devices = obd.bondedDevices()
    }

    fun loadPidCatalog() {
        scope.launch {
            runCatching {
                SupabaseClient.client.from("obd_pids").select(Columns.list("id","pid","name","mode","data_type","unit","min_value","max_value","description_fr","description_ar"))
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
            busy = true; status = "Connexion à ${device.name ?: device.address}…"
            runCatching { obd.connect(device) }.onSuccess { connected = true; status = it; loadPidCatalog() }
                .onFailure { status = it.message ?: "Échec de connexion" }
            busy = false
        }
    }

    fun readAll() {
        scope.launch {
            busy = true
            val next = values.toMutableMap()
            var ok = 0
            pids.take(16).forEach { pid ->
                runCatching { parsePid(pid, obd.readMode01Pid(pid.pid)) }
                    .onSuccess { next[pid.id] = LiveValue(pid, it.first, it.second); ok++ }
                    .onFailure { next[pid.id] = LiveValue(pid, null, null, it.message) }
            }
            values = next
            status = "$ok/${pids.take(16).size} PID lus — ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}"
            busy = false
        }
    }

    LaunchedEffect(Unit) { refreshDevices() }

    Scaffold(topBar = { TopAppBar(title = { Text("Live Data Pro") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("OBD-II Telemetry", style = MaterialTheme.typography.headlineSmall)
                        Text("PID catalogue + valeurs ECU + plage normale", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(status, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (!connected) {
                item { Button(onClick = ::refreshDevices, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Actualiser Bluetooth") } }
                items(devices) { device ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) { Text(device.name ?: "ELM327", style = MaterialTheme.typography.titleMedium); Text(device.address, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Button(onClick = { connect(device) }, enabled = !busy) { Text("Connecter") }
                        }
                    }
                }
                if (devices.isEmpty()) item { Text("لا يوجد ELM327 مقترن. اربطه من إعدادات Bluetooth أولًا.") }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ::readAll, enabled = !busy && pids.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Lire Live Data") }
                        OutlinedButton(onClick = { obd.disconnect(); connected = false; status = "Déconnecté" }, modifier = Modifier.weight(1f)) { Text("Déconnecter") }
                    }
                }
                items(pids.take(16)) { pid ->
                    val v = values[pid.id]
                    PidCard(v ?: LiveValue(pid, null, null))
                }
            }
        }
    }
}

private fun parsePid(pid: ObdPidCatalog, raw: String): Pair<Double?, String?> {
    val bytes = raw.replace("\\s".toRegex(), "").uppercase()
    val marker = "41${pid.pid.padStart(2, '0')}"
    val idx = bytes.indexOf(marker)
    require(idx >= 0) { "PID ${pid.pid} not returned" }
    val payload = bytes.substring(idx + marker.length).take(8)
    require(payload.length >= 2) { "Empty PID payload" }
    val a = payload.substring(0, 2).toInt(16)
    val b = if (payload.length >= 4) payload.substring(2, 4).toInt(16) else 0
    val c = if (payload.length >= 6) payload.substring(4, 6).toInt(16) else 0
    val d = if (payload.length >= 8) payload.substring(6, 8).toInt(16) else 0
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
        else -> (a * 256.0 + b * 1.0 + c / 256.0 + d / 65536.0)
    }
    return value to raw
}

@Composable
private fun PidCard(value: LiveValue) {
    val p = value.pid
    val number = value.value
    val outOfRange = number != null && ((p.min_value != null && number < p.min_value!!) || (p.max_value != null && number > p.max_value!!))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text("PID ${p.pid} • Mode ${p.mode}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (number == null) "—" else String.format("%.1f %s", number, p.unit ?: ""), style = MaterialTheme.typography.headlineSmall)
            }
            if (p.min_value != null || p.max_value != null) Text("Normal: ${p.min_value ?: "—"} → ${p.max_value ?: "—"} ${p.unit ?: ""}", color = if (outOfRange) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (value.error != null) Text(value.error, color = MaterialTheme.colorScheme.error)
        }
    }
}
