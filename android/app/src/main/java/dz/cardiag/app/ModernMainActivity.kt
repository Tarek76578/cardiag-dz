package dz.cardiag.app

import android.Manifest
import android.app.LocaleManager
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dz.cardiag.app.core.AuthService
import dz.cardiag.app.core.DiagnosticService
import dz.cardiag.app.core.ObdService
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class VehicleModel(
    val id: String,
    @SerialName("make_id") val makeId: String,
    val name: String,
    @SerialName("year_from") val yearFrom: Int? = null,
    @SerialName("year_to") val yearTo: Int? = null,
    val generation: String? = null,
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class VehicleMake(val id: String, val name: String)

class ModernMainActivity : ComponentActivity() {
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ModernCarDiag(::requestBluetoothPermissions) }
    }

    private fun requestBluetoothPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        }
    }

    fun setLanguage(language: String) {
        getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(if (language == "ar") "ar" else "fr")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernCarDiag(requestBluetoothPermissions: () -> Unit) {
    val auth = remember { AuthService() }
    var ready by remember { mutableStateOf(auth.currentUser != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(0) }
    val activity = LocalContext.current as? ModernMainActivity

    LaunchedEffect(retry) {
        if (!ready) {
            try {
                auth.signInAnonymously()
                ready = auth.currentUser != null
                if (!ready) error = "Authentication session was not created"
            } catch (e: Exception) { error = e.message ?: "Authentication failed" }
        }
    }

    if (!ready) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF071016)) {
            Column(Modifier.fillMaxSize().padding(28.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("CarDiag", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = Color.White)
                Text("DZ • Intelligent vehicle diagnostics", color = Color(0xFF9EB0B9))
                Spacer(Modifier.height(24.dp))
                if (error == null) CircularProgressIndicator(color = Color(0xFF52E5D3))
                else {
                    Text(error!!, color = Color(0xFFFF8B8B), modifier = Modifier.padding(bottom = 12.dp))
                    Button(onClick = { error = null; retry++ }) { Text("Retry / Réessayer") }
                }
            }
        }
        return
    }

    val ar = LocalConfiguration.current.locales[0].language == "ar"
    val titles = if (ar) listOf("الرئيسية", "التشخيص", "السيارات", "الإعدادات") else listOf("Accueil", "Diagnostic", "Véhicules", "Réglages")
    Scaffold(
        containerColor = Color(0xFF071016),
        topBar = { TopAppBar(title = { Text("CarDiag", color = Color.White, fontWeight = FontWeight.Black) }, actions = { Text("DZ", color = Color(0xFF52E5D3), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 18.dp)) }) },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0C171E)) {
                titles.forEachIndexed { index, title ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Text(listOf("⌂", "⌁", "▣", "⚙")[index]) }, label = { Text(title) })
                }
            }
        }
    ) { padding ->
        when (tab) {
            0 -> HomeModern(padding, ar, { tab = 1 }, { tab = 2 })
            1 -> DiagnoseModern(padding, ar, requestBluetoothPermissions)
            2 -> VehiclesModern(padding, ar, { tab = 1 })
            else -> SettingsModern(padding, ar) { activity?.setLanguage(it) }
        }
    }
}

@Composable
private fun VehicleImage(vehicle: VehicleModel, modifier: Modifier = Modifier) {
    AsyncImage(
        model = vehicle.imageUrl,
        contentDescription = vehicle.name,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        placeholder = painterResource(dz.cardiag.app.R.drawable.cardiag_car_fallback),
        error = painterResource(dz.cardiag.app.R.drawable.cardiag_car_fallback)
    )
}

