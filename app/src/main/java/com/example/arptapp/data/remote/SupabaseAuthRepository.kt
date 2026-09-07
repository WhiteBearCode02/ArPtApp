package com.example.arptapp.data.remote

import android.content.Intent
import com.example.arptapp.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSession(
    val userId: String,
    val email: String,
    val isAdmin: Boolean
)

object AuthSessionStore {
    var current: AppSession? = null
}

class SupabaseAuthRepository {
    private val client = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
        install(GoTrue)
        install(Postgrest)
    }

    private val _session = MutableStateFlow<AppSession?>(null)
    val session: StateFlow<AppSession?> = _session.asStateFlow()

    suspend fun signUp(email: String, password: String): Result<AppSession?> = runCatching {
        client.gotrue.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        refreshSession()
    }

    suspend fun signIn(email: String, password: String): Result<AppSession> = runCatching {
        client.gotrue.signInWith(Email) {
            this.email = email
            this.password = password
        }
        refreshSession() ?: error("인증 세션을 확인할 수 없습니다.")
    }

    suspend fun startGoogleSignIn(): Result<Unit> = runCatching {
        client.gotrue.signInWith(Google) {
            redirectTo = REDIRECT_URI
        }
    }

    suspend fun handleAuthCallback(intent: Intent): Result<AppSession?> = runCatching {
        client.gotrue.handleDeeplinks(intent)
        refreshSession()
    }

    suspend fun signOut() {
        client.gotrue.signOut()
        AuthSessionStore.current = null
        _session.value = null
    }

    private suspend fun refreshSession(): AppSession? {
        val user = client.gotrue.currentUserOrNull() ?: run {
            AuthSessionStore.current = null
            return null
        }
        val email = user.email.orEmpty()
        val appSession = AppSession(
            userId = user.id,
            email = email,
            isAdmin = email.equals(BuildConfig.ADMIN_EMAIL, ignoreCase = true)
        )
        AuthSessionStore.current = appSession
        _session.value = appSession
        return appSession
    }

    private companion object {
        const val REDIRECT_URI = "arptapp://auth/callback"
    }
}