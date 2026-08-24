package dz.cardiag.app

import android.Manifest
import android.app.LocaleManager
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dz.cardiag.app.core.AuthService
import dz.cardiag.app.core.DiagnosticService
import dz.cardiag.app.core.NetworkStatus
import dz.cardiag.app.core.ObdService
import dz.cardiag.app.core.SupabaseClient
import dz.cardiag.app.core.VehicleCache
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
data class UserVehicle(
    val id: String,
    @SerialName("model_id") val modelId: String,
    val nickname: String? = null,
    val vin: String? = null,
    val mileage: Int? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false
)

@Serializable
data class DiagnosticHistory(
    val id: String,
    val complaint: String? = null,
    val status: String,
    val language: String,
    @SerialName("created_at") val createdAt: String
)

class ModernMainActivity : ComponentActivity() {
    private val bluetoothPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CarDiagApp(::requestBluetoothPermissions, ::setLanguage) }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        }
    }

    private fun setLanguage(language: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language)
        }
    }
}

@Composable
private fun CarDiagApp(requestBluetoothPermissions: () -> Unit, setLanguage: (String) -> Unit) {
    val context = LocalContext.current
    val auth = remember { AuthService() }
    // CarDiag is guest-first: authentication is optional and must never block app startup.
    var authenticated by remember { mutableStateOf(true) }
    var dark by rememberSaveable { mutableStateOf(context.getSharedPreferences("cardiag_settings", Context.MODE_PRIVATE).getBoolean("dark", true)) }
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    val direction = if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        CarDiagTheme(dark) {
            MainShell(
                arabic = ar,
                dark = dark,
                setDark = { value -> dark = value; context.getSharedPreferences("cardiag_settings", Context.MODE_PRIVATE).edit().putBoolean("dark", value).apply() },
                setLanguage = setLanguage,
                requestBluetoothPermissions = requestBluetoothPermissions,
                // Sign out only clears the optional Supabase session; it must not return the user to Login.
                signOut = { scope.launch { auth.signOut(); authenticated = true } }
            )
        }
    }
}

@Composable
private fun CarDiagTheme(dark: Boolean, content: @Composable () -> Unit) {
    val darkScheme = darkColorScheme(
        primary = Color(0xFF55D9CA), onPrimary = Color(0xFF00201D), secondary = Color(0xFFB3CBD0),
        background = Color(0xFF071015), surface = Color(0xFF0C181F), surfaceVariant = Color(0xFF17262D)
    )
    val lightScheme = lightColorScheme(primary = Color(0xFF006A63), secondary = Color(0xFF48656A))
    MaterialTheme(colorScheme = if (dark) darkScheme else lightScheme, typography = Typography(), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    arabic: Boolean,
    dark: Boolean,
    setDark: (Boolean) -> Unit,
    setLanguage: (String) -> Unit,
    requestBluetoothPermissions: () -> Unit,
    signOut: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val titles = if (arabic) listOf("الرئيسية", "التشخيص", "مرآبي", "السجل", "الإعدادات") else listOf("Accueil", "Diagnostic", "Garage", "Historique", "Réglages")
    val icons = listOf(Icons.Default.Home, Icons.Default.Build, Icons.Default.DirectionsCar, Icons.Default.History, Icons.Default.Settings)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("CarDiag", fontWeight = FontWeight.Black) },
                navigationIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = "CarDiag", tint = MaterialTheme.colorScheme.primary) }
            )
        },
        bottomBar = {
            NavigationBar {
                titles.forEachIndexed { i, title ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], contentDescription = title) }, label = { Text(title, maxLines = 1) })
                }
            }
        }
    ) { padding ->
        when (tab) {
            0 -> HomeScreen(padding, arabic, { tab = 1 }, { tab = 2 })
            1 -> DiagnosticScreen(padding, arabic, requestBluetoothPermissions)
            2 -> GarageScreen(padding, arabic)
            3 -> HistoryScreen(padding, arabic)
            else -> SettingsScreen(padding, arabic, dark, setDark, setLanguage, signOut)
        }
    }
}

@Composable
private fun VehicleImage(vehicle: VehicleModel, modifier: Modifier = Modifier) {
    AsyncImage(
        model = vehicle.imageUrl,
        contentDescription = vehicle.name,
        modifier = modifier.semantics { contentDescription = vehicle.name },
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.cardiag_car_fallback),
        error = painterResource(R.drawable.cardiag_car_fallback)
    )
}

