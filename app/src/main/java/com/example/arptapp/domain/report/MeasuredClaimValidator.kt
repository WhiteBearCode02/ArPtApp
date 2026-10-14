package com.example.arptapp.domain.report

import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.math.round

/** Conservative numeric-prose guard, not proof of semantic truthfulness. */
object MeasuredClaimValidator {
    private val claim = Regex("(-?\\d+(?:\\.\\d+)?)\\s*(°|도|점|ms|밀리초|초|회)?", RegexOption.IGNORE_CASE)
    fun validate(texts: List<String>, reps: List<StructuredRepAnalysis>, references: Set<String>? = null,
                 totals: List<Double> = emptyList(), averageScore: Float? = null) {
        val values = mutableMapOf<String, MutableList<Double>>()
        fun add(unit: String, value: Double) { values.getOrPut(unit) { mutableListOf() }.add(value) }
        totals.forEach { add("회", it) }
        averageScore?.let { add("점", it.toDouble()) }
        reps.forEach { rep ->
            add("회", rep.repNumber.toDouble())
            val root = Json.encodeToJsonElement(StructuredRepAnalysis.serializer(), rep).jsonObject
            val keys = references ?: AiReportValidator.evidenceKeys(rep)
            for (key in keys) {
                val path = key.split('.')
                val element = if (path.size == 2) (root[path[0]] as? JsonObject)?.get(path[1]) else root[key]
                val value = (element as? JsonPrimitive)?.doubleOrNull ?: continue
                when {
                    key.startsWith("jointMetrics.") || key.startsWith("asymmetryMetrics.") -> add("°", value)
                    key.startsWith("phaseDurations.") || key == "totalDurationMs" -> { add("ms", value); add("초", value / 1000) }
                    key == "score" -> add("점", value)
                }
            }
        }
        val any = values.values.flatten()
        texts.forEach { text -> claim.findAll(text).forEach { match ->
            val number = match.groupValues[1].toDouble()
            val unit = when (match.groupValues[2].lowercase()) { "도" -> "°"; "밀리초" -> "ms"; else -> match.groupValues[2].lowercase() }
            val allowed = if (unit.isBlank()) any else values[unit].orEmpty()
            require(allowed.any { abs(number - it) < 0.00001 || abs(number - round(it)) < 0.00001 ||
                abs(number - round(it * 10) / 10) < 0.00001 }) { "Unobserved numeric claim" }
        } }
    }
}
