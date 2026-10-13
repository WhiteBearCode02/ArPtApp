package com.example.arptapp.data.remote

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthSessionStoreTest {
    @Before fun resetBefore() { AuthSessionStore.current = null }
    @After fun resetAfter() { AuthSessionStore.current = null }

    @Test fun repositoriesShareOneSessionWithoutInitializingNetworkClient() {
        val first = SupabaseAuthRepository()
        val second = SupabaseAuthRepository()
        val session = AppSession("test-user", "test@example.invalid", false, true)
        AuthSessionStore.current = session
        assertSame(first.session, second.session)
        assertEquals(session, first.session.value)
        assertEquals(session, AuthSessionStore.current)
        AuthSessionStore.current = null
        assertNull(second.session.value)
    }

    @Test fun switchingAccountDoesNotLeaveAnOldRepositorySnapshot() {
        val repository = SupabaseAuthRepository()
        AuthSessionStore.current = AppSession("a", "a@example.invalid", false, true)
        AuthSessionStore.current = AppSession("b", "b@example.invalid", false, true)
        assertEquals("b", repository.session.value?.userId)
    }
}
