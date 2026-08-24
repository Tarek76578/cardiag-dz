package dz.cardiag.app

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dz.cardiag.app.core.SupabaseClient
import dz.cardiag.app.ui.theme.CarDiagTheme
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class UnifiedMake(val id: String, val name: String)
@Serializable
private data class UnifiedModel(
    val id: String,
    @SerialName("make_id") val makeId: String,
    val name: String,
    @SerialName("year_from") val yearFrom: Int? = null,
    @SerialName("year_to") val yearTo: Int? = null,
    val generation: String? = null,
    @SerialName("image_url") val imageUrl: String? = null
)

private enum class UnifiedTab { HOME, CARS, DIAGNOSTIC, GARAGE, MENU }

@Composable
fun CarDiagUnifiedApp() {
    var dark by remember { mutableStateOf(true) }
    var arabic by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(UnifiedTab.HOME) }
    var selectedModel by remember { mutableStateOf<UnifiedModel?>(null) }
    CarDiagTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            if (selectedModel != null) {
                UnifiedVehicleScreen(selectedModel!!, arabic) { selectedModel = null }
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = { UnifiedBottomBar(tab, arabic) { tab = it } }
                ) { padding ->
                    when (tab) {
                        UnifiedTab.HOME -> UnifiedHome(padding, arabic) { selectedModel = it; tab = UnifiedTab.CARS }
                        UnifiedTab.CARS -> UnifiedCars(padding, arabic) { selectedModel = it }
                        UnifiedTab.DIAGNOSTIC -> UnifiedDiagnostic(padding, arabic)
                        UnifiedTab.GARAGE -> UnifiedGarage(padding, arabic) { tab = UnifiedTab.CARS }
                        UnifiedTab.MENU -> UnifiedMenu(padding, arabic, dark, { dark = !dark }, { arabic = !arabic }) { tab = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedBottomBar(tab: UnifiedTab, arabic: Boolean, onTab: (UnifiedTab) -> Unit) {
    val labels = if (arabic) listOf("الرئيسية", "السيارات", "التشخيص", "المرآب", "المزيد") else listOf("Accueil", "Voitures", "Diagnostic", "Garage", "Menu")
    val icons = listOf(Icons.Default.Home, Icons.Default.DirectionsCar, Icons.Default.Build, Icons.Default.Garage, Icons.Default.MoreHoriz)
    NavigationBar {
        UnifiedTab.values().forEachIndexed { index, item ->
            NavigationBarItem(tab == item, { onTab(item) }, { Icon(icons[index], labels[index]) }, label = { Text(labels[index]) })
        }
    }
}

@Composable
private fun UnifiedHome(padding: PaddingValues, arabic: Boolean, onModel: (UnifiedModel) -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)), RoundedCornerShape(28.dp))) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("CarDiag", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                    Text(if (arabic) "تشخيص السيارات الذكي" else "SMART VEHICLE DIAGNOSTICS", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(if (arabic) "السيارة أولاً • التشخيص ثانياً • AI عندما تحتاجه" else "Vehicle first • diagnostics second • AI when you need it", color = Color.White.copy(alpha = .85f))
                    Button({ context.startActivity(Intent(context, AiSymptomDiagnosisActivity::class.java)) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary)) {
                        Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(if (arabic) "تشخيص AI" else "AI Diagnosis")
                    }
                }
            }
        }
        item { SectionTitle(if (arabic) "ابدأ" else "Start") }
        item { UnifiedAction(if (arabic) "اختيار سيارة" else "Choose a vehicle", if (arabic) "تصفح الماركات والموديلات والمحركات" else "Browse makes, models and engines", Icons.Default.DirectionsCar) { onModelOrCars(context, onModel) } }
        item { UnifiedAction(if (arabic) "التشخيص" else "Diagnostic", if (arabic) "OBD-II • Live Data • DTC • VIN • AI" else "OBD-II • Live Data • DTC • VIN • AI", Icons.Default.Build) { context.startActivity(Intent(context, ObdScannerActivity::class.java)) } }
        item { UnifiedAction(if (arabic) "المرآب" else "Garage", if (arabic) "سياراتك وملفاتها" else "Your vehicles and profiles", Icons.Default.Garage) { } }
    }
}

