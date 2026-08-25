package dz.cardiag.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.VehicleEngineRow
import dz.cardiag.app.core.VehicleRepository
import dz.cardiag.app.core.VehicleSpecificationRow
import dz.cardiag.app.core.VehicleYearProfile
import kotlinx.coroutines.launch

private val VPBg = Color(0xFF06090B)
private val VPSurface = Color(0xFF0D1418)
private val VPTeal = Color(0xFF48D7C5)
private val VPText = Color(0xFFF5F8F8)
private val VPMuted = Color(0xFF8B9A9F)

@Composable
fun ExactVehicleProfileScreen(model: UiModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repository = remember { VehicleRepository() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var profiles by remember { mutableStateOf<List<VehicleYearProfile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var tab by remember { mutableIntStateOf(0) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching { repository.getVehicleProfile(model.id) }
                .onSuccess { loaded -> profiles = loaded }
                .onFailure { error = it.message ?: if (rtl) "تعذر تحميل ملف السيارة" else "Impossible de charger la fiche" }
            loading = false
        }
    }

    LaunchedEffect(model.id) { load() }
    val years = profiles.map { it.year }.distinct().sortedDescending()
    val current = selectedYear?.let { y -> profiles.firstOrNull { it.year == y } } ?: profiles.firstOrNull()

    Scaffold(containerColor = VPBg) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = VPTeal) }
            error != null -> ErrorState(error ?: "", rtl, ::load, onBack)
            profiles.isEmpty() -> EmptyState(model.name, rtl, onBack)
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { VehicleHeader(model.name, current?.generation?.name, years, selectedYear, rtl, onBack) }
                item {
                    if (years.size > 1) {
                        YearSelector(years, selectedYear ?: years.first(), rtl) { selectedYear = it }
                    }
                }
                item {
                    TabRow(selectedTabIndex = tab, containerColor = VPSurface, contentColor = VPTeal) {
                        listOf(
                            if (rtl) "نظرة عامة" else "Vue d'ensemble",
                            if (rtl) "المحرك" else "Moteur",
                            if (rtl) "المواصفات" else "Spécifications",
                            if (rtl) "ECU والتشخيص" else "ECU & Diagnostic"
                        ).forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }) }
                    }
                }
                current?.let { profile ->
                    when (tab) {
                        0 -> {
                            item { YearSummary(profile, rtl) }
                            item { SectionTitle(if (rtl) "المحركات المتاحة لهذه السنة" else "Motorisations disponibles cette année") }
                            items(profile.engines, key = { it.id }) { EngineCard(it, profile.year, rtl) }
                            if (profile.trims.isNotEmpty()) {
                                item { SectionTitle(if (rtl) "الفئات والتجهيزات" else "Finitions et configurations") }
                                items(profile.trims, key = { it.id }) { TrimCard(it, rtl) }
                            }
                        }
                        1 -> {
                            item { SectionTitle(if (rtl) "المحركات المتاحة لسنة ${profile.year}" else "Motorisations disponibles en ${profile.year}") }
                            items(profile.engines, key = { it.id }) { EngineDetailCard(it, profile.year, rtl) }
                        }
                        2 -> {
                            item { SectionTitle(if (rtl) "المواصفات الكاملة لسنة ${profile.year}" else "Fiche technique complète ${profile.year}") }
                            items(profile.engines, key = { "engine-${it.id}" }) { EngineDetailCard(it, profile.year, rtl) }
                            items(profile.specifications, key = { it.id }) { SpecificationCard(it, rtl) }
                            items(profile.trims, key = { "trim-${it.id}" }) { TrimCard(it, rtl) }
                        }
                        else -> {
                            item { DiagnosticActions(rtl, context) }
                            item { SectionTitle(if (rtl) "وحدات ECU المرتبطة بالسنة ${profile.year}" else "ECU associés à l'année ${profile.year}") }
                            if (profile.ecus.isEmpty()) item { Text(if (rtl) "لا توجد بيانات ECU مؤكدة لهذه السنة." else "Aucune donnée ECU vérifiée pour cette année.", color = VPMuted) }
                            items(profile.ecus, key = { it.first.id }) { (link, ecu) ->
                                Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(16.dp)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(ecu.name, color = VPText, style = MaterialTheme.typography.titleMedium)
                                        Text(listOfNotNull(ecu.manufacturer, ecu.family, ecu.ecuType).joinToString(" • "), color = VPTeal)
                                        if (ecu.protocols.isNotEmpty()) Text("Protocoles: ${ecu.protocols.joinToString()}", color = VPMuted)
                                        if (ecu.partNumbers.isNotEmpty()) Text("Part numbers: ${ecu.partNumbers.joinToString()}", color = VPMuted)
                                        Text(if (link.required) (if (rtl) "وحدة مطلوبة" else "Module requis") else (if (rtl) "وحدة اختيارية" else "Module optionnel"), color = VPMuted)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Button(onClick = { context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (rtl) "تشخيص هذه السيارة" else "Diagnostiquer ce véhicule")
                    }
                }
                item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(if (rtl) "رجوع" else "Retour") } }
            }
        }
    }
}

