package com.example.arptapp.domain.report

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.analyzer.ExerciseMotionPolicy as P
import kotlin.math.abs

/** New exercise scalar analysis. Curl buffers are side-isolated, bounded and independently reset.
 * No form observation can reject a counted repetition. Missing values are never replaced by zero.
 */
internal class ExpandedRepAnalysisEngine {
    private data class Buffer(val frames: ArrayDeque<RepFrameFeatures> = ArrayDeque(), var partial: Boolean = false)
    private val buffers = mutableMapOf<BodySide?, Buffer>()
    private val lastRom = mutableMapOf<BodySide, Double>()
    private var type: String? = null
    private var view: CameraView? = null
    val isActive: Boolean get() = type != null
    fun reset() { buffers.clear(); lastRom.clear(); type = null; view = null }
    fun observe(frame: RepFrameFeatures) {
        if (type != frame.exerciseType || view != frame.view) { reset(); type = frame.exerciseType; view = frame.view }
        val sides = if (frame.exerciseType == "BICEP_CURL") listOf(BodySide.LEFT, BodySide.RIGHT) else listOf(null)
        sides.forEach { side ->
            val angle = when (side) { BodySide.LEFT -> frame.leftElbow; BodySide.RIGHT -> frame.rightElbow; null -> frame.representativeAngle }
            val range = if (frame.exerciseType == "SIDE_BEND") -P.SIDE_BEND_MAX_MEASURABLE_DEG..P.SIDE_BEND_MAX_MEASURABLE_DEG else 0.0..180.0
            if (angle == null || !angle.isFinite() || angle !in range) { buffers.remove(side); return@forEach }
            var b = buffers.getOrPut(side) { Buffer() }
            val previous = b.frames.lastOrNull()
            if (previous != null && (frame.timestampMs <= previous.timestampMs || frame.timestampMs - previous.timestampMs > P.MAX_FRAME_GAP_MS)) {
                b = Buffer(partial = true); buffers[side] = b
            }
            b.frames.addLast(frame.copy(representativeAngle = angle))
            while (b.frames.size > P.MAX_REP_FRAMES || b.frames.last().timestampMs - b.frames.first().timestampMs > P.MAX_REP_MS) {
                b.frames.removeFirst(); b.partial = true
            }
        }
    }

