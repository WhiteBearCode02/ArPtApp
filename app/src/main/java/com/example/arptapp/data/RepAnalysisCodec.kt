package com.example.arptapp.data

import com.example.arptapp.model.RepAnalysis
import kotlinx.serialization.json.*

/** Current envelope/object arrays and legacy score arrays. Pure JVM API, no Android JSON stub. */
object RepAnalysisCodec {
    fun encode(analyses: List<RepAnalysis>): String = buildJsonArray {
        analyses.forEach { analysis -> add(buildJsonObject {
            put("rep", analysis.repNumber); put("score", analysis.score); put("detail", analysis.detail)
        }) }
    }.toString()

    fun decode(raw: String): List<RepAnalysis> = runCatching {
        require(raw.length <= 512_000)
        val root = Json.parseToJsonElement(raw)
        val array = if (root is JsonObject) root.getValue("analyses").jsonArray else root.jsonArray
        array.mapIndexedNotNull { index, element ->
            val entry = element as? JsonObject
            val score = ((entry?.get("score") ?: element) as? JsonPrimitive)?.doubleOrNull
                ?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return@mapIndexedNotNull null
            RepAnalysis(
                repNumber = (entry?.get("rep") as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 } ?: index + 1,
                score = score.toFloat(),
                detail = (entry?.get("detail") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: "이전 기록에는 이 회차의 세부 관절 분석이 저장되지 않았습니다."
            )
        }
    }.getOrDefault(emptyList())
}
