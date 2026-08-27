package dz.cardiag.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** Dedicated vehicle selection entry point used by the production dashboard. */
class VehicleCatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CarDiagExactApp() }
    }
}