@Composable
private fun HomeScreen(padding: PaddingValues, ar: Boolean, diagnose: () -> Unit, garage: () -> Unit) {
    val context = LocalContext.current
    var models by remember { mutableStateOf(VehicleCache.read(context)) }
    var loading by remember { mutableStateOf(models.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        loading = true; error = null
        runCatching {
            SupabaseClient.client.from("vehicle_models").select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")).decodeList<VehicleModel>()
        }.onSuccess { fresh -> models = fresh; VehicleCache.write(context, fresh) }
            .onFailure { if (models.isEmpty()) error = it.message }
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }
    val online = NetworkStatus.isOnline(context)
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!online) item { OfflineBanner(ar) }
        item {
            Card(shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth().height(255.dp)) {
                    val featured = models.firstOrNull()
                    if (featured != null) VehicleImage(featured, Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface))))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (ar) "تشخيص ذكي لسيارتك" else "Diagnostic intelligent", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(if (ar) "اعرف المشكلة قبل تغيير القطع" else "Comprenez le problème avant de remplacer une pièce", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Button(onClick = diagnose) { Icon(Icons.Default.Build, null); Spacer(Modifier.width(6.dp)); Text(if (ar) "ابدأ التشخيص" else "Lancer le diagnostic") }
                    }
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { QuickAction(Icons.Default.Bluetooth, "OBD", if (ar) "فحص المحول" else "Scanner OBD", Modifier.weight(1f), diagnose); QuickAction(Icons.Default.DirectionsCar, "VIN", if (ar) "هوية السيارة" else "Identité VIN", Modifier.weight(1f), garage) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { QuickAction(Icons.Default.History, "DTC", if (ar) "رموز الأعطال" else "Codes défaut", Modifier.weight(1f), diagnose); QuickAction(Icons.Default.Add, "Garage", if (ar) "أضف سيارتك" else "Ajouter une voiture", Modifier.weight(1f), garage) } }
        item { SectionTitle(if (ar) "الموديلات" else "Modèles") }
        if (loading) items(3) { SkeletonCard() }
        items(models.take(8)) { CarCard(it) }
        if (!loading && models.isEmpty()) item {
            EmptyState(if (ar) "لا توجد بيانات متاحة" else "Aucune donnée disponible", error ?: if (ar) "تحقق من الاتصال ثم أعد المحاولة." else "Vérifiez votre connexion puis réessayez.") { scope.launch { refresh() } }
        }
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, modifier: Modifier, click: () -> Unit) {
    Card(modifier.clickable(onClick = click), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(title, fontWeight = FontWeight.Black); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }

@Composable private fun CarCard(vehicle: VehicleModel, click: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth().clickable(onClick = click), shape = RoundedCornerShape(22.dp)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { VehicleImage(vehicle, Modifier.size(132.dp, 92.dp).clip(RoundedCornerShape(20.dp))); Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(vehicle.name, fontWeight = FontWeight.Black); Text(listOfNotNull(vehicle.yearFrom, vehicle.yearTo).joinToString("–").ifBlank { vehicle.generation.orEmpty() }, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(if (vehicle.imageUrl.isNullOrBlank()) "Illustration" else "Photo", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) } } }
}

@Composable private fun SkeletonCard() { Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp)) { Box(Modifier.size(110.dp, 78.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)); Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Box(Modifier.width(150.dp).height(16.dp).background(MaterialTheme.colorScheme.surfaceVariant)); Box(Modifier.width(90.dp).height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant)) } } } }

@Composable private fun OfflineBanner(ar: Boolean) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CloudOff, null); Spacer(Modifier.width(8.dp)); Text(if (ar) "وضع عدم الاتصال: البيانات المخزنة مؤقتًا متاحة." else "Hors connexion : les données mises en cache restent disponibles.") } } }

