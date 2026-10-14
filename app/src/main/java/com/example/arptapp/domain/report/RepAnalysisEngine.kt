package com.example.arptapp.domain.report

import com.example.arptapp.domain.analyzer.CameraView
import kotlin.math.abs

/**
 * Companion to ExerciseCounter, not a replacement counter. At most 300 scalar frames/20s.
 * IMAGE_2D interior angles; 15 degree bilateral difference / 20 degree lean are unvalidated
 * ENGINEERING_INITIAL observation flags, not medical or safety thresholds.
 */
class RepAnalysisEngine {
    private val frames = ArrayDeque<RepFrameFeatures>()
    private var partial = false
    fun reset() { frames.clear(); partial = false }
    fun observe(frame: RepFrameFeatures) {
        if (frame.representativeAngle?.let { it.isFinite() && it in 0.0..180.0 } != true) { reset(); return }
        val last = frames.lastOrNull()
        if (last != null && (frame.exerciseType != last.exerciseType || frame.view != last.view ||
                frame.timestampMs <= last.timestampMs || frame.timestampMs - last.timestampMs > 1000)) reset()
        frames.addLast(frame)
        while (frames.size > 300 || frames.last().timestampMs - frames.first().timestampMs > 20_000) {
            frames.removeFirst(); partial = true
        }
    }

    fun complete(repNumber: Int, score: Float?): StructuredRepAnalysis? {
        val all = frames.toList()
        val wasPartial = partial
        reset()
        if (all.size < 3 || all.first().exerciseType !in listOf("SQUAT", "SHOULDER_PRESS")) return null
        // Exclude standing/rest time before the first measurable motion. First partial repetition
        // may start at the bottom: its missing descent remains null.
        val firstMotion = (1 until all.size).firstOrNull {
            abs(all[it].representativeAngle!! - all[0].representativeAngle!!) >= 3.0
        } ?: return null
        val samples = all.drop((firstMotion - 1).coerceAtLeast(0))
        val start = samples.first().timestampMs
        val min = samples.minOf { it.representativeAngle!! }
        val bottomIndices = samples.indices.filter { samples[it].representativeAngle!! <= min + 5.0 }
        val bottomStart = bottomIndices.first()
        val bottomEnd = bottomIndices.last()
        val durations = PhaseDurations(
            descentMs = if (!wasPartial && bottomStart > 0 && samples.first().representativeAngle!! - min >= 10)
                samples[bottomStart].timestampMs - start else null,
            bottomMs = if (bottomEnd > bottomStart) samples[bottomEnd].timestampMs - samples[bottomStart].timestampMs else null,
            ascentMs = if (bottomEnd < samples.lastIndex && samples.last().representativeAngle!! - min >= 10)
                samples.last().timestampMs - samples[bottomEnd].timestampMs else null
        )
        fun phase(index: Int): MovementPhase = when {
            index < bottomStart -> MovementPhase.DESCENT
            index <= bottomEnd -> MovementPhase.BOTTOM
            else -> MovementPhase.ASCENT
        }
        fun valid(v: Double?) = v?.takeIf { it.isFinite() && it in 0.0..180.0 }
        fun minimum(get: (RepFrameFeatures) -> Double?) = samples.mapNotNull { valid(get(it)) }.minOrNull()
        fun maximum(get: (RepFrameFeatures) -> Double?) = samples.mapNotNull { valid(get(it)) }.maxOrNull()
        fun difference(a: (RepFrameFeatures) -> Double?, b: (RepFrameFeatures) -> Double?) =
            samples.mapNotNull { f -> valid(a(f))?.let { x -> valid(b(f))?.let { y -> abs(x - y) } } }.maxOrNull()
        val squat = samples.first().exerciseType == "SQUAT"
        val front = samples.first().view == CameraView.FRONT
        val side = samples.first().view == CameraView.SIDE
        val joints = JointMetrics(
            leftKneeMinAngle = if (squat) minimum { it.leftKnee } else null,
            rightKneeMinAngle = if (squat) minimum { it.rightKnee } else null,
            leftHipMinAngle = if (squat && side) minimum { it.leftHip } else null,
            rightHipMinAngle = if (squat && side) minimum { it.rightHip } else null,
            leftElbowMaxAngle = if (!squat) maximum { it.leftElbow } else null,
            rightElbowMaxAngle = if (!squat) maximum { it.rightElbow } else null,
            torsoLeanMaxDeg = if (squat && side) maximum { it.torsoLean } else null
        )
        val asymmetry = AsymmetryMetrics(
            kneeAngleDifferenceDeg = if (squat && front) difference({ it.leftKnee }, { it.rightKnee }) else null,
            // Hip symmetry in frontal projection is not a reliable flexion measurement.
            elbowAngleDifferenceDeg = if (!squat && front) difference({ it.leftElbow }, { it.rightElbow }) else null
        )
        val events = mutableListOf<FormErrorEvent>()
        fun event(type: FormErrorType, severity: Int, predicate: (RepFrameFeatures) -> Boolean) {
            val indices = samples.indices.filter { predicate(samples[it]) }
            // Suppress one/two frame jitter. Bounded summary, not an event for every frame.
            if (indices.size >= 3) events += FormErrorEvent(type, phase(indices.first()), severity = severity,
                timestampMs = samples[indices.first()].timestampMs - start, occurrenceFrames = indices.size)
        }
        if (squat && front) {
            event(FormErrorType.KNEE_VALGUS, 2) { it.kneeValgusObserved && valid(it.leftKnee) != null && valid(it.rightKnee) != null }
            event(FormErrorType.KNEE_ASYMMETRY, 1) { f ->
                valid(f.leftKnee)?.let { l -> valid(f.rightKnee)?.let { abs(l - it) > 15 } } == true
            }
        }
        if (squat && side) event(FormErrorType.FORWARD_LEAN, 1) { (valid(it.torsoLean) ?: 0.0) > 20 }
        if (!squat && front) event(FormErrorType.ELBOW_ASYMMETRY, 1) { f ->
            valid(f.leftElbow)?.let { l -> valid(f.rightElbow)?.let { abs(l - it) > 15 } } == true
        }
        // Depth / extension and HIP_ASYMMETRY are intentionally NOT_EVALUATED: the current
        // counter accepts only complete reps, and no validated individualized target exists.
        val strengths = buildList {
            if (asymmetry.kneeAngleDifferenceDeg?.let { it <= 15 } == true &&
                samples.count { valid(it.leftKnee) != null && valid(it.rightKnee) != null } >= 3) add("KNEE_SYMMETRY_OBSERVED")
            if (asymmetry.elbowAngleDifferenceDeg?.let { it <= 15 } == true &&
                samples.count { valid(it.leftElbow) != null && valid(it.rightElbow) != null } >= 3) add("ELBOW_SYMMETRY_OBSERVED")
            if (durations.descentMs != null && durations.ascentMs != null) add("BOTH_PHASES_CAPTURED")
        }
        val priority = events.sortedWith(compareByDescending<FormErrorEvent> { it.severity }
            .thenByDescending { it.occurrenceFrames }.thenBy { it.type.ordinal }).firstOrNull()
        return StructuredRepAnalysis(repNumber, samples.first().exerciseType, samples.first().view.name,
            if (wasPartial) null else samples.last().timestampMs - start, durations, joints, asymmetry,
            events, strengths, events.map { it.type.name }, LocalRepFeedback.forIssue(priority?.type),
            score?.takeIf { it.isFinite() && it in 0f..100f }, samples.size, wasPartial)
    }
}

