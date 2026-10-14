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
                frame.timestampMs <= last.timestampMs || frame.timestampMs - last.timestampMs > 1000)) {
            val lostPrefix = frame.exerciseType == last.exerciseType && frame.view == last.view
            reset()
            partial = lostPrefix
        }
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
        // Keep a 3-frame preparation pre-roll so a press starting at bottom can be observed,
        // without importing an arbitrary long stationary period into its duration.
        val samples = all.drop((firstMotion - EngineeringFormPolicy.MIN_EVENT_FRAMES).coerceAtLeast(0))
        val start = samples.first().timestampMs
        val segmentation = RepPhaseSegmenter.segment(samples, wasPartial)
        val durations = segmentation.durations
        fun valid(v: Double?) = v?.takeIf { it.isFinite() && it in 0.0..180.0 }
        fun minimum(get: (RepFrameFeatures) -> Double?) = samples.mapNotNull { valid(get(it)) }.minOrNull()
        fun maximum(get: (RepFrameFeatures) -> Double?) = samples.mapNotNull { valid(get(it)) }.maxOrNull()
        fun difference(a: (RepFrameFeatures) -> Double?, b: (RepFrameFeatures) -> Double?) =
            samples.mapNotNull { f -> valid(a(f))?.let { x -> valid(b(f))?.let { y -> abs(x - y) } } }.maxOrNull()
        val squat = samples.first().exerciseType == "SQUAT"
        val front = samples.first().view == CameraView.FRONT
        val side = samples.first().view == CameraView.SIDE
        val selectedSide = if (!side) null else {
            val leftCount = samples.count { valid(if (squat) it.leftKnee else it.leftElbow) != null }
            val rightCount = samples.count { valid(if (squat) it.rightKnee else it.rightElbow) != null }
            if (leftCount >= rightCount) BodySide.LEFT else BodySide.RIGHT
        }
        val left = front || (side && selectedSide == BodySide.LEFT)
        val right = front || (side && selectedSide == BodySide.RIGHT)
        fun lean(f: RepFrameFeatures) = f.torsoLean.takeIf { f.visibleSide == null || f.visibleSide == selectedSide }
        val joints = JointMetrics(
            leftKneeMinAngle = if (squat && left) minimum { it.leftKnee } else null,
            rightKneeMinAngle = if (squat && right) minimum { it.rightKnee } else null,
            leftHipMinAngle = if (squat && side && left) minimum { it.leftHip } else null,
            rightHipMinAngle = if (squat && side && right) minimum { it.rightHip } else null,
            leftElbowMaxAngle = if (!squat && left) maximum { it.leftElbow } else null,
            rightElbowMaxAngle = if (!squat && right) maximum { it.rightElbow } else null,
            torsoLeanMaxDeg = if (squat && side) maximum(::lean) else null
        )
        val asymmetry = AsymmetryMetrics(
            kneeAngleDifferenceDeg = if (squat && front) difference({ it.leftKnee }, { it.rightKnee }) else null,
            // Hip symmetry in frontal projection is not a reliable flexion measurement.
            elbowAngleDifferenceDeg = if (!squat && front) difference({ it.leftElbow }, { it.rightElbow }) else null
        )
        val events = mutableListOf<FormErrorEvent>()
        fun event(type: FormErrorType, bodySide: BodySide? = null, severity: (RepFrameFeatures) -> Int) {
            events += FormEventAggregator.collect(samples, segmentation.phases, type, bodySide, severity)
        }
        if (squat && front) {
            event(FormErrorType.KNEE_VALGUS, BodySide.LEFT) {
                if (it.leftKneeValgusObserved && valid(it.leftKnee) != null) EngineeringFormPolicy.valgusSeverity(it.leftKneeValgusRatio) else 0
            }
            event(FormErrorType.KNEE_VALGUS, BodySide.RIGHT) {
                if (it.rightKneeValgusObserved && valid(it.rightKnee) != null) EngineeringFormPolicy.valgusSeverity(it.rightKneeValgusRatio) else 0
            }
            // Old scalar callers have no side evidence; never guess LEFT/RIGHT from one boolean.
            if (samples.none { it.leftKneeValgusObserved || it.rightKneeValgusObserved }) event(FormErrorType.KNEE_VALGUS) {
                if (it.kneeValgusObserved && valid(it.leftKnee) != null && valid(it.rightKnee) != null) 2 else 0
            }
            event(FormErrorType.KNEE_ASYMMETRY) { f ->
                valid(f.leftKnee)?.let { l -> valid(f.rightKnee)?.let { EngineeringFormPolicy.asymmetrySeverity(abs(l - it)) } } ?: 0
            }
        }
        if (squat && side) event(FormErrorType.FORWARD_LEAN) { EngineeringFormPolicy.leanSeverity(valid(lean(it)) ?: 0.0) }
        if (!squat && front) event(FormErrorType.ELBOW_ASYMMETRY) { f ->
            valid(f.leftElbow)?.let { l -> valid(f.rightElbow)?.let { EngineeringFormPolicy.asymmetrySeverity(abs(l - it)) } } ?: 0
        }
        // Depth / extension and HIP_ASYMMETRY are intentionally NOT_EVALUATED: the current
        // counter accepts only complete reps, and no validated individualized target exists.
        val strengths = buildList {
            if (asymmetry.kneeAngleDifferenceDeg?.let { it <= EngineeringFormPolicy.ASYMMETRY_DEG } == true &&
                samples.count { valid(it.leftKnee) != null && valid(it.rightKnee) != null } >= 3) add("KNEE_SYMMETRY_OBSERVED")
            if (asymmetry.elbowAngleDifferenceDeg?.let { it <= EngineeringFormPolicy.ASYMMETRY_DEG } == true &&
                samples.count { valid(it.leftElbow) != null && valid(it.rightElbow) != null } >= 3) add("ELBOW_SYMMETRY_OBSERVED")
            if (durations.descentMs != null && durations.ascentMs != null) add("BOTH_PHASES_CAPTURED")
        }
        val priority = events.sortedWith(compareByDescending<FormErrorEvent> { it.severity }
            .thenByDescending { it.occurrenceFrames }.thenBy { it.type.ordinal }).firstOrNull()
        return StructuredRepAnalysis(repNumber, samples.first().exerciseType, samples.first().view.name,
            if (wasPartial) null else samples.last().timestampMs - start, durations, joints, asymmetry,
            events.sortedBy { it.timestampMs }, strengths, events.map { it.type.name }.distinct(), LocalRepFeedback.forEvent(priority),
            score?.takeIf { it.isFinite() && it in 0f..100f }, samples.size, wasPartial, phaseModelVersion = 2)
    }
}

object LocalRepFeedback {
    fun forEvent(event: FormErrorEvent?): String = event?.let(LocalEventPresentation::feedback) ?: forIssue(null)
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
