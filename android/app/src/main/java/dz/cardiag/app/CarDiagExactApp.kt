package dz.cardiag.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.vector.ImageVector
import coil.compose.AsyncImage
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UiModel(val id: String, val name: String, val imageUrl: String? = null)

@Serializable
data class ExactVehicle(
    val id: String,
    @SerialName("make_id") val makeId: String,
    val name: String,
    @SerialName("year_from") val yearFrom: Int? = null,
    @SerialName("year_to") val yearTo: Int? = null,
    val generation: String? = null,
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class ExactMake(val id: String, val name: String)

private val DarkBg = Color(0xFF06090B)
private val DarkSurface = Color(0xFF0D1418)
private val DarkTeal = Color(0xFF48D7C5)
private val DarkSoft = Color(0xFF153F3B)
private val DarkText = Color(0xFFF5F8F8)
private val DarkMuted = Color(0xFF8B9A9F)
private val LightBg = Color(0xFFF4F7F7)
private val LightSurface = Color.White
private val LightTeal = Color(0xFF087F73)
private val LightSoft = Color(0xFFDDF4F0)
private val LightText = Color(0xFF102024)
private val LightMuted = Color(0xFF647277)

private const val PREFS = "cardiag_ui"
private const val KEY_DARK = "dark_mode"
private const val KEY_LANG = "language"

private class UiPrefs(context: Context) {
    private val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var dark: Boolean
        get() = p.getBoolean(KEY_DARK, true)
        set(v) = p.edit().putBoolean(KEY_DARK, v).apply()
    var lang: String
        get() = p.getString(KEY_LANG, "fr") ?: "fr"
        set(v) = p.edit().putString(KEY_LANG, v).apply()
}

private data class Copy(
    val home: String, val diagnostic: String, val garage: String, val history: String, val more: String,
    val smart: String, val hero: String, val launch: String, val search: String, val catalog: String,
    val models: String, val makes: String, val scanner: String, val vehicleProfile: String,
    val language: String, val appearance: String, val dark: String, val light: String,
    val account: String, val about: String, val retry: String, val back: String, val noHistory: String,
    val historyHint: String, val addVehicle: String, val noVehicles: String
)

private val FR = Copy("Accueil", "Diagnostic", "Garage", "Historique", "Plus", "SMART VEHICLE DIAGNOSTICS", "Votre voiture.\nVos données. Votre diagnostic.", "Lancer le diagnostic", "Rechercher une marque ou un modèle", "Catalogue véhicules", "MODÈLES", "MARQUES", "OBD-II • SCANNER", "FICHE VÉHICULE", "Langue", "Apparence", "Mode sombre", "Mode clair", "Compte", "À propos", "Réessayer", "Retour", "Aucun diagnostic enregistré", "Vos sessions, DTC et rapports apparaîtront ici.", "Ajouter un véhicule", "Aucun véhicule trouvé")
private val AR = Copy("الرئيسية", "التشخيص", "المرآب", "السجل", "المزيد", "تشخيص السيارات الذكي", "سيارتك.\nبياناتك. تشخيصك.", "بدء التشخيص", "ابحث عن الماركة أو الموديل", "كتالوج السيارات", "الموديلات", "الماركات", "ماسح OBD-II", "ملف السيارة", "اللغة", "المظهر", "الوضع الداكن", "الوضع الفاتح", "الحساب", "حول التطبيق", "إعادة المحاولة", "رجوع", "لا توجد تشخيصات محفوظة", "ستظهر جلسات التشخيص وأكواد الأعطال والتقارير هنا.", "إضافة سيارة", "لم يتم العثور على سيارات")

@Composable
fun CarDiagExactApp() {
    val context = LocalContext.current
    val prefs = remember { UiPrefs(context) }
    var dark by remember { mutableStateOf(prefs.dark) }
    var lang by remember { mutableStateOf(prefs.lang) }
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ExactVehicle?>(null) }
    var showLanguage by remember { mutableStateOf(false) }
    val c = if (lang == "ar") AR else FR
    val direction = if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    val teal = if (dark) DarkTeal else LightTeal
    val bg = if (dark) DarkBg else LightBg
    val surface = if (dark) DarkSurface else LightSurface
    val text = if (dark) DarkText else LightText
    val muted = if (dark) DarkMuted else LightMuted
    val soft = if (dark) DarkSoft else LightSoft

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = teal, background = bg, surface = surface, onSurface = text, onSurfaceVariant = muted, onPrimary = bg) else lightColorScheme(primary = teal, background = bg, surface = surface, onSurface = text, onSurfaceVariant = muted, onPrimary = Color.White)) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            if (selected != null) {
                ExactVehicleProfileScreen(model = UiModel(selected!!.id, selected!!.name, selected!!.imageUrl), onBack = { selected = null })
            } else {
                Scaffold(
                    containerColor = bg,
                    bottomBar = {
                        NavigationBar(containerColor = surface) {
                            val labels = listOf(c.home, c.diagnostic, c.garage, c.history, c.more)
                            val icons = listOf(Icons.Default.Home, Icons.Default.Build, Icons.Default.Garage, Icons.Default.History, Icons.Default.Settings)
                            labels.forEachIndexed { index, label -> NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], label) }, label = { Text(label) }) }
                        }
                    }
                ) { padding ->
                    when (tab) {
                        0 -> HomeScreen(padding, c, dark, teal, bg, surface, text, muted, soft) { selected = it }
                        1 -> DiagnosticScreen(padding, c, teal, bg, surface, text, muted)
                        2 -> GarageScreen(padding, c, teal, bg, surface, text, muted) { selected = it }
                        3 -> HistoryScreen(padding, c, teal, bg, surface, text, muted)
                        else -> MoreScreen(padding, c, dark, teal, bg, surface, text, muted, onLanguage = { showLanguage = true }, onTheme = { dark = !dark; prefs.dark = dark })
                    }
                }
            }
        }
        if (showLanguage) {
            AlertDialog(
                onDismissRequest = { showLanguage = false },
                title = { Text(c.language, fontWeight = FontWeight.Black) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageRow("Français", lang == "fr") { lang = "fr"; prefs.lang = "fr"; showLanguage = false }
                    LanguageRow("العربية", lang == "ar") { lang = "ar"; prefs.lang = "ar"; showLanguage = false }
                } },
                confirmButton = { TextButton(onClick = { showLanguage = false }) { Text(c.back) } }
            )
        }
    }
}