@Composable
private fun HomeModern(padding: PaddingValues, ar: Boolean, diagnose: () -> Unit, vehicles: () -> Unit) {
    val models = remember { mutableStateListOf<VehicleModel>() }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        try {
            models.clear()
            models.addAll(SupabaseClient.client.from("vehicle_models").select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")).decodeList())
        } catch (_: Exception) { }
        loading = false
    }
    val featured = models.firstOrNull()
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(30.dp))) {
                if (featured != null) VehicleImage(featured, Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(Color(0xFF102029)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE061015)))))
                Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (ar) "تشخيص ذكي لسيارتك" else "Diagnostic intelligent", color = Color(0xFF52E5D3), fontWeight = FontWeight.Bold)
                    Text(if (ar) "اعرف المشكلة قبل تغيير القطع" else "Comprenez le problème avant de remplacer une pièce", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Button(onClick = diagnose, shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF52E5D3))) { Text(if (ar) "ابدأ التشخيص" else "Lancer le diagnostic", color = Color(0xFF06221F), fontWeight = FontWeight.Black) }
                }
            }
        }
        item { Text(if (ar) "أدوات سريعة" else "Actions rapides", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { ActionCard("OBD", if (ar) "فحص الأعطال" else "Scanner les défauts", Modifier.weight(1f), diagnose); ActionCard("DTC", if (ar) "رموز الخطأ" else "Codes défaut", Modifier.weight(1f), diagnose) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { ActionCard("VIN", if (ar) "هوية السيارة" else "Identité véhicule", Modifier.weight(1f), vehicles); ActionCard("AI", if (ar) "تحليل ذكي" else "Analyse IA", Modifier.weight(1f), diagnose) } }
        item { Text(if (ar) "سيارات متوفرة" else "Véhicules disponibles", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        items(models.take(6)) { vehicle -> CarImageCard(vehicle, {}) }
        if (!loading && models.isEmpty()) item { Text(if (ar) "تعذر تحميل الكتالوج. حاول لاحقًا." else "Impossible de charger le catalogue. Réessayez plus tard.", color = Color(0xFFFFB4AB)) }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, modifier: Modifier, click: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = click), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF102029))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, color = Color(0xFF52E5D3), fontWeight = FontWeight.Black); Text(subtitle, color = Color.White, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun CarImageCard(vehicle: VehicleModel, click: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = click), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1A22))) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            VehicleImage(vehicle, Modifier.size(132.dp, 92.dp).clip(RoundedCornerShape(20.dp)))
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(vehicle.name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                val years = listOfNotNull(vehicle.yearFrom, vehicle.yearTo).joinToString("–")
                Text(if (years.isBlank()) vehicle.generation ?: "" else years, color = Color(0xFF9EB0B9))
                Text("●  Ready", color = Color(0xFF52E5D3), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun VehiclesModern(padding: PaddingValues, ar: Boolean, diagnose: () -> Unit) {
    var models by remember { mutableStateOf<List<VehicleModel>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try { models = SupabaseClient.client.from("vehicle_models").select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")).decodeList() }
        catch (_: Exception) { error = true }
    }
    val filtered = models.filter { it.name.contains(query, true) || (it.generation?.contains(query, true) == true) }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(if (ar) "كتالوج السيارات" else "Catalogue véhicules", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text(if (ar) "قاعدة بيانات CarDiag للموديلات" else "Base de données CarDiag des modèles", color = Color(0xFF9EB0B9)) }
        item { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (ar) "ابحث عن سيارة" else "Rechercher un modèle") }) }
        if (error) item { Text(if (ar) "تعذر تحميل السيارات من الخادم." else "Impossible de charger les véhicules depuis le serveur.", color = Color(0xFFFFB4AB)) }
        items(filtered) { vehicle -> CarImageCard(vehicle, diagnose) }
    }
}

