package dz.cardiag.app

import android.app.LocaleManager
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import dz.cardiag.app.core.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.delay

@Serializable
data class VehicleModel(
    val id: String,
    @SerialName("make_id")
    val makeId: String,
    val name: String,
    @SerialName("year_from")
    val yearFrom: Int? = null,
    @SerialName("year_to")
    val yearTo: Int? = null,
    val generation: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("search_text")
    val searchText: String? = null
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                CarDiagApp(
                    onLanguageChange = { language ->
                        setAppLanguage(language)
                    }
                )
            }
        }
    }

    private fun setAppLanguage(language: String) {
        val localeManager = getSystemService(LocaleManager::class.java)

        val locale = when (language) {
            "ar" -> "ar"
            else -> "fr"
        }

        localeManager.applicationLocales =
            LocaleList.forLanguageTags(locale)
    }
}

@Composable
fun CarDiagApp(
    onLanguageChange: (String) -> Unit
) {
    val navController = rememberNavController()

    Scaffold { padding ->

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {

            composable("home") {
                HomeScreen(
                    onStartDiagnostic = {
                        navController.navigate("diagnostic")
                    },
                    onSettings = {
                        navController.navigate("settings")
                    }
                )
            }

            composable("diagnostic") {
                DiagnosticScreen(
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onBack = {
                        navController.popBackStack()
                    },
                    onLanguageChange = onLanguageChange
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    onStartDiagnostic: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.titleMedium
        )

        Button(
            onClick = onStartDiagnostic,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.start_diagnostic))
        }

        Button(
            onClick = onSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings))
        }
    }
}

@Composable
fun DiagnosticScreen(
    onBack: () -> Unit
) {
    var problem by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var selectedVehicle by remember { mutableStateOf<VehicleModel?>(null) }

    val fillRequiredFieldsText =
        stringResource(R.string.fill_required_fields)
    val diagnosticReceivedText =
        stringResource(R.string.diagnostic_received)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = stringResource(R.string.diagnostic_title),
            style = MaterialTheme.typography.headlineMedium
        )

        VehiclePicker(
            selectedVehicle = selectedVehicle,
            onVehicleSelected = {
                selectedVehicle = it
            }
        )

        OutlinedTextField(
            value = problem,
            onValueChange = { problem = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            label = {
                Text(stringResource(R.string.describe_problem))
            },
            placeholder = {
                Text(stringResource(R.string.problem_hint))
            }
        )

        Button(
            onClick = {
                result =
                    if (selectedVehicle == null || problem.isBlank()) {
                        fillRequiredFieldsText
                    } else {
                        diagnosticReceivedText
                    }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.diagnose_with_ai))
        }

        if (result.isNotEmpty()) {
            Text(
                text = result,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
fun VehiclePicker(
    selectedVehicle: VehicleModel?,
    onVehicleSelected: (VehicleModel) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var vehicles by remember { mutableStateOf<List<VehicleModel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(searchQuery, selectedVehicle?.id) {
        val query = searchQuery.trim()

        if (selectedVehicle?.id?.isNotEmpty() == true) {
            vehicles = emptyList()
            loading = false
            error = null
            return@LaunchedEffect
        }

        if (query.length < 1) {
            vehicles = emptyList()
            loading = false
            error = null
            return@LaunchedEffect
        }

        delay(300)

        loading = true
        error = null

        try {
            vehicles = SupabaseClient.client
                .from("vehicle_models")
                .select(
                    columns = Columns.raw(
                        """
                        id,
                        make_id,
                        name,
                        year_from,
                        year_to,
                        generation,
                        image_url,
                        search_text
                        """.trimIndent()
                    )
                ) {
                    filter {
                        ilike(
                            "search_text",
                            "%${query.lowercase()}%"
                        )
                    }
                    limit(20)
                }
                .decodeList<VehicleModel>()
        } catch (e: Exception) {
            vehicles = emptyList()
            error = e.message ?: "Unknown error"
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.vehicle),
            style = MaterialTheme.typography.titleMedium
        )

        if (selectedVehicle == null || selectedVehicle.id.isEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text(stringResource(R.string.vehicle))
                },
                placeholder = {
                    Text(stringResource(R.string.vehicle_hint))
                }
            )
        }

        if (selectedVehicle != null && selectedVehicle.id.isNotEmpty()) {
            SelectedVehicleCard(
                vehicle = selectedVehicle,
                onChange = {
                    searchQuery = ""
                    vehicles = emptyList()
                    error = null
                    onVehicleSelected(
                        VehicleModel(
                            id = "",
                            makeId = "",
                            name = ""
                        )
                    )
                }
            )
        }

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        if (error != null) {
            Text(
                text = error ?: "",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (
            searchQuery.isNotBlank() &&
            !loading &&
            error == null &&
            selectedVehicle?.id.isNullOrEmpty()
        ) {
            if (vehicles.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = vehicles,
                        key = { it.id }
                    ) { vehicle ->
                        VehicleResultItem(
                            vehicle = vehicle,
                            onClick = {
                                onVehicleSelected(vehicle)
                                searchQuery = ""
                                vehicles = emptyList()
                            }
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.no_vehicle_found),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun VehicleResultItem(
    vehicle: VehicleModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            AsyncImage(
                model = vehicle.imageUrl,
                contentDescription = vehicle.name,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {

                Text(
                    text = vehicle.name,
                    style = MaterialTheme.typography.titleMedium
                )

                if (!vehicle.generation.isNullOrBlank()) {
                    Text(
                        text = vehicle.generation,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                val years = when {
                    vehicle.yearFrom != null &&
                        vehicle.yearTo != null ->
                        "${vehicle.yearFrom} – ${vehicle.yearTo}"

                    vehicle.yearFrom != null ->
                        "${vehicle.yearFrom} –"

                    vehicle.yearTo != null ->
                        "– ${vehicle.yearTo}"

                    else -> ""
                }

                if (years.isNotEmpty()) {
                    Text(
                        text = years,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedVehicleCard(
    vehicle: VehicleModel,
    onChange: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            AsyncImage(
                model = vehicle.imageUrl,
                contentDescription = vehicle.name,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = vehicle.name,
                    style = MaterialTheme.typography.titleMedium
                )

                if (!vehicle.generation.isNullOrBlank()) {
                    Text(
                        text = vehicle.generation,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Button(onClick = onChange) {
                Text("✕")
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLanguageChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = stringResource(R.string.language)
        )

        Button(
            onClick = {
                onLanguageChange("fr")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.french))
        }

        Button(
            onClick = {
                onLanguageChange("ar")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.arabic))
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.back))
        }
    }
}
