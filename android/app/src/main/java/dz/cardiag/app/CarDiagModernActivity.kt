package dz.cardiag.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
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
data class UiMake(val id: String, val name: String)
@Serializable
data class UiModel(val id: String, @SerialName("make_id") val makeId: String, val name: String, @SerialName("year_from") val yearFrom: Int? = null, @SerialName("year_to") val yearTo: Int? = null, val generation: String? = null, @SerialName("image_url") val imageUrl: String? = null)
@Serializable
data class UiGeneration(val id: String, @SerialName("model_id") val modelId: String, val name: String, val code: String? = null, @SerialName("year_from") val yearFrom: Int? = null, @SerialName("year_to") val yearTo: Int? = null, @SerialName("body_type") val bodyType: String? = null, @SerialName("platform_code") val platformCode: String? = null, @SerialName("image_url") val imageUrl: String? = null)
@Serializable
data class UiEngine(val id: String, @SerialName("generation_id") val generationId: String, val name: String, @SerialName("engine_code") val engineCode: String? = null, @SerialName("fuel_type") val fuelType: String = "unknown", @SerialName("displacement_cc") val displacementCc: Int? = null, val cylinders: Int? = null, @SerialName("power_hp") val powerHp: Double? = null, @SerialName("power_kw") val powerKw: Double? = null, @SerialName("torque_nm") val torqueNm: Double? = null, @SerialName("transmission_types") val transmissions: List<String> = emptyList())
@Serializable
data class UiTrim(val id: String, @SerialName("generation_id") val generationId: String, @SerialName("engine_id") val engineId: String? = null, val name: String, val drivetrain: String? = null, val transmission: String? = null, val doors: Int? = null, val seats: Int? = null, val market: String? = null)

class CarDiagModernActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { CarDiagModernApp() } }
}

private val Teal = Color(0xFF48D7C5)
private val Ink = Color(0xFF071014)
private val Panel = Color(0xFF101C22)

@Composable
private fun CarDiagModernApp() {
    remember { AuthService() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<UiModel?>(null) }
    var dark by rememberSaveable { mutableStateOf(true) }
    val scheme = if (dark) darkColorScheme(primary = Teal, background = Ink, surface = Panel, surfaceVariant = Color(0xFF17262D)) else lightColorScheme(primary = Color(0xFF00695F))
    MaterialTheme(colorScheme = scheme) {
        if (selected != null) VehicleProfileProScreen(selected!!, onBack = { selected = null })
        else Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { CenterAlignedTopAppBar(title = { Text("CarDiag", fontWeight = FontWeight.Black) }, navigationIcon = { Icon(Icons.Default.DirectionsCar, null, tint = Teal) }) },
            bottomBar = {
                NavigationBar {
                    val labels = listOf("Accueil", "Diagnostic", "Garage", "Historique", "Réglages")
                    val icons = listOf(Icons.Default.Home, Icons.Default.Build, Icons.Default.DirectionsCar, Icons.Default.History, Icons.Default.Settings)
                    labels.forEachIndexed { i, label -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], null) }, label = { Text(label) }) }
                }
            }
        ) { p ->
            when (tab) {
                0 -> HomeModern(p, onOpenVehicle = { selected = it }, onDiagnostic = { tab = 1 })
                1 -> DiagnosticModern(p)
                2 -> GarageModern(p, onOpenVehicle = { selected = it })
                3 -> HistoryModern(p)
                else -> SettingsModern(p, dark, { dark = it })
            }
        }
    }
}

