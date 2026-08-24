package dz.cardiag.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val ProfileTeal = Color(0xFF48D7C5)

@Serializable
data class ProfileImage(val id:String,@SerialName("image_url") val imageUrl:String,@SerialName("model_id") val modelId:String?=null,@SerialName("generation_id") val generationId:String?=null,@SerialName("is_primary") val primary:Boolean=false,@SerialName("sort_order") val sortOrder:Int=0,@SerialName("alt_text_fr") val altFr:String?=null)
@Serializable
data class ProfileEcuLink(val id:String,@SerialName("generation_id") val generationId:String,@SerialName("engine_id") val engineId:String?=null,@SerialName("ecu_id") val ecuId:String,val required:Boolean=true,val notes:String?=null)
@Serializable
data class ProfileEcu(val id:String,val manufacturer:String?=null,val name:String,val family:String?=null,@SerialName("ecu_type") val ecuType:String="other",val protocols:List<String> = emptyList(),@SerialName("part_numbers") val partNumbers:List<String> = emptyList(),@SerialName("description_fr") val descriptionFr:String?=null)
@Serializable
data class ProfileDtcLink(val id:String,@SerialName("code_id") val codeId:String,@SerialName("model_id") val modelId:String?=null,@SerialName("generation_id") val generationId:String?=null,@SerialName("engine_id") val engineId:String?=null,@SerialName("ecu_id") val ecuId:String?=null,val applicability:String="confirmed")
@Serializable
data class ProfileDtc(val id:String,val code:String,val system:String?=null,@SerialName("title_fr") val titleFr:String?=null,@SerialName("description_fr") val descriptionFr:String?=null,val severity:String?=null,val category:String?=null,@SerialName("causes_fr") val causesFr:String?=null,@SerialName("diagnostic_steps_fr") val stepsFr:String?=null,@SerialName("repair_summary_fr") val repairFr:String?=null)
@Serializable
data class ProfileSpec(val id:String,@SerialName("generation_id") val generationId:String,@SerialName("engine_id") val engineId:String?=null,val key:String,@SerialName("value_text") val valueText:String?=null,@SerialName("value_number") val valueNumber:Double?=null,val unit:String?=null)

