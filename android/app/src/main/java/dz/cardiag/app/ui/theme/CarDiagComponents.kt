package dz.cardiag.app.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun CarDiagPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = CarDiagShapes.Medium,
        contentPadding = ButtonDefaults.ContentPadding
    ) { Text(text) }
}

@Composable
fun CarDiagCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = CarDiagShapes.Card) {
        Column(
            modifier = Modifier.padding(CarDiagSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CarDiagSpacing.sm),
            content = content
        )
    }
}

@Composable
fun CarDiagStatusCard(title: String, message: String, severity: CarDiagSeverity) {
    val container = when (severity) {
        CarDiagSeverity.SUCCESS -> CarDiagColors.Success.copy(alpha = .12f)
        CarDiagSeverity.WARNING -> CarDiagColors.Warning.copy(alpha = .14f)
        CarDiagSeverity.CRITICAL -> CarDiagColors.Critical.copy(alpha = .14f)
        CarDiagSeverity.INFO -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when (severity) {
        CarDiagSeverity.SUCCESS -> CarDiagColors.Success
        CarDiagSeverity.WARNING -> CarDiagColors.Warning
        CarDiagSeverity.CRITICAL -> CarDiagColors.Critical
        CarDiagSeverity.INFO -> MaterialTheme.colorScheme.primary
    }
    Card(colors = CardDefaults.cardColors(containerColor = container), shape = CarDiagShapes.Card) {
        Row(modifier = Modifier.padding(CarDiagSpacing.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(CarDiagSpacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(message, color = content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

enum class CarDiagSeverity { SUCCESS, INFO, WARNING, CRITICAL }

@Composable
fun CarDiagLoadingState(message: String = "Chargement…") {
    CarDiagStatusCard("CarDiag", message, CarDiagSeverity.INFO)
}

@Composable
fun CarDiagEmptyState(title: String, message: String) {
    CarDiagStatusCard(title, message, CarDiagSeverity.INFO)
}

@Composable
fun CarDiagErrorState(title: String, message: String, retry: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(CarDiagSpacing.sm)) {
        CarDiagStatusCard(title, message, CarDiagSeverity.CRITICAL)
        retry?.let { TextButton(onClick = it) { Text("Réessayer") } }
    }
}
