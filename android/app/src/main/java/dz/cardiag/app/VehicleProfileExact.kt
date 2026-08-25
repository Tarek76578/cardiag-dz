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