@Composable private fun VehicleHeader(model: String, generation: String?, years: List<Int>, selectedYear: Int?, rtl: Boolean, onBack: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = VPText) }
                Column(Modifier.weight(1f)) {
                    Text(model, color = VPText, style = MaterialTheme.typography.headlineSmall)
                    Text(generation ?: (if (rtl) "جيل غير محدد" else "Génération non renseignée"), color = VPTeal)
                }
                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = VPTeal, modifier = Modifier.size(40.dp))
            }
            Text(if (rtl) "اختر سنة السيارة أولاً؛ كل سنة مرتبطة بجيل ومحركاتها الخاصة." else "Sélectionnez l'année : chaque année est liée à sa génération et à ses moteurs.", color = VPMuted)
            Text(if (rtl) "السنة المحددة: ${selectedYear ?: years.firstOrNull() ?: "—"}" else "Année sélectionnée : ${selectedYear ?: years.firstOrNull() ?: "—"}", color = VPText)
        }
    }
}

@Composable private fun YearSelector(years: List<Int>, selected: Int, rtl: Boolean, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (rtl) "سنة الموديل" else "Année du modèle", color = VPText, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            years.take(8).forEach { year -> FilterChip(selected = year == selected, onClick = { onSelect(year) }, label = { Text(year.toString()) }) }
        }
    }
}

@Composable private fun YearSummary(profile: VehicleYearProfile, rtl: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(if (rtl) "${profile.year} — معلومات دقيقة" else "${profile.year} — Données précises", color = VPTeal, style = MaterialTheme.typography.titleLarge)
            Text(listOfNotNull(profile.generation?.bodyType, profile.generation?.platformCode).joinToString(" • ").ifBlank { if (rtl) "جيل السيارة" else "Génération" }, color = VPMuted)
            Text(if (rtl) "${profile.engines.size} محرك • ${profile.trims.size} فئة • ${profile.specifications.size} مواصفة • ${profile.ecus.size} ECU" else "${profile.engines.size} moteurs • ${profile.trims.size} finitions • ${profile.specifications.size} spécifications • ${profile.ecus.size} ECU", color = VPText)
            profile.generation?.descriptionFr?.takeIf { !rtl }?.let { Text(it, color = VPMuted) }
            profile.generation?.descriptionAr?.takeIf { rtl }?.let { Text(it, color = VPMuted) }
        }
    }
}

@Composable private fun EngineCard(engine: VehicleEngineRow, year: Int, rtl: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(engine.name, color = VPText, style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(engine.engineCode, engine.fuelType, engine.displacementCc?.let { "${it} cc" }, engine.cylinders?.let { "${it} cyl" }).joinToString(" • "), color = VPMuted)
            Text(listOfNotNull(engine.powerHp?.let { "${it.toInt()} hp" }, engine.powerKw?.let { "${it.toInt()} kW" }, engine.torqueNm?.let { "${it.toInt()} Nm" }).joinToString(" • ").ifBlank { "—" }, color = VPTeal)
            Text(if (rtl) "متوفر لسنة $year" else "Disponible pour ${year}", color = VPMuted)
        }
    }
}

