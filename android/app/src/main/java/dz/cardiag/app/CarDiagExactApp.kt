package dz.cardiag.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

private val Bg = Color(0xFF06090B)
private val Surface = Color(0xFF0D1418)
private val Teal = Color(0xFF48D7C5)
private val TealSoft = Color(0xFF153F3B)
private val TextMain = Color(0xFFF5F8F8)
private val Muted = Color(0xFF8B9A9F)

@Composable
fun CarDiagExactApp() {
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ExactVehicle?>(null) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Teal,
            onPrimary = Bg,
            background = Bg,
            surface = Surface,
            onSurface = TextMain,
            onSurfaceVariant = Muted
        )
    ) {
        if (selected != null) {
            ExactVehicleProfileScreen(
                model = UiModel(selected!!.id, selected!!.name, selected!!.imageUrl),
                onBack = { selected = null }
            )
        } else {
            Scaffold(
                containerColor = Bg,
                bottomBar = {
                    NavigationBar(containerColor = Surface) {
                        val labels = listOf("Accueil", "Diagnostic", "Garage", "Historique", "Plus")
                        val icons = listOf(Icons.Default.Home, Icons.Default.Build, Icons.Default.Garage, Icons.Default.History, Icons.Default.Settings)
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(imageVector = icons[index], contentDescription = label) },
                                label = { Text(text = label) }
                            )
                        }
                    }
                }
            ) { padding ->
                when (tab) {
                    0 -> HomeScreen(padding) { selected = it }
                    1 -> DiagnosticScreen(padding)
                    2 -> GarageScreen(padding) { selected = it }
                    3 -> HistoryScreen(padding)
                    else -> MoreScreen(padding)
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, eyebrow: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(TealSoft),
            contentAlignment = Alignment.Center
        ) { Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, tint = Teal) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(text = eyebrow, color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HomeScreen(padding: PaddingValues, onVehicle: (ExactVehicle) -> Unit) {
    var vehicles by remember { mutableStateOf<List<ExactVehicle>>(emptyList()) }
    var makes by remember { mutableStateOf<List<ExactMake>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching {
            vehicles = SupabaseClient.client.from("vehicle_models").select(
                Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")
            ).decodeList<ExactVehicle>()
        }
        runCatching {
            makes = SupabaseClient.client.from("vehicle_makes").select(Columns.list("id", "name")).decodeList<ExactMake>()
        }
    }

    val filtered = vehicles.filter { vehicle ->
        query.isBlank() || vehicle.name.contains(query, true) || makes.firstOrNull { make -> make.id == vehicle.makeId }?.name?.contains(query, true) == true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Header("CarDiag", "SMART VEHICLE DIAGNOSTICS") }
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(330.dp).padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF18383D), Surface, Bg)))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(text = "SMART VEHICLE DIAGNOSTICS", color = Teal, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Votre voiture.\nVos données. Votre diagnostic.",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = TextMain
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { }, shape = RoundedCornerShape(17.dp)) {
                        Icon(imageVector = Icons.Default.Build, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(text = "Lancer le diagnostic", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                shape = RoundedCornerShape(19.dp),
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Teal) },
                placeholder = { Text(text = "Rechercher une marque ou un modèle") }
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(vehicles.size.toString(), "MODÈLES", Modifier.weight(1f))
                StatCard(makes.size.toString(), "MARQUES", Modifier.weight(1f))
                StatCard("OBD-II", "SCANNER", Modifier.weight(1f))
            }
        }
        item { Text(text = "Catalogue véhicules", modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        items(items = filtered.take(12), key = { it.id }) { vehicle ->
            VehicleCard(
                vehicle = vehicle,
                make = makes.firstOrNull { it.id == vehicle.makeId }?.name ?: "Vehicle",
                onClick = { onVehicle(vehicle) }
            )
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column(modifier = Modifier.padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, color = Teal, fontWeight = FontWeight.Black)
            Text(text = label, color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VehicleCard(vehicle: ExactVehicle, make: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(27.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(205.dp)) {
                AsyncImage(model = vehicle.imageUrl, contentDescription = vehicle.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg.copy(alpha = .98f)))))
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Text(text = make.uppercase(), color = Teal, fontWeight = FontWeight.Black)
                    Text(text = vehicle.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(text = listOfNotNull(vehicle.generation, vehicle.yearFrom?.toString(), vehicle.yearTo?.toString()).joinToString(" • ").ifBlank { "Vehicle profile" }, color = Muted)
                }
            }
            Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Voir la fiche technique", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(text = "→", color = Teal, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun DiagnosticScreen(padding: PaddingValues) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { Header("Diagnostic", "OBD-II • LIVE DATA • DTC") }
        item { ActionCard("OBD-II Scanner", "Bluetooth ELM327 • ECU • DTC", Icons.Default.Build) { openObd(context) } }
        item { ActionCard("Live Data", "RPM • température • charge • capteurs", Icons.Default.Speed) { openObd(context) } }
        item { ActionCard("DTC & Faults", "Codes défaut et analyse guidée", Icons.Default.Warning) { openObd(context) } }
        item { ActionCard("Diagnostic guidé", "Symptômes + véhicule + mesures", Icons.Default.Build) { openGuided(context) } }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick), shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
        Row(modifier = Modifier.padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(TealSoft), contentAlignment = Alignment.Center) { Icon(imageVector = icon, contentDescription = null, tint = Teal) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black); Text(text = subtitle, color = Muted) }
            Text(text = "›", color = Teal, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun GarageScreen(padding: PaddingValues, onVehicle: (ExactVehicle) -> Unit) {
    var vehicles by remember { mutableStateOf<List<ExactVehicle>>(emptyList()) }
    LaunchedEffect(Unit) { runCatching { vehicles = SupabaseClient.client.from("vehicle_models").select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")).decodeList<ExactVehicle>() } }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Header("Mon Garage", "VOS VÉHICULES") }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(27.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
                Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Votre véhicule", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(text = "VIN, moteur, kilométrage, santé et historique au même endroit.", color = Muted)
                    Button(onClick = { vehicles.firstOrNull()?.let(onVehicle) }, enabled = vehicles.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(text = "Ajouter un véhicule") }
                }
            }
        }
        items(items = vehicles.take(8), key = { it.id }) { vehicle -> VehicleCard(vehicle, "Catalogue") { onVehicle(vehicle) } }
    }
}

@Composable
private fun HistoryScreen(padding: PaddingValues) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { Header("Historique", "VOS DIAGNOSTICS") }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(27.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
                Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(imageVector = Icons.Default.History, contentDescription = null, tint = Teal, modifier = Modifier.size(48.dp))
                    Text(text = "Aucun diagnostic enregistré", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(text = "Vos sessions, DTC et rapports apparaîtront ici.", color = Muted)
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(padding: PaddingValues) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("Plus", "CARDIAG") }
        item { ActionCard("Langue", "Français • العربية • RTL", Icons.Default.Settings) {} }
        item { ActionCard("Compte", "Profil, sécurité et préférences", Icons.Default.Garage) {} }
        item { ActionCard("À propos", "CarDiag • OBD-II • Vehicle Intelligence", Icons.Default.DirectionsCar) {} }
    }
}
