package com.example.arptapp.domain.session

import com.example.arptapp.domain.classifier.ExerciseClassifier
import com.example.arptapp.domain.classifier.ExerciseType
import org.junit.Assert.*
import org.junit.Test

class ManualSessionExerciseLockTest {
    @Test fun everySupportedSelectionImmediatelyBecomesManualAuthority() {
        assertEquals(7, ExerciseType.supported.size)
        ExerciseType.supported.forEach { selected ->
            val lock = SessionExerciseLock()
            assertTrue(selected.name, lock.lockSelectedExercise(selected))
            assertEquals(selected, lock.sessionExerciseType)
            assertEquals(SessionExerciseSelectionSource.MANUAL_SELECTED, lock.selectionSource)
        }
    }

    @Test fun sideBendAndCurlCannotBeCorrectedToSquatEvenBeforeAnyRep() {
        listOf(ExerciseType.SIDE_BEND, ExerciseType.BICEP_CURL).forEach { selected ->
            val lock = SessionExerciseLock(requiredCorrectionFrames = 1)
            assertTrue(lock.lockSelectedExercise(selected))
            repeat(ExerciseClassifier.DEFAULT_REQUIRED_STABLE_FRAMES * 4) {
                assertFalse(lock.observe(ExerciseType.SQUAT))
                assertEquals(SessionExerciseLockChange.NONE,
                    lock.observeStable(ExerciseType.SQUAT, 0, ExerciseType.SQUAT))
                assertEquals(selected, lock.sessionExerciseType)
            }
        }
    }

    @Test fun allDiagnosticResultsAndRepCountsLeaveEveryManualSessionUnchanged() {
        ExerciseType.supported.forEach { selected ->
            val lock = SessionExerciseLock(requiredCorrectionFrames = 1)
            assertTrue(lock.lockSelectedExercise(selected))
            listOf(-1, 0, 1, 5, 0).forEach { completed ->
                ExerciseType.entries.forEach { detected ->
                    assertFalse(lock.observe(detected))
                    assertEquals(SessionExerciseLockChange.NONE,
                        lock.observeStable(detected, completed, detected))
                    assertEquals(SessionExerciseLockChange.NONE,
                        lock.observeStable(detected, completed, ExerciseType.UNKNOWN))
                    assertEquals(selected, lock.sessionExerciseType)
                    assertEquals(SessionExerciseSelectionSource.MANUAL_SELECTED, lock.selectionSource)
                }
            }
        }
    }

    @Test fun unsupportedSelectionsFailClosedWithoutChangingSelectionOrAuthority() {
        val lock = SessionExerciseLock()
        listOf(ExerciseType.UNKNOWN, ExerciseType.IDLE, ExerciseType.LUNGE).forEach { rejected ->
            assertFalse(lock.lockSelectedExercise(rejected))
            assertEquals(ExerciseType.UNKNOWN, lock.sessionExerciseType)
            assertEquals(SessionExerciseSelectionSource.NONE, lock.selectionSource)
        }
        assertTrue(lock.lockSelectedExercise(ExerciseType.SIDE_BEND))
        assertFalse(lock.lockSelectedExercise(ExerciseType.LUNGE))
        assertEquals(ExerciseType.SIDE_BEND, lock.sessionExerciseType)
        assertEquals(SessionExerciseSelectionSource.MANUAL_SELECTED, lock.selectionSource)
    }

    @Test fun aSecondStartCannotReplaceAnActiveManualOrAutomaticSession() {
        val manual = SessionExerciseLock()
        assertTrue(manual.lockSelectedExercise(ExerciseType.SIDE_BEND))
        assertFalse(manual.lockSelectedExercise(ExerciseType.SQUAT))
        assertFalse(manual.lockSelectedExercise(ExerciseType.SIDE_BEND))
        assertEquals(ExerciseType.SIDE_BEND, manual.sessionExerciseType)

        val automatic = SessionExerciseLock()
        assertTrue(automatic.observe(ExerciseType.SQUAT))
        assertEquals(SessionExerciseSelectionSource.LANDMARK_AUTOMATIC, automatic.selectionSource)
        assertFalse(automatic.lockSelectedExercise(ExerciseType.SIDE_BEND))
        assertEquals(ExerciseType.SQUAT, automatic.sessionExerciseType)
        assertEquals(SessionExerciseSelectionSource.LANDMARK_AUTOMATIC, automatic.selectionSource)
    }

    @Test fun explicitEndResetAllowsEveryDifferentSelectionInTheFollowingSession() {
        val lock = SessionExerciseLock()
        ExerciseType.supported.forEach { selected ->
            assertTrue(lock.lockSelectedExercise(selected))
            lock.observeStable(ExerciseType.SQUAT, 5, ExerciseType.SQUAT)
            lock.reset()
            assertEquals(ExerciseType.UNKNOWN, lock.sessionExerciseType)
            assertEquals(SessionExerciseSelectionSource.NONE, lock.selectionSource)
        }
    }

    @Test fun legacyAutomaticZeroRepCorrectionRemainsExplicitlySeparate() {
        val lock = SessionExerciseLock(requiredCorrectionFrames = 2)
        assertEquals(SessionExerciseLockChange.LOCKED,
            lock.observeStable(ExerciseType.SQUAT, 0, ExerciseType.SQUAT))
        assertEquals(SessionExerciseSelectionSource.LANDMARK_AUTOMATIC, lock.selectionSource)
        assertEquals(SessionExerciseLockChange.NONE,
            lock.observeStable(ExerciseType.SIDE_BEND, 0, ExerciseType.SIDE_BEND))
        assertEquals(SessionExerciseLockChange.CORRECTED,
            lock.observeStable(ExerciseType.SIDE_BEND, 0, ExerciseType.SIDE_BEND))
        assertEquals(SessionExerciseSelectionSource.LANDMARK_AUTOMATIC, lock.selectionSource)
        repeat(5) {
            assertEquals(SessionExerciseLockChange.NONE,
                lock.observeStable(ExerciseType.BICEP_CURL, 0, ExerciseType.BICEP_CURL))
        }
        assertEquals(ExerciseType.SIDE_BEND, lock.sessionExerciseType)
    }
}
