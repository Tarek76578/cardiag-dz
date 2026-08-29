package dz.cardiag.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.edit
import dz.cardiag.app.core.AppMode
import dz.cardiag.app.core.AuthService
import dz.cardiag.app.ui.theme.CarDiagTheme
import kotlinx.coroutines.launch

/**
 * Single production UI entry point.
 *
 * First launch shows the onboarding flow (Language -> Mode -> Guest). Once
 * completed, the unified navigation graph takes over. All product surfaces
 * (Home, Garage, Diagnose, Vehicle profile, DTC detail, symptom diagnosis,
 * OBD onboarding, live data, freeze frame, readiness, VIN, guided diagnosis,
 * AI assistant, history, more) live inside this single Compose graph.
 *
 * Account creation is optional: every guest user can use the full app. Mode
 * and language are persisted in [PREFS_UI] and can be changed at any time
 * from the "More" / "Settings" screen.
 */
@Composable
fun CarDiagUnifiedApp(
    initialRoute: CarDiagRoute = CarDiagRoute.HOME,
    initialDtcCode: String? = null,
    initialVehicleId: String? = null,
    initialVehicleName: String? = null,
    skipOnboarding: Boolean = false
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var dark by remember { mutableStateOf(prefs.getBoolean(KEY_DARK, true)) }
    var arabic by remember { mutableStateOf(prefs.getBoolean(KEY_ARABIC, false)) }
    var mode by remember { mutableStateOf(readAppMode(prefs)) }
    var onboardingComplete by remember { mutableStateOf(skipOnboarding || prefs.getBoolean(KEY_ONBOARDING_DONE, false)) }
    // Persist the active vehicle context across launches so Home, diagnosis and
    // the vehicle profile always know which vehicle the user is investigating.
    val persistedVehicleId = remember {
        if (initialVehicleId.isNullOrBlank()) prefs.getString(KEY_ACTIVE_VEHICLE_ID, null) else initialVehicleId
    }
    val persistedVehicleName = remember {
        if (initialVehicleName.isNullOrBlank()) prefs.getString(KEY_ACTIVE_VEHICLE_NAME, null) else initialVehicleName
    }

    CarDiagTheme(darkTheme = dark) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            if (!onboardingComplete) {
                OnboardingScreen(
                    initialArabic = arabic,
                    initialMode = mode,
                    onLanguageChosen = { value ->
                        arabic = value
                        prefs.edit().putBoolean(KEY_ARABIC, value).apply()
                    },
                    onModeChosen = { value ->
                        mode = value
                        prefs.edit().putString(KEY_APP_MODE, value.name).apply()
                    },
                    onContinue = { _ ->
                        scope.launch {
                            runCatching { AuthService().ensureGuest() }
                            prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
                            onboardingComplete = true
                        }
                    }
                )
            } else {
                CarDiagNavGraph(
                    initialRoute = initialRoute,
                    initialDtcCode = initialDtcCode,
                    initialVehicleId = persistedVehicleId,
                    initialVehicleName = persistedVehicleName,
                    dark = dark,
                    arabic = arabic,
                    mode = mode,
                    setDark = { value ->
                        dark = value
                        prefs.edit().putBoolean(KEY_DARK, value).apply()
                    },
                    setArabic = { value ->
                        arabic = value
                        prefs.edit().putBoolean(KEY_ARABIC, value).apply()
                    },
                    setMode = { value ->
                        mode = value
                        prefs.edit().putString(KEY_APP_MODE, value.name).apply()
                    },
                    persistVehicle = { id, name ->
                        val editor = prefs.edit()
                        if (id.isNullOrBlank()) {
                            editor.remove(KEY_ACTIVE_VEHICLE_ID).remove(KEY_ACTIVE_VEHICLE_NAME)
                        } else {
                            editor.putString(KEY_ACTIVE_VEHICLE_ID, id)
                            if (!name.isNullOrBlank()) editor.putString(KEY_ACTIVE_VEHICLE_NAME, name)
                        }
                        editor.apply()
                    },
                    onReopenOnboarding = {
                        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, false).apply()
                        onboardingComplete = false
                    }
                )
            }
        }
    }
}

private fun readAppMode(prefs: android.content.SharedPreferences): AppMode {
    val raw = prefs.getString(KEY_APP_MODE, null) ?: return AppMode.DRIVER
    return runCatching { AppMode.valueOf(raw) }.getOrDefault(AppMode.DRIVER)
}

internal const val PREFS_UI = "cardiag_ui"
internal const val KEY_DARK = "dark"
internal const val KEY_ARABIC = "arabic"
internal const val KEY_APP_MODE = "app_mode"
internal const val KEY_ONBOARDING_DONE = "onboarding_done"
internal const val KEY_ACTIVE_VEHICLE_ID = "active_vehicle_id"
internal const val KEY_ACTIVE_VEHICLE_NAME = "active_vehicle_name"
