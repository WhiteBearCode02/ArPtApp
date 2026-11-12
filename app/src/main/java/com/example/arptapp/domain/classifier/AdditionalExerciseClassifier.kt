package com.example.arptapp.domain.classifier

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.analyzer.ExerciseMotionPolicy as P
import kotlin.math.abs

/** Conservative geometric candidates, not learned classification or medical posture validation.
 * An ambiguous new signature abstains; old feature fixtures without new evidence stay unchanged.
 */
object AdditionalExerciseClassifier {
    fun detect(f: ExerciseFeatures, view: CameraView): ExerciseType? {
        if (view == CameraView.UNKNOWN) return null
        val sides = if (view == CameraView.FRONT) listOf(f.left, f.right) else
            listOf(if (f.left.armVisibility >= f.right.armVisibility) f.left else f.right)
        fun usable(s: ExerciseSideFeatures) = s.armVisibility >= P.VISIBILITY && s.elbow?.let { it.isFinite() && it in 0.0..180.0 } == true
        fun upright(s: ExerciseSideFeatures) = s.torsoInclination?.let { it in 0.0..P.UPRIGHT_CLASSIFIER_DEG } == true
        fun extendedLeg(s: ExerciseSideFeatures) = s.legVisibility >= P.VISIBILITY &&
            s.knee?.let { it >= P.STRAIGHT_LEG_CLASSIFIER_DEG } == true
        val candidates = buildList {
            if (sides.all { usable(it) && it.elbow!! <= P.FLEXED_ELBOW_CLASSIFIER_DEG &&
                    it.upperArmUpInclination?.let { a -> a <= P.UPPER_ARM_CLASSIFIER_DEG } == true && upright(it) })
                add(ExerciseType.TRICEPS_EXTENSION)
            if (sides.any { usable(it) && extendedLeg(it) && upright(it) &&
                    it.elbow!! <= P.FLEXED_ELBOW_CLASSIFIER_DEG && it.wristAboveShoulder == false &&
                    it.upperArmDownInclination?.let { a -> a <= P.UPPER_ARM_CLASSIFIER_DEG } == true }) add(ExerciseType.BICEP_CURL)
            if (sides.all { usable(it) && extendedLeg(it) && it.hip?.let { a -> a >= P.PUSH_UP_HIP_MIN_DEG } == true &&
                    it.torsoInclination?.let { a -> a >= P.HORIZONTAL_CLASSIFIER_DEG && a <= P.HORIZONTAL_CLASSIFIER_MAX_DEG } == true &&
                    it.elbow!! <= P.PUSH_UP_ELBOW_MAX_DEG }) add(ExerciseType.PUSH_UP)
            if (sides.all { usable(it) && extendedLeg(it) && it.wristAboveShoulder == false &&
                    it.hip?.let { a -> a <= P.ROW_HIP_MAX_DEG } == true &&
                    it.torsoInclination?.let { a -> a in P.ROW_CLASSIFIER_MIN_DEG..P.ROW_CLASSIFIER_MAX_DEG } == true &&
                    it.elbow!! <= P.FLEXED_ELBOW_CLASSIFIER_DEG }) add(ExerciseType.BENT_OVER_ROW)
            if (view == CameraView.FRONT && f.signedLateralInclination?.let { abs(it) in P.sideBend.turnDeg..P.SIDE_BEND_CLASSIFIER_MAX_DEG } == true &&
                sides.all { usable(it) && extendedLeg(it) && it.elbow!! >= P.SIDE_BEND_ELBOW_MIN_DEG && it.wristAboveShoulder == false })
                add(ExerciseType.SIDE_BEND)
        }
        return if (candidates.size > 1) ExerciseType.UNKNOWN else candidates.singleOrNull()
    }
}