@Composable
private fun HomeModern(p: PaddingValues, onOpenVehicle: (UiModel) -> Unit, onDiagnostic: () -> Unit) {
    var models by remember { mutableStateOf<List<UiModel>>(emptyList()) }
    var makes by remember { mutableStateOf<List<UiMake>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { scope.launch {
        runCatching { SupabaseClient.client.from("vehicle_models").select(Columns.list("id","make_id","name","year_from","year_to","generation","image_url")).decodeList<UiModel>() }.onSuccess { models = it }.also { loading = false }
        runCatching { makes = SupabaseClient.client.from("vehicle_makes").select(Columns.list("id","name")).decodeList<UiMake>() }
    } }
    val filtered = models.filter { it.name.contains(query, true) || makes.firstOrNull { m -> m.id == it.makeId }?.name?.contains(query, true) == true }
    LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF123039), Ink)))) {
                Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("SMART VEHICLE DIAGNOSTICS", color = Teal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Votre voiture.\nVos données. Votre diagnostic.", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Button(onClick = onDiagnostic, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Build, null); Spacer(Modifier.width(8.dp)); Text("Lancer le diagnostic") }
                }
            }
        }
        item { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Rechercher une marque ou un modèle") }) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) { StatCard("${models.size}", "Modèles", Modifier.weight(1f)); StatCard("${makes.size}", "Marques", Modifier.weight(1f)); StatCard("OBD-II", "Scanner", Modifier.weight(1f)) } }
        item { Text("Catalogue véhicules", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        if (loading) items(4) { LoadingVehicleCard() }
        else items(filtered) { model -> VehicleCard(model, makes.firstOrNull { it.id == model.makeId }?.name ?: "Vehicle", onOpenVehicle) }
        if (!loading && filtered.isEmpty()) item { EmptyCard("Aucun véhicule trouvé", "Essayez une autre marque ou un autre modèle.") }
    }
}

@Composable private fun VehicleCard(model: UiModel, make: String, open: (UiModel) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { open(model) }, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = model.imageUrl, contentDescription = model.name, modifier = Modifier.size(126.dp, 92.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                Text(make.uppercase(), style = MaterialTheme.typography.labelSmall, color = Teal, fontWeight = FontWeight.Bold)
                Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(listOfNotNull(model.yearFrom, model.yearTo).joinToString(" – ").ifBlank { model.generation ?: "Catalogue" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Voir le profil", color = Teal, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.width(5.dp)); Icon(Icons.Default.ChevronRight, null, tint = Teal, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable private fun DiagnosticModern(p: PaddingValues) { LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { SectionHeader("Diagnostic OBD-II", Icons.Default.Build) }; item { Card(shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Connectez votre adaptateur ELM327", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge); Text("Lisez les DTC, les données en direct et associez les résultats au véhicule sélectionné.", color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Bluetooth, null); Spacer(Modifier.width(8.dp)); Text("Connecter l'OBD") } } } }; item { ActionCard("Scanner DTC", "Lire et expliquer les codes défaut", Icons.Default.Warning) }; item { ActionCard("Données live", "RPM, température, charge moteur et plus", Icons.Default.Speed) }; item { ActionCard("Diagnostic guidé", "Croiser symptômes + OBD + véhicule", Icons.Default.AutoAwesome) } } }
@Composable private fun GarageModern(p: PaddingValues, onOpenVehicle: (UiModel) -> Unit) { var models by remember { mutableStateOf<List<UiModel>>(emptyList()) }; LaunchedEffect(Unit) { runCatching { models = SupabaseClient.client.from("vehicle_models").select(Columns.list("id","make_id","name","year_from","year_to","generation","image_url")).decodeList() } }; LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { SectionHeader("Mon Garage", Icons.Default.Garage) }; item { Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Votre véhicule principal", fontWeight = FontWeight.Black); Text("Ajoutez un véhicule pour conserver VIN, kilométrage, moteur et historique.", color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Ajouter une voiture") } } } }; item { Text("Catalogue rapide", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }; items(models.take(8)) { VehicleCard(it, "Catalogue", onOpenVehicle) } } }
@Composable private fun HistoryModern(p: PaddingValues) { LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { SectionHeader("Historique", Icons.Default.History) }; item { EmptyCard("Aucun diagnostic enregistré", "Vos prochaines sessions OBD et diagnostics guidés apparaîtront ici.") } } }
@Composable private fun SettingsModern(p: PaddingValues, dark: Boolean, setDark: (Boolean) -> Unit) { LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { SectionHeader("Réglages", Icons.Default.Settings) }; item { Card { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Mode sombre", fontWeight = FontWeight.Bold); Text("Interface confortable pour l'atelier", color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = dark, onCheckedChange = setDark) } } }; item { ActionCard("Langue", "العربية / Français", Icons.Default.Language) }; item { ActionCard("Compte & sécurité", "Connexion, session et confidentialité", Icons.Default.Security) }; item { ActionCard("À propos", "CarDiag DZ • Diagnostic automobile", Icons.Default.Info) } } }
@Composable private fun ActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, tint = Teal, modifier = Modifier.padding(12.dp)) }; Column(Modifier.padding(start = 14.dp)) { Text(title, fontWeight = FontWeight.Black); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null) } } }
@Composable private fun StatCard(value: String, label: String, modifier: Modifier) { Card(modifier) { Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = Teal, fontWeight = FontWeight.Black); Text(label, style = MaterialTheme.typography.labelSmall) } } }
@Composable private fun LoadingVehicleCard() { Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp)) { Box(Modifier.size(120.dp,88.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)); Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.size(130.dp,16.dp).background(MaterialTheme.colorScheme.surfaceVariant)); Box(Modifier.size(90.dp,12.dp).background(MaterialTheme.colorScheme.surfaceVariant)) } } } }
@Composable private fun EmptyCard(title: String, subtitle: String) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Default.Info, null, tint = Teal); Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun SectionHeader(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Teal); Spacer(Modifier.width(8.dp)); Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) } }