@Composable
fun VehicleProfileProScreen(model:UiModel,onBack:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var generations by remember(model.id){mutableStateOf<List<UiGeneration>>(emptyList())}
    var engines by remember(model.id){mutableStateOf<List<UiEngine>>(emptyList())}
    var images by remember(model.id){mutableStateOf<List<ProfileImage>>(emptyList())}
    var ecuLinks by remember(model.id){mutableStateOf<List<ProfileEcuLink>>(emptyList())}
    var ecus by remember(model.id){mutableStateOf<List<ProfileEcu>>(emptyList())}
    var dtcLinks by remember(model.id){mutableStateOf<List<ProfileDtcLink>>(emptyList())}
    var dtcs by remember(model.id){mutableStateOf<List<ProfileDtc>>(emptyList())}
    var specs by remember(model.id){mutableStateOf<List<ProfileSpec>>(emptyList())}
    var loading by remember(model.id){mutableStateOf(true)}
    var error by remember(model.id){mutableStateOf<String?>(null)}
    var selectedDtc by remember{mutableStateOf<ProfileDtc?>(null)}

    fun load(){
        scope.launch {
            loading=true; error=null
            runCatching {
                val gens=SupabaseClient.client.from("vehicle_generations").select(Columns.list("id","model_id","name","code","year_from","year_to","body_type","platform_code","image_url")).decodeList<UiGeneration>().filter{it.modelId==model.id}
                val genIds=gens.map{it.id}.toSet()
                val imgs=SupabaseClient.client.from("vehicle_images").select(Columns.list("id","image_url","model_id","generation_id","is_primary","sort_order","alt_text_fr")).decodeList<ProfileImage>().filter{it.modelId==model.id || it.generationId in genIds}.sortedBy{it.sortOrder}
                val ens=SupabaseClient.client.from("vehicle_engines").select(Columns.list("id","generation_id","name","engine_code","fuel_type","displacement_cc","cylinders","power_hp","power_kw","torque_nm","transmission_types")).decodeList<UiEngine>().filter{it.generationId in genIds}
                val eLinks=SupabaseClient.client.from("vehicle_ecus").select(Columns.list("id","generation_id","engine_id","ecu_id","required","notes")).decodeList<ProfileEcuLink>().filter{it.generationId in genIds}
                val ecuIds=eLinks.map{it.ecuId}.distinct()
                val ecuRows=if(ecuIds.isEmpty()) emptyList() else SupabaseClient.client.from("ecu_modules").select(Columns.list("id","manufacturer","name","family","ecu_type","protocols","part_numbers","description_fr")).decodeList<ProfileEcu>().filter{it.id in ecuIds}
                val dLinks=SupabaseClient.client.from("diagnostic_code_vehicles").select(Columns.list("id","code_id","model_id","generation_id","engine_id","ecu_id","applicability")).decodeList<ProfileDtcLink>().filter{it.modelId==model.id || it.modelId==null && it.generationId in genIds}
                val codeIds=dLinks.map{it.codeId}.distinct()
                val codes=if(codeIds.isEmpty()) emptyList() else SupabaseClient.client.from("diagnostic_codes").select(Columns.list("id","code","system","title_fr","description_fr","severity","category","causes_fr","diagnostic_steps_fr","repair_summary_fr")).decodeList<ProfileDtc>().filter{it.id in codeIds}.sortedBy{it.code}
                val sp=SupabaseClient.client.from("vehicle_specifications").select(Columns.list("id","generation_id","engine_id","key","value_text","value_number","unit")).decodeList<ProfileSpec>().filter{it.generationId in genIds}
                generations=gens; images=imgs; engines=ens; ecuLinks=eLinks; ecus=ecuRows; dtcLinks=dLinks; dtcs=codes; specs=sp
            }.onFailure{error=it.message?:"Impossible de charger le profil"}
            loading=false
        }
    }
    LaunchedEffect(model.id){load()}

    fun openDiagnosis(dtc:ProfileDtc){context.startActivity(Intent(context,GuidedDiagnosisActivity::class.java).apply{putExtra("model_id",model.id);putExtra("model_name",model.name);putExtra("dtc_id",dtc.id);putExtra("dtc_code",dtc.code)})}
    val hero=images.firstOrNull{it.primary}?.imageUrl?:images.firstOrNull()?.imageUrl?:model.imageUrl
    Scaffold(topBar={TopAppBar(title={Text(model.name,fontWeight=FontWeight.Black)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Retour")}},actions={IconButton(onClick={::load}){Icon(Icons.Default.Refresh,"Actualiser")}})}){padding->
        when{
            loading->Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            error!=null->Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text("Erreur de chargement",fontWeight=FontWeight.Black);Text(error!!,color=MaterialTheme.colorScheme.error);Button(onClick={load}){Text("Réessayer")}}
            else->LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                item{Box(Modifier.fillMaxWidth().height(280.dp).background(Brush.verticalGradient(listOf(Color(0xFF123039),Color(0xFF071014))))){AsyncImage(model=hero,contentDescription=model.name,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop);Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xEE071014)))));Column(Modifier.align(Alignment.BottomStart).padding(20.dp)){Text("VEHICLE PROFILE",color=ProfileTeal,fontWeight=FontWeight.Black);Text(model.name,color=Color.White,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Black);Text("${generations.size} générations • ${engines.size} moteurs • ${ecus.size} ECU • ${dtcs.size} DTC",color=Color.White.copy(alpha=.8f))}}}
                item{Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Metric("${generations.size}","Générations",Modifier.weight(1f));Metric("${engines.size}","Moteurs",Modifier.weight(1f));Metric("${ecus.size}","ECU",Modifier.weight(1f));Metric("${dtcs.size}","DTC",Modifier.weight(1f))}}
                item{Section("Galerie",Icons.Default.PhotoLibrary)}
                item{if(images.isEmpty())Empty("Aucune image disponible") else LazyRow(contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){items(images){AsyncImage(model=it.imageUrl,contentDescription=it.altFr?:model.name,modifier=Modifier.size(220.dp,140.dp).clip(RoundedCornerShape(18.dp)),contentScale=ContentScale.Crop)}}}
                item{Section("Moteurs",Icons.Default.Settings)}
                if(engines.isEmpty())item{Empty("Aucune motorisation cataloguée")} else items(engines){e->EngineCard(e)}
                item{Section("ECU & électronique",Icons.Default.Memory)}
                if(ecus.isEmpty())item{Empty("Aucun ECU associé")} else items(ecus.distinctBy{it.id}){e->EcuCard(e,ecuLinks.count{it.ecuId==e.id})}
                item{Section("Codes défaut compatibles",Icons.Default.Warning)}
                if(dtcs.isEmpty())item{Empty("Aucun DTC lié à ce véhicule")} else items(dtcs.take(60)){d->DtcCard(d,dtcLinks.count{it.codeId==d.id}){selectedDtc=d}}
                item{Section("Spécifications techniques",Icons.Default.Tune)}
                if(specs.isEmpty())item{Empty("Aucune spécification détaillée")} else items(specs){s->SpecCard(s)}
                item{Card(Modifier.padding(horizontal=16.dp).fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Diagnostic de ce véhicule",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black);Text("Utilisez le profil comme contexte pour les DTC et les mesures OBD.",color=MaterialTheme.colorScheme.onSurfaceVariant);Button(onClick={dtcs.firstOrNull()?.let(::openDiagnosis)},enabled=dtcs.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Build,null);Spacer(Modifier.width(8.dp));Text("Lancer le diagnostic")}}}}
            }
        }
    }
    selectedDtc?.let{d->AlertDialog(onDismissRequest={selectedDtc=null},title={Text(d.code,fontWeight=FontWeight.Black)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(d.titleFr?:d.descriptionFr?:"Code défaut");d.system?.let{Text("Système: $it")};d.severity?.let{Text("Sévérité: $it")};d.causesFr?.let{Text("Causes: $it")};d.stepsFr?.let{Text("Diagnostic: $it")};d.repairFr?.let{Text("Réparation: $it")}},confirmButton={Button(onClick={selectedDtc=null;openDiagnosis(d)}){Text("Diagnostic guidé")}},dismissButton={TextButton(onClick={selectedDtc=null}){Text("Fermer")}})}
}

