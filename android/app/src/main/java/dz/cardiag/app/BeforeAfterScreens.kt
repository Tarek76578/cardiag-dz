package dz.cardiag.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.BeforeAfterComparison
import dz.cardiag.app.core.BeforeAfterOutcome
import dz.cardiag.app.core.BeforeAfterSnapshot
import dz.cardiag.app.ui.theme.CarDiagShapes

/**
 * Renders a deterministic before/after repair comparison. The widget is
 * offline-safe: it relies only on the snapshots recorded by the user and
 * never invents diagnostic conclusions.
 */
@Composable
fun BeforeAfterComparisonCard(
    before: BeforeAfterSnapshot,
    after: BeforeAfterSnapshot,
    modifier: Modifier = Modifier
) {
    val outcome = remember(before, after) { BeforeAfterComparison.compare(before, after) }
    val (color, icon) = outcomePresentation(outcome)
    val outcomeLabel = outcomeLabel(outcome)
    val milBefore = milLabel(before.milOn)
    val milAfter = milLabel(after.milOn)
    val readinessBefore = readinessLabel(before.readinessReady)
    val readinessAfter = readinessLabel(after.readinessReady)
    val dtcBeforeCount = before.dtcs.size + before.pendingDtcs.size + before.permanentDtcs.size
    val dtcAfterCount = after.dtcs.size + after.pendingDtcs.size + after.permanentDtcs.size
    val dtcBeforeList = (before.dtcs + before.pendingDtcs + before.permanentDtcs).joinToString(", ").ifBlank { "—" }
    val dtcAfterList = (after.dtcs + after.pendingDtcs + after.permanentDtcs).joinToString(", ").ifBlank { "—" }

    Card(modifier = modifier.fillMaxWidth(), shape = CarDiagShapes.Card) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.history_before_after),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(onClick = {}, label = { Text(outcomeLabel) }, colors = AssistChipDefaults.assistChipColors(containerColor = color.copy(alpha = 0.15f)))
            }
            if (outcome == BeforeAfterOutcome.INSUFFICIENT) {
                Text(stringResource(R.string.before_after_insufficient), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SnapshotColumn(
                    title = stringResource(R.string.before_label),
                    dtcCount = dtcBeforeCount,
                    dtcList = dtcBeforeList,
                    mil = milBefore,
                    readiness = readinessBefore,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                SnapshotColumn(
                    title = stringResource(R.string.after_label),
                    dtcCount = dtcAfterCount,
                    dtcList = dtcAfterList,
                    mil = milAfter,
                    readiness = readinessAfter,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SnapshotColumn(
    title: String,
    dtcCount: Int,
    dtcList: String,
    mil: String,
    readiness: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.before_after_count_before, dtcCount), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.before_after_dtc_before, dtcList), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.before_after_mil_before, mil), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.before_after_readiness_before, readiness), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DiagnosticReportScreen(
    padding: PaddingValues,
    arabic: Boolean,
    sessionId: String?,
    before: BeforeAfterSnapshot?,
    after: BeforeAfterSnapshot?,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            CarDiagTopBar(
                title = stringResource(R.string.history_detail_title),
                subtitle = sessionId ?: stringResource(R.string.history_subtitle),
                onBack = onBack
            )
        }
        if (before != null && after != null) {
            item {
                BeforeAfterComparisonCard(
                    before = before,
                    after = after,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            item {
                CarDiagInfoCard(
                    title = stringResource(R.string.history_before_after),
                    body = stringResource(R.string.before_after_no_data)
                )
            }
        }
    }
}

@Composable
private fun outcomePresentation(outcome: BeforeAfterOutcome): Pair<Color, androidx.compose.ui.graphics.vector.ImageVector> = when (outcome) {
    BeforeAfterOutcome.IMPROVED -> MaterialTheme.colorScheme.primary to Icons.Default.CheckCircle
    BeforeAfterOutcome.SAME -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Default.Info
    BeforeAfterOutcome.REGRESSED -> MaterialTheme.colorScheme.error to Icons.Default.Warning
    BeforeAfterOutcome.INSUFFICIENT -> MaterialTheme.colorScheme.tertiary to Icons.Default.Info
}

@Composable
private fun outcomeLabel(outcome: BeforeAfterOutcome): String = when (outcome) {
    BeforeAfterOutcome.IMPROVED -> stringResource(R.string.before_after_improved_label)
    BeforeAfterOutcome.SAME -> stringResource(R.string.before_after_same_label)
    BeforeAfterOutcome.REGRESSED -> stringResource(R.string.before_after_regressed_label)
    BeforeAfterOutcome.INSUFFICIENT -> stringResource(R.string.before_after_insufficient_label)
}

@Composable
private fun milLabel(value: Boolean?): String = when (value) {
    true -> stringResource(R.string.readiness_mil_on)
    false -> stringResource(R.string.readiness_mil_off)
    null -> stringResource(R.string.state_unknown)
}

@Composable
private fun readinessLabel(value: Boolean?): String = when (value) {
    true -> stringResource(R.string.readiness_monitor_ready)
    false -> stringResource(R.string.readiness_monitor_not_ready)
    null -> stringResource(R.string.state_unknown)
}
