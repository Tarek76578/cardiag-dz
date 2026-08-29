package dz.cardiag.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.AppMode
import dz.cardiag.app.core.AuthService
import dz.cardiag.app.ui.components.CarDiagPrimaryButton
import dz.cardiag.app.ui.components.CarDiagSecondaryButton
import dz.cardiag.app.ui.theme.CarDiagShapes
import kotlinx.coroutines.launch

/**
 * First-launch onboarding flow:
 * 1. Language
 * 2. Mode (Conducteur / Mécanicien)
 * 3. Continue as Guest (account creation is optional and offered, never required)
 */
@Composable
fun OnboardingScreen(
    onLanguageChosen: (Boolean) -> Unit,
    onModeChosen: (AppMode) -> Unit,
    onContinue: (useGuest: Boolean) -> Unit,
    initialArabic: Boolean,
    initialMode: AppMode
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var step by remember { mutableIntStateOf(0) }
    var arabic by remember { mutableStateOf(initialArabic) }
    var mode by remember { mutableStateOf(initialMode) }

    val totalSteps = 3
    val progress = (step + 1).toFloat() / totalSteps.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_welcome),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (step) {
                0 -> stringResource(R.string.onboarding_step_language)
                1 -> stringResource(R.string.onboarding_step_mode)
                else -> stringResource(R.string.onboarding_step_guest)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        when (step) {
            0 -> {
                Text(
                    text = stringResource(R.string.onboarding_language_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_language_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                OnboardingChoiceCard(
                    label = stringResource(R.string.onboarding_lang_fr),
                    icon = Icons.Default.PrivacyTip,
                    selected = !arabic,
                    onClick = { arabic = false; onLanguageChosen(false) }
                )
                Spacer(Modifier.height(12.dp))
                OnboardingChoiceCard(
                    label = stringResource(R.string.onboarding_lang_ar),
                    icon = Icons.Default.DirectionsCar,
                    selected = arabic,
                    onClick = { arabic = true; onLanguageChosen(true) }
                )
            }
            1 -> {
                Text(
                    text = stringResource(R.string.onboarding_mode_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_mode_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                OnboardingChoiceCard(
                    label = stringResource(R.string.onboarding_mode_driver),
                    description = stringResource(R.string.onboarding_mode_driver_desc),
                    icon = Icons.Default.DirectionsCar,
                    selected = mode == AppMode.DRIVER,
                    onClick = { mode = AppMode.DRIVER; onModeChosen(AppMode.DRIVER) }
                )
                Spacer(Modifier.height(12.dp))
                OnboardingChoiceCard(
                    label = stringResource(R.string.onboarding_mode_mechanic),
                    description = stringResource(R.string.onboarding_mode_mechanic_desc),
                    icon = Icons.Default.Build,
                    selected = mode == AppMode.MECHANIC,
                    onClick = { mode = AppMode.MECHANIC; onModeChosen(AppMode.MECHANIC) }
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.onboarding_continue_as_guest),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_continue_as_guest_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                OnboardingChoiceCard(
                    label = stringResource(R.string.onboarding_continue_as_guest),
                    description = stringResource(R.string.onboarding_guest_optional),
                    icon = Icons.Default.Person,
                    selected = true,
                    onClick = { onContinue(true) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        if (step < 2) {
            CarDiagPrimaryButton(
                text = stringResource(R.string.onboarding_continue),
                onClick = {
                    when (step) {
                        0 -> onLanguageChosen(arabic)
                        1 -> onModeChosen(mode)
                    }
                    step++
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            CarDiagPrimaryButton(
                text = stringResource(R.string.onboarding_finish),
                icon = Icons.Default.CheckCircle,
                onClick = { onContinue(true) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OnboardingChoiceCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    description: String? = null
) {
    val cd = label
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .semantics { contentDescription = cd },
        shape = CarDiagShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (description != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
