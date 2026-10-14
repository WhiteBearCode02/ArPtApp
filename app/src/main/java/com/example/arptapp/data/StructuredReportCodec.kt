package com.example.arptapp.data

import com.example.arptapp.domain.report.StructuredWorkoutAnalysis
import com.example.arptapp.model.RepAnalysis
import kotlinx.serialization.json.*

/** Additive envelope inside scoresJson. No Room schema/version change or destructive migration. */
object StructuredReportCodec {
    private val json = Json { encodeDefaults = true; explicitNulls = true }
    fun encode(analyses: List<RepAnalysis>, structured: StructuredWorkoutAnalysis): String = buildJsonObject {
        put("reportSchemaVersion", 1)
        put("analyses", json.parseToJsonElement(RepAnalysisCodec.encode(analyses)))
        put("structured", json.encodeToJsonElement(StructuredWorkoutAnalysis.serializer(), structured))
    }.toString()

    fun decode(raw: String): StructuredWorkoutAnalysis? = runCatching {
        if (raw.length > 512_000) return null
        val root = json.parseToJsonElement(raw).jsonObject
        if (root["reportSchemaVersion"]?.jsonPrimitive?.intOrNull != 1) return null
        json.decodeFromJsonElement(StructuredWorkoutAnalysis.serializer(), root.getValue("structured"))
            .takeIf { it.schemaVersion == 1 && it.exerciseType in listOf("SQUAT", "SHOULDER_PRESS") &&
                it.repAnalyses.all { rep -> rep.repNumber > 0 && rep.exerciseType == it.exerciseType } }
    }.getOrNull()
}
