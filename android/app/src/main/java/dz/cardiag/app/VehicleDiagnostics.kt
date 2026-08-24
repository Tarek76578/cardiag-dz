package dz.cardiag.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileImage(val id: String, @SerialName("image_url") val imageUrl: String, @SerialName("is_primary") val isPrimary: Boolean = false, @SerialName("sort_order") val sortOrder: Int = 0, @SerialName("alt_text_fr") val altFr: String? = null, @SerialName("alt_text_ar") val altAr: String? = null)

@Serializable
data class ProfileEcuLink(val id: String, @SerialName("generation_id") val generationId: String, @SerialName("engine_id") val engineId: String? = null, @SerialName("ecu_id") val ecuId: String, val required: Boolean = true, val notes: String? = null)

@Serializable
data class ProfileEcu(val id: String, val manufacturer: String? = null, val name: String, val family: String? = null, @SerialName("ecu_type") val ecuType: String = "other", val protocols: List<String> = emptyList(), @SerialName("part_numbers") val partNumbers: List<String> = emptyList(), @SerialName("description_fr") val descriptionFr: String? = null, @SerialName("description_ar") val descriptionAr: String? = null)

@Serializable
data class ProfileDtcLink(val id: String, @SerialName("code_id") val codeId: String, @SerialName("model_id") val modelId: String? = null, @SerialName("generation_id") val generationId: String? = null, @SerialName("engine_id") val engineId: String? = null, @SerialName("ecu_id") val ecuId: String? = null, val applicability: String = "confirmed", @SerialName("notes_fr") val notesFr: String? = null, @SerialName("notes_ar") val notesAr: String? = null)

@Serializable
data class ProfileDtc(val id: String, val code: String, val system: String? = null, @SerialName("title_fr") val titleFr: String? = null, @SerialName("title_ar") val titleAr: String? = null, @SerialName("description_fr") val descriptionFr: String? = null, @SerialName("description_ar") val descriptionAr: String? = null, val severity: String? = null, val category: String? = null, @SerialName("causes_fr") val causesFr: String? = null, @SerialName("causes_ar") val causesAr: String? = null, @SerialName("diagnostic_steps_fr") val stepsFr: String? = null, @SerialName("diagnostic_steps_ar") val stepsAr: String? = null, @SerialName("repair_summary_fr") val repairFr: String? = null, @SerialName("repair_summary_ar") val repairAr: String? = null)

@Serializable
data class ProfileSpec(val id: String, @SerialName("generation_id") val generationId: String, @SerialName("engine_id") val engineId: String? = null, @SerialName("trim_id") val trimId: String? = null, val key: String, @SerialName("value_text") val valueText: String? = null, @SerialName("value_number") val valueNumber: Double? = null, val unit: String? = null)

private data class ProfileBundle(val generations: List<UiGeneration>, val engines: List<UiEngine>, val images: List<ProfileImage>, val ecuLinks: List<ProfileEcuLink>, val ecus: List<ProfileEcu>, val dtcLinks: List<ProfileDtcLink>, val dtcs: List<ProfileDtc>, val specs: List<ProfileSpec>, val trims: List<UiTrim>)

