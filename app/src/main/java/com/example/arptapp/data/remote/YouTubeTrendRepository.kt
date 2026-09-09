package com.example.arptapp.data.remote

import android.content.Context
import com.example.arptapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WorkoutTrend(
    val exercise: String,
    val score: Int,
    val sampleSize: Int
)

data class WorkoutTrendSnapshot(
    val trends: List<WorkoutTrend>,
    val updatedAt: String,
    val isCached: Boolean
)

/**
 * Reads only aggregated public-video trends from our Supabase Edge Function.
 * The YouTube API key never ships in the Android application.
 */
class YouTubeTrendRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun load(): Result<WorkoutTrendSnapshot> = withContext(Dispatchers.IO) {
        loadCached()?.let { cached ->
            if (System.currentTimeMillis() - preferences.getLong(CACHE_TIME_KEY, 0L) < CACHE_DURATION_MS) {
                return@withContext Result.success(cached.copy(isCached = true))
            }
        }

        runCatching {
            require(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()) {
                "트렌드 서비스를 설정하지 못했습니다."
            }

            val connection = (URL("${BuildConfig.SUPABASE_URL}/functions/v1/youtube-trends").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
                setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
            }

            try {
                val responseCode = connection.responseCode
                val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                check(responseCode in 200..299) {
                    JSONObject(body).optString("message", "트렌드 데이터를 불러오지 못했습니다.")
                }
                parseSnapshot(body).also(::saveCache)
            } finally {
                connection.disconnect()
            }
        }.recoverCatching { error ->
            loadCached()?.copy(isCached = true) ?: throw error
        }
    }

    private fun parseSnapshot(body: String): WorkoutTrendSnapshot {
        val json = JSONObject(body)
        val trends = json.getJSONArray("trends").toWorkoutTrends()
        require(trends.isNotEmpty()) { "표시할 운동 트렌드가 없습니다." }
        return WorkoutTrendSnapshot(
            trends = trends,
            updatedAt = json.optString("updatedAt", "방금"),
            isCached = false
        )
    }

    private fun JSONArray.toWorkoutTrends(): List<WorkoutTrend> = buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                WorkoutTrend(
                    exercise = item.getString("exercise"),
                    score = item.getInt("score").coerceIn(0, 100),
                    sampleSize = item.optInt("sampleSize", 0)
                )
            )
        }
    }

    private fun saveCache(snapshot: WorkoutTrendSnapshot) {
        val trendArray = JSONArray().apply {
            snapshot.trends.forEach { trend ->
                put(JSONObject().apply {
                    put("exercise", trend.exercise)
                    put("score", trend.score)
                    put("sampleSize", trend.sampleSize)
                })
            }
        }
        preferences.edit()
            .putString(CACHE_VALUE_KEY, JSONObject().apply {
                put("trends", trendArray)
                put("updatedAt", snapshot.updatedAt)
            }.toString())
            .putLong(CACHE_TIME_KEY, System.currentTimeMillis())
            .apply()
    }

    private fun loadCached(): WorkoutTrendSnapshot? = preferences.getString(CACHE_VALUE_KEY, null)
        ?.let { cachedValue -> runCatching { parseSnapshot(cachedValue) }.getOrNull() }

    private companion object {
        const val PREFERENCES_NAME = "youtube_trends"
        const val CACHE_VALUE_KEY = "snapshot"
        const val CACHE_TIME_KEY = "snapshot_time"
        const val CACHE_DURATION_MS = 6 * 60 * 60 * 1_000L
    }
}
