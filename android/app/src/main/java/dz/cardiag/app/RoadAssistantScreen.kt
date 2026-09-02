package dz.cardiag.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.road.AndroidLocationProvider
import dz.cardiag.app.core.road.NearbyService
import dz.cardiag.app.core.road.RoadAssistantContext
import dz.cardiag.app.core.road.RoadAssistantService
import dz.cardiag.app.core.road.ServiceCategory
import dz.cardiag.app.ui.components.InteractiveMapView
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** Road Assistant: real device GPS + live OpenStreetMap/Overpass services. */
@Composable
fun RoadAssistantScreen(
    padding: PaddingValues,
    arabic: Boolean,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { RoadAssistantService(AndroidLocationProvider(context)) }
    val language = if (arabic) "ar" else "fr"
    val locationPermission = RoadAssistantContext.hasLocationPermission(context)

    var permissionGranted by remember { mutableStateOf(locationPermission) }
    var location by remember { mutableStateOf<dz.cardiag.app.core.road.CoarseLocation?>(null) }
    var results by remember { mutableStateOf<List<NearbyService>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var radiusKm by remember { mutableStateOf(5f) }
    var query by remember { mutableStateOf("") }
    var selected by remember {
        mutableStateOf(setOf(ServiceCategory.MECHANIC, ServiceCategory.AUTO_ELECTRICIAN, ServiceCategory.ROADSIDE_ASSISTANCE))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionGranted = grants.values.any { it }
        if (!permissionGranted) {
            message = if (arabic) "لم يتم منح إذن الموقع." else "Autorisation de localisation refusée."
        }
    }

    fun refresh() {
        if (!permissionGranted) return
        loading = true
        message = null
        scope.launch {
            val snap = runCatching {
                service.snapshot(selected, (radiusKm * 1000).roundToInt(), language)
            }
            snap.onSuccess {
                location = it.location
                results = it.services
                if (it.location == null) {
                    message = if (arabic) {
                        "تعذر الحصول على موقع GPS. تأكد من تشغيل خدمات الموقع ثم أعد المحاولة."
                    } else {
                        "Position GPS indisponible. Activez la localisation puis réessayez."
                    }
                } else if (it.services.isEmpty()) {
                    message = if (arabic) {
                        "لم نجد خدمات موسومة في OpenStreetMap ضمن هذا النطاق. جرّب زيادة النطاق أو تغيير الفئات."
                    } else {
                        "Aucun service OSM trouvé dans ce rayon. Augmentez le rayon ou changez les catégories."
                    }
                }
            }.onFailure {
                message = it.message ?: if (arabic) "خطأ في جلب الخدمات." else "Erreur lors de la recherche."
            }
            loading = false
        }
    }

    LaunchedEffect(permissionGranted, selected, radiusKm.roundToInt()) {
        if (permissionGranted) refresh()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onBack?.let {
                    IconButton(onClick = it) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (arabic) "المساعدة على الطريق" else "Assistance routière",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        if (arabic) "خدمات حقيقية قريبة منك" else "Services réels à proximité",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (!permissionGranted) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (arabic) "فعّل الموقع" else "Activez la localisation",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (arabic) "يحتاج CarDiag إلى GPS الحقيقي للبحث حول موقعك."
                            else "CarDiag utilise le GPS réel pour rechercher autour de votre position."
                        )
                        Button(onClick = {
                            permissionLauncher.launch(RoadAssistantContext.locationPermissions)
                        }) {
                            Text(if (arabic) "السماح بالموقع" else "Autoriser la localisation")
                        }
                    }
                }
            }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (arabic) "الخدمات" else "Services", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ServiceCategory.entries.filter { it != ServiceCategory.OTHER }.forEach { category ->
                                FilterChip(
                                    selected = category in selected,
                                    onClick = {
                                        selected = if (category in selected) selected - category else selected + category
                                    },
                                    label = { Text(categoryLabel(category, arabic)) }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (arabic) "النطاق: ${radiusKm.roundToInt()} كم"
                                else "Rayon : ${radiusKm.roundToInt()} km",
                                Modifier.weight(1f)
                            )
                        }
                        Slider(
                            value = radiusKm,
                            onValueChange = { radiusKm = it },
                            valueRange = 1f..20f,
                            steps = 18
                        )
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            placeholder = {
                                Text(if (arabic) "ابحث بالاسم أو العنوان أو الهاتف" else "Nom, adresse ou téléphone")
                            }
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (arabic) "موقعك الحالي" else "Votre position actuelle",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(2.dp))
                        InteractiveMapView(
                            latitude = location?.latitude,
                            longitude = location?.longitude,
                            accuracyMeters = location?.accuracyMeters,
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            contentDescriptionText = if (arabic) "خريطة موقع GPS الحالي" else "Carte de la position GPS actuelle"
                        )
                        location?.let { fix ->
                            val accuracyText = if (fix.accuracyMeters.isFinite()) {
                                if (arabic) "الدقة: ${fix.accuracyMeters.roundToInt()} م"
                                else "Précision : ${fix.accuracyMeters.roundToInt()} m"
                            } else ""
                            Text(
                                text = if (arabic) {
                                    "GPS: ${String.format(Locale.US, "%.6f", fix.latitude)}, ${String.format(Locale.US, "%.6f", fix.longitude)} $accuracyText"
                                } else {
                                    "GPS : ${String.format(Locale.US, "%.6f", fix.latitude)}, ${String.format(Locale.US, "%.6f", fix.longitude)} $accuracyText"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (arabic) "المصدر المباشر" else "Source en direct",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text("OpenStreetMap / Overpass", style = MaterialTheme.typography.bodySmall)
                    }
                    if (loading) {
                        CircularProgressIndicator(
                            Modifier.width(24.dp).height(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            message?.let { text ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(text, Modifier.padding(16.dp))
                    }
                }
            }

            val normalized = query.trim().lowercase(Locale.ROOT)
            val visible = if (normalized.isEmpty()) results else results.filter {
                listOfNotNull(it.name, it.address, it.phone, it.category.key)
                    .joinToString(" ")
                    .lowercase(Locale.ROOT)
                    .contains(normalized)
            }
            item {
                Text(
                    if (arabic) "${visible.size} نتيجة حقيقية"
                    else "${visible.size} résultat(s) réel(s)",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(visible, key = { it.id }) { nearby ->
                NearbyServiceCard(nearby, arabic, context)
            }

            item {
                OutlinedButton(
                    onClick = ::refresh,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) {
                    Text(if (arabic) "تحديث النتائج" else "Actualiser les résultats")
                }
            }
        }
    }
}

@Composable
private fun NearbyServiceCard(service: NearbyService, arabic: Boolean, context: Context) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(service.name, style = MaterialTheme.typography.titleMedium)
                    Text(categoryLabel(service.category, arabic), style = MaterialTheme.typography.bodySmall)
                }
            }
            service.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            service.distanceMeters?.let { Text(formatDistance(it, arabic), style = MaterialTheme.typography.bodySmall) }
            service.phone?.let { phone ->
                Button(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                    }
                }) {
                    Icon(Icons.Default.Phone, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(phone)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val uri = Uri.parse("geo:${service.latitude},${service.longitude}?q=${Uri.encode(service.name)}")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }) {
                    Text(if (arabic) "فتح الخريطة" else "Ouvrir la carte")
                }
                Text("OSM", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

private fun categoryLabel(category: ServiceCategory, arabic: Boolean): String = when (category) {
    ServiceCategory.MECHANIC -> if (arabic) "ميكانيكي" else "Garage / mécanicien"
    ServiceCategory.AUTO_ELECTRICIAN -> if (arabic) "كهربائي سيارات" else "Électricien automobile"
    ServiceCategory.ROADSIDE_ASSISTANCE -> if (arabic) "مساعدة على الطريق" else "Dépannage"
    ServiceCategory.SPARE_PARTS -> if (arabic) "قطع غيار" else "Pièces automobiles"
    ServiceCategory.FUEL_STATION -> if (arabic) "محطة وقود" else "Station-service"
    ServiceCategory.HOSPITAL -> if (arabic) "مستشفى" else "Hôpital"
    ServiceCategory.TOWING -> if (arabic) "سحب المركبات" else "Remorquage"
    ServiceCategory.OTHER -> if (arabic) "خدمة سيارات" else "Service automobile"
}

private fun formatDistance(meters: Double, arabic: Boolean): String {
    return if (meters < 1000) {
        if (arabic) "على بعد ${meters.roundToInt()} م" else "À ${meters.roundToInt()} m"
    } else {
        val km = meters / 1000.0
        if (arabic) "على بعد ${String.format(Locale.US, "%.1f", km)} كم"
        else "À ${String.format(Locale.US, "%.1f", km)} km"
    }
}
