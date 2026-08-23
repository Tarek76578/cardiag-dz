package dz.cardiag.app

import android.app.LocaleManager
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

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
    var carInfo by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

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

        OutlinedTextField(
            value = carInfo,
            onValueChange = { carInfo = it },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(stringResource(R.string.vehicle))
            },
            placeholder = {
                Text(stringResource(R.string.vehicle_hint))
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
                    if (carInfo.isBlank() || problem.isBlank()) {
                        stringResource(R.string.fill_required_fields)
                    } else {
                        stringResource(R.string.diagnostic_received)
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
