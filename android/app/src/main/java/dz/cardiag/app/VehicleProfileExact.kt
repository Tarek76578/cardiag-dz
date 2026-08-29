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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    val errorFallback = stringResource(R.string.vp_loading_error)
    val loadingErrorTitle = stringResource(R.string.vp_loading_error_title)
    val loadingEmptyTitle = stringResource(R.string.vp_loading_empty_title)
    val loadingEmptyBody = stringResource(R.string.vp_loading_empty_body)
    val tabOverview = stringResource(R.string.vp_tab_overview)
    val tabEngine = stringResource(R.string.vp_tab_engine)
    val tabSpecs = stringResource(R.string.vp_tab_specs)
    val tabEcu = stringResource(R.string.vp_tab_ecu)
    val yearSelector = stringResource(R.string.vp_year_selector)
    val generationUnknown = stringResource(R.string.vp_generation_unknown)
    val yearHint = stringResource(R.string.vp_select_year_hint)
    val enginesYearFormat = stringResource(R.string.vp_section_engines_year)
    val enginesYearFullFormat = stringResource(R.string.vp_section_engines_year_full)
    val specsYearFormat = stringResource(R.string.vp_section_specs_year)
    val ecuYearFormat = stringResource(R.string.vp_section_ecu_year)
    val trimsLabel = stringResource(R.string.vp_section_trims)
    val noEcu = stringResource(R.string.vp_no_ecu)
    val ecuRequired = stringResource(R.string.vp_ecu_required)
    val ecuOptional = stringResource(R.string.vp_ecu_optional)
    val diagnoseAction = stringResource(R.string.vp_diagnose_action)
    val diagnoseButton = stringResource(R.string.vp_diagnose_button)
    val backLabel = stringResource(R.string.vp_back)
    val specDisplacement = stringResource(R.string.vp_spec_displacement)
    val specCylinders = stringResource(R.string.vp_spec_cylinders)
    val specPower = stringResource(R.string.vp_spec_power)
    val specPowerKw = stringResource(R.string.vp_spec_power_kw)
    val specTorque = stringResource(R.string.vp_spec_torque)
    val specTorqueNm = stringResource(R.string.vp_spec_torque_nm)
    val specAspiration = stringResource(R.string.vp_spec_aspiration)
    val specInjection = stringResource(R.string.vp_spec_injection)
    val specTransmissions = stringResource(R.string.vp_spec_transmissions)
    val specEngineYears = stringResource(R.string.vp_spec_engine_years)
    val yearSelectedFormat = stringResource(R.string.vp_year_selected)

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching { repository.getVehicleProfile(model.id) }
                .onSuccess { loaded -> profiles = loaded }
                .onFailure { error = it.message ?: errorFallback }
            loading = false
        }
    }

    LaunchedEffect(model.id) { load() }
    val years = profiles.map { it.year }.distinct().sortedDescending()
    val current = selectedYear?.let { y -> profiles.firstOrNull { it.year == y } } ?: profiles.firstOrNull()

    Scaffold(containerColor = VPBg) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = VPTeal) }
            error != null -> ErrorState(error ?: "", loadingErrorTitle, ::load, onBack)
            profiles.isEmpty() -> EmptyState(model.name, loadingEmptyTitle, loadingEmptyBody, onBack)
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { VehicleHeader(model.name, current?.generation?.name, years, selectedYear, yearHint, yearSelectedFormat, generationUnknown, yearSelector, onBack) }
                item {
                    if (years.size > 1) {
                        YearSelector(years, selectedYear ?: years.first(), yearSelector) { selectedYear = it }
                    }
                }
                item {
                    TabRow(selectedTabIndex = tab, containerColor = VPSurface, contentColor = VPTeal) {
                        listOf(tabOverview, tabEngine, tabSpecs, tabEcu).forEachIndexed { i, title ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                        }
                    }
                }
                current?.let { profile ->
                    when (tab) {
                        0 -> {
                            item { YearSummary(profile) }
                            item { SectionTitle(enginesYearFormat.format(profile.year)) }
                            items(profile.engines, key = { it.id }) { EngineCard(it, profile.year) }
                            if (profile.trims.isNotEmpty()) {
                                item { SectionTitle(trimsLabel) }
                                items(profile.trims, key = { it.id }) { TrimCard(it) }
                            }
                        }
                        1 -> {
                            item { SectionTitle(enginesYearFullFormat.format(profile.year)) }
                            items(profile.engines, key = { it.id }) { EngineDetailCard(it, profile.year, specDisplacement, specCylinders, specPower, specPowerKw, specTorque, specTorqueNm, specAspiration, specInjection, specTransmissions, specEngineYears) }
                        }
                        2 -> {
                            item { SectionTitle(specsYearFormat.format(profile.year)) }
                            items(profile.engines, key = { "engine-${it.id}" }) { EngineDetailCard(it, profile.year, specDisplacement, specCylinders, specPower, specPowerKw, specTorque, specTorqueNm, specAspiration, specInjection, specTransmissions, specEngineYears) }
                            items(profile.specifications, key = { it.id }) { SpecificationCard(it) }
                            items(profile.trims, key = { "trim-${it.id}" }) { TrimCard(it) }
                        }
                        else -> {
                            item { DiagnosticActions(context, diagnoseButton) }
                            item { SectionTitle(ecuYearFormat.format(profile.year)) }
                            if (profile.ecus.isEmpty()) item { Text(noEcu, color = VPMuted) }
                            items(profile.ecus, key = { it.first.id }) { (link, ecu) ->
                                Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(16.dp)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(ecu.name, color = VPText, style = MaterialTheme.typography.titleMedium)
                                        Text(listOfNotNull(ecu.manufacturer, ecu.family, ecu.ecuType).joinToString(" • "), color = VPTeal)
                                        if (ecu.protocols.isNotEmpty()) Text(stringResource(R.string.vp_ecu_protocols, ecu.protocols.joinToString()), color = VPMuted)
                                        if (ecu.partNumbers.isNotEmpty()) Text(stringResource(R.string.vp_ecu_part_numbers, ecu.partNumbers.joinToString()), color = VPMuted)
                                        Text(if (link.required) ecuRequired else ecuOptional, color = VPMuted)
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
                        Text(diagnoseAction)
                    }
                }
                item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(backLabel) } }
            }
        }
    }
}

