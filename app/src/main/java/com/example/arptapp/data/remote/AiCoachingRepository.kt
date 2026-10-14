package com.example.arptapp.data.remote

import com.example.arptapp.BuildConfig
import com.example.arptapp.core.coroutines.suspendResult
import com.example.arptapp.domain.report.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream

/** Public, non-secret release gate. Do not enable before provider/privacy/cost review and approval. */
object AiCoachingPolicy { const val ENABLED = false }
class AiUnavailableException : Exception()
class AiRequestException(val status: Int) : Exception()

fun interface AiReportTransport { suspend fun request(body: String): String }

/** No provider SDK/dependency; JWT goes only in the Edge Authorization header, never in JSON. */
class AiCoachingRepository(
    private val enabled: Boolean = AiCoachingPolicy.ENABLED,
    private val transport: AiReportTransport = SupabaseAiReportTransport(),
    private val timeoutMs: Long = 18_000
) {
    private val json = Json { encodeDefaults = true; explicitNulls = true }
    suspend fun generateWorkoutReport(input: StructuredWorkoutAnalysis): Result<AiWorkoutReport> = suspendResult {
        if (!enabled) throw AiUnavailableException()
        val safeInput = WorkoutAnalysisAggregator.forNetwork(input)
        require(safeInput.repAnalyses.isNotEmpty())
        val body = json.encodeToString(StructuredWorkoutAnalysis.serializer(), safeInput)
        require(body.toByteArray(Charsets.UTF_8).size <= 131_072)
        val raw = withTimeoutOrNull(timeoutMs.coerceIn(1, 18_000)) { transport.request(body) } ?: throw AiRequestException(408)
        AiReportValidator.decode(raw, safeInput)
    }
}

private class SupabaseAiReportTransport : AiReportTransport {
    override suspend fun request(body: String): String = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
        val session = client.auth.currentSessionOrNull() ?: throw AiRequestException(401)
        val account = AuthSessionStore.current?.userId ?: throw AiRequestException(401)
        if (session.user?.id != account) throw AiRequestException(401)
        val url = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/generate-workout-report")
        require(url.protocol == "https" && url.userInfo == null)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 4_000; readTimeout = 12_000
            instanceFollowRedirects = false; doOutput = true
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status == 503) throw AiUnavailableException()
            if (status !in 200..299) throw AiRequestException(status)
            // Hard byte cap even when a server streams an oversized response.
            val bytes = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (output.size() <= 32_768) {
                    val count = stream.read(buffer, 0, minOf(buffer.size, 32_769 - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            require(bytes.size <= 32_768)
            if (AuthSessionStore.current?.userId != account) throw AiRequestException(401)
            bytes.toString(Charsets.UTF_8)
        } finally { connection.disconnect() }
    }
}
