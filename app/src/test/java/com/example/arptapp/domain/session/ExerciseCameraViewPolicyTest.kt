package com.example.arptapp.domain.session

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCameraViewPolicyTest {
    @Test fun everySupportedExerciseHasAnAdmittedExistingCameraView() {
        assertEquals(7, ExerciseType.supported.size)
        ExerciseType.supported.forEach { type ->
            assertTrue(type.name, knownViews.any { ExerciseCameraViewPolicy.canStart(type, it) })
        }
    }

    @Test fun unselectedDirectionCannotStartAnySupportedExercise() {
        ExerciseType.supported.forEach { type ->
            assertEquals(ExerciseCameraViewAdmission.VIEW_REQUIRED,
                ExerciseCameraViewPolicy.assess(type, CameraView.UNKNOWN))
            assertFalse(ExerciseCameraViewPolicy.canStart(type, CameraView.UNKNOWN))
        }
    }

    @Test fun unknownIdleAndHistoricalLungeCannotStartFromAnyView() {
        listOf(ExerciseType.UNKNOWN, ExerciseType.IDLE, ExerciseType.LUNGE).forEach { type ->
            CameraView.entries.forEach { view ->
                assertEquals(ExerciseCameraViewAdmission.UNSUPPORTED,
                    ExerciseCameraViewPolicy.assess(type, view))
                assertFalse(ExerciseCameraViewPolicy.canStart(type, view))
            }
        }
    }

    @Test fun sideBendRetainsItsExistingFrontOnlyMeasurementConstraint() {
        assertEquals(ExerciseCameraViewAdmission.ALLOWED,
            ExerciseCameraViewPolicy.assess(ExerciseType.SIDE_BEND, CameraView.FRONT))
        assertTrue(ExerciseCameraViewPolicy.canStart(ExerciseType.SIDE_BEND, CameraView.FRONT))
        assertEquals(ExerciseCameraViewAdmission.FRONT_REQUIRED,
            ExerciseCameraViewPolicy.assess(ExerciseType.SIDE_BEND, CameraView.SIDE))
        assertFalse(ExerciseCameraViewPolicy.canStart(ExerciseType.SIDE_BEND, CameraView.SIDE))
    }

    @Test fun pushUpAndRowRecommendSideWithoutRemovingExistingFrontElbowSupport() {
        listOf(ExerciseType.PUSH_UP, ExerciseType.BENT_OVER_ROW).forEach { type ->
            assertEquals(ExerciseCameraViewAdmission.SIDE_RECOMMENDED,
                ExerciseCameraViewPolicy.assess(type, CameraView.FRONT))
            assertTrue(ExerciseCameraViewPolicy.canStart(type, CameraView.FRONT))
            assertEquals(ExerciseCameraViewAdmission.ALLOWED,
                ExerciseCameraViewPolicy.assess(type, CameraView.SIDE))
            assertTrue(ExerciseCameraViewPolicy.canStart(type, CameraView.SIDE))
        }
    }

    @Test fun squatPressCurlAndTricepsKeepBothKnownCameraViews() {
        listOf(ExerciseType.SQUAT, ExerciseType.SHOULDER_PRESS,
            ExerciseType.BICEP_CURL, ExerciseType.TRICEPS_EXTENSION).forEach { type ->
            knownViews.forEach { view ->
                assertEquals(ExerciseCameraViewAdmission.ALLOWED,
                    ExerciseCameraViewPolicy.assess(type, view))
                assertTrue(ExerciseCameraViewPolicy.canStart(type, view))
            }
        }
    }

    @Test fun assessingViewsDoesNotMutateTheSupportedRegistry() {
        val before = ExerciseType.supported.toList()
        ExerciseType.entries.forEach { type -> CameraView.entries.forEach { view ->
            ExerciseCameraViewPolicy.assess(type, view)
        } }
        assertEquals(before, ExerciseType.supported.toList())
        assertFalse(ExerciseType.LUNGE in before)
    }

    private val knownViews = listOf(CameraView.FRONT, CameraView.SIDE)
}
