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
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable private data class XGeneration(val id: String, @SerialName("model_id") val modelId: String? = null, val name: String? = null, @SerialName("year_from") val yearFrom: Int? = null, @SerialName("year_to") val yearTo: Int? = null, @SerialName("image_url") val imageUrl: String? = null)
@Serializable private data class XImage(val id: String, @SerialName("model_id") val modelId: String, @SerialName("generation_id") val generationId: String? = null, @SerialName("image_url") val imageUrl: String, @SerialName("alt_text_fr") val altFr: String? = null, @SerialName("alt_text_ar") val altAr: String? = null, @SerialName("is_primary") val primary: Boolean = false, @SerialName("sort_order") val order: Int = 0)
@Serializable private data class XEngine(val id: String, @SerialName("generation_id") val generationId: String, val name: String? = null, @SerialName("engine_code") val code: String? = null, @SerialName("fuel_type") val fuel: String? = null, @SerialName("displacement_cc") val cc: Int? = null, val cylinders: Int? = null, val aspiration: String? = null, @SerialName("injection_type") val injection: String? = null, @SerialName("power_hp") val hp: Double? = null, @SerialName("power_kw") val kw: Double? = null, @SerialName("torque_nm") val torque: Double? = null, @SerialName("transmission_types") val transmissions: List<String> = emptyList(), @SerialName("year_from") val yearFrom: Int? = null, @SerialName("year_to") val yearTo: Int? = null)
@Serializable private data class XEcuLink(val id: String, @SerialName("generation_id") val generationId: String, @SerialName("engine_id") val engineId: String? = null, @SerialName("ecu_id") val ecuId: String, val required: Boolean = false, val notes: String? = null)
@Serializable private data class XEcu(val id: String, val manufacturer: String? = null, val name: String? = null, val family: String? = null, @SerialName("ecu_type") val type: String? = null, val protocols: List<String> = emptyList(), @SerialName("description_fr") val description: String? = null)
@Serializable private data class XSpec(val id: String, @SerialName("generation_id") val generationId: String? = null, @SerialName("engine_id") val engineId: String? = null, val key: String, @SerialName("value_text") val text: String? = null, @SerialName("value_number") val number: Double? = null, val unit: String? = null)
@Serializable private data class XDtcLink(@SerialName("code_id") val codeId: String, @SerialName("model_id") val modelId: String? = null, @SerialName("generation_id") val generationId: String? = null)
@Serializable private data class XDtc(val id: String, val code: String, val system: String? = null, @SerialName("title_fr") val title: String? = null, @SerialName("description_fr") val description: String? = null, val severity: String? = null, @SerialName("causes_fr") val causes: String? = null, @SerialName("diagnostic_steps_fr") val steps: String? = null, @SerialName("repair_summary_fr") val repair: String? = null)

private val XBg = Color(0xFF06090B)
private val XSurface = Color(0xFF0D1418)
private val XSurface2 = Color(0xFF131D22)
private val XTeal = Color(0xFF48D7C5)
private val XSoft = Color(0xFF153F3B)
private val XText = Color(0xFFF5F8F8)
private val XMuted = Color(0xFF8B9A9F)

