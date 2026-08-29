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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import dz.cardiag.app.core.DiagnosticService
import dz.cardiag.app.ui.theme.CarDiagShapes
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// ---------------------------------------------------------------------------
// DTC Detail
// ---------------------------------------------------------------------------

@Composable
fun DtcDetailScreen(
    padding: PaddingValues,
    arabic: Boolean,
    initialCode: String?,
    onBack: () -> Unit,
    onOpenGuided: (String) -> Unit,
    onOpenBrowse: () -> Unit = {}
) {
    var code by remember { mutableStateOf(initialCode?.uppercase() ?: "") }
    var record by remember { mutableStateOf<DtcRecord?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        val normalized = code.trim().uppercase()
        if (!normalized.matches(Regex("[PBCU][0-3][0-9A-F]{3}"))) {
            error = contextStringPlain(arabic, "أدخل رمز DTC صالح (مثال P0301).", "Entrez un code DTC valide (ex. P0301).")
            record = null
            return
        }
        scope.launch {
            loading = true
            error = null
            record = null
            record = lookupDtc(normalized)
            if (record == null) error = contextStringPlain(
                arabic,
                "لا يوجد تعريف لهذا الرمز في قاعدة البيانات.",
                "Code introuvable dans la base DTC."
            )
            loading = false
        }
    }

    LaunchedEffect(initialCode) { if (!initialCode.isNullOrBlank()) load() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { CarDiagTopBar(stringResource(R.string.dtc_title), stringResource(R.string.dtc_detail_meaning), onBack = onBack) }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase(); record = null; error = null },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.dtc_title)) },
                    placeholder = { Text(stringResource(R.string.dtc_search_hint)) }
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = ::load, enabled = !loading) { Text(stringResource(R.string.action_retry)) }
            }
        }
        item {
            OutlinedButton(onClick = onOpenBrowse, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.dtc_browse_open))
            }
        }
        if (loading) item { CarDiagLoadingState(stringResource(R.string.state_loading)) }
        error?.let { item { CarDiagInfoCard(stringResource(R.string.state_error), it) } }
        record?.let { r -> item { DtcRecordContent(r, arabic, loading, onOpenGuided) } }
    }
}

@Composable
private fun DtcRecordContent(r: DtcRecord, arabic: Boolean, loading: Boolean, onOpenGuided: (String) -> Unit) {
    val unknownMeaning = stringResource(R.string.dtc_unknown_meaning)
    val unknownAdvice = stringResource(R.string.dtc_unknown_advice)
    val hasAnyData = r.descriptionFr != null || r.descriptionAr != null || r.titleFr != null || r.titleAr != null
    val familyLabel = when (r.code.firstOrNull()) {
        'P' -> stringResource(R.string.dtc_family_p)
        'B' -> stringResource(R.string.dtc_family_b)
        'C' -> stringResource(R.string.dtc_family_c)
        'U' -> stringResource(R.string.dtc_family_u)
        else -> r.system ?: stringResource(R.string.state_unknown)
    }
    val severityLabel = when (r.severity?.lowercase()) {
        "critical" -> stringResource(R.string.dtc_severity_critical)
        "warning" -> stringResource(R.string.dtc_severity_warning)
        "info" -> stringResource(R.string.dtc_severity_info)
        else -> stringResource(R.string.dtc_severity_unknown)
    }
    val meaning = if (arabic) r.descriptionAr ?: r.descriptionFr else r.descriptionFr ?: r.descriptionAr
    val causes = splitMultiline(if (arabic) r.causesAr ?: r.causesFr else r.causesFr ?: r.causesAr)
    val steps = splitMultiline(if (arabic) r.diagnosticStepsAr ?: r.diagnosticStepsFr else r.diagnosticStepsFr ?: r.diagnosticStepsAr)
    val repairs = splitMultiline(if (arabic) r.repairSummaryAr ?: r.repairSummaryFr else r.repairSummaryFr ?: r.repairSummaryAr)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = CarDiagShapes.Card) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    AssistChip(onClick = {}, label = { Text(severityLabel) })
                }
                Text(familyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                if (meaning.isNullOrBlank()) {
                    Text(unknownMeaning, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(unknownAdvice, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(stringResource(R.string.dtc_detail_meaning), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(meaning)
                }
            }
        }
        if (causes.isNotEmpty()) {
            DtcListCard(stringResource(R.string.dtc_detail_causes), causes, Icons.Default.Warning)
        }
        if (steps.isNotEmpty()) {
            DtcListCard(stringResource(R.string.dtc_detail_tests), steps, Icons.Default.CheckCircle)
        }
        if (repairs.isNotEmpty()) {
            DtcListCard(stringResource(R.string.dtc_detail_repair), repairs, Icons.Default.CheckCircle)
        }
        if (!hasAnyData) {
            CarDiagInfoCard(stringResource(R.string.dtc_unknown_meaning), stringResource(R.string.dtc_unknown_advice))
        }
        Button(onClick = { onOpenGuided(r.code) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.diagnose_entry_guided))
        }
    }
}

