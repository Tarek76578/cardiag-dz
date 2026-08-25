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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dz.cardiag.app.core.AuthService
import dz.cardiag.app.core.SupabaseClient
import dz.cardiag.app.ui.theme.CarDiagTheme
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
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

private data class Copy(
    val home: String, val diagnostic: String, val garage: String,
    val history: String, val more: String, val smart: String,
    val search: String, val catalog: String, val settings: String
)

private val FR = Copy("Accueil", "Diagnostic", "Garage", "Historique", "Plus", "SMART VEHICLE DIAGNOSTICS", "Rechercher une marque ou un modèle", "Catalogue véhicules", "Paramètres")
private val AR = Copy("الرئيسية", "التشخيص", "المرآب", "السجل", "المزيد", "تشخيص السيارات الذكي", "ابحث عن الماركة أو الموديل", "كتالوج السيارات", "الإعدادات")

private const val PREFS = "cardiag_ui"
private const val KEY_DARK = "dark"
private const val KEY_ARABIC = "arabic"

@Composable
fun CarDiagExactApp() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var dark by remember { mutableStateOf(prefs.getBoolean(KEY_DARK, true)) }
    var arabic by remember { mutableStateOf(prefs.getBoolean(KEY_ARABIC, false)) }
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ExactVehicle?>(null) }

    fun setDark(value: Boolean) {
        dark = value
        prefs.edit().putBoolean(KEY_DARK, value).apply()
    }
    fun setArabic(value: Boolean) {
        arabic = value
        prefs.edit().putBoolean(KEY_ARABIC, value).apply()
    }

    val copy = if (arabic) AR else FR
    CarDiagTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            if (selected != null) {
                ExactVehicleProfileScreen(UiModel(selected!!.id, selected!!.name, selected!!.imageUrl)) { selected = null }
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            val icons = listOf(Icons.Default.Home, Icons.Default.Build, Icons.Default.Garage, Icons.Default.History, Icons.Default.MoreHoriz)
                            val labels = listOf(copy.home, copy.diagnostic, copy.garage, copy.history, copy.more)
                            labels.forEachIndexed { index, label ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    icon = { Icon(imageVector = icons[index], contentDescription = label) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    when (tab) {
                        0 -> HomeScreen(padding, copy) { selected = it }
                        1 -> DiagnosticHub(padding, arabic)
                        2 -> GarageScreen(padding, copy) { selected = it }
                        3 -> HistoryScreen(padding, copy, arabic)
                        else -> MoreScreen(padding, copy, dark, arabic, ::setDark, ::setArabic)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeScreen(padding: PaddingValues, copy: Copy, onVehicle: (ExactVehicle) -> Unit) {
    var models by remember { mutableStateOf<List<ExactVehicle>>(emptyList()) }
    var makes by remember { mutableStateOf<List<ExactMake>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun reload() {
        scope.launch {
            loading = true
            error = null
            val result = runCatching {
                val m = SupabaseClient.client.from("vehicle_models").select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")).decodeList<ExactVehicle>()
                val mk = SupabaseClient.client.from("vehicle_makes").select(Columns.list("id", "name")).decodeList<ExactMake>()
                m to mk
            }
            result.onSuccess { (m, mk) -> models = m; makes = mk }.onFailure { error = it.message ?: "Supabase unavailable" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }
    val filtered = models.filter { v -> query.isBlank() || v.name.contains(query, true) || makes.firstOrNull { it.id == v.makeId }?.name?.contains(query, true) == true }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("CarDiag", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = Color.White)
                    Text(copy.smart, color = Color.White.copy(alpha = .9f))
                    Text(if (copy == AR) "OBD • VIN • DTC • Live Data • AI" else "OBD • VIN • DTC • Live Data • AI", color = Color.White.copy(alpha = .9f))
                    Button(
                        onClick = { context.startActivity(Intent(context, AiSymptomDiagnosisActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (copy == AR) "تشخيص بالذكاء الاصطناعي" else "Diagnostic avec AI", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        item {
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, shape = RoundedCornerShape(18.dp), placeholder = { Text(copy.search) }, leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) })
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(copy.catalog, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${filtered.size}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                IconButton(onClick = ::reload) { Icon(imageVector = Icons.Default.Refresh, contentDescription = if (copy == AR) "تحديث" else "Actualiser") }
            }
        }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        error?.let { message -> item { Text(message, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error) } }
        if (!loading && filtered.isEmpty() && error == null) item { EmptyState(if (copy == AR) "لا توجد سيارات" else "Aucun véhicule") }
        items(filtered.take(100), key = { it.id }) { vehicle ->
            VehicleListCard(vehicle, makes.firstOrNull { it.id == vehicle.makeId }?.name ?: "Vehicle", onVehicle)
        }
    }
}

@Composable
private fun VehicleListCard(vehicle: ExactVehicle, make: String, onVehicle: (ExactVehicle) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onVehicle(vehicle) }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = vehicle.imageUrl, contentDescription = vehicle.name, modifier = Modifier.size(88.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(make, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(vehicle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(listOfNotNull(vehicle.generation, vehicle.yearFrom?.toString(), vehicle.yearTo?.toString()).joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiagnosticHub(padding: PaddingValues, arabic: Boolean) {
    val context = LocalContext.current
    fun open(activity: Class<*>) { context.startActivity(Intent(context, activity)) }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader(if (arabic) "التشخيص" else "Diagnostic", "SMART VEHICLE DIAGNOSTICS") }
        item { DiagnosticHero(arabic) }
        item { ActionCard(if (arabic) "ماسح OBD-II" else "OBD-II Scanner", if (arabic) "اتصال وقراءة وحدات ECU" else "Connexion et lecture ECU", Icons.Default.BluetoothConnected) { open(ObdScannerActivity::class.java) } }
        item { ActionCard("Live Data", if (arabic) "الحساسات والقيم في الوقت الحقيقي" else "Capteurs et paramètres en temps réel", Icons.Default.Speed) { open(LiveDataProActivity::class.java) } }
        item { ActionCard(if (arabic) "الأعطال DTC" else "DTC & Faults", if (arabic) "الأكواد والأسباب وخطوات الإصلاح" else "Codes, causes et réparation", Icons.Default.Warning) { open(GuidedDiagnosisActivity::class.java) } }
        item { ActionCard(if (arabic) "هوية VIN" else "VIN Identity", if (arabic) "التعرف الدقيق على السيارة" else "Identifier précisément le véhicule", Icons.Default.DirectionsCar) { open(ObdScannerActivity::class.java) } }
        item { ActionCard(if (arabic) "تشخيص AI بدون OBD" else "AI Diagnosis • Sans OBD", if (arabic) "تحليل الأعراض بدون جهاز فحص" else "Analyse des symptômes sans scanner", Icons.Default.AutoAwesome) { open(AiSymptomDiagnosisActivity::class.java) } }
    }
}

@Composable private fun DiagnosticHero(arabic: Boolean) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(58.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .14f)) { Box(contentAlignment = Alignment.Center) { Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary) } }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(if (arabic) "مركز التشخيص" else "Centre de diagnostic", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(if (arabic) "OBD عند توفره، وAI عندما تملك الأعراض فقط." else "OBD quand il est disponible, AI quand vous avez seulement les symptômes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun ActionCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(52.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) { Box(contentAlignment = Alignment.Center) { Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) } }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GarageScreen(padding: PaddingValues, copy: Copy, onVehicle: (ExactVehicle) -> Unit) {
    var models by remember { mutableStateOf<List<ExactVehicle>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var addDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val user = AuthService().currentUser
    LaunchedEffect(Unit) {
        models = runCatching { SupabaseClient.client.from("vehicle_models").select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")).decodeList<ExactVehicle>() }.getOrDefault(emptyList())
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        PageHeader(copy.garage, if (copy == AR) "سياراتك وملفاتها" else "Vos véhicules et leurs fiches")
        Button(onClick = { addDialog = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp)) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (copy == AR) "إضافة سيارة" else "Ajouter un véhicule", fontWeight = FontWeight.Bold)
        }
        if (user == null) {
            EmptyState(if (copy == AR) "سجّل الدخول لإدارة سياراتك. يمكنك تصفح الكتالوج الآن." else "Connectez-vous pour gérer votre garage. Le catalogue reste disponible.")
        } else if (models.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(16.dp), singleLine = true, placeholder = { Text(if (copy == AR) "اختر سيارة" else "Choisir un véhicule") }, leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) })
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(models.filter { it.name.contains(query, true) }.take(50), key = { it.id }) { vehicle ->
                    VehicleListCard(vehicle, vehicle.makeId, onVehicle)
                }
            }
        }
    }
    if (addDialog) {
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text(if (copy == AR) "اختر سيارتك" else "Choisissez votre véhicule") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(models.take(50), key = { it.id }) { vehicle ->
                        ListItem(headlineContent = { Text(vehicle.name, fontWeight = FontWeight.Bold) }, supportingContent = { Text("ID: ${vehicle.id.take(8)}") }, modifier = Modifier.clickable { selectedId = vehicle.id; addDialog = false })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addDialog = false }) { Text(if (copy == AR) "إلغاء" else "Annuler") } }
        )
    }
    selectedId?.let { id -> models.firstOrNull { it.id == id }?.let { onVehicle(it); selectedId = null } }
}

@Composable
private fun HistoryScreen(padding: PaddingValues, copy: Copy, arabic: Boolean) {
    var loading by remember { mutableStateOf(true) }
    var count by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        loading = true
        count = runCatching { SupabaseClient.client.from("diagnostic_sessions").select(Columns.list("id")).decodeList<Map<String, String>>().size }.getOrDefault(0)
        loading = false
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        PageHeader(copy.history, if (arabic) "جلسات التشخيص السابقة" else "Vos sessions de diagnostic")
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (count == 0) EmptyState(if (arabic) "لا توجد جلسات تشخيص بعد" else "Aucune session de diagnostic")
        else {
            Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(22.dp)) {
                ListItem(leadingContent = { Icon(imageVector = Icons.Default.History, contentDescription = null) }, headlineContent = { Text(if (arabic) "$count جلسة تشخيص" else "$count sessions de diagnostic", fontWeight = FontWeight.Bold) }, supportingContent = { Text(if (arabic) "البيانات محفوظة في Supabase" else "Données enregistrées dans Supabase") })
            }
        }
    }
}

