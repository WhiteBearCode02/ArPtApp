package com.example.arptapp.ui.dashboard

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardUiPolicyTest {
    @Test fun referenceListHasExactlySquatThenShoulderPress() {
        assertEquals(
            listOf(ExerciseType.SQUAT, ExerciseType.SHOULDER_PRESS),
            DashboardUiPolicy.referenceExerciseOptions
        )
    }

    @Test fun idleAllowsDirectionReferenceAndStart() {
        assertEquals(DashboardButtonState(true, true, true), state(false, false))
    }

    @Test fun activeWorkoutDisablesDirectionReferenceAndStart() {
        assertEquals(DashboardButtonState(false, false, false), state(true, false))
    }

    @Test fun referenceRecordingKeepsSaveEnabledButBlocksDirectionAndWorkout() {
        assertEquals(DashboardButtonState(false, true, false), state(false, true))
    }

    @Test fun inconsistentConcurrentStatesStillFailClosed() {
        assertEquals(DashboardButtonState(false, false, false), state(true, true))
    }

    @Test fun squatSelectionCanEnterExistingRecordingFromEitherKnownView() {
        knownViews.forEach { view ->
            assertTrue(canBegin(ExerciseType.SQUAT, view))
        }
    }

    @Test fun shoulderPressSelectionCanEnterExistingRecordingFromEitherKnownView() {
        knownViews.forEach { view ->
            assertTrue(canBegin(ExerciseType.SHOULDER_PRESS, view))
        }
    }

    @Test fun unknownViewCannotBeginAnyReference() {
        ExerciseType.entries.forEach { type ->
            assertFalse(canBegin(type, CameraView.UNKNOWN))
        }
    }

    @Test fun unsupportedExerciseTypesCannotEnterRecording() {
        listOf(ExerciseType.IDLE, ExerciseType.UNKNOWN, ExerciseType.LUNGE).forEach { type ->
            CameraView.entries.forEach { view -> assertFalse(canBegin(type, view)) }
        }
    }

    @Test fun dialogSelectionCannotBeginWhileWorkoutIsActive() {
        DashboardUiPolicy.referenceExerciseOptions.forEach { type ->
            knownViews.forEach { view ->
                assertFalse(canBegin(type, view, exercising = true))
            }
        }
    }

    @Test fun staleDialogSelectionCannotRestartOrReplaceAReferenceRecording() {
        DashboardUiPolicy.referenceExerciseOptions.forEach { type ->
            knownViews.forEach { view ->
                assertFalse(canBegin(type, view, recording = true))
                assertFalse(canBegin(type, view, exercising = true, recording = true))
            }
        }
    }

    @Test fun policyIsStatelessAcrossRecordingAndWorkoutTransitions() {
        val before = state(false, false)
        state(false, true)
        state(true, false)
        assertEquals(before, state(false, false))
        assertTrue(canBegin(ExerciseType.SQUAT, CameraView.FRONT))
    }

    @Test fun readyShowsSetupAndHidesWorkoutSummary() {
        assertEquals(DashboardDisplayState(true, false), display(false, false))
    }

    @Test fun referenceRecordingKeepsSetupSaveActionVisible() {
        assertEquals(DashboardDisplayState(true, false), display(false, true))
    }

    @Test fun activeWorkoutReplacesSetupWithCompactSummary() {
        assertEquals(DashboardDisplayState(false, true), display(true, false))
    }

    @Test fun activeWorkoutTakesDisplayPriorityOverInconsistentRecordingFlag() {
        assertEquals(DashboardDisplayState(false, true), display(true, true))
    }

    @Test fun setupAndWorkoutSummaryAreMutuallyExclusiveForEveryState() {
        listOf(false, true).forEach { exercising ->
            listOf(false, true).forEach { recording ->
                val result = display(exercising, recording)
                assertTrue(result.showSetup xor result.showWorkoutSummary)
                assertEquals(exercising, result.showWorkoutSummary)
            }
        }
    }

    @Test fun finishingWorkoutRestoresSetupWithoutChangingAdmissionPolicy() {
        val ready = display(false, false)
        display(true, false)
        assertEquals(ready, display(false, false))
        assertEquals(DashboardButtonState(true, true, true), state(false, false))
    }

    private fun display(exercising: Boolean, recording: Boolean) =
        DashboardUiPolicy.displayState(exercising, recording)

    private fun state(exercising: Boolean, recording: Boolean) =
        DashboardUiPolicy.buttonState(exercising, recording)

    private fun canBegin(
        type: ExerciseType,
        view: CameraView,
        exercising: Boolean = false,
        recording: Boolean = false
    ) = DashboardUiPolicy.canBeginReference(type, view, exercising, recording)

    private val knownViews = listOf(CameraView.FRONT, CameraView.SIDE)
}
