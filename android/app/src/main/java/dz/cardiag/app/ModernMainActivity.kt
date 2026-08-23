package dz.cardiag.app

import android.app.LocaleManager
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dz.cardiag.app.core.AuthService
import dz.cardiag.app.core.DiagnosticService
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

private data class CarCard(val name: String, val meta: String, val image: String)

private val cars = listOf(
    CarCard("Peugeot 208", "1.2 PureTech • 2019–2024", "https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=1200&q=85"),
    CarCard("Volkswagen Golf", "1.6 TDI • 2017–2023", "https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?auto=format&fit=crop&w=1200&q=85"),
    CarCard("Renault Clio", "1.5 dCi • 2018–2024", "https://images.unsplash.com/photo-1553440569-bcc63803a83d?auto=format&fit=crop&w=1200&q=85"),
    CarCard("Dacia Sandero", "1.5 dCi • 2017–2024", "https://images.unsplash.com/photo-1542362567-b07e54358753?auto=format&fit=crop&w=1200&q=85")
)

class ModernMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ModernCarDiag() }
    }

    fun setLanguage(language: String) {
        getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(if (language == "ar") "ar" else "fr")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernCarDiag() {
    val auth = remember { AuthService() }
    val activity = LocalContext.current as? ModernMainActivity
    var ready by remember { mutableStateOf(auth.currentUser != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(retry) {
        if (!ready) try {
            auth.signInAnonymously()
            ready = auth.currentUser != null
        } catch (e: Exception) { error = e.message ?: "Authentication failed" }
    }

    if (!ready) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF071016)) {
            Column(Modifier.fillMaxSize().padding(28.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("CarDiag", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = Color.White)
                Text("DZ • Intelligent vehicle diagnostics", color = Color(0xFF9EB0B9))
                Spacer(Modifier.height(24.dp))
                if (error == null) CircularProgressIndicator(color = Color(0xFF52E5D3))
                else {
                    Text(error!!, color = Color(0xFFFF8B8B), modifier = Modifier.padding(bottom = 12.dp))
                    Button(onClick = { error = null; retry++ }) { Text("Retry / Réessayer") }
                }
            }
        }
        return
    }

    val ar = LocalConfiguration.current.locales[0].language == "ar"
    var tab by remember { mutableStateOf(0) }
    val titles = if (ar) listOf("الرئيسية", "التشخيص", "السيارات", "الإعدادات") else listOf("Accueil", "Diagnostic", "Véhicules", "Réglages")

    Scaffold(
        containerColor = Color(0xFF071016),
        topBar = {
            TopAppBar(
                title = { Text("CarDiag", color = Color.White, fontWeight = FontWeight.Black) },
                actions = { Text("DZ", color = Color(0xFF52E5D3), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 18.dp)) }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0C171E)) {
                titles.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Text(listOf("⌂", "⌁", "▣", "⚙")[index]) },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            0 -> HomeModern(padding, ar, { tab = 1 }, { tab = 2 })
            1 -> DiagnoseModern(padding, ar)
            2 -> VehiclesModern(padding, ar, { tab = 1 })
            else -> SettingsModern(padding, ar) { activity?.setLanguage(it) }
        }
    }
}