@Composable
fun ExactVehicleProfileScreen(model: UiModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var generations by remember { mutableStateOf(emptyList<XGeneration>()) }
    var images by remember { mutableStateOf(emptyList<XImage>()) }
    var engines by remember { mutableStateOf(emptyList<XEngine>()) }
    var ecuLinks by remember { mutableStateOf(emptyList<XEcuLink>()) }
    var ecus by remember { mutableStateOf(emptyList<XEcu>()) }
    var specs by remember { mutableStateOf(emptyList<XSpec>()) }
    var dtcs by remember { mutableStateOf(emptyList<XDtc>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var section by rememberSaveable { mutableIntStateOf(0) }
    var selectedDtc by remember { mutableStateOf<XDtc?>(null) }

    fun load() {
        scope.launch {
            loading = true; error = null
            runCatching {
                val gs = SupabaseClient.client.from("vehicle_generations").select(Columns.list("id", "model_id", "name", "year_from", "year_to", "image_url")).decodeList<XGeneration>().filter { it.modelId == model.id }
                val gids = gs.map { it.id }.toSet()
                val ims = SupabaseClient.client.from("vehicle_images").select(Columns.list("id", "model_id", "generation_id", "image_url", "alt_text_fr", "alt_text_ar", "is_primary", "sort_order")).decodeList<XImage>().filter { it.modelId == model.id && it.imageUrl.isNotBlank() }.sortedWith(compareByDescending<XImage> { it.primary }.thenBy { it.order })
                val es = if (gids.isEmpty()) emptyList() else SupabaseClient.client.from("vehicle_engines").select(Columns.list("id", "generation_id", "name", "engine_code", "fuel_type", "displacement_cc", "cylinders", "aspiration", "injection_type", "power_hp", "power_kw", "torque_nm", "transmission_types", "year_from", "year_to")).decodeList<XEngine>().filter { it.generationId in gids }
                val eids = es.map { it.id }.toSet()
                val links = if (gids.isEmpty()) emptyList() else SupabaseClient.client.from("vehicle_ecus").select(Columns.list("id", "generation_id", "engine_id", "ecu_id", "required", "notes")).decodeList<XEcuLink>().filter { it.generationId in gids && (it.engineId == null || it.engineId in eids) }
                val ecuIds = links.map { it.ecuId }.distinct()
                val erows = if (ecuIds.isEmpty()) emptyList() else SupabaseClient.client.from("ecu_modules").select(Columns.list("id", "manufacturer", "name", "family", "ecu_type", "protocols", "description_fr")).decodeList<XEcu>().filter { it.id in ecuIds }
                val ss = if (gids.isEmpty()) emptyList() else SupabaseClient.client.from("vehicle_specifications").select(Columns.list("id", "generation_id", "engine_id", "key", "value_text", "value_number", "unit")).decodeList<XSpec>().filter { it.generationId in gids || it.engineId in eids }
                val dl = SupabaseClient.client.from("diagnostic_code_vehicles").select(Columns.list("code_id", "model_id", "generation_id")).decodeList<XDtcLink>().filter { it.modelId == model.id || it.generationId in gids }
                val dids = dl.map { it.codeId }.distinct()
                val ds = if (dids.isEmpty()) emptyList() else SupabaseClient.client.from("diagnostic_codes").select(Columns.list("id", "code", "system", "title_fr", "description_fr", "severity", "causes_fr", "diagnostic_steps_fr", "repair_summary_fr")).decodeList<XDtc>().filter { it.id in dids }.sortedBy { it.code }
                generations = gs; images = ims; engines = es; ecuLinks = links; ecus = erows; specs = ss; dtcs = ds
            }.onFailure { error = it.message ?: "Erreur Supabase" }
            loading = false
        }
    }
    LaunchedEffect(model.id) { load() }

    val hero = images.firstOrNull { it.primary }?.imageUrl ?: images.firstOrNull()?.imageUrl ?: model.imageUrl
    val tabs = listOf("Overview", "Engine", "Specs", "ECU & OBD", "DTC")

    Scaffold(containerColor = XBg) { p ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(p), Alignment.Center) { CircularProgressIndicator(color = XTeal) }
        } else if (error != null) {
            Column(Modifier.fillMaxSize().padding(p).padding(24.dp), Alignment.CenterHorizontally, Arrangement.Center) {
                Text("Impossible de charger la fiche", color = XText, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp)); Text(error!!, color = XMuted)
                Spacer(Modifier.height(16.dp)); Button(onClick = { load() }) { Text("Réessayer") }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(p), contentPadding = PaddingValues(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().height(365.dp)) {
                        if (hero != null) AsyncImage(hero, model.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, XBg.copy(alpha = .98f)))))
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour", tint = XText) }
                            Spacer(Modifier.weight(1f)); IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, "Actualiser", tint = XText) }
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                            Text("VEHICLE PROFILE", color = XTeal, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                            Text(model.name, color = XText, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                            Text("${generations.size} générations • ${engines.size} moteurs • ${ecus.size} ECU • ${dtcs.size} DTC", color = XMuted)
                        }
                    }
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tabs) { label ->
                            val i = tabs.indexOf(label)
                            Surface(Modifier.clip(RoundedCornerShape(15.dp)).clickable { section = i }, color = if (section == i) XTeal else XSurface) {
                                Text(label, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), color = if (section == i) XBg else XText, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item {
                    when (section) {
                        0 -> OverviewBlock(model, generations, engines, ecus, dtcs)
                        1 -> EngineBlock(engines)
                        2 -> SpecsBlock(specs)
                        3 -> EcuBlock(ecus, ecuLinks)
                        else -> DtcBlock(dtcs) { selectedDtc = it }
                    }
                }
                item {
                    Button(
                        onClick = { context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java).apply { putExtra("model_id", model.id); putExtra("model_name", model.name) }) },
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(17.dp),
                        contentPadding = PaddingValues(vertical = 15.dp)
                    ) { Icon(Icons.Default.Build, null); Spacer(Modifier.width(8.dp)); Text("Lancer le diagnostic", fontWeight = FontWeight.Black) }
                }
            }
        }
    }

    selectedDtc?.let { d ->
        AlertDialog(
            onDismissRequest = { selectedDtc = null },
            containerColor = XSurface,
            title = { Text(d.code, color = XText, fontWeight = FontWeight.Black) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) { Text(d.title ?: d.description ?: "Code défaut", color = XText); d.system?.let { Text("Système • $it", color = XMuted) }; d.causes?.let { Text("Causes\n$it", color = XMuted) }; d.steps?.let { Text("Diagnostic\n$it", color = XMuted) }; d.repair?.let { Text("Réparation\n$it", color = XMuted) } } },
            confirmButton = { Button(onClick = { selectedDtc = null }) { Text("Fermer") } }
        )
    }
}

