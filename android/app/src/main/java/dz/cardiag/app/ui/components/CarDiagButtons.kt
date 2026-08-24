package dz.cardiag.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CarDiagPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CarDiagSecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp)
    ) { Text(text) }
}

@Composable
fun CarDiagAiButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

@Composable
fun CarDiagActionRow(
    primaryText: String,
    secondaryText: String,
    aiText: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onAi: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CarDiagPrimaryButton(primaryText, Modifier.fillMaxWidth(), onClick = onPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CarDiagSecondaryButton(secondaryText, Modifier.weight(1f), onClick = onSecondary)
            CarDiagAiButton(aiText, Modifier.weight(1f), onClick = onAi)
        }
    }
}
