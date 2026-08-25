package dz.cardiag.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dz.cardiag.app.core.SupabaseClient
import dz.cardiag.app.ui.theme.CarDiagTheme
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FunctionalVehicle(
    val id: String,
    @SerialName("make_id") val makeId: String,
    val name: String,
    @SerialName("year_from") val yearFrom: Int? = null,
    @SerialName("year_to") val yearTo: Int? = null,
    val generation: String? = null,
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class FunctionalMake(val id: String, val name: String)

private const val FUNCTIONAL_PREFS = "cardiag_functional"
private const val PREF_DARK = "dark"
private const val PREF_ARABIC = "arabic"
private const val PREF_VEHICLE = "vehicle_id"

private data class Labels(
    val home: String, val diagnostic: String, val garage: String, val history: String, val more: String,
    val search: String, val catalog: String, val refresh: String, val noVehicles: String,
    val ai: String, val scanner: String, val liveData: String, val dtc: String, val vin: String,
    val settings: String, val language: String, val appearance: String, val about: String,
    val privacy: String, val light: String, val dark: String, val selectVehicle: String,
    val noHistory: String, val loading: String, val retry: String
)

private val FR_LABELS = Labels(
    "Accueil", "Diagnostic", "Garage", "Historique", "Plus", "Rechercher une marque ou un modèle",
    "Catalogue véhicules", "Actualiser", "Aucun véhicule", "Diagnostic AI", "Scanner OBD-II", "Live Data",
    "DTC & Faults", "Identité VIN", "Paramètres", "Langue", "Apparence", "À propos", "Confidentialité",
    "Clair", "Sombre", "Choisir un véhicule", "Aucun diagnostic enregistré", "Chargement…", "Réessayer"
)
private val AR_LABELS = Labels(
    "الرئيسية", "التشخيص", "المرآب", "السجل", "المزيد", "ابحث عن الماركة أو الموديل",
    "كتالوج السيارات", "تحديث", "لا توجد سيارات", "تشخيص بالذكاء الاصطناعي", "ماسح OBD-II", "البيانات الحية",
    "الأعطال DTC", "هوية VIN", "الإعدادات", "اللغة", "المظهر", "حول التطبيق", "الخصوصية",
    "فاتح", "داكن", "اختر سيارة", "لا توجد جلسات تشخيص", "جار التحميل…", "إعادة المحاولة"
)

@Composable
fun CarDiagFunctionalApp() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(FUNCTIONAL_PREFS, Context.MODE_PRIVATE) }
    var dark by remember { mutableStateOf(prefs.getBoolean(PREF_DARK, true)) }
    var arabic by remember { mutableStateOf(prefs.getBoolean(PREF_ARABIC, false)) }
    var tab by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<FunctionalVehicle?>(null) }
    val labels = if (arabic) AR_LABELS else FR_LABELS

    fun setDark(value: Boolean) { dark = value; prefs.edit().putBoolean(PREF_DARK, value).apply() }
    fun setArabic(value: Boolean) { arabic = value; prefs.edit().putBoolean(PREF_ARABIC, value).apply() }
    fun saveVehicle(id: String?) { prefs.edit().putString(PREF_VEHICLE, id).apply() }

    CarDiagTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            if (selected != null) {
                FunctionalVehicleScreen(vehicle = selected!!, arabic = arabic, onBack = { selected = null }, onDiagnostic = {
                    openObd(context, selected!!.id, selected!!.name)
                })
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar {
                            val icons = listOf(Icons.Default.Home, Icons.Default.Build, Icons.Default.Garage, Icons.Default.History, Icons.Default.MoreHoriz)
                            val texts = listOf(labels.home, labels.diagnostic, labels.garage, labels.history, labels.more)
                            texts.forEachIndexed { index, text ->
                                NavigationBarItem(tab == index, { tab = index }, icon = { Icon(icons[index], text) }, label = { Text(text) })
                            }
                        }
                    }
                ) { padding ->
                    when (tab) {
                        0 -> FunctionalHome(padding, labels, arabic) { vehicle -> selected = vehicle; saveVehicle(vehicle.id) }
                        1 -> FunctionalDiagnostic(padding, labels, arabic)
                        2 -> FunctionalGarage(padding, labels, arabic) { vehicle -> selected = vehicle; saveVehicle(vehicle.id) }
                        3 -> FunctionalHistory(padding, labels, arabic)
                        else -> FunctionalMore(padding, labels, arabic, dark, ::setDark, ::setArabic)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FunctionalHome(padding: PaddingValues, l: Labels, arabic: Boolean, onVehicle: (FunctionalVehicle) -> Unit) {
    var vehicles by remember { mutableStateOf<List<FunctionalVehicle>>(emptyList()) }
    var makes by remember { mutableStateOf<List<FunctionalMake>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true; error = null
            runCatching {
                val v = SupabaseClient.client.from("vehicle_models").select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")).decodeList<FunctionalVehicle>()
                val m = SupabaseClient.client.from("vehicle_makes").select(Columns.list("id", "name")).decodeList<FunctionalMake>()
                vehicles = v; makes = m
            }.onFailure { error = it.message ?: "Supabase unavailable" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }
    val filtered = vehicles.filter { v -> query.isBlank() || v.name.contains(query, true) || makes.firstOrNull { it.id == v.makeId }?.name?.contains(query, true) == true }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp).height(220.dp).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)), RoundedCornerShape(30.dp))) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("CarDiag", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    Text(if (arabic) "تشخيص سيارات ذكي ومتطور" else "SMART VEHICLE DIAGNOSTICS", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (arabic) "OBD • VIN • DTC • Live Data" else "OBD • VIN • DTC • Live Data", color = Color.White.copy(alpha = .85f))
                    Button(onClick = { /* AI screen is opened below */ }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary)) {
                        Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(l.ai, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, shape = RoundedCornerShape(18.dp), placeholder = { Text(l.search) }, leadingIcon = { Icon(Icons.Default.Search, null) }) }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text(l.catalog, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, Modifier.weight(1f)); Text("${filtered.size}", color = MaterialTheme.colorScheme.primary); IconButton({ reload() }) { Icon(Icons.Default.Refresh, l.refresh) } } }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
        error?.let { msg -> item { Column(Modifier.padding(20.dp)) { Text(msg, color = MaterialTheme.colorScheme.error); TextButton({ reload() }) { Text(l.retry) } } } }
        if (!loading && filtered.isEmpty() && error == null) item { Text(l.noVehicles, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(filtered.take(100), key = { it.id }) { vehicle ->
            val make = makes.firstOrNull { it.id == vehicle.makeId }?.name ?: "Vehicle"
            VehicleCard(vehicle, make, onVehicle)
        }
    }
}

