package com.example.arptapp.data

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.detection.DetectorStatus
import com.example.arptapp.domain.detection.ExportContractVerification
import com.example.arptapp.domain.detection.YoloModelPolicy
import com.example.arptapp.domain.detection.YoloSessionDiagnostics
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * Fail-closed local persistence boundary, deliberately unrelated to report-upload/LLM DTOs.
 * Rejects unknown properties, arbitrary strings/count keys, nonfinite numbers and inconsistent totals.
 * Legacy/null/malformed diagnostics return null and never prevent loading the exercise record.
 */
object YoloSessionDiagnosticsCodec {
    const val MAX_JSON_CHARS = 16_384
    private val json = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false }
    private val exercises = setOf("SQUAT", "SHOULDER_PRESS", "UNKNOWN")

    fun encode(value: YoloSessionDiagnostics?): String? = value?.let { sanitize(it) }?.let {
        runCatching { json.encodeToString(it).takeIf { raw -> raw.length <= MAX_JSON_CHARS } }.getOrNull()
    }

    fun decode(raw: String?): YoloSessionDiagnostics? {
        if (raw.isNullOrBlank() || raw.length > MAX_JSON_CHARS) return null
        return runCatching { sanitize(json.decodeFromString<YoloSessionDiagnostics>(raw)) }.getOrNull()
    }

    private fun sanitize(value: YoloSessionDiagnostics): YoloSessionDiagnostics? = value.run {
        if (schemaVersion != 1 || modelId != YoloModelPolicy.MODEL_ID || modelSha256 != YoloModelPolicy.CANONICAL_SHA256 ||
            modelStatus !in DetectorStatus.entries.map { it.name } || contractStatus !in ExportContractVerification.entries.map { it.name } ||
            cameraView !in CameraView.entries.map { it.name } || expectedSessionExercise !in exercises ||
            predictionEnabled && (modelStatus != DetectorStatus.READY.name || contractStatus != ExportContractVerification.VERIFIED.name)) return null
        // Historical documents omit the gate. New documents must not contradict the independent policy.
        if (shadowRuntimeEnabled != null && predictionEnabled != (shadowRuntimeEnabled &&
                modelStatus == DetectorStatus.READY.name && contractStatus == ExportContractVerification.VERIFIED.name)) return null
        if (shadowRuntimeEnabled == false && processedFrames > 0L) return null
        val counts = listOf(totalFramesSeen, admittedFrames, processedFrames, activeDurationMs, confidenceSampleCount,
            matchingStableFrames, mismatchingStableFrames, unknownStableFrames, unknownSessionFrames)
        if (counts.any { it < 0 } || admittedFrames > totalFramesSeen || processedFrames > admittedFrames ||
            confidenceSampleCount > processedFrames || startedAt < 0 || endedAt < startedAt ||
            maxThermalStatus?.let { it !in 0..6 } == true) return null
        fun normalize(source: Map<String, Long>): Map<String, Long>? {
            if (source.keys.any { it !in exercises } || source.values.any { it < 0 || it > processedFrames }) return null
            val normalized = exercises.associateWith { source[it] ?: 0L }
            // Subtract from the budget rather than summing attacker-supplied longs (overflow-safe).
            var remaining = processedFrames
            normalized.values.forEach { if (it > remaining) return null else remaining -= it }
            return normalized.takeIf { remaining == 0L }
        }
        val raw = normalize(rawDetectionCounts) ?: return null
        val stable = normalize(stableDetectionCounts) ?: return null
        var comparisonRemaining = processedFrames
        listOf(matchingStableFrames, mismatchingStableFrames, unknownStableFrames, unknownSessionFrames).forEach {
            if (it > comparisonRemaining) return null else comparisonRemaining -= it
        }
        if (comparisonRemaining != 0L) return null
        val timingValues = listOf(averagePreprocessMs, averageInferenceMs, averagePostprocessMs, averageTotalMs)
        if (processedFrames == 0L && timingValues.any { it != null } ||
            processedFrames > 0 && timingValues.any { it == null || !it.isFinite() || it < 0 }) return null
        val expectedFps = if (processedFrames > 0 && activeDurationMs > 0) processedFrames.toDouble() * 1000 / activeDurationMs else null
        if (expectedFps == null && averageProcessedFps != null || expectedFps != null &&
            (averageProcessedFps == null || !averageProcessedFps.isFinite() || abs(averageProcessedFps - expectedFps) > 1e-9 * maxOf(1.0, expectedFps))) return null
        val confidenceValues = listOf(averageConfidence, minConfidence, maxConfidence)
        if (confidenceSampleCount == 0L && confidenceValues.any { it != null } || confidenceSampleCount > 0 &&
            confidenceValues.any { it == null || !it.isFinite() || it !in 0.0..1.0 }) return null
        if (confidenceSampleCount > 0 && (minConfidence!! > averageConfidence!! || averageConfidence > maxConfidence!!)) return null
        copy(rawDetectionCounts = raw, stableDetectionCounts = stable)
    }
}
