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
import dz.cardiag.app.core.DiagnosticMeasurementInsert
import dz.cardiag.app.core.DiagnosticService
import dz.cardiag.app.core.ObdService
import dz.cardiag.app.core.ReadinessStatus
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ObdScannerActivity : ComponentActivity() {
    private val obd = ObdService()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ObdScannerScreen(obd,intent.getStringExtra("model_id"),intent.getStringExtra("model_name"),intent.getStringExtra("dtc_code")) }
    }
    override fun onDestroy() { obd.disconnect(); super.onDestroy() }
}

@Composable
private fun ObdScannerScreen(obd: ObdService,modelId: String?,modelName: String?,targetDtc: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Prêt — sélectionnez un adaptateur ELM327 appairé") }
    var dtcs by remember { mutableStateOf<List<String>>(emptyList()) }
    var pending by remember { mutableStateOf<List<String>>(emptyList()) }
    var permanent by remember { mutableStateOf<List<String>>(emptyList()) }
    var rpm by remember { mutableStateOf<Double?>(null) }
    var coolant by remember { mutableStateOf<Double?>(null) }
    var speed by remember { mutableStateOf<Double?>(null) }
    var maf by remember { mutableStateOf<Double?>(null) }
    var map by remember { mutableStateOf<Double?>(null) }
    var throttle by remember { mutableStateOf<Double?>(null) }
    var voltage by remember { mutableStateOf<Double?>(null) }
    var vin by remember { mutableStateOf<String?>(null) }
    var freeze by remember { mutableStateOf<String?>(null) }
    var readiness by remember { mutableStateOf<ReadinessStatus?>(null) }
    var adapterInfo by remember { mutableStateOf<String?>(null) }
    var supported by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (ContextCompat.checkSelfPermission(context,Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            (context as? ComponentActivity)?.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT),4101)
            return
        }
        devices = obd.bondedDevices()
    }
    fun connect(device: BluetoothDevice) {
        scope.launch {
            busy=true; status="Connexion…"
            runCatching { obd.connect(device) }.onSuccess { connected=true; status=it }.onFailure { connected=false; status=it.message ?: "Connexion impossible" }
            busy=false
        }
    }
    fun fullScan() {
        scope.launch {
            busy=true; status="Full Vehicle Scan…"
            runCatching { Triple(obd.readTroubleCodes(),obd.readPendingTroubleCodes(),obd.readPermanentTroubleCodes()) to Pair(obd.readReadiness(),obd.readSupportedPids()) }
                .onSuccess { result -> dtcs=result.first.first; pending=result.first.second; permanent=result.first.third; readiness=result.second.first; supported=result.second.second; status="Scan terminé • ${dtcs.size} DTC confirmé(s)" }
                .onFailure { status=it.message ?: "Scan impossible" }
            busy=false
        }
    }
    fun readLive() {
        scope.launch {
            busy=true; status="Lecture Live Data…"
            runCatching { listOf(obd.readRpm(),obd.readCoolantTemperature(),obd.readVehicleSpeedKmh(),obd.readMaf(),obd.readMap(),obd.readThrottlePosition(),obd.readBatteryVoltage()) }
                .onSuccess { v -> rpm=v[0]; coolant=v[1]; speed=v[2]; maf=v[3]; map=v[4]; throttle=v[5]; voltage=v[6]; status="Live Data actualisée" }
                .onFailure { status=it.message ?: "Lecture impossible" }
            busy=false
        }
    }
    fun runAi() {
        if (dtcs.isEmpty()) { status="Lisez d'abord les DTC"; return }
        scope.launch {
            busy=true; aiResult=null; status="Analyse CarDiag AI…"
            runCatching {
                val service=DiagnosticService()
                val session=service.createSession(modelId,null,"Full OBD scan — ${modelName ?: "Véhicule"}","fr")
                val rows=listOfNotNull(rpm?.let{DiagnosticMeasurementInsert(session.id,name="RPM",valueNumeric=it,unit="rpm")},coolant?.let{DiagnosticMeasurementInsert(session.id,name="Coolant",valueNumeric=it,unit="°C")},speed?.let{DiagnosticMeasurementInsert(session.id,name="Speed",valueNumeric=it,unit="km/h")},maf?.let{DiagnosticMeasurementInsert(session.id,name="MAF",valueNumeric=it,unit="g/s")},map?.let{DiagnosticMeasurementInsert(session.id,name="MAP",valueNumeric=it,unit="kPa")},throttle?.let{DiagnosticMeasurementInsert(session.id,name="Throttle",valueNumeric=it,unit="%")},voltage?.let{DiagnosticMeasurementInsert(session.id,name="Battery",valueNumeric=it,unit="V")})
                service.saveMeasurements(session.id,rows)
                service.diagnose(session.id,dtcs,buildJsonObject{put("source","obd")},buildJsonObject{rpm?.let{put("rpm",it)};coolant?.let{put("coolant_c",it)};speed?.let{put("speed_kmh",it)};maf?.let{put("maf_gps",it)};map?.let{put("map_kpa",it)};throttle?.let{put("throttle_percent",it)};voltage?.let{put("battery_voltage",it)}},buildJsonObject{put("model_id",modelId ?: "");put("model_name",modelName ?: "Véhicule");vin?.let{put("vin_response",it)}},"fr")
            }.onSuccess { aiResult=it.toString(); status="Diagnostic AI terminé" }.onFailure { status=it.message ?: "AI indisponible" }
            busy=false
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Scaffold(topBar={TopAppBar(title={Text("Scanner OBD-II Pro")})}) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item { Text("Connexion & ECU",style=MaterialTheme.typography.titleLarge); Text(status,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Button(onClick=::refresh,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Actualiser adaptateurs")} }
            if (!connected) {
                items(devices) { device -> Card(Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(device.name ?: "ELM327",style=MaterialTheme.typography.titleMedium);Text(device.address)};Button(onClick={connect(device)},enabled=!busy){Text("Connecter")}}} }
                if (devices.isEmpty()) item { Text("Aucun ELM327 appairé. Le diagnostic OBD réel nécessite un adaptateur compatible.") }
            } else {
                item { Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("OBD connecté",style=MaterialTheme.typography.titleLarge);Text("${obd.protocol()} • Bluetooth Classic");Button(onClick={scope.launch{busy=true;runCatching{obd.adapterInfo() to obd.adapterProtocol()}.onSuccess{(a,p)->adapterInfo="$a\nProtocol: $p";status="Adaptateur contrôlé"}.onFailure{status=it.message ?: "Adapter check failed"};busy=false}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Tester l'adaptateur")};OutlinedButton(onClick={obd.disconnect();connected=false;status="Déconnecté"},modifier=Modifier.fillMaxWidth()){Text("Déconnecter")}}} }
                item { Button(onClick=::fullScan,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("FULL VEHICLE SCAN")} }
                item { Button(onClick=::readLive,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Actualiser Live Data")} }
                item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={scope.launch{busy=true;runCatching{obd.readTroubleCodes()}.onSuccess{dtcs=it;status="DTC confirmés lus"}.onFailure{status=it.message ?: "Erreur DTC"};busy=false}},enabled=!busy,modifier=Modifier.weight(1f)){Text("DTC")};OutlinedButton(onClick={scope.launch{busy=true;runCatching{obd.readVehicleInfoVin()}.onSuccess{vin=it;status="VIN lu"}.onFailure{status=it.message ?: "VIN indisponible"};busy=false}},enabled=!busy,modifier=Modifier.weight(1f)){Text("VIN")}} }
                item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={scope.launch{busy=true;runCatching{obd.readPendingTroubleCodes()}.onSuccess{pending=it;status="Pending lus"}.onFailure{status=it.message ?: "Erreur"};busy=false}},enabled=!busy,modifier=Modifier.weight(1f)){Text("Pending")};OutlinedButton(onClick={scope.launch{busy=true;runCatching{obd.readPermanentTroubleCodes()}.onSuccess{permanent=it;status="Permanent lus"}.onFailure{status=it.message ?: "Erreur"};busy=false}},enabled=!busy,modifier=Modifier.weight(1f)){Text("Permanent")}} }
                item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={scope.launch{busy=true;runCatching{obd.readFreezeFrameRaw()}.onSuccess{freeze=it;status="Freeze Frame reçu"}.onFailure{status=it.message ?: "Freeze Frame indisponible"};busy=false}},enabled=!busy,modifier=Modifier.weight(1f)){Text("Freeze Frame")};OutlinedButton(onClick={scope.launch{busy=true;runCatching{obd.readReadiness()}.onSuccess{readiness=it;status="Readiness actualisée"}.onFailure{status=it.message ?: "Readiness indisponible"};busy=false}},enabled=!busy,modifier=Modifier.weight(1f)){Text("Readiness")}} }
                item { Button(onClick={scope.launch{busy=true;runCatching{obd.clearTroubleCodes()}.onSuccess{dtcs=emptyList();status="Clear DTC envoyé — rescanner recommandé"}.onFailure{status=it.message ?: "Clear DTC impossible"};busy=false}},enabled=!busy,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("EFFACER DTC")} }
                item { Button(onClick={context.startActivity(Intent(context,LiveDataProActivity::class.java).apply{putExtra("model_id",modelId);putExtra("model_name",modelName ?: "Véhicule");putExtra("dtc_code",targetDtc ?: dtcs.firstOrNull())})},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Ouvrir Live Data Pro →")} }
                item { Text("RPM ${rpm?.let{"%.0f".format(it)} ?: "—"} • Coolant ${coolant?.let{"%.0f°C".format(it)} ?: "—"} • Speed ${speed?.let{"%.0f km/h".format(it)} ?: "—"}") }
                item { Text("MAF ${maf?.let{"%.2f g/s".format(it)} ?: "—"} • MAP ${map?.let{"%.0f kPa".format(it)} ?: "—"} • Throttle ${throttle?.let{"%.1f%%".format(it)} ?: "—"} • Battery ${voltage?.let{"%.2f V".format(it)} ?: "—"}") }
                item { Text("Confirmed: ${dtcs.joinToString().ifBlank{"aucun"}}") }; item { Text("Pending: ${pending.joinToString().ifBlank{"aucun"}}") }; item { Text("Permanent: ${permanent.joinToString().ifBlank{"aucun"}}") }
                readiness?.let{r->item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("Readiness / MIL",style=MaterialTheme.typography.titleMedium);Text("MIL: ${r.milOn?.toString() ?: "unknown"} • Ready: ${r.monitorsReady?.toString() ?: "unknown"}");Text(r.raw)}}}}
                freeze?.let{f->item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("Freeze Frame",style=MaterialTheme.typography.titleMedium);Text(f)}}}}
                vin?.let{v->item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("VIN",style=MaterialTheme.typography.titleMedium);Text(v)}}}}
                adapterInfo?.let{a->item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("Adapter health",style=MaterialTheme.typography.titleMedium);Text(a)}}}}
                item { Text("Supported PIDs: ${supported.size}") }
                if (dtcs.isNotEmpty()) item { Button(onClick=::runAi,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Analyser avec CarDiag AI")} }
                aiResult?.let{r->item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("CarDiag AI",style=MaterialTheme.typography.titleLarge);Text(r)}}}}
            }
        }
    }
}
