package com.example.arptapp.domain.analyzer

/** All numerical values here are ENGINEERING_INITIAL, not paper-derived normal/safe cutoffs.
 * Original squat/press thresholds remain owned by StandardPose and are deliberately unchanged.
 */
data class MotionThresholds(val turnDeg: Double, val returnDeg: Double, val comparisonRomDeg: Double)
object ExerciseMotionPolicy {
    const val SOURCE = "ENGINEERING_INITIAL"
    val bicepCurl = MotionThresholds(70.0, 150.0, 80.0)
    val pushUp = MotionThresholds(100.0, 155.0, 70.0)
    val bentOverRow = MotionThresholds(90.0, 150.0, 70.0)
    // Bottom -> top completion, like the existing press. Bottom must be re-observed for every rep.
    val tricepsExtension = MotionThresholds(90.0, 155.0, 70.0)
    val sideBend = MotionThresholds(15.0, 5.0, 20.0)
    const val VISIBILITY = .65f
    const val UPPER_ARM_VARIATION_DEG = 15.0
    const val TRUNK_VARIATION_DEG = 10.0
    const val BODY_LINE_VARIATION_DEG = 10.0
    const val RETURN_VARIATION_DEG = 3.0
    const val ROM_DIFFERENCE_DEG = 15.0
    const val STABILITY_FULL_LOSS_DEG = 40.0
    const val PHASE_TRAVEL_DEG = 10.0
    const val FIRST_MOTION_DEG = 3.0
    const val UPRIGHT_CLASSIFIER_DEG = 20.0
    const val HORIZONTAL_CLASSIFIER_DEG = 65.0
    const val ROW_CLASSIFIER_MIN_DEG = 35.0
    const val ROW_CLASSIFIER_MAX_DEG = 75.0
    const val STRAIGHT_LEG_CLASSIFIER_DEG = 145.0
    const val FLEXED_ELBOW_CLASSIFIER_DEG = 105.0
    const val UPPER_ARM_CLASSIFIER_DEG = 30.0
    const val PUSH_UP_HIP_MIN_DEG = 145.0
    const val PUSH_UP_ELBOW_MAX_DEG = 120.0
    const val ROW_HIP_MAX_DEG = 135.0
    const val SIDE_BEND_ELBOW_MIN_DEG = 140.0
    const val SIDE_BEND_CLASSIFIER_MAX_DEG = 45.0
    const val HORIZONTAL_CLASSIFIER_MAX_DEG = 110.0
    const val SIDE_BEND_MAX_MEASURABLE_DEG = 60.0
    const val MIN_SHOULDER_AXIS_WIDTH = .03
    const val MIN_HIP_AXIS_WIDTH = .02
    const val MAX_FRAME_GAP_MS = 1000L
    const val MAX_REP_FRAMES = 300
    const val MAX_REP_MS = 20_000L
    const val TURN_BAND_DEG = 5.0
    fun forExercise(type: String): MotionThresholds? = when (type) {
        "BICEP_CURL" -> bicepCurl; "PUSH_UP" -> pushUp; "BENT_OVER_ROW" -> bentOverRow
        "TRICEPS_EXTENSION" -> tricepsExtension; "SIDE_BEND" -> sideBend; else -> null
    }
}
