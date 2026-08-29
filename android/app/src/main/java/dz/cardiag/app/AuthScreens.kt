package dz.cardiag.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.AuthService
import kotlinx.coroutines.launch

/**
 * Localized sign-in / sign-up screen. The "Continue as guest" action is the
 * primary entry point and is always available; the user is never required
 * to create an account to use CarDiag.
 */
@Composable
fun AuthScreen(
    padding: PaddingValues,
    arabic: Boolean,
    onAuthenticated: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val auth = remember { AuthService() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signUpMode by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    LaunchedEffect(Unit) {
        // If we are already authenticated, no need to show the form.
        runCatching { auth.currentUser }.getOrNull()?.let { onAuthenticated() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.auth_back))
                }
            } else {
                Spacer(Modifier.width(4.dp))
            }
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (signUpMode) stringResource(R.string.auth_sign_up) else stringResource(R.string.auth_sign_in),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = stringResource(R.string.auth_continue_guest_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = if (arabic) "auth_email" else "auth_email" },
            label = { Text(stringResource(R.string.auth_email)) },
            placeholder = { Text(stringResource(R.string.auth_email_placeholder)) },
            singleLine = true,
            enabled = !loading
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "auth_password" },
            label = { Text(stringResource(R.string.auth_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !loading
        )
        Button(
            onClick = {
                scope.launch {
                    loading = true
                    message = null
                    val failureMessageId: Int? = null
                    val failureText: String? = runCatching {
                        val cleanEmail = email.trim().lowercase()
                        if (signUpMode) {
                            auth.signUp(cleanEmail, password)
                            if (auth.currentUser == null) {
                                return@runCatching resolveFailure(R.string.auth_failed_signup_confirm)
                            } else {
                                onAuthenticated()
                                return@runCatching null
                            }
                        } else {
                            auth.signIn(cleanEmail, password)
                            if (auth.currentUser == null) {
                                return@runCatching resolveFailure(R.string.auth_failed_no_session)
                            } else {
                                onAuthenticated()
                                return@runCatching null
                            }
                        }
                    }.getOrElse { e -> mapAuthError(e.message.orEmpty(), arabic) }
                    if (failureText != null) message = failureText
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (signUpMode) stringResource(R.string.auth_sign_up) else stringResource(R.string.auth_sign_in),
                fontWeight = FontWeight.Bold
            )
        }
        TextButton(onClick = { signUpMode = !signUpMode; message = null }, enabled = !loading) {
            Text(
                text = if (signUpMode) stringResource(R.string.auth_have_account) else stringResource(R.string.auth_no_account)
            )
        }
        message?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = msg,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.guest_banner_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.guest_banner_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    loading = true
                    message = null
                    val failureText: String? = runCatching {
                        auth.ensureGuest()
                        onAuthenticated()
                        null
                    }.getOrElse { e -> mapAuthError(e.message.orEmpty(), arabic) }
                    if (failureText != null) message = failureText
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Person, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.auth_continue_guest), fontWeight = FontWeight.Bold)
        }
    }
}

/** Resolve a failure message using the application context (no recomposition). */
private fun resolveFailure(@androidx.annotation.StringRes id: Int): String {
    return dz.cardiag.app.core.SupabaseClientRef.context.getString(id)
}

private fun mapAuthError(raw: String, arabic: Boolean): String {
    val context = dz.cardiag.app.core.SupabaseClientRef.context
    val arabicPattern = Regex("\\p{InArabic}")
    val isAr = arabic || (arabicPattern.containsMatchIn(raw))
    return when {
        raw.isBlank() -> if (isAr) context.getString(R.string.auth_failed_generic) else context.getString(R.string.auth_failed_generic)
        raw.contains("email", ignoreCase = true) && raw.contains("confirm", ignoreCase = true) -> context.getString(R.string.auth_failed_confirm_email)
        raw.contains("invalid login credentials", ignoreCase = true) -> context.getString(R.string.auth_failed_invalid_credentials)
        raw.contains("already registered", ignoreCase = true) || raw.contains("user already", ignoreCase = true) -> context.getString(R.string.auth_failed_already_registered)
        else -> raw
    }
}

@Composable
internal fun GuestStatusBanner(arabic: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.guest_banner_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.guest_banner_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
