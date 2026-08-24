package dz.cardiag.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UiModel(val id: String, val name: String, val imageUrl: String? = null)

@Serializable
data class ExactVehicle(
    val id: String,
    @SerialName("make_id") val makeId: String,
    val name: String,
    @SerialName("year_from") val yearFrom: Int? = null,
    @SerialName("year_to") val yearTo: Int? = null,
    val generation: String? = null,
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class ExactMake(val id: String, val name: String)
@Serializable
data class SessionRow(val id: String)

@Serializable
data class GarageVehicle(
    val id: String,
    @SerialName("model_id") val modelId: String,
    @SerialName("make_id") val makeId: String,
    val nickname: String? = null,
    val vin: String? = null,
    val mileage: Int? = null,
    val year: Int? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false
)

@Serializable
data class GarageVehicleInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("make_id") val makeId: String,
    @SerialName("model_id") val modelId: String,
    val nickname: String? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false
)

private val DarkBg = Color(0xFF06090B)
private val DarkSurface = Color(0xFF0D1418)
private val DarkTeal = Color(0xFF48D7C5)
private val DarkText = Color(0xFFF5F8F8)
private val DarkMuted = Color(0xFF8B9A9F)
private val LightBg = Color(0xFFF4F7F7)
private val LightSurface = Color.White
private val LightTeal = Color(0xFF087F73)
private val LightText = Color(0xFF102024)
private val LightMuted = Color(0xFF647277)
private const val PREFS = "cardiag_ui"
private const val KEY_DARK = "dark_mode"
private const val KEY_LANG = "language"

private class UiPrefs(context: Context) {
    private val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var dark: Boolean get() = p.getBoolean(KEY_DARK, true); set(value) { p.edit().putBoolean(KEY_DARK, value).apply() }
    var lang: String get() = p.getString(KEY_LANG, "fr") ?: "fr"; set(value) { p.edit().putString(KEY_LANG, value).apply() }
}

private data class Copy(
    val home: String, val diagnostic: String, val garage: String, val history: String, val more: String,
    val smart: String, val hero: String, val launch: String, val search: String, val catalog: String,
    val models: String, val makes: String, val scanner: String, val language: String, val appearance: String,
    val dark: String, val light: String, val account: String, val about: String, val back: String,
    val noHistory: String, val historyHint: String, val noVehicles: String
)

private val FR = Copy("Accueil", "Diagnostic", "Garage", "Historique", "Plus", "SMART VEHICLE DIAGNOSTICS", "Votre voiture.\nVos données. Votre diagnostic.", "Lancer le diagnostic", "Rechercher une marque ou un modèle", "Catalogue véhicules", "MODÈLES", "MARQUES", "OBD-II • SCANNER", "Langue", "Apparence", "Mode sombre", "Mode clair", "Compte", "À propos", "Retour", "Aucun diagnostic enregistré", "Vos sessions de diagnostic apparaîtront ici.", "Aucun véhicule trouvé")
private val AR = Copy("الرئيسية", "التشخيص", "المرآب", "السجل", "المزيد", "تشخيص السيارات الذكي", "سيارتك.\nبياناتك. تشخيصك.", "بدء التشخيص", "ابحث عن الماركة أو الموديل", "كتالوج السيارات", "الموديلات", "الماركات", "ماسح OBD-II", "اللغة", "المظهر", "الوضع الداكن", "الوضع الفاتح", "الحساب", "حول التطبيق", "رجوع", "لا توجد تشخيصات محفوظة", "ستظهر جلسات التشخيص هنا.", "لم يتم العثور على سيارات")

