package dz.cardiag.app.core

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Email
import io.github.jan.supabase.auth.user.UserInfo

class AuthService {
    private val auth get() = SupabaseClient.client.auth

    val currentUser: UserInfo?
        get() = auth.currentUserOrNull()

    suspend fun signUp(email: String, password: String) {
        require(email.isNotBlank()) { "Email is required" }
        require(password.length >= 8) { "Password must contain at least 8 characters" }
        auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signIn(email: String, password: String) {
        require(email.isNotBlank()) { "Email is required" }
        require(password.isNotBlank()) { "Password is required" }
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }
}