@Composable private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold); if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black) }
    }
}

@Composable private fun Header(title: String, eyebrow: String, teal: Color, muted: Color, surface: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(teal.copy(alpha = .14f)), Alignment.Center) { Icon(Icons.Default.DirectionsCar, null, tint = teal) }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text(eyebrow, color = muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun HomeScreen(padding: PaddingValues, c: Copy, dark: Boolean, teal: Color, bg: Color, surface: Color, text: Color, muted: Color, soft: Color, onVehicle: (ExactVehicle) -> Unit) {
    val scope = rememberCoroutineScope(); var vehicles by remember { mutableStateOf<List<ExactVehicle>>(emptyList()) }; var makes by remember { mutableStateOf<List<ExactMake>>(emptyList()) }; var query by remember { mutableStateOf("") }; var loading by remember { mutableStateOf(true) }
    fun load() { scope.launch { loading = true; runCatching { vehicles = SupabaseClient.client.from("vehicle_models").select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")).decodeList<ExactVehicle>() }; runCatching { makes = SupabaseClient.client.from("vehicle_makes").select(Columns.list("id", "name")).decodeList<ExactMake>() }; loading = false } }
    LaunchedEffect(Unit) { load() }
    val filtered = vehicles.filter { v -> query.isBlank() || v.name.contains(query, true) || makes.firstOrNull { it.id == v.makeId }?.name?.contains(query, true) == true }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Header("CarDiag", c.smart, teal, muted, surface) }
        item { Box(Modifier.fillMaxWidth().height(330.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(32.dp)).background(Brush.linearGradient(listOf(if (dark) Color(0xFF18383D) else Color(0xFFE0F5F1), surface, bg)))) {
            Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Bottom) { Text(c.smart, color = teal, fontWeight = FontWeight.Black); Spacer(Modifier.height(8.dp)); Text(c.hero, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = text); Spacer(Modifier.height(18.dp)); Button(onClick = { onVehicle(filtered.firstOrNull() ?: vehicles.firstOrNull() ?: return@Button) }, enabled = vehicles.isNotEmpty(), shape = RoundedCornerShape(17.dp)) { Icon(Icons.Default.Build, null); Spacer(Modifier.width(8.dp)); Text(c.launch, fontWeight = FontWeight.Black) } }
        } }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(19.dp), leadingIcon = { Icon(Icons.Default.Search, null, tint = teal) }, placeholder = { Text(c.search) }); IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, c.retry, tint = teal) } } }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), Arrangement.spacedBy(10.dp)) { StatCard(vehicles.size.toString(), c.models, Modifier.weight(1f), teal, surface, muted); StatCard(makes.size.toString(), c.makes, Modifier.weight(1f), teal, surface, muted); StatCard("OBD-II", c.scanner, Modifier.weight(1f), teal, surface, muted) } }
        item { Text(c.catalog, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = text) }
        if (loading) item { Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) { CircularProgressIndicator(color = teal) } }
        else if (filtered.isEmpty()) item { Text(c.noVehicles, Modifier.padding(24.dp), color = muted) }
        else items(filtered.take(20), key = { it.id }) { v -> VehicleCard(v, makes.firstOrNull { it.id == v.makeId }?.name ?: "Vehicle", teal, surface, text, muted, onVehicle) }
    }
}