@Composable private fun EngineDetailCard(engine: VehicleEngineRow, year: Int, rtl: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(engine.name, color = VPText, style = MaterialTheme.typography.titleLarge)
            SpecLine(if (rtl) "كود المحرك" else "Code moteur", engine.engineCode)
            SpecLine(if (rtl) "الوقود" else "Carburant", engine.fuelType)
            SpecLine(if (rtl) "السعة" else "Cylindrée", engine.displacementCc?.let { "$it cc" })
            SpecLine(if (rtl) "الأسطوانات" else "Cylindres", engine.cylinders?.toString())
            SpecLine(if (rtl) "القوة" else "Puissance", engine.powerHp?.let { "${it} hp" })
            SpecLine("kW", engine.powerKw?.let { "$it kW" })
            SpecLine(if (rtl) "العزم" else "Couple", engine.torqueNm?.let { "${it} Nm" })
            SpecLine(if (rtl) "السحب" else "Aspiration", engine.aspiration)
            SpecLine(if (rtl) "الحقن" else "Injection", engine.injectionType)
            SpecLine(if (rtl) "ناقلات الحركة" else "Transmissions", engine.transmissionTypes.joinToString().ifBlank { null })
            SpecLine(if (rtl) "سنوات المحرك" else "Années moteur", "${engine.yearFrom ?: year} — ${engine.yearTo ?: year}")
            if (rtl) engine.notesAr?.let { Text(it, color = VPMuted) } else engine.notesFr?.let { Text(it, color = VPMuted) }
        }
    }
}

@Composable private fun TrimCard(trim: dz.cardiag.app.core.VehicleTrimRow, rtl: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(trim.name, color = VPText, style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(trim.code, trim.market, trim.drivetrain, trim.transmission, trim.doors?.let { "${it} portes" }, trim.seats?.let { "${it} places" }).joinToString(" • "), color = VPMuted)
            Text(if (rtl) "${trim.yearFrom ?: "—"} — ${trim.yearTo ?: "—"}" else "${trim.yearFrom ?: "—"} — ${trim.yearTo ?: "—"}", color = VPTeal)
        }
    }
}

@Composable private fun SpecificationCard(spec: VehicleSpecificationRow, rtl: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(spec.key, color = VPMuted, modifier = Modifier.weight(1f))
            Text(spec.valueText ?: spec.valueNumber?.toString()?.let { n -> listOfNotNull(n, spec.unit).joinToString(" ") } ?: "—", color = VPText, modifier = Modifier.weight(1f))
        }
    }
}

@Composable private fun SpecLine(label: String, value: String?) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = VPMuted); Text(value ?: "—", color = VPText) } }

@Composable private fun DiagnosticActions(rtl: Boolean, context: Context) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java)) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Search, contentDescription = null); Spacer(Modifier.width(4.dp)); Text(if (rtl) "تشخيص" else "Diagnostic") }
        OutlinedButton(onClick = { context.startActivity(Intent(context, ObdScannerActivity::class.java)) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Bluetooth, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("OBD-II") }
    }
}

@Composable private fun ErrorState(message: String, rtl: Boolean, onRetry: () -> Unit, onBack: () -> Unit) { Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error); Text(if (rtl) "تعذر تحميل البيانات" else "Impossible de charger la fiche", color = VPText); Text(message, color = VPMuted); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onRetry) { Text(if (rtl) "إعادة المحاولة" else "Réessayer") }; OutlinedButton(onClick = onBack) { Text(if (rtl) "رجوع" else "Retour") } } } } } }
@Composable private fun EmptyState(name: String, rtl: Boolean, onBack: () -> Unit) { Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = VPTeal, modifier = Modifier.size(48.dp)); Text(if (rtl) "لا توجد بيانات لسنة هذا الموديل" else "Aucune donnée détaillée pour ce modèle", color = VPText); Text(if (rtl) "الموديل موجود، لكن اخترنا فقط البيانات المرتبطة به وبسنته من الكتالوج." else "Le modèle existe, mais aucune fiche année/génération détaillée n'est encore liée.", color = VPMuted); OutlinedButton(onClick = onBack) { Text(if (rtl) "رجوع" else "Retour") } } } }
