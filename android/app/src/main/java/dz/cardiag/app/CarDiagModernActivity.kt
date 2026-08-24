package dz.cardiag.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.graphics.vector.ImageVector
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
data class UiMake(val id: String, val name: String)

@Serializable
data class UiModel(
    val id: String,
    @SerialName("make_id") val makeId: String,
    val name: String,
    @SerialName("year_from") val yearFrom: Int? = null,
    @SerialName("year_to") val yearTo: Int? = null,
    val generation: String? = null,
    @SerialName("image_url") val imageUrl: String? = null
)

class CarDiagModernActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CarDiagModernApp() }
    }
}

private val MainTeal = Color(0xFF48D7C5)
private val MainInk = Color(0xFF071014)
private val MainPanel = Color(0xFF101C22)

@Composable
private fun CarDiagModernApp() {
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<UiModel?>(null) }
    var dark by remember { mutableStateOf(true) }
    var showLanguage by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scheme = if (dark) {
        darkColorScheme(primary = MainTeal, background = MainInk, surface = MainPanel, surfaceVariant = Color(0xFF17262D))
    } else {
        lightColorScheme(primary = Color(0xFF00695F))
    }

    MaterialTheme(colorScheme = scheme) {
        when {
            selected != null -> VehicleProfileProScreen(selected!!, onBack = { selected = null })
            showAccount -> AccountPanel(onClose = { showAccount = false })
            else -> Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("CarDiag", fontWeight = FontWeight.Black) },
                        navigationIcon = { Icon(Icons.Default.DirectionsCar, null, tint = MainTeal) }
                    )
                },
                bottomBar = {
                    val labels = listOf("Accueil", "Diagnostic", "Garage", "Historique", "Réglages")
                    val icons = listOf(Icons.Default.Home, Icons.Default.Build, Icons.Default.Garage, Icons.Default.History, Icons.Default.Settings)
                    NavigationBar {
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(icons[index], null) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            ) { padding ->
                when (tab) {
                    0 -> MainHome(padding, { selected = it }, { tab = 1 })
                    1 -> MainDiagnostic(padding, context)
                    2 -> MainGarage(padding) { selected = it }
                    3 -> MainHistory(padding, context)
                    else -> MainSettings(
                        padding,
                        dark,
                        { dark = it },
                        { showLanguage = true },
                        { showAccount = true },
                        { showAbout = true }
                    )
                }
            }
        }

        if (showLanguage) {
            AlertDialog(
                onDismissRequest = { showLanguage = false },
                title = { Text("Langue") },
                text = { Text("Choisissez la langue de l'interface. العربية et Français sont prises en charge.") },
                confirmButton = { Button(onClick = { showLanguage = false }) { Text("Français") } },
                dismissButton = { TextButton(onClick = { showLanguage = false }) { Text("العربية") } }
            )
        }
        if (showAbout) {
            AlertDialog(
                onDismissRequest = { showAbout = false },
                title = { Text("CarDiag DZ") },
                text = { Text("Plateforme de diagnostic automobile: catalogue véhicule, DTC, ECU, OBD-II et Live Data.") },
                confirmButton = { Button(onClick = { showAbout = false }) { Text("Fermer") } }
            )
        }
    }
}

private fun openObd(context: Context, model: UiModel? = null, dtc: String? = null) {
    context.startActivity(Intent(context, ObdScannerActivity::class.java).apply {
        putExtra("model_id", model?.id)
        putExtra("model_name", model?.name ?: "Véhicule")
        putExtra("dtc_code", dtc)
    })
}

private fun openGuided(context: Context, model: UiModel? = null) {
    context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java).apply {
        putExtra("model_id", model?.id)
        putExtra("model_name", model?.name ?: "Véhicule")
    })
}

