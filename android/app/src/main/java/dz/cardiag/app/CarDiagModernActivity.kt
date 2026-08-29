package dz.cardiag.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dz.cardiag.app.core.SupabaseClientRef

/** Single launcher entry point for the production CarDiag application shell. */
class CarDiagModernActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseClientRef.init(applicationContext)
        setContent { CarDiagUnifiedApp() }
    }
}