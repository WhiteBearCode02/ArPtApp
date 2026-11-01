package com.example.arptapp.data.history

import com.example.arptapp.data.RepAnalysisCodec
import com.example.arptapp.data.StructuredReportCodec
import com.example.arptapp.model.RepRecord
import kotlinx.serialization.json.*

/** Explicit scalar allowlist. Never serialize a Room entity or navigation object over the network. */
object WorkoutPayloadCodec {
    const val MAX_JSON_LENGTH = 512_000
    const val MAX_REPS = 1000
    fun analyses(raw: String): String {
        require(raw.length <= MAX_JSON_LENGTH)
        val root = Json.parseToJsonElement(raw)
        val analyses = RepAnalysisCodec.decode(raw)
        require(analyses.size <= MAX_REPS)
        return if (root is JsonObject) {
            val structured = requireNotNull(StructuredReportCodec.decode(raw))
            require(structured.repAnalyses.size <= MAX_REPS)
            StructuredReportCodec.encode(analyses, structured)
        } else {
            require(root is JsonArray)
            RepAnalysisCodec.encode(analyses)
        }
    }
    fun reps(records: List<RepRecord>): String {
        require(records.size <= MAX_REPS && records.map { it.repNumber }.distinct().size == records.size)
        return buildJsonArray {
            records.forEach { rep ->
                require(rep.repNumber > 0 && rep.maxAngle.isFinite() && rep.swayX.isFinite())
                require(rep.poseScore == null || rep.poseScore.isFinite())
                add(buildJsonObject {
                    put("rep", rep.repNumber); put("maxAngle", rep.maxAngle); put("swayX", rep.swayX)
                    put("errorTags", buildJsonArray { rep.errorTags.forEach { add(it) } })
                    put("eccentricDurationMs", rep.eccentricDurationMs); put("concentricDurationMs", rep.concentricDurationMs)
                    put("poseScore", rep.poseScore?.let(::JsonPrimitive) ?: JsonNull)
                })
            }
        }.toString().also { require(it.length <= MAX_JSON_LENGTH) }
    }
    fun decodeReps(raw: String): List<RepRecord> {
        require(raw.length <= MAX_JSON_LENGTH)
        val array = Json.parseToJsonElement(raw).jsonArray
        require(array.size <= MAX_REPS)
        return array.map { element ->
            val row = element.jsonObject
            RepRecord(row.getValue("rep").jsonPrimitive.int, row.getValue("maxAngle").jsonPrimitive.double,
                row.getValue("swayX").jsonPrimitive.float,
                row.getValue("errorTags").jsonArray.map { it.jsonPrimitive.content },
                row.getValue("eccentricDurationMs").jsonPrimitive.long,
                row.getValue("concentricDurationMs").jsonPrimitive.long,
                row["poseScore"]?.jsonPrimitive?.floatOrNull)
        }.also { reps(it) }
    }
}