@Composable
private fun DtcListCard(title: String, items: List<String>, icon: ImageVector) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = CarDiagShapes.Card) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            items.forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(line, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Guided Diagnosis (decision tree)
// ---------------------------------------------------------------------------

@Composable
fun GuidedDiagnosisScreen(
    padding: PaddingValues,
    arabic: Boolean,
    vehicleId: String?,
    vehicleName: String?,
    initialCode: String?,
    onBack: () -> Unit,
    onOpenAi: (String) -> Unit,
    onOpenObd: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var code by remember { mutableStateOf(initialCode?.uppercase() ?: "") }
    var record by remember { mutableStateOf<DtcRecord?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var aiResult by remember { mutableStateOf<String?>(null) }
    val steps = remember { mutableStateListOf<GuidedStep>() }
    var stepIndex by remember { mutableStateOf(0) }

    fun loadGuide() {
        val normalized = code.trim().uppercase()
        if (!normalized.matches(Regex("[PBCU][0-3][0-9A-F]{3}"))) {
            error = contextStringPlain(arabic, "أدخل رمز DTC صالح (مثال P0301).", "Entrez un code DTC valide (ex. P0301).")
            return
        }
        scope.launch {
            loading = true
            error = null
            record = null
            aiResult = null
            steps.clear()
            stepIndex = 0
            record = lookupDtc(normalized)
            steps.addAll(GuidedDecisionTree.forCode(normalized, record))
            if (steps.isEmpty() && record == null) {
                error = contextStringPlain(arabic, "لا توجد شجرة قرار لهذا الرمز.", "Aucun arbre de décision connu pour ce code.")
            }
            loading = false
        }
    }

    fun runAi() {
        if (record == null) {
            error = contextStringPlain(arabic, "حمّل رمزا صالحا أولا.", "Chargez d'abord un code valide.")
            return
        }
        scope.launch {
            loading = true
            error = null
            aiResult = null
            runCatching {
                DiagnosticService().runDiagnostic(
                    vehicleModelId = vehicleId,
                    userVehicleId = null,
                    complaint = "Guided $code",
                    language = if (arabic) "ar" else "fr",
                    codes = listOf(code),
                    symptoms = buildJsonObject { put("source", "guided_dtc") },
                    vehicle = buildJsonObject {
                        put("model_id", vehicleId ?: "")
                        put("model_name", vehicleName ?: "")
                    }
                )
            }.onSuccess { response -> aiResult = response.toString() }
                .onFailure { error = it.message ?: context.getString(R.string.state_error) }
            loading = false
        }
    }

    LaunchedEffect(initialCode) { if (!initialCode.isNullOrBlank()) loadGuide() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { CarDiagTopBar(stringResource(R.string.guided_title), stringResource(R.string.guided_subtitle), onBack = onBack) }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase(); record = null; error = null; steps.clear() },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.dtc_title)) },
                    placeholder = { Text("P0301") }
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = ::loadGuide, enabled = !loading && code.isNotBlank()) { Text(stringResource(R.string.action_retry)) }
            }
        }
        record?.let {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = CarDiagShapes.Card) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(it.code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        val meaning = if (arabic) it.descriptionAr ?: it.descriptionFr else it.descriptionFr ?: it.descriptionAr
                        Text(meaning ?: stringResource(R.string.dtc_unknown_meaning), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (loading) item { CarDiagLoadingState(stringResource(R.string.state_loading)) }
        error?.let { item { CarDiagInfoCard(stringResource(R.string.state_error), it) } }
        if (steps.isNotEmpty() && stepIndex < steps.size) {
            val current = steps[stepIndex]
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = CarDiagShapes.Card) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.guided_current_step, stepIndex + 1, steps.size), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(current.prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = current.result == TestResult.PASSED, onClick = { current.result = TestResult.PASSED }, label = { Text(stringResource(R.string.guided_test_passed)) })
                            FilterChip(selected = current.result == TestResult.FAILED, onClick = { current.result = TestResult.FAILED }, label = { Text(stringResource(R.string.guided_test_failed)) })
                            FilterChip(selected = current.result == TestResult.INCONCLUSIVE, onClick = { current.result = TestResult.INCONCLUSIVE }, label = { Text(stringResource(R.string.guided_test_inconclusive)) })
                        }
                    }
                }
            }
            val recommendation = GuidedDecisionTree.recommendation(steps, stepIndex)
            if (recommendation != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = CarDiagShapes.Card) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.guided_recommendation), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(recommendation)
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (stepIndex > 0) stepIndex-- }, enabled = stepIndex > 0, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.guided_previous)) }
                    Button(onClick = { if (stepIndex < steps.size - 1) stepIndex++ else { /* finished */ } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.guided_next)) }
                }
            }
        }
        if (record != null) {
            item {
                Button(onClick = ::runAi, enabled = !loading, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_analyze))
                }
            }
        }
        aiResult?.let { raw ->
            item { AiStructuredCard(raw, arabic) }
        }
        item {
            OutlinedButton(onClick = onOpenObd, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.diagnose_entry_obd))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// AI Diagnosis
