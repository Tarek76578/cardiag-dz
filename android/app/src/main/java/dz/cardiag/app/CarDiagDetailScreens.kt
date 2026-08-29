package dz.cardiag.app

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dz.cardiag.app.core.DiagnosticService
import dz.cardiag.app.core.ObdService
import dz.cardiag.app.core.ReadinessStatus
import dz.cardiag.app.ui.theme.CarDiagShapes
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ---------------------------------------------------------------------------
// OBD Onboarding
// ---------------------------------------------------------------------------

@Composable
fun ObdOnboardingScreen(
    padding: PaddingValues,
    arabic: Boolean,
    vehicleId: String?,
    vehicleName: String?,
    onBack: () -> Unit,
    onOpenDtc: (String) -> Unit,
    onOpenLiveData: () -> Unit,
    onOpenFreezeFrame: () -> Unit,
    onOpenReadiness: () -> Unit,
    onOpenVin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val obd = remember { ObdService() }
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var connected by remember { mutableStateOf(obd.isConnected()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var adapterInfo by remember { mutableStateOf<String?>(null) }
    var protocol by remember { mutableStateOf<String?>(null) }
    var vin by remember { mutableStateOf<String?>(null) }
    var readiness by remember { mutableStateOf<ReadinessStatus?>(null) }
    var dtcs by remember { mutableStateOf<List<String>>(emptyList()) }
    var pending by remember { mutableStateOf<List<String>>(emptyList()) }
    var permanent by remember { mutableStateOf<List<String>>(emptyList()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableIntStateOf(0) }

    fun ensurePermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            (context as? androidx.activity.ComponentActivity)?.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 4101)
            return false
        }
        return true
    }

    fun refresh() {
        if (!ensurePermission()) return
        devices = obd.bondedDevices()
    }
    fun connect(device: BluetoothDevice) {
        scope.launch {
            busy = true
            status = context.getString(R.string.state_connecting)
            runCatching { obd.connect(device) }
                .onSuccess { msg -> connected = true; status = msg; adapterInfo = obd.adapterInfo(); protocol = obd.adapterProtocol() }
                .onFailure { connected = false; status = it.message }
            busy = false
        }
    }
    fun fullScan() {
        scope.launch {
            busy = true
            status = context.getString(R.string.obd_scan_running)
            scanProgress = 0
            runCatching {
                val codes = obd.readTroubleCodes()
                scanProgress = 33
                val pend = obd.readPendingTroubleCodes()
                scanProgress = 66
                val perm = obd.readPermanentTroubleCodes()
                scanProgress = 100
                val rd = runCatching { obd.readReadiness() }.getOrNull()
                val v = runCatching { obd.readVehicleInfoVin() }.getOrNull()
                Triple(codes, pend, perm) to (rd to v)
            }
                .onSuccess { (d, misc) ->
                    dtcs = d.first; pending = d.second; permanent = d.third
                    readiness = misc.first; vin = misc.second
                    status = null
                }
                .onFailure { status = it.message }
            busy = false
        }
    }
    fun clearDtcs() {
        scope.launch {
            busy = true
            runCatching { obd.clearTroubleCodes() }
                .onSuccess { status = context.getString(R.string.obd_clear_done); dtcs = emptyList(); pending = emptyList(); permanent = emptyList() }
                .onFailure { status = it.message }
            busy = false
        }
    }

    DisposableEffectLifecycle(onLeave = { obd.disconnect() })

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { CarDiagTopBar(stringResource(R.string.obd_title), vehicleName, onBack = onBack) }
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ObdStepRow(index = 1, text = stringResource(R.string.obd_step_ignition))
                ObdStepRow(index = 2, text = stringResource(R.string.obd_step_plug))
                ObdStepRow(index = 3, text = stringResource(R.string.obd_step_bluetooth))
                ObdStepRow(index = 4, text = stringResource(R.string.obd_step_select))
            }
        }
        if (!connected) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = CarDiagShapes.Card) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.obd_step_select), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (devices.isEmpty()) {
                            Text(stringResource(R.string.obd_no_adapter), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            devices.forEach { d ->
                                @Suppress("DEPRECATION")
                                val name = d.name ?: d.address
                                Button(onClick = { connect(d) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(name) }
                            }
                        }
                        OutlinedButton(onClick = ::refresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }
        } else {
            item { ObdAdapterHealthCard(adapterInfo, protocol, status) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = CarDiagShapes.Card) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.obd_scan_systems), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (busy) {
                            LinearProgressIndicator(progress = { (scanProgress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.obd_scan_running), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Button(onClick = ::fullScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.obd_scan_start)) }
                        }
                        ObdSystemStatusRow(stringResource(R.string.obd_status_critical), dtcs.isNotEmpty())
                        ObdSystemStatusRow(stringResource(R.string.obd_status_attention), pending.isNotEmpty() || permanent.isNotEmpty())
                        ObdSystemStatusRow(stringResource(R.string.obd_status_no_faults), dtcs.isEmpty() && pending.isEmpty() && permanent.isEmpty())
                    }
                }
            }
            if (dtcs.isNotEmpty()) {
                item { ObdCodeListCard(stringResource(R.string.dtc_title), dtcs, onOpenDtc) }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = CarDiagShapes.Card) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.more_advanced), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        Button(onClick = onOpenLiveData, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.obd_live_data)) }
                        OutlinedButton(onClick = onOpenFreezeFrame, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.obd_freeze_frame)) }
                        OutlinedButton(onClick = onOpenReadiness, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.obd_readiness)) }
                        OutlinedButton(onClick = onOpenVin, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.obd_vin)) }
                    }
                }
            }
            item {
                Button(
                    onClick = { showClearDialog = true },
                    enabled = !busy && dtcs.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.obd_clear_codes)) }
            }
        }
        status?.let { item { CarDiagInfoCard(stringResource(R.string.state_error), it) } }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) showClearDialog = false },
            title = { Text(stringResource(R.string.obd_clear_codes)) },
            text = { Text(stringResource(R.string.obd_clear_warning)) },
            confirmButton = { TextButton(onClick = { showClearDialog = false; clearDtcs() }) { Text(stringResource(R.string.obd_clear_confirm)) } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun ObdStepRow(index: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Text("$index", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ObdAdapterHealthCard(adapter: String?, protocol: String?, status: String?) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = CarDiagShapes.Card) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.obd_adapter_health), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ObdHealthRow(stringResource(R.string.obd_health_bluetooth), stringResource(R.string.state_connected), Icons.Default.CheckCircle)
            ObdHealthRow(stringResource(R.string.obd_health_response), adapter ?: stringResource(R.string.state_unavailable), if (adapter.isNullOrBlank()) Icons.Default.Error else Icons.Default.CheckCircle)
            ObdHealthRow(stringResource(R.string.obd_health_elm), if (adapter != null) stringResource(R.string.obd_status_ok) else stringResource(R.string.state_unavailable), if (adapter != null) Icons.Default.CheckCircle else Icons.Default.HelpOutline)
            ObdHealthRow(stringResource(R.string.obd_health_protocol), protocol ?: stringResource(R.string.state_unavailable), if (protocol != null) Icons.Default.CheckCircle else Icons.Default.HelpOutline)
            ObdHealthRow(stringResource(R.string.obd_health_ecu), if (status != null) stringResource(R.string.obd_status_ok) else stringResource(R.string.state_unavailable), if (status != null) Icons.Default.CheckCircle else Icons.Default.HelpOutline)
        }
    }
}

