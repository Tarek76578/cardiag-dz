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

/** UI model shared by the premium catalog and the full Supabase vehicle profile. */
data class UiModel(
    val id: String,
    val name: String,
    val imageUrl: String? = null
)

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

private val ExactBg = Color(0xFF06090B)
private val ExactSurface = Color(0xFF0D1418)
private val ExactSurface2 = Color(0xFF131D22)
private val ExactTeal = Color(0xFF48D7C5)
private val ExactTealSoft = Color(0xFF153F3B)
private val ExactText = Color(0xFFF5F8F8)
private val ExactMuted = Color(0xFF8B9A9F)

@Composable
fun CarDiagExactApp() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ExactVehicle?>(null) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ExactTeal,
            onPrimary = ExactBg,
            background = ExactBg,
            surface = ExactSurface,
            surfaceVariant = ExactSurface2,
            onSurface = ExactText,
            onSurfaceVariant = ExactMuted
        )
    ) {
        if (selected != null) {
            VehicleProfileProScreen(
                model = UiModel(selected!!.id, selected!!.name, selected!!.imageUrl),
                onBack = { selected = null }
            )
        } else {
            Scaffold(
                containerColor = ExactBg,
                bottomBar = {
                    NavigationBar(containerColor = ExactSurface) {
                        val labels = listOf("Accueil", "Diagnostic", "Garage", "Historique", "Plus")
                        val icons = listOf(Icons.Default.Home, Icons.Default.Build, Icons.Default.Garage, Icons.Default.History, Icons.Default.Settings)
                        labels.forEachIndexed { i, label ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(icons[i], contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            ) { padding ->
                when (tab) {
                    0 -> ExactHome(padding, onVehicle = { selected = it })
                    1 -> ExactDiagnostic(padding)
                    2 -> ExactGarage(padding, onVehicle = { selected = it })
                    3 -> ExactHistory(padding)
                    else -> ExactMore(padding)
                }
            }
        }
    }
}

@Composable
private fun ExactHeader(title: String, eyebrow: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(ExactTealSoft), Alignment.Center) {
            Icon(Icons.Default.DirectionsCar, null, tint = ExactTeal)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(eyebrow, color = ExactMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExactHome(padding: PaddingValues, onVehicle: (ExactVehicle) -> Unit) {
    var vehicles by remember { mutableStateOf(emptyList<ExactVehicle>()) }
    var makes by remember { mutableStateOf(emptyList<ExactMake>()) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching {
            vehicles = SupabaseClient.client.from("vehicle_models")
                .select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url"))
                .decodeList<ExactVehicle>()
        }
        runCatching {
            makes = SupabaseClient.client.from("vehicle_makes")
                .select(Columns.list("id", "name"))
                .decodeList<ExactMake>()
        }
    }

    val filtered = vehicles.filter { v ->
        query.isBlank() || v.name.contains(query, true) || makes.firstOrNull { it.id == v.makeId }?.name?.contains(query, true) == true
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { ExactHeader("CarDiag", "SMART VEHICLE DIAGNOSTICS") }
        item {
            Box(
                Modifier.fillMaxWidth().height(330.dp).padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF18383D), ExactSurface, ExactBg)))
            ) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Bottom) {
                    Text("SMART VEHICLE DIAGNOSTICS", color = ExactTeal, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text("Votre voiture.\nVos données. Votre diagnostic.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = ExactText)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { }, shape = RoundedCornerShape(17.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                        Icon(Icons.Default.Build, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Lancer le diagnostic", fontWeight = FontWeight.Black)
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
                leadingIcon = { Icon(Icons.Default.Search, null, tint = ExactTeal) },
                placeholder = { Text("Rechercher une marque ou un modèle") }
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExactStat(vehicles.size.toString(), "MODÈLES", Modifier.weight(1f))
                ExactStat(makes.size.toString(), "MARQUES", Modifier.weight(1f))
                ExactStat("OBD-II", "SCANNER", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Catalogue véhicules", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text("Tout", color = ExactTeal, fontWeight = FontWeight.Bold)
            }
        }
        items(filtered.take(12), key = { it.id }) { vehicle ->
            ExactVehicleCard(vehicle, makes.firstOrNull { it.id == vehicle.makeId }?.name ?: "Vehicle", onClick = { onVehicle(vehicle) })
        }
    }
}

@Composable
private fun ExactStat(value: String, label: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ExactSurface)) {
        Column(Modifier.padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = ExactTeal, fontWeight = FontWeight.Black)
            Text(label, color = ExactMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExactVehicleCard(vehicle: ExactVehicle, make: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(27.dp),
        colors = CardDefaults.cardColors(containerColor = ExactSurface)
    ) {
        Box(Modifier.fillMaxWidth().height(205.dp)) {
            AsyncImage(vehicle.imageUrl, vehicle.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, ExactBg.copy(alpha = .98f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Text(make.uppercase(), color = ExactTeal, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                Text(vehicle.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(listOfNotNull(vehicle.generation, vehicle.yearFrom?.toString(), vehicle.yearTo?.toString()).joinToString(" • ").ifBlank { "Vehicle profile" }, color = ExactMuted)
            }
        }
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Voir la fiche technique", color = ExactText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("→", color = ExactTeal, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ExactDiagnostic(padding: PaddingValues) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { ExactHeader("Diagnostic", "OBD-II • LIVE DATA • DTC") }
        item { ExactFeature("OBD-II Scanner", "Bluetooth ELM327 • ECU • DTC", Icons.Default.Build) { openObd(context) } }
        item { ExactFeature("Live Data", "RPM • température • charge • capteurs", Icons.Default.Speed) { openObd(context) } }
        item { ExactFeature("DTC & Faults", "Codes défaut et analyse guidée", Icons.Default.Warning) { openObd(context) } }
        item { ExactFeature("Diagnostic guidé", "Symptômes + véhicule + mesures", Icons.Default.Build) { openGuided(context) } }
    }
}

@Composable
private fun ExactFeature(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick), shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = ExactSurface)) {
        Row(Modifier.padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(ExactTealSoft), Alignment.Center) { Icon(icon, null, tint = ExactTeal) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(subtitle, color = ExactMuted)
            }
            Text("›", color = ExactTeal, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ExactGarage(padding: PaddingValues, onVehicle: (ExactVehicle) -> Unit) {
    var vehicles by remember { mutableStateOf(emptyList<ExactVehicle>()) }
    LaunchedEffect(Unit) {
        runCatching { vehicles = SupabaseClient.client.from("vehicle_models").select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")).decodeList() }
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ExactHeader("Mon Garage", "VOS VÉHICULES") }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(27.dp), colors = CardDefaults.cardColors(containerColor = ExactSurface)) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Votre véhicule", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Gardez le VIN, moteur, kilométrage, santé et historique au même endroit.", color = ExactMuted)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { vehicles.firstOrNull()?.let(onVehicle) }, enabled = vehicles.isNotEmpty(), Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Ajouter un véhicule", fontWeight = FontWeight.Black) }
                }
            }
        }
        items(vehicles.take(8), key = { it.id }) { ExactVehicleCard(it, "Catalogue", { onVehicle(it) }) }
    }
}

@Composable
private fun ExactHistory(padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { ExactHeader("Historique", "VOS DIAGNOSTICS") }
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(27.dp), colors = CardDefaults.cardColors(containerColor = ExactSurface)) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(ExactTealSoft), Alignment.Center) { Icon(Icons.Default.History, null, tint = ExactTeal) }
                    Text("Aucun diagnostic enregistré", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Vos sessions, DTC et rapports apparaîtront ici.", color = ExactMuted)
                }
            }
        }
    }
}

@Composable
private fun ExactMore(padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ExactHeader("Plus", "CARDIAG") }
        item { ExactFeature("Langue", "Français • العربية • RTL", Icons.Default.Settings) {} }
        item { ExactFeature("Compte", "Profil, sécurité et préférences", Icons.Default.Garage) {} }
        item { ExactFeature("À propos", "CarDiag • OBD-II • Vehicle Intelligence", Icons.Default.DirectionsCar) {} }
    }
}
