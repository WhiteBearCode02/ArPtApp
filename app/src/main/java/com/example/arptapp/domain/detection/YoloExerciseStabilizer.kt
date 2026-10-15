package com.example.arptapp.domain.detection

import com.example.arptapp.domain.classifier.ExerciseType

/** ENGINEERING_INITIAL window policy, not an EXP02 accuracy claim. Confidence is NOT_VERIFIED. */
data class YoloStabilizationPolicy(
    val minimumConfidence: Float?, val windowSize: Int = 5, val minimumAgreeing: Int = 4,
    val maximumEvidenceAgeMs: Long = 2000
) {
    init {
        require(windowSize > 0 && minimumAgreeing in 1..windowSize && minimumAgreeing > windowSize / 2)
        require(maximumEvidenceAgeMs > 0)
        require(minimumConfidence == null || (minimumConfidence.isFinite() && minimumConfidence in 0f..1f))
    }
}

/** No model semantics are inferred here; input must already be a verified, canonical detection. */
class YoloExerciseStabilizer(private val policy: YoloStabilizationPolicy) {
    private val evidence = ArrayDeque<Pair<Long, ExerciseType>>()
    private var lastTimestamp: Long? = null
    fun observe(detection: ExerciseDetection?, timestampMs: Long): ExerciseType {
        val previous = lastTimestamp
        if (timestampMs < 0 || (previous != null && timestampMs <= previous)) { reset(); return ExerciseType.UNKNOWN }
        lastTimestamp = timestampMs
        while (evidence.isNotEmpty() && timestampMs - evidence.first().first > policy.maximumEvidenceAgeMs) evidence.removeFirst()
        val threshold = policy.minimumConfidence
        val type = detection?.takeIf { d -> threshold != null && d.source == DetectionSource.YOLO &&
            d.exerciseType in YoloModelPolicy.supported && d.confidence?.let { it.isFinite() && it in threshold..1f } == true }
            ?.exerciseType ?: ExerciseType.UNKNOWN
        evidence.addLast(timestampMs to type)
        while (evidence.size > policy.windowSize) evidence.removeFirst()
        return evidence.map { it.second }.filter { it in YoloModelPolicy.supported }
            .groupingBy { it }.eachCount().entries.firstOrNull { it.value >= policy.minimumAgreeing }?.key ?: ExerciseType.UNKNOWN
    }
    fun reset() { evidence.clear(); lastTimestamp = null }
}
