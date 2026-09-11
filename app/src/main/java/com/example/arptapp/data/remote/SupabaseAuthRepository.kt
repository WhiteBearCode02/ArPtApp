package com.example.arptapp.data.remote

import android.content.Intent
import com.example.arptapp.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject

data class AppSession(
    val userId: String,
    val email: String,
    val isAdmin: Boolean,
    val requiresCurrentPassword: Boolean
)

object AuthSessionStore {
    var current: AppSession? = null
}

internal fun hasAdminRole(appMetadata: JsonObject?): Boolean = appMetadata
    ?.get("role")
    ?.jsonPrimitive
    ?.contentOrNull
    ?.equals("admin", ignoreCase = true) == true

class SupabaseAuthRepository {
    private val client by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        require(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()) {
            "Supabase 설정이 없습니다. local.properties를 확인해 주세요."
        }
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
            install(Auth) {
                // Email confirmation and OAuth return to MainActivity through
                // the arptapp://auth/callback intent filter in AndroidManifest.
                scheme = "arptapp"
                host = "auth"
            }
            install(Postgrest)
        }
    }

    private val _session = MutableStateFlow<AppSession?>(null)
    val session: StateFlow<AppSession?> = _session.asStateFlow()

    suspend fun signUp(email: String, password: String): Result<AppSession?> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        refreshSession()
    }

    suspend fun signIn(email: String, password: String): Result<AppSession> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        refreshSession() ?: error("인증 세션을 확인할 수 없습니다.")
    }

    suspend fun startGoogleSignIn(): Result<Unit> = runCatching {
        client.auth.signInWith(Google, REDIRECT_URI)
    }

    suspend fun handleAuthCallback(intent: Intent): Result<AppSession?> = runCatching {
        client.handleDeeplinks(intent)
        refreshSession()
    }

    suspend fun restoreSession(): Result<AppSession?> = runCatching {
        refreshSession()
    }

    suspend fun updateEmail(newEmail: String): Result<Unit> = runCatching {
        check(client.auth.currentUserOrNull() != null) { "로그인이 필요한 작업입니다." }
        client.auth.updateUser {
            email = newEmail
        }
        refreshSession()
        Unit
    }

    suspend fun updatePassword(
        currentPassword: String,
        newPassword: String
    ): Result<Unit> = runCatching {
        val user = client.auth.currentUserOrNull()
            ?: error("로그인이 필요한 작업입니다.")
        val email = user.email
            ?: error("이메일 로그인 사용자만 비밀번호를 변경할 수 있습니다.")
        val requiresCurrentPassword = user.identities.orEmpty().any { it.provider == "email" }
        if (requiresCurrentPassword) {
            require(currentPassword.isNotBlank()) { "현재 비밀번호를 입력해 주세요." }
            client.auth.signInWith(Email) {
                this.email = email
                password = currentPassword
            }
        }
        client.auth.updateUser {
            password = newPassword
        }
        refreshSession()
        Unit
    }

    suspend fun signOut() {
        client.auth.signOut()
        AuthSessionStore.current = null
        _session.value = null
    }

    private suspend fun refreshSession(): AppSession? {
        val user = client.auth.currentUserOrNull() ?: run {
            AuthSessionStore.current = null
            return null
        }
        val email = user.email.orEmpty()
        val appSession = AppSession(
            userId = user.id,
            email = email,
            isAdmin = hasAdminRole(user.appMetadata),
            requiresCurrentPassword = user.identities.orEmpty().any { it.provider == "email" }
        )
        AuthSessionStore.current = appSession
        _session.value = appSession
        return appSession
    }

    private companion object {
        const val REDIRECT_URI = "arptapp://auth/callback"
    }
}