@Composable
private fun DiagnoseModern(padding: PaddingValues, ar: Boolean, requestBluetoothPermissions: () -> Unit) {
    var complaint by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<JsonObject?>(null) }
    var obdStatus by remember { mutableStateOf(if (ar) "غير متصل" else "Non connecté") }
    var obdResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val service = remember { DiagnosticService() }
    val obd = remember { ObdService() }
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(if (ar) "التشخيص الذكي" else "Diagnostic intelligent", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(if (ar) "ادمج أعراض السيارة مع بيانات OBD عندما يكون المحول متصلًا." else "Combinez les symptômes avec les données OBD lorsque l'adaptateur est connecté.", color = Color(0xFF9EB0B9))
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF102029))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (ar) "OBD-II" else "OBD-II", color = Color(0xFF52E5D3), fontWeight = FontWeight.Black)
                    Text(obdStatus, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = requestBluetoothPermissions) { Text(if (ar) "صلاحيات Bluetooth" else "Autoriser Bluetooth") }
                        Button(onClick = {
                            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 31) return@Button
                            val device: BluetoothDevice? = obd.bondedDevices().firstOrNull()
                            if (device == null) { obdStatus = if (ar) "لا يوجد محول مقترن" else "Aucun adaptateur appairé"; return@Button }
                            scope.launch {
                                try { obdStatus = obd.connect(device); obdResult = obd.readTroubleCodes() }
                                catch (e: Exception) { obdStatus = e.message ?: "OBD connection failed" }
                            }
                        }) { Text(if (ar) "اتصال وفحص" else "Connecter & scanner") }
                    }
                    obdResult?.let { Text("DTC: $it", color = Color(0xFFFFD180)) }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1A22))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (ar) "ما الذي يحدث؟" else "Que se passe-t-il ?", color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = complaint, onValueChange = { complaint = it }, modifier = Modifier.fillMaxWidth(), minLines = 5, label = { Text(if (ar) "الأعراض" else "Symptômes") })
                    Button(onClick = {
                        if (complaint.isBlank()) return@Button
                        scope.launch {
                            running = true; result = null
                            try { result = service.runDiagnostic(null, complaint, if (ar) "ar" else "fr", codes = emptyList(), vehicle = JsonObject(emptyMap())) }
                            catch (e: Exception) { result = JsonObject(mapOf("error" to kotlinx.serialization.json.JsonPrimitive(e.message ?: "Diagnostic failed"))) }
                            finally { running = false }
                        }
                    }, enabled = !running, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF52E5D3))) {
                        if (running) CircularProgressIndicator(Modifier.size(20.dp), color = Color(0xFF06221F), strokeWidth = 2.dp)
                        else Text(if (ar) "حلّل المشكلة بالذكاء الاصطناعي" else "Analyser avec l'IA", color = Color(0xFF06221F), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        result?.let { item { ResultCard(it, ar) } }
    }
}

@Composable
private fun ResultCard(result: JsonObject, ar: Boolean) {
    val diagnosis = (result["diagnosis"] as? JsonObject) ?: result
    val summary = diagnosis["summary"]?.toString()?.trim('"') ?: diagnosis.toString()
    val severity = diagnosis["severity"]?.toString()?.trim('"')
    val confidence = diagnosis["confidence"]?.toString()?.trim('"')
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF102C2A))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (ar) "نتيجة التحليل" else "Résultat de l'analyse", color = Color(0xFF52E5D3), fontWeight = FontWeight.Black)
            Text(summary, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            if (severity != null) Text("${if (ar) "الخطورة" else "Sévérité"}: $severity", color = Color.White)
            if (confidence != null) Text("${if (ar) "الثقة" else "Confiance"}: $confidence", color = Color.White)
            Text(if (ar) "تنبيه: هذا تحليل مساعد وليس بديلًا عن فحص ميكانيكي مؤهل." else "Attention : cette analyse est une aide et ne remplace pas un contrôle professionnel.", color = Color(0xFFFFD180), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsModern(padding: PaddingValues, ar: Boolean, change: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (ar) "الإعدادات" else "Réglages", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1A22))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (ar) "اللغة" else "Langue", color = Color.White, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = { change("ar") }) { Text("العربية") }; Button(onClick = { change("fr") }) { Text("Français") } }
            }
        }
    }
}
