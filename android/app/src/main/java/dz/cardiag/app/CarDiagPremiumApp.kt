package dz.cardiag.app

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dz.cardiag.app.ui.theme.CarDiagTheme

@Composable
fun CarDiagPremiumApp() {
    val context = LocalContext.current
    CarDiagTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize()) {
            CarDiagExactApp()
            FloatingActionButton(
                onClick = {
                    context.startActivity(Intent(context, AiSymptomDiagnosisActivity::class.java))
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 82.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "AI Diagnosis")
            }
        }
    }
}
