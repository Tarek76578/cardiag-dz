package dz.cardiag.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.AuthService
import dz.cardiag.app.core.SupabaseClient
import dz.cardiag.app.core.VehicleHealthEngine
import dz.cardiag.app.core.VehicleHealthSnapshot
import dz.cardiag.app.ui.rememberCarDiagWindowSize
import dz.cardiag.app.ui.theme.CarDiagSeverity
import dz.cardiag.app.ui.theme.CarDiagShapes
import dz.cardiag.app.ui.theme.CarDiagSpacing
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------

@Composable
internal fun CarDiagPageHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CarDiagSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
internal fun CarDiagEmptyState(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CarDiagLoadingState(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CarDiagTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

@Composable
internal fun CarDiagActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CarDiagShapes.Card,
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .padding(0.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .padding(0.dp)
                )
                Icon(icon, contentDescription = null, tint = accentColor)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------

@Composable
fun HomeScreen(
    padding: PaddingValues,
    arabic: Boolean,
    activeVehicleId: String?,
    activeVehicleName: String?,
    onVehicle: (String, String) -> Unit,
    onOpenDtc: (String) -> Unit,
    onOpenObd: () -> Unit,
    onOpenSymptom: () -> Unit,
    onOpenGuided: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDtcSearch: () -> Unit
) {
    val windowSize = rememberCarDiagWindowSize()
    var sessionCount by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        loading = true
        sessionCount = runCatching {
            SupabaseClient.client.from("diagnostic_sessions")
                .select(Columns.list("id"))
                .decodeList<Map<String, String>>()
                .size
        }.getOrDefault(0)
        loading = false
    }
    val health = activeVehicleId?.let { VehicleHealthEngine.snapshot() }
    val healthScore = health?.score
    val healthExplain = health?.summary ?: stringResource(R.string.home_health_score_explainer)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CarDiagPageHeader(
                title = stringResource(R.string.home_greeting),
                subtitle = stringResource(R.string.home_subtitle)
            )
        }
        item {
            VehicleContextCard(
                arabic = arabic,
                activeVehicleId = activeVehicleId,
                activeVehicleName = activeVehicleName,
                onChooseVehicle = { onVehicle("", "") }
            )
        }
        item {
            HealthScoreCard(
                arabic = arabic,
                score = healthScore,
                explainer = healthExplain
            )
        }
        item {
            CarDiagSectionHeader(stringResource(R.string.home_start_diagnosis))
        }
        if (windowSize.isExpanded) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CarDiagActionCard(
                            title = stringResource(R.string.home_obd_scan),
                            subtitle = stringResource(R.string.home_obd_scan_desc),
                            icon = Icons.Default.Bolt,
                            onClick = onOpenObd
                        )
                        CarDiagActionCard(
                            title = stringResource(R.string.home_dtc_lookup),
                            subtitle = stringResource(R.string.home_dtc_lookup_desc),
                            icon = Icons.Default.Warning,
                            onClick = onOpenDtcSearch
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CarDiagActionCard(
                            title = stringResource(R.string.home_symptom_diagnosis),
                            subtitle = stringResource(R.string.home_symptom_diagnosis_desc),
                            icon = Icons.Default.Search,
                            onClick = onOpenSymptom
                        )
                        CarDiagActionCard(
                            title = stringResource(R.string.home_ai_assistant),
                            subtitle = stringResource(R.string.home_ai_assistant_desc),
                            icon = Icons.Default.AutoAwesome,
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            onClick = onOpenAi
                        )
                    }
                }
            }
        } else {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CarDiagActionCard(
                        title = stringResource(R.string.home_obd_scan),
                        subtitle = stringResource(R.string.home_obd_scan_desc),
                        icon = Icons.Default.Bolt,
                        onClick = onOpenObd
                    )
                    CarDiagActionCard(
                        title = stringResource(R.string.home_symptom_diagnosis),
                        subtitle = stringResource(R.string.home_symptom_diagnosis_desc),
                        icon = Icons.Default.Search,
                        onClick = onOpenSymptom
                    )
                    CarDiagActionCard(
                        title = stringResource(R.string.home_dtc_lookup),
                        subtitle = stringResource(R.string.home_dtc_lookup_desc),
                        icon = Icons.Default.Warning,
                        onClick = onOpenDtcSearch
                    )
                    CarDiagActionCard(
                        title = stringResource(R.string.home_ai_assistant),
                        subtitle = stringResource(R.string.home_ai_assistant_desc),
                        icon = Icons.Default.AutoAwesome,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        onClick = onOpenAi
                    )
                }
            }
        }
        item {
            CarDiagSectionHeader(stringResource(R.string.home_recent_session))
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = CarDiagShapes.Card,
                onClick = onOpenHistory
            ) {
                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        val title: String = if (loading) stringResource(R.string.state_loading)
                        else stringResource(R.string.history_session_count, sessionCount)
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.history_data_source), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun VehicleContextCard(arabic: Boolean, activeVehicleId: String?, activeVehicleName: String?, onChooseVehicle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = CarDiagShapes.Large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (activeVehicleName.isNullOrBlank()) {
                    Text(stringResource(R.string.home_no_vehicle), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.home_no_vehicle_desc), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(activeVehicleName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.garage_active), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onChooseVehicle) {
                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.home_select_vehicle))
            }
        }
    }
}