@Composable
fun VehicleProfileProScreen(model: UiModel, onBack: () -> Unit) {
    var bundle by remember(model.id) { mutableStateOf<ProfileBundle?>(null) }
    var loading by remember(model.id) { mutableStateOf(true) }
    var error by remember(model.id) { mutableStateOf<String?>(null) }
    var selectedDtc by remember { mutableStateOf<ProfileDtc?>(null) }
    var selectedEcu by remember { mutableStateOf<ProfileEcu?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(model.id) {
        loading = true
        error = null
        runCatching { loadProfileBundle(model.id) }
            .onSuccess { bundle = it }
            .onFailure { error = it.message ?: "Impossible de charger le profil" }
        loading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(model.name, fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") } },
                actions = { IconButton(onClick = { scope.launch { loading = true; bundle = runCatching { loadProfileBundle(model.id) }.getOrNull(); loading = false } }) { Icon(Icons.Default.Refresh, "Actualiser") } }
            )
        }
    ) { padding ->
        when {
            loading -> LoadingProfile(padding)
            error != null -> ErrorProfile(padding, error!!, onRetry = { scope.launch { loading = true; error = null; runCatching { loadProfileBundle(model.id) }.onSuccess { bundle = it }.onFailure { error = it.message }; loading = false } })
            else -> bundle?.let { data ->
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item { ProfileHero(model, data.images, data.generations) }
                    item { ProfileQuickActions(onDiagnostic = { /* Diagnostic tab is intentionally opened from main flow */ }, onSave = { }) }
                    item { ProfileSectionTitle("Vue d'ensemble", Icons.Default.DirectionsCar) }
                    item { OverviewGrid(model, data.generations, data.engines, data.trims, data.ecus, data.dtcs) }
                    if (data.images.isNotEmpty()) {
                        item { ProfileSectionTitle("Galerie", Icons.Default.PhotoLibrary) }
                        item { ImageGallery(data.images) }
                    }
                    item { ProfileSectionTitle("Moteurs", Icons.Default.SettingsApplications) }
                    if (data.engines.isEmpty()) item { EmptyProfileCard("Aucun moteur catalogué") }
                    items(data.engines) { engine -> EngineCard(engine, data.specs.filter { it.engineId == engine.id }) }
                    item { ProfileSectionTitle("ECU & électronique", Icons.Default.Memory) }
                    if (data.ecus.isEmpty()) item { EmptyProfileCard("Aucun ECU catalogué") }
                    items(data.ecus.distinctBy { it.id }) { ecu -> EcuCard(ecu, data.ecuLinks.count { it.ecuId == ecu.id }, onClick = { selectedEcu = ecu }) }
                    item { ProfileSectionTitle("Codes défaut compatibles", Icons.Default.Warning) }
                    if (data.dtcs.isEmpty()) item { EmptyProfileCard("Aucun DTC lié à ce véhicule") }
                    items(data.dtcs.take(60)) { dtc -> DtcCard(dtc, data.dtcLinks.count { it.codeId == dtc.id }, onClick = { selectedDtc = dtc }) }
                    if (data.dtcs.size > 60) item { Text("${data.dtcs.size - 60} autres codes sont disponibles dans la base diagnostique.", modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { ProfileSectionTitle("Spécifications", Icons.Default.Tune) }
                    if (data.specs.isEmpty()) item { EmptyProfileCard("Les spécifications détaillées seront ajoutées au catalogue") }
                    items(data.specs) { spec -> SpecRow(spec) }
                    item { GuidedDiagnosisCard() }
                }
            }
        }
    }

    selectedDtc?.let { dtc -> DtcDetailSheet(dtc, onDismiss = { selectedDtc = null }) }
    selectedEcu?.let { ecu -> EcuDetailSheet(ecu, onDismiss = { selectedEcu = null }) }
}

private suspend fun loadProfileBundle(modelId: String): ProfileBundle = coroutineScope {
    val generations = async { SupabaseClient.client.from("vehicle_generations").select(Columns.list("id","model_id","name","code","year_from","year_to","body_type","platform_code","image_url")).decodeList<UiGeneration>().filter { it.modelId == modelId } }
    val images = async { SupabaseClient.client.from("vehicle_images").select(Columns.list("id","image_url","is_primary","sort_order","alt_text_fr","alt_text_ar")).decodeList<ProfileImage>() }
    val dtcLinks = async { SupabaseClient.client.from("diagnostic_code_vehicles").select(Columns.list("id","code_id","model_id","generation_id","engine_id","ecu_id","applicability","notes_fr","notes_ar")).decodeList<ProfileDtcLink>().filter { it.modelId == modelId || it.modelId == null } }
    val gens = generations.await()
    val genIds = gens.map { it.id }.toSet()
    val engines = async { SupabaseClient.client.from("vehicle_engines").select(Columns.list("id","generation_id","name","engine_code","fuel_type","displacement_cc","cylinders","aspiration","injection_type","power_hp","power_kw","torque_nm","transmission_types")).decodeList<UiEngine>().filter { it.generationId in genIds } }
    val trims = async { SupabaseClient.client.from("vehicle_trims").select(Columns.list("id","generation_id","engine_id","name","drivetrain","transmission","doors","seats","market")).decodeList<UiTrim>().filter { it.generationId in genIds } }
    val ecuLinks = async { SupabaseClient.client.from("vehicle_ecus").select(Columns.list("id","generation_id","engine_id","ecu_id","required","notes")).decodeList<ProfileEcuLink>().filter { it.generationId in genIds } }
    val specs = async { SupabaseClient.client.from("vehicle_specifications").select(Columns.list("id","generation_id","engine_id","trim_id","key","value_text","value_number","unit")).decodeList<ProfileSpec>().filter { it.generationId in genIds } }
    val eLinks = ecuLinks.await()
    val ecuIds = eLinks.map { it.ecuId }.distinct().toSet()
    val ecus = async { SupabaseClient.client.from("ecu_modules").select(Columns.list("id","manufacturer","name","family","ecu_type","protocols","part_numbers","description_fr","description_ar")).decodeList<ProfileEcu>().filter { it.id in ecuIds } }
    val links = dtcLinks.await()
    val codeIds = links.map { it.codeId }.distinct().toSet()
    val dtcs = async { SupabaseClient.client.from("diagnostic_codes").select(Columns.list("id","code","system","title_fr","title_ar","description_fr","description_ar","severity","category","causes_fr","causes_ar","diagnostic_steps_fr","diagnostic_steps_ar","repair_summary_fr","repair_summary_ar")).decodeList<ProfileDtc>().filter { it.id in codeIds } }
    ProfileBundle(gens, engines.await(), images.await().filter { it.imageUrl.isNotBlank() }.sortedBy { it.sortOrder }, eLinks, ecus.await(), links, dtcs.await().sortedBy { it.code }, specs.await(), trims.await())
}

@Composable private fun ProfileHero(model: UiModel, images: List<ProfileImage>, generations: List<UiGeneration>) {
    val hero = images.firstOrNull { it.isPrimary }?.imageUrl ?: images.firstOrNull()?.imageUrl ?: model.imageUrl
    Box(Modifier.fillMaxWidth().height(330.dp)) {
        AsyncImage(model = hero, contentDescription = model.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE071014)))))
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("VEHICLE PROFILE", color = Teal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Text(model.name, color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text(generations.joinToString(" • ") { listOfNotNull(it.name, it.yearFrom?.toString()?.let { y -> "depuis $y" }).joinToString(" ") }.ifBlank { "Catalogue CarDiag" }, color = Color.White.copy(alpha = .82f))
        }
    }
}

