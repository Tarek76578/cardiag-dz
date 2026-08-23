package dz.cardiag.app

import android.app.LocaleManager
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import dz.cardiag.app.core.AuthService
import dz.cardiag.app.core.DiagnosticService
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.delay
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
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("search_text") val searchText: String? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CarDiagTheme { CarDiagApp(onLanguageChange = ::setAppLanguage) } }
    }

    private fun setAppLanguage(language: String) {
        val localeManager = getSystemService(LocaleManager::class.java)
        localeManager.applicationLocales = LocaleList.forLanguageTags(if (language == "ar") "ar" else "fr")
    }
}

@Composable
private fun CarDiagTheme(content: @Composable () -> Unit) {
    val colors = androidx.compose.material3.darkColorScheme(
        primary = Color(0xFF5EE7F2),
        onPrimary = Color(0xFF00262A),
        background = Color(0xFF071016),
        surface = Color(0xFF0D1820),
        surfaceVariant = Color(0xFF172631),
        onBackground = Color(0xFFE8F2F5),
        onSurface = Color(0xFFE8F2F5),
        onSurfaceVariant = Color(0xFFAABCC4)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun CarDiagApp(onLanguageChange: (String) -> Unit) {
    val auth = remember { AuthService() }
    var authenticated by remember { mutableStateOf(auth.currentUser != null) }
    var authError by remember { mutableStateOf<String?>(null) }
    var retryNonce by remember { mutableStateOf(0) }

    LaunchedEffect(retryNonce) {
        if (auth.currentUser != null) {
            authenticated = true
            authError = null
            return@LaunchedEffect
        }
        authenticated = false
        authError = null
        try {
            auth.signInAnonymously()
            authenticated = auth.currentUser != null
            if (!authenticated) error("Anonymous session was not created")
        } catch (e: Exception) {
            authError = e.message ?: "Anonymous sign-in failed"
        }
    }

    if (!authenticated) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("CarDiag", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("DZ • Smart vehicle diagnostics", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(28.dp))
                if (authError == null) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.preparing), Modifier.padding(top = 16.dp))
                } else {
                    Text(stringResource(R.string.start_failed), style = MaterialTheme.typography.headlineSmall)
                    Text(authError ?: "", Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.error)
                    Button(onClick = { retryNonce++ }) { Text(stringResource(R.string.retry)) }
                }
            }
        }
        return
    }

    val navController = rememberNavController()
    var selectedTab by remember { mutableStateOf("home") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CarDiag", fontWeight = FontWeight.Bold) },
                actions = {
                    Text(
                        "DZ",
                        modifier = Modifier.padding(end = 18.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    "home" to stringResource(R.string.nav_home),
                    "diagnostic" to stringResource(R.string.nav_diagnose),
                    "vehicles" to stringResource(R.string.nav_vehicles),
                    "settings" to stringResource(R.string.nav_settings)
                ).forEach { (route, label) ->
                    NavigationBarItem(
                        selected = selectedTab == route,
                        onClick = {
                            selectedTab = route
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(if (route == "home") "⌂" else if (route == "diagnostic") "⌁" else if (route == "vehicles") "▣" else "⚙") },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") { HomeScreen(onStartDiagnostic = { selectedTab = "diagnostic"; navController.navigate("diagnostic") }, onVehicles = { selectedTab = "vehicles"; navController.navigate("vehicles") }) }
            composable("diagnostic") { DiagnosticScreen() }
            composable("vehicles") { VehicleCatalogScreen(onStartDiagnostic = { selectedTab = "diagnostic"; navController.navigate("diagnostic") }) }
            composable("settings") { SettingsScreen(onLanguageChange) }
        }
    }
}

@Composable
private fun HomeScreen(onStartDiagnostic: () -> Unit, onVehicles: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.home_eyebrow), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.home_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = onStartDiagnostic,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text(stringResource(R.string.start_diagnostic), fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            Text(stringResource(R.string.quick_actions), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard("OBD", stringResource(R.string.obd_action), Modifier.weight(1f), onStartDiagnostic)
                QuickActionCard("DTC", stringResource(R.string.dtc_action), Modifier.weight(1f), onStartDiagnostic)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard("VIN", stringResource(R.string.vin_action), Modifier.weight(1f), onVehicles)
                QuickActionCard("AI", stringResource(R.string.ai_action), Modifier.weight(1f), onStartDiagnostic)
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.vehicle_garage), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.vehicle_garage_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = onVehicles) { Text(stringResource(R.string.open)) }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticScreen() {
    var problem by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<JsonObject?>(null) }
    var selectedVehicle by remember { mutableStateOf<VehicleModel?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val diagnosticService = remember { DiagnosticService() }
    val configuration = LocalConfiguration.current
    val language = if (configuration.locales[0].language == "ar") "ar" else "fr"
    val fillRequiredFieldsText = stringResource(R.string.fill_required_fields)
    val errorText = stringResource(R.string.diagnostic_failed)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.diagnostic_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.diagnostic_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.step_vehicle), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    VehiclePicker(selectedVehicle) { selectedVehicle = it }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.step_symptoms), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = problem,
                        onValueChange = { problem = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        enabled = !running,
                        label = { Text(stringResource(R.string.describe_problem)) },
                        placeholder = { Text(stringResource(R.string.problem_hint)) },
                        shape = RoundedCornerShape(16.dp)
                    )
                    Button(
                        onClick = {
                            if (selectedVehicle == null || problem.isBlank()) {
                                result = JsonObject(mapOf("summary" to kotlinx.serialization.json.JsonPrimitive(fillRequiredFieldsText)))
                                return@Button
                            }
                            scope.launch {
                                running = true
                                result = null
                                try {
                                    result = diagnosticService.runDiagnostic(selectedVehicle!!.id, problem.trim(), language)
                                } catch (e: Exception) {
                                    result = JsonObject(mapOf("summary" to kotlinx.serialization.json.JsonPrimitive(errorText)))
                                } finally { running = false }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !running,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.diagnose_with_ai), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (running) {
            item { Text(stringResource(R.string.ai_analyzing), color = MaterialTheme.colorScheme.primary) }
        }
        result?.let { diagnosis ->
            item { DiagnosisResultCard(diagnosis) }
        }
    }
}

@Composable
private fun DiagnosisResultCard(result: JsonObject) {
    val diagnosis = (result["diagnosis"] as? JsonObject) ?: result
    val summary = diagnosis["summary"]?.toString()?.trim('"') ?: stringResource(R.string.diagnostic_received)
    val severity = diagnosis["severity"]?.toString()?.trim('"') ?: "—"
    val confidence = diagnosis["confidence"]?.toString()?.trim('"') ?: "—"
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ai_result), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                FilterChip(selected = true, onClick = {}, label = { Text(severity) })
            }
            Text(summary, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricPill(stringResource(R.string.confidence), confidence)
                MetricPill(stringResource(R.string.safety), stringResource(R.string.safety_cautious))
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VehicleCatalogScreen(onStartDiagnostic: () -> Unit) {
    var selectedVehicle by remember { mutableStateOf<VehicleModel?>(null) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.vehicles), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.vehicle_catalog_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        VehiclePicker(selectedVehicle) { selectedVehicle = it }
        selectedVehicle?.let {
            Button(onClick = onStartDiagnostic, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.diagnose_this_vehicle))
            }
        }
    }
}

