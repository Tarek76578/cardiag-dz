package dz.cardiag.app

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Serializable data class ProfileDtcLink(@SerialName("code_id") val codeId: String, @SerialName("model_id") val modelId: String? = null, @SerialName("generation_id") val generationId: String? = null)
@Serializable data class ProfileDtc(val id: String, val code: String, val system: String? = null, @SerialName("title_fr") val titleFr: String? = null, @SerialName("description_fr") val descriptionFr: String? = null, val severity: String? = null, @SerialName("causes_fr") val causesFr: String? = null, @SerialName("diagnostic_steps_fr") val stepsFr: String? = null, @SerialName("repair_summary_fr") val repairFr: String? = null)
@Serializable data class ProfileGeneration(val id: String, @SerialName("model_id") val modelId: String? = null, val name: String? = null, @SerialName("year_from") val yearFrom: Int? = null, @SerialName("year_to") val yearTo: Int? = null, @SerialName("image_url") val imageUrl: String? = null)
@Serializable data class ProfileVehicleImage(val id: String, @SerialName("model_id") val modelId: String, @SerialName("generation_id") val generationId: String? = null, @SerialName("trim_id") val trimId: String? = null, @SerialName("image_url") val imageUrl: String, @SerialName("alt_text_fr") val altTextFr: String? = null, @SerialName("alt_text_ar") val altTextAr: String? = null, @SerialName("is_primary") val isPrimary: Boolean = false, @SerialName("sort_order") val sortOrder: Int = 0)
@Serializable data class ProfileEngine(val id: String, @SerialName("generation_id") val generationId: String, val name: String? = null, @SerialName("engine_code") val engineCode: String? = null, @SerialName("fuel_type") val fuelType: String? = null, @SerialName("displacement_cc") val displacementCc: Int? = null, val cylinders: Int? = null, val aspiration: String? = null, @SerialName("injection_type") val injectionType: String? = null, @SerialName("power_hp") val powerHp: Double? = null, @SerialName("power_kw") val powerKw: Double? = null, @SerialName("torque_nm") val torqueNm: Double? = null, @SerialName("transmission_types") val transmissions: List<String> = emptyList(), @SerialName("year_from") val yearFrom: Int? = null, @SerialName("year_to") val yearTo: Int? = null)
@Serializable data class ProfileEcuLink(val id: String, @SerialName("generation_id") val generationId: String, @SerialName("engine_id") val engineId: String? = null, @SerialName("ecu_id") val ecuId: String, val required: Boolean = false, @SerialName("year_from") val yearFrom: Int? = null, @SerialName("year_to") val yearTo: Int? = null, val notes: String? = null)
@Serializable data class ProfileEcu(val id: String, val manufacturer: String? = null, val name: String? = null, val family: String? = null, @SerialName("ecu_type") val ecuType: String? = null, val protocols: List<String> = emptyList(), @SerialName("description_fr") val descriptionFr: String? = null)
@Serializable data class ProfileSpec(val id: String, @SerialName("generation_id") val generationId: String? = null, @SerialName("engine_id") val engineId: String? = null, val key: String, @SerialName("value_text") val valueText: String? = null, @SerialName("value_number") val valueNumber: Double? = null, val unit: String? = null)