@Composable private fun StatCard(value: String, label: String, modifier: Modifier, teal: Color, surface: Color, muted: Color) { Card(modifier, RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surface)) { Column(Modifier.fillMaxWidth().padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = teal, fontWeight = FontWeight.Black); Text(label, color = muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) } } }

@Composable private fun VehicleCard(v: ExactVehicle, make: String, teal: Color, surface: Color, text: Color, muted: Color, onClick: (ExactVehicle) -> Unit) { Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onClick(v) }, RoundedCornerShape(27.dp), colors = CardDefaults.cardColors(containerColor = surface)) { Column { Box(Modifier.fillMaxWidth().height(205.dp)) { AsyncImage(v.imageUrl, v.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f))))); Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) { Text(make.uppercase(), color = teal, fontWeight = FontWeight.Black); Text(v.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = text); Text(listOfNotNull(v.generation, v.yearFrom?.toString(), v.yearTo?.toString()).joinToString(" • ").ifBlank { "Vehicle profile" }, color = muted) } }; Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Text("${if (teal == DarkTeal) "View" else "Voir"} • ${if (teal == DarkTeal) "vehicle profile" else "fiche technique"}", fontWeight = FontWeight.Bold, Modifier.weight(1f)); Text("→", color = teal, fontWeight = FontWeight.Black) } } } }

@Composable private fun DiagnosticScreen(padding: PaddingValues, c: Copy, teal: Color, bg: Color, surface: Color, text: Color, muted: Color) { val context = LocalContext.current; LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) { item { Header(c.diagnostic, "OBD-II • LIVE DATA • DTC", teal, muted, surface) }; item { ActionCard("OBD-II Scanner", if (c == AR) "Bluetooth ELM327 • ECU • أكواد الأعطال" else "Bluetooth ELM327 • ECU • DTC", Icons.Default.Build, teal, surface, text, muted) { openObd(context) } }; item { ActionCard("Live Data", if (c == AR) "RPM • الحرارة • الحمل • الحساسات" else "RPM • température • charge • capteurs", Icons.Default.Speed, teal, surface, text, muted) { openObd(context) } }; item { ActionCard("DTC & Faults", if (c == AR) "أكواد الأعطال والتحليل الموجه" else "Codes défaut et analyse guidée", Icons.Default.Warning, teal, surface, text, muted) { openObd(context) } }; item { ActionCard(if (c == AR) "التشخيص الموجه" else "Diagnostic guidé", if (c == AR) "الأعراض + السيارة + القياسات" else "Symptômes + véhicule + mesures", Icons.Default.Build, teal, surface, text, muted) { openGuided(context) } } } }

