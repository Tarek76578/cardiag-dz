package dz.cardiag.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dz.cardiag.app.core.AuthService
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UiModel(val id: String, val name: String, val imageUrl: String? = null)
@Serializable
data class ExactVehicle(val id: String, @SerialName("make_id") val makeId: String, val name: String, @SerialName("year_from") val yearFrom: Int? = null, @SerialName("year_to") val yearTo: Int? = null, val generation: String? = null, @SerialName("image_url") val imageUrl: String? = null)
@Serializable
data class ExactMake(val id: String, val name: String)
@Serializable
data class GarageVehicle(val id: String, @SerialName("model_id") val modelId: String, @SerialName("make_id") val makeId: String, val nickname: String? = null, val vin: String? = null, val mileage: Int? = null, val year: Int? = null, @SerialName("is_primary") val isPrimary: Boolean = false)
@Serializable
data class GarageVehicleInsert(@SerialName("user_id") val userId: String, @SerialName("make_id") val makeId: String, @SerialName("model_id") val modelId: String, val nickname: String? = null, @SerialName("is_primary") val isPrimary: Boolean = false)

data class Copy(val home:String,val diagnostic:String,val garage:String,val history:String,val more:String,val smart:String,val search:String,val catalog:String,val back:String)
private val FR=Copy("Accueil","Diagnostic","Garage","Historique","Plus","SMART VEHICLE DIAGNOSTICS","Rechercher une marque ou un modèle","Catalogue véhicules","Retour")
private val AR=Copy("الرئيسية","التشخيص","المرآب","السجل","المزيد","تشخيص السيارات الذكي","ابحث عن الماركة أو الموديل","كتالوج السيارات","رجوع")

@Composable
fun CarDiagExactApp() {
    val context=LocalContext.current
    var dark by remember { mutableStateOf(true) }
    var arabic by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ExactVehicle?>(null) }
    val c=if(arabic) AR else FR
    val scheme=if(dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme=scheme) {
        CompositionLocalProvider(LocalLayoutDirection provides if(arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            if(selected!=null) ExactVehicleProfileScreen(UiModel(selected!!.id,selected!!.name,selected!!.imageUrl)){selected=null}
            else Scaffold(bottomBar={NavigationBar{val icons=listOf(Icons.Default.Home,Icons.Default.Build,Icons.Default.Garage,Icons.Default.History,Icons.Default.Settings);listOf(c.home,c.diagnostic,c.garage,c.history,c.more).forEachIndexed{i,label->NavigationBarItem(tab==i,{tab=i},{Icon(icons[i],label)},label={Text(label)})}}}){p->when(tab){0->HomeScreen(p,c){selected=it};1->DiagnosticHub(p);2->GarageScreen(p,c){selected=it};3->HistoryScreen(p,c);else->MoreScreen(p,c,dark,{dark=!dark},{arabic=!arabic})}}}
        }
    }
}

@Composable private fun Header(title:String,subtitle:String){Column(Modifier.fillMaxWidth().padding(20.dp)){Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant)}}

@Composable private fun HomeScreen(p:PaddingValues,c:Copy,onVehicle:(ExactVehicle)->Unit){val scope=rememberCoroutineScope();var models by remember{mutableStateOf<List<ExactVehicle>>(emptyList())};var makes by remember{mutableStateOf<List<ExactMake>>(emptyList())};var query by remember{mutableStateOf("")};LaunchedEffect(Unit){scope.launch{models=runCatching{SupabaseClient.client.from("vehicle_models").select(Columns.list("id","make_id","name","year_from","year_to","generation","image_url")).decodeList()}.getOrDefault(emptyList());makes=runCatching{SupabaseClient.client.from("vehicle_makes").select(Columns.list("id","name")).decodeList()}.getOrDefault(emptyList())}};val filtered=models.filter{query.isBlank()||it.name.contains(query,true)||makes.firstOrNull{m->m.id==it.makeId}?.name?.contains(query,true)==true};LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(bottom=20.dp)){item{Header("CarDiag",c.smart)};item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(16.dp),singleLine=true,placeholder={Text(c.search)},leadingIcon={Icon(Icons.Default.Search,null)})};item{Text(c.catalog,Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black)};items(filtered.take(50),key={it.id}){v->Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=5.dp).clickable{onVehicle(v)}){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){AsyncImage(v.imageUrl,v.name,Modifier.size(78.dp),contentScale=ContentScale.Crop);Spacer(Modifier.width(12.dp));Column{Text(makes.firstOrNull{m->m.id==v.makeId}?.name?:"Vehicle",color=MaterialTheme.colorScheme.primary);Text(v.name,fontWeight=FontWeight.Bold);Text(listOfNotNull(v.generation,v.yearFrom?.toString(),v.yearTo?.toString()).joinToString(" • "),color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}}}

