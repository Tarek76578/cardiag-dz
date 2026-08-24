package dz.cardiag.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
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
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable private data class VPGeneration(val id: String, @SerialName("model_id") val modelId: String? = null, val name: String? = null, @SerialName("year_from") val yearFrom: Int? = null, @SerialName("year_to") val yearTo: Int? = null, @SerialName("image_url") val imageUrl: String? = null)
@Serializable private data class VPEngine(val id: String, @SerialName("generation_id") val generationId: String, val name: String? = null, @SerialName("engine_code") val code: String? = null, @SerialName("fuel_type") val fuel: String? = null, @SerialName("displacement_cc") val cc: Int? = null, val cylinders: Int? = null, @SerialName("power_hp") val hp: Double? = null, @SerialName("torque_nm") val torque: Double? = null, @SerialName("transmission_types") val transmissions: List<String> = emptyList())
@Serializable private data class VPEcuLink(val ecuId: String, @SerialName("generation_id") val generationId: String, @SerialName("engine_id") val engineId: String? = null)
@Serializable private data class VPEcu(val id: String, val manufacturer: String? = null, val name: String? = null, val family: String? = null, @SerialName("ecu_type") val type: String? = null, val protocols: List<String> = emptyList())
@Serializable private data class VPSpec(val id: String, @SerialName("generation_id") val generationId: String? = null, @SerialName("engine_id") val engineId: String? = null, val key: String, @SerialName("value_text") val text: String? = null, @SerialName("value_number") val number: Double? = null, val unit: String? = null)
@Serializable private data class VPDtcLink(@SerialName("code_id") val codeId: String, @SerialName("model_id") val modelId: String? = null, @SerialName("generation_id") val generationId: String? = null)
@Serializable private data class VPDtc(val id: String, val code: String, val system: String? = null, @SerialName("title_fr") val title: String? = null, @SerialName("description_fr") val description: String? = null, val severity: String? = null, @SerialName("causes_fr") val causes: String? = null, @SerialName("diagnostic_steps_fr") val steps: String? = null)

private val VPBg = Color(0xFF06090B)
private val VPSurface = Color(0xFF0D1418)
private val VPTeal = Color(0xFF48D7C5)
private val VPSoft = Color(0xFF153F3B)
private val VPText = Color(0xFFF5F8F8)
private val VPMuted = Color(0xFF8B9A9F)

@Composable
fun ExactVehicleProfileScreen(model: UiModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var generations by remember { mutableStateOf<List<VPGeneration>>(emptyList()) }
    var engines by remember { mutableStateOf<List<VPEngine>>(emptyList()) }
    var ecus by remember { mutableStateOf<List<VPEcu>>(emptyList()) }
    var specs by remember { mutableStateOf<List<VPSpec>>(emptyList()) }
    var dtcs by remember { mutableStateOf<List<VPDtc>>(emptyList()) }
    var images by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var selectedDtc by remember { mutableStateOf<VPDtc?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                val gs = SupabaseClient.client.from("vehicle_generations").select(Columns.list("id", "model_id", "name", "year_from", "year_to", "image_url")).decodeList<VPGeneration>().filter { it.modelId == model.id }
                val generationIds = gs.map { it.id }.toSet()
                val es = if (generationIds.isEmpty()) emptyList() else SupabaseClient.client.from("vehicle_engines").select(Columns.list("id", "generation_id", "name", "engine_code", "fuel_type", "displacement_cc", "cylinders", "power_hp", "torque_nm", "transmission_types")).decodeList<VPEngine>().filter { it.generationId in generationIds }
                val engineIds = es.map { it.id }.toSet()
                val links = if (generationIds.isEmpty()) emptyList() else SupabaseClient.client.from("vehicle_ecus").select(Columns.list("ecu_id", "generation_id", "engine_id")).decodeList<VPEcuLink>().filter { it.generationId in generationIds && (it.engineId == null || it.engineId in engineIds) }
                val ecuIds = links.map { it.ecuId }.distinct()
                val esu = if (ecuIds.isEmpty()) emptyList() else SupabaseClient.client.from("ecu_modules").select(Columns.list("id", "manufacturer", "name", "family", "ecu_type", "protocols")).decodeList<VPEcu>().filter { it.id in ecuIds }
                val ss = if (generationIds.isEmpty()) emptyList() else SupabaseClient.client.from("vehicle_specifications").select(Columns.list("id", "generation_id", "engine_id", "key", "value_text", "value_number", "unit")).decodeList<VPSpec>().filter { it.generationId in generationIds || it.engineId in engineIds }
                val ims = SupabaseClient.client.from("vehicle_images").select(Columns.list("image_url", "is_primary", "sort_order")).decodeList<VPImage>().filter { it.imageUrl.isNotBlank() }.sortedWith(compareByDescending<VPImage> { it.primary }.thenBy { it.order }).map { it.imageUrl }
                val linksDtc = SupabaseClient.client.from("diagnostic_code_vehicles").select(Columns.list("code_id", "model_id", "generation_id")).decodeList<VPDtcLink>().filter { it.modelId == model.id || it.generationId in generationIds }
                val dtcIds = linksDtc.map { it.codeId }.distinct()
                val ds = if (dtcIds.isEmpty()) emptyList() else SupabaseClient.client.from("diagnostic_codes").select(Columns.list("id", "code", "system", "title_fr", "description_fr", "severity", "causes_fr", "diagnostic_steps_fr")).decodeList<VPDtc>().filter { it.id in dtcIds }
                generations = gs
                engines = es
                ecus = esu
                specs = ss
                images = ims
                dtcs = ds
            }.onFailure { error = it.message ?: "Erreur Supabase" }
            loading = false
        }
    }

    LaunchedEffect(model.id) { load() }
    val hero = images.firstOrNull() ?: model.imageUrl
    val tabs = listOf("Overview", "Engine", "Specs", "ECU & OBD", "DTC")

    Scaffold(containerColor = VPBg) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = VPTeal) }
            error != null -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("Impossible de charger la fiche", color = VPText, fontWeight = FontWeight.Black); Text(error!!, color = VPMuted); Button(onClick = { load() }) { Text("Réessayer") } }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().height(350.dp)) {
                        if (hero != null) AsyncImage(model = hero, contentDescription = model.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, VPBg.copy(alpha = .98f)))))
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = VPText) }; Spacer(Modifier.weight(1f)); IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, contentDescription = "Actualiser", tint = VPText) } }
                        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) { Text("VEHICLE PROFILE", color = VPTeal, fontWeight = FontWeight.Black); Text(model.name, color = VPText, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Text("${generations.size} générations • ${engines.size} moteurs • ${ecus.size} ECU • ${dtcs.size} DTC", color = VPMuted) }
                    }
                }
                item { LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(tabs) { label -> val index = tabs.indexOf(label); Surface(modifier = Modifier.clip(RoundedCornerShape(15.dp)).clickable { tab = index }, color = if (tab == index) VPTeal else VPSurface) { Text(text = label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), color = if (tab == index) VPBg else VPText, fontWeight = FontWeight.Bold) } } } }
                item {
                    when (tab) {
                        0 -> OverviewTab(model, generations, engines, ecus, dtcs)
                        1 -> EngineTab(engines)
                        2 -> SpecsTab(specs)
                        3 -> EcuTab(ecus)
                        else -> DtcTab(dtcs) { selectedDtc = it }
                    }
                }
                item { Button(onClick = { context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java).apply { putExtra("model_id", model.id); putExtra("model_name", model.name) }) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(17.dp)) { Icon(Icons.Default.Build, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Lancer le diagnostic", fontWeight = FontWeight.Black) } }
            }
        }
    }

    selectedDtc?.let { dtc -> AlertDialog(onDismissRequest = { selectedDtc = null }, title = { Text(dtc.code) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(dtc.title ?: dtc.description ?: "Code défaut"); dtc.system?.let { Text("Système • $it") }; dtc.causes?.let { Text("Causes\n$it") }; dtc.steps?.let { Text("Diagnostic\n$it") } } }, confirmButton = { Button(onClick = { selectedDtc = null }) { Text("Fermer") } }) }
}