@Composable
private fun HealthScoreCard(arabic: Boolean, score: Int?, explainer: String) {
    val label = if (score == null) stringResource(R.string.home_health_score_unknown) else "$score/100"
    val cd = stringResource(R.string.cd_health_explainer)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = cd },
        shape = CarDiagShapes.Card
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_health_score), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            if (score != null) {
                LinearProgressIndicator(progress = { (score / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
            Text(explainer, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------------------
// Garage
// ---------------------------------------------------------------------------

@Composable
fun GarageScreen(
    padding: PaddingValues,
    arabic: Boolean,
    activeVehicleId: String?,
    onVehicle: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<UiModel>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var authed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val unavailableText = stringResource(R.string.state_unavailable)
    fun reload() {
        scope.launch {
            loading = true
            error = null
            authed = runCatching { AuthService().currentUser != null }.getOrDefault(false)
            models = runCatching {
                SupabaseClient.client.from("vehicle_models")
                    .select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url"))
                    .decodeList<ExactVehicle>()
                    .map { UiModel(id = it.id, name = it.name, imageUrl = it.imageUrl) }
            }.getOrDefault(emptyList())
            if (models.isEmpty()) error = unavailableText
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    val filtered = models.filter { it.name.contains(query, ignoreCase = true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            CarDiagPageHeader(
                title = stringResource(R.string.garage_title),
                subtitle = stringResource(R.string.garage_subtitle)
            )
        }
        if (!authed) {
            item {
                CarDiagInfoCard(
                    title = stringResource(R.string.garage_title),
                    body = stringResource(R.string.garage_sign_in_required)
                )
            }
        }
        item {
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text(stringResource(R.string.garage_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
        }
        item { CarDiagSectionHeader(stringResource(R.string.garage_catalog)) }
        if (loading) {
            item { CarDiagLoadingState(stringResource(R.string.state_loading)) }
        } else if (filtered.isEmpty()) {
            item { CarDiagEmptyState(stringResource(R.string.garage_no_vehicles), stringResource(R.string.home_no_vehicle_desc)) }
        } else {
            items(filtered.take(50), key = { it.id }) { vehicle ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = CarDiagShapes.Card,
                    onClick = { onVehicle(vehicle.id, vehicle.name) }
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(vehicle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        }
                        if (vehicle.id == activeVehicleId) {
                            Icon(Icons.Default.TrackChanges, contentDescription = stringResource(R.string.garage_active), tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        error?.let { item { CarDiagInfoCard(title = stringResource(R.string.state_error), body = it) } }
    }
}

@Composable
internal fun CarDiagInfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = CarDiagShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Diagnose Hub
// ---------------------------------------------------------------------------

@Composable
fun DiagnoseHubScreen(
    padding: PaddingValues,
    arabic: Boolean,
    hasVehicle: Boolean,
    onOpenObd: () -> Unit,
    onOpenSymptom: () -> Unit,
    onOpenDtc: (String) -> Unit,
    onOpenGuided: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            CarDiagPageHeader(
                title = stringResource(R.string.diagnose_title),
                subtitle = stringResource(R.string.diagnose_subtitle)
            )
        }
        item { DiagnoseFlowCard(arabic, hasVehicle) }
        item { CarDiagSectionHeader(stringResource(R.string.diagnose_title)) }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CarDiagActionCard(
                    title = stringResource(R.string.diagnose_entry_obd),
                    subtitle = stringResource(R.string.home_obd_scan_desc),
                    icon = Icons.Default.Bolt,
                    onClick = onOpenObd
                )
                CarDiagActionCard(
                    title = stringResource(R.string.diagnose_entry_symptom),
                    subtitle = stringResource(R.string.home_symptom_diagnosis_desc),
                    icon = Icons.Default.Search,
                    onClick = onOpenSymptom
                )
                CarDiagActionCard(
                    title = stringResource(R.string.diagnose_entry_guided),
                    subtitle = stringResource(R.string.guided_subtitle),
                    icon = Icons.Default.Build,
                    onClick = onOpenGuided
                )
                CarDiagActionCard(
                    title = stringResource(R.string.diagnose_entry_dtc),
                    subtitle = stringResource(R.string.dtc_title),
                    icon = Icons.Default.Warning,
                    onClick = { onOpenDtc("") }
                )
            }
        }
    }
}

@Composable
private fun DiagnoseFlowCard(arabic: Boolean, hasVehicle: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = CarDiagShapes.Large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.guided_subtitle), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            StepRow(1, stringResource(R.string.diagnose_step_vehicle))
            StepRow(2, stringResource(R.string.diagnose_step_problem))
            StepRow(3, stringResource(R.string.diagnose_step_system))
            StepRow(4, stringResource(R.string.diagnose_step_scan))
            StepRow(5, stringResource(R.string.diagnose_step_evidence))
            StepRow(6, stringResource(R.string.diagnose_step_causes))
            StepRow(7, stringResource(R.string.diagnose_step_tests))
            StepRow(8, stringResource(R.string.diagnose_step_results))
            StepRow(9, stringResource(R.string.diagnose_step_diagnosis))
            StepRow(10, stringResource(R.string.diagnose_step_repair))
            StepRow(11, stringResource(R.string.diagnose_step_clear))
            StepRow(12, stringResource(R.string.diagnose_step_save))
            if (!hasVehicle) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.symptom_no_vehicle), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StepRow(index: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .padding(0.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("$index", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------
// History
// ---------------------------------------------------------------------------

@Composable
fun HistoryScreen(padding: PaddingValues, arabic: Boolean, onOpenSession: (String) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var count by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        loading = true
        error = null
        count = runCatching {
            SupabaseClient.client.from("diagnostic_sessions")
                .select(Columns.list("id"))
                .decodeList<Map<String, String>>()
                .size
        }.onFailure { error = it.message }.getOrDefault(0)
        loading = false
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        CarDiagPageHeader(
            title = stringResource(R.string.history_title),
            subtitle = stringResource(R.string.history_subtitle)
        )
        when {
            loading -> CarDiagLoadingState(stringResource(R.string.state_loading))
            count == 0 -> CarDiagEmptyState(stringResource(R.string.history_no_sessions), stringResource(R.string.home_no_recent_session))
            else -> Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = CarDiagShapes.Card,
                onClick = { onOpenSession("") }
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.history_session_count, count), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.history_data_source), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        error?.let { CarDiagInfoCard(title = stringResource(R.string.state_error), body = it) }
    }
}

// ---------------------------------------------------------------------------
// More / Settings
// ---------------------------------------------------------------------------

@Composable
fun MoreScreen(
    padding: PaddingValues,
    arabic: Boolean,
    dark: Boolean,
    setDark: (Boolean) -> Unit,
    setArabic: (Boolean) -> Unit,
    onOpenAdvanced: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        CarDiagPageHeader(
            title = stringResource(R.string.more_title),
            subtitle = stringResource(R.string.more_about_value)
        )
        SettingRow(
            title = stringResource(R.string.more_appearance),
            value = if (dark) stringResource(R.string.more_appearance_dark) else stringResource(R.string.more_appearance_light),
            icon = Icons.Default.Settings,
            onClick = { setDark(!dark) }
        )
        SettingRow(
            title = stringResource(R.string.more_language),
            value = if (arabic) stringResource(R.string.more_language_ar) else stringResource(R.string.more_language_fr),
            icon = Icons.Default.Language,
            onClick = { setArabic(!arabic) }
        )
        SettingRow(
            title = stringResource(R.string.more_about),
            value = stringResource(R.string.more_about_value),
            icon = Icons.Default.Info,
            onClick = {}
        )
        SettingRow(
            title = stringResource(R.string.more_privacy),
            value = stringResource(R.string.more_privacy_value),
            icon = Icons.Default.Security,
            onClick = {}
        )
        SettingRow(
            title = stringResource(R.string.more_advanced),
            value = stringResource(R.string.obd_title),
            icon = Icons.Default.Bolt,
            onClick = onOpenAdvanced
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = CarDiagShapes.Card,
        onClick = onClick
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
