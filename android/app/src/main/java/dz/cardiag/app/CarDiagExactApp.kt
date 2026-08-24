package dz.cardiag.app

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val home: String,
    val diagnostic: String,
    val garage: String,
    val history: String,
    val more: String,
    val smart: String,
    val search: String,
    val catalog: String,
    val back: String
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

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            if (selected != null) {
                val vehicle = selected!!
                ExactVehicleProfileScreen(
                    UiModel(vehicle.id, vehicle.name, vehicle.imageUrl)
                ) { selected = null }
            } else {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val icons = listOf(
                                Icons.Default.Home,
                                Icons.Default.Build,
                                Icons.Default.Garage,
                                Icons.Default.History,
                                Icons.Default.Settings
                            )
                            val labels = listOf(
                                copy.home,
                                copy.diagnostic,
                                copy.garage,
                                copy.history,
                                copy.more
                            )
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
private fun Header(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    copy: Copy,
    onVehicle: (ExactVehicle) -> Unit
) {
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<ExactVehicle>>(emptyList()) }
    var makes by remember { mutableStateOf<List<ExactMake>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch {
            models = runCatching {
                SupabaseClient.client.from("vehicle_models")
                    .select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url"))
                    .decodeList<ExactVehicle>()
            }.getOrDefault(emptyList())
            makes = runCatching {
                SupabaseClient.client.from("vehicle_makes")
                    .select(Columns.list("id", "name"))
                    .decodeList<ExactMake>()
            }.getOrDefault(emptyList())
        }
    }

    val filtered = models.filter { model ->
        query.isBlank() ||
            model.name.contains(query, ignoreCase = true) ||
            makes.firstOrNull { it.id == model.makeId }?.name?.contains(query, ignoreCase = true) == true
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item { Header("CarDiag", copy.smart) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                singleLine = true,
                placeholder = { Text(copy.search) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
        }
        item {
            Text(copy.catalog, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
        items(filtered.take(50), key = { it.id }) { vehicle ->
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable { onVehicle(vehicle) }
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = vehicle.imageUrl,
                        contentDescription = vehicle.name,
                        modifier = Modifier.size(78.dp),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(makes.firstOrNull { it.id == vehicle.makeId }?.name ?: "Vehicle", color = MaterialTheme.colorScheme.primary)
                        Text(vehicle.name, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(vehicle.generation, vehicle.yearFrom?.toString(), vehicle.yearTo?.toString()).joinToString(" • "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticHub(padding: PaddingValues) {
    val context = LocalContext.current

    fun open(activity: Class<*>) {
        context.startActivity(Intent(context, activity))
    }

    Column(
        Modifier.fillMaxSize().padding(padding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header("Diagnostic", "SMART VEHICLE DIAGNOSTICS")
        ActionCard("OBD-II Scanner", Icons.Default.Build) { open(ObdScannerActivity::class.java) }
        ActionCard("Live Data", Icons.Default.Speed) { open(LiveDataProActivity::class.java) }
        ActionCard("DTC & Faults", Icons.Default.Warning) { open(GuidedDiagnosisActivity::class.java) }
        ActionCard("VIN Identity", Icons.Default.DirectionsCar) { open(ObdScannerActivity::class.java) }
        ActionCard("AI Diagnosis • Sans OBD", Icons.Default.Build) { open(AiSymptomDiagnosisActivity::class.java) }
    }
}

@Composable
private fun ActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun GarageScreen(
    padding: PaddingValues,
    copy: Copy,
    onVehicle: (ExactVehicle) -> Unit
) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<GarageVehicle>>(emptyList()) }
    var models by remember { mutableStateOf<List<ExactVehicle>>(emptyList()) }
    var makes by remember { mutableStateOf<List<ExactMake>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var add by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            val user = AuthService().currentUser
            if (user == null) {
                error = if (copy == AR) "سجّل الدخول لإدارة سياراتك" else "Connectez-vous pour gérer votre garage"
                loading = false
                return@launch
            }
            rows = runCatching {
                SupabaseClient.client.from("user_vehicles")
                    .select(Columns.list("id", "model_id", "make_id", "nickname", "vin", "mileage", "year", "is_primary"))
                    .decodeList<GarageVehicle>()
            }.getOrElse {
                error = it.message ?: "Garage unavailable"
                emptyList()
            }
            models = runCatching {
                SupabaseClient.client.from("vehicle_models")
                    .select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url"))
                    .decodeList<ExactVehicle>()
            }.getOrDefault(emptyList())
            makes = runCatching {
                SupabaseClient.client.from("vehicle_makes")
                    .select(Columns.list("id", "name"))
                    .decodeList<ExactMake>()
            }.getOrDefault(emptyList())
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        Modifier.fillMaxSize().padding(padding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header(copy.garage, copy.smart)
        Button(onClick = { add = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (copy == AR) "إضافة سيارة" else "Ajouter un véhicule")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(if (copy == AR) "المرآب فارغ" else "Votre garage est vide")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows, key = { it.id }) { row ->
                    val model = models.firstOrNull { it.id == row.modelId }
                    Card(Modifier.fillMaxWidth().clickable { model?.let(onVehicle) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = model?.imageUrl,
                                contentDescription = model?.name,
                                modifier = Modifier.size(76.dp),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(row.nickname ?: model?.name ?: "Vehicle", fontWeight = FontWeight.Bold)
                                Text(makes.firstOrNull { it.id == row.makeId }?.name ?: (model?.name ?: ""))
                                row.vin?.takeIf { it.isNotBlank() }?.let {
                                    Text("VIN • $it", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (add) {
        AlertDialog(
            onDismissRequest = { add = false },
            title = { Text(if (copy == AR) "اختر سيارة" else "Choisir un véhicule") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(models.take(50), key = { it.id }) { model ->
                        Text(
                            model.name,
                            Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    val userId = AuthService().currentUser?.id
                                    if (userId == null) {
                                        error = if (copy == AR) "يجب تسجيل الدخول أولًا" else "Connectez-vous d'abord"
                                        add = false
                                    } else {
                                        runCatching {
                                            SupabaseClient.client.from("user_vehicles").insert(
                                                GarageVehicleInsert(
                                                    userId = userId,
                                                    makeId = model.makeId,
                                                    modelId = model.id,
                                                    nickname = model.name,
                                                    isPrimary = rows.isEmpty()
                                                )
                                            )
                                        }.onFailure { error = it.message ?: "Impossible d'ajouter le véhicule" }
                                        add = false
                                        load()
                                    }
                                }
                            }.padding(14.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { add = false }) { Text(copy.back) }
            }
        )
    }
}

@Composable
private fun HistoryScreen(padding: PaddingValues, copy: Copy) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Header(copy.history, copy.smart)
        ActionCard(
            if (copy == AR) "لا توجد تشخيصات محفوظة" else "Aucun diagnostic enregistré",
            Icons.Default.History
        ) { }
    }
}

@Composable
private fun MoreScreen(
    padding: PaddingValues,
    copy: Copy,
    dark: Boolean,
    onTheme: () -> Unit,
    onLanguage: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header(copy.more, copy.smart)
        ActionCard(
            if (dark) {
                if (copy == AR) "الوضع الداكن" else "Mode sombre"
            } else {
                if (copy == AR) "الوضع الفاتح" else "Mode clair"
            },
            Icons.Default.Brightness6,
            onTheme
        )
        ActionCard(
            if (copy == AR) "تغيير اللغة" else "Changer la langue",
            Icons.Default.Language,
            onLanguage
        )
    }
}