@Composable
fun CarDiagExactApp() {
    val context = LocalContext.current
    val prefs = remember { UiPrefs(context) }
    var dark by remember { mutableStateOf(prefs.dark) }
    var lang by remember { mutableStateOf(prefs.lang) }
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ExactVehicle?>(null) }
    var languageDialog by remember { mutableStateOf(false) }
    val c = if (lang == "ar") AR else FR
    val direction = if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    val primary = if (dark) DarkTeal else LightTeal
    val bg = if (dark) DarkBg else LightBg
    val surface = if (dark) DarkSurface else LightSurface
    val text = if (dark) DarkText else LightText
    val muted = if (dark) DarkMuted else LightMuted
    val colors = if (dark) darkColorScheme(primary = primary, background = bg, surface = surface, onSurface = text, onSurfaceVariant = muted) else lightColorScheme(primary = primary, background = bg, surface = surface, onSurface = text, onSurfaceVariant = muted)
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            if (selected != null) ExactVehicleProfileScreen(model = UiModel(selected!!.id, selected!!.name, selected!!.imageUrl), onBack = { selected = null })
            else Scaffold(containerColor = bg, bottomBar = { NavigationBar(containerColor = surface) {
                val labels = listOf(c.home, c.diagnostic, c.garage, c.history, c.more)
                val icons = listOf(Icons.Default.Home, Icons.Default.Build, Icons.Default.Garage, Icons.Default.History, Icons.Default.Settings)
                labels.forEachIndexed { index, label -> NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], label) }, label = { Text(label) }) }
            } }) { padding ->
                when (tab) {
                    0 -> HomeScreen(padding, c, dark, primary, bg, surface, text, muted) { selected = it }
                    1 -> ActionScreen(padding, c, primary, surface, muted)
                    2 -> GarageScreen(padding, c, primary, surface, muted) { selected = it }
                    3 -> HistoryScreen(padding, c, primary, surface, muted)
                    else -> MoreScreen(padding, c, dark, primary, surface, muted, onLanguage = { languageDialog = true }, onTheme = { dark = !dark; prefs.dark = dark })
                }
            }
        }
        if (languageDialog) AlertDialog(onDismissRequest = { languageDialog = false }, title = { Text(c.language, fontWeight = FontWeight.Black) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Français", lang == "fr") { lang = "fr"; prefs.lang = "fr"; languageDialog = false }
            Choice("العربية", lang == "ar") { lang = "ar"; prefs.lang = "ar"; languageDialog = false }
        } }, confirmButton = { TextButton(onClick = { languageDialog = false }) { Text(c.back) } })
    }
}

@Composable private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) { Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(16.dp), color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold); if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black) } } }
@Composable private fun Header(title: String, eyebrow: String, primary: Color, muted: Color) { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.DirectionsCar, null, tint = primary) }; Spacer(Modifier.width(12.dp)); Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text(eyebrow, color = muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) } } }

@Composable private fun HomeScreen(padding: PaddingValues, c: Copy, dark: Boolean, primary: Color, bg: Color, surface: Color, text: Color, muted: Color, onVehicle: (ExactVehicle) -> Unit) {
    val scope = rememberCoroutineScope(); var vehicles by remember { mutableStateOf<List<ExactVehicle>>(emptyList()) }; var makes by remember { mutableStateOf<List<ExactMake>>(emptyList()) }; var query by remember { mutableStateOf("") }; var loading by remember { mutableStateOf(true) }
    fun load() { scope.launch { loading = true; vehicles = runCatching { SupabaseClient.client.from("vehicle_models").select(Columns.list("id","make_id","name","year_from","year_to","generation","image_url")).decodeList<ExactVehicle>() }.getOrDefault(emptyList()); makes = runCatching { SupabaseClient.client.from("vehicle_makes").select(Columns.list("id","name")).decodeList<ExactMake>() }.getOrDefault(emptyList()); loading = false } }
    LaunchedEffect(Unit) { load() }; val filtered = vehicles.filter { v -> query.isBlank() || v.name.contains(query,true) || makes.firstOrNull { it.id == v.makeId }?.name?.contains(query,true) == true }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Header("CarDiag", c.smart, primary, muted) }
        item { Box(Modifier.fillMaxWidth().height(320.dp).padding(horizontal=16.dp).clip(RoundedCornerShape(32.dp)).background(Brush.linearGradient(listOf(if(dark) Color(0xFF18383D) else Color(0xFFE0F5F1),surface,bg)))) { Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Bottom) { Text(c.smart,color=primary,fontWeight=FontWeight.Black); Spacer(Modifier.height(8.dp)); Text(c.hero,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black,color=text); Spacer(Modifier.height(18.dp)); Button(onClick={filtered.firstOrNull()?.let(onVehicle)},enabled=filtered.isNotEmpty(),shape=RoundedCornerShape(17.dp)){Icon(Icons.Default.Build,null);Spacer(Modifier.width(8.dp));Text(c.launch,fontWeight=FontWeight.Black)} } } }
        item { Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) { OutlinedTextField(query,{query=it},Modifier.weight(1f),singleLine=true,shape=RoundedCornerShape(19.dp),leadingIcon={Icon(Icons.Default.Search,null,tint=primary)},placeholder={Text(c.search)});IconButton(onClick={load()}){Icon(Icons.Default.Refresh,c.search,tint=primary)} } }
        item { Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) { Stat(vehicles.size.toString(),c.models,Modifier.weight(1f),primary,surface,muted);Stat(makes.size.toString(),c.makes,Modifier.weight(1f),primary,surface,muted);Stat("OBD-II",c.scanner,Modifier.weight(1f),primary,surface,muted) } }
        item { Text(c.catalog,Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black) }
        if(loading)item{Box(Modifier.fillMaxWidth().height(120.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(color=primary)}}else if(filtered.isEmpty())item{Text(c.noVehicles,Modifier.padding(24.dp),color=muted)}else items(filtered.take(30),key={it.id}){vehicle->VehicleCard(vehicle,makes.firstOrNull{it.id==vehicle.makeId}?.name?:"Vehicle",primary,surface,muted,onVehicle)}
    }
}

