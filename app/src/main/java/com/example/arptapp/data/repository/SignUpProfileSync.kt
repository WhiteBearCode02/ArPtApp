package com.example.arptapp.data.repository

import com.example.arptapp.data.remote.SignUpProfile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.arptapp.core.coroutines.suspendResult

interface PendingSignUpProfileStore {
    suspend fun save(userId: String, profile: SignUpProfile)
    suspend fun get(userId: String): SignUpProfile?
    suspend fun remove(userId: String)
}

/** Only the authenticated UUID can complete its draft. Failures retain it; never use email as an account key. */
class SignUpProfileSync(
    private val pending: PendingSignUpProfileStore,
    private val write: suspend (String, SignUpProfile) -> Result<Unit>
) {
    private val mutex = Mutex()
    suspend fun complete(userId: String): Boolean = suspendResult {
        mutex.withLock {
            val profile = pending.get(userId) ?: return@withLock true
            if (write(userId, profile).isFailure) return@withLock false
            pending.remove(userId)
            true
        }
    }.getOrDefault(false)
}