@Composable
private fun MoreScreen(padding: PaddingValues, copy: Copy, dark: Boolean, arabic: Boolean, setDark: (Boolean) -> Unit, setArabic: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        PageHeader(copy.more, if (arabic) "إعدادات CarDiag" else "Réglages CarDiag")
        SettingRow(if (arabic) "المظهر" else "Apparence", if (dark) "Dark" else "Light", Icons.Default.Brightness6) { setDark(!dark) }
        SettingRow(if (arabic) "اللغة" else "Langue", if (arabic) "العربية" else "Français", Icons.Default.Language) { setArabic(!arabic) }
        SettingRow(if (arabic) "حول CarDiag" else "À propos de CarDiag", "CarDiag", Icons.Default.Info) { }
        SettingRow(if (arabic) "الخصوصية" else "Confidentialité", if (arabic) "البيانات محمية عبر Supabase" else "Données protégées via Supabase", Icons.Default.Security) { }
    }
}

@Composable private fun SettingRow(title: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) {
        ListItem(leadingContent = { Icon(imageVector = icon, contentDescription = null) }, headlineContent = { Text(title, fontWeight = FontWeight.Bold) }, supportingContent = { Text(value) }, trailingContent = { Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null) })
    }
}

@Composable private fun EmptyState(text: String) { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
