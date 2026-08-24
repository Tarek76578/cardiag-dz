package dz.cardiag.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
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
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProImage(
    val id: String,
    @SerialName("model_id") val modelId: String? = null,
    @SerialName("generation_id") val generationId: String? = null,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("alt_text_fr") val altFr: String? = null,
    @SerialName("is_primary") val primary: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0
)

@Serializable
data class ProSpec(
    val id: String,
    @SerialName("generation_id") val generationId: String? = null,
    @SerialName("engine_id") val engineId: String? = null,
    @SerialName("trim_id") val trimId: String? = null,
    val key: String,
    @SerialName("value_text") val valueText: String? = null,
    @SerialName("value_number") val valueNumber: Double? = null,
    val unit: String? = null
)

@Serializable
data class ProEcu(
    val id: String,
    @SerialName("generation_id") val generationId: String? = null,
    @SerialName("engine_id") val engineId: String? = null,
    @SerialName("ecu_id") val ecuId: String,
    val required: Boolean = false,
    @SerialName("year_from") val yearFrom: Int? = null,
    @SerialName("year_to") val yearTo: Int? = null,
    val notes: String? = null,
    val ecu: ProEcuModule? = null
)

@Serializable
data class ProEcuModule(
    val id: String,
    val manufacturer: String? = null,
    val name: String? = null,
    val family: String? = null,
    @SerialName("ecu_type") val ecuType: String? = null,
    val protocols: List<String> = emptyList(),
    @SerialName("description_fr") val descriptionFr: String? = null
)

@Serializable
data class ProDtcLink(
    val id: String,
    @SerialName("generation_id") val generationId: String? = null,
    @SerialName("engine_id") val engineId: String? = null,
    val applicability: String? = null,
    @SerialName("notes_fr") val notesFr: String? = null,
    val code: ProDtcCode? = null
)

@Serializable
data class ProDtcCode(
    val id: String,
    val code: String,
    val system: String? = null,
    @SerialName("title_fr") val titleFr: String? = null,
    @SerialName("description_fr") val descriptionFr: String? = null,
    val severity: String? = null,
    val category: String? = null,
    @SerialName("causes_fr") val causesFr: String? = null,
    @SerialName("diagnostic_steps_fr") val diagnosticStepsFr: String? = null,
    @SerialName("repair_summary_fr") val repairSummaryFr: String? = null
)

