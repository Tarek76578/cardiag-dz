package dz.cardiag.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DtcGuide(
    val id:String?=null,
    val code:String,
    val system:String?=null,
    val category:String?=null,
    val severity:String?=null,
    @SerialName("description_fr") val descriptionFr:String?=null,
    @SerialName("causes_fr") val causesFr:String?=null,
    @SerialName("diagnostic_steps_fr") val diagnosticStepsFr:String?=null,
    @SerialName("repair_summary_fr") val repairSummaryFr:String?=null
)

class GuidedDiagnosisActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val modelId=intent.getStringExtra("model_id");val modelName=intent.getStringExtra("model_name")?:"Véhicule";val initialCode=intent.getStringExtra("dtc_code")?:"P0301";setContent{GuidedDiagnosisScreen(modelId,modelName,initialCode)}}
}

@Composable private fun GuidedDiagnosisScreen(modelId:String?,modelName:String,initialCode:String){
 val scope=rememberCoroutineScope();var code by remember{mutableStateOf(initialCode)};var guide by remember{mutableStateOf<DtcGuide?>(null)};var loading by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
 fun load(){scope.launch{loading=true;error=null;runCatching{SupabaseClient.client.from("diagnostic_codes").select(Columns.list("id","code","system","category","severity","description_fr","causes_fr","diagnostic_steps_fr","repair_summary_fr")){filter{eq("code",code.trim().uppercase())}}.decodeList<DtcGuide>().firstOrNull()}.onSuccess{guide=it;if(it==null)error="Code DTC introuvable"}.onFailure{error=it.message?:"Impossible de charger le DTC"};loading=false}}
 LaunchedEffect(initialCode){load()}
 Scaffold(topBar={TopAppBar(title={Text("Diagnostic guidé")})}){padding->LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{Text(modelName,style=MaterialTheme.typography.titleLarge);Text(if(modelId!=null)"Véhicule sélectionné • contexte actif" else "Aucun véhicule sélectionné",color=MaterialTheme.colorScheme.onSurfaceVariant)}
  item{OutlinedTextField(value=code,onValueChange={code=it.uppercase()},modifier=Modifier.fillMaxWidth(),label={Text("Code DTC")},singleLine=true)}
  item{Button(onClick=::load,enabled=code.isNotBlank()&&!loading,modifier=Modifier.fillMaxWidth()){Text(if(loading)"Chargement…"else"Lancer le diagnostic")}}
  if(loading)item{LinearProgressIndicator(Modifier.fillMaxWidth())};error?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}}
  guide?.let{d->
   item{DtcCard("${d.code} • ${d.system?:"Système inconnu"}",d.descriptionFr?:"Description non disponible")}
   item{DtcCard("Sévérité / catégorie",listOfNotNull(d.severity,d.category).joinToString(" • ").ifBlank{"Non renseigné"})}
   item{DtcCard("Causes probables",d.causesFr?:"Non renseignées")}
   item{DtcCard("Étapes de diagnostic",d.diagnosticStepsFr?:"Non renseignées")}
   item{DtcCard("Réparation",d.repairSummaryFr?:"Non renseignée")}
   item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Prochaine étape",style=MaterialTheme.typography.titleMedium);Text("Connecter l'OBD, lire les PID compatibles avec ce moteur, comparer les valeurs et enregistrer la session de diagnostic.")}}}
  }
 }}
}

@Composable private fun DtcCard(title:String,body:String){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,style=MaterialTheme.typography.titleMedium);Text(body)}}}
