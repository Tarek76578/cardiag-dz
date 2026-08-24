package dz.cardiag.app

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dz.cardiag.app.core.AuthService
import dz.cardiag.app.core.SupabaseClient
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

@Serializable
data class GarageVehicle(
    val id: String,
    @SerialName("model_id") val modelId: String,
    @SerialName("make_id") val makeId: String,
    val nickname: String? = null,
    val vin: String? = null,
    val mileage: Int? = null,
    val year: Int? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false
)

@Serializable
data class GarageVehicleInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("make_id") val makeId: String,
    @SerialName("model_id") val modelId: String,
    val nickname: String? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false
)

data class Copy(
    val home: String, val diagnostic: String, val garage: String, val history: String,
    val more: String, val smart: String, val search: String, val catalog: String, val back: String
)

private val FR = Copy("Accueil", "Diagnostic", "Garage", "Historique", "Plus", "SMART VEHICLE DIAGNOSTICS", "Rechercher une marque ou un modèle", "Catalogue véhicules", "Retour")
private val AR = Copy("الرئيسية", "التشخيص", "المرآب", "السجل", "المزيد", "تشخيص السيارات الذكي", "ابحث عن الماركة أو الموديل", "كتالوج السيارات", "رجوع")

@Composable
fun CarDiagExactApp() {
    var dark by remember { mutableStateOf(true) }
    var arabic by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ExactVehicle?>(null) }
    val copy = if (arabic) AR else FR

    val scheme = if (dark) {
        darkColorScheme(primary = Color(0xFF64B5FF), secondary = Color(0xFF55D6BE), surface = Color(0xFF10141B), background = Color(0xFF080B10))
    } else {
        lightColorScheme(primary = Color(0xFF1769AA), secondary = Color(0xFF087F6B), surface = Color(0xFFF8FAFC), background = Color(0xFFF3F6FA))
    }

    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            if (selected != null) {
                val vehicle = selected!!
                ExactVehicleProfileScreen(UiModel(vehicle.id, vehicle.name, vehicle.imageUrl)) { selected = null }
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            val icons = listOf(Icons.Default.Home, Icons.Default.Build, Icons.Default.Garage, Icons.Default.History, Icons.Default.Settings)
                            val labels = listOf(copy.home, copy.diagnostic, copy.garage, copy.history, copy.more)
                            labels.forEachIndexed { index, label ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    icon = { Icon(icons[index], contentDescription = label) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    when (tab) {
                        0 -> HomeScreen(padding, copy) { selected = it }
                        1 -> DiagnosticHub(padding)
                        2 -> GarageScreen(padding, copy) { selected = it }
                        3 -> HistoryScreen(padding, copy)
                        else -> MoreScreen(padding, copy, dark, { dark = !dark }, { arabic = !arabic })
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
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<ExactVehicle>>(emptyList()) }
    var makes by remember { mutableStateOf<List<ExactMake>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch {
            models = runCatching { SupabaseClient.client.from("vehicle_models").select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")).decodeList<ExactVehicle>() }.getOrDefault(emptyList())
            makes = runCatching { SupabaseClient.client.from("vehicle_makes").select(Columns.list("id", "name")).decodeList<ExactMake>() }.getOrDefault(emptyList())
        }
    }

    val filtered = models.filter { model ->
        query.isBlank() || model.name.contains(query, true) || makes.firstOrNull { it.id == model.makeId }?.name?.contains(query, true) == true
    }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))) {
                Column(Modifier.padding(24.dp)) {
                    Text("CarDiag", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(copy.smart, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(18.dp))
                    Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .16f)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
                            Spacer(Modifier.width(10.dp))
                            Text(if (copy == AR) "تشخيص ذكي • OBD + AI" else "Diagnostic intelligent • OBD + AI", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true, shape = RoundedCornerShape(18.dp), placeholder = { Text(copy.search) }, leadingIcon = { Icon(Icons.Default.Search, null) })
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(copy.catalog, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, Modifier.weight(1f))
                Text("${filtered.size}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        items(filtered.take(50), key = { it.id }) { vehicle ->
            VehicleListCard(vehicle, makes.firstOrNull { it.id == vehicle.makeId }?.name ?: "Vehicle", onVehicle)
        }
    }
}

@Composable
private fun VehicleListCard(vehicle: ExactVehicle, make: String, onVehicle: (ExactVehicle) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable { onVehicle(vehicle) }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(vehicle.imageUrl, vehicle.name, Modifier.size(88.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(make, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(vehicle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(listOfNotNull(vehicle.generation, vehicle.yearFrom?.toString(), vehicle.yearTo?.toString()).joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiagnosticHub(padding: PaddingValues) {
    val context = LocalContext.current
    fun open(activity: Class<*>) { context.startActivity(Intent(context, activity)) }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("Diagnostic", "SMART VEHICLE DIAGNOSTICS") }
        item { DiagnosticHero() }
        item { ActionCard("OBD-II Scanner", "Connexion et lecture ECU", Icons.Default.BluetoothConnected) { open(ObdScannerActivity::class.java) } }
        item { ActionCard("Live Data", "Capteurs et paramètres en temps réel", Icons.Default.Speed) { open(LiveDataProActivity::class.java) } }
        item { ActionCard("DTC & Faults", "Codes défauts, causes et réparation", Icons.Default.Warning) { open(GuidedDiagnosisActivity::class.java) } }
        item { ActionCard("VIN Identity", "Identifier précisément le véhicule", Icons.Default.DirectionsCar) { open(ObdScannerActivity::class.java) } }
        item { ActionCard("AI Diagnosis • Sans OBD", "Analyse des symptômes sans scanner", Icons.Default.AutoAwesome) { open(AiSymptomDiagnosisActivity::class.java) } }
    }
}

@Composable
private fun DiagnosticHero() {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(58.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .14f)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Diagnostic intelligent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text("OBD quand il est disponible. AI quand vous avez seulement les symptômes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(52.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GarageScreen(padding: PaddingValues, copy: Copy, onVehicle: (ExactVehicle) -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<GarageVehicle>>(emptyList()) }
    var models by remember { mutableStateOf<List<ExactVehicle>>(emptyList()) }
    var makes by remember { mutableStateOf<List<ExactMake>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var add by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true; error = null
            val user = AuthService().currentUser
            if (user == null) { error = if (copy == AR) "سجّل الدخول لإدارة سياراتك" else "Connectez-vous pour gérer votre garage"; loading = false; return@launch }
            rows = runCatching { SupabaseClient.client.from("user_vehicles").select(Columns.list("id", "model_id", "make_id", "nickname", "vin", "mileage", "year", "is_primary")).decodeList<GarageVehicle>() }.getOrElse { error = it.message ?: "Garage unavailable"; emptyList() }
            models = runCatching { SupabaseClient.client.from("vehicle_models").select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")).decodeList<ExactVehicle>() }.getOrDefault(emptyList())
            makes = runCatching { SupabaseClient.client.from("vehicle_makes").select(Columns.list("id", "name")).decodeList<ExactMake>() }.getOrDefault(emptyList())
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(padding)) {
        PageHeader(copy.garage, if (copy == AR) "سياراتك وملفاتها" else "Vos véhicules et leurs fiches")
        Button(onClick = { add = true }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(if (copy == AR) "إضافة سيارة" else "Ajouter un véhicule", fontWeight = FontWeight.Bold)
        }
        error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (rows.isEmpty()) EmptyGarage(copy)
        else LazyColumn(contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(rows, key = { it.id }) { row ->
                val model = models.firstOrNull { it.id == row.modelId }
                Card(Modifier.fillMaxWidth().clickable { model?.let(onVehicle) }, shape = RoundedCornerShape(24.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model?.imageUrl, model?.name, Modifier.size(92.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            if (row.isPrimary) Text("PRIMARY VEHICLE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                            Text(row.nickname ?: model?.name ?: "Vehicle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            Text(makes.firstOrNull { it.id == row.makeId }?.name ?: (model?.name ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            row.vin?.takeIf { it.isNotBlank() }?.let { Text("VIN • $it", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    }

    if (add) {
        AlertDialog(onDismissRequest = { add = false }, title = { Text(if (copy == AR) "اختر سيارة" else "Choisir un véhicule") }, text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(models.take(80), key = { it.id }) { model ->
                    Text(model.name, Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            val userId = AuthService().currentUser?.id
                            if (userId == null) { error = if (copy == AR) "يجب تسجيل الدخول أولًا" else "Connectez-vous d'abord"; add = false }
                            else {
                                runCatching { SupabaseClient.client.from("user_vehicles").insert(GarageVehicleInsert(userId, model.makeId, model.id, model.name, rows.isEmpty())) }.onFailure { error = it.message ?: "Impossible d'ajouter le véhicule" }
                                add = false; load()
                            }
                        }
                    }.padding(14.dp))
                }
            }
        }, confirmButton = { TextButton({ add = false }) { Text(copy.back) } })
    }
}

@Composable
private fun EmptyGarage(copy: Copy) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(Modifier.size(84.dp), RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Garage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) } }
        Spacer(Modifier.height(18.dp))
        Text(if (copy == AR) "المرآب فارغ" else "Votre garage est vide", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(if (copy == AR) "أضف سيارتك للوصول السريع إلى التشخيص والخصائص." else "Ajoutez votre véhicule pour accéder rapidement au diagnostic et à ses caractéristiques.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryScreen(padding: PaddingValues, copy: Copy) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        PageHeader(copy.history, if (copy == AR) "كل عمليات التشخيص السابقة" else "Tous vos diagnostics précédents")
        Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(22.dp)) {
                Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(if (copy == AR) "لا توجد تشخيصات محفوظة" else "Aucun diagnostic enregistré", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (copy == AR) "ستظهر هنا نتائج AI وDTC المحفوظة." else "Les résultats AI et DTC enregistrés apparaîtront ici.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MoreScreen(padding: PaddingValues, copy: Copy, dark: Boolean, onTheme: () -> Unit, onLanguage: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        PageHeader(copy.more, if (copy == AR) "إعدادات CarDiag" else "Paramètres CarDiag")
        ActionCard(if (dark) { if (copy == AR) "الوضع الداكن" else "Mode sombre" } else { if (copy == AR) "الوضع الفاتح" else "Mode clair" }, if (copy == AR) "تغيير مظهر التطبيق" else "Modifier l'apparence", Icons.Default.Brightness6, onTheme)
        ActionCard(if (copy == AR) "تغيير اللغة" else "Changer la langue", if (copy == AR) "العربية / Français" else "Français / العربية", Icons.Default.Language, onLanguage)
    }
}
