package dz.cardiag.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** CarDiag automotive palette: deep blue-black, not bright blue. */
object CarDiagColors {
    val Primary = Color(0xFF1B2733)
    val PrimaryStrong = Color(0xFF263746)
    val Accent = Color(0xFF8FA9BF)
    val AccentBright = Color(0xFFB8CBD9)
    val DarkBackground = Color(0xFF070B0F)
    val DarkSurface = Color(0xFF0D141B)
    val DarkSurfaceElevated = Color(0xFF131D26)
    val DarkBorder = Color(0xFF22303C)
    val LightBackground = Color(0xFFEFF3F6)
    val LightSurface = Color(0xFFF8FAFC)
    val LightBorder = Color(0xFFD3DDE5)
    val Success = Color(0xFF39B982)
    val Warning = Color(0xFFE6A23C)
    val Critical = Color(0xFFE85B68)
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
        primary = CarDiagColors.Primary,
        onPrimary = Color.White,
        secondary = CarDiagColors.Accent,
        onSecondary = Color.White,
        tertiary = CarDiagColors.AccentBright,
        background = CarDiagColors.DarkBackground,
        onBackground = Color(0xFFE7EDF2),
        surface = CarDiagColors.DarkSurface,
        onSurface = Color(0xFFE7EDF2),
        surfaceVariant = CarDiagColors.DarkSurfaceElevated,
        onSurfaceVariant = Color(0xFFA9B7C2),
        outline = CarDiagColors.DarkBorder,
        error = CarDiagColors.Critical
    ) else lightColorScheme(
        primary = CarDiagColors.Primary,
        onPrimary = Color.White,
        secondary = Color(0xFF40596D),
        onSecondary = Color.White,
        background = CarDiagColors.LightBackground,
        surface = CarDiagColors.LightSurface,
        onSurface = Color(0xFF17212A),
        surfaceVariant = Color(0xFFE5EBF0),
        onSurfaceVariant = Color(0xFF52616D),
        outline = CarDiagColors.LightBorder,
        error = Color(0xFFB4233C)
    )
    MaterialTheme(colorScheme = colors, content = content)
}