@Composable
private fun HomeModern(padding: PaddingValues, ar: Boolean, diagnose: () -> Unit, vehicles: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(30.dp))) {
                AsyncImage(model = cars[0].image, contentDescription = cars[0].name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE061015)))))
                Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (ar) "تشخيص ذكي لسيارتك" else "Diagnostic intelligent", color = Color(0xFF52E5D3), fontWeight = FontWeight.Bold)
                    Text(if (ar) "اعرف المشكلة قبل تغيير القطع" else "Comprenez le problème avant de remplacer une pièce", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Button(onClick = diagnose, shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF52E5D3))) {
                        Text(if (ar) "ابدأ التشخيص" else "Lancer le diagnostic", color = Color(0xFF06221F), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        item { Text(if (ar) "أدوات سريعة" else "Actions rapides", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard("OBD", if (ar) "فحص الأعطال" else "Scanner les défauts", Modifier.weight(1f), diagnose)
            ActionCard("DTC", if (ar) "رموز الخطأ" else "Codes défaut", Modifier.weight(1f), diagnose)
        }}
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard("VIN", if (ar) "هوية السيارة" else "Identité véhicule", Modifier.weight(1f), vehicles)
            ActionCard("AI", if (ar) "تحليل ذكي" else "Analyse IA", Modifier.weight(1f), diagnose)
        }}
        item { Text(if (ar) "سيارات شائعة" else "Véhicules populaires", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        items(cars.take(3)) { car -> CarImageCard(car, {}) }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, modifier: Modifier, click: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = click), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF102029))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color(0xFF52E5D3), fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CarImageCard(car: CarCard, click: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = click), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1A22))) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = car.image, contentDescription = car.name, modifier = Modifier.size(132.dp, 92.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(car.name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(car.meta, color = Color(0xFF9EB0B9))
                Text("●  Ready", color = Color(0xFF52E5D3), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun VehiclesModern(padding: PaddingValues, ar: Boolean, diagnose: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(if (ar) "كتالوج السيارات" else "Catalogue véhicules", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(if (ar) "صور ومعلومات سريعة للموديلات الشائعة في الجزائر" else "Photos et infos rapides des modèles populaires en Algérie", color = Color(0xFF9EB0B9))
        }
        items(cars) { car -> CarImageCard(car, diagnose) }
    }
}

@Composable
private fun DiagnoseModern(padding: PaddingValues, ar: Boolean) {
    var complaint by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<JsonObject?>(null) }
    val scope = rememberCoroutineScope()
    val service = remember { DiagnosticService() }
    val vehicle = cars[0]
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(if (ar) "التشخيص الذكي" else "Diagnostic intelligent", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(if (ar) "اختر السيارة واشرح الأعراض، ثم دع طبقة الذكاء الاصطناعي تحللها." else "Choisissez le véhicule, décrivez les symptômes et laissez l'IA analyser.", color = Color(0xFF9EB0B9))
        }
        item { CarImageCard(vehicle, {}) }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1A22))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (ar) "ما الذي يحدث؟" else "Que se passe-t-il ?", color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = complaint, onValueChange = { complaint = it }, modifier = Modifier.fillMaxWidth(), minLines = 5, label = { Text(if (ar) "الأعراض" else "Symptômes") })
                    Button(onClick = {
                        if (complaint.isBlank()) return@Button
                        scope.launch {
                            running = true
                            result = null
                            try { result = service.runDiagnostic(null, complaint, if (ar) "ar" else "fr") }
                            catch (_: Exception) { result = JsonObject(emptyMap()) }
                            finally { running = false }
                        }
                    }, enabled = !running, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF52E5D3))) {
                        if (running) CircularProgressIndicator(Modifier.size(20.dp), color = Color(0xFF06221F), strokeWidth = 2.dp)
                        else Text(if (ar) "حلّل المشكلة" else "Analyser le problème", color = Color(0xFF06221F), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        result?.let { item { ResultCard(it, ar) } }
    }
}

@Composable
private fun ResultCard(result: JsonObject, ar: Boolean) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF102C2A))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (ar) "نتيجة التحليل" else "Résultat de l'analyse", color = Color(0xFF52E5D3), fontWeight = FontWeight.Black)
            Text(result.toString(), color = Color.White)
        }
    }
}

@Composable
private fun SettingsModern(padding: PaddingValues, ar: Boolean, change: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (ar) "الإعدادات" else "Réglages", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1A22))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (ar) "اللغة" else "Langue", color = Color.White, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { change("ar") }) { Text("العربية") }
                    Button(onClick = { change("fr") }) { Text("Français") }
                }
            }
        }
    }
}
