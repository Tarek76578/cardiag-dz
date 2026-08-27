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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.cardiag.app.ui.theme.CarDiagTheme

private const val PROFESSIONAL_PREFS = "cardiag_professional"
private const val VEHICLE_NAME = "vehicle_name"
private const val VEHICLE_ENGINE = "vehicle_engine"
private const val DARK_MODE = "dark_mode"
private const val ARABIC = "arabic"

@Composable
fun ProfessionalDashboard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PROFESSIONAL_PREFS, Context.MODE_PRIVATE) }
    var arabic by remember { mutableStateOf(prefs.getBoolean(ARABIC, false)) }
    var dark by remember { mutableStateOf(prefs.getBoolean(DARK_MODE, true)) }
    val vehicleName = prefs.getString(VEHICLE_NAME, null)
    val vehicleEngine = prefs.getString(VEHICLE_ENGINE, null)
    fun setArabic(value: Boolean) { arabic = value; prefs.edit().putBoolean(ARABIC, value).apply() }
    fun setDark(value: Boolean) { dark = value; prefs.edit().putBoolean(DARK_MODE, value).apply() }

    CarDiagTheme(darkTheme = dark) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides if (arabic) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Column { Text("CarDiag", fontWeight = FontWeight.Black); Text(if (arabic) "تشخيص ذكي للسيارات" else "Smart Vehicle Diagnostics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                        actions = { IconButton(onClick = { setDark(!dark) }) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = if (arabic) "الوضع الفاتح" else "Light mode") } }
                    )
                }
            ) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))) {
                            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(Modifier.size(48.dp), shape = RoundedCornerShape(15.dp), color = Color.White.copy(alpha = .16f)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.DirectionsCar, null, tint = Color.White) } }
                                    Spacer(Modifier.width(12.dp))
                                    Column { Text(if (arabic) "مركز التشخيص" else "DIAGNOSTIC CENTER", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text(if (arabic) "ابدأ فحص سيارتك" else "Start your vehicle check", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
                                }
                                Button(onClick = { open(context, ObdScannerActivity::class.java) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary)) {
                                    Icon(Icons.Default.BluetoothConnected, null); Spacer(Modifier.width(8.dp)); Text(if (arabic) "بدء فحص OBD" else "START OBD SCAN", fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                    item { VehicleStatusCard(vehicleName, vehicleEngine, arabic) { open(context, ObdScannerActivity::class.java) } }
                    item { Text(if (arabic) "أدوات التشخيص" else "Diagnostic tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }
                    item { ToolGrid(arabic) { activity -> open(context, activity) } }
                    item {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text("CarDiag AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold) }
                                Text(if (arabic) "حلّل الأعراض والأعطال واحصل على خطوات فحص منظمة." else "Analyze symptoms and faults with structured diagnostic guidance.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = { open(context, AiSymptomDiagnosisActivity::class.java) }, modifier = Modifier.fillMaxWidth()) { Text(if (arabic) "تشخيص الأعراض" else "SYMPTOM DIAGNOSIS") }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AssistChip(onClick = { setArabic(!arabic) }, label = { Text(if (arabic) "Français" else "العربية") }, leadingIcon = { Icon(Icons.Default.Language, null) })
                            AssistChip(onClick = { setDark(!dark) }, label = { Text(if (dark) "Light" else "Dark") }, leadingIcon = { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, null) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleStatusCard(name: String?, engine: String?, arabic: Boolean, onChange: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onChange), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(54.dp), shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.DirectionsCar, null, tint = MaterialTheme.colorScheme.primary) } }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (arabic) "السيارة الحالية" else "CURRENT VEHICLE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(name ?: if (arabic) "لم يتم اختيار سيارة" else "No vehicle selected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                if (!engine.isNullOrBlank()) Text(engine, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun ToolGrid(arabic: Boolean, open: (Class<*>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolCard(if (arabic) "ماسح OBD-II" else "OBD-II Scanner", if (arabic) "DTC • VIN • ECU" else "DTC • VIN • ECU", Icons.Default.BluetoothConnected) { open(ObdScannerActivity::class.java) }
        ToolCard("Live Data Pro", if (arabic) "الحساسات والقيم المباشرة" else "Sensors & real-time values", Icons.Default.Speed) { open(LiveDataProActivity::class.java) }
        ToolCard(if (arabic) "DTC والتشخيص الموجّه" else "DTC & Guided Diagnosis", if (arabic) "الأسباب • الاختبارات • الإصلاح" else "Causes • tests • repairs", Icons.Default.Build) { open(GuidedDiagnosisActivity::class.java) }
    }
}

@Composable
private fun ToolCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(48.dp), shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) } }
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun open(context: Context, activity: Class<*>) { context.startActivity(Intent(context, activity)) }