@Composable private fun ProfileQuickActions(onDiagnostic: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onDiagnostic, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Build, null); Spacer(Modifier.width(6.dp)); Text("Diagnostic") }
        OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.FavoriteBorder, null); Spacer(Modifier.width(6.dp)); Text("Garage") }
    }
}

@Composable private fun OverviewGrid(model: UiModel, generations: List<UiGeneration>, engines: List<UiEngine>, trims: List<UiTrim>, ecus: List<ProfileEcu>, dtcs: List<ProfileDtc>) {
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("${generations.size}", "Générations", Modifier.weight(1f)); MetricCard("${engines.size}", "Moteurs", Modifier.weight(1f)); MetricCard("${trims.size}", "Finitions", Modifier.weight(1f)) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("${ecus.size}", "ECU", Modifier.weight(1f)); MetricCard("${dtcs.size}", "DTC liés", Modifier.weight(1f)); MetricCard(if (model.yearFrom != null) "${model.yearFrom}" else "—", "Année", Modifier.weight(1f)) }
    }
}

@Composable private fun ImageGallery(images: List<ProfileImage>) {
    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(images) { image -> AsyncImage(model = image.imageUrl, contentDescription = image.altFr, modifier = Modifier.size(230.dp, 145.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop) }
    }
}

@Composable private fun EngineCard(engine: UiEngine, specs: List<ProfileSpec>) {
    Card(Modifier.padding(horizontal = 18.dp), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.SettingsApplications, null, tint = Teal); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(engine.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium); Text(engine.engineCode ?: "Code moteur non renseigné", color = MaterialTheme.colorScheme.onSurfaceVariant) }; AssistChip(onClick = {}, label = { Text(engine.fuelType.uppercase()) }) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { InfoPill("${engine.displacementCc ?: "—"} cc"); InfoPill("${engine.powerHp?.let { "%.0f hp".format(it) } ?: "—"}"); InfoPill("${engine.torqueNm?.let { "%.0f Nm".format(it) } ?: "—"}") }
            val details = listOfNotNull(engine.cylinders?.let { "Cylindres" to it.toString() }, engine.aspiration?.let { "Aspiration" to it }, engine.injectionType?.let { "Injection" to it }, engine.transmissions.takeIf { it.isNotEmpty() }?.let { "Transmission" to it.joinToString(", ") })
            details.forEach { DetailLine(it.first, it.second) }
            specs.take(4).forEach { SpecRow(it, compact = true) }
        }
    }
}

