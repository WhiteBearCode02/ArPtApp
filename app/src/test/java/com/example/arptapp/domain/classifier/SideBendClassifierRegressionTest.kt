package com.example.arptapp.domain.classifier

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.analyzer.ExerciseMotionPolicy as P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.abs

/** Scalar reproductions of the reported pose, not evidence of a physical-device exercise pass. */
class SideBendClassifierRegressionTest {
    private val loweringArm = ExerciseSideFeatures(
        knee = 170.0,
        hip = 170.0,
        elbow = 160.0,
        wristAboveShoulder = false,
        legVisibility = 1f,
        armVisibility = 1f,
        torsoInclination = 25.0,
        upperArmDownInclination = 0.0,
        upperArmUpInclination = 180.0
    )

    private fun asymmetricBend(
        lateral: Double,
        activeLeft: Boolean,
        oppositeElbow: Double = 90.0,
        oppositeVisibility: Float = 1f
    ): ExerciseFeatures {
        val active = loweringArm.copy(torsoInclination = abs(lateral))
        val opposite = active.copy(elbow = oppositeElbow, armVisibility = oppositeVisibility)
        return if (activeLeft) ExerciseFeatures(active, opposite, lateral)
        else ExerciseFeatures(opposite, active, lateral)
    }

    @Test fun oneLoweringArmAndBentHipArmRecognizeBothBendDirections() {
        for (lateral in listOf(-30.0, -25.0, -20.0, 20.0, 25.0, 30.0)) {
            for (activeLeft in listOf(false, true)) {
                for (oppositeElbow in listOf(70.0, 90.0, 110.0)) {
                    val fixture = asymmetricBend(lateral, activeLeft, oppositeElbow)
                    assertEquals("lateral=$lateral, activeLeft=$activeLeft, hipElbow=$oppositeElbow",
                        ExerciseType.SIDE_BEND, AdditionalExerciseClassifier.detect(fixture, CameraView.FRONT))
                }
            }
        }
    }

    @Test fun oppositeArmMayHaveLowVisibilityWhileBothLegsRemainVisible() {
        for (activeLeft in listOf(false, true)) {
            val fixture = asymmetricBend(25.0, activeLeft, oppositeVisibility = .2f)
            val classifier = ExerciseClassifier()
            repeat(4) { assertEquals(ExerciseType.UNKNOWN, classifier.detectFeatures(fixture, CameraView.FRONT)) }
            assertEquals(ExerciseType.SIDE_BEND, classifier.detectFeatures(fixture, CameraView.FRONT))
        }
    }

    @Test fun oneUsableArmDoesNotRelaxBothLegVisibilityOrExtensionRequirements() {
        val fixture = asymmetricBend(25.0, activeLeft = true)
        for (right in listOf(fixture.right.copy(legVisibility = .2f), fixture.right.copy(knee = 120.0))) {
            val classifier = ExerciseClassifier(requiredStableFrames = 1)
            assertEquals(ExerciseType.UNKNOWN, classifier.detectFeatures(fixture.copy(right = right), CameraView.FRONT))
        }
    }

    @Test fun clearLateralBendWithoutUsableLoweringArmAbstainsRatherThanSquat() {
        for (arm in listOf(
            loweringArm.copy(elbow = 90.0),
            loweringArm.copy(armVisibility = .2f),
            loweringArm.copy(wristAboveShoulder = null)
        )) {
            val fixture = ExerciseFeatures(arm, arm, 25.0)
            assertEquals(ExerciseType.UNKNOWN, AdditionalExerciseClassifier.detect(fixture, CameraView.FRONT))
            val classifier = ExerciseClassifier(requiredStableFrames = 1)
            repeat(4) {
                assertEquals(ExerciseType.UNKNOWN, classifier.detectFeatures(fixture, CameraView.FRONT))
                assertEquals(ExerciseType.UNKNOWN, classifier.candidateExercise)
            }
        }
    }