// ---------------------------------------------------------------------------

@Composable
fun AiDiagnosisScreen(
    padding: PaddingValues,
    arabic: Boolean,
    vehicleId: String?,
    vehicleName: String?,
    initialCode: String?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var complaint by remember { mutableStateOf("") }
    var whenHappens by remember { mutableStateOf("") }
    var engineState by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runAi() {
        scope.launch {
            loading = true
            error = null
            raw = null
            runCatching {
                DiagnosticService().runDiagnostic(
                    vehicleModelId = vehicleId,
                    userVehicleId = null,
                    complaint = complaint.trim(),
                    language = if (arabic) "ar" else "fr",
                    codes = listOfNotNull(initialCode),
                    symptoms = buildJsonObject {
                        put("source", "ai_screen")
                        put("when_happens", whenHappens)
                        put("engine_state", engineState)
                    },
                    vehicle = buildJsonObject {
                        put("model_id", vehicleId ?: "")
                        put("model_name", vehicleName ?: "")
                    }
                )
            }.onSuccess { response -> raw = response.toString() }
                .onFailure { error = it.message ?: context.getString(R.string.ai_unavailable, "") }
            loading = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { CarDiagTopBar(stringResource(R.string.ai_title), stringResource(R.string.ai_subtitle), onBack = onBack) }
        item { CarDiagInfoCard(stringResource(R.string.ai_disclaimer), stringResource(R.string.ai_interpretation_only)) }
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = complaint, onValueChange = { complaint = it; error = null },
                    modifier = Modifier.fillMaxWidth(), minLines = 3,
                    label = { Text(stringResource(R.string.symptom_complaint_label)) },
                    placeholder = { Text(stringResource(R.string.symptom_complaint_placeholder)) }
                )
                OutlinedTextField(
                    value = whenHappens, onValueChange = { whenHappens = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                    label = { Text(stringResource(R.string.symptom_when_label)) },
                    placeholder = { Text(stringResource(R.string.symptom_when_placeholder)) }
                )
                OutlinedTextField(
                    value = engineState, onValueChange = { engineState = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                    label = { Text(stringResource(R.string.symptom_engine_label)) },
                    placeholder = { Text(stringResource(R.string.symptom_engine_placeholder)) }
                )
            }
        }
        item {
            Button(onClick = ::runAi, enabled = !loading && complaint.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ai_analyze))
            }
        }
        if (loading) item { CarDiagLoadingState(stringResource(R.string.symptom_analyzing)) }
        error?.let { item { CarDiagInfoCard(stringResource(R.string.state_error), it) } }
        raw?.let { item { AiStructuredCard(it, arabic) } }
    }
}