@Composable private fun ActionCard(title: String, subtitle: String, icon: ImageVector, teal: Color, surface: Color, text: Color, muted: Color, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick), RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = surface)) { Row(Modifier.padding(19.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(teal.copy(alpha = .14f)), Alignment.Center) { Icon(icon, null, tint = teal) }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = text); Text(subtitle, color = muted) }; Text("›", color = teal, style = MaterialTheme.typography.headlineSmall) } } }

@Composable private fun GarageScreen(padding: PaddingValues, c: Copy, teal: Color, bg: Color, surface: Color, text: Color, muted: Color, onVehicle: (ExactVehicle) -> Unit) { var vehicles by remember { mutableStateOf<List<ExactVehicle>>(emptyList()) }; LaunchedEffect(Unit) { runCatching { vehicles = SupabaseClient.client.from("vehicle_models").select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")).decodeList<ExactVehicle>() } }; LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { item { Header(c.garage, if (c == AR) "سياراتك" else "VOS VÉHICULES", teal, muted, surface) }; item { Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(27.dp), colors = CardDefaults.cardColors(containerColor = surface)) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(if (c == AR) "سيارتك" else "Votre véhicule", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = text); Text(if (c == AR) "أضف سيارة للوصول السريع إلى التشخيص والمعلومات التقنية." else "Ajoutez votre véhicule pour accéder rapidement au diagnostic et aux données techniques.", color = muted); Button(onClick = { vehicles.firstOrNull()?.let(onVehicle) }, enabled = vehicles.isNotEmpty(), Modifier.fillMaxWidth(), RoundedCornerShape(16.dp)) { Text(c.addVehicle) } } } }; items(vehicles.take(8), key = { it.id }) { VehicleCard(it, "Catalogue", teal, surface, text, muted, onVehicle) } } }

@Serializable private data class SessionRow(val id: String, @SerialName("vehicle_model_id") val vehicleModelId: String? = null, val status: String? = null, val created_at: String? = null)

@Composable private fun HistoryScreen(padding: PaddingValues, c: Copy, teal: Color, bg: Color, surface: Color, text: Color, muted: Color) { var rows by remember { mutableStateOf<List<SessionRow>>(emptyList()) }; var loading by remember { mutableStateOf(true) }; LaunchedEffect(Unit) { runCatching { rows = SupabaseClient.client.from("diagnostic_sessions").select(Columns.list("id", "vehicle_model_id", "status", "created_at")).decodeList<SessionRow>() }; loading = false }; LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp)) { item { Header(c.history, if (c == AR) "جلسات التشخيص" else "VOS DIAGNOSTICS", teal, muted, surface) }; when { loading -> item { Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) { CircularProgressIndicator(color = teal) } }; rows.isEmpty() -> item { Card(Modifier.fillMaxWidth().padding(16.dp), RoundedCornerShape(27.dp), colors = CardDefaults.cardColors(containerColor = surface)) { Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.History, null, tint = teal, Modifier.size(48.dp)); Text(c.noHistory, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = text); Text(c.historyHint, color = muted) } } }; else -> items(rows.take(30), key = { it.id }) { s -> Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surface)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.History, null, tint = teal); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(s.vehicleModelId ?: "Vehicle", color = text, fontWeight = FontWeight.Black); Text(s.status ?: "created", color = muted) }; Text("›", color = teal, fontWeight = FontWeight.Black) } } } } } }

@Composable private fun MoreScreen(padding: PaddingValues, c: Copy, dark: Boolean, teal: Color, bg: Color, surface: Color, text: Color, muted: Color, onLanguage: () -> Unit, onTheme: () -> Unit) { LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Header(c.more, "CARDIAG", teal, muted, surface) }; item { ActionCard(c.language, "Français • العربية • RTL", Icons.Default.Language, teal, surface, text, muted, onLanguage) }; item { ActionCard(c.appearance, if (dark) c.dark else c.light, if (dark) Icons.Default.DarkMode else Icons.Default.LightMode, teal, surface, text, muted, onTheme) }; item { ActionCard(c.account, if (c == AR) "الملف الشخصي والأمان والتفضيلات" else "Profil, sécurité et préférences", Icons.Default.Settings, teal, surface, text, muted) { }; }; item { ActionCard(c.about, "CarDiag • OBD-II • Vehicle Intelligence", Icons.Default.DirectionsCar, teal, surface, text, muted) { } } } }
