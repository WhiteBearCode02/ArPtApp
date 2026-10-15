package com.example.arptapp.core.auth

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class PasswordRecoveryPolicyTest {
    private val now = 1_800_000_000L
    private val issuer = "https://example.supabase.co/auth/v1"
    private val user = "00000000-0000-4000-8000-000000000001"
    private val session = "00000000-0000-4000-8000-000000000002"
    private fun token(method: String = "recovery", expires: Long = now + 3600, issued: Long = now,
                      tokenIssuer: String = issuer, amrTime: Long = now): String {
        val payload = """{"iss":"$tokenIssuer","sub":"$user","session_id":"$session","role":"authenticated","aud":"authenticated","exp":$expires,"iat":$issued,"amr":[{"method":"$method","timestamp":$amrTime}]}"""
        return "e30.${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}.testsignature"
    }
    private fun link(access: String = token()) = "${PasswordRecoveryPolicy.REDIRECT_URI}#type=recovery&token_type=bearer&access_token=$access&refresh_token=test_refresh_token_not_real&expires_in=3600"
    private fun reject(raw: String?) {
        try { PasswordRecoveryPolicy.parse(raw, issuer, now); fail("Invalid link accepted") }
        catch (error: IllegalArgumentException) {
            assertFalse(error.message.orEmpty().contains("test_refresh"))
            assertNull(error.cause)
        }
    }

    @Test fun validRecoveryHasBoundIdentityAndRedactedCredentials() {
        val parsed = PasswordRecoveryPolicy.parse(link(), issuer, now)
        assertEquals(user, parsed.claims.userId)
        assertEquals(session, parsed.claims.sessionId)
        assertEquals("RecoveryCredentials([REDACTED])", parsed.toString())
    }
    @Test fun rejectsBareLinkDespiteAnyExistingLogin() = reject(PasswordRecoveryPolicy.REDIRECT_URI)
    @Test fun rejectsMissingUri() = reject(null)
    @Test fun rejectsWrongSchemeHostPortUserinfoAndEncodedPath() {
        listOf("https://auth/reset-password", "arptapp://evil/reset-password", "arptapp://auth:80/reset-password",
            "arptapp://user@auth/reset-password", "arptapp://auth/%72eset-password", "arptapp://auth/reset-password/",
            "arptapp://auth/callback").forEach { reject(link().replace(PasswordRecoveryPolicy.REDIRECT_URI, it)) }
    }
    @Test fun rejectsQueryCredentialsAndAmbiguousQuery() {
        reject(link().replace('#', '?'))
        reject(link().replace("#", "?code=unexpected#"))
    }
    @Test fun rejectsMissingTokensOrType() {
        reject(link().replace("&refresh_token=test_refresh_token_not_real", ""))
        reject(link().replace("type=recovery&", ""))
        reject(link().replace("type=recovery", "type=signup"))
    }
    @Test fun rejectsDuplicatesAndUnknownParameters() {
        reject(link() + "&type=recovery")
        reject(link() + "&%74ype=recovery")
        reject(link() + "&error=access_denied")
    }
    @Test fun rejectsOrdinaryLoginTokenWithForgedRecoveryType() = reject(link(token(method = "password")))
    @Test fun rejectsExpiredFutureOldAndWrongIssuerTokens() {
        reject(link(token(expires = now)))
        reject(link(token(issued = now + 61)))
        reject(link(token(issued = now - 901)))
        reject(link(token(tokenIssuer = "https://other.supabase.co/auth/v1")))
        reject(link(token(amrTime = now - 901)))
    }
    @Test fun rejectsMalformedTokensAndUnboundedInput() {
        reject(link("not_a_jwt")); reject(link("e30.invalid.test"))
        reject(link() + "x".repeat(16_384))
        reject(link().replace("expires_in=3600", "expires_in=NaN"))
    }
    @Test fun oldSessionDoesNotAuthorizeAndGrantBindsUserAndSession() {
        val grant = PasswordRecoveryGrant { now }
        assertThrows(IllegalStateException::class.java) { grant.requireAuthorized(user, session) }
        grant.authorize(RecoveryClaims(user, session, now + 3600))
        grant.requireAuthorized(user, session)
        assertThrows(IllegalStateException::class.java) { grant.requireAuthorized("other", session) }
        assertThrows(IllegalStateException::class.java) { grant.requireAuthorized(user, "other-session") }
        grant.clear()
        assertThrows(IllegalStateException::class.java) { grant.requireAuthorized(user, session) }
    }
    @Test fun grantExpiresAfterTenMinutesAndDoesNotSurviveNewInstance() {
        var clock = now
        val grant = PasswordRecoveryGrant { clock }
        grant.authorize(RecoveryClaims(user, session, now + 3600))
        clock += 600
        assertThrows(IllegalStateException::class.java) { grant.requireAuthorized(user, session) }
        assertThrows(IllegalStateException::class.java) { PasswordRecoveryGrant { now }.requireAuthorized(user, session) }
    }
    @Test fun newLinkInvalidatesAnAlreadyQueuedPasswordSubmissionEvenForSameAccount() {
        val grant = PasswordRecoveryGrant { now }
        grant.authorize(RecoveryClaims(user, session, now + 3600))
        val oldVersion = grant.version()
        grant.clear()
        grant.authorize(RecoveryClaims(user, session, now + 3600))
        assertThrows(IllegalStateException::class.java) { grant.requireAuthorized(user, session, oldVersion) }
        grant.requireAuthorized(user, session, grant.version())
    }
    @Test fun expiredSessionCanStillBeIdentifiedForLocalCleanupOnly() {
        assertEquals(session, PasswordRecoveryPolicy.sessionIdForCleanup(token(expires = now - 1)))
        assertNull(PasswordRecoveryPolicy.sessionIdForCleanup("malformed"))
    }
}
