package dz.cardiag.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object CarDiagColors {
    val Primary = Color(0xFF2563EB)
    val PrimaryStrong = Color(0xFF1D4ED8)
    val Accent = Color(0xFF38BDF8)
    val AccentBright = Color(0xFF7DD3FC)
    val DarkBackground = Color(0xFF070A0F)
    val DarkSurface = Color(0xFF0E141D)
    val DarkSurfaceElevated = Color(0xFF151E2A)
    val DarkBorder = Color(0xFF243244)
    val DarkOnSurface = Color(0xFFF1F5F9)
    val DarkMuted = Color(0xFFB8C4D1)
    val LightBackground = Color(0xFFEFF3F7)
    val LightSurface = Color(0xFFF8FAFC)
    val LightSurfaceVariant = Color(0xFFE8EEF5)
    val LightBorder = Color(0xFFD7E0EA)
    val LightOnSurface = Color(0xFF111827)
    val LightMuted = Color(0xFF526174)
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Critical = Color(0xFFEF4444)
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

object CarDiagTypography {
    val Default = Typography()
    val Brand = Typography(
        displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black),
        headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold),
        headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    )
}

@Composable
fun CarDiagTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (darkTheme) darkColorScheme(
        primary = CarDiagColors.Primary, onPrimary = Color.White,
        primaryContainer = CarDiagColors.PrimaryStrong, onPrimaryContainer = Color.White,
        secondary = CarDiagColors.Accent, onSecondary = Color(0xFF041018),
        secondaryContainer = Color(0xFF123B55), onSecondaryContainer = Color(0xFFD7F2FF),
        tertiary = CarDiagColors.AccentBright, onTertiary = Color(0xFF061018),
        background = CarDiagColors.DarkBackground, onBackground = CarDiagColors.DarkOnSurface,
        surface = CarDiagColors.DarkSurface, onSurface = CarDiagColors.DarkOnSurface,
        surfaceVariant = CarDiagColors.DarkSurfaceElevated, onSurfaceVariant = CarDiagColors.DarkMuted,
        outline = CarDiagColors.DarkBorder, error = CarDiagColors.Critical, onError = Color.White
    ) else lightColorScheme(
        primary = CarDiagColors.PrimaryStrong, onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE8FF), onPrimaryContainer = Color(0xFF08245F),
        secondary = Color(0xFF334155), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE2E8F0), onSecondaryContainer = Color(0xFF172033),
        tertiary = Color(0xFF0284C7), onTertiary = Color.White,
        background = CarDiagColors.LightBackground, onBackground = CarDiagColors.LightOnSurface,
        surface = CarDiagColors.LightSurface, onSurface = CarDiagColors.LightOnSurface,
        surfaceVariant = CarDiagColors.LightSurfaceVariant, onSurfaceVariant = CarDiagColors.LightMuted,
        outline = CarDiagColors.LightBorder, error = Color(0xFFDC2626), onError = Color.White
    )
    MaterialTheme(colorScheme = colors, typography = CarDiagTypography.Brand, content = content)
}