@Composable
private fun ObdHealthRow(label: String, value: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ObdSystemStatusRow(label: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (active) Icons.Default.Warning else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ObdCodeListCard(title: String, codes: List<String>, onOpenDtc: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = CarDiagShapes.Card) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            codes.take(20).forEach { code ->
                Card(onClick = { onOpenDtc(code) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(code, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// DisposableEffectLifecycle is a tiny helper used by the OBD screen so that
// disconnect runs when the user navigates away. It is kept here to avoid
// pulling in more imports.
@Composable
private fun DisposableEffectLifecycle(onLeave: () -> Unit) {
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { onLeave() }
    }
}

// ---------------------------------------------------------------------------
// Live data
// ---------------------------------------------------------------------------

@Composable
fun LiveDataScreen(padding: PaddingValues, arabic: Boolean, initialDtc: String?, onBack: () -> Unit) {
    val obd = remember { ObdService() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val items = remember {
        mutableStateListOf<LiveMeasurement>().apply {
            addAll(LiveMeasurement.defaultCatalog())
        }
    }
    val initialDtcNorm = initialDtc?.uppercase()?.takeIf { it.matches(Regex("[PBCU][0-3][0-9A-F]{3}")) }
    var findings by remember { mutableStateOf<List<CorrelationFinding>>(emptyList()) }

    fun refresh() {
        scope.launch {
            busy = true
            error = null
            val collected = mutableListOf<LiveMeasurement>()
            runCatching {
                collected.add(LiveMeasurement("RPM", "010C", "rpm", obd.readRpm(), 700.0, 8000.0))
                collected.add(LiveMeasurement("Coolant", "0105", "°C", obd.readCoolantTemperature(), 70.0, 110.0))
                collected.add(LiveMeasurement("Speed", "010D", "km/h", obd.readVehicleSpeedKmh(), 0.0, 250.0))
                collected.add(LiveMeasurement("MAF", "0110", "g/s", obd.readMaf(), 0.0, 200.0))
                collected.add(LiveMeasurement("MAP", "010B", "kPa", obd.readMap(), 20.0, 200.0))
                collected.add(LiveMeasurement("Throttle", "0111", "%", obd.readThrottlePosition(), 0.0, 100.0))
                collected.add(LiveMeasurement("Battery", "0142", "V", obd.readBatteryVoltage(), 12.0, 15.0))
                collected.add(LiveMeasurement("Engine load", "0104", "%", obd.readEngineLoad(), 0.0, 100.0))
                collected.add(LiveMeasurement("Timing", "010E", "°", obd.readTimingAdvance(), -10.0, 50.0))
            }.onFailure { error = it.message }
            items.clear()
            items.addAll(collected)
            if (initialDtcNorm != null) {
                findings = LiveDataCorrelation.findings(initialDtcNorm, collected)
            }
            busy = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { CarDiagTopBar(stringResource(R.string.live_data_title), stringResource(R.string.live_data_advanced), onBack = onBack) }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = ::refresh, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.live_data_refresh)) }
                OutlinedButton(onClick = {}, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.live_data_save)) }
            }
        }
        if (busy) item { CarDiagLoadingState(stringResource(R.string.state_loading)) }
        if (findings.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = CarDiagShapes.Card) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.ai_likely_causes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        findings.forEach { f ->
                            Text("• ${f.title} (${f.confidence}%)", fontWeight = FontWeight.SemiBold)
                            Text(f.reason, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        items(items.size, key = { it }) { idx -> LiveMeasurementCard(items[idx]) }
        error?.let { item { CarDiagInfoCard(stringResource(R.string.state_error), it) } }
        if (items.all { it.value == null } && !busy) {
            item { CarDiagEmptyState(stringResource(R.string.state_unavailable), stringResource(R.string.obd_unsupported_pid)) }
        }
    }
}

@Composable
private fun LiveMeasurementCard(m: LiveMeasurement) {
    val valueText = m.value?.let { "%.1f %s".format(it, m.unit) } ?: stringResource(R.string.live_data_unsupported)
    val outOfRange = m.value != null && (m.value < m.min || m.value > m.max)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = CarDiagShapes.Card
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(valueText, style = MaterialTheme.typography.headlineSmall, color = if (outOfRange) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
            Text("PID ${m.pid}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(
                if (outOfRange) stringResource(R.string.live_data_out_of_range) else stringResource(R.string.live_data_normal_range, "${m.min}", "${m.max}", m.unit),
                color = if (outOfRange) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Freeze frame
// ---------------------------------------------------------------------------

@Composable
fun FreezeFrameScreen(padding: PaddingValues, arabic: Boolean, onBack: () -> Unit) {
    val obd = remember { ObdService() }
    var raw by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching { obd.readFreezeFrameRaw() }.onSuccess { raw = it }.onFailure { error = it.message }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { CarDiagTopBar(stringResource(R.string.freeze_frame_title), stringResource(R.string.freeze_frame_subtitle), onBack = onBack) }
        item { CarDiagInfoCard(stringResource(R.string.freeze_frame_why), stringResource(R.string.freeze_frame_subtitle)) }
        if (raw == null && error == null) {
            item { CarDiagLoadingState(stringResource(R.string.state_loading)) }
        }
        error?.let { item { CarDiagInfoCard(stringResource(R.string.state_error), it) } }
        raw?.let {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = CarDiagShapes.Card) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.freeze_frame_raw), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (raw == null && error == null) {
            item { CarDiagEmptyState(stringResource(R.string.freeze_frame_no_data), stringResource(R.string.obd_unsupported_pid)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Readiness
// ---------------------------------------------------------------------------

@Composable
fun ReadinessScreen(padding: PaddingValues, arabic: Boolean, onBack: () -> Unit) {
    val obd = remember { ObdService() }
    var readiness by remember { mutableStateOf<ReadinessStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { obd.readReadiness() }.onSuccess { readiness = it }.onFailure { error = it.message }
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        CarDiagTopBar(stringResource(R.string.readiness_title), stringResource(R.string.readiness_subtitle), onBack = onBack)
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CarDiagInfoCard(stringResource(R.string.readiness_explainer), stringResource(R.string.readiness_subtitle))
            readiness?.let { r ->
                Card(modifier = Modifier.fillMaxWidth(), shape = CarDiagShapes.Card) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.readiness_mil), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(
                                if (r.milOn == true) stringResource(R.string.readiness_mil_on) else stringResource(R.string.readiness_mil_off),
                                color = if (r.milOn == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.readiness_monitors_ready), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(
                                when (r.monitorsReady) {
                                    true -> stringResource(R.string.readiness_monitor_ready)
                                    false -> stringResource(R.string.readiness_monitor_not_ready)
                                    null -> stringResource(R.string.state_unknown)
                                },
                                color = if (r.monitorsReady == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            error?.let { CarDiagInfoCard(stringResource(R.string.state_error), it) }
        }
    }
}


// ---------------------------------------------------------------------------
// VIN
// ---------------------------------------------------------------------------

@Composable
fun VinScreen(padding: PaddingValues, arabic: Boolean, onBack: () -> Unit, onLinkToProfile: () -> Unit) {
    val obd = remember { ObdService() }
    var vin by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { obd.readVehicleInfoVin() }.onSuccess { vin = it }.onFailure { error = it.message }
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        CarDiagTopBar(stringResource(R.string.vin_title), stringResource(R.string.vin_subtitle), onBack = onBack)
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), shape = CarDiagShapes.Card) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.vin_value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(vin ?: stringResource(R.string.vin_unavailable), fontWeight = FontWeight.Bold)
                }
            }
            Button(onClick = onLinkToProfile, enabled = !vin.isNullOrBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.vin_link)) }
            error?.let { CarDiagInfoCard(stringResource(R.string.state_error), it) }
        }
    }
}

// ---------------------------------------------------------------------------
// Scan results
// ---------------------------------------------------------------------------

@Composable
fun ScanResultsScreen(
    padding: PaddingValues,
    arabic: Boolean,
    onBack: () -> Unit,
    onOpenDtc: (String) -> Unit,
    onOpenGuided: (String) -> Unit,
    onOpenAi: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        CarDiagTopBar(stringResource(R.string.obd_scan_systems), null, onBack = onBack)
        CarDiagEmptyState(stringResource(R.string.state_unavailable), stringResource(R.string.home_no_recent_session))
    }
}
