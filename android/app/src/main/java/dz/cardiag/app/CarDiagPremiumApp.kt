package dz.cardiag.app

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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private val Carbon = Color(0xFF070B0D)
private val Carbon2 = Color(0xFF0D1418)
private val Carbon3 = Color(0xFF131D22)
private val Teal = Color(0xFF48D7C5)
private val TealDark = Color(0xFF123E3A)
private val White = Color(0xFFF5F8F8)
private val Muted = Color(0xFF8B9A9F)

@Serializable
data class PremiumVehicle(
    val id: String,
    @SerialName("make_id") val makeId: String,
    val name: String,
    @SerialName("year_from") val yearFrom: Int? = null,
    @SerialName("year_to") val yearTo: Int? = null,
    val generation: String? = null,
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class PremiumMake(val id: String, val name: String)

@Composable
fun CarDiagPremiumApp() {
    var tab by remember { mutableIntStateOf(0) }
    var selectedVehicle by remember { mutableStateOf<PremiumVehicle?>(null) }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Teal,
        onPrimary = Carbon,
        background = Carbon,
        surface = Carbon2,
        surfaceVariant = Carbon3,
        onSurface = White,
        onSurfaceVariant = Muted
    )) {
        if (selectedVehicle != null) {
            PremiumVehicleProfile(selectedVehicle!!, onBack = { selectedVehicle = null })
            return@MaterialTheme
        }

        Scaffold(
            containerColor = Carbon,
            bottomBar = {
                NavigationBar(containerColor = Carbon2) {
                    val labels = listOf("Accueil", "Diagnostic", "Garage", "Historique", "Plus")
                    val icons = listOf(Icons.Default.Home, Icons.Default.Build, Icons.Default.Garage, Icons.Default.History, Icons.Default.Settings)
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
                0 -> PremiumHome(padding) { selectedVehicle = it }
                1 -> PremiumDiagnostic(padding)
                2 -> PremiumGarage(padding) { selectedVehicle = it }
                3 -> PremiumHistory(padding)
                else -> PremiumMore(padding)
            }
        }
    }
}

@Composable
private fun PremiumTopBar(title: String, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(TealDark),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.DirectionsCar, null, tint = Teal) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Muted) }
        }
        IconButton(onClick = {}) { Icon(Icons.Default.Settings, null, tint = Muted) }
    }
}

@Composable
private fun PremiumHome(padding: PaddingValues, onVehicle: (PremiumVehicle) -> Unit) {
    var vehicles by remember { mutableStateOf<List<PremiumVehicle>>(emptyList()) }
    var makes by remember { mutableStateOf<List<PremiumMake>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching {
            vehicles = SupabaseClient.client.from("vehicle_models").select(
                Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")
            ).decodeList<PremiumVehicle>()
        }
        runCatching {
            makes = SupabaseClient.client.from("vehicle_makes").select(Columns.list("id", "name")).decodeList<PremiumMake>()
        }
    }

    val filtered = vehicles.filter {
        it.name.contains(query, true) || makes.firstOrNull { make -> make.id == it.makeId }?.name?.contains(query, true) == true
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { PremiumTopBar("CarDiag", "SMART VEHICLE DIAGNOSTICS") }

        item {
            Box(
                Modifier.fillMaxWidth().height(310.dp).padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF183239), Carbon2, Carbon)))
            ) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Bottom) {
                    Text("SMART VEHICLE DIAGNOSTICS", color = Teal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(7.dp))
                    Text("Votre voiture.\nVos données. Votre diagnostic.", color = White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { /* scanner screen is opened from Diagnostic */ }, shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.Build, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Lancer le diagnostic", fontWeight = FontWeight.Bold)
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
                shape = RoundedCornerShape(18.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Teal) },
                placeholder = { Text("Rechercher une marque ou un modèle") }
            )
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumStat(vehicles.size.toString(), "MODÈLES", Modifier.weight(1f))
                PremiumStat(makes.size.toString(), "MARQUES", Modifier.weight(1f))
                PremiumStat("OBD-II", "SCANNER", Modifier.weight(1f))
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Catalogue véhicules", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text("Voir tout", color = Teal, fontWeight = FontWeight.Bold)
            }
        }

        items(filtered.take(12)) { vehicle ->
            PremiumVehicleCard(
                vehicle = vehicle,
                make = makes.firstOrNull { it.id == vehicle.makeId }?.name ?: "Vehicle",
                onClick = { onVehicle(vehicle) }
            )
        }
    }
}

@Composable
private fun PremiumStat(value: String, label: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Carbon2)) {
        Column(Modifier.padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = Teal, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PremiumVehicleCard(vehicle: PremiumVehicle, make: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Carbon2)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))) {
                AsyncImage(vehicle.imageUrl, vehicle.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Carbon2.copy(alpha = .95f)))))
                Text(make.uppercase(), Modifier.align(Alignment.BottomStart).padding(18.dp), color = Teal, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            }
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(vehicle.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        listOfNotNull(vehicle.generation, vehicle.yearFrom?.toString(), vehicle.yearTo?.toString()).joinToString(" • ").ifBlank { "Vehicle profile" },
                        color = Muted
                    )
                }
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(TealDark), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ChevronRight, null, tint = Teal)
                }
            }
        }
    }
}

