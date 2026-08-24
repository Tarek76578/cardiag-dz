package dz.cardiag.app

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dz.cardiag.app.core.DiagnosticService
import dz.cardiag.app.core.ObdService
import kotlinx.coroutines.launch

class ObdScannerActivity : ComponentActivity() {
    private val obd = ObdService()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ObdScannerScreen(obd, intent.getStringExtra("model_id"), intent.getStringExtra("model_name"), intent.getStringExtra("dtc_code"))
        }
    }
    override fun onDestroy() { obd.disconnect(); super.onDestroy() }
}

@Composable
private fun ObdScannerScreen(obd: ObdService, modelId: String?, modelName: String?, targetDtc: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Prêt — sélectionnez un adaptateur ELM327 appairé") }
    var dtcs by remember { mutableStateOf<List<String>>(emptyList()) }
    var rpm by remember { mutableStateOf<Double?>(null) }
    var coolant by remember { mutableStateOf<Double?>(null) }
    var speed by remember { mutableStateOf<Double?>(null) }
    var vinRaw by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val hasBluetoothPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun refreshDevices() {
        if (!hasBluetoothPermission) {
            (context as? ComponentActivity)?.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 4101)
            return
        }
        devices = obd.bondedDevices()
    }

    fun connect(device: BluetoothDevice) {
        scope.launch {
            busy = true; status = "Connexion à ${device.name ?: device.address}…"
            runCatching { obd.connect(device) }.onSuccess { connected = true; status = it }.onFailure { connected = false; status = it.message ?: "Échec de connexion" }
            busy = false
        }
    }

    fun readLive() {
        scope.launch {
            busy = true; status = "Lecture des données ECU…"
            runCatching { Triple(obd.readRpm(), obd.readCoolantTemperature(), obd.readVehicleSpeedKmh()) }
                .onSuccess { (r, c, s) -> rpm = r; coolant = c; speed = s; status = "Données ECU actualisées" }
                .onFailure { status = it.message ?: "Lecture impossible" }
            busy = false
        }
    }

    fun readCodes() {
        scope.launch {
            busy = true; status = "Lecture des DTC…"
            runCatching { obd.readTroubleCodes() }.onSuccess { dtcs = it; status = if (it.isEmpty()) "Aucun DTC confirmé" else "${it.size} DTC détecté(s)" }
                .onFailure { status = it.message ?: "Lecture DTC impossible" }
            busy = false
        }
    }

    fun readVin() {
        scope.launch {
            busy = true; status = "Lecture VIN…"
            runCatching { obd.readVehicleInfoVin() }.onSuccess { vinRaw = it; status = "Réponse VIN reçue" }
                .onFailure { status = it.message ?: "VIN non disponible" }
            busy = false
        }
    }

    LaunchedEffect(Unit) { refreshDevices() }

    Scaffold(topBar = { TopAppBar(title = { Text("Scanner OBD-II") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Connexion Bluetooth", style = MaterialTheme.typography.titleLarge); Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Button(onClick = ::refreshDevices, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Actualiser les adaptateurs appairés") } }
            if (!connected) {
                items(devices) { device ->
                    Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(device.name ?: "ELM327", style = MaterialTheme.typography.titleMedium); Text(device.address, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Button(onClick = { connect(device) }, enabled = !busy) { Text("Connecter") } } }
                }
                if (devices.isEmpty()) item { Text("Aucun appareil appairé. Appairez d'abord votre ELM327 dans les réglages Bluetooth Android.") }
            } else {
                item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("ECU connecté", style = MaterialTheme.typography.titleLarge); Text("Bluetooth Classic • ELM327 • OBD-II"); OutlinedButton(onClick = { obd.disconnect(); connected = false; status = "Déconnecté" }, modifier = Modifier.fillMaxWidth()) { Text("Déconnecter") } } } }
                item { Button(onClick = { context.startActivity(Intent(context, LiveDataProActivity::class.java).apply { putExtra("model_id", modelId); putExtra("model_name", modelName ?: "Véhicule"); putExtra("dtc_code", targetDtc ?: dtcs.firstOrNull()) }) }, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Ouvrir Live Data Pro →") } }
                item { Text("Données live rapides", style = MaterialTheme.typography.titleLarge) }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { LiveCard("RPM", rpm?.let { "%.0f".format(it) } ?: "—", Modifier.weight(1f)); LiveCard("°C", coolant?.let { "%.0f".format(it) } ?: "—", Modifier.weight(1f)); LiveCard("km/h", speed?.let { "%.0f".format(it) } ?: "—", Modifier.weight(1f)) } }
                item { Button(onClick = ::readLive, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Actualiser Live Data") } }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = ::readCodes, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Lire DTC") }; OutlinedButton(onClick = ::readVin, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Lire VIN") } } }
                if (dtcs.isNotEmpty()) item { Text("DTC: ${dtcs.joinToString(" • ")}", style = MaterialTheme.typography.titleMedium) }
                if (dtcs.isEmpty()) item { Text("Aucun DTC lu dans cette session.") }
                vinRaw?.let { item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("VIN / ECU response", style = MaterialTheme.typography.titleMedium); Text(it) } } } }
                item { Button(onClick = { scope.launch { busy = true; status = "Création de la session…"; runCatching { DiagnosticService().createSession(modelId, null, "OBD live scan — ${modelName ?: "Véhicule"}", "fr") }.onSuccess { status = "Session diagnostic ${it.id} créée dans Supabase" }.onFailure { status = it.message ?: "Impossible de créer la session" }; busy = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer la session") } }
            }
        }
    }
}

@Composable private fun LiveCard(label: String, value: String, modifier: Modifier) { Card(modifier) { Column(Modifier.padding(14.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Text(value, style = MaterialTheme.typography.titleLarge); Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
