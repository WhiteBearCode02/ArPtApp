package com.example.arptapp.core.auth

import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import kotlinx.serialization.json.*

/** Strict implicit callback parser. JWT claims are NOT trusted until Auth verifies the exact token. */
object PasswordRecoveryPolicy {
    const val REDIRECT_URI = "arptapp://auth/reset-password"
    private const val INVALID_LINK = "유효하지 않거나 만료된 재설정 링크입니다. 다시 요청해 주세요."
    private val allowed = setOf("access_token", "refresh_token", "token_type", "type", "expires_in", "expires_at")

    fun parse(raw: String?, issuer: String, nowSeconds: Long): RecoveryCredentials {
        // URI/JSON exception messages can contain credentials. Never attach the original exception.
        return try { parseChecked(requireNotNull(raw), issuer, nowSeconds) }
        catch (_: Exception) { throw IllegalArgumentException(INVALID_LINK) }
    }

    private fun parseChecked(raw: String, issuer: String, now: Long): RecoveryCredentials {
        require(raw.length <= 16_384 && raw.none { it.isWhitespace() || it.code < 32 })
        val uri = URI(raw)
        require(uri.scheme == "arptapp" && uri.rawAuthority == "auth" &&
            uri.rawPath == "/reset-password" && uri.rawQuery == null)
        val params = linkedMapOf<String, String>()
        require(!uri.rawFragment.isNullOrEmpty())
        uri.rawFragment.split('&').forEach { part ->
            val pair = part.split('=', limit = 2)
            require(pair.size == 2)
            val key = URLDecoder.decode(pair[0], "UTF-8")
            val value = URLDecoder.decode(pair[1], "UTF-8")
            require(key in allowed && value.isNotBlank() && params.put(key, value) == null)
        }
        require(params["type"] == "recovery" && params["token_type"] == "bearer")
        val access = requireNotNull(params["access_token"])
        val refresh = requireNotNull(params["refresh_token"])
        require(access.length <= 8192 && access.matches(Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")))
        require(refresh.length in 16..4096 && refresh.matches(Regex("[A-Za-z0-9_-]+")))
        params["expires_in"]?.let { require(it.toLong() in 1..604_800) }
        params["expires_at"]?.let { require(it.toLong() > now) }
        return RecoveryCredentials(access, refresh, claims(access, issuer, now))
    }

    fun claims(token: String, issuer: String, now: Long): RecoveryClaims {
        val payload = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8)).jsonObject
        fun text(key: String) = payload[key]?.jsonPrimitive?.content
        val expires = payload.getValue("exp").jsonPrimitive.long
        val issued = payload.getValue("iat").jsonPrimitive.long
        require(text("iss") == issuer && text("aud") == "authenticated" && text("role") == "authenticated")
        require(payload["is_anonymous"]?.jsonPrimitive?.booleanOrNull != true)
        require(expires > now && issued in (now - 900)..(now + 60))
        val methods = payload.getValue("amr").jsonArray.map { it.jsonObject }
            .filter { it["method"]?.jsonPrimitive?.content != "token_refresh" }
        val latest = requireNotNull(methods.maxByOrNull { it.getValue("timestamp").jsonPrimitive.long })
        require(latest["method"]?.jsonPrimitive?.content == "recovery")
        require(latest.getValue("timestamp").jsonPrimitive.long in (now - 900)..(now + 60))
        val subject = requireNotNull(text("sub"))
        val session = requireNotNull(text("session_id"))
        val uuid = Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
        require(subject.matches(uuid) && session.matches(uuid))
        return RecoveryClaims(subject, session, expires)
    }

    /** Local cleanup only, never authorization: must work even when our imported session expired. */
    fun sessionIdForCleanup(token: String): String? = runCatching {
        Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8))
            .jsonObject["session_id"]?.jsonPrimitive?.content
    }.getOrNull()
}

class RecoveryCredentials(val accessToken: String, val refreshToken: String, val claims: RecoveryClaims) {
    override fun toString() = "RecoveryCredentials([REDACTED])"
}
data class RecoveryClaims(val userId: String, val sessionId: String, val expiresAtSeconds: Long)

/** In-memory, per-repository capability; never restored from an ordinary login or persisted. */
class PasswordRecoveryGrant(private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 }) {
    private var claims: RecoveryClaims? = null
    private var deadline = 0L
    private var generation = 0L
    fun clear() { claims = null; deadline = 0; generation++ }
    fun authorize(value: RecoveryClaims) {
        generation++
        claims = value
        deadline = minOf(value.expiresAtSeconds, nowSeconds() + 600)
    }
    fun version(): Long? = generation.takeIf { claims != null && nowSeconds() < deadline }
    fun requireAuthorized(userId: String?, sessionId: String?, expectedVersion: Long? = generation) {
        val grant = claims
        check(grant != null && expectedVersion == generation && userId == grant.userId && sessionId == grant.sessionId && nowSeconds() < deadline) {
            "재설정 인증이 만료되었습니다. 링크를 다시 요청해 주세요."
        }
    }
}