    fun complete(number: Int, activeSide: BodySide?, motionStartedAtMs: Long? = null): StructuredRepAnalysis? {
        val key = if (type == "BICEP_CURL") activeSide else null
        val b = buffers.remove(key) ?: return null
        val all = b.frames.toList()
        if (all.size < 3 || all.first().view == CameraView.UNKNOWN) return null
        val motion = motionStartedAtMs?.let { start -> all.indexOfFirst { it.timestampMs >= start }.takeIf { it >= 0 } }
            ?: (1 until all.size).firstOrNull {
            abs(all[it].representativeAngle!! - all.first().representativeAngle!!) >= P.FIRST_MOTION_DEG
        } ?: return null
        val s = all.drop((motion - EngineeringFormPolicy.MIN_EVENT_FRAMES).coerceAtLeast(0))
        val exercise = s.first().exerciseType
        val front = s.first().view == CameraView.FRONT
        val side = s.first().view == CameraView.SIDE
        val selected = if (side) s.mapNotNull { it.visibleSide }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key else null
        fun visible(f: RepFrameFeatures) = !side || f.visibleSide == selected
        val measured = s.filter(::visible)
        fun values(get: (RepFrameFeatures) -> Double?) = measured.mapNotNull { get(it)?.takeIf { v -> v.isFinite() && v in 0.0..180.0 } }
        fun min(get: (RepFrameFeatures) -> Double?) = values(get).minOrNull()
        fun max(get: (RepFrameFeatures) -> Double?) = values(get).maxOrNull()
        fun span(get: (RepFrameFeatures) -> Double?): Double? = values(get).takeIf { it.size >= 3 }?.let { it.max() - it.min() }
        val curl = exercise == "BICEP_CURL"
        val bend = exercise == "SIDE_BEND"
        val left = !bend && (front || selected == BodySide.LEFT) && (!curl || activeSide == BodySide.LEFT)
        val right = !bend && (front || selected == BodySide.RIGHT) && (!curl || activeSide == BodySide.RIGHT)
        val lateral = if (bend && front) s.mapNotNull { it.lateralFlexion?.takeIf { value -> value.isFinite() && abs(value) <= P.SIDE_BEND_MAX_MEASURABLE_DEG } } else emptyList()
        val peak = lateral.maxOfOrNull(::abs)
        val returnDelta = if (lateral.size >= 3) abs(lateral.last() - lateral.first()) else null
        val joints = JointMetrics(
            leftElbowMinAngle = if (left) min { it.leftElbow } else null,
            rightElbowMinAngle = if (right) min { it.rightElbow } else null,
            leftElbowMaxAngle = if (left) max { it.leftElbow } else null,
            rightElbowMaxAngle = if (right) max { it.rightElbow } else null,
            leftElbowRomDeg = if (left) span { it.leftElbow } else null,
            rightElbowRomDeg = if (right) span { it.rightElbow } else null,
            upperArmDriftLeftDeg = if (left && exercise in listOf("BICEP_CURL", "TRICEPS_EXTENSION")) span { it.upperArmLeft } else null,
            upperArmDriftRightDeg = if (right && exercise in listOf("BICEP_CURL", "TRICEPS_EXTENSION")) span { it.upperArmRight } else null,
            torsoInclinationDeg = if (exercise == "BENT_OVER_ROW" && side) max { it.torsoInclination } else null,
            torsoInclinationChangeDeg = if (exercise in listOf("BICEP_CURL", "BENT_OVER_ROW", "TRICEPS_EXTENSION") && side) span { it.torsoInclination } else null,
            bodyLineDeviationDeg = if (exercise == "PUSH_UP" && side) max { it.bodyLineDeviation } else null,
            shoulderHeightDifferenceDeg = if (exercise == "PUSH_UP" && front) max { it.shoulderHeightDifference } else null,
            peakLateralFlexionDeg = peak, returnToNeutralDeviationDeg = returnDelta)
        val currentRom = if (bend) peak else if (activeSide == BodySide.LEFT) joints.leftElbowRomDeg else joints.rightElbowRomDeg
        val opposite = when (activeSide) { BodySide.LEFT -> BodySide.RIGHT; BodySide.RIGHT -> BodySide.LEFT; null -> null }
        // Alternating curl: compare the last completed contralateral ROM, NOT the currently resting arm.
        val oppositeDifference = if (front && currentRom != null && opposite != null) lastRom[opposite]?.let { abs(currentRom - it) } else null
        val bilateral = if (!curl && !bend && front) s.mapNotNull { f -> f.leftElbow?.takeIf { it.isFinite() && it in 0.0..180.0 }?.let { l ->
            f.rightElbow?.takeIf { it.isFinite() && it in 0.0..180.0 }?.let { abs(l - it) } } }.maxOrNull() else null
        val asymmetry = AsymmetryMetrics(elbowAngleDifferenceDeg = bilateral,
            contralateralRomDifferenceDeg = if (curl) oppositeDifference else null,
            lateralRangeDifferenceDeg = if (bend) oppositeDifference else null)
        if (front && currentRom != null && activeSide != null) lastRom[activeSide] = currentRom
        val phase = RepPhaseSegmenter.segment(s, b.partial)
        val events = mutableListOf<FormErrorEvent>()
        fun event(kind: FormErrorType, bodySide: BodySide? = null, severity: (RepFrameFeatures) -> Int) {
            events += FormEventAggregator.collect(s, phase.phases, kind, bodySide, severity)
        }
        fun variation(get: (RepFrameFeatures) -> Double?, threshold: Double): (RepFrameFeatures) -> Int {
            val baseline = measured.firstNotNullOfOrNull(get)
            return { f -> if (visible(f) && baseline != null) get(f)?.let { if (abs(it - baseline) > threshold) 1 else 0 } ?: 0 else 0 }
        }
        if (left && joints.upperArmDriftLeftDeg != null) event(FormErrorType.UPPER_ARM_POSITION_VARIATION, BodySide.LEFT,
            variation({ it.upperArmLeft }, P.UPPER_ARM_VARIATION_DEG))
        if (right && joints.upperArmDriftRightDeg != null) event(FormErrorType.UPPER_ARM_POSITION_VARIATION, BodySide.RIGHT,
            variation({ it.upperArmRight }, P.UPPER_ARM_VARIATION_DEG))
        if (joints.torsoInclinationChangeDeg != null) event(FormErrorType.TRUNK_POSITION_VARIATION,
            severity = variation({ it.torsoInclination }, P.TRUNK_VARIATION_DEG))
        if (joints.bodyLineDeviationDeg != null) event(FormErrorType.BODY_LINE_VARIATION) { f ->
            if (visible(f) && f.bodyLineDeviation?.let { it > P.BODY_LINE_VARIATION_DEG } == true) 1 else 0 }
        if (bilateral != null) event(FormErrorType.ELBOW_ASYMMETRY) { f ->
            if (f.leftElbow != null && f.rightElbow != null) EngineeringFormPolicy.asymmetrySeverity(abs(f.leftElbow - f.rightElbow)) else 0 }
        if (bend && oppositeDifference?.let { it > P.ROM_DIFFERENCE_DEG } == true)
            event(FormErrorType.LATERAL_RANGE_ASYMMETRY, activeSide) { f ->
                if (peak != null && f.lateralFlexion?.let { abs(it) >= peak - P.TURN_BAND_DEG } == true) 1 else 0 }
        if (bend && returnDelta?.let { it > P.RETURN_VARIATION_DEG } == true) {
            val baseline = lateral.first()
            event(FormErrorType.RETURN_TO_NEUTRAL_VARIATION, activeSide) { f ->
                if (abs(f.lateralFlexion ?: 90.0) <= P.sideBend.returnDeg &&
                    f.lateralFlexion?.let { abs(it - baseline) > P.RETURN_VARIATION_DEG } == true) 1 else 0 }
        }
        val strengths = buildList {
            if (bilateral != null && bilateral <= EngineeringFormPolicy.ASYMMETRY_DEG) add("ELBOW_SYMMETRY_OBSERVED")
            if (phase.durations.descentMs != null && phase.durations.ascentMs != null) add("BOTH_PHASES_CAPTURED")
        }
        val priority = events.sortedWith(compareByDescending<FormErrorEvent> { it.severity }.thenByDescending { it.occurrenceFrames }.thenBy { it.type.ordinal }).firstOrNull()
        return StructuredRepAnalysis(number, exercise, s.first().view.name,
            if (b.partial) null else s.last().timestampMs - s.first().timestampMs, phase.durations, joints, asymmetry,
            events, strengths, events.map { it.type.name }.distinct(),
            if (priority != null) LocalEventPresentation.feedback(priority, exercise) else ExerciseReportLanguage.defaultFeedback(exercise),
            observedFrames = s.size, partialCapture = b.partial, phaseModelVersion = 2, activeSide = activeSide)
    }
}
