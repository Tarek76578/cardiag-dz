package dz.cardiag.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import dz.cardiag.app.core.CanonicalVehicleRow
import dz.cardiag.app.core.VehicleRepository
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
    var rows by remember { mutableStateOf<List<CanonicalVehicleRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var tab by remember { mutableIntStateOf(0) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching { repository.getModelYearVehicles(model.name) }
                .onSuccess { rows = it.sortedWith(compareByDescending<CanonicalVehicleRow> { row -> row.modelYear }.thenBy { row -> row.engineName ?: "" }) }
                .onFailure { error = it.message ?: if (rtl) "تعذر تحميل بيانات السيارة" else "Impossible de charger la fiche" }
            loading = false
        }
    }

    LaunchedEffect(model.id, model.name) { load() }
    val years = rows.map { it.modelYear }.distinct().sortedDescending()
    val filtered = selectedYear?.let { year -> rows.filter { it.modelYear == year } } ?: rows
    val first = rows.firstOrNull()
    val make = first?.makeName ?: model.name

    Scaffold(containerColor = VPBg) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = VPTeal) }
            error != null -> ErrorState(error ?: "", rtl, onRetry = ::load, onBack = onBack)
            rows.isEmpty() -> EmptyState(model.name, rtl, onBack)
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Header(
                        make = make,
                        model = first?.modelName ?: model.name,
                        years = years,
                        rtl = rtl,
                        onBack = onBack,
                        onDiagnose = { context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java)) }
                    )
                }
                item {
                    TabRow(selectedTabIndex = tab, containerColor = VPSurface, contentColor = VPTeal) {
                        listOf(
                            if (rtl) "نظرة عامة" else "Vue d'ensemble",
                            if (rtl) "المحرك" else "Moteur",
                            if (rtl) "المواصفات" else "Spécifications",
                            if (rtl) "التشخيص" else "Diagnostic"
                        ).forEachIndexed { index, title ->
                            Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                        }
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { YearChip(if (rtl) "الكل" else "Tous", selectedYear == null) { selectedYear = null } }
                        items(years) { year -> YearChip(year.toString(), selectedYear == year) { selectedYear = year } }
                    }
                }
                when (tab) {
                    0 -> {
                        item { SummaryCard(first, rtl, rows.size, years.size) }
                        item { SectionTitle(if (rtl) "المحركات المتاحة" else "Motorisations disponibles") }
                        items(filtered) { row -> EngineCard(row, rtl) }
                    }
                    1 -> {
                        item { SectionTitle(if (rtl) "المحركات المتاحة" else "Motorisations disponibles") }
                        items(filtered) { row -> EngineCard(row, rtl) }
                    }
                    2 -> {
                        item { SectionTitle(if (rtl) "المواصفات التقنية" else "Spécifications techniques") }
                        items(filtered) { row -> SpecificationCard(row, rtl) }
                    }
                    else -> {
                        item { DiagnosticActions(rtl, context) }
                        item { SectionTitle(if (rtl) "بيانات المحركات للتشخيص" else "Données moteur pour le diagnostic") }
                        items(filtered) { row -> DiagnosticEngineCard(row, rtl) }
                    }
                }
                item {
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (rtl) "رجوع" else "Retour")
                    }
                }
            }
        }
    }
}

@Composable private fun Header(make: String, model: String, years: List<Int>, rtl: Boolean, onBack: () -> Unit, onDiagnose: () -> Unit) {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = VPSurface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, if (rtl) "رجوع" else "Retour", tint = VPText) }
                Column(Modifier.weight(1f)) {
                    Text(make, color = VPTeal, style = MaterialTheme.typography.labelLarge)
                    Text(model, color = VPText, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (years.isEmpty()) (if (rtl) "بيانات السنة غير متوفرة" else "Années indisponibles")
                        else "${years.minOrNull()} – ${years.maxOrNull()}",
                        color = VPMuted
                    )
                }
                Surface(shape = RoundedCornerShape(14.dp), color = VPTeal.copy(alpha = .14f)) {
                    Icon(Icons.Default.DirectionsCar, null, modifier = Modifier.padding(12.dp), tint = VPTeal)
                }
            }
            Button(onClick = onDiagnose, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Build, null)
                Spacer(Modifier.width(8.dp))
                Text(if (rtl) "تشخيص هذه السيارة" else "Diagnostiquer ce véhicule")
            }
        }
    }
}

