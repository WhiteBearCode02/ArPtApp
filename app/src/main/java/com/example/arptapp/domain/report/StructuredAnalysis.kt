package com.example.arptapp.domain.report

import com.example.arptapp.domain.analyzer.CameraView
import kotlinx.serialization.Serializable

@Serializable enum class MovementPhase { DESCENT, BOTTOM, ASCENT }
@Serializable enum class BodySide { LEFT, RIGHT }
@Serializable enum class FormErrorType {
    KNEE_VALGUS, FORWARD_LEAN, KNEE_ASYMMETRY, HIP_ASYMMETRY,
    ELBOW_ASYMMETRY, SHALLOW_DEPTH, INCOMPLETE_EXTENSION
}
@Serializable data class PhaseDurations(val descentMs: Long? = null, val bottomMs: Long? = null, val ascentMs: Long? = null)
@Serializable data class JointMetrics(
    val leftKneeMinAngle: Double? = null, val rightKneeMinAngle: Double? = null,
    val leftHipMinAngle: Double? = null, val rightHipMinAngle: Double? = null,
    val leftElbowMaxAngle: Double? = null, val rightElbowMaxAngle: Double? = null,
    val torsoLeanMaxDeg: Double? = null
)
@Serializable data class AsymmetryMetrics(
    val kneeAngleDifferenceDeg: Double? = null, val hipAngleDifferenceDeg: Double? = null,
    val elbowAngleDifferenceDeg: Double? = null
)
@Serializable data class FormErrorEvent(
    val type: FormErrorType, val phase: MovementPhase? = null, val side: BodySide? = null,
    val severity: Int = 1, val timestampMs: Long? = null,
    /** Sum of frames in accepted >=3-frame runs of this type/side/phase only. */
    val occurrenceFrames: Int = 1
)

/** No coordinates, identity or images. null means NOT_EVALUATED, never normal/zero. */
@Serializable data class StructuredRepAnalysis(
    val repNumber: Int, val exerciseType: String, val cameraView: String,
    val totalDurationMs: Long?, val phaseDurations: PhaseDurations,
    val jointMetrics: JointMetrics, val asymmetryMetrics: AsymmetryMetrics,
    val errorEvents: List<FormErrorEvent>, val strengths: List<String>,
    val improvementCandidates: List<String>, val localPriorityFeedback: String,
    val score: Float? = null, val observedFrames: Int = 0, val partialCapture: Boolean = false,
    /** 1 = legacy min-based segmentation, 2 = exercise-specific observed phases. */
    val phaseModelVersion: Int = 1
)
@Serializable data class StructuredWorkoutAnalysis(
    val schemaVersion: Int = 1, val exerciseType: String, val totalReps: Int,
    val averageScore: Float? = null, val bestRepNumber: Int? = null, val weakestRepNumber: Int? = null,
    val dominantIssues: List<FormErrorType> = emptyList(), val repAnalyses: List<StructuredRepAnalysis>,
    val omittedReps: Int = 0
)

/** Immutable scalar input port. Raw landmarks live only in the camera adapter. */
data class RepFrameFeatures(
    val timestampMs: Long, val exerciseType: String, val view: CameraView, val representativeAngle: Double?,
    val leftKnee: Double? = null, val rightKnee: Double? = null,
    val leftHip: Double? = null, val rightHip: Double? = null,
    val leftElbow: Double? = null, val rightElbow: Double? = null,
    val torsoLean: Double? = null, val kneeValgusObserved: Boolean = false,
    val leftKneeValgusObserved: Boolean = false, val rightKneeValgusObserved: Boolean = false,
    // Transient scalars: ratios and visible-side selector never enter serialized LLM input.
    val leftKneeValgusRatio: Double? = null, val rightKneeValgusRatio: Double? = null,
    val visibleSide: BodySide? = null
)