@Composable private fun Stat(value:String,label:String,modifier:Modifier,primary:Color,surface:Color,muted:Color){Card(modifier,RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=surface)){Column(Modifier.fillMaxWidth().padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(value,color=primary,fontWeight=FontWeight.Black);Text(label,color=muted,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)}}}

@Composable private fun VehicleCard(v:ExactVehicle,make:String,primary:Color,surface:Color,muted:Color,onClick:(ExactVehicle)->Unit){Card(Modifier.fillMaxWidth().padding(horizontal=16.dp).clickable{onClick(v)},RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=surface)){Column{AsyncImage(v.imageUrl,v.name,Modifier.fillMaxWidth().height(190.dp),contentScale=ContentScale.Crop);Column(Modifier.padding(18.dp)){Text(make,color=primary,fontWeight=FontWeight.Bold);Text(v.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black);Text(listOfNotNull(v.generation,v.yearFrom?.toString(),v.yearTo?.toString()).joinToString(" • "),color=muted)}}}}

@Composable private fun ActionScreen(padding:PaddingValues,c:Copy,primary:Color,surface:Color,muted:Color){val context=LocalContext.current;fun open(a:Class<*>){context.startActivity(Intent(context,a))};Column(Modifier.fillMaxSize().padding(padding).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Header(c.diagnostic,c.smart,primary,muted);ActionCard("OBD-II Scanner",Icons.Default.Build,primary,surface){open(ObdScannerActivity::class.java)};ActionCard("Live Data",Icons.Default.Speed,primary,surface){open(LiveDataProActivity::class.java)};ActionCard("DTC & Faults",Icons.Default.Warning,primary,surface){open(GuidedDiagnosisActivity::class.java)};ActionCard("VIN Identity",Icons.Default.DirectionsCar,primary,surface){open(ObdScannerActivity::class.java)};ActionCard("AI Diagnosis • Sans OBD",Icons.Default.Build,primary,surface){open(AiSymptomDiagnosisActivity::class.java)}}}

@Composable private fun ActionCard(title:String,icon:androidx.compose.ui.graphics.vector.ImageVector,primary:Color,surface:Color,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick),RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=surface)){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=primary);Spacer(Modifier.width(14.dp));Text(title,Modifier.weight(1f),fontWeight=FontWeight.Black);Icon(Icons.Default.ChevronRight,null,tint=primary)}}}

