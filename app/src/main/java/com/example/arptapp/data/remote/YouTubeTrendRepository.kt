package com.example.arptapp.data.remote

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
    val remainingRequests: Int
)

/**
 * Reads only aggregated public-video trends from our Supabase Edge Function.
 * The YouTube API key never ships in the Android application.
 */
class YouTubeTrendRepository {
    suspend fun load(): Result<WorkoutTrendSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            require(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()) {
                "트렌드 서비스를 설정하지 못했습니다."
            }

            val connection = (URL("${BuildConfig.SUPABASE_URL}/functions/v1/youtube-trends").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            }

            try {
                val responseCode = connection.responseCode
                val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                check(responseCode in 200..299) {
                    runCatching { JSONObject(body).optString("message") }.getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: "트렌드 데이터를 불러오지 못했습니다."
                }
                parseSnapshot(body)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseSnapshot(body: String): WorkoutTrendSnapshot {
        val json = JSONObject(body)
        val trends = json.getJSONArray("trends").toWorkoutTrends()
        require(trends.isNotEmpty()) { "표시할 운동 트렌드가 없습니다." }
        return WorkoutTrendSnapshot(
            trends = trends,
            updatedAt = json.optString("updatedAt", "방금"),
            remainingRequests = json.optInt("remainingRequests", 0)
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

}
