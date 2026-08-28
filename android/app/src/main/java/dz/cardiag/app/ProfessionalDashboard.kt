package dz.cardiag.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dz.cardiag.app.ui.theme.CarDiagTheme

private const val PREFS = "cardiag_professional"
private const val VEHICLE_NAME = "vehicle_name"
private const val VEHICLE_ENGINE = "vehicle_engine"
private const val DARK_MODE = "dark_mode"
private const val ARABIC = "arabic"

@Composable
fun ProfessionalDashboard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var arabic by remember { mutableStateOf(prefs.getBoolean(ARABIC, false)) }
    var dark by remember { mutableStateOf(prefs.getBoolean(DARK_MODE, true)) }
    val vehicleName = prefs.getString(VEHICLE_NAME, null)
    val vehicleEngine = prefs.getString(VEHICLE_ENGINE, null)
    fun setArabic(v: Boolean) { arabic = v; prefs.edit().putBoolean(ARABIC, v).apply() }
    fun setDark(v: Boolean) { dark = v; prefs.edit().putBoolean(DARK_MODE, v).apply() }
    CarDiagTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = { TopAppBar(title = { Column { Text("CarDiag", fontWeight = FontWeight.Black); Text(if (arabic) "تشخيص ذكي للسيارات" else "Smart vehicle diagnostics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, actions = { IconButton(onClick = { setDark(!dark) }, modifier = Modifier.semantics { contentDescription = if (arabic) "تبديل المظهر" else "Toggle appearance" }) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = null) } }) }
            ) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { HeroCard(arabic) { open(context, ObdScannerActivity::class.java) } }
                    item { VehicleStatusCard(vehicleName, vehicleEngine, arabic) { open(context, ObdScannerActivity::class.java) } }
                    item { SectionHeader(if (arabic) "التشخيص" else "Diagnostics", if (arabic) "كل أدوات الفحص في مكان واحد" else "All diagnostic tools in one place") }
                    item { ToolGrid(arabic) { open(context, it) } }
                    item { AiCard(arabic) { open(context, AiSymptomDiagnosisActivity::class.java) } }
                    item { SettingsRow(arabic, ::setArabic, ::setDark) }
                }
            }
        }
    }
}

@Composable private fun HeroCard(arabic: Boolean, onScan: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(50.dp), shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .16f)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.DirectionsCar, null, tint = Color.White) } }
                Spacer(Modifier.width(12.dp))
                Column { Text(if (arabic) "مركز التشخيص" else "DIAGNOSTIC CENTER", color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text(if (arabic) "ابدأ فحص سيارتك" else "Start your vehicle check", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
            }
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.Default.BluetoothConnected, null); Spacer(Modifier.width(8.dp)); Text(if (arabic) "بدء فحص OBD" else "START OBD SCAN", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable private fun SectionHeader(title: String, subtitle: String) { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable private fun VehicleStatusCard(name: String?, engine: String?, arabic: Boolean, onChange: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onChange), shape = RoundedCornerShape(22.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(54.dp), shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.DirectionsCar, null, tint = MaterialTheme.colorScheme.primary) } }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(if (arabic) "السيارة الحالية" else "CURRENT VEHICLE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text(name ?: if (arabic) "اختر سيارتك" else "Select your vehicle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold); if (!engine.isNullOrBlank()) Text(engine, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ChevronRight, if (arabic) "فتح السيارة" else "Open vehicle") } }
}

@Composable private fun ToolGrid(arabic: Boolean, open: (Class<*>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolCard(if (arabic) "ماسح OBD-II" else "OBD-II Scanner", if (arabic) "DTC • VIN • ECU" else "DTC • VIN • ECU", Icons.Default.BluetoothConnected) { open(ObdScannerActivity::class.java) }
        ToolCard(if (arabic) "البيانات المباشرة" else "Live Data Pro", if (arabic) "الحساسات والقيم المباشرة" else "Sensors & real-time values", Icons.Default.Speed) { open(LiveDataProActivity::class.java) }
        ToolCard(if (arabic) "التشخيص الموجّه" else "Guided Diagnosis", if (arabic) "الأسباب • الاختبارات • الإصلاح" else "Causes • tests • repairs", Icons.Default.Build) { open(GuidedDiagnosisActivity::class.java) }
    }
}

@Composable private fun ToolCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(48.dp), shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) } }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable private fun AiCard(arabic: Boolean, onClick: () -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) } }; Spacer(Modifier.width(10.dp)); Column { Text("CarDiag AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold); Text(if (arabic) "مساعد التشخيص" else "Diagnostic assistant", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }; Text(if (arabic) "حلّل الأعراض والأعطال واحصل على خطوات فحص منظمة." else "Analyze symptoms and faults with structured diagnostic guidance.", color = MaterialTheme.colorScheme.onSurfaceVariant); OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text(if (arabic) "تشخيص الأعراض" else "SYMPTOM DIAGNOSIS") } } } }

@Composable private fun SettingsRow(arabic: Boolean, setArabic: (Boolean) -> Unit, setDark: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { AssistChip(onClick = { setArabic(!arabic) }, label = { Text(if (arabic) "Français" else "العربية") }, leadingIcon = { Icon(Icons.Default.Language, null) }); AssistChip(onClick = { setDark(true) }, label = { Text("Dark") }, leadingIcon = { Icon(Icons.Default.DarkMode, null) }); AssistChip(onClick = { setDark(false) }, label = { Text("Light") }, leadingIcon = { Icon(Icons.Default.LightMode, null) }) } }

private fun open(context: Context, activity: Class<*>) { context.startActivity(Intent(context, activity)) }
