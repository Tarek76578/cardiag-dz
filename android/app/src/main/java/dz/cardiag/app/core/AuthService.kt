package dz.cardiag.app.core

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo

class AuthService {
    private val auth get() = SupabaseClient.client.auth

    val currentUser: UserInfo?
        get() = auth.currentSessionOrNull()?.user

    suspend fun signUp(email: String, password: String) {
        val normalized = email.trim()
        require(normalized.contains("@")) { "Valid email is required" }
        require(password.length >= 8) { "Password must contain at least 8 characters" }
        auth.signUpWith(Email) {
            this.email = normalized
            this.password = password
        }
    }

    suspend fun signIn(email: String, password: String) {
        val normalized = email.trim()
        require(normalized.contains("@")) { "Valid email is required" }
        require(password.isNotBlank()) { "Password is required" }
        auth.signInWith(Email) {
            this.email = normalized
            this.password = password
        }
    }

    suspend fun signOut() = auth.signOut()
}
