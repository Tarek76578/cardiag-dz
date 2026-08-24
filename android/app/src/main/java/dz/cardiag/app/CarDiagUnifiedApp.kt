package dz.cardiag.app

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class Make(val id: String, val name: String)

@Serializable
private data class Model(
    val id: String,
    @SerialName("make_id") val makeId: String,
    val name: String,
    @SerialName("year_from") val yearFrom: Int? = null,
    @SerialName("year_to") val yearTo: Int? = null
)

private enum class Tab { HOME, CARS, DIAGNOSTIC, GARAGE, MENU }

@Composable
fun CarDiagUnifiedApp() {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var selected by remember { mutableStateOf<Model?>(null) }

    if (selected != null) {
        VehicleDetails(selected!!, onBack = { selected = null })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val labels = listOf("Home", "Cars", "Diagnostic", "Garage", "Menu")
                val icons = listOf(Icons.Default.Home, Icons.Default.DirectionsCar, Icons.Default.Build, Icons.Default.Garage, Icons.Default.MoreHoriz)
                Tab.values().forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(icons[index], labels[index]) },
                        label = { Text(labels[index]) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            Tab.HOME -> HomeScreen(padding, onCars = { tab = Tab.CARS })
            Tab.CARS -> CarsScreen(padding, onSelect = { selected = it })
            Tab.DIAGNOSTIC -> DiagnosticScreen(padding)
            Tab.GARAGE -> GarageScreen(padding, onCars = { tab = Tab.CARS })
            Tab.MENU -> MenuScreen(padding)
        }
    }
}

@Composable
private fun HomeScreen(padding: PaddingValues, onCars: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("CarDiag", style = MaterialTheme.typography.headlineLarge)
            Text("SMART VEHICLE DIAGNOSTICS", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }
        item { ActionCard("Vehicle Catalog", "Make → Model → Year → Engine", Icons.Default.DirectionsCar, onCars) }
        item { ActionCard("OBD-II Scanner", "ECU, DTC, VIN and Live Data", Icons.Default.Build) { context.startActivity(Intent(context, ObdScannerActivity::class.java)) } }
        item { ActionCard("AI Diagnosis", "Analyse symptoms with AI", Icons.Default.Warning) { context.startActivity(Intent(context, AiSymptomDiagnosisActivity::class.java)) } }
    }
}

@Composable
private fun CarsScreen(padding: PaddingValues, onSelect: (Model) -> Unit) {
    var models by remember { mutableStateOf<List<Model>>(emptyList()) }
    var makes by remember { mutableStateOf<List<Make>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        models = runCatching {
            SupabaseClient.client.from("vehicle_models")
                .select(Columns.list("id", "make_id", "name", "year_from", "year_to"))
                .decodeList<Model>()
        }.getOrDefault(emptyList())
        makes = runCatching {
            SupabaseClient.client.from("vehicle_makes")
                .select(Columns.list("id", "name"))
                .decodeList<Make>()
        }.getOrDefault(emptyList())
    }

    val filtered = models.filter { model ->
        val make = makes.firstOrNull { it.id == model.makeId }?.name.orEmpty()
        query.isBlank() || model.name.contains(query, true) || make.contains(query, true)
    }

    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Vehicle Catalog", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search make or model") },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
        }
        items(filtered.take(200), key = { it.id }) { model ->
            val make = makes.firstOrNull { it.id == model.makeId }?.name ?: "Vehicle"
            ActionCard(
                title = "$make ${model.name}",
                subtitle = listOfNotNull(model.yearFrom, model.yearTo).joinToString(" – "),
                icon = Icons.Default.DirectionsCar,
                onClick = { onSelect(model) }
            )
        }
    }
}

@Composable
private fun VehicleDetails(model: Model, onBack: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Text("‹") }
                Column(Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.headlineMedium)
                    Text("${model.yearFrom ?: "—"} – ${model.yearTo ?: "—"}")
                }
            }
        }
        item { ActionCard("OBD-II Scanner", "Scan ECU and read DTCs", Icons.Default.Build) { context.startActivity(Intent(context, ObdScannerActivity::class.java)) } }
        item { ActionCard("Live Data", "Real-time sensor values", Icons.Default.Build) { context.startActivity(Intent(context, LiveDataProActivity::class.java)) } }
        item { ActionCard("DTC & Faults", "Fault codes and guided diagnosis", Icons.Default.Warning) { context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java)) } }
        item { ActionCard("AI Diagnosis", "Analyse symptoms", Icons.Default.Warning) { context.startActivity(Intent(context, AiSymptomDiagnosisActivity::class.java)) } }
    }
}

@Composable
private fun DiagnosticScreen(padding: PaddingValues) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Diagnostic", style = MaterialTheme.typography.headlineMedium) }
        item { ActionCard("OBD-II Scanner", "Connect to the vehicle", Icons.Default.Build) { context.startActivity(Intent(context, ObdScannerActivity::class.java)) } }
        item { ActionCard("Live Data", "Read sensor values", Icons.Default.Build) { context.startActivity(Intent(context, LiveDataProActivity::class.java)) } }
        item { ActionCard("DTC & Faults", "Read and analyse trouble codes", Icons.Default.Warning) { context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java)) } }
        item { ActionCard("VIN Identity", "Identify the vehicle", Icons.Default.DirectionsCar) { context.startActivity(Intent(context, ObdScannerActivity::class.java)) } }
    }
}

@Composable
private fun GarageScreen(padding: PaddingValues, onCars: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Garage", style = MaterialTheme.typography.headlineMedium) }
        item { ActionCard("Add Vehicle", "Choose a vehicle from the catalog", Icons.Default.DirectionsCar, onCars) }
        item { ActionCard("Diagnostic History", "Previous sessions and results", Icons.Default.Build) {} }
    }
}

@Composable
private fun MenuScreen(padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Menu", style = MaterialTheme.typography.headlineMedium) }
        item { Text("CarDiag • Algeria", style = MaterialTheme.typography.bodyLarge) }
        item { Text("Arabic and French support is available in the app settings.") }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