@Composable
private fun VehicleCard(vehicle: FunctionalVehicle, make: String, onVehicle: (FunctionalVehicle) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onVehicle(vehicle) }, RoundedCornerShape(22.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(vehicle.imageUrl, vehicle.name, Modifier.size(88.dp), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(make, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(vehicle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black); Text(listOfNotNull(vehicle.generation, vehicle.yearFrom?.toString(), vehicle.yearTo?.toString()).joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FunctionalDiagnostic(padding: PaddingValues, l: Labels, arabic: Boolean) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header(l.diagnostic, if (arabic) "مركز التشخيص الذكي" else "SMART VEHICLE DIAGNOSTICS") }
        item { FeatureCard(l.scanner, if (arabic) "اتصال وقراءة وحدات ECU" else "Connexion et lecture ECU", Icons.Default.Bluetooth) { openObd(context) } }
        item { FeatureCard(l.liveData, if (arabic) "الحساسات والقيم في الوقت الحقيقي" else "Capteurs et paramètres en temps réel", Icons.Default.Speed) { context.startActivity(Intent(context, LiveDataProActivity::class.java)) } }
        item { FeatureCard(l.dtc, if (arabic) "الأكواد والأسباب وخطوات التشخيص" else "Codes, causes et procédure", Icons.Default.Warning) { openGuided(context) } }
        item { FeatureCard(l.vin, if (arabic) "التعرف على هوية السيارة عبر OBD" else "Identification VIN via OBD", Icons.Default.DirectionsCar) { openObd(context) } }
        item { FeatureCard(l.ai, if (arabic) "تحليل الأعراض بدون OBD" else "Analyse des symptômes sans OBD", Icons.Default.AutoAwesome) { context.startActivity(Intent(context, AiSymptomDiagnosisActivity::class.java)) } }
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick), RoundedCornerShape(22.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(54.dp), RoundedCornerShape(17.dp), MaterialTheme.colorScheme.primary.copy(alpha = .13f)) { Box(Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) } }
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun FunctionalGarage(padding: PaddingValues, l: Labels, arabic: Boolean, onVehicle: (FunctionalVehicle) -> Unit) {
    var vehicles by remember { mutableStateOf<List<FunctionalVehicle>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { loading = true; vehicles = runCatching { SupabaseClient.client.from("vehicle_models").select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")).decodeList<FunctionalVehicle>() }.getOrDefault(emptyList()); loading = false } }
    LaunchedEffect(Unit) { load() }
    val filtered = vehicles.filter { it.name.contains(query, true) }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header(l.garage, if (arabic) "اختر سيارتك للوصول السريع للتشخيص" else "Choisissez votre véhicule pour un accès rapide au diagnostic") }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, shape = RoundedCornerShape(18.dp), placeholder = { Text(l.selectVehicle) }, leadingIcon = { Icon(Icons.Default.Search, null) }) }
        item { OutlinedButton({ load() }, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(l.refresh) } }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
        items(filtered.take(100), key = { it.id }) { VehicleCard(it, "Vehicle", onVehicle) }
    }
}