@Composable
private fun DiagnosticScreen(padding: PaddingValues, ar: Boolean, requestBluetoothPermissions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { DiagnosticService() }
    val obd = remember { ObdService() }
    var complaint by rememberSaveable { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<JsonObject?>(null) }
    var status by rememberSaveable { mutableStateOf(if (ar) "غير متصل" else "Non connecté") }
    var dtc by remember { mutableStateOf<List<String>>(emptyList()) }
    var live by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    val online = NetworkStatus.isOnline(context)
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle(if (ar) "التشخيص" else "Diagnostic"); Text(if (ar) "استخدم OBD-II مع وصف المشكلة للحصول على تحليل منظم." else "Combinez OBD-II et symptômes pour une analyse structurée.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (!online) item { OfflineBanner(ar) }
        item {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (ar) "وصف المشكلة" else "Symptômes", fontWeight = FontWeight.Bold)
                OutlinedTextField(value = complaint, onValueChange = { complaint = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text(if (ar) "مثال: المحرك يهتز عند التوقف" else "Ex. moteur instable au ralenti") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = requestBluetoothPermissions) { Icon(Icons.Default.Bluetooth, null); Spacer(Modifier.width(4.dp)); Text(if (ar) "Bluetooth" else "Bluetooth") }
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            requestBluetoothPermissions()
                        } else {
                            val device: BluetoothDevice? = obd.bondedDevices().firstOrNull()
                            if (device == null) {
                                status = if (ar) "لا يوجد محول ELM327 مقترن" else "Aucun adaptateur ELM327 appairé"
                            } else {
                                scope.launch {
                                    try {
                                        status = obd.connect(device)
                                        dtc = obd.readTroubleCodes()
                                        live = mapOf(
                                            "RPM" to (obd.readRpm()?.let { "%.0f rpm".format(it) } ?: "—"),
                                            "Coolant" to (obd.readCoolantTemperature()?.let { "%.1f °C".format(it) } ?: "—"),
                                            "Speed" to (obd.readVehicleSpeedKmh()?.let { "%.0f km/h".format(it) } ?: "—")
                                        )
                                    } catch (e: Exception) {
                                        status = e.message ?: "OBD error"
                                    }
                                }
                            }
                        }
                    }) { Text(if (ar) "اتصال وفحص" else "Connecter") }
                }
                Text(status, color = MaterialTheme.colorScheme.primary)
                if (dtc.isNotEmpty()) Text("DTC: ${dtc.joinToString()}", color = MaterialTheme.colorScheme.error)
                if (live.isNotEmpty()) live.forEach { (k, v) -> Text("$k: $v") }
            } }
        }
        item {
            Button(onClick = {
                if (!online) { error = if (ar) "الاتصال بالإنترنت مطلوب لتحليل AI." else "Internet requis pour l'analyse IA."; return@Button }
                if (complaint.isBlank() && dtc.isEmpty()) { error = if (ar) "أدخل الأعراض أو اتصل بـOBD أولًا." else "Ajoutez des symptômes ou connectez l'OBD."; return@Button }
                scope.launch {
                    running = true; error = null
                    try {
                        val measurementJson = buildJsonObject { live.forEach { (k, v) -> put(k, v) } }
                        result = withContext(Dispatchers.IO) { service.runDiagnostic(null, complaint, if (ar) "ar" else "fr", dtc, buildJsonObject { put("complaint", complaint) }, measurementJson) }
                    } catch (e: Exception) { error = e.message ?: if (ar) "فشل التشخيص" else "Échec du diagnostic" }
                    finally { running = false }
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !running) { if (running) CircularProgressIndicator(strokeWidth = 2.dp) else Text(if (ar) "تحليل بالذكاء الاصطناعي" else "Analyser avec l'IA") }
        }
        error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
        result?.let { json -> item { DiagnosisResultCard(json, ar) } }
    }
}

@Composable private fun DiagnosisResultCard(result: JsonObject, ar: Boolean) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(if (ar) "نتيجة منظمة" else "Résultat structuré", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); result.entries.take(12).forEach { (key, value) -> Text("$key: $value") } } } }