@Composable private fun SummaryCard(first: CanonicalVehicleRow?, rtl: Boolean, engines: Int, years: Int) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = VPSurface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (rtl) "ملخص السيارة" else "Résumé véhicule", color = VPText, style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(if (rtl) "سنوات" else "Années", years.toString(), Modifier.weight(1f))
                Metric(if (rtl) "محركات" else "Moteurs", engines.toString(), Modifier.weight(1f))
                Metric(if (rtl) "وقود" else "Carburant", first?.fuelType ?: "—", Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun Metric(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = Color(0xFF111C20)) {
        Column(Modifier.padding(10.dp)) { Text(value, color = VPText, style = MaterialTheme.typography.titleMedium); Text(label, color = VPMuted, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable private fun SectionTitle(title: String) { Text(title, color = VPText, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp)) }

@Composable private fun EngineCard(row: CanonicalVehicleRow, rtl: Boolean) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = VPSurface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("${row.modelYear} • ${row.engineName ?: if (rtl) "المحرك غير متوفر" else "Engine information unavailable"}", color = VPText, style = MaterialTheme.typography.titleMedium)
            InfoLine(if (rtl) "السعة" else "Cylindrée", row.displacementCc?.let { "${it.toInt()} cc" })
            InfoLine(if (rtl) "الأسطوانات" else "Cylindres", row.cylinders?.let { it.toInt().toString() })
            InfoLine(if (rtl) "القوة" else "Puissance", row.powerHp?.let { "$it HP" })
            InfoLine(if (rtl) "الوقود" else "Carburant", row.fuelType)
            InfoLine(if (rtl) "ناقل الحركة" else "Transmission", row.transmission)
            InfoLine(if (rtl) "الدفع" else "Transmission intégrale", row.drivetrain)
        }
    }
}

@Composable private fun SpecificationCard(row: CanonicalVehicleRow, rtl: Boolean) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = VPSurface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.engineName ?: if (rtl) "مواصفات المحرك" else "Spécifications moteur", color = VPText, style = MaterialTheme.typography.titleMedium)
            InfoLine(if (rtl) "السنة" else "Année", row.modelYear.toString())
            InfoLine(if (rtl) "سنة المحرك" else "Année moteur", row.engineYear?.toString())
            InfoLine(if (rtl) "السعة" else "Cylindrée", row.displacementCc?.let { "${it.toInt()} cc" })
            InfoLine(if (rtl) "القوة" else "Puissance", row.powerHp?.let { "$it HP" })
            InfoLine(if (rtl) "نظام الوقود" else "Carburant", row.fuelType)
            InfoLine(if (rtl) "ناقل الحركة" else "Transmission", row.transmission)
            InfoLine(if (rtl) "نظام الدفع" else "Transmission", row.drivetrain)
        }
    }
}

@Composable private fun DiagnosticActions(rtl: Boolean, context: android.content.Context) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (rtl) "أدوات التشخيص" else "Outils de diagnostic", color = VPText, style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiagnosticButton(if (rtl) "OBD-II" else "OBD-II", Icons.Default.BluetoothConnected, Modifier.weight(1f)) { context.startActivity(Intent(context, ObdScannerActivity::class.java)) }
            DiagnosticButton("Live Data", Icons.Default.Speed, Modifier.weight(1f)) { context.startActivity(Intent(context, LiveDataProActivity::class.java)) }
        }
        DiagnosticButton(if (rtl) "الأعطال DTC" else "DTC & Faults", Icons.Default.Warning, Modifier.fillMaxWidth()) { context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java)) }
    }
}

@Composable private fun DiagnosticButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VPSurface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = VPTeal); Spacer(Modifier.width(8.dp)); Text(label, color = VPText) }
    }
}

@Composable private fun DiagnosticEngineCard(row: CanonicalVehicleRow, rtl: Boolean) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = VPSurface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(row.engineName ?: if (rtl) "محرك غير محدد" else "Moteur non identifié", color = VPText, style = MaterialTheme.typography.titleMedium)
            Text("${row.modelYear} • ${row.fuelType ?: "—"}", color = VPMuted)
            InfoLine(if (rtl) "القوة" else "Puissance", row.powerHp?.let { "$it HP" })
            InfoLine(if (rtl) "العزم" else "Couple", null)
            InfoLine(if (rtl) "ECU" else "ECU", if (rtl) "بيانات ECU تُعرض عند توفرها" else "Données ECU affichées lorsqu'elles sont disponibles")
        }
    }
}

@Composable private fun InfoLine(label: String, value: String?) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = VPMuted); Text(value ?: "—", color = VPText) } }

@Composable private fun YearChip(label: String, selected: Boolean, onClick: () -> Unit) { Surface(modifier = Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(15.dp), color = if (selected) VPTeal else VPSurface) { Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = if (selected) VPBg else VPText) } }

@Composable private fun ErrorState(message: String, rtl: Boolean, onRetry: () -> Unit, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(if (rtl) "تعذر تحميل بطاقة السيارة" else "Impossible de charger la fiche", color = VPText, style = MaterialTheme.typography.titleLarge); Text(message, color = VPMuted); Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onRetry) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(if (rtl) "إعادة المحاولة" else "Réessayer") }; OutlinedButton(onClick = onBack) { Text(if (rtl) "رجوع" else "Retour") } } } }

@Composable private fun EmptyState(model: String, rtl: Boolean, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(model, color = VPText, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(6.dp)); Text(if (rtl) "لا توجد بيانات فنية مؤكدة لهذا الموديل بعد." else "Aucune donnée technique canonique pour ce modèle.", color = VPMuted); Spacer(Modifier.height(12.dp)); Button(onClick = onBack) { Text(if (rtl) "رجوع" else "Retour") } } }
