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
fun AuthScreen(
    onAuthenticated: () -> Unit,
    onContinueAsGuest: () -> Unit,
    arabic: Boolean = false
) {
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
        Text(if (arabic) "الحساب اختياري. استخدمه لحفظ سياراتك وسجل التشخيص." else "Le compte est facultatif. Il permet de sauvegarder vos véhicules et votre historique.")
        OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Email" }, label = { Text("Email") }, singleLine = true, enabled = !loading)
        OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Password" }, label = { Text(if (arabic) "كلمة المرور" else "Mot de passe") }, singleLine = true, enabled = !loading, visualTransformation = PasswordVisualTransformation())
        Button(onClick = {
            scope.launch {
                loading = true
                message = ""
                try {
                    val cleanEmail = email.trim().lowercase()
                    if (signUpMode) {
                        auth.signUp(cleanEmail, password)
                        if (auth.currentUser == null) {
                            message = if (arabic) "تم إنشاء الحساب. إذا كان تأكيد البريد مفعّلًا في Supabase، افتح رسالة التأكيد ثم ارجع وسجّل الدخول." else "Compte créé. Si la confirmation email est activée dans Supabase, confirmez votre adresse puis reconnectez-vous."
                        } else onAuthenticated()
                    } else {
                        auth.signIn(cleanEmail, password)
                        if (auth.currentUser == null) message = if (arabic) "تم الدخول لكن لم يتم إنشاء جلسة. حاول مرة أخرى." else "Connexion effectuée mais aucune session n'a été créée. Réessayez." else onAuthenticated()
                    }
                } catch (e: Exception) {
                    val raw = e.message.orEmpty()
                    message = when {
                        raw.contains("email", ignoreCase = true) && raw.contains("confirm", ignoreCase = true) -> if (arabic) "البريد الإلكتروني غير مؤكد. افتح رسالة Supabase وأكد البريد ثم سجّل الدخول." else "Email non confirmé. Ouvrez le message Supabase, confirmez l'adresse puis reconnectez-vous."
                        raw.contains("invalid login credentials", ignoreCase = true) -> if (arabic) "البريد أو كلمة المرور غير صحيحة." else "Email ou mot de passe incorrect."
                        raw.contains("already registered", ignoreCase = true) || raw.contains("user already", ignoreCase = true) -> if (arabic) "هذا البريد مسجل بالفعل. استخدم تسجيل الدخول." else "Cet email est déjà enregistré. Utilisez Connexion."
                        else -> raw.ifBlank { if (arabic) "فشل المصادقة." else "Échec de l'authentification." }
                    }
                } finally { loading = false }
            }
        }, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
            if (loading) CircularProgressIndicator(strokeWidth = 2.dp) else Text(title)
        }
        TextButton(onClick = { signUpMode = !signUpMode; message = "" }, enabled = !loading) {
            Text(if (signUpMode) if (arabic) "لدي حساب بالفعل" else "J'ai déjà un compte" else if (arabic) "إنشاء حساب جديد" else "Créer un compte")
        }
        TextButton(onClick = {
            scope.launch {
                loading = true
                message = ""
                try {
                    auth.ensureGuest()
                    onContinueAsGuest()
                } catch (e: Exception) {
                    message = e.message ?: if (arabic) "تعذر إنشاء جلسة الضيف." else "Impossible de créer la session invité."
                } finally { loading = false }
            }
        }, enabled = !loading) {
            Text(if (arabic) "متابعة كضيف" else "Continuer en invité")
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
    }
}
