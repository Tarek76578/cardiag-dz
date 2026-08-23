package dz.cardiag.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dz.cardiag.app.core.AuthService
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onAuthenticated: () -> Unit, arabic: Boolean = false) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signUpMode by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val auth = remember { AuthService() }
    val title = if (arabic) if (signUpMode) "إنشاء حساب" else "تسجيل الدخول" else if (signUpMode) "Créer un compte" else "Connexion"

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("CarDiag DZ", style = MaterialTheme.typography.headlineLarge)
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(if (arabic) "حسابك يحفظ سياراتك وسجل التشخيص بأمان." else "Votre compte protège vos véhicules et votre historique de diagnostic.")
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Email" },
            label = { Text("Email") },
            singleLine = true,
            enabled = !loading
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Password" },
            label = { Text(if (arabic) "كلمة المرور" else "Mot de passe") },
            singleLine = true,
            enabled = !loading,
            visualTransformation = PasswordVisualTransformation()
        )
        Button(
            onClick = {
                scope.launch {
                    loading = true; message = ""
                    try {
                        if (signUpMode) auth.signUp(email, password) else auth.signIn(email, password)
                        if (auth.currentUser == null) {
                            message = if (arabic) "تم إنشاء الحساب. تحقق من بريدك ثم سجل الدخول." else "Compte créé. Vérifiez votre email puis connectez-vous."
                        } else onAuthenticated()
                    } catch (e: Exception) { message = e.message ?: if (arabic) "فشل تسجيل الدخول" else "Échec de connexion" }
                    finally { loading = false }
                }
            },
            modifier = Modifier.fillMaxWidth(), enabled = !loading
        ) {
            if (loading) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text(title)
        }
        TextButton(onClick = { signUpMode = !signUpMode; message = "" }, enabled = !loading) {
            Text(if (signUpMode) if (arabic) "لدي حساب بالفعل" else "J'ai déjà un compte" else if (arabic) "إنشاء حساب جديد" else "Créer un compte")
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
    }
}