@Composable private fun VehicleHeader(model: String, generation: String?, years: List<Int>, selectedYear: Int?, yearHint: String, yearSelectedFormat: String, generationUnknown: String, yearSelector: String, onBack: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = VPText) }
                Column(Modifier.weight(1f)) {
                    Text(model, color = VPText, style = MaterialTheme.typography.headlineSmall)
                    Text(generation ?: generationUnknown, color = VPTeal)
                }
                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = VPTeal, modifier = Modifier.size(40.dp))
            }
            Text(yearHint, color = VPMuted)
            Text(yearSelectedFormat.format(selectedYear ?: years.firstOrNull() ?: "—"), color = VPText)
        }
    }
}

@Composable private fun YearSelector(years: List<Int>, selected: Int, yearSelector: String, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(yearSelector, color = VPText, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            years.take(8).forEach { year -> FilterChip(selected = year == selected, onClick = { onSelect(year) }, label = { Text(year.toString()) }) }
        }
    }
}

@Composable private fun YearSummary(profile: VehicleYearProfile) {
    Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.guided_subtitle), color = VPMuted, style = MaterialTheme.typography.bodySmall)
            Text(profile.year.toString(), color = VPText, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable private fun EngineCard(engine: VehicleEngineRow, year: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(engine.name, color = VPText, style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(engine.engineCode, engine.fuelType, engine.displacementCc?.let { "$it cc" }, engine.cylinders?.let { "$it cyl" }, engine.aspiration).joinToString(" • "), color = VPMuted)
            Text(stringResource(R.string.profile_engine_year, year), color = VPTeal)
        }
    }
}

@Composable private fun EngineDetailCard(
    engine: VehicleEngineRow,
    year: Int,
    specDisplacement: String,
    specCylinders: String,
    specPower: String,
    specPowerKw: String,
    specTorque: String,
    specTorqueNm: String,
    specAspiration: String,
    specInjection: String,
    specTransmissions: String,
    specEngineYears: String
) {
    Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(engine.name, color = VPText, style = MaterialTheme.typography.titleMedium)
            SpecLine(specDisplacement, engine.displacementCc?.let { "$it cc" })
            SpecLine(specCylinders, engine.cylinders?.toString())
            SpecLine(specPower, engine.powerHp?.let { "$it ch" })
            SpecLine(specPower, engine.powerKw?.let { "$it $specPowerKw" })
            SpecLine(specTorque, engine.torqueNm?.let { "$it $specTorqueNm" })
            SpecLine(specAspiration, engine.aspiration)
            SpecLine(specInjection, engine.injectionType)
            SpecLine(specTransmissions, engine.transmissionTypes.joinToString().ifBlank { null })
            SpecLine(specEngineYears, "${engine.yearFrom ?: year} — ${engine.yearTo ?: year}")
            engine.notesFr?.let { Text(it, color = VPMuted) }
        }
    }
}

@Composable private fun TrimCard(trim: dz.cardiag.app.core.VehicleTrimRow) {
    Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(trim.name, color = VPText, style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(trim.code, trim.market, trim.drivetrain, trim.transmission, trim.doors?.let { "$it portes" }, trim.seats?.let { "$it places" }).joinToString(" • "), color = VPMuted)
            Text("${trim.yearFrom ?: "—"} — ${trim.yearTo ?: "—"}", color = VPTeal)
        }
    }
}

@Composable private fun SpecificationCard(spec: VehicleSpecificationRow) {
    Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(spec.key, color = VPMuted, modifier = Modifier.weight(1f))
            Text(spec.valueText ?: spec.valueNumber?.toString()?.let { n -> listOfNotNull(n, spec.unit).joinToString(" ") } ?: "—", color = VPText, modifier = Modifier.weight(1f))
        }
    }
}

@Composable private fun SpecLine(label: String, value: String?) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = VPMuted); Text(value ?: "—", color = VPText) } }

@Composable private fun DiagnosticActions(context: Context, diagnoseButton: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java)) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Search, contentDescription = null); Spacer(Modifier.width(4.dp)); Text(diagnoseButton) }
        OutlinedButton(onClick = { context.startActivity(Intent(context, ObdScannerActivity::class.java)) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Bluetooth, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("OBD-II") }
    }
}

@Composable private fun ErrorState(message: String, title: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(title, color = VPText)
                Text(message, color = VPMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry) { Text(stringResource(R.string.vp_loading_error_retry)) }
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.vp_loading_error_back)) }
                }
            }
        }
    }
}

@Composable private fun SectionTitle(title: String) {
    Text(title, color = VPText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable private fun EmptyState(name: String, title: String, body: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = VPTeal, modifier = Modifier.size(48.dp))
            Text(title, color = VPText)
            Text(body, color = VPMuted)
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.vp_loading_empty_back)) }
        }
    }
}