object LocalRepFeedback {
    fun forIssue(type: FormErrorType?): String = when (type) {
        FormErrorType.KNEE_VALGUS -> "다음 회차에서는 무릎 방향을 발 방향에 맞춰 보세요."
        FormErrorType.KNEE_ASYMMETRY -> "다음 회차에서는 양쪽 무릎의 움직임을 함께 확인해 보세요."
        FormErrorType.ELBOW_ASYMMETRY -> "다음 회차에서는 양팔이 함께 올라가도록 천천히 움직여 보세요."
        FormErrorType.FORWARD_LEAN -> "다음 회차에서는 몸통 기울기를 확인하며 편안하게 움직여 보세요."
        else -> "회차가 기록되었습니다. 편안한 범위에서 천천히 반복해 보세요."
    }
    fun strength(key: String): String = when (key) {
        "KNEE_SYMMETRY_OBSERVED" -> "관찰 구간의 좌우 무릎 각도 차이가 초기 기준 이내였습니다."
        "ELBOW_SYMMETRY_OBSERVED" -> "관찰 구간의 좌우 팔꿈치 각도 차이가 초기 기준 이내였습니다."
        "BOTH_PHASES_CAPTURED" -> "내려가는 구간과 올라가는 구간이 모두 측정되었습니다."
        else -> "측정 가능한 근거가 부족합니다."
    }
}

/** Aggregate all local reps; only the last 50 summaries may cross the optional LLM boundary. */
object WorkoutAnalysisAggregator {
    fun aggregate(exerciseType: String, reps: List<StructuredRepAnalysis>, totalReps: Int = reps.size): StructuredWorkoutAnalysis {
        val scored = reps.filter { it.score != null }
        return StructuredWorkoutAnalysis(exerciseType = exerciseType, totalReps = totalReps,
            averageScore = scored.takeIf { it.isNotEmpty() }?.map { it.score!!.toDouble() }?.average()?.toFloat(),
            bestRepNumber = scored.maxByOrNull { it.score!! }?.repNumber,
            weakestRepNumber = scored.minByOrNull { it.score!! }?.repNumber,
            dominantIssues = reps.flatMap { it.errorEvents }.groupBy { it.type }.entries
                .sortedByDescending { it.value.size }.map { it.key }, repAnalyses = reps)
    }
    fun forNetwork(input: StructuredWorkoutAnalysis) = input.copy(
        repAnalyses = input.repAnalyses.takeLast(50), omittedReps = (input.totalReps - input.repAnalyses.takeLast(50).size).coerceAtLeast(0))
}
