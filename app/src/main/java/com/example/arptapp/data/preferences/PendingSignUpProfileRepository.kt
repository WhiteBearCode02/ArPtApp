package com.example.arptapp.data.preferences

import android.content.Context
import android.util.AtomicFile
import com.example.arptapp.data.remote.SignUpProfile
import com.example.arptapp.data.repository.PendingSignUpProfileStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Private, no-backup draft for email-confirmation signup. No email/password/tokens. TTL is 24 hours. */
class PendingSignUpProfileRepository(context: Context) : PendingSignUpProfileStore {
    private val directory = File(context.applicationContext.noBackupFilesDir, "pending_signup_profiles")
    private fun file(userId: String): AtomicFile {
        require(userId.matches(Regex("[0-9a-fA-F-]{36}"))) { "유효하지 않은 사용자 식별자입니다." }
        return AtomicFile(File(directory, "$userId.json"))
    }
    override suspend fun save(userId: String, profile: SignUpProfile) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        // Purge expired drafts on access, not with a background worker/cloud job.
        purgeExpired()
        val target = file(userId)
        val stream = target.startWrite()
        try {
            stream.write(Json.encodeToString(Draft(System.currentTimeMillis(), profile)).toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
        } catch (error: Exception) { target.failWrite(stream); throw error }
    }
    override suspend fun get(userId: String): SignUpProfile? = withContext(Dispatchers.IO) {
        purgeExpired()
        read(file(userId).baseFile)?.profile
    }
    override suspend fun remove(userId: String) = withContext(Dispatchers.IO) { file(userId).delete() }

    private fun read(target: File): Draft? = runCatching {
        require(target.length() in 1..8192)
        val draft = Json.decodeFromString<Draft>(AtomicFile(target).openRead().bufferedReader().use { it.readText() })
        require(System.currentTimeMillis() - draft.createdAt in 0..86_400_000L)
        draft
    }.getOrNull()
    private fun purgeExpired() {
        directory.listFiles()?.filter { it.extension == "json" }?.forEach {
            if (read(it) == null) AtomicFile(it).delete()
        }
    }
    @Serializable private data class Draft(val createdAt: Long, val profile: SignUpProfile)
}
