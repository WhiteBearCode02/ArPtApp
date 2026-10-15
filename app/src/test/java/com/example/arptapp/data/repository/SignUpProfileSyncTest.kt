package com.example.arptapp.data.repository

import com.example.arptapp.data.remote.SignUpProfile
import com.example.arptapp.data.remote.signUpAuthMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SignUpProfileSyncTest {
    private val profile = SignUpProfile("test", 60.0, 25.0, 12.0, 20.0)
    private class Store : PendingSignUpProfileStore {
        val values = mutableMapOf<String, SignUpProfile>()
        override suspend fun save(userId: String, profile: SignUpProfile) { values[userId] = profile }
        override suspend fun get(userId: String) = values[userId]
        override suspend fun remove(userId: String) { values.remove(userId) }
    }
    @Test fun signupAuthMetadataContainsNoBodyValues() {
        assertEquals(setOf("name"), signUpAuthMetadata(profile).keys)
        assertFalse(signUpAuthMetadata(profile).toString().contains("60.0"))
    }
    @Test fun confirmationDefersToAuthenticatedAccountAndDoesNotUseOtherDraft() = runBlocking {
        val store = Store().apply { save("a", profile) }
        val calls = mutableListOf<String>()
        val sync = SignUpProfileSync(store) { id, value ->
            calls += id; assertEquals(profile, value); Result.success(Unit)
        }
        assertTrue(sync.complete("b")); assertTrue(calls.isEmpty())
        assertTrue(sync.complete("a")); assertEquals(listOf("a"), calls)
        assertNull(store.get("a")); assertTrue(sync.complete("a")); assertEquals(1, calls.size)
    }
    @Test fun offlineFailureRetainsDraftWithoutBlockingLogin() = runBlocking {
        val store = Store().apply { save("a", profile) }
        assertFalse(SignUpProfileSync(store) { _, _ -> Result.failure(IllegalStateException()) }.complete("a"))
        assertEquals(profile, store.get("a"))
    }
    @Test fun cancellationRetainsDraftAndEscapes() = runBlocking {
        val store = Store().apply { save("a", profile) }
        try { SignUpProfileSync(store) { _, _ -> throw CancellationException() }.complete("a"); fail("Cancellation lost") }
        catch (_: CancellationException) { assertEquals(profile, store.get("a")) }
    }
}