@Composable private fun EcuCard(ecu: ProfileEcu, count: Int, onClick: () -> Unit) {
    Card(Modifier.padding(horizontal = 18.dp).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Default.Memory, null, tint = Teal, modifier = Modifier.padding(12.dp)) }
            Column(Modifier.padding(start = 14.dp).weight(1f)) { Text(ecu.name, fontWeight = FontWeight.Black); Text(listOfNotNull(ecu.manufacturer, ecu.family, ecu.ecuType.uppercase()).joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall); Text("$count association(s) véhicule", color = Teal, style = MaterialTheme.typography.labelSmall) }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable private fun DtcCard(dtc: ProfileDtc, count: Int, onClick: () -> Unit) {
    val severity = dtc.severity ?: "info"
    Card(Modifier.padding(horizontal = 18.dp).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(dtc.code, color = Teal, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.width(8.dp)); SeverityChip(severity); Spacer(Modifier.weight(1f)); Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
            Text(dtc.titleFr ?: dtc.titleAr ?: "Code défaut", fontWeight = FontWeight.Bold)
            Text(dtc.descriptionFr ?: dtc.descriptionAr ?: "Description indisponible", maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun DtcDetailSheet(dtc: ProfileDtc, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text(dtc.code, color = Teal, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Spacer(Modifier.width(10.dp)); SeverityChip(dtc.severity ?: "info") } }
            item { Text(dtc.titleFr ?: "Code défaut", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
            DetailBlock("Description", dtc.descriptionFr)
            DetailBlock("Causes probables", dtc.causesFr)
            DetailBlock("Étapes de diagnostic", dtc.stepsFr)
            DetailBlock("Réparation", dtc.repairFr)
            item { Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Démarrer le diagnostic guidé") } }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable private fun EcuDetailSheet(ecu: ProfileEcu, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(ecu.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(listOfNotNull(ecu.manufacturer, ecu.family, ecu.ecuType.uppercase()).joinToString(" • "))
            DetailBlock("Protocoles", ecu.protocols.joinToString(", ").ifBlank { "Non renseigné" })
            DetailBlock("Références", ecu.partNumbers.joinToString(", ").ifBlank { "Non renseigné" })
            DetailBlock("Description", ecu.descriptionFr ?: ecu.descriptionAr)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
        }
    }
}

@Composable private fun GuidedDiagnosisCard() {
    Card(Modifier.padding(horizontal = 18.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, null, tint = Teal); Spacer(Modifier.width(8.dp)); Text("Diagnostic guidé", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge) }
            Text("Croisez véhicule + ECU + DTC + symptômes + données OBD pour obtenir un parcours de diagnostic structuré.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Bientôt disponible dans la session OBD") }
        }
    }
}

@Composable private fun ProfileSectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) { Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Teal); Spacer(Modifier.width(8.dp)); Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) } }
@Composable private fun MetricCard(value: String, label: String, modifier: Modifier) { Card(modifier, shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = Teal, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun InfoPill(text: String) { Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) } }
@Composable private fun DetailLine(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp)) } }
@Composable private fun SpecRow(spec: ProfileSpec, compact: Boolean = false) { Card(Modifier.padding(horizontal = if (compact) 0.dp else 18.dp), shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)) { Row(Modifier.fillMaxWidth().padding(if (compact) 11.dp else 14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(spec.key.replace('_', ' ').replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)); val value = spec.valueText ?: spec.valueNumber?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "—"; Text(listOfNotNull(value, spec.unit).joinToString(" "), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp)) } } }
@Composable private fun SeverityChip(severity: String) { AssistChip(onClick = {}, label = { Text(severity.uppercase()) }, leadingIcon = { Icon(if (severity == "critical" || severity == "high") Icons.Default.PriorityHigh else Icons.Default.Info, null) }) }
@Composable private fun DetailBlock(title: String, value: String?) { if (!value.isNullOrBlank()) Card(shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, color = Teal, fontWeight = FontWeight.Bold); Text(value) } } }
@Composable private fun EmptyProfileCard(text: String) { Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = Teal); Spacer(Modifier.width(10.dp)); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun LoadingProfile(padding: PaddingValues) { Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(14.dp)) { Box(Modifier.fillMaxWidth().height(300.dp).background(MaterialTheme.colorScheme.surfaceVariant)); repeat(5) { Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(70.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) } } }
@Composable private fun ErrorProfile(padding: PaddingValues, message: String, onRetry: () -> Unit) { Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)); Spacer(Modifier.height(10.dp)); Text("Impossible de charger le profil", fontWeight = FontWeight.Black); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(14.dp)); Button(onClick = onRetry) { Text("Réessayer") } } }

private val Teal get() = Color(0xFF48D7C5)
