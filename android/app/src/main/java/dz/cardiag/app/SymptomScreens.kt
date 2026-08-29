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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.DiagnosticService
import dz.cardiag.app.ui.theme.CarDiagShapes
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Canonical symptom-diagnosis screen. The categories, specific symptoms and
 * contextual questions come from [SymptomCatalog] / [SymptomQuestions] so the
 * UI never invents its own taxonomy. The user can multi-select specific
 * symptoms; the chip row shows the localized labels and the contextual
 * questions adapt to the selection.
 */
@Composable
fun SymptomDiagnosisScreen(
    padding: PaddingValues,
    arabic: Boolean,
    hasVehicle: Boolean,
    onBack: () -> Unit,
    onOpenAi: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var category by remember { mutableStateOf(SymptomCategoryId.OTHER) }
    val selected = remember { mutableStateListOf<String>() }
    val answers = remember { mutableStateListOf<Pair<String, String>>() }
    var complaint by remember { mutableStateOf("") }
    var whenHappens by remember { mutableStateOf("") }
    var engineState by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun toggle(id: String) {
        if (selected.contains(id)) selected.remove(id) else selected.add(id)
        answers.clear()
    }

    fun setAnswer(questionIdRes: Int, optionId: String) {
        val key = questionIdRes.toString()
        answers.removeAll { it.first == key }
        answers.add(key to optionId)
    }

    val categoryEntries = remember(category) { SymptomCatalog.byCategory()[category].orEmpty() }
    val questions = remember(selected.toList()) { SymptomQuestions.forSymptoms(selected.toSet()) }
    val answerMap = remember(answers.toList()) { answers.toMap() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { CarDiagTopBar(stringResource(R.string.symptom_title), stringResource(R.string.symptom_subtitle), onBack = onBack) }
        if (!hasVehicle) {
            item { CarDiagInfoCard(stringResource(R.string.symptom_title), stringResource(R.string.symptom_no_vehicle)) }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.symptom_category_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SymptomCategoryRow(current = category, onSelect = { newCategory ->
                    if (newCategory != category) {
                        category = newCategory
                        selected.clear()
                        answers.clear()
                    }
                })
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.symptom_specific_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.symptom_specific_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                if (categoryEntries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.state_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SymptomSpecificChipFlow(
                        entries = categoryEntries,
                        selected = selected.toSet(),
                        onToggle = ::toggle
                    )
                }
                if (selected.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.symptom_selected_count, selected.size),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        if (questions.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.symptom_questions_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    questions.forEach { q ->
                        SymptomQuestionCard(
                            prompt = stringResource(q.idRes),
                            options = q.options.map { (labelRes, id) -> stringResource(labelRes) to id },
                            selectedId = answerMap[q.idRes.toString()],
                            onSelect = { setAnswer(q.idRes, it) }
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = stringResource(R.string.symptom_questions_none),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = complaint, onValueChange = { complaint = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text(stringResource(R.string.symptom_complaint_label)) },
                    placeholder = { Text(stringResource(R.string.symptom_complaint_placeholder)) }
                )
                OutlinedTextField(
                    value = whenHappens, onValueChange = { whenHappens = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text(stringResource(R.string.symptom_when_label)) },
                    placeholder = { Text(stringResource(R.string.symptom_when_placeholder)) }
                )
                OutlinedTextField(
                    value = engineState, onValueChange = { engineState = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text(stringResource(R.string.symptom_engine_label)) },
                    placeholder = { Text(stringResource(R.string.symptom_engine_placeholder)) }
                )
            }
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        runCatching {
                            DiagnosticService().runDiagnostic(
                                vehicleModelId = null,
                                userVehicleId = null,
                                complaint = complaint.trim().ifBlank {
                                    selected.joinToString(", ") { it }
                                },
                                language = if (arabic) "ar" else "fr",
                                symptoms = buildJsonObject {
                                    put("source", "symptom_catalog")
                                    put("category", category.name)
                                    put("selected_symptoms", buildJsonArray { selected.forEach { add(it) } })
                                    put("answers", buildJsonObject { answerMap.forEach { (k, v) -> put(k, v) } })
                                    put("when_happens", whenHappens)
                                    put("engine_state", engineState)
                                }
                            )
                        }.onSuccess { response ->
                            val diag = response["diagnosis"]?.toString().orEmpty()
                            summary = if (diag.isBlank()) context.getString(R.string.ai_no_evidence) else diag
                        }.onFailure {
                            error = it.message ?: context.getString(R.string.ai_unavailable, "")
                        }
                        loading = false
                    }
                },
                enabled = !loading && (complaint.isNotBlank() || selected.isNotEmpty()) && hasVehicle,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) { Text(stringResource(R.string.symptom_analyze)) }
        }
        if (loading) item { CarDiagLoadingState(stringResource(R.string.symptom_analyzing)) }
        summary?.let { item { CarDiagInfoCard(stringResource(R.string.ai_summary), it) } }
        error?.let { item { CarDiagInfoCard(stringResource(R.string.state_error), it) } }
        item {
            OutlinedButton(
                onClick = onOpenAi,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) { Text(stringResource(R.string.home_ai_assistant)) }
        }
    }
}

@Composable
private fun SymptomCategoryRow(current: SymptomCategoryId, onSelect: (SymptomCategoryId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SymptomCategoryId.values().toList().chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { cat ->
                    AssistChip(
                        onClick = { onSelect(cat) },
                        label = { Text(stringResource(cat.labelRes)) },
                        colors = if (cat == current)
                            AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else
                            AssistChipDefaults.assistChipColors()
                    )
                }
            }
        }
    }
}

@Composable
private fun SymptomSpecificChipFlow(
    entries: List<SymptomEntry>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { entry ->
                    FilterChip(
                        selected = selected.contains(entry.id),
                        onClick = { onToggle(entry.id) },
                        label = { Text(stringResource(entry.labelRes)) },
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SymptomQuestionCard(
    prompt: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = CarDiagShapes.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(prompt, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (label, id) ->
                    FilterChip(
                        selected = selectedId == id,
                        onClick = { onSelect(id) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}