    @Test fun frontSquatBottomAndReturnKeepSquatWithSmallLateralNoiseAndVaryingArms() {
        for ((knee, hip, torso) in listOf(Triple(130.0, 150.0, 15.0), Triple(90.0, 100.0, 30.0), Triple(125.0, 140.0, 20.0))) {
            for (noise in listOf(-10.0, 0.0, 10.0)) {
                for (elbow in listOf(70.0, 160.0)) {
                    val side = loweringArm.copy(knee = knee, hip = hip, torsoInclination = torso, elbow = elbow)
                    val classifier = ExerciseClassifier(requiredStableFrames = 1)
                    assertEquals(ExerciseType.SQUAT, classifier.detectFeatures(ExerciseFeatures(side, side, noise), CameraView.FRONT))
                }
            }
        }
    }

    @Test fun explicitPressRemainsSupportedWithSmallLateralNoise() {
        val raisedArm = loweringArm.copy(wristAboveShoulder = true, upperArmUpInclination = 90.0)
        val classifier = ExerciseClassifier(requiredStableFrames = 1)
        assertEquals(ExerciseType.SHOULDER_PRESS,
            classifier.detectFeatures(ExerciseFeatures(raisedArm, raisedArm, 10.0), CameraView.FRONT))
    }

    @Test fun malformedLegOrLateralAnglesCannotBecomeSideBendEvidence() {
        val fixture = asymmetricBend(25.0, activeLeft = true).copy(right = loweringArm)
        for (knee in listOf(Double.NaN, Double.POSITIVE_INFINITY, 181.0)) {
            assertNotEquals(ExerciseType.SIDE_BEND, AdditionalExerciseClassifier.detect(
                fixture.copy(left = fixture.left.copy(knee = knee)), CameraView.FRONT))
        }
        for (lateral in listOf(Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNotEquals(ExerciseType.SIDE_BEND, AdditionalExerciseClassifier.detect(
                fixture.copy(signedLateralInclination = lateral), CameraView.FRONT))
        }
    }

    @Test fun frontOnlySideBendNeverUsesSideOrUnknownView() {
        for (view in listOf(CameraView.SIDE, CameraView.UNKNOWN)) {
            val classifier = ExerciseClassifier(requiredStableFrames = 1)
            assertEquals(ExerciseType.UNKNOWN, classifier.detectFeatures(asymmetricBend(25.0, true), view))
        }
    }

    @Test fun existingEngineeringThresholdsRemainInclusiveWithoutRelaxingArmEvidence() {
        assertEquals(15.0, P.sideBend.turnDeg, 0.0)
        assertEquals(5.0, P.sideBend.returnDeg, 0.0)
        assertEquals(140.0, P.SIDE_BEND_ELBOW_MIN_DEG, 0.0)
        val arm = loweringArm.copy(knee = P.STRAIGHT_LEG_CLASSIFIER_DEG,
            elbow = P.SIDE_BEND_ELBOW_MIN_DEG, legVisibility = P.VISIBILITY, armVisibility = P.VISIBILITY)
        val opposite = arm.copy(elbow = 90.0, armVisibility = .2f)
        for (lateral in listOf(-P.SIDE_BEND_CLASSIFIER_MAX_DEG, -P.sideBend.turnDeg,
            P.sideBend.turnDeg, P.SIDE_BEND_CLASSIFIER_MAX_DEG)) {
            assertEquals(ExerciseType.SIDE_BEND, AdditionalExerciseClassifier.detect(
                ExerciseFeatures(arm, opposite, lateral), CameraView.FRONT))
        }
        assertEquals(ExerciseType.UNKNOWN, AdditionalExerciseClassifier.detect(
            ExerciseFeatures(arm.copy(elbow = P.SIDE_BEND_ELBOW_MIN_DEG - 1.0), opposite, 25.0), CameraView.FRONT))
        assertEquals(ExerciseType.UNKNOWN, AdditionalExerciseClassifier.detect(
            ExerciseFeatures(arm, opposite, P.SIDE_BEND_CLASSIFIER_MAX_DEG + 1.0), CameraView.FRONT))
    }
}
