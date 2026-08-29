package dz.cardiag.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.DtcKnowledgeCatalog
import dz.cardiag.app.core.DtcKnowledgeEntry
import dz.cardiag.app.ui.rememberCarDiagWindowSize
import dz.cardiag.app.ui.theme.CarDiagShapes

/**
 * DTC browse screen. Uses the offline [DtcKnowledgeCatalog] so the screen is
 * always useful even without network access. The screen never invents DTC
 * information; unknown codes must be looked up in the full remote catalog
 * via [DtcDetailScreen].
 *
 * On expanded layouts (tablet / large landscape) the screen renders a
 * two-pane list | detail layout. On compact layouts it stays a single
 * scrolling list.
 */
@Composable
fun DtcBrowseScreen(
    padding: PaddingValues,
    arabic: Boolean,
    onBack: () -> Unit,
    onSelectCode: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var familyFilter by remember { mutableStateOf<Char?>(null) }
    var severityFilter by remember { mutableStateOf<String?>(null) }
    var selectedCode by remember { mutableStateOf<String?>(null) }
    val windowSize = rememberCarDiagWindowSize()

    val filtered = remember(query, familyFilter, severityFilter) {
        dz.cardiag.app.core.DtcBrowseFilter.apply(
            DtcKnowledgeCatalog.entries,
            query,
            familyFilter,
            severityFilter
        )
    }
    val selected = remember(selectedCode, filtered) { selectedCode?.let { DtcKnowledgeCatalog.lookup(it) } }

    Column(Modifier.fillMaxSize().padding(padding)) {
        CarDiagTopBar(
            title = stringResource(R.string.dtc_browse_title),
            subtitle = stringResource(R.string.dtc_browse_subtitle),
            onBack = onBack
        )
        if (windowSize.isExpanded) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(0.45f).fillMaxSize()) {
                    DtcBrowseFilters(
                        query = query,
                        onQueryChange = { query = it.uppercase(); selectedCode = null },
                        familyFilter = familyFilter,
                        onFamily = { familyFilter = if (familyFilter == it) null else it; selectedCode = null },
                        severityFilter = severityFilter,
                        onSeverity = { severityFilter = if (severityFilter == it) null else it; selectedCode = null }
                    )
                    DtcBrowseList(
                        entries = filtered,
                        selectedCode = selectedCode,
                        onSelect = { selectedCode = it }
                    )
                }
                Column(Modifier.weight(0.55f).fillMaxSize()) {
                    if (selected != null) {
                        DtcBrowseDetail(entry = selected, onChoose = { onSelectCode(selected.code) })
                    } else {
                        DtcBrowseDetailEmpty()
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    DtcBrowseFilters(
                        query = query,
                        onQueryChange = { query = it.uppercase() },
                        familyFilter = familyFilter,
                        onFamily = { familyFilter = if (familyFilter == it) null else it },
                        severityFilter = severityFilter,
                        onSeverity = { severityFilter = if (severityFilter == it) null else it }
                    )
                }
                if (filtered.isEmpty()) {
                    item { DtcBrowseNoResults() }
                } else {
                    items(filtered, key = { it.code }) { entry ->
                        DtcBrowseRow(
                            entry = entry,
                            expanded = true,
                            selected = false,
                            onSelect = { onSelectCode(entry.code) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DtcBrowseFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    familyFilter: Char?,
    onFamily: (Char) -> Unit,
    severityFilter: String?,
    onSeverity: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.dtc_search_hint)) }
        )
        Text(stringResource(R.string.dtc_browse_filter_family), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf('P', 'B', 'C', 'U').forEach { fam ->
                FilterChip(
                    selected = familyFilter == fam,
                    onClick = { onFamily(fam) },
                    label = {
                        Text(
                            when (fam) {
                                'P' -> stringResource(R.string.dtc_family_p)
                                'B' -> stringResource(R.string.dtc_family_b)
                                'C' -> stringResource(R.string.dtc_family_c)
                                else -> stringResource(R.string.dtc_family_u)
                            }
                        )
                    }
                )
            }
        }
        Text(stringResource(R.string.dtc_browse_filter_severity), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("critical", "warning", "info").forEach { sev ->
                FilterChip(
                    selected = severityFilter == sev,
                    onClick = { onSeverity(sev) },
                    label = {
                        Text(
                            when (sev) {
                                "critical" -> stringResource(R.string.dtc_severity_critical)
                                "warning" -> stringResource(R.string.dtc_severity_warning)
                                else -> stringResource(R.string.dtc_severity_info)
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DtcBrowseList(
    entries: List<DtcKnowledgeEntry>,
    selectedCode: String?,
    onSelect: (String) -> Unit
) {
    if (entries.isEmpty()) {
        DtcBrowseNoResults()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(entries, key = { it.code }) { entry ->
            DtcBrowseRow(
                entry = entry,
                expanded = false,
                selected = entry.code == selectedCode,
                onSelect = { onSelect(entry.code) }
            )
        }
    }
}

@Composable
private fun DtcBrowseRow(
    entry: DtcKnowledgeEntry,
    expanded: Boolean,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val severityColor: Color = when (entry.severity) {
        "critical" -> MaterialTheme.colorScheme.error
        "warning" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val severityLabel = when (entry.severity) {
        "critical" -> stringResource(R.string.dtc_severity_critical)
        "warning" -> stringResource(R.string.dtc_severity_warning)
        else -> stringResource(R.string.dtc_severity_info)
    }
    val cardCd = stringResource(R.string.cd_dtc_code) + ": " + entry.code
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = cardCd },
        shape = CarDiagShapes.Card,
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
        onClick = onSelect
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = severityColor)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.code, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = {}, label = { Text(severityLabel) }, colors = AssistChipDefaults.assistChipColors(containerColor = severityColor.copy(alpha = 0.15f)))
                }
                Text(entry.titleEn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (expanded) {
                    Spacer(Modifier.width(0.dp))
                    Text(entry.descriptionEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun DtcBrowseDetail(entry: DtcKnowledgeEntry, onChoose: () -> Unit) {
    val severityLabel = when (entry.severity) {
        "critical" -> stringResource(R.string.dtc_severity_critical)
        "warning" -> stringResource(R.string.dtc_severity_warning)
        else -> stringResource(R.string.dtc_severity_info)
    }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = CarDiagShapes.Card) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    AssistChip(onClick = {}, label = { Text(severityLabel) })
                }
                Text(entry.titleEn, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(entry.descriptionEn, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (entry.symptoms.isNotEmpty()) {
            DtcListCard(stringResource(R.string.dtc_detail_symptoms), entry.symptoms)
        }
        if (entry.causes.isNotEmpty()) {
            DtcListCard(stringResource(R.string.dtc_detail_causes), entry.causes)
        }
        androidx.compose.material3.Button(onClick = onChoose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dtc_browse_select_code))
        }
    }
}

@Composable
private fun DtcBrowseDetailEmpty() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.adaptive_select_code), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DtcBrowseNoResults() {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.dtc_browse_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DtcListCard(title: String, items: List<String>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = CarDiagShapes.Card) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            items.forEach { line -> Text("• $line", modifier = Modifier.fillMaxWidth()) }
        }
    }
}
