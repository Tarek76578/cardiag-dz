package dz.cardiag.app

import android.app.LocaleManager
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.navigation.compose.*
import dz.cardiag.app.core.AuthService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CarDiagApp(onLanguageChange = { setAppLanguage(it) })
            }
        }
    }

    private fun setAppLanguage(language: String) {
        val localeManager = getSystemService(LocaleManager::class.java)
        localeManager.applicationLocales = LocaleList.forLanguageTags(if (language == "ar") "ar" else "fr")
    }
}

@Composable
fun CarDiagApp(onLanguageChange: (String) -> Unit) {
    val navController = rememberNavController()
    var authenticated by remember { mutableStateOf(AuthService().currentUser != null) }

    if (!authenticated) {
        AuthScreen(onAuthenticated = { authenticated = true })
        return
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onStartDiagnostic = { navController.navigate("diagnostic") },
                onSettings = { navController.navigate("settings") }
            )
        }
        composable("diagnostic") { DiagnosticScreen(onBack = { navController.popBackStack() }) }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLanguageChange = onLanguageChange
            )
        }
    }
}