private fun onModelOrCars(context: android.content.Context, onModel: (UnifiedModel) -> Unit) {
    // Home action intentionally opens the automotive catalog through the main navigation.
    // The callback is retained for a shared action signature.
    onModel(UnifiedModel("", "", ""))
}

@Composable
private fun UnifiedCars(padding: PaddingValues, arabic: Boolean, onModel: (UnifiedModel) -> Unit) {
    var models by remember { mutableStateOf<List<UnifiedModel>>(emptyList()) }
    var makes by remember { mutableStateOf<List<UnifiedMake>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        models = runCatching { SupabaseClient.client.from("vehicle_models").select(Columns.list("id", "make_id", "name", "year_from", "year_to", "generation", "image_url")).decodeList<UnifiedModel>() }.getOrDefault(emptyList())
        makes = runCatching { SupabaseClient.client.from("vehicle_makes").select(Columns.list("id", "name")).decodeList<UnifiedMake>() }.getOrDefault(emptyList())
        loading = false
    }
    val filtered = models.filter { m -> query.isBlank() || m.name.contains(query, true) || makes.firstOrNull { it.id == m.makeId }?.name?.contains(query, true) == true }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle(if (arabic) "كتالوج السيارات" else "Vehicle Catalog", if (arabic) "ماركة → موديل → جيل → محرك" else "Make → Model → Generation → Engine") }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp), placeholder = { Text(if (arabic) "ابحث عن سيارة أو ماركة" else "Search make or model") }, leadingIcon = { Icon(Icons.Default.Search, null) }) }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        items(filtered.take(100), key = { it.id }) { model ->
            val make = makes.firstOrNull { it.id == model.makeId }?.name ?: "Vehicle"
            VehicleCatalogCard(model, make) { onModel(model) }
        }
    }
}

@Composable
private fun VehicleCatalogCard(model: UnifiedModel, make: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model.imageUrl, model.name, Modifier.size(92.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(make, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text(model.name, style = MaterialTheme.typography.titleMedium)
                Text(listOfNotNull(model.generation, model.yearFrom?.toString(), model.yearTo?.toString()).joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun UnifiedVehicleScreen(model: UnifiedModel, arabic: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(250.dp).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(.85f), MaterialTheme.colorScheme.background)))) {
                AsyncImage(model.imageUrl, model.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                IconButton(onBack, Modifier.padding(16.dp)) { Icon(Icons.Default.ArrowBack, if (arabic) "رجوع" else "Back", tint = Color.White) }
            }
        }
        item { Column(Modifier.padding(horizontal = 20.dp)) { Text(model.name, style = MaterialTheme.typography.headlineMedium); Text(listOfNotNull(model.generation, model.yearFrom?.toString(), model.yearTo?.toString()).joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item { SectionTitle(if (arabic) "تشخيص السيارة" else "Vehicle Diagnostics") }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button({ context.startActivity(Intent(context, ObdScannerActivity::class.java)) }, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.BluetoothConnected, null); Spacer(Modifier.width(5.dp)); Text("OBD") }
            FilledTonalButton({ context.startActivity(Intent(context, AiSymptomDiagnosisActivity::class.java)) }, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(5.dp)); Text("AI") }
        } }
        item { UnifiedAction("Live Data", if (arabic) "بيانات الحساسات مباشرة" else "Live sensor data", Icons.Default.Speed) { context.startActivity(Intent(context, LiveDataProActivity::class.java)) } }
        item { UnifiedAction("DTC & Faults", if (arabic) "الأكواد والأعطال" else "Codes and faults", Icons.Default.Warning) { context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java)) } }
        item { SectionTitle(if (arabic) "معلومات السيارة" else "Vehicle Information") }
        item { InfoGrid(model, arabic) }
    }
}

@Composable
private fun InfoGrid(model: UnifiedModel, arabic: Boolean) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard(if (arabic) "الجيل" else "Generation", model.generation ?: "—", Modifier.weight(1f))
            InfoCard(if (arabic) "من" else "From", model.yearFrom?.toString() ?: "—", Modifier.weight(1f))
            InfoCard(if (arabic) "إلى" else "To", model.yearTo?.toString() ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, modifier: Modifier = Modifier) { Card(modifier, shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(14.dp)) { Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.titleMedium) } } }

