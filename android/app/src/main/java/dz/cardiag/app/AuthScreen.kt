package dz.cardiag.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.AuthService
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signUpMode by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val auth = remember { AuthService() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (signUpMode) "Create account" else "Sign in", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Button(
            onClick = {
                scope.launch {
                    loading = true
                    message = ""
                    try {
                        if (signUpMode) {
                            auth.signUp(email, password)
                            if (auth.currentUser == null) {
                                message = "Account created. Check your email to confirm the account, then sign in."
                            } else {
                                onAuthenticated()
                            }
                        } else {
                            auth.signIn(email, password)
                            if (auth.currentUser == null) error("Sign-in did not create a session")
                            onAuthenticated()
                        }
                    } catch (e: Exception) {
                        message = e.message ?: "Authentication failed"
                    } finally {
                        loading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) { Text(if (signUpMode) "Create account" else "Sign in") }
        Button(
            onClick = { signUpMode = !signUpMode; message = "" },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) { Text(if (signUpMode) "I already have an account" else "Create a new account") }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