@Composable
private fun AiStructuredCard(raw: String, arabic: Boolean) {
    val parsed = runCatching { Json.parseToJsonElement(raw).jsonObject["diagnosis"]?.jsonObject }.getOrNull()
    val summary = parsed?.get("summary")?.jsonPrimitive?.contentOrEmptySafe() ?: raw
    val severity = parsed?.get("severity")?.jsonPrimitive?.contentOrEmptySafe() ?: "unknown"
    val confidence = parsed?.get("confidence")?.jsonPrimitive?.contentOrEmptySafe() ?: "-"
    val likelyCauses = parsed?.get("likely_causes")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrEmptySafe() }.orEmpty()
    val tests = parsed?.get("recommended_tests")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrEmptySafe() }.orEmpty()
    val repairs = parsed?.get("repair_guidance")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrEmptySafe() }.orEmpty()
    val safety = parsed?.get("safety_notes")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrEmptySafe() }.orEmpty()

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = CarDiagShapes.Card) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ai_label), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
            Text(stringResource(R.string.ai_evidence_required), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(stringResource(R.string.ai_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(summary)
            Text("${stringResource(R.string.dtc_detail_severity)}: $severity  •  ${stringResource(R.string.ai_confidence)}: $confidence", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (likelyCauses.isNotEmpty()) {
                Text(stringResource(R.string.ai_likely_causes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                likelyCauses.forEach { Text("• $it") }
            }
            if (tests.isNotEmpty()) {
                Text(stringResource(R.string.ai_recommended_tests), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                tests.forEach { Text("• $it") }
            }
            if (repairs.isNotEmpty()) {
                Text(stringResource(R.string.ai_repair_guidance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                repairs.forEach { Text("• $it") }
            }
            if (safety.isNotEmpty()) {
                Text(stringResource(R.string.ai_safety_notes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                safety.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrEmptySafe(): String =
    runCatching { content }.getOrDefault("")

// ---------------------------------------------------------------------------
// Vehicle profile
// ---------------------------------------------------------------------------

@Composable
fun VehicleProfileScreen(
    padding: PaddingValues,
    arabic: Boolean,
    vehicleId: String?,
    vehicleName: String?,
    onBack: () -> Unit,
    onOpenDtc: (String) -> Unit,
    onOpenObd: () -> Unit
) {
    // VehicleProfileScreen delegates to the existing ExactVehicleProfileScreen
    // so we keep the previously validated engine/trims/ECU logic. Only the
    // chrome (top bar / actions) is added here.
    val ui = UiModel(id = vehicleId.orEmpty(), name = vehicleName.orEmpty(), imageUrl = null)
    Column(Modifier.fillMaxSize().padding(padding)) {
        CarDiagTopBar(
            title = vehicleName ?: stringResource(R.string.state_unavailable),
            subtitle = stringResource(R.string.profile_tab_overview),
            onBack = onBack
        )
        if (vehicleId.isNullOrBlank()) {
            CarDiagEmptyState(stringResource(R.string.home_no_vehicle), stringResource(R.string.home_no_vehicle_desc))
        } else {
            ExactVehicleProfileScreen(model = ui, onBack = onBack)
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun contextString(arabic: Boolean, ar: String, fr: String): String = if (arabic) ar else fr

internal fun contextStringPlain(arabic: Boolean, ar: String, fr: String): String = if (arabic) ar else fr

/**
 * Avoids breaking on a missing field in an unexpected JsonPrimitive subtype.
 */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? = runCatching { content }.getOrNull()