@Composable
fun VehicleProfileProScreen(model: UiModel, onBack: () -> Unit) {
    var generations by remember { mutableStateOf<List<UiGeneration>>(emptyList()) }
    var engines by remember { mutableStateOf<List<UiEngine>>(emptyList()) }
    var trims by remember { mutableStateOf<List<UiTrim>>(emptyList()) }
    var images by remember { mutableStateOf<List<ProImage>>(emptyList()) }
    var specs by remember { mutableStateOf<List<ProSpec>>(emptyList()) }
    var ecus by remember { mutableStateOf<List<ProEcu>>(emptyList()) }
    var dtcs by remember { mutableStateOf<List<ProDtcLink>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedGeneration by remember { mutableStateOf<UiGeneration?>(null) }
    var selectedEngine by remember { mutableStateOf<UiEngine?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(model.id) {
        scope.launch {
            runCatching {
                SupabaseClient.client.from("vehicle_generations").select(
                    Columns.list("id","model_id","name","code","year_from","year_to","body_type","platform_code","image_url")
                ).decodeList<UiGeneration>()
            }.onSuccess { list ->
                generations = list.filter { it.modelId == model.id }
                selectedGeneration = generations.firstOrNull()
            }
            runCatching {
                SupabaseClient.client.from("vehicle_engines").select(
                    Columns.list("id","generation_id","name","engine_code","fuel_type","displacement_cc","cylinders","power_hp","power_kw","torque_nm","transmission_types")
                ).decodeList<UiEngine>()
            }.onSuccess { engines = it }
            runCatching {
                SupabaseClient.client.from("vehicle_trims").select(
                    Columns.list("id","generation_id","engine_id","name","drivetrain","transmission","doors","seats","market")
                ).decodeList<UiTrim>()
            }.onSuccess { trims = it }
            runCatching {
                SupabaseClient.client.from("vehicle_images").select(
                    Columns.list("id","model_id","generation_id","image_url","alt_text_fr","is_primary","sort_order")
                ).decodeList<ProImage>()
            }.onSuccess { images = it.filter { x -> x.modelId == model.id || generations.any { g -> g.id == x.generationId } }.sortedBy { it.sortOrder } }
            runCatching {
                SupabaseClient.client.from("vehicle_specifications").select(
                    Columns.list("id","generation_id","engine_id","trim_id","key","value_text","value_number","unit")
                ).decodeList<ProSpec>()
            }.onSuccess { specs = it }
            runCatching {
                SupabaseClient.client.from("vehicle_ecus").select(
                    Columns.raw("id,generation_id,engine_id,ecu_id,required,year_from,year_to,notes,ecu_modules(id,manufacturer,name,family,ecu_type,protocols,description_fr)")
                ).decodeList<ProEcu>()
            }.onSuccess { ecus = it }
            runCatching {
                SupabaseClient.client.from("diagnostic_code_vehicles").select(
                    Columns.raw("id,generation_id,engine_id,applicability,notes_fr,diagnostic_codes(id,code,system,title_fr,description_fr,severity,category,causes_fr,diagnostic_steps_fr,repair_summary_fr)")
                ).decodeList<ProDtcLink>()
            }.onSuccess { dtcs = it }
            loading = false
        }
    }

    val generation = selectedGeneration
    val generationEngines = engines.filter { generation?.id == it.generationId }
    val generationTrims = trims.filter { generation?.id == it.generationId }
    val generationImages = images.filter { it.generationId == generation?.id || it.modelId == model.id }
    val generationSpecs = specs.filter { generation?.id == it.generationId && (it.engineId == null || it.engineId == selectedEngine?.id) }
    val generationEcus = ecus.filter { generation?.id == it.generationId && (selectedEngine == null || it.engineId == null || it.engineId == selectedEngine?.id) }
    val generationDtcs = dtcs.filter { generation?.id == it.generationId && (selectedEngine == null || it.engineId == null || it.engineId == selectedEngine?.id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(model.name, fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") } },
                actions = { IconButton(onClick = {}) { Icon(Icons.Default.Share, "Partager") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { ProHero(model, generationImages.firstOrNull { it.primary } ?: generationImages.firstOrNull()) }
            item { ProQuickStats(model, generations.size, generationEngines.size, generationEcus.size, generationDtcs.size) }

            item { ProSectionTitle("Générations", Icons.Default.Timeline) }
            if (loading) items(2) { ProSkeleton() }
            else if (generations.isEmpty()) item { ProEmpty("Aucune génération disponible") }
            else items(generations) { g ->
                ProGenerationCard(g, g.id == generation?.id) {
                    selectedGeneration = g
                    selectedEngine = engines.firstOrNull { it.generationId == g.id }
                }
            }

            item { ProSectionTitle("Galerie", Icons.Default.PhotoLibrary) }
            if (generationImages.isEmpty()) item { ProEmpty("Aucune image disponible", "Ajoutez des images officielles ou correctement licenciées au catalogue.") }
            else item {
                LazyRow(contentPadding = PaddingValues(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(generationImages) { image ->
                        AsyncImage(model = image.imageUrl, contentDescription = image.altFr ?: model.name, modifier = Modifier.size(230.dp, 145.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
                    }
                }
            }

            item { ProSectionTitle("Moteur & performances", Icons.Default.Settings) }
            if (generationEngines.isEmpty()) item { ProEmpty("Aucune motorisation pour cette génération") }
            else items(generationEngines) { engine ->
                ProEngineCard(engine, engine.id == selectedEngine?.id) { selectedEngine = engine }
            }

            item { ProSectionTitle("ECU & électronique", Icons.Default.Memory) }
            if (generationEcus.isEmpty()) item { ProEmpty("Aucun ECU associé", "Les modules apparaîtront dès qu'ils sont liés à cette génération.") }
            else items(generationEcus) { ProEcuCard(it) }

            item { ProSectionTitle("Codes défaut DTC", Icons.Default.Warning) }
            if (generationDtcs.isEmpty()) item { ProEmpty("Aucun DTC spécifique", "Les codes génériques OBD-II restent disponibles dans le scanner.") }
            else items(generationDtcs.take(30)) { ProDtcCard(it) }

            item { ProSectionTitle("Spécifications techniques", Icons.Default.List) }
            if (generationSpecs.isEmpty()) item { ProEmpty("Aucune spécification détaillée") }
            else items(generationSpecs.groupBy { it.key }.toList()) { (key, values) -> ProSpecCard(key, values) }

            item { ProSectionTitle("Finitions", Icons.Default.DirectionsCar) }
            if (generationTrims.isEmpty()) item { ProEmpty("Aucune finition disponible") }
            else items(generationTrims) { ProTrimCard(it) }

            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Prêt pour le diagnostic ?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text("Le véhicule sélectionné sera utilisé pour contextualiser les DTC, ECU et mesures OBD.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = {}, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.Bolt, null); Spacer(Modifier.width(8.dp)); Text("Lancer le diagnostic")
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun ProHero(model: UiModel, image: ProImage?) {
    Box(Modifier.fillMaxWidth().height(270.dp).background(Brush.verticalGradient(listOf(Color(0xFF17343C), Color(0xFF071014))))) {
        if (image != null) AsyncImage(model = image.imageUrl, contentDescription = model.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .92f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("VEHICLE PROFILE", color = Teal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(model.name, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Catalogue technique • CarDiag DZ", color = Color.White.copy(alpha = .72f))
        }
    }
}

@Composable private fun ProQuickStats(model: UiModel, generations: Int, engines: Int, ecus: Int, dtcs: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProStat("$generations", "Générations", Modifier.weight(1f))
        ProStat("$engines", "Moteurs", Modifier.weight(1f))
        ProStat("$ecus", "ECU", Modifier.weight(1f))
        ProStat("$dtcs", "DTC", Modifier.weight(1f))
    }
}

@Composable private fun ProStat(value: String, label: String, modifier: Modifier) { Card(modifier, shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = Teal, fontWeight = FontWeight.Black); Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) } } }
@Composable private fun ProSectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) { Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Teal); Spacer(Modifier.width(8.dp)); Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) } }

@Composable private fun ProGenerationCard(g: UiGeneration, selected: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = g.imageUrl, contentDescription = g.name, modifier = Modifier.size(88.dp, 64.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.padding(start = 12.dp).weight(1f)) { Text(g.name, fontWeight = FontWeight.Black); Text(listOfNotNull(g.yearFrom, g.yearTo).joinToString(" – ").ifBlank { g.code ?: "—" }, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(listOfNotNull(g.bodyType, g.platformCode).joinToString(" • "), color = Teal, style = MaterialTheme.typography.labelSmall) }
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = Teal)
        }
    }
}

@Composable private fun ProEngineCard(e: UiEngine, selected: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(e.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); AssistChip(onClick = {}, label = { Text(e.fuelType.uppercase()) }) }
            Text(e.engineCode ?: "Code moteur non renseigné", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ProMetric("Cylindrée", e.displacementCc?.let { "$it cc" } ?: "—")
                ProMetric("Puissance", e.powerHp?.let { "${it.toInt()} ch" } ?: "—")
                ProMetric("Couple", e.torqueNm?.let { "${it.toInt()} Nm" } ?: "—")
            }
            if (e.transmissions.isNotEmpty()) Text("Transmission: ${e.transmissions.joinToString(" • ")}", color = Teal, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun ProMetric(label: String, value: String) { Surface(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.Bold) } } }

@Composable private fun ProEcuCard(item: ProEcu) {
    val e = item.ecu
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(e?.name ?: "ECU ${item.ecuId.take(8)}", fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); if (item.required) AssistChip(onClick = {}, label = { Text("REQUIRED") }) }
            Text(listOfNotNull(e?.manufacturer, e?.family, e?.ecuType).joinToString(" • ").ifBlank { "Module électronique" }, color = Teal)
            if (!e?.protocols.isNullOrEmpty()) Text("Protocoles: ${e?.protocols?.joinToString(", ")}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (!item.notes.isNullOrBlank()) Text(item.notes!!, style = MaterialTheme.typography.bodySmall)
            if (!e?.descriptionFr.isNullOrBlank()) Text(e?.descriptionFr ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun ProDtcCard(item: ProDtcLink) {
    val d = item.code
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(d?.code ?: "DTC", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Teal); Spacer(Modifier.width(10.dp)); Text(d?.severity ?: "", style = MaterialTheme.typography.labelSmall); Spacer(Modifier.weight(1f)); Text(d?.system ?: "OBD-II", style = MaterialTheme.typography.labelSmall) }
            Text(d?.titleFr ?: "Code défaut", fontWeight = FontWeight.Bold)
            if (!d?.descriptionFr.isNullOrBlank()) Text(d?.descriptionFr ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!d?.causesFr.isNullOrBlank()) Text("Causes: ${d?.causesFr}", style = MaterialTheme.typography.bodySmall)
            if (!d?.diagnosticStepsFr.isNullOrBlank()) Text("Diagnostic: ${d?.diagnosticStepsFr}", style = MaterialTheme.typography.bodySmall)
            if (!d?.repairSummaryFr.isNullOrBlank()) Text("Réparation: ${d?.repairSummaryFr}", style = MaterialTheme.typography.bodySmall, color = Teal)
        }
    }
}

@Composable private fun ProSpecCard(key: String, values: List<ProSpec>) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(key.replace('_', ' ').replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)
            values.forEach { spec -> Text(listOfNotNull(spec.valueText, spec.valueNumber?.toString(), spec.unit).joinToString(" ").ifBlank { "—" }, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable private fun ProTrimCard(t: UiTrim) { Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(t.name, fontWeight = FontWeight.Black); Text(listOfNotNull(t.drivetrain, t.transmission, t.market).joinToString(" • ").ifBlank { "Spécifications" }, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(listOfNotNull(t.doors?.let { "$it portes" }, t.seats?.let { "$it places" }).joinToString(" • "), color = Teal, style = MaterialTheme.typography.labelSmall) } } }
@Composable private fun ProEmpty(title: String, subtitle: String = "Les données seront affichées dès qu'elles sont disponibles.") { Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Info, null, tint = Teal); Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } } }
@Composable private fun ProSkeleton() { Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Box(Modifier.fillMaxWidth().height(80.dp).background(MaterialTheme.colorScheme.surfaceVariant)) } }