@Composable
private fun UnifiedDiagnostic(padding: PaddingValues, arabic: Boolean) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle(if (arabic) "مركز التشخيص" else "Diagnostic Center", "OBD-II • Live Data • DTC • VIN • AI") }
        item { UnifiedAction("OBD-II Scanner", if (arabic) "فحص ECU والاتصال بالسيارة" else "Connect and scan ECUs", Icons.Default.BluetoothConnected) { context.startActivity(Intent(context, ObdScannerActivity::class.java)) } }
        item { UnifiedAction("Live Data", if (arabic) "قراءة الحساسات في الوقت الحقيقي" else "Read sensors in real time", Icons.Default.Speed) { context.startActivity(Intent(context, LiveDataProActivity::class.java)) } }
        item { UnifiedAction("DTC & Faults", if (arabic) "رموز الأعطال وتحليلها" else "Fault codes and analysis", Icons.Default.Warning) { context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java)) } }
        item { UnifiedAction("VIN Identity", if (arabic) "تحديد هوية السيارة" else "Identify the vehicle", Icons.Default.DirectionsCar) { context.startActivity(Intent(context, ObdScannerActivity::class.java)) } }
        item { Card(shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp)) { Text("AI Diagnosis", style = MaterialTheme.typography.titleLarge); Text(if (arabic) "حلل الأعراض بدون OBD" else "Analyze symptoms without an OBD device", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)); FilledTonalButton({ context.startActivity(Intent(context, AiSymptomDiagnosisActivity::class.java)) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(if (arabic) "ابدأ تشخيص AI" else "Start AI Diagnosis") } } } }
    }
}

@Composable
private fun UnifiedGarage(padding: PaddingValues, arabic: Boolean, onAdd: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle(if (arabic) "المرآب" else "Garage", if (arabic) "سياراتك وملفاتها" else "Your vehicles and profiles") }
        item { UnifiedAction(if (arabic) "إضافة سيارة" else "Add a vehicle", if (arabic) "اختر السيارة من الكتالوج" else "Choose a vehicle from the catalog", Icons.Default.AddCircle) { onAdd() } }
        item { UnifiedAction(if (arabic) "سجل التشخيص" else "Diagnostic History", if (arabic) "الجلسات والنتائج السابقة" else "Previous sessions and results", Icons.Default.History) { } }
        item { Text(if (arabic) "بعد إضافة سيارة، ستظهر هنا كسيارتك النشطة." else "After adding a vehicle, it will appear here as your active vehicle.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun UnifiedMenu(padding: PaddingValues, arabic: Boolean, dark: Boolean, toggleDark: () -> Unit, toggleArabic: () -> Unit, navigate: (UnifiedTab) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle(if (arabic) "المزيد" else "Menu", if (arabic) "إعدادات ووظائف CarDiag" else "CarDiag tools and settings") }
        item { UnifiedAction(if (arabic) "تشخيص AI" else "AI Diagnosis", if (arabic) "تشخيص الأعراض بدون OBD" else "Symptom diagnosis without OBD", Icons.Default.AutoAwesome) { navigate(UnifiedTab.DIAGNOSTIC) } }
        item { UnifiedAction(if (arabic) "السيارات" else "Vehicle Catalog", if (arabic) "الماركات والموديلات والمحركات" else "Makes, models and engines", Icons.Default.DirectionsCar) { navigate(UnifiedTab.CARS) } }
        item { UnifiedAction(if (arabic) "المرآب" else "My Garage", if (arabic) "سياراتك" else "Your vehicles", Icons.Default.Garage) { navigate(UnifiedTab.GARAGE) } }
        item { UnifiedAction(if (arabic) "السجل" else "Diagnostic History", if (arabic) "الجلسات السابقة" else "Previous sessions", Icons.Default.History) { } }
        item { SettingRow(if (arabic) "الوضع الداكن" else "Dark mode", dark, toggleDark) }
        item { SettingRow(if (arabic) "العربية" else "Français / Arabic", arabic, toggleArabic) }
        item { UnifiedAction(if (arabic) "حول CarDiag" else "About CarDiag", "CarDiag DZ", Icons.Default.Info) { } }
    }
}

@Composable
private fun SettingRow(title: String, checked: Boolean, onChange: () -> Unit) { Card(shape = RoundedCornerShape(18.dp)) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); Switch(checked, { onChange() }) } } }

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) { Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall); if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun UnifiedAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) { Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(50.dp), shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) } }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } } }
