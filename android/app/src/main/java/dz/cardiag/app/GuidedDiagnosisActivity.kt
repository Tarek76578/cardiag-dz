package dz.cardiag.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.DiagnosticService
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Serializable
data class DtcGuide(@SerialName("id") val id: String? = null,val code: String,val system: String? = null,val category: String? = null,val severity: String? = null,@SerialName("description_fr") val descriptionFr: String? = null,@SerialName("causes_fr") val causesFr: String? = null,@SerialName("diagnostic_steps_fr") val diagnosticStepsFr: String? = null,@SerialName("repair_summary_fr") val repairSummaryFr: String? = null)

class GuidedDiagnosisActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modelId = intent.getStringExtra("model_id")
        val modelName = intent.getStringExtra("model_name") ?: "Véhicule"
        val code = intent.getStringExtra("dtc_code")?.trim()?.uppercase().orEmpty()
        setContent { GuidedDiagnosisScreen(modelId, modelName, code) }
    }
}

@Composable
private fun GuidedDiagnosisScreen(modelId: String?, modelName: String, initialCode: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(initialCode) }
    var guide by remember { mutableStateOf<DtcGuide?>(null) }
    var loading by remember { mutableStateOf(false) }
    var aiLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var aiResult by remember { mutableStateOf<String?>(null) }

    fun loadGuide() {
        val normalized = code.trim().uppercase()
        if (!normalized.matches(Regex("[PBCU][0-3][0-9A-F]{3}"))) { errorMessage = "Entrez un code DTC valide, par exemple P0301."; return }
        scope.launch {
            loading = true; errorMessage = null; guide = null; aiResult = null
            runCatching {
                SupabaseClient.client.from("diagnostic_codes").select(Columns.list("id","code","system","category","severity","description_fr","causes_fr","diagnostic_steps_fr","repair_summary_fr")) { filter { eq("code", normalized) } }.decodeList<DtcGuide>().firstOrNull()
            }.onSuccess { result -> guide = result; if (result == null) errorMessage = "Code $normalized introuvable dans la base DTC." }
             .onFailure { errorMessage = "Impossible de charger le DTC: ${it.message ?: "erreur réseau"}" }
            loading = false
        }
    }

    fun runAi() {
        val normalized = code.trim().uppercase()
        if (guide == null) { errorMessage = "Chargez d'abord un DTC valide."; return }
        scope.launch {
            aiLoading = true; errorMessage = null; aiResult = null
            runCatching {
                DiagnosticService().runDiagnostic(
                    vehicleModelId = modelId,
                    userVehicleId = null,
                    complaint = "Guided DTC $normalized",
                    language = "fr",
                    codes = listOf(normalized),
                    symptoms = buildJsonObject { put("source", "guided_dtc") },
                    measurements = buildJsonObject {},
                    vehicle = buildJsonObject { put("model_id", modelId ?: ""); put("model_name", modelName) }
                )
            }.onSuccess { aiResult = it.toString() }
             .onFailure { errorMessage = it.message ?: "Erreur du moteur AI." }
            aiLoading = false
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Diagnostic guidé") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item { Text(modelName, style=MaterialTheme.typography.titleLarge); Text(if(modelId!=null) "Véhicule sélectionné • contexte actif" else "Diagnostic sans OBD", color=MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text("Diagnostic sans OBD",style=MaterialTheme.typography.titleMedium); Text("La base DTC et CarDiag AI peuvent expliquer un défaut sans ELM327. L'OBD devient nécessaire pour les mesures réelles.",color=MaterialTheme.colorScheme.onSurfaceVariant) } } }
            item { OutlinedTextField(value=code,onValueChange={code=it.uppercase();guide=null;aiResult=null;errorMessage=null},modifier=Modifier.fillMaxWidth(),label={Text("Code DTC")},placeholder={Text("Ex. P0301")},singleLine=true) }
            item { Button(onClick=::loadGuide,enabled=code.isNotBlank()&&!loading&&!aiLoading,modifier=Modifier.fillMaxWidth()) { Text(if(loading) "Chargement…" else "Rechercher le DTC") } }
            item { OutlinedButton(onClick={context.startActivity(Intent(context,ObdScannerActivity::class.java).apply{putExtra("model_id",modelId);putExtra("model_name",modelName);putExtra("dtc_code",code.ifBlank{null})})},enabled=!loading&&!aiLoading,modifier=Modifier.fillMaxWidth()) { Text("Passer au diagnostic OBD / Live Data") } }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            errorMessage?.let { message -> item { Text(message,color=MaterialTheme.colorScheme.error) } }
            guide?.let { d ->
                item { DtcCard("${d.code} • ${d.system ?: "Système inconnu"}",d.descriptionFr ?: "Description non disponible") }
                item { DtcCard("Sévérité / catégorie",listOfNotNull(d.severity,d.category).joinToString(" • ").ifBlank{"Non renseigné"}) }
                item { DtcCard("Causes probables",d.causesFr ?: "Non renseignées") }
                item { DtcCard("Étapes de diagnostic",d.diagnosticStepsFr ?: "Non renseignées") }
                item { DtcCard("Réparation",d.repairSummaryFr ?: "Non renseignée") }
                item { Button(onClick=::runAi,enabled=!aiLoading&&!loading,modifier=Modifier.fillMaxWidth()) { Text(if(aiLoading) "Analyse AI en cours…" else "Analyser avec CarDiag AI") } }
            }
            aiResult?.let { raw -> item { AiResultCard(raw) } }
        }
    }
}

@Composable private fun DtcCard(title:String,body:String) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(title,style=MaterialTheme.typography.titleMedium); Text(body) } } }
@Composable private fun AiResultCard(raw:String) {
    val diagnosis = runCatching { Json.parseToJsonElement(raw).jsonObject["diagnosis"]?.jsonObject }.getOrNull()
    val summary = diagnosis?.get("summary")?.toString()?.trim('"') ?: "Analyse AI reçue."
    val severity = diagnosis?.get("severity")?.toString()?.trim('"') ?: "unknown"
    val confidence = diagnosis?.get("confidence")?.toString()?.trim('"') ?: "-"
    val causes = diagnosis?.get("likely_causes")?.toString() ?: "[]"
    val tests = diagnosis?.get("recommended_tests")?.toString() ?: "[]"
    val repairs = diagnosis?.get("repair_guidance")?.toString() ?: "[]"
    val safety = diagnosis?.get("safety_notes")?.toString() ?: "[]"
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) { Text("CarDiag AI",style=MaterialTheme.typography.titleLarge); Text(summary,style=MaterialTheme.typography.bodyLarge); Text("Sévérité: $severity • Confiance: $confidence"); Text("Causes probables: $causes"); Text("Tests recommandés: $tests"); Text("Conseils de réparation: $repairs"); Text("Sécurité: $safety",color=MaterialTheme.colorScheme.error); Text("لا تستبدل قطعة اعتمادًا على AI وحده؛ أكّد التشخيص بقياسات واختبارات فعلية.",color=MaterialTheme.colorScheme.onSurfaceVariant) } }
}
