package dz.cardiag.app

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDtcLink(@SerialName("code_id") val codeId:String,@SerialName("model_id") val modelId:String?=null,@SerialName("generation_id") val generationId:String?=null)
@Serializable
data class ProfileDtc(val id:String,val code:String,val system:String?=null,@SerialName("title_fr") val titleFr:String?=null,@SerialName("description_fr") val descriptionFr:String?=null,val severity:String?=null,@SerialName("causes_fr") val causesFr:String?=null,@SerialName("diagnostic_steps_fr") val stepsFr:String?=null,@SerialName("repair_summary_fr") val repairFr:String?=null)
@Serializable
data class ProfileGeneration(val id:String,@SerialName("model_id") val modelId:String?=null,val name:String?=null,@SerialName("year_from") val yearFrom:Int?=null,@SerialName("year_to") val yearTo:Int?=null)

@Composable
fun VehicleProfileProScreen(model:UiModel,onBack:()->Unit){
 val context=androidx.compose.ui.platform.LocalContext.current
 val scope=rememberCoroutineScope()
 var generations by remember{mutableStateOf(emptyList<ProfileGeneration>())}
 var dtcs by remember{mutableStateOf(emptyList<ProfileDtc>())}
 var loading by remember{mutableStateOf(true)}
 var error by remember{mutableStateOf<String?>(null)}
 var selected by remember{mutableStateOf<ProfileDtc?>(null)}
 fun load(){scope.launch{loading=true;error=null;runCatching{
  val gs=SupabaseClient.client.from("vehicle_generations").select(Columns.list("id","model_id","name","year_from","year_to")).decodeList<ProfileGeneration>().filter{it.modelId==model.id}
  val ids=gs.map{it.id}.toSet()
  val links=SupabaseClient.client.from("diagnostic_code_vehicles").select(Columns.list("code_id","model_id","generation_id")).decodeList<ProfileDtcLink>().filter{it.modelId==model.id||it.generationId in ids}
  val cids=links.map{it.codeId}.distinct()
  val cs=if(cids.isEmpty())emptyList() else SupabaseClient.client.from("diagnostic_codes").select(Columns.list("id","code","system","title_fr","description_fr","severity","causes_fr","diagnostic_steps_fr","repair_summary_fr")).decodeList<ProfileDtc>().filter{it.id in cids}.sortedBy{it.code}
  generations=gs;dtcs=cs
 }.onFailure{error=it.message?:"Erreur Supabase"};loading=false}}
 LaunchedEffect(model.id){load()}
 fun diagnose(d:ProfileDtc){context.startActivity(Intent(context,GuidedDiagnosisActivity::class.java).apply{putExtra("model_id",model.id);putExtra("model_name",model.name);putExtra("dtc_id",d.id);putExtra("dtc_code",d.code)})}
 Scaffold(topBar={TopAppBar(title={Text(model.name,fontWeight=FontWeight.Black)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Retour")}},actions={IconButton(onClick={load}){Icon(Icons.Default.Refresh,"Actualiser")}})}){p->
  when{loading->Box(Modifier.fillMaxSize().padding(p),contentAlignment=Alignment.Center){CircularProgressIndicator()};error!=null->Column(Modifier.fillMaxSize().padding(p).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text("Erreur",fontWeight=FontWeight.Bold);Text(error!!);Button(onClick={load}){Text("Réessayer")}};else->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   item{Text("VEHICLE PROFILE",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Black);Text(model.name,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);Text("${generations.size} générations • ${dtcs.size} DTC")}
   item{Text("Générations",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
   items(generations){g->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(g.name?:"Génération",fontWeight=FontWeight.Bold);Text(listOfNotNull(g.yearFrom?.toString(),g.yearTo?.toString()).joinToString(" – "))}}}
   item{Text("DTC compatibles",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
   items(dtcs.take(80)){d->Card(Modifier.fillMaxWidth(),onClick={selected=d}){Column(Modifier.padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Warning,null);Spacer(Modifier.width(8.dp));Text(d.code,fontWeight=FontWeight.Black);Spacer(Modifier.weight(1f));Text(d.severity?:"info")};Text(d.titleFr?:d.descriptionFr?:"Code défaut")}}}
   item{Button(onClick={dtcs.firstOrNull()?.let(::diagnose)},enabled=dtcs.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Build,null);Spacer(Modifier.width(8.dp));Text("Lancer le diagnostic")}}
  }}
 }
 selected?.let{d->AlertDialog(onDismissRequest={selected=null},title={Text(d.code)},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text(d.descriptionFr?:d.titleFr?:"Code défaut");d.system?.let{Text("Système: $it")};d.causesFr?.let{Text("Causes: $it")};d.stepsFr?.let{Text("Diagnostic: $it")};d.repairFr?.let{Text("Réparation: $it")}}},confirmButton={Button(onClick={selected=null;diagnose(d)}){Text("Diagnostic guidé")}},dismissButton={TextButton(onClick={selected=null}){Text("Fermer")}})}
}