@Serializable private data class VPImage(@SerialName("image_url") val imageUrl: String, @SerialName("is_primary") val primary: Boolean = false, @SerialName("sort_order") val order: Int = 0)

@Composable private fun OverviewTab(model: UiModel, generations: List<VPGeneration>, engines: List<VPEngine>, ecus: List<VPEcu>, dtcs: List<VPDtc>) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Vehicle overview", color = VPText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { InfoCard("GENERATIONS", generations.size.toString(), Modifier.weight(1f)); InfoCard("ENGINES", engines.size.toString(), Modifier.weight(1f)); InfoCard("DTC", dtcs.size.toString(), Modifier.weight(1f)) }
        InfoCard("MODEL ID", model.id, Modifier.fillMaxWidth())
        Text("Diagnostic coverage", color = VPMuted, fontWeight = FontWeight.Bold)
        Text("${ecus.size} ECU compatibles et ${dtcs.size} codes défaut associés.", color = VPText)
    }
}

@Composable private fun EngineTab(rows: List<VPEngine>) { Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Motorisations", color = VPText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); rows.forEach { e -> DataCard(e.name ?: e.code ?: "Moteur", listOfNotNull(e.code, e.fuel, e.cc?.let { "$it cc" }, e.cylinders?.let { "$it cylindres" }, e.hp?.let { "$it ch" }, e.torque?.let { "$it Nm" }, e.transmissions.joinToString("/").ifBlank { null }).joinToString(" • ")) } } }

@Composable private fun SpecsTab(rows: List<VPSpec>) { Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Specifications", color = VPText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); rows.take(120).forEach { s -> Row(Modifier.fillMaxWidth().background(VPSurface, RoundedCornerShape(14.dp)).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(s.key, color = VPText, fontWeight = FontWeight.SemiBold); Text(listOfNotNull(s.text, s.number?.toString()).joinToString(" ") + (s.unit?.let { " $it" } ?: ""), color = VPMuted) } } } }

@Composable private fun EcuTab(rows: List<VPEcu>) { Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("ECU & OBD", color = VPText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); rows.forEach { e -> DataCard(e.name ?: "ECU", listOfNotNull(e.manufacturer, e.family, e.type, e.protocols.joinToString(", ").ifBlank { null }).joinToString(" • ")) } } }

@Composable private fun DtcTab(rows: List<VPDtc>, onClick: (VPDtc) -> Unit) { Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("DTC & Faults", color = VPText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); rows.take(100).forEach { d -> Card(modifier = Modifier.fillMaxWidth().clickable { onClick(d) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = VPSurface)) { Column(Modifier.padding(16.dp)) { Row { Text(d.code, color = VPTeal, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text(d.severity ?: "INFO", color = VPMuted) }; Text(d.title ?: d.description ?: "Code défaut", color = VPText) } } } } }

@Composable private fun InfoCard(label: String, value: String, modifier: Modifier) { Card(modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = VPSurface)) { Column(Modifier.padding(14.dp)) { Text(label, color = VPMuted, style = MaterialTheme.typography.labelSmall); Text(value, color = VPTeal, fontWeight = FontWeight.Black) } } }
@Composable private fun DataCard(title: String, value: String) { Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = VPSurface)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, color = VPText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black); Text(value.ifBlank { "Données détaillées bientôt disponibles" }, color = VPMuted) } } }
