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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
        setContent { MaterialTheme { CarDiagApp(onLanguageChange = ::setAppLanguage) } }
    }
    private fun setAppLanguage(language: String) {
        val localeManager = getSystemService(LocaleManager::class.java)
        localeManager.applicationLocales = LocaleList.forLanguageTags(if (language == "ar") "ar" else "fr")
    }
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
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (authError == null) {
                CircularProgressIndicator()
                Text("Preparing CarDiag…", Modifier.padding(top = 16.dp))
            } else {
                Text("Unable to start CarDiag", style = MaterialTheme.typography.headlineSmall)
                Text(authError ?: "", Modifier.padding(vertical = 12.dp))
                Button(onClick = { retryNonce++ }) { Text("Retry") }
            }
        }
        return
    }

    val navController = rememberNavController()
    Scaffold { padding ->
        NavHost(navController = navController, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen({ navController.navigate("diagnostic") }, { navController.navigate("settings") }) }
            composable("diagnostic") { DiagnosticScreen { navController.popBackStack() } }
            composable("settings") { SettingsScreen({ navController.popBackStack() }, onLanguageChange) }
        }
    }
}

@Composable
fun HomeScreen(onStartDiagnostic: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.titleMedium)
        Button(onClick = onStartDiagnostic, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_diagnostic)) }
        Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings)) }
    }
}

@Composable
fun DiagnosticScreen(onBack: () -> Unit) {
    var problem by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var selectedVehicle by remember { mutableStateOf<VehicleModel?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val diagnosticService = remember { DiagnosticService() }
    val configuration = LocalConfiguration.current
    val language = if (configuration.locales[0].language == "ar") "ar" else "fr"
    val fillRequiredFieldsText = stringResource(R.string.fill_required_fields)
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.diagnostic_title), style = MaterialTheme.typography.headlineMedium)
        VehiclePicker(selectedVehicle) { selectedVehicle = it }
        OutlinedTextField(problem, { problem = it }, Modifier.fillMaxWidth(), minLines = 4,
            label = { Text(stringResource(R.string.describe_problem)) }, placeholder = { Text(stringResource(R.string.problem_hint)) }, enabled = !running)
        Button(onClick = {
            if (selectedVehicle == null || problem.isBlank()) { result = fillRequiredFieldsText; return@Button }
            scope.launch {
                running = true; result = ""
                try {
                    result = diagnosticService.runDiagnostic(selectedVehicle!!.id, problem.trim(), language).toString()
                } catch (e: Exception) { result = e.message ?: "Diagnostic request failed" }
                finally { running = false }
            }
        }, modifier = Modifier.fillMaxWidth(), enabled = !running) {
            if (running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.diagnose_with_ai))
        }
        if (result.isNotEmpty()) Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text(result, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
        Button(onClick = onBack, Modifier.fillMaxWidth(), enabled = !running) { Text(stringResource(R.string.back)) }
    }
}

@Composable
fun VehiclePicker(selectedVehicle: VehicleModel?, onVehicleSelected: (VehicleModel) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var vehicles by remember { mutableStateOf<List<VehicleModel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(searchQuery, selectedVehicle?.id) {
        val query = searchQuery.trim()
        if (!selectedVehicle?.id.isNullOrEmpty() || query.isEmpty()) { vehicles = emptyList(); loading = false; error = null; return@LaunchedEffect }
        delay(300); loading = true; error = null
        try {
            vehicles = SupabaseClient.client.from("vehicle_models").select(columns = Columns.raw("id, make_id, name, year_from, year_to, generation, image_url, search_text")) {
                filter { ilike("search_text", "%${query.lowercase()}%") }; limit(20)
            }.decodeList<VehicleModel>()
        } catch (e: Exception) { vehicles = emptyList(); error = e.message ?: "Unknown error" }
        finally { loading = false }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.vehicle), style = MaterialTheme.typography.titleMedium)
        if (selectedVehicle == null || selectedVehicle.id.isEmpty()) OutlinedTextField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.vehicle)) }, placeholder = { Text(stringResource(R.string.vehicle_hint)) })
        if (selectedVehicle != null && selectedVehicle.id.isNotEmpty()) SelectedVehicleCard(selectedVehicle) {
            searchQuery = ""; vehicles = emptyList(); error = null; onVehicleSelected(VehicleModel("", "", ""))
        }
        if (loading) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        if (error != null) Text(error ?: "", style = MaterialTheme.typography.bodySmall)
        if (searchQuery.isNotBlank() && !loading && error == null && selectedVehicle?.id.isNullOrEmpty()) {
            if (vehicles.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().height(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vehicles, key = { it.id }) { vehicle -> VehicleResultItem(vehicle) { onVehicleSelected(vehicle); searchQuery = ""; vehicles = emptyList() } }
            } else Text(stringResource(R.string.no_vehicle_found), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun VehicleResultItem(vehicle: VehicleModel, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = vehicle.imageUrl, contentDescription = vehicle.name, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(vehicle.name, style = MaterialTheme.typography.titleMedium)
                if (!vehicle.generation.isNullOrBlank()) Text(vehicle.generation, style = MaterialTheme.typography.bodySmall)
                val years = when { vehicle.yearFrom != null && vehicle.yearTo != null -> "${vehicle.yearFrom} – ${vehicle.yearTo}"; vehicle.yearFrom != null -> "${vehicle.yearFrom} –"; vehicle.yearTo != null -> "– ${vehicle.yearTo}"; else -> "" }
                if (years.isNotEmpty()) Text(years, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SelectedVehicleCard(vehicle: VehicleModel, onChange: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = vehicle.imageUrl, contentDescription = vehicle.name, modifier = Modifier.size(80.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(vehicle.name, style = MaterialTheme.typography.titleMedium); if (!vehicle.generation.isNullOrBlank()) Text(vehicle.generation, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = onChange) { Text("✕") }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onLanguageChange: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.language))
        Button(onClick = { onLanguageChange("fr") }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.french)) }
        Button(onClick = { onLanguageChange("ar") }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.arabic)) }
        Button(onClick = onBack, Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
    }
}
