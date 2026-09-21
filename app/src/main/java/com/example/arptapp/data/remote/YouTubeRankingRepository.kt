package com.example.arptapp.data.remote

import com.example.arptapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class RankingCategory(val label: String) {
    SQUAT("스쿼트"),
    SHOULDER_PRESS("숄더 프레스")
}

data class RankedVideo(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String?,
    val publishedAt: String?,
    val viewCount: Long?,
    val likeCount: Long?,
    val commentCount: Long?,
    val videoUrl: String
)

data class YouTubeRanking(
    val category: RankingCategory,
    val videos: List<RankedVideo>,
    val fetchedAt: String,
    val cacheStatus: String,
    val message: String?
)

/** The YouTube API key stays in the Supabase Edge Function secret. */
class YouTubeRankingRepository {
    suspend fun load(category: RankingCategory): Result<YouTubeRanking> = withContext(Dispatchers.IO) {
        runCatching {
            require(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()) {
                "Supabase 연결 설정이 없습니다."
            }
            val connection = (URL("${BuildConfig.SUPABASE_URL}/functions/v1/youtube-ranking").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 12_000
                doOutput = true
                setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                connection.outputStream.use { output ->
                    output.write(JSONObject().put("category", category.name).toString().toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val response = runCatching { JSONObject(body) }.getOrElse {
                    error("순위 데이터를 읽을 수 없습니다.")
                }
                check(status in 200..299) {
                    response.optString("message").takeIf { it.isNotBlank() }
                        ?: "순위를 불러오지 못했습니다."
                }
                val rows = response.getJSONArray("videos")
                val videos = buildList {
                    for (index in 0 until rows.length()) {
                        val item = rows.getJSONObject(index)
                        add(RankedVideo(
                            videoId = item.getString("videoId"),
                            title = item.optString("title", "제목 없음"),
                            channelTitle = item.optString("channelTitle"),
                            thumbnailUrl = item.optNullableString("thumbnailUrl"),
                            publishedAt = item.optNullableString("publishedAt"),
                            viewCount = item.optNullableString("viewCount")?.toLongOrNull(),
                            likeCount = item.optNullableString("likeCount")?.toLongOrNull(),
                            commentCount = item.optNullableString("commentCount")?.toLongOrNull(),
                            videoUrl = item.getString("videoUrl")
                        ))
                    }
                }
                YouTubeRanking(
                    category = category,
                    videos = videos,
                    fetchedAt = response.optString("fetchedAt"),
                    cacheStatus = response.optString("cacheStatus"),
                    message = response.optNullableString("message")
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
}