@Composable
private fun FunctionalHistory(padding: PaddingValues, l: Labels, arabic: Boolean) {
    var loading by remember { mutableStateOf(true) }
    var count by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { loading = true; error = null; runCatching { SupabaseClient.client.from("diagnostic_sessions").select(Columns.list("id")).decodeList<Map<String, String>>() }.onSuccess { count = it.size }.onFailure { error = it.message }; loading = false } }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Header(l.history, if (arabic) "جلسات التشخيص المحفوظة" else "Sessions de diagnostic enregistrées")
        if (loading) Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator() }
        else if (error != null) Column(Modifier.padding(20.dp)) { Text(error ?: "", color = MaterialTheme.colorScheme.error); TextButton({ load() }) { Text(l.retry) } }
        else if (count == 0) Text(l.noHistory, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Card(Modifier.fillMaxWidth().padding(16.dp), RoundedCornerShape(22.dp)) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp)); Column { Text(if (arabic) "عدد جلسات التشخيص" else "Sessions de diagnostic", fontWeight = FontWeight.Bold); Text("$count", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black) } } }
    }
}

@Composable
private fun FunctionalMore(padding: PaddingValues, l: Labels, arabic: Boolean, dark: Boolean, setDark: (Boolean) -> Unit, setArabic: (Boolean) -> Unit) {
    var about by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Header(l.more, l.settings)
        SettingRow(l.language, if (arabic) "العربية" else "Français", Icons.Default.Language) { setArabic(!arabic) }
        SettingRow(l.appearance, if (dark) l.dark else l.light, if (dark) Icons.Default.Nightlight else Icons.Default.LightMode) { setDark(!dark) }
        SettingRow(l.about, "CarDiag", Icons.Default.Info) { about = true }
        SettingRow(l.privacy, if (arabic) "سياسة الخصوصية" else "Politique de confidentialité", Icons.Default.Settings) { privacy = true }
    }
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("CarDiag") }, text = { Text(if (arabic) "منصة تشخيص ومعلومات سيارات موجهة للسائق والميكانيكي." else "Plateforme de diagnostic et de connaissance automobile pour conducteurs et mécaniciens.") }, confirmButton = { TextButton({ about = false }) { Text("OK") } })
    if (privacy) AlertDialog(onDismissRequest = { privacy = false }, title = { Text(l.privacy) }, text = { Text(if (arabic) "لا تضع مفاتيح سرية داخل التطبيق. بيانات Supabase المسموح بها فقط تستخدم من التطبيق." else "Aucune clé secrète ne doit être embarquée dans l'application. Seules les données publiques autorisées sont utilisées.") }, confirmButton = { TextButton({ privacy = false }) { Text("OK") } })
}

@Composable
private fun SettingRow(title: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick), RoundedCornerShape(20.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium) }
    }
}

@Composable
private fun FunctionalVehicleScreen(vehicle: FunctionalVehicle, arabic: Boolean, onBack: () -> Unit, onDiagnostic: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.Default.ArrowBack, if (arabic) "رجوع" else "Retour") }; Text(vehicle.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) } }
        item { AsyncImage(vehicle.imageUrl, vehicle.name, Modifier.fillMaxWidth().height(260.dp), contentScale = ContentScale.Crop) }
        item { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(vehicle.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text(listOfNotNull(vehicle.generation, vehicle.yearFrom?.toString(), vehicle.yearTo?.toString()).joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onDiagnostic, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Build, null); Spacer(Modifier.width(8.dp)); Text(if (arabic) "ابدأ التشخيص" else "Lancer le diagnostic", fontWeight = FontWeight.Black) } } }
        item { ProfileCard(if (arabic) "نظرة عامة" else "Overview", if (arabic) "ملف السيارة" else "Fiche véhicule", Icons.Default.DirectionsCar) }
        item { ProfileCard(if (arabic) "المحرك" else "Engine", if (arabic) "المحركات والمواصفات التفصيلية" else "Moteurs et caractéristiques détaillées", Icons.Default.Settings) }
        item { ProfileCard(if (arabic) "ECU و OBD" else "ECU & OBD", if (arabic) "وحدات التحكم والبروتوكولات" else "Calculateurs et protocoles", Icons.Default.Bluetooth) }
        item { ProfileCard("DTC", if (arabic) "الأعطال وخطوات التشخيص" else "Codes défaut et diagnostic guidé", Icons.Default.Warning) }
    }
}

@Composable
private fun ProfileCard(title: String, subtitle: String, icon: ImageVector) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), RoundedCornerShape(20.dp), CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp)); Column { Text(title, fontWeight = FontWeight.Black); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
}