@Composable private fun OverviewBlock(model: UiModel, generations: List<XGeneration>, engines: List<XEngine>, ecus: List<XEcu>, dtcs: List<XDtc>) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Vehicle overview", color = XText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            XInfo("GENERATIONS", generations.size.toString(), Modifier.weight(1f))
            XInfo("ENGINES", engines.size.toString(), Modifier.weight(1f))
            XInfo("DTC", dtcs.size.toString(), Modifier.weight(1f))
        }
        XInfo("MODEL ID", model.id, Modifier.fillMaxWidth())
        if (generations.isNotEmpty()) XGenerationStrip(generations)
        Text("Diagnostic coverage", color = XMuted, fontWeight = FontWeight.Bold)
        Text("${ecus.size} ECU compatibles et ${dtcs.size} codes défaut associés à ce modèle.", color = XText)
    }
}

@Composable private fun XGenerationStrip(rows: List<XGeneration>) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("GENERATIONS", color = XMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(rows.take(10)) { g -> Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = XSurface)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { g.imageUrl?.let { AsyncImage(it, g.name, Modifier.size(76.dp, 54.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) }; Spacer(Modifier.width(9.dp)); Column { Text(g.name ?: "Generation", color = XText, fontWeight = FontWeight.Bold); Text(listOfNotNull(g.yearFrom?.toString(), g.yearTo?.toString()).joinToString(" – "), color = XMuted) } } } } }
    }
}

