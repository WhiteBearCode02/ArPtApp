package com.example.arptapp.domain.classifier

import com.example.arptapp.data.model.Landmark
import com.example.arptapp.domain.analyzer.CameraView
import org.junit.Assert.*
import org.junit.Test

class CameraViewClassifierTest {
    private fun side(knee: Double? = 90.0, hip: Double? = 100.0, visibility: Float = 1f,
                     press: Boolean = false) = ExerciseSideFeatures(knee, hip,
        if (press) 165.0 else null, press, visibility, if (press) visibility else 0f)
    private fun stable(features: ExerciseFeatures, view: CameraView): ExerciseType {
        val classifier = ExerciseClassifier()
        repeat(4) { assertEquals(ExerciseType.UNKNOWN, classifier.detectFeatures(features, view)) }
        return classifier.detectFeatures(features, view)
    }
    @Test fun frontBilateralSquat() {
        assertEquals(ExerciseType.SQUAT, stable(ExerciseFeatures(side(), side()), CameraView.FRONT))
    }
    @Test fun frontBilateralPress() {
        assertEquals(ExerciseType.SHOULDER_PRESS, stable(ExerciseFeatures(side(press = true), side(press = true)), CameraView.FRONT))
    }
    @Test fun frontRequiresBothSides() {
        assertEquals(ExerciseType.UNKNOWN, stable(ExerciseFeatures(side(), side(visibility = .2f)), CameraView.FRONT))
    }
    @Test fun sideUsesLeftWhenRightOccluded() {
        assertEquals(ExerciseType.SQUAT, stable(ExerciseFeatures(side(), side(170.0, visibility = .2f)), CameraView.SIDE))
    }
    @Test fun sideUsesRightWhenLeftOccluded() {
        assertEquals(ExerciseType.SQUAT, stable(ExerciseFeatures(side(170.0, visibility = .2f), side()), CameraView.SIDE))
    }
    @Test fun sidePressNeedsOnlyVisibleArm() {
        assertEquals(ExerciseType.SHOULDER_PRESS, stable(ExerciseFeatures(side(visibility = .1f), side(press = true)), CameraView.SIDE))
    }
    @Test fun sideAsymmetryIsNotLungeEvidence() {
        val classifier = ExerciseClassifier()
        repeat(30) {
            assertNotEquals(ExerciseType.LUNGE,
                classifier.detectFeatures(ExerciseFeatures(side(), side(170.0, visibility = .8f)), CameraView.SIDE))
            assertNotEquals(ExerciseType.LUNGE, classifier.candidateExercise)
        }
    }
    @Test fun lowVisibilityAbstainsInBothViews() {
        listOf(CameraView.FRONT, CameraView.SIDE).forEach { view ->
            assertEquals(ExerciseType.UNKNOWN, stable(ExerciseFeatures(side(visibility = .2f), side(visibility = .2f)), view))
        }
    }
    @Test fun unknownViewAbstains() {
        assertEquals(ExerciseType.UNKNOWN, stable(ExerciseFeatures(side(), side()), CameraView.UNKNOWN))
    }
    @Test fun lungeOnlyStaysUnsupportedCandidate() {
        val classifier = ExerciseClassifier()
        repeat(30) { assertEquals(ExerciseType.UNKNOWN,
            classifier.detectFeatures(ExerciseFeatures(side(), side(170.0)), CameraView.FRONT)) }
        assertEquals(ExerciseType.LUNGE, classifier.candidateExercise)
    }
    @Test fun briefLungeDoesNotReplaceSupportedStableType() {
        val classifier = ExerciseClassifier()
        repeat(5) { classifier.detectFeatures(ExerciseFeatures(side(), side()), CameraView.FRONT) }
        repeat(3) { assertEquals(ExerciseType.SQUAT,
            classifier.detectFeatures(ExerciseFeatures(side(), side(170.0)), CameraView.FRONT)) }
        assertEquals(ExerciseType.SQUAT, classifier.stableDetectedExercise)
        assertEquals(ExerciseType.LUNGE, classifier.candidateExercise)
    }
    @Test fun supportedSwitchRequiresDoubleEvidence() {
        val classifier = ExerciseClassifier()
        repeat(5) { classifier.detectFeatures(ExerciseFeatures(side(), side()), CameraView.FRONT) }
        repeat(9) { assertEquals(ExerciseType.SQUAT,
            classifier.detectFeatures(ExerciseFeatures(side(press = true), side(press = true)), CameraView.FRONT)) }
        assertEquals(ExerciseType.SHOULDER_PRESS,
            classifier.detectFeatures(ExerciseFeatures(side(press = true), side(press = true)), CameraView.FRONT))
    }
    @Test fun changingViewClearsPreviousEvidence() {
        val classifier = ExerciseClassifier()
        repeat(5) { classifier.detectFeatures(ExerciseFeatures(side(), side()), CameraView.FRONT) }
        assertEquals(ExerciseType.UNKNOWN, classifier.detectFeatures(ExerciseFeatures(side(), side()), CameraView.SIDE))
    }
    @Test fun scalarExtractorUsesVisibleSideGeometryWithoutFabricatingHiddenJoints() {
        listOf(0, 1).forEach { visible ->
            val points = MutableList(33) { Landmark(0f, 0f, 0f, 0f) }
            points[11 + visible] = Landmark(.3f, .25f, 0f, 1f)
            points[23 + visible] = Landmark(.3f, .45f, 0f, 1f)
            points[25 + visible] = Landmark(.5f, .45f, 0f, 1f)
            points[27 + visible] = Landmark(.5f, .7f, 0f, 1f)
            val features = ExerciseFeatureExtractor.extract(points, CameraView.SIDE)
            val shown = if (visible == 0) features.left else features.right
            val hidden = if (visible == 0) features.right else features.left
            assertEquals(90.0, shown.knee!!, .01)
            assertNull(hidden.knee)
            assertEquals(ExerciseType.SQUAT, stable(features, CameraView.SIDE))
        }
    }
    @Test fun nonFiniteCoordinatesCannotBecomeExercise() {
        val points = List(33) { Landmark(Float.NaN, 0f, 0f, 1f) }
        assertEquals(ExerciseType.UNKNOWN, stable(ExerciseFeatureExtractor.extract(points, CameraView.SIDE), CameraView.SIDE))
    }
}
