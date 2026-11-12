package com.example.arptapp.domain.report

import com.example.arptapp.domain.classifier.ExerciseType
import kotlinx.serialization.json.*

/** Versioned scalar matrix. Decoder/network validators may not turn wrong-view facts into evidence. */
object ExerciseMetricPolicy {
    val expandedEvents = setOf(FormErrorType.UPPER_ARM_POSITION_VARIATION, FormErrorType.TRUNK_POSITION_VARIATION,
        FormErrorType.BODY_LINE_VARIATION, FormErrorType.LATERAL_RANGE_ASYMMETRY, FormErrorType.RETURN_TO_NEUTRAL_VARIATION)
    val legacyJoints = setOf("leftKneeMinAngle", "rightKneeMinAngle", "leftHipMinAngle", "rightHipMinAngle",
        "leftElbowMaxAngle", "rightElbowMaxAngle", "torsoLeanMaxDeg")
    val legacyAsymmetries = setOf("kneeAngleDifferenceDeg", "hipAngleDifferenceDeg", "elbowAngleDifferenceDeg")
    fun joints(value: JointMetrics): Map<String, Double> = scalars(Json.encodeToJsonElement(JointMetrics.serializer(), value))
    fun asymmetries(value: AsymmetryMetrics): Map<String, Double> = scalars(Json.encodeToJsonElement(AsymmetryMetrics.serializer(), value))
    private fun scalars(value: JsonElement) = value.jsonObject.mapNotNull { (key, entry) ->
        entry.jsonPrimitive.doubleOrNull?.let { key to it }
    }.toMap()
    private val elbows = setOf("leftElbowMinAngle", "rightElbowMinAngle", "leftElbowMaxAngle", "rightElbowMaxAngle", "leftElbowRomDeg", "rightElbowRomDeg")
    private val upperArms = setOf("upperArmDriftLeftDeg", "upperArmDriftRightDeg")
    fun allowedJoints(exercise: String, view: String): Set<String> {
        if (view !in setOf("FRONT", "SIDE")) return emptySet()
        return when (exercise) {
            "SQUAT" -> setOf("leftKneeMinAngle", "rightKneeMinAngle") + if (view == "SIDE")
                setOf("leftHipMinAngle", "rightHipMinAngle", "torsoLeanMaxDeg") else emptySet()
            "SHOULDER_PRESS" -> setOf("leftElbowMaxAngle", "rightElbowMaxAngle")
            "BICEP_CURL", "TRICEPS_EXTENSION" -> elbows + upperArms + if (view == "SIDE") setOf("torsoInclinationChangeDeg") else emptySet()
            "PUSH_UP" -> elbows + if (view == "SIDE") setOf("bodyLineDeviationDeg") else setOf("shoulderHeightDifferenceDeg")
            "BENT_OVER_ROW" -> elbows + if (view == "SIDE") setOf("torsoInclinationDeg", "torsoInclinationChangeDeg") else emptySet()
            "SIDE_BEND" -> if (view == "FRONT") setOf("peakLateralFlexionDeg", "returnToNeutralDeviationDeg") else emptySet()
            else -> emptySet()
        }
    }
    fun allowedAsymmetries(exercise: String, view: String): Set<String> = if (view != "FRONT") emptySet() else when (exercise) {
        "SQUAT" -> setOf("kneeAngleDifferenceDeg"); "BICEP_CURL" -> setOf("contralateralRomDifferenceDeg")
        "SIDE_BEND" -> setOf("lateralRangeDifferenceDeg")
        "SHOULDER_PRESS", "PUSH_UP", "BENT_OVER_ROW", "TRICEPS_EXTENSION" -> setOf("elbowAngleDifferenceDeg")
        else -> emptySet()
    }
    fun validSchema(workout: StructuredWorkoutAnalysis): Boolean = runCatching {
        require(workout.schemaVersion in 1..2)
        val types = if (workout.schemaVersion == 1) setOf("SQUAT", "SHOULDER_PRESS") else ExerciseType.supportedNames
        require(workout.exerciseType in types)
        workout.repAnalyses.forEach { rep ->
            require(rep.exerciseType == workout.exerciseType)
            val joints = joints(rep.jointMetrics); val asym = asymmetries(rep.asymmetryMetrics)
            if (workout.schemaVersion == 1) {
                require(rep.activeSide == null && joints.keys.all { it in legacyJoints } && asym.keys.all { it in legacyAsymmetries })
                require(rep.errorEvents.all { it.type.ordinal <= FormErrorType.INCOMPLETE_EXTENSION.ordinal })
            } else {
                require(joints.values.all { it.isFinite() && it in 0.0..180.0 } && asym.values.all { it.isFinite() && it in 0.0..180.0 })
                require(joints.keys.all { it in allowedJoints(rep.exerciseType, rep.cameraView) })
                require(asym.keys.all { it in allowedAsymmetries(rep.exerciseType, rep.cameraView) })
                if (rep.cameraView == "SIDE") {
                    val left = joints.keys.any { it.startsWith("left") || "Left" in it }
                    val right = joints.keys.any { it.startsWith("right") || "Right" in it }
                    require(!(left && right))
                }
                require(rep.activeSide == null || rep.exerciseType in setOf("BICEP_CURL", "SIDE_BEND"))
                if (rep.exerciseType == "BICEP_CURL" && joints.isNotEmpty()) {
                    require(rep.activeSide != null)
                    require(joints.keys.none { if (rep.activeSide == BodySide.LEFT) it.startsWith("right") || "Right" in it else it.startsWith("left") || "Left" in it })
                }
            }
        }
        true
    }.getOrDefault(false)
}
