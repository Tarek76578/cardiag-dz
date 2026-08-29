@file:Suppress("InlinedApi")

package dz.cardiag.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Manifest entry preserved for backwards compatibility / external deep links.
 * The actual UX lives in [CarDiagUnifiedApp] under [CarDiagRoute.AI].
 */
class AiSymptomDiagnosisActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val route = runCatching { CarDiagRoute.valueOf(intent.getStringExtra(CarDiagActivityRoute.EXTRA_INITIAL_ROUTE) ?: CarDiagRoute.AI.name) }.getOrDefault(CarDiagRoute.AI)
        setContent {
            CarDiagUnifiedApp(
                initialRoute = route,
                initialDtcCode = intent.getStringExtra(CarDiagActivityRoute.EXTRA_DTC_CODE) ?: intent.getStringExtra("dtc_code"),
                initialVehicleId = intent.getStringExtra(CarDiagActivityRoute.EXTRA_VEHICLE_ID) ?: intent.getStringExtra("model_id"),
                initialVehicleName = intent.getStringExtra(CarDiagActivityRoute.EXTRA_VEHICLE_NAME) ?: intent.getStringExtra("model_name")
            )
        }
    }
}