@Composable private fun Metric(value:String,label:String,modifier:Modifier)=Card(modifier,shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(10.dp).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){Text(value,color=ProfileTeal,fontWeight=FontWeight.Black);Text(label,style=MaterialTheme.typography.labelSmall)}}
@Composable private fun Section(title:String,icon:androidx.compose.ui.graphics.vector.ImageVector)=Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=ProfileTeal);Spacer(Modifier.width(8.dp));Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black)}
@Composable private fun Empty(text:String)=Card(Modifier.padding(horizontal=16.dp).fillMaxWidth()){Text(text,Modifier.padding(18.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable private fun EngineCard(e:UiEngine)=Card(Modifier.padding(horizontal=16.dp).fillMaxWidth(),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(e.name,fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleMedium);Text(listOfNotNull(e.engineCode,e.fuelType,e.displacementCc?.let{"$it cc"}).joinToString(" • "),color=MaterialTheme.colorScheme.onSurfaceVariant);Text(listOfNotNull(e.powerHp?.let{"%.0f hp".format(it)},e.powerKw?.let{"%.0f kW".format(it)},e.torqueNm?.let{"%.0f Nm".format(it)}).joinToString(" • "),color=ProfileTeal)}}
@Composable private fun EcuCard(e:ProfileEcu,count:Int)=Card(Modifier.padding(horizontal=16.dp).fillMaxWidth(),shape=RoundedCornerShape(20.dp)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Memory,null,tint=ProfileTeal);Column(Modifier.padding(start=12.dp).weight(1f)){Text(e.name,fontWeight=FontWeight.Black);Text(listOfNotNull(e.manufacturer,e.family,e.ecuType).joinToString(" • "),color=MaterialTheme.colorScheme.onSurfaceVariant)}Text("$count",color=ProfileTeal,fontWeight=FontWeight.Bold)}}
@Composable private fun DtcCard(d:ProfileDtc,count:Int,onClick:()->Unit)=Card(Modifier.padding(horizontal=16.dp).fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text(d.code,color=ProfileTeal,fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleLarge);Spacer(Modifier.width(10.dp));Text(d.severity?:"info",style=MaterialTheme.typography.labelSmall);Spacer(Modifier.weight(1f));Text("$count")};Text(d.titleFr?:d.descriptionFr?:"Code défaut",fontWeight=FontWeight.SemiBold);Text(d.system?:"Système",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall)}}
@Composable private fun SpecCard(s:ProfileSpec)=Card(Modifier.padding(horizontal=16.dp).fillMaxWidth()){Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(s.key,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text(s.valueText?:s.valueNumber?.toString()?:"—");s.unit?.let{Text(" $it")}}}