@Composable
private fun GarageScreen(padding: PaddingValues, ar: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var vehicles by remember { mutableStateOf<List<UserVehicle>>(emptyList()) }
    var models by remember { mutableStateOf<List<VehicleModel>>(VehicleCache.read(context)) }
    var nickname by rememberSaveable { mutableStateOf("") }
    var vin by rememberSaveable { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf<VehicleModel?>(models.firstOrNull()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    fun refresh() { scope.launch { loading = true; runCatching { vehicles = SupabaseClient.client.from("user_vehicles").select(Columns.list("id","model_id","nickname","vin","mileage","is_primary")).decodeList() }.onFailure { error = it.message }; loading = false } }
    LaunchedEffect(Unit) { if (models.isEmpty()) runCatching { models = SupabaseClient.client.from("vehicle_models").select(Columns.list("id","make_id","name","year_from","year_to","generation","image_url")).decodeList<VehicleModel>() }; refresh() }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle(if (ar) "مرآبي" else "Mon garage"); Text(if (ar) "سياراتك وسجلها محفوظان لحسابك." else "Vos véhicules et leur historique sont liés à votre compte.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(if (ar) "إضافة سيارة" else "Ajouter un véhicule", fontWeight = FontWeight.Bold); OutlinedTextField(nickname, { nickname = it }, Modifier.fillMaxWidth(), label = { Text(if (ar) "اسم السيارة" else "Nom") }); OutlinedTextField(vin, { vin = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(17) }, Modifier.fillMaxWidth(), label = { Text("VIN") }, singleLine = true); Text(if (ar) "الموديل: ${selectedModel?.name ?: "اختر من الكتالوج"}" else "Modèle: ${selectedModel?.name ?: "Choisir dans le catalogue"}"); Button(onClick = { val model = selectedModel ?: return@Button; scope.launch { error = null; runCatching { require(vin.isBlank() || vin.length == 17) { if (ar) "VIN يجب أن يكون 17 خانة" else "Le VIN doit contenir 17 caractères" }; SupabaseClient.client.from("user_vehicles").insert(mapOf("model_id" to model.id, "make_id" to model.makeId, "nickname" to nickname.ifBlank { model.name }, "vin" to vin.ifBlank { null }, "is_primary" to vehicles.isEmpty())); nickname = ""; vin = ""; refresh() }.onFailure { error = it.message } } }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text(if (ar) "حفظ السيارة" else "Enregistrer") } } } }
        if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        item { SectionTitle(if (ar) "سياراتي" else "Mes véhicules") }
        if (loading) items(2) { SkeletonCard() }
        items(vehicles) { v -> Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(v.nickname ?: "Vehicle", fontWeight = FontWeight.Black); Text("VIN: ${v.vin ?: "—"}"); Text("${if (ar) "الموديل" else "Modèle"}: ${models.firstOrNull { it.id == v.modelId }?.name ?: v.modelId}") } } }
        if (!loading && vehicles.isEmpty()) item { Text(if (ar) "أضف سيارتك الأولى لربط التشخيص بها." else "Ajoutez votre premier véhicule pour lier les diagnostics.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun HistoryScreen(padding: PaddingValues, ar: Boolean) {
    var history by remember { mutableStateOf<List<DiagnosticHistory>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    fun refresh() { scope.launch { loading = true; runCatching { history = SupabaseClient.client.from("diagnostic_sessions").select(Columns.list("id","complaint","status","language","created_at")).decodeList() }.onFailure { error = it.message }; loading = false } }
    LaunchedEffect(Unit) { refresh() }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle(if (ar) "سجل التشخيص" else "Historique") }
        if (loading) items(3) { SkeletonCard() }
        items(history) { h -> Card { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (h.status == "completed") Icons.Default.CheckCircle else Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary); Column(Modifier.padding(start = 12.dp)) { Text(h.complaint ?: if (ar) "فحص بدون وصف" else "Diagnostic sans symptôme", fontWeight = FontWeight.Bold); Text("${h.status} • ${h.createdAt}", color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
        if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        if (!loading && history.isEmpty()) item { EmptyState(if (ar) "السجل فارغ" else "Historique vide", if (ar) "نتائج التشخيص ستظهر هنا." else "Vos diagnostics apparaîtront ici.") { refresh() } }
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues, ar: Boolean, dark: Boolean, setDark: (Boolean) -> Unit, setLanguage: (String) -> Unit, signOut: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle(if (ar) "الإعدادات" else "Réglages") }
        item { SettingRow(Icons.Default.DarkMode, if (ar) "الوضع الداكن" else "Mode sombre", if (ar) "واجهة مريحة للعين" else "Interface confortable", Switch(checked = dark, onCheckedChange = setDark)) }
        item { SettingRow(Icons.Default.Language, if (ar) "اللغة" else "Langue", if (ar) "العربية" else "Français", TextButton(onClick = { setLanguage(if (ar) "fr" else "ar") }) { Text(if (ar) "Français" else "العربية") }) }
        item { SettingRow(Icons.Default.Security, if (ar) "الخصوصية" else "Confidentialité", if (ar) "بياناتك مرتبطة بحسابك فقط." else "Vos données restent liées à votre compte.", null) }
        item { OutlinedButton(onClick = signOut, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text(if (ar) "تسجيل الخروج" else "Déconnexion") } }
    }
}

@Composable private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: @Composable (() -> Unit)?) { Card { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; action?.invoke() } } }

@Composable private fun EmptyState(title: String, body: String, retry: () -> Unit) { Card { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary); Text(title, fontWeight = FontWeight.Bold); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant); OutlinedButton(onClick = retry) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Retry") } } } }
