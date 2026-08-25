package dz.cardiag.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class CanonicalRow(
    val id: String? = null,
    @SerialName("make_name") val make: String,
    @SerialName("model_name") val model: String,
    @SerialName("model_year") val modelYear: Int,
    @SerialName("engine_name") val engine: String? = null,
    @SerialName("engine_year") val engineYear: Int? = null,
    @SerialName("engine_displacement") val displacement: Double? = null,
    @SerialName("engine_cylinders") val cylinders: Double? = null,
    @SerialName("engine_power_hp") val powerHp: Double? = null,
    val transmission: String? = null,
    val drivetrain: String? = null,
    @SerialName("fuel_type") val fuel: String? = null
)

private val VPBg = Color(0xFF06090B)
private val VPSurface = Color(0xFF0D1418)
private val VPTeal = Color(0xFF48D7C5)
private val VPText = Color(0xFFF5F8F8)
private val VPMuted = Color(0xFF8B9A9F)

@Composable
fun ExactVehicleProfileScreen(model: UiModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<CanonicalRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedYear by remember { mutableStateOf<Int?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                SupabaseClient.client.from("vehicle_catalog_canonical")
                    .select(Columns.list("id", "make_name", "model_name", "model_year", "engine_name", "engine_year", "engine_displacement", "engine_cylinders", "engine_power_hp", "transmission", "drivetrain", "fuel_type"))
                    .decodeList<CanonicalRow>()
                    .filter { it.model.equals(model.name, ignoreCase = true) }
                    .sortedWith(compareByDescending<CanonicalRow> { it.modelYear }.thenBy { it.engine ?: "" })
            }.onSuccess { rows = it }
             .onFailure { error = it.message ?: "Erreur Supabase" }
            loading = false
        }
    }

    LaunchedEffect(model.id, model.name) { load() }
    val years = rows.map { it.modelYear }.distinct().sortedDescending()
    val filtered = selectedYear?.let { y -> rows.filter { it.modelYear == y } } ?: rows
    val make = rows.firstOrNull()?.make ?: model.name

    Scaffold(containerColor = VPBg) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = VPTeal) }
            error != null -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Impossible de charger la fiche", color = VPText, style = MaterialTheme.typography.titleLarge)
                Text(error!!, color = VPMuted)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { load() }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Réessayer") }
            }
            rows.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(model.name, color = VPText, style = MaterialTheme.typography.headlineMedium)
                Text("Aucune donnée canonique pour ce modèle.", color = VPMuted)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Retour") }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(make, color = VPTeal, style = MaterialTheme.typography.labelLarge)
                    Text(rows.first().model, color = VPText, style = MaterialTheme.typography.headlineLarge)
                    Text("${years.size} années de modèle • ${rows.size} motorisations", color = VPMuted)
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { YearChip("Tous", selectedYear == null) { selectedYear = null } }
                        items(years) { year -> YearChip(year.toString(), selectedYear == year) { selectedYear = year } }
                    }
                }
                item { Text("Motorisations", color = VPText, style = MaterialTheme.typography.titleLarge) }
                items(filtered) { row -> EngineCard(row) }
                item { Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("Retour") } }
            }
        }
    }
}

@Composable
private fun YearChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.clip(RoundedCornerShape(15.dp)).clickable(onClick = onClick), color = if (selected) VPTeal else VPSurface) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = if (selected) VPBg else VPText)
    }
}

@Composable
private fun EngineCard(row: CanonicalRow) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = VPSurface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("${row.modelYear} • ${row.engine ?: "Engine information unavailable"}", color = VPText, style = MaterialTheme.typography.titleMedium)
            row.engineYear?.let { Text("Engine year: $it", color = VPMuted) }
            row.displacement?.let { Text("Displacement: ${it.toInt()} cc", color = VPMuted) }
            row.cylinders?.let { Text("Cylinders: ${it.toInt()}", color = VPMuted) }
            row.powerHp?.let { Text("Power: $it HP", color = VPMuted) }
            row.fuel?.let { Text("Fuel: $it", color = VPMuted) }
            row.transmission?.let { Text("Transmission: $it", color = VPMuted) }
            row.drivetrain?.let { Text("Drivetrain: $it", color = VPMuted) }
        }
    }
}
