package dz.cardiag.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dz.cardiag.app.core.road.AndroidLocationProvider
import dz.cardiag.app.core.road.NearbyService
import dz.cardiag.app.core.road.OfflineRoadDataProvider
import dz.cardiag.app.core.road.RoadAssistantContext
import dz.cardiag.app.core.road.RoadAssistantService
import dz.cardiag.app.core.road.RoadAssistantSnapshot
import dz.cardiag.app.core.road.ServiceCategory
import dz.cardiag.app.ui.theme.CarDiagShapes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class LoadState { Idle, Loading, Ready, Failed }

/**
 * Driver-facing Road Assistant.
 *
 * The screen provides:
 * 1. Location permission handling (request, denied, "continue without").
 * 2. A categorized list of nearby services (mechanic, electric, roadside,
 *    spare parts, fuel, hospital, towing) with explicit disclaimers that
 *    no specific business / address / phone / rating is fabricated.
 * 3. Hand-off to an external map / search application for actual nearby
 *    results via the curated Arabic + French query templates.
 * 4. Road hazard status, shown as a generic empty state until a live
 *    provider is integrated.
 * 5. Emergency numbers for Algeria (public knowledge).
 */
@Composable
fun RoadAssistantScreen(
    padding: PaddingValues,
    arabic: Boolean,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val language = if (arabic) "ar" else "fr"
    val scope = rememberCoroutineScope()
    val service = remember { RoadAssistantService(AndroidLocationProvider(context)) }

    var permissionGranted by remember {
        mutableStateOf(RoadAssistantContext.hasLocationPermission(context))
    }
    var permissionAskedOnce by remember { mutableStateOf(false) }
    var loadState by remember { mutableStateOf(LoadState.Idle) }
    var snapshot by remember { mutableStateOf<RoadAssistantSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var radiusKm by remember { mutableStateOf(5f) }
    var selected by remember {
        mutableStateOf(
            setOf(
                ServiceCategory.MECHANIC,
                ServiceCategory.AUTO_ELECTRICIAN,
                ServiceCategory.ROADSIDE_ASSISTANCE
            )
        )
    }
    var query by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionAskedOnce = true
        permissionGranted = result.values.any { it }
    }

    fun refresh() {
        if (!permissionGranted) {
            loadState = LoadState.Idle
            snapshot = null
            return
        }
        loadState = LoadState.Loading
        error = null
        scope.launch {
            val snap = runCatching {
                service.snapshot(selected, (radiusKm * 1000).roundToInt(), language)
            }
            snap.onSuccess {
                snapshot = it
                loadState = LoadState.Ready
            }.onFailure {
                error = it.message
                loadState = LoadState.Failed
            }
        }
    }

    LaunchedEffect(permissionGranted, selected, radiusKm.roundToInt()) {
        if (permissionGranted) refresh()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ra_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = stringResource(R.string.ra_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (!permissionGranted) {
            item { RoadAssistantPermissionCard(arabic, onRequest = {
                permissionLauncher.launch(RoadAssistantContext.locationPermissions)
            }, onOpenSettings = {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                runCatching { context.startActivity(intent) }
            }) }
        } else {
            item { RoadAssistantControls(arabic, radiusKm, { radiusKm = it }, selected, { cat ->
                selected = if (cat in selected) selected - cat else selected + cat
            }, query, { query = it }) }
            item {
                val langKey = if (arabic) "ar" else "fr"
                val providerLabel = stringResource(R.string.ra_offline_catalog)
                RoadAssistantSourceCard(
                    source = snapshot?.servicesSource ?: providerLabel,
                    servicesLive = snapshot?.servicesLive ?: false,
                    hazardsLive = snapshot?.hazardsLive ?: false,
                    language = langKey,
                    arabic = arabic
                )
            }
            when {
                loadState == LoadState.Loading -> {
                    item { CarDiagLoadingState(stringResource(R.string.ra_search_loading)) }
                }
                error != null -> {
                    item { CarDiagInfoCard(stringResource(R.string.state_error), error ?: "") }
                    item {
                        OutlinedButton(onClick = ::refresh, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
                snapshot == null -> {
                    item { CarDiagEmptyState(stringResource(R.string.state_unavailable), stringResource(R.string.ra_gps_unavailable)) }
                }
                else -> {
                    val snap = snapshot!!
                    if (selected.isEmpty()) {
                        item { CarDiagInfoCard(stringResource(R.string.ra_no_category_selected), "") }
                    } else {
                        val q = query.trim()
                        val items = if (q.isEmpty()) {
                            snap.services
                        } else {
                            val langKey = if (arabic) "ar" else "fr"
                            val templates = selected.flatMap { cat ->
                                OfflineRoadDataProvider.searchQueries[cat.key]?.get(langKey).orEmpty()
                            }
                            val qNorm = q.lowercase()
                            val matched = templates.any { it.lowercase().contains(qNorm) || qNorm.contains(it.lowercase()) }
                            if (matched) snap.services else emptyList()
                        }
                        item { CarDiagSectionHeader(stringResource(R.string.ra_results_count, items.size)) }
                        items(items.size, key = { items[it].id }) { i ->
                            RoadAssistantServiceCard(items[i], arabic, onOpenMap = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri(items[i])))
                                runCatching { context.startActivity(intent) }.onFailure {
                                    error = context.getString(R.string.ra_map_intent_failed)
                                }
                            }, onSearchExternal = {
                                val langKey = if (arabic) "ar" else "fr"
                                val firstQuery = OfflineRoadDataProvider.searchQueries[items[i].category.key]?.get(langKey)?.firstOrNull()
                                    ?: items[i].category.key
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUri(firstQuery, snap.location)))
                                runCatching { context.startActivity(intent) }.onFailure {
                                    error = context.getString(R.string.ra_map_intent_failed)
                                }
                            })
                        }
                        if (q.isNotEmpty() && items.isEmpty()) {
                            item { CarDiagInfoCard(stringResource(R.string.ra_search_no_match), "") }
                        }
                    }
                    item { CarDiagSectionHeader(stringResource(R.string.ra_hazards_title)) }
                    item { CarDiagInfoCard(stringResource(R.string.ra_hazards_subtitle), "") }
                    if (snap.hazards.isEmpty()) {
                        item { CarDiagInfoCard(stringResource(R.string.ra_hazards_empty), "") }
                    } else {
                        items(snap.hazards.size, key = { snap.hazards[it].id }) { i ->
                            Card(modifier = Modifier.fillMaxWidth(), shape = CarDiagShapes.Card) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(snap.hazards[i].description, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = stringResource(R.string.ra_source_label, snap.hazards[i].source),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { CarDiagSectionHeader(stringResource(R.string.emergency_title)) }
                    item { CarDiagInfoCard(stringResource(R.string.emergency_subtitle), "") }
                    item { EmergencyNumberCard(R.string.emergency_police, R.string.emergency_police_value, arabic) }
                    item { EmergencyNumberCard(R.string.emergency_fire, R.string.emergency_fire_value, arabic) }
                    item { EmergencyNumberCard(R.string.emergency_ambulance, R.string.emergency_ambulance_value, arabic) }
                    item { EmergencyNumberCard(R.string.emergency_protection_civile, R.string.emergency_protection_civile_value, arabic) }
                }
            }
        }
    }
}

@Composable
private fun RoadAssistantPermissionCard(
    arabic: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CarDiagShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ra_need_permission_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ra_need_permission_body), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRequest) { Text(stringResource(R.string.ra_request_permission)) }
                OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.ra_open_settings)) }
            }
        }
    }
}