@Composable
private fun MainHome(padding: PaddingValues, onOpen: (UiModel) -> Unit, onDiagnostic: () -> Unit) {
    var models by remember { mutableStateOf<List<UiModel>>(emptyList()) }
    var makes by remember { mutableStateOf<List<UiMake>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching {
            SupabaseClient.client.from("vehicle_models").select(
                Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")
            ).decodeList<UiModel>()
        }.onSuccess { models = it }.also { loading = false }
        runCatching {
            makes = SupabaseClient.client.from("vehicle_makes").select(Columns.list("id", "name")).decodeList<UiMake>()
        }
    }

    val filtered = models.filter { model ->
        model.name.contains(query, true) || makes.firstOrNull { it.id == model.makeId }?.name?.contains(query, true) == true
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF123039), MainInk)))
            ) {
                Column(
                    Modifier.align(Alignment.BottomStart).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text("SMART VEHICLE DIAGNOSTICS", color = MainTeal, fontWeight = FontWeight.Bold)
                    Text("Votre voiture.\nVos données. Votre diagnostic.", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Button(onClick = onDiagnostic) {
                        Icon(Icons.Default.Build, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Lancer le diagnostic")
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Rechercher une marque ou un modèle") }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MainStat("${models.size}", "Modèles", Modifier.weight(1f))
                MainStat("${makes.size}", "Marques", Modifier.weight(1f))
                MainStat("OBD-II", "Scanner", Modifier.weight(1f))
            }
        }
        item { Text("Catalogue véhicules", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        if (loading) {
            items(3) { MainLoadingCard() }
        } else {
            items(filtered) { model ->
                MainVehicleCard(model, makes.firstOrNull { it.id == model.makeId }?.name ?: "Vehicle") { onOpen(model) }
            }
        }
        if (!loading && filtered.isEmpty()) {
            item { MainEmptyCard("Aucun véhicule trouvé", "Essayez une autre marque ou un autre modèle.") }
        }
    }
}

@Composable
private fun MainVehicleCard(model: UiModel, make: String, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = model.imageUrl,
                contentDescription = model.name,
                modifier = Modifier.size(126.dp, 92.dp).clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(make.uppercase(), style = MaterialTheme.typography.labelSmall, color = MainTeal, fontWeight = FontWeight.Bold)
                Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(listOfNotNull(model.yearFrom, model.yearTo).joinToString(" – ").ifBlank { model.generation ?: "Catalogue" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Voir le profil  ›", color = MainTeal, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MainDiagnostic(padding: PaddingValues, context: Context) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { MainHeader("Diagnostic OBD-II", Icons.Default.Build) }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Connectez votre adaptateur ELM327", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text("Lisez les DTC et les données live, puis associez-les au véhicule.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { openObd(context) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Bluetooth, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Connecter l'OBD")
                    }
                }
            }
        }
        item { MainAction("Scanner DTC", "Lire et expliquer les codes défaut", Icons.Default.Warning) { openObd(context) } }
        item { MainAction("Données live", "RPM, température, charge moteur et plus", Icons.Default.Speed) { openObd(context) } }
        item { MainAction("Diagnostic guidé", "Croiser symptômes + OBD + véhicule", Icons.Default.AutoAwesome) { openGuided(context) } }
    }
}

@Composable
private fun MainGarage(padding: PaddingValues, onOpen: (UiModel) -> Unit) {
    var models by remember { mutableStateOf<List<UiModel>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching {
            models = SupabaseClient.client.from("vehicle_models").select(
                Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")
            ).decodeList<UiModel>()
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { MainHeader("Mon Garage", Icons.Default.Garage) }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Votre véhicule principal", fontWeight = FontWeight.Black)
                    Text("Conservez VIN, kilométrage, moteur et historique.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { models.firstOrNull()?.let(onOpen) }, enabled = models.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text("Ajouter une voiture")
                    }
                }
            }
        }
        item { Text("Catalogue rapide", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(models.take(8)) { model -> MainVehicleCard(model, "Catalogue") { onOpen(model) } }
    }
}

@Composable
private fun MainHistory(padding: PaddingValues, context: Context) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { MainHeader("Historique", Icons.Default.History) }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.History, null, tint = MainTeal)
                    Text("Vos sessions de diagnostic apparaîtront ici", fontWeight = FontWeight.Bold)
                    Text("Lancez un scan OBD puis enregistrez la session dans Supabase.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { openObd(context) }) { Text("Lancer un scan OBD") }
                }
            }
        }
    }
}

@Composable
private fun MainSettings(
    padding: PaddingValues,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onLanguage: () -> Unit,
    onAccount: () -> Unit,
    onAbout: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MainHeader("Réglages", Icons.Default.Settings) }
        item {
            Card {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mode sombre", fontWeight = FontWeight.Bold)
                        Text("Interface atelier", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = dark, onCheckedChange = onDarkChange)
                }
            }
        }
        item { MainAction("Langue", "العربية / Français", Icons.Default.Language, onLanguage) }
        item { MainAction("Compte & sécurité", "Session et confidentialité", Icons.Default.Security, onAccount) }
        item { MainAction("À propos", "CarDiag DZ", Icons.Default.Info, onAbout) }
    }
}

@Composable
private fun AccountPanel(onClose: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compte") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Retour") } }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AuthScreen(onAuthenticated = onClose, onContinueAsGuest = onClose)
        }
    }
}

@Composable
private fun MainHeader(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MainTeal)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MainAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, tint = MainTeal, modifier = Modifier.padding(12.dp))
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun MainStat(value: String, label: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = MainTeal, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MainLoadingCard() {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            Box(Modifier.size(120.dp, 88.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(120.dp, 16.dp).background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}

@Composable
private fun MainEmptyCard(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Info, null, tint = MainTeal)
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