@Composable
private fun PremiumDiagnostic(padding: PaddingValues) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PremiumTopBar("Diagnostic", "OBD-II • LIVE DATA • DTC") }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Carbon2)) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(TealDark), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Bluetooth, null, tint = Teal)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("OBD-II Scanner", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text("ELM327 • Bluetooth", color = Muted)
                        }
                    }
                    Text("Connectez votre adaptateur et commencez un diagnostic guidé.", color = Muted)
                    Button(onClick = { openObd(context) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Text("Connecter l'adaptateur", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { PremiumActionCard("Lire les DTC", "Codes défaut moteur et systèmes", Icons.Default.Warning) { openObd(context) } }
        item { PremiumActionCard("Live Data", "RPM • température • charge • capteurs", Icons.Default.Speed) { openObd(context) } }
        item { PremiumActionCard("Diagnostic guidé", "Symptômes + DTC + données véhicule", Icons.Default.Build) { openGuided(context) } }
    }
}

@Composable
private fun PremiumActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Carbon2)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(TealDark), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Teal) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = Muted)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
private fun PremiumGarage(padding: PaddingValues, onVehicle: (PremiumVehicle) -> Unit) {
    var vehicles by remember { mutableStateOf<List<PremiumVehicle>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching {
            vehicles = SupabaseClient.client.from("vehicle_models").select(
                Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")
            ).decodeList<PremiumVehicle>()
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PremiumTopBar("Mon Garage", "VOS VÉHICULES") }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Carbon2)) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Votre garage", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Ajoutez un véhicule pour garder VIN, moteur, kilométrage et historique au même endroit.", color = Muted)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { vehicles.firstOrNull()?.let(onVehicle) }, enabled = vehicles.isNotEmpty(), Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Ajouter un véhicule") }
                }
            }
        }
        item { Text("Véhicules disponibles", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        items(vehicles.take(8)) { PremiumVehicleCard(it, "Catalogue", { onVehicle(it) }) }
    }
}

@Composable
private fun PremiumHistory(padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PremiumTopBar("Historique", "VOS DIAGNOSTICS") }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Carbon2)) {
                Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(TealDark), contentAlignment = Alignment.Center) { Icon(Icons.Default.History, null, tint = Teal) }
                    Text("Aucun diagnostic enregistré", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Vos sessions, DTC et rapports apparaîtront ici.", color = Muted)
                }
            }
        }
    }
}

@Composable
private fun PremiumMore(padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PremiumTopBar("Plus", "CAR DIAG") }
        item { PremiumActionCard("Langue", "Français • العربية", Icons.Default.Settings) {} }
        item { PremiumActionCard("Compte", "Profil et sécurité", Icons.Default.Garage) {} }
        item { PremiumActionCard("À propos", "CarDiag DZ • OBD-II", Icons.Default.DirectionsCar) {} }
    }
}

@Composable
private fun PremiumVehicleProfile(vehicle: PremiumVehicle, onBack: () -> Unit) {
    var section by remember { mutableIntStateOf(0) }
    val sections = listOf("Overview", "Engine", "Specs", "ECU & OBD", "DTC")
    Scaffold(containerColor = Carbon) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(330.dp)) {
                    AsyncImage(vehicle.imageUrl, vehicle.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Carbon))))
                    IconButton(onClick = onBack, Modifier.padding(14.dp)) { Icon(Icons.Default.ChevronRight, null, tint = White) }
                    Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                        Text("VEHICLE PROFILE", color = Teal, fontWeight = FontWeight.Black)
                        Text(vehicle.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                        Text(listOfNotNull(vehicle.generation, vehicle.yearFrom?.toString(), vehicle.yearTo?.toString()).joinToString(" • "), color = Muted)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sections.forEachIndexed { index, label ->
                        Surface(
                            Modifier.clip(RoundedCornerShape(14.dp)).clickable { section = index },
                            color = if (section == index) Teal else Carbon2
                        ) { Text(label, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), color = if (section == index) Carbon else White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            item {
                when (section) {
                    0 -> VehicleOverview(vehicle)
                    1 -> VehicleSection("Engine", "Motorisations compatibles", "1.2 Turbo • 1.5 Diesel • informations moteur depuis Supabase")
                    2 -> VehicleSection("Specifications", "Fiche technique", "Puissance • couple • cylindrée • carburant • transmission")
                    3 -> VehicleSection("ECU & OBD", "Architecture diagnostic", "ECU • CAN • ISO 15765-4 • DTC • Live Data")
                    else -> VehicleSection("DTC & Faults", "Codes défaut", "Recherchez un DTC et lancez une procédure de diagnostic guidée.")
                }
            }
        }
    }
}

@Composable
private fun VehicleOverview(vehicle: PremiumVehicle) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Vehicle overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PremiumInfo("Generation", vehicle.generation ?: "—", Modifier.weight(1f))
            PremiumInfo("Years", listOfNotNull(vehicle.yearFrom, vehicle.yearTo).joinToString("–").ifBlank { "—" }, Modifier.weight(1f))
        }
        PremiumInfo("Model ID", vehicle.id, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PremiumInfo(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Carbon2)) {
        Column(Modifier.padding(17.dp)) {
            Text(label.uppercase(), color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(value, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun VehicleSection(title: String, subtitle: String, body: String) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Carbon2)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(subtitle, color = Teal, fontWeight = FontWeight.Bold)
            Text(body, color = Muted)
        }
    }
}