@Composable
fun VehiclePicker(selectedVehicle: VehicleModel?, onVehicleSelected: (VehicleModel?) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var vehicles by remember { mutableStateOf<List<VehicleModel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(searchQuery, selectedVehicle?.id) {
        val query = searchQuery.trim()
        if (selectedVehicle != null || query.isEmpty()) {
            vehicles = emptyList(); loading = false; error = null; return@LaunchedEffect
        }
        delay(250)
        loading = true; error = null
        try {
            vehicles = SupabaseClient.client.from("vehicle_models").select(
                columns = Columns.raw("id, make_id, name, year_from, year_to, generation, image_url, search_text")
            ) {
                filter { ilike("search_text", "%${query.lowercase()}%") }
                limit(20)
            }.decodeList<VehicleModel>()
        } catch (e: Exception) {
            vehicles = emptyList(); error = e.message ?: "Unknown error"
        } finally { loading = false }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (selectedVehicle == null) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.vehicle)) },
                placeholder = { Text(stringResource(R.string.vehicle_hint)) },
                shape = RoundedCornerShape(16.dp)
            )
        } else {
            SelectedVehicleCard(selectedVehicle) { searchQuery = ""; vehicles = emptyList(); onVehicleSelected(null) }
        }
        if (loading) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (searchQuery.isNotBlank() && !loading && error == null && selectedVehicle == null) {
            if (vehicles.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxWidth().height(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vehicles, key = { it.id }) { vehicle ->
                        VehicleResultItem(vehicle) { onVehicleSelected(vehicle); searchQuery = ""; vehicles = emptyList() }
                    }
                }
            } else Text(stringResource(R.string.no_vehicle_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VehicleResultItem(vehicle: VehicleModel, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            VehicleImage(vehicle, Modifier.size(82.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(vehicle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                vehicle.generation?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val years = when {
                    vehicle.yearFrom != null && vehicle.yearTo != null -> "${vehicle.yearFrom} – ${vehicle.yearTo}"
                    vehicle.yearFrom != null -> "${vehicle.yearFrom} –"
                    vehicle.yearTo != null -> "– ${vehicle.yearTo}"
                    else -> ""
                }
                if (years.isNotEmpty()) Text(years, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SelectedVehicleCard(vehicle: VehicleModel, onChange: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            VehicleImage(vehicle, Modifier.size(90.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(vehicle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                vehicle.generation?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Button(onClick = onChange) { Text("×") }
        }
    }
}

@Composable
private fun VehicleImage(vehicle: VehicleModel, modifier: Modifier) {
    AsyncImage(
        model = vehicle.imageUrl?.takeIf { it.isNotBlank() },
        placeholder = painterResource(R.drawable.cardiag_car_fallback),
        error = painterResource(R.drawable.cardiag_car_fallback),
        contentDescription = vehicle.name,
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun SettingsScreen(onLanguageChange: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.language), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { onLanguageChange("fr") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.french)) }
        Button(onClick = { onLanguageChange("ar") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.arabic)) }
    }
}
