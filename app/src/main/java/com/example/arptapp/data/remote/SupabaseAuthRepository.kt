package com.example.arptapp.data.remote

import android.content.Intent
import com.example.arptapp.BuildConfig
import com.example.arptapp.core.auth.PasswordRecoveryGrant
import com.example.arptapp.core.auth.PasswordRecoveryPolicy
import com.example.arptapp.core.coroutines.suspendResult
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@kotlinx.serialization.Serializable
data class SignUpProfile(
    val name: String,
    val weightKg: Double?,
    val skeletalMuscleMassKg: Double?,
    val bodyFatMassKg: Double?,
    val bodyFatPercentage: Double?
)

data class RegisteredAccount(val userId: String, val session: AppSession?)

data class AppSession(
    val userId: String,
    val email: String,
    val isAdmin: Boolean,
    val requiresCurrentPassword: Boolean
)

object AuthSessionStore {
    private val mutableSession = MutableStateFlow<AppSession?>(null)
    val session: StateFlow<AppSession?> = mutableSession.asStateFlow()

    // 기존 current API도 같은 상태를 읽고 써서 저장소 인스턴스 사이의 세션 불일치를 막는다.
    var current: AppSession?
        get() = mutableSession.value
        set(value) { mutableSession.value = value }
}

internal fun hasAdminRole(appMetadata: JsonObject?): Boolean = appMetadata
    ?.get("role")
    ?.jsonPrimitive
    ?.contentOrNull
    ?.equals("admin", ignoreCase = true) == true

/**
 * Supabase Auth SDK 어댑터. 인증·재인증·딥링크 검증만 담당하며 화면 이동/신체정보 저장은 하지 않습니다.
 * 관리자 권한은 사용자 편집이 불가능한 appMetadata에서만 읽습니다. 최종 데이터 접근 권한은 서버 RLS가 검증합니다.
 * suspendResult로 lifecycle 취소를 상위 호출자에게 전달하고 암호/토큰을 로그에 남기지 않습니다.
 */
class SupabaseAuthRepository {
    private val client get() = SupabaseClientProvider.client
    private val recoveryClient = lazy { SupabaseClientProvider.createRecoveryClient() }
    private val recoveryGrant = PasswordRecoveryGrant()
    private val recoveryMutex = Mutex()
    private val issuer get() = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1"

    val session: StateFlow<AppSession?> = AuthSessionStore.session

    suspend fun signUp(email: String, password: String, profile: SignUpProfile): Result<RegisteredAccount> = suspendResult {
        val registered = client.auth.signUpWith(Email, redirectUrl = REDIRECT_URI) {
            this.email = email
            this.password = password
            // Body information must never enter Auth metadata/JWTs. Authenticated profile sync owns it.
            data = signUpAuthMetadata(profile)
        }
        val userId = registered?.id ?: client.auth.currentUserOrNull()?.id
            ?: error("가입 계정을 확인할 수 없습니다.")
        // A pre-existing session must never bind a new signup's body draft to the wrong account.
        val session = if (client.auth.currentUserOrNull()?.id == userId) refreshSession() else null
        RegisteredAccount(userId, session)
    }

    suspend fun signIn(email: String, password: String): Result<AppSession> = suspendResult {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        refreshSession() ?: error("인증 세션을 확인할 수 없습니다.")
    }

    suspend fun startGoogleSignIn(): Result<Unit> = suspendResult {
        client.auth.signInWith(Google, REDIRECT_URI)
    }

    suspend fun requestPasswordReset(email: String): Result<Unit> = suspendResult {
        require(email.isNotBlank()) { "이메일을 입력해 주세요." }
        client.auth.resetPasswordForEmail(
            email = email.trim(),
            redirectUrl = PASSWORD_RESET_REDIRECT_URI
        )
    }

    suspend fun handlePasswordRecovery(intent: Intent): Result<Unit> = suspendResult {
        recoveryMutex.withLock {
            recoveryGrant.clear()
            val credentials = PasswordRecoveryPolicy.parse(intent.dataString, issuer, System.currentTimeMillis() / 1000)
            val auth = recoveryClient.value.auth
            auth.awaitInitialization()
            // Validate the EXACT incoming token with Auth before trusting its recovery AMR/claims.
            // Existing currentUser != null is never evidence that a recovery link is valid.
            val verified = auth.retrieveUser(credentials.accessToken)
            check(verified.id == credentials.claims.userId) { "재설정 인증에 실패했습니다." }
            // Also validate/rotate the refresh token; reject token pairs from different accounts/sessions.
            val restored = auth.refreshSession(credentials.refreshToken)
            val claims = PasswordRecoveryPolicy.claims(restored.accessToken, issuer, System.currentTimeMillis() / 1000)
            check(restored.user?.id == verified.id && claims.userId == verified.id &&
                claims.sessionId == credentials.claims.sessionId) { "재설정 인증에 실패했습니다." }
            auth.importSession(restored, autoRefresh = false)
            recoveryGrant.authorize(claims)
        }
    }

