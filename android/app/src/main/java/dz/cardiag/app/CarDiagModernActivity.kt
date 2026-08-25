package dz.cardiag.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class CarDiagModernActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CarDiagExactApp() }
    }
}

fun openObd(
    context: Context,
    modelId: String? = null,
    modelName: String? = null,
    dtc: String? = null,
) {
    context.startActivity(Intent(context, ObdScannerActivity::class.java).apply {
        putExtra("model_id", modelId)
        putExtra("model_name", modelName ?: "Véhicule")
        putExtra("dtc_code", dtc)
    })
}

fun openGuided(context: Context, modelId: String? = null, modelName: String? = null) {
    context.startActivity(Intent(context, GuidedDiagnosisActivity::class.java).apply {
        putExtra("model_id", modelId)
        putExtra("model_name", modelName ?: "Véhicule")
        putExtra("source", "vehicle_profile")
    })
}
