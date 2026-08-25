package dz.cardiag.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
                .onSuccess { loaded -> rows = loaded.sortedWith(compareByDescending<CanonicalVehicleRow> { it.modelYear ?: 0 }.thenBy { it.engineName ?: "" }) }
                .onFailure { error = it.message ?: if (rtl) "تعذر تحميل بيانات السيارة" else "Impossible de charger la fiche" }
            loading = false
        }
    }

    LaunchedEffect(model.id, model.name) { load() }
    val years = rows.mapNotNull { it.modelYear }.distinct().sortedDescending()
    val filtered = selectedYear?.let { year -> rows.filter { it.modelYear == year } } ?: rows
    val first = rows.firstOrNull()
    val make = first?.makeName ?: model.name

    Scaffold(containerColor = VPBg) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = VPTeal) }
            error != null -> ErrorState(error ?: "", rtl, ::load, onBack)
            rows.isEmpty() -> EmptyState(model.name, rtl, onBack)
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Header(make, first?.modelName ?: model.name, years, rtl, onBack) { context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java)) } }
                item { TabRow(selectedTabIndex = tab, containerColor = VPSurface, contentColor = VPTeal) { listOf(if (rtl) "نظرة عامة" else "Vue d'ensemble", if (rtl) "المحرك" else "Moteur", if (rtl) "المواصفات" else "Spécifications", if (rtl) "التشخيص" else "Diagnostic").forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }) } } }
                item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { item { YearChip(if (rtl) "الكل" else "Tous", selectedYear == null) { selectedYear = null } }; items(years) { y -> YearChip(y.toString(), selectedYear == y) { selectedYear = y } } } }
                when (tab) {
                    0 -> { item { SummaryCard(first, rtl, rows.size, years.size) }; item { SectionTitle(if (rtl) "المحركات المتاحة" else "Motorisations disponibles") }; items(filtered) { EngineCard(it, rtl) } }
                    1 -> { item { SectionTitle(if (rtl) "المحركات المتاحة" else "Motorisations disponibles") }; items(filtered) { EngineCard(it, rtl) } }
                    2 -> { item { SectionTitle(if (rtl) "المواصفات التقنية" else "Spécifications techniques") }; items(filtered) { SpecificationCard(it, rtl) } }
                    else -> { item { DiagnosticActions(rtl, context) }; item { SectionTitle(if (rtl) "بيانات المحركات للتشخيص" else "Données moteur pour le diagnostic") }; items(filtered) { DiagnosticEngineCard(it, rtl) } }
                }
                item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(if (rtl) "رجوع" else "Retour") } }
            }
        }
    }
}

@Composable private fun ErrorState(message: String, rtl: Boolean, onRetry: () -> Unit, onBack: () -> Unit) { Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error); Text(if (rtl) "تعذر تحميل البيانات" else "Impossible de charger la fiche", color = VPText); Text(message, color = VPMuted); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onRetry) { Text(if (rtl) "إعادة المحاولة" else "Réessayer") }; OutlinedButton(onClick = onBack) { Text(if (rtl) "رجوع" else "Retour") } } } } } }
@Composable private fun EmptyState(name: String, rtl: Boolean, onBack: () -> Unit) { Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = VPTeal, modifier = Modifier.size(48.dp)); Text(if (rtl) "لا توجد بيانات لـ $name" else "Aucune donnée pour $name", color = VPText); OutlinedButton(onClick = onBack) { Text(if (rtl) "رجوع" else "Retour") } } } }
@Composable private fun Header(make: String, model: String, years: List<Int>, rtl: Boolean, onBack: () -> Unit, onDiagnose: () -> Unit) { Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = VPText) }; Column(Modifier.weight(1f)) { Text(make, color = VPMuted); Text(model, color = VPText, style = MaterialTheme.typography.headlineSmall) }; Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = VPTeal, modifier = Modifier.size(40.dp)) }; Text(if (rtl) "${years.firstOrNull() ?: "—"} — ${years.lastOrNull() ?: "—"} • ملف السيارة" else "${years.lastOrNull() ?: "—"} — ${years.firstOrNull() ?: "—"} • Fiche véhicule", color = VPMuted); Button(onClick = onDiagnose, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.HealthAndSafety, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (rtl) "تشخيص هذه السيارة" else "Diagnostiquer ce véhicule") } } } }
@Composable private fun YearChip(label: String, selected: Boolean, onClick: () -> Unit) { FilterChip(selected = selected, onClick = onClick, label = { Text(label) }) }
@Composable private fun SummaryCard(row: CanonicalVehicleRow?, rtl: Boolean, engineCount: Int, yearCount: Int) { Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(if (rtl) "ملخص" else "Résumé", color = VPTeal); Text(if (rtl) "$engineCount محرك • $yearCount سنة" else "$engineCount motorisations • $yearCount années", color = VPText); row?.let { Text(listOfNotNull(it.fuelType, it.transmission, it.drivetrain).joinToString(" • ").ifBlank { "—" }, color = VPMuted) } } } }
@Composable private fun SectionTitle(text: String) { Text(text, color = VPText, style = MaterialTheme.typography.titleLarge) }
@Composable private fun EngineCard(row: CanonicalVehicleRow, rtl: Boolean) { Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(row.engineName ?: if (rtl) "محرك غير محدد" else "Moteur non renseigné", color = VPText, style = MaterialTheme.typography.titleMedium); Text(listOfNotNull(row.fuelType, row.transmission, row.drivetrain).joinToString(" • ").ifBlank { "—" }, color = VPMuted); Text(listOfNotNull(row.displacementCc?.let { "${it.toInt()} cc" }, row.cylinders?.let { "${it.toInt()} cyl" }, row.powerHp?.let { "${it.toInt()} hp" }).joinToString(" • ").ifBlank { "—" }, color = VPTeal) } } }
@Composable private fun SpecificationCard(row: CanonicalVehicleRow, rtl: Boolean) { Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(if (rtl) "المواصفات" else "Spécifications", color = VPTeal); SpecLine(if (rtl) "المحرك" else "Moteur", row.engineName); SpecLine(if (rtl) "الوقود" else "Carburant", row.fuelType); SpecLine(if (rtl) "القوة" else "Puissance", row.powerHp?.let { "${it.toInt()} hp" }); SpecLine(if (rtl) "ناقل الحركة" else "Transmission", row.transmission); SpecLine(if (rtl) "الدفع" else "Transmission", row.drivetrain) } } }
@Composable private fun SpecLine(label: String, value: String?) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = VPMuted); Text(value ?: "—", color = VPText) } }
@Composable private fun DiagnosticActions(rtl: Boolean, context: Context) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java)) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Search, contentDescription = null); Spacer(Modifier.width(4.dp)); Text(if (rtl) "تشخيص" else "Diagnostic") }; OutlinedButton(onClick = { context.startActivity(Intent(context, ObdScannerActivity::class.java)) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Bluetooth, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("OBD-II") } } }
@Composable private fun DiagnosticEngineCard(row: CanonicalVehicleRow, rtl: Boolean) { Card(colors = CardDefaults.cardColors(containerColor = VPSurface), shape = RoundedCornerShape(16.dp)) { Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Memory, contentDescription = null, tint = VPTeal); Spacer(Modifier.width(10.dp)); Column { Text(row.engineName ?: "ECU", color = VPText); Text(if (rtl) "بيانات المحرك متاحة للقراءة والتشخيص" else "Données moteur disponibles pour lecture et diagnostic", color = VPMuted) } } } }
