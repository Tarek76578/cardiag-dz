package dz.cardiag.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object CarDiagColors {
    val ElectricBlue = Color(0xFF64B5FF)
    val Cyan = Color(0xFF55D6BE)
    val DarkBackground = Color(0xFF080B10)
    val DarkSurface = Color(0xFF10141B)
    val LightBackground = Color(0xFFF3F6FA)
    val LightSurface = Color(0xFFF8FAFC)
    val Success = Color(0xFF32C48D)
    val Warning = Color(0xFFFFB547)
    val Critical = Color(0xFFFF5C70)
}

object CarDiagShapes {
    val Small = RoundedCornerShape(12.dp)
    val Medium = RoundedCornerShape(16.dp)
    val Card = RoundedCornerShape(20.dp)
    val Large = RoundedCornerShape(24.dp)
    val Hero = RoundedCornerShape(28.dp)
}

object CarDiagSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

@Composable
fun CarDiagTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (darkTheme) darkColorScheme(
        primary = CarDiagColors.ElectricBlue,
        secondary = CarDiagColors.Cyan,
        background = CarDiagColors.DarkBackground,
        surface = CarDiagColors.DarkSurface,
        error = CarDiagColors.Critical
    ) else lightColorScheme(
        primary = Color(0xFF1769AA),
        secondary = Color(0xFF087F6B),
        background = CarDiagColors.LightBackground,
        surface = CarDiagColors.LightSurface,
        error = Color(0xFFB4233C)
    )
    MaterialTheme(colorScheme = colors, content = content)
}