@Composable private fun DiagnosticHub(p:PaddingValues){val ctx=LocalContext.current;fun open(a:Class<*>)=ctx.startActivity(Intent(ctx,a));Column(Modifier.fillMaxSize().padding(p).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Header("Diagnostic","SMART VEHICLE DIAGNOSTICS");ActionCard("OBD-II Scanner",Icons.Default.Build){open(ObdScannerActivity::class.java)};ActionCard("Live Data",Icons.Default.Speed){open(LiveDataProActivity::class.java)};ActionCard("DTC & Faults",Icons.Default.Warning){open(GuidedDiagnosisActivity::class.java)};ActionCard("VIN Identity",Icons.Default.DirectionsCar){open(ObdScannerActivity::class.java)};ActionCard("AI Diagnosis • Sans OBD",Icons.Default.Build){open(AiSymptomDiagnosisActivity::class.java)}}}
@Composable private fun ActionCard(title:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick)){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(14.dp));Text(title,Modifier.weight(1f),fontWeight=FontWeight.Bold);Icon(Icons.Default.ChevronRight,null)}}}

@Composable private fun GarageScreen(p:PaddingValues,c:Copy,onVehicle:(ExactVehicle)->Unit){val scope=rememberCoroutineScope();var rows by remember{mutableStateOf<List<GarageVehicle>>(emptyList())};var models by remember{mutableStateOf<List<ExactVehicle>>(emptyList())};var makes by remember{mutableStateOf<List<ExactMake>>(emptyList())};var loading by remember{mutableStateOf(true)};var add by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};fun load(){scope.launch{loading=true;val user=AuthService().currentUser;if(user==null){error=if(c==AR)"سجّل الدخول لإدارة سياراتك"else"Connectez-vous pour gérer votre garage";loading=false;return@launch};rows=runCatching{SupabaseClient.client.from("user_vehicles").select(Columns.list("id","model_id","make_id","nickname","vin","mileage","year","is_primary")).decodeList()}.getOrElse{error=it.message;emptyList()};models=runCatching{SupabaseClient.client.from("vehicle_models").select(Columns.list("id","make_id","name","year_from","year_to","generation","image_url")).decodeList()}.getOrDefault(emptyList());makes=runCatching{SupabaseClient.client.from("vehicle_makes").select(Columns.list("id","name")).decodeList()}.getOrDefault(emptyList());loading=false}};LaunchedEffect(Unit){load()};Column(Modifier.fillMaxSize().padding(p).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Header(c.garage,c.smart);Button({add=true},Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text(if(c==AR)"إضافة سيارة"else"Ajouter un véhicule")};error?.let{Text(it,color=MaterialTheme.colorScheme.error)};if(loading)Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){CircularProgressIndicator()}else LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(rows,key={it.id}){r->val m=models.firstOrNull{it.id==r.modelId};Card(Modifier.fillMaxWidth().clickable{m?.let(onVehicle)}){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){AsyncImage(m?.imageUrl,m?.name,Modifier.size(76.dp),contentScale=ContentScale.Crop);Spacer(Modifier.width(12.dp));Column{Text(r.nickname?:m?.name?:"Vehicle",fontWeight=FontWeight.Bold);Text(makes.firstOrNull{it.id==r.makeId}?.name?:(m?.name?:""));r.vin?.let{Text("VIN • $it",color=MaterialTheme.colorScheme.primary)}}}}}}};if(add)AlertDialog({add=false},{Text(if(c==AR)"اختر سيارة"else"Choisir un véhicule")},{LazyColumn{items(models.take(50),key={it.id}){m->Text(m.name,Modifier.fillMaxWidth().clickable{scope.launch{val uid=AuthService().currentUser?.id;if(uid!=null){runCatching{SupabaseClient.client.from("user_vehicles").insert(GarageVehicleInsert(uid,m.makeId,m.id,m.name,rows.isEmpty()))}.onFailure{error=it.message};add=false;load()}}}.padding(14.dp))}}},{TextButton({add=false}){Text(c.back)}})}}

@Composable private fun HistoryScreen(p:PaddingValues,c:Copy){Column(Modifier.fillMaxSize().padding(p).padding(20.dp)){Header(c.history,c.smart);ActionCard(if(c==AR)"لا توجد تشخيصات محفوظة"else"Aucun diagnostic enregistré",Icons.Default.History){}}}
@Composable private fun MoreScreen(p:PaddingValues,c:Copy,dark:Boolean,onTheme:()->Unit,onLanguage:()->Unit){Column(Modifier.fillMaxSize().padding(p).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Header(c.more,c.smart);ActionCard(if(dark)if(c==AR)"الوضع الداكن"else"Mode sombre"else if(c==AR)"الوضع الفاتح"else"Mode clair",Icons.Default.Brightness6,onTheme);ActionCard(if(c==AR)"تغيير اللغة"else"Changer la langue",Icons.Default.Language,onLanguage)}}