@Composable private fun EngineBlock(rows: List<XEngine>) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Motorisations", color = XText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        if (rows.isEmpty()) XEmpty("Aucune donnée moteur disponible.")
        rows.forEach { e ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = XSurface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(e.name ?: e.code ?: "Moteur", color = XText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(listOfNotNull(e.code, e.fuel, e.cc?.let { "${it} cc" }, e.cylinders?.let { "${it} cyl." }).joinToString(" • "), color = XMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        XMetric("PUISSANCE", e.hp?.let { "${it} ch" } ?: "—", Modifier.weight(1f))
                        XMetric("COUPLE", e.torque?.let { "${it} Nm" } ?: "—", Modifier.weight(1f))
                    }
                    Text(listOfNotNull(e.aspiration, e.injection, e.transmissions.joinToString(" / ").takeIf { it.isNotBlank() }).joinToString(" • "), color = XMuted)
                }
            }
        }
    }
}

@Composable private fun SpecsBlock(rows: List<XSpec>) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Fiche technique", color = XText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        if (rows.isEmpty()) XEmpty("Les spécifications détaillées ne sont pas encore disponibles pour ce modèle.")
        rows.take(120).forEach { s ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = XSurface)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Text(s.key, color = XMuted, modifier = Modifier.weight(1f)); Text(listOfNotNull(s.text, s.number?.toString()).joinToString(" ") + (s.unit?.let { " $it" } ?: ""), color = XText, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable private fun EcuBlock(ecus: List<XEcu>, links: List<XEcuLink>) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ECU & OBD", color = XText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        if (ecus.isEmpty()) XEmpty("Aucun calculateur détaillé disponible.")
        ecus.forEach { e ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = XSurface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(45.dp).clip(RoundedCornerShape(14.dp)).background(XSoft), Alignment.Center) { Icon(Icons.Default.Memory, null, tint = XTeal) }; Spacer(Modifier.width(12.dp)); Column { Text(e.name ?: "ECU", color = XText, fontWeight = FontWeight.Black); Text(listOfNotNull(e.manufacturer, e.family, e.type).joinToString(" • "), color = XMuted) } }
                    if (e.protocols.isNotEmpty()) Text("Protocoles • ${e.protocols.joinToString(" • ")}", color = XTeal, fontWeight = FontWeight.Bold)
                    e.description?.let { Text(it, color = XMuted) }
                    Text(if (links.any { it.ecuId == e.id && it.required }) "✓ Requis pour cette configuration" else "Compatible avec la configuration", color = XText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable private fun DtcBlock(rows: List<XDtc>, onSelect: (XDtc) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("DTC & Faults", color = XText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); Text(rows.size.toString(), color = XTeal, fontWeight = FontWeight.Black) }
        if (rows.isEmpty()) XEmpty("Aucun DTC associé à ce modèle.")
        rows.take(100).forEach { d ->
            Card(Modifier.fillMaxWidth().clickable { onSelect(d) }, shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = XSurface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(XSoft), Alignment.Center) { Icon(Icons.Default.Warning, null, tint = XTeal) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(d.code, color = XText, fontWeight = FontWeight.Black); Text(d.title ?: d.description ?: "Code défaut", color = XMuted, maxLines = 2) }; Text(d.severity ?: "INFO", color = XTeal, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable private fun XInfo(label: String, value: String, modifier: Modifier) { Card(modifier, shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = XSurface)) { Column(Modifier.padding(15.dp)) { Text(label, color = XMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black); Spacer(Modifier.height(4.dp)); Text(value, color = XText, fontWeight = FontWeight.Black) } } }
@Composable private fun XMetric(label: String, value: String, modifier: Modifier) { Surface(modifier, color = XSurface2, shape = RoundedCornerShape(15.dp)) { Column(Modifier.padding(12.dp)) { Text(label, color = XMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black); Text(value, color = XTeal, fontWeight = FontWeight.Black) } } }
@Composable private fun XEmpty(text: String) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = XSurface)) { Text(text, Modifier.padding(18.dp), color = XMuted) } }
