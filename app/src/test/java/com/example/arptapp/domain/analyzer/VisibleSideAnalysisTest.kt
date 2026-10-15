package com.example.arptapp.domain.analyzer

import com.example.arptapp.data.model.Landmark
import com.example.arptapp.data.model.PoseData
import com.example.arptapp.domain.classifier.ExerciseClassifier
import com.example.arptapp.domain.classifier.ExerciseType
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.util.Optional
import org.junit.Assert.*
import org.junit.Test

class VisibleSideAnalysisTest {
    private fun squat(offset: Int): List<Landmark> = MutableList(33) { Landmark(0f, 0f, 0f, .1f) }.apply {
        this[11 + offset] = Landmark(.3f, .25f, 0f, 1f)
        this[23 + offset] = Landmark(.3f, .45f, 0f, 1f)
        this[25 + offset] = Landmark(.5f, .45f, 0f, 1f)
        this[27 + offset] = Landmark(.5f, .7f, 0f, 1f)
    }
    private fun native(points: List<Landmark>) = points.map {
        NormalizedLandmark.create(it.x, it.y, it.z, Optional.of(it.visibility), Optional.of(1f))
    }
    @Test fun selectedVisibleSideMatchesRepresentativeAngleAndCoaching() {
        listOf(0, 1).forEach { offset ->
            val points = squat(offset)
            assertEquals(offset, PoseAngleExtractor.visibleSideOffset(points, "SQUAT"))
            val angle = PoseAngleExtractor.measurePose("SQUAT", PoseData(0, points, emptyMap()), CameraView.SIDE)
            assertTrue(angle.isEvaluable)
            assertEquals(90.0, angle.valueDeg!!, .01)
            val assessment = FormErrorAnalyzer.assessSquat(native(points), CameraView.SIDE)
            assertTrue(assessment.evaluable)
            assertTrue(assessment.errors.isEmpty())
            assertNull(assessment.kneeValgus)
        }
    }
    @Test fun nativeCameraAdapterUsesViewAwareFallback() {
        val classifier = ExerciseClassifier()
        val points = native(squat(1))
        repeat(5) { classifier.detectExercise(points, CameraView.SIDE) }
        assertEquals(ExerciseType.SQUAT, classifier.stableDetectedExercise)
    }
    @Test fun nativeFrontAdapterRequiresAndRecognizesBilateralSquat() {
        val points = squat(0).toMutableList()
        squat(1).forEachIndexed { index, p -> if (p.visibility == 1f) points[index] = p }
        val classifier = ExerciseClassifier()
        repeat(5) { classifier.detectExercise(native(points), CameraView.FRONT) }
        assertEquals(ExerciseType.SQUAT, classifier.stableDetectedExercise)
    }
    @Test fun nativeFrontPressUsesBothArmsAndRaisedWrists() {
        val points = MutableList(33) { Landmark(0f, 0f, 0f, .1f) }
        listOf(0, 1).forEach { offset ->
            points[11 + offset] = Landmark(.3f, .5f, 0f, 1f)
            points[13 + offset] = Landmark(.4f, .35f, 0f, 1f)
            points[15 + offset] = Landmark(.45f, .15f, 0f, 1f)
        }
        val classifier = ExerciseClassifier()
        repeat(5) { classifier.detectExercise(native(points), CameraView.FRONT) }
        assertEquals(ExerciseType.SHOULDER_PRESS, classifier.stableDetectedExercise)
    }
    @Test fun sidePressUsesVisibleRightArm() {
        val points = MutableList(33) { Landmark(0f, 0f, 0f, .1f) }.apply {
            this[12] = Landmark(.7f, .5f, 0f, 1f)
            this[14] = Landmark(.6f, .35f, 0f, 1f)
            this[16] = Landmark(.55f, .15f, 0f, 1f)
        }
        val classifier = ExerciseClassifier()
        repeat(5) { classifier.detectExercise(native(points), CameraView.SIDE) }
        assertEquals(ExerciseType.SHOULDER_PRESS, classifier.stableDetectedExercise)
        val measured = PoseAngleExtractor.measurePose("SHOULDER_PRESS", PoseData(0, points, emptyMap()), CameraView.SIDE)
        assertTrue(measured.isEvaluable)
        assertTrue(measured.valueDeg!! > 150)
    }
    @Test fun occlusionDoesNotInventSideAssessment() {
        val points = squat(0).map { it.copy(visibility = .2f) }
        assertFalse(FormErrorAnalyzer.assessSquat(native(points), CameraView.SIDE).evaluable)
        assertFalse(PoseAngleExtractor.measurePose("SQUAT", PoseData(0, points, emptyMap()), CameraView.SIDE).isEvaluable)
    }
}