@Composable
private fun RoadAssistantControls(
    arabic: Boolean,
    radiusKm: Float,
    onRadius: (Float) -> Unit,
    selected: Set<ServiceCategory>,
    onToggle: (ServiceCategory) -> Unit,
    query: String,
    onQuery: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = CarDiagShapes.Card) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ra_categories), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            CategoryChipRow(selected, onToggle, arabic)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ra_radius), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.ra_radius_value, radiusKm.roundToInt()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = radiusKm,
                onValueChange = onRadius,
                valueRange = 1f..20f,
                steps = 18,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.ra_search_hint)) }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CategoryChipRow(
    selected: Set<ServiceCategory>,
    onToggle: (ServiceCategory) -> Unit,
    arabic: Boolean
) {
    val items: List<Pair<ServiceCategory, Int>> = listOf(
        ServiceCategory.MECHANIC to R.string.ra_category_mechanic,
        ServiceCategory.AUTO_ELECTRICIAN to R.string.ra_category_auto_electrician,
        ServiceCategory.ROADSIDE_ASSISTANCE to R.string.ra_category_roadside,
        ServiceCategory.SPARE_PARTS to R.string.ra_category_spare_parts,
        ServiceCategory.FUEL_STATION to R.string.ra_category_fuel,
        ServiceCategory.HOSPITAL to R.string.ra_category_hospital,
        ServiceCategory.TOWING to R.string.ra_category_towing,
        ServiceCategory.OTHER to R.string.ra_category_other
    )
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (cat, labelRes) ->
            FilterChip(
                selected = cat in selected,
                onClick = { onToggle(cat) },
                label = { Text(stringResource(labelRes)) }
            )
        }
    }
}

