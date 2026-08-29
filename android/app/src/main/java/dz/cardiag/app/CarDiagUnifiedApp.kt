package dz.cardiag.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.edit
import dz.cardiag.app.ui.theme.CarDiagTheme

/**
 * Single production UI entry point.
 *
 * All product surfaces (Home, Garage, Diagnose, Vehicle profile, DTC detail,
 * symptom diagnosis, OBD onboarding, live data, freeze frame, readiness, VIN,
 * guided diagnosis, AI assistant, history, more) live inside this single
 * Compose graph. The activity wrappers (ObdScannerActivity, LiveDataProActivity,
 * GuidedDiagnosisActivity, AiSymptomDiagnosisActivity) are kept only as deep
 * links into this graph; they no longer host their own UI.
 */
@Composable
fun CarDiagUnifiedApp(
    initialRoute: CarDiagRoute = CarDiagRoute.HOME,
    initialDtcCode: String? = null,
    initialVehicleId: String? = null,
    initialVehicleName: String? = null
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE) }
    var dark by remember { mutableStateOf(prefs.getBoolean(KEY_DARK, true)) }
    var arabic by remember { mutableStateOf(prefs.getBoolean(KEY_ARABIC, false)) }
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
            CarDiagNavGraph(
                initialRoute = initialRoute,
                initialDtcCode = initialDtcCode,
                initialVehicleId = persistedVehicleId,
                initialVehicleName = persistedVehicleName,
                dark = dark,
                arabic = arabic,
                setDark = { value ->
                    dark = value
                    prefs.edit { putBoolean(KEY_DARK, value) }
                },
                setArabic = { value ->
                    arabic = value
                    prefs.edit { putBoolean(KEY_ARABIC, value) }
                },
                persistVehicle = { id, name ->
                    prefs.edit {
                        if (id.isNullOrBlank()) {
                            remove(KEY_ACTIVE_VEHICLE_ID); remove(KEY_ACTIVE_VEHICLE_NAME)
                        } else {
                            putString(KEY_ACTIVE_VEHICLE_ID, id)
                            if (!name.isNullOrBlank()) putString(KEY_ACTIVE_VEHICLE_NAME, name)
                        }
                    }
                }
            )
        }
    }
}

internal const val PREFS_UI = "cardiag_ui"
internal const val KEY_DARK = "dark"
internal const val KEY_ARABIC = "arabic"
internal const val KEY_ACTIVE_VEHICLE_ID = "active_vehicle_id"
internal const val KEY_ACTIVE_VEHICLE_NAME = "active_vehicle_name"
