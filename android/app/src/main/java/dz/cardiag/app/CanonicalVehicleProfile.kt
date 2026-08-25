package dz.cardiag.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class CanonicalVehicle(
    val make: String,
    val model: String,
    val modelYear: Int,
    val engine: String?,
    val engineYear: Int?,
    val displacementCc: Double?,
    val cylinders: Double?,
    val powerHp: Double?,
    val transmission: String?,
    val drivetrain: String?,
    val fuelType: String?
)

@Composable
fun CanonicalVehicleProfile(vehicle: CanonicalVehicle, modifier: Modifier = Modifier) {
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(vehicle.make, style = MaterialTheme.typography.labelLarge, color = Color(0xFF48D7C5))
            Text(vehicle.model, style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Model year", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Text(vehicle.modelYear.toString())
            }
        }
        item { Text("Engine", style = MaterialTheme.typography.titleLarge) }
        item {
            Card(colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(vehicle.engine ?: "Engine information unavailable", style = MaterialTheme.typography.titleMedium)
                    vehicle.engineYear?.let { Text("Engine year: $it") }
                    vehicle.displacementCc?.let { Text("Displacement: ${it.toInt()} cc") }
                    vehicle.cylinders?.let { Text("Cylinders: ${it.toInt()}") }
                    vehicle.powerHp?.let { Text("Power: $it HP") }
                    vehicle.fuelType?.let { Text("Fuel: $it") }
                    vehicle.transmission?.let { Text("Transmission: $it") }
                    vehicle.drivetrain?.let { Text("Drivetrain: $it") }
                }
            }
        }
    }
}