@Composable
private fun RoadAssistantSourceCard(
    source: String,
    servicesLive: Boolean,
    hazardsLive: Boolean,
    language: String,
    arabic: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CarDiagShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.ra_source_label, source), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!servicesLive || !hazardsLive) {
                Text(stringResource(R.string.ra_live_source_required), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(stringResource(R.string.ra_offline_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RoadAssistantServiceCard(
    service: NearbyService,
    arabic: Boolean,
    onOpenMap: () -> Unit,
    onSearchExternal: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = CarDiagShapes.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(categoryIcon(service.category), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(service.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.ra_category_label, categoryDisplayName(service.category, arabic)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenMap) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ra_open_map))
                }
                OutlinedButton(onClick = onSearchExternal) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ra_search_external))
                }
            }
        }
    }
}

@Composable
private fun EmergencyNumberCard(labelRes: Int, valueRes: Int, arabic: Boolean) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth(), shape = CarDiagShapes.Card) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(labelRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(valueRes), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            OutlinedButton(onClick = {
                val number = context.getString(valueRes)
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                runCatching { context.startActivity(intent) }
            }) { Text(stringResource(R.string.emergency_call)) }
        }
    }
}

private fun categoryIcon(category: ServiceCategory): ImageVector = when (category) {
    ServiceCategory.MECHANIC -> Icons.Default.Build
    ServiceCategory.AUTO_ELECTRICIAN -> Icons.Default.Settings
    ServiceCategory.ROADSIDE_ASSISTANCE -> Icons.Default.DirectionsCar
    ServiceCategory.SPARE_PARTS -> Icons.Default.Build
    ServiceCategory.FUEL_STATION -> Icons.Default.LocalGasStation
    ServiceCategory.HOSPITAL -> Icons.Default.LocalHospital
    ServiceCategory.TOWING -> Icons.Default.DirectionsCar
    ServiceCategory.OTHER -> Icons.Default.Search
}

private fun categoryDisplayName(category: ServiceCategory, arabic: Boolean): String = when (category) {
    ServiceCategory.MECHANIC -> if (arabic) "ميكانيكي" else "Mécanicien"
    ServiceCategory.AUTO_ELECTRICIAN -> if (arabic) "كهربائي سيارات" else "Électricien auto"
    ServiceCategory.ROADSIDE_ASSISTANCE -> if (arabic) "مساعدة على الطريق" else "Assistance"
    ServiceCategory.SPARE_PARTS -> if (arabic) "قطع غيار" else "Pièces"
    ServiceCategory.FUEL_STATION -> if (arabic) "محطة وقود" else "Station"
    ServiceCategory.HOSPITAL -> if (arabic) "مستشفى" else "Hôpital"
    ServiceCategory.TOWING -> if (arabic) "سحب" else "Remorquage"
    ServiceCategory.OTHER -> if (arabic) "أخرى" else "Autre"
}

private fun geoUri(service: NearbyService): String =
    "geo:${service.latitude},${service.longitude}?q=${service.latitude},${service.longitude}(${Uri.encode(service.name)})"

private fun searchUri(query: String, location: dz.cardiag.app.core.road.CoarseLocation?): String {
    val encoded = Uri.encode(query)
    return if (location == null) "geo:0,0?q=$encoded"
    else "geo:${location.latitude},${location.longitude}?q=$encoded"
}