    suspend fun updateRecoveredPassword(newPassword: String, expectedVersion: Long): Result<Unit> = suspendResult {
        recoveryMutex.withLock {
            val auth = recoveryClient.value.auth
            val session = auth.currentSessionOrNull() ?: error("재설정 인증이 필요합니다.")
            val claims = PasswordRecoveryPolicy.claims(session.accessToken, issuer, System.currentTimeMillis() / 1000)
            recoveryGrant.requireAuthorized(session.user?.id, claims.sessionId, expectedVersion)
            require(newPassword.length >= 8) { "비밀번호는 8자 이상 입력해 주세요." }
            auth.updateUser { password = newPassword }
            recoveryGrant.clear() // one successful update per verified recovery capability
            // Revoke this recovered account's sessions, not an unrelated ordinary login's sessions.
            try { suspendResult { auth.signOut() } } finally { auth.clearSession() }
        }
        Unit
    }

    /** Abandoning recovery must not leave its newly imported session logged in. No server-wide logout. */
    suspend fun discardPasswordRecovery() = recoveryMutex.withLock {
        recoveryGrant.clear()
        if (recoveryClient.isInitialized()) recoveryClient.value.auth.clearSession()
    }

    fun hasPasswordRecoveryAuthorization(): Boolean = runCatching {
        if (!recoveryClient.isInitialized()) return false
        val current = recoveryClient.value.auth.currentSessionOrNull() ?: return false
        recoveryGrant.requireAuthorized(current.user?.id, PasswordRecoveryPolicy.sessionIdForCleanup(current.accessToken))
        true
    }.getOrDefault(false)

    fun passwordRecoveryAuthorizationVersion(): Long? =
        if (hasPasswordRecoveryAuthorization()) recoveryGrant.version() else null

    suspend fun handleAuthCallback(intent: Intent): Result<AppSession?> = suspendResult {
        val uri = intent.data
        require(uri?.scheme == "arptapp" && uri.host == "auth" && uri.path == "/callback" &&
            uri.port == -1 && uri.userInfo == null && !uri.fragment.isNullOrBlank() &&
            !uri.fragment.orEmpty().split('&').contains("type=recovery")) { "유효하지 않은 인증 링크입니다." }
        client.handleDeeplinks(intent)
        refreshSession()
    }

    suspend fun restoreSession(): Result<AppSession?> = suspendResult {
        client.auth.awaitInitialization()
        refreshSession()
    }

    suspend fun updateEmail(newEmail: String): Result<Unit> = suspendResult {
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
    ): Result<Unit> = suspendResult {
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

    suspend fun verifyCurrentPassword(currentPassword: String): Result<Unit> = suspendResult {
        val user = client.auth.currentUserOrNull() ?: error("로그인이 필요합니다.")
        require(user.identities.orEmpty().any { it.provider == "email" }) {
            "Google 계정의 비밀번호는 Google 계정에서 변경해 주세요."
        }
        require(currentPassword.isNotBlank()) { "현재 비밀번호를 입력해 주세요." }
        val userId = user.id
        val email = user.email ?: error("계정 이메일을 확인할 수 없습니다.")
        client.auth.signInWith(Email) {
            this.email = email
            password = currentPassword
        }
        check(client.auth.currentUserOrNull()?.id == userId) { "계정 확인에 실패했습니다." }
        refreshSession()
        Unit
    }

    suspend fun signOut() {
        recoveryGrant.clear()
        client.auth.awaitInitialization()
        client.auth.signOut()
        AuthSessionStore.current = null
    }

    private fun refreshSession(): AppSession? {
        val user = client.auth.currentUserOrNull() ?: run {
            AuthSessionStore.current = null
            return null
        }
        val appSession = AppSession(
            userId = user.id,
            email = user.email.orEmpty(),
            isAdmin = hasAdminRole(user.appMetadata),
            requiresCurrentPassword = user.identities.orEmpty().any { it.provider == "email" }
        )
        AuthSessionStore.current = appSession
        return appSession
    }

    private companion object {
        const val REDIRECT_URI = "arptapp://auth/callback"
        const val PASSWORD_RESET_REDIRECT_URI = PasswordRecoveryPolicy.REDIRECT_URI
    }
}

/** Deliberate allowlist; adding a SignUpProfile field cannot silently expand Auth/JWT data. */
internal fun signUpAuthMetadata(profile: SignUpProfile): JsonObject = buildJsonObject {
    put("name", profile.name)
}
