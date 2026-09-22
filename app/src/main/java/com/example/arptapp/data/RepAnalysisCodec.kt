package com.example.arptapp.data

import com.example.arptapp.model.RepAnalysis
import org.json.JSONArray
import org.json.JSONObject

/** Reads both current per-rep reports and older score-only arrays. */
object RepAnalysisCodec {
    fun encode(analyses: List<RepAnalysis>): String = JSONArray().apply {
        analyses.forEach { analysis ->
            put(JSONObject().apply {
                put("rep", analysis.repNumber)
                put("score", analysis.score)
                put("detail", analysis.detail)
            })
        }
    }.toString()

    fun decode(raw: String): List<RepAnalysis> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index)
            val score = if (entry == null) {
                array.optDouble(index, Double.NaN)
            } else {
                entry.optDouble("score", Double.NaN)
            }
            if (!score.isFinite() || score !in 0.0..100.0) return@mapNotNull null
            RepAnalysis(
                repNumber = entry?.optInt("rep", index + 1)?.takeIf { it > 0 } ?: index + 1,
                score = score.toFloat(),
                detail = entry?.optString("detail")?.takeIf { it.isNotBlank() }
                    ?: "이전 기록에는 이 회차의 세부 관절 분석이 저장되지 않았습니다."
            )
        }
    }.getOrDefault(emptyList())
}