@Composable
fun VehicleProfileProScreen(model: UiModel, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var generations by remember { mutableStateOf(emptyList<ProfileGeneration>()) }
    var engines by remember { mutableStateOf(emptyList<ProfileEngine>()) }
    var ecuLinks by remember { mutableStateOf(emptyList<ProfileEcuLink>()) }
    var ecus by remember { mutableStateOf(emptyList<ProfileEcu>()) }
    var specs by remember { mutableStateOf(emptyList<ProfileSpec>()) }
    var images by remember { mutableStateOf(emptyList<ProfileVehicleImage>()) }
    var dtcs by remember { mutableStateOf(emptyList<ProfileDtc>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<ProfileDtc?>(null) }

    fun load() {
        scope.launch {
            loading = true; error = null
            runCatching {
                val gs = SupabaseClient.client.from("vehicle_generations").select(Columns.list("id", "model_id", "name", "year_from", "year_to", "image_url")).decodeList<ProfileGeneration>().filter { it.modelId == model.id }.sortedWith(compareBy<ProfileGeneration> { it.yearFrom ?: Int.MAX_VALUE }.thenBy { it.name ?: "" })
                val generationIds = gs.map { it.id }.toSet()
                val imageRows = SupabaseClient.client.from("vehicle_images").select(Columns.list("id", "model_id", "generation_id", "trim_id", "image_url", "alt_text_fr", "alt_text_ar", "is_primary", "sort_order")).decodeList<ProfileVehicleImage>().filter { it.modelId == model.id && it.imageUrl.isNotBlank() }.sortedWith(compareByDescending<ProfileVehicleImage> { it.isPrimary }.thenBy { it.sortOrder })
                val es = if (generationIds.isEmpty()) emptyList() else SupabaseClient.client.from("vehicle_engines").select(Columns.list("id", "generation_id", "name", "engine_code", "fuel_type", "displacement_cc", "cylinders", "aspiration", "injection_type", "power_hp", "power_kw", "torque_nm", "transmission_types", "year_from", "year_to")).decodeList<ProfileEngine>().filter { it.generationId in generationIds }.sortedBy { it.name ?: "" }
                val engineIds = es.map { it.id }.toSet()
                val links = if (generationIds.isEmpty()) emptyList() else SupabaseClient.client.from("vehicle_ecus").select(Columns.list("id", "generation_id", "engine_id", "ecu_id", "required", "year_from", "year_to", "notes")).decodeList<ProfileEcuLink>().filter { it.generationId in generationIds && (it.engineId == null || it.engineId in engineIds) }
                val ecuIds = links.map { it.ecuId }.distinct()
                val ecuRows = if (ecuIds.isEmpty()) emptyList() else SupabaseClient.client.from("ecu_modules").select(Columns.list("id", "manufacturer", "name", "family", "ecu_type", "protocols", "description_fr")).decodeList<ProfileEcu>().filter { it.id in ecuIds }
                val specRows = if (generationIds.isEmpty()) emptyList() else SupabaseClient.client.from("vehicle_specifications").select(Columns.list("id", "generation_id", "engine_id", "key", "value_text", "value_number", "unit")).decodeList<ProfileSpec>().filter { (it.generationId in generationIds) || (it.engineId in engineIds) }
                val linksDtc = SupabaseClient.client.from("diagnostic_code_vehicles").select(Columns.list("code_id", "model_id", "generation_id")).decodeList<ProfileDtcLink>().filter { it.modelId == model.id || it.generationId in generationIds }
                val cids = linksDtc.map { it.codeId }.distinct()
                val cs = if (cids.isEmpty()) emptyList() else SupabaseClient.client.from("diagnostic_codes").select(Columns.list("id", "code", "system", "title_fr", "description_fr", "severity", "causes_fr", "diagnostic_steps_fr", "repair_summary_fr")).decodeList<ProfileDtc>().filter { it.id in cids }.sortedBy { it.code }
                generations = gs; engines = es; ecuLinks = links; ecus = ecuRows; specs = specRows; images = imageRows; dtcs = cs
            }.onFailure { error = it.message ?: "Erreur Supabase" }
            loading = false
        }
    }
    LaunchedEffect(model.id) { load() }

    fun diagnose(d: ProfileDtc) { context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java).apply { putExtra("model_id", model.id); putExtra("model_name", model.name); putExtra("dtc_id", d.id); putExtra("dtc_code", d.code) }) }
    val primaryImage = images.firstOrNull { it.isPrimary }?.imageUrl ?: images.firstOrNull()?.imageUrl ?: model.imageUrl?.takeIf { it.isNotBlank() }

    Scaffold(topBar = { TopAppBar(title = { Text(model.name, fontWeight = FontWeight.Black) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") } }, actions = { IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, "Actualiser") } }) }) { p ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Column(Modifier.fillMaxSize().padding(p).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("Erreur", fontWeight = FontWeight.Bold); Text(error!!); Button(onClick = { load() }) { Text("Réessayer") } }
            else -> LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { if (primaryImage != null) AsyncImage(primaryImage, model.name, Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop) else Card(Modifier.fillMaxWidth().height(230.dp), shape = RoundedCornerShape(28.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Image du véhicule bientôt disponible", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                if (images.size > 1) item { Text("Galerie", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(images) { image -> AsyncImage(image.imageUrl, image.altTextFr ?: model.name, Modifier.size(150.dp, 100.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop) } } }
                item { Text("VEHICLE PROFILE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black); Text(model.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("${generations.size} générations • ${engines.size} moteurs • ${ecus.size} ECU • ${dtcs.size} DTC") }
                item { ProfileSection("Moteurs", Icons.Default.Speed) { if (engines.isEmpty()) Text("Aucune fiche moteur détaillée disponible.", color = MaterialTheme.colorScheme.onSurfaceVariant) else engines.forEach { e -> ProfileEngineCard(e) } } }
                item { ProfileSection("ECU / calculateurs", Icons.Default.Memory) { if (ecus.isEmpty()) Text("Aucun ECU détaillé disponible.", color = MaterialTheme.colorScheme.onSurfaceVariant) else ecus.forEach { ecu -> val linksFor = ecuLinks.filter { it.ecuId == ecu.id }; ProfileEcuCard(ecu, linksFor) } } }
                item { ProfileSection("Spécifications", Icons.Default.Speed) { if (specs.isEmpty()) Text("Les spécifications détaillées seront enrichies progressivement.", color = MaterialTheme.colorScheme.onSurfaceVariant) else specs.take(80).forEach { s -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(s.key, fontWeight = FontWeight.SemiBold); Text(listOfNotNull(s.valueText, s.valueNumber?.toString()).joinToString(" ") + (s.unit?.let { " $it" } ?: "")) } } } }
                item { Text("Générations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(generations) { g -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { g.imageUrl?.takeIf { it.isNotBlank() }?.let { AsyncImage(it, g.name, Modifier.size(88.dp, 64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop); Spacer(Modifier.width(12.dp)) }; Column { Text(g.name ?: "Génération", fontWeight = FontWeight.Bold); Text(listOfNotNull(g.yearFrom?.toString(), g.yearTo?.toString()).joinToString(" – ")) } } } }
                item { Text("DTC compatibles", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(dtcs.take(100)) { d -> Card(onClick = { selected = d }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, null); Spacer(Modifier.width(8.dp)); Text(d.code, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text(d.severity ?: "info") }; Text(d.titleFr ?: d.descriptionFr ?: "Code défaut") } } }
                item { Button(onClick = { dtcs.firstOrNull()?.let { diagnose(it) } }, enabled = dtcs.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Build, null); Spacer(Modifier.width(8.dp)); Text("Lancer le diagnostic") } }
            }
        }
    }
    selected?.let { d -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(d.code) }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(d.descriptionFr ?: d.titleFr ?: "Code défaut"); d.system?.let { Text("Système: $it") }; d.causesFr?.let { Text("Causes: $it") }; d.stepsFr?.let { Text("Diagnostic: $it") }; d.repairFr?.let { Text("Réparation: $it") } } }, confirmButton = { Button(onClick = { selected = null; diagnose(d) }) { Text("Diagnostic guidé") } }, dismissButton = { TextButton(onClick = { selected = null }) { Text("Fermer") } }) }
}

@Composable private fun ProfileSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }; content() } } }
@Composable private fun ProfileEngineCard(e: ProfileEngine) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(e.name ?: e.engineCode ?: "Moteur", fontWeight = FontWeight.Bold); Text(listOfNotNull(e.engineCode, e.fuelType, e.displacementCc?.let { "${it} cc" }, e.powerHp?.let { "${it} ch" }, e.torqueNm?.let { "${it} Nm" }).joinToString(" • ")); Text(listOfNotNull(e.aspiration, e.injectionType, e.cylinders?.let { "${it} cylindres" }, e.transmissions.joinToString("/").takeIf { it.isNotBlank() }).joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun ProfileEcuCard(e: ProfileEcu, links: List<ProfileEcuLink>) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(e.name ?: "ECU", fontWeight = FontWeight.Bold); Text(listOfNotNull(e.manufacturer, e.family, e.ecuType).joinToString(" • ")); if (e.protocols.isNotEmpty()) Text("Protocoles: ${e.protocols.joinToString(", ")}", color = MaterialTheme.colorScheme.onSurfaceVariant); links.firstOrNull()?.notes?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
