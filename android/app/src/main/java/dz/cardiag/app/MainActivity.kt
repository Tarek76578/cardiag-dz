package dz.cardiag.app

import android.os.Bundle
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
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                CarDiagApp()
            }
        }
    }
}

@Composable
fun CarDiagApp() {
    var showDiagnostic by remember { mutableStateOf(false) }

    Scaffold { padding ->

        if (!showDiagnostic) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Text(
                    text = "CarDiag DZ",
                    style = MaterialTheme.typography.headlineLarge
                )

                Text(
                    text = "تشخيص السيارات بالذكاء الاصطناعي",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Diagnostic automobile intelligent"
                )

                Button(
                    onClick = {
                        showDiagnostic = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ابدأ التشخيص")
                }
            }

        } else {

            DiagnosticScreen(
                onBack = {
                    showDiagnostic = false
                }
            )
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
            text = "تشخيص السيارة",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Diagnostic automobile"
        )

        OutlinedTextField(
            value = carInfo,
            onValueChange = { carInfo = it },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("السيارة / Véhicule")
            },
            placeholder = {
                Text("مثال: Renault Clio 4 1.5 dCi")
            }
        )

        OutlinedTextField(
            value = problem,
            onValueChange = { problem = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            label = {
                Text("صف المشكلة / Décrivez le problème")
            },
            placeholder = {
                Text("مثال: المحرك يهتز عند التوقف...")
            }
        )

        Button(
            onClick = {
                result =
                    if (carInfo.isBlank() || problem.isBlank()) {
                        "يرجى إدخال معلومات السيارة ووصف المشكلة."
                    } else {
                        "تم استلام طلب التشخيص. سيتم ربطه بمحرك الذكاء الاصطناعي."
                    }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("تشخيص بالذكاء الاصطناعي")
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
            Text("رجوع")
        }
    }
}