@Composable private fun GarageScreen(padding:PaddingValues,c:Copy,primary:Color,surface:Color,muted:Color,onVehicle:(ExactVehicle)->Unit){val scope=rememberCoroutineScope();var garage by remember{mutableStateOf<List<GarageVehicle>>(emptyList())};var models by remember{mutableStateOf<List<ExactVehicle>>(emptyList())};var makes by remember{mutableStateOf<List<ExactMake>>(emptyList())};var loading by remember{mutableStateOf(true)};var showCatalog by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    fun load(){scope.launch{loading=true;error=null;val user=SupabaseClient.client.auth.currentUserOrNull();if(user==null){loading=false;error=if(c==AR)"سجّل الدخول لإدارة سياراتك" else "Connectez-vous pour gérer votre garage";return@launch};garage=runCatching{SupabaseClient.client.from("user_vehicles").select(Columns.list("id","model_id","make_id","nickname","vin","mileage","year","is_primary")).decodeList<GarageVehicle>()}.getOrElse{error=it.message?:"Garage unavailable";emptyList()};models=runCatching{SupabaseClient.client.from("vehicle_models").select(Columns.list("id","make_id","name","year_from","year_to","generation","image_url")).decodeList<ExactVehicle>()}.getOrDefault(emptyList());makes=runCatching{SupabaseClient.client.from("vehicle_makes").select(Columns.list("id","name")).decodeList<ExactMake>()}.getOrDefault(emptyList());loading=false}}
    LaunchedEffect(Unit){load()}
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){Header(c.garage,c.smart,primary,muted);Button(onClick={showCatalog=true},Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text(if(c==AR)"إضافة سيارة" else "Ajouter un véhicule",fontWeight=FontWeight.Black)};if(loading){Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){CircularProgressIndicator(color=primary)}}else if(error!=null){Card(Modifier.fillMaxWidth(),RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=surface)){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Icon(Icons.Default.Lock,null,tint=primary);Text(error!!,fontWeight=FontWeight.Bold);Text(if(c==AR)"سجّل الدخول بحسابك ثم أعد فتح المرآب." else "Connectez-vous avec votre compte puis rouvrez le Garage.",color=muted);TextButton(onClick={load()}){Text(if(c==AR)"إعادة المحاولة" else "Réessayer")}}}}else if(garage.isEmpty()){Card(Modifier.fillMaxWidth(),RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=surface)){Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Garage,null,tint=primary,modifier=Modifier.size(44.dp));Spacer(Modifier.height(10.dp));Text(if(c==AR)"مرآبك فارغ" else "Votre garage est vide",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black);Text(if(c==AR)"أضف سيارتك لحفظ VIN والكيلومترات وربط التشخيص بها." else "Ajoutez votre voiture pour conserver le VIN, le kilométrage et lier les diagnostics.",color=muted)}}}else{LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=30.dp)){items(garage,key={it.id}){gv->val model=models.firstOrNull{it.id==gv.modelId};val make=makes.firstOrNull{it.id==gv.makeId}?.name?:"";val modelName=model?.name?:"Vehicle";Card(Modifier.fillMaxWidth().clickable{model?.let(onVehicle)},RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=surface)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){AsyncImage(model?.imageUrl,modelName,Modifier.size(92.dp).clip(RoundedCornerShape(18.dp)),contentScale=ContentScale.Crop);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(gv.nickname?:modelName,fontWeight=FontWeight.Black);Text(listOf(make,modelName).filter{it.isNotBlank()}.distinct().joinToString(" • "),color=muted);gv.vin?.takeIf{it.isNotBlank()}?.let{Text("VIN • $it",color=primary,style=MaterialTheme.typography.labelSmall)};gv.mileage?.let{Text("$it km",color=muted,style=MaterialTheme.typography.labelSmall)}};if(gv.isPrimary)Icon(Icons.Default.Star,null,tint=primary)}}}}}}
    if(showCatalog)AlertDialog(onDismissRequest={showCatalog=false},title={Text(if(c==AR)"اختر سيارة" else "Choisir un véhicule",fontWeight=FontWeight.Black)},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.heightIn(max=420.dp)){items(models.take(40),key={it.id}){model->val make=makes.firstOrNull{it.id==model.makeId}?.name?:"";Surface(Modifier.fillMaxWidth().clickable{scope.launch{val user=SupabaseClient.client.auth.currentUserOrNull();if(user!=null){runCatching{SupabaseClient.client.from("user_vehicles").insert(GarageVehicleInsert(userId=user.id,makeId=model.makeId,modelId=model.id,nickname=model.name,isPrimary=garage.isEmpty()));showCatalog=false;load()}.onFailure{error=it.message?:"Impossible d'ajouter le véhicule"}}}},RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){AsyncImage(model.imageUrl,model.name,Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop);Spacer(Modifier.width(12.dp));Column{Text(make,color=primary,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold);Text(model.name,fontWeight=FontWeight.Bold)}}}}}},confirmButton={TextButton(onClick={showCatalog=false}){Text(c.back)}})
}
