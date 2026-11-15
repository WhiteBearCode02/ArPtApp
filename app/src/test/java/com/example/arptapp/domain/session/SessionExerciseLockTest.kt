package com.example.arptapp.domain.session

import com.example.arptapp.domain.classifier.ExerciseClassifier
import com.example.arptapp.domain.classifier.ExerciseType
import org.junit.Assert.*
import org.junit.Test

class SessionExerciseLockTest {
    private fun SessionExerciseLock.frame(
        stable: ExerciseType,
        reps: Int = 0,
        candidate: ExerciseType = stable
    ) = observeStable(stable, reps, candidate)

    @Test fun initialLockRequiresSupportedFreshLandmarkEvidenceAndZeroCompletedReps() {
        val lock = SessionExerciseLock()
        listOf(ExerciseType.UNKNOWN, ExerciseType.IDLE, ExerciseType.LUNGE).forEach {
            assertEquals(SessionExerciseLockChange.NONE, lock.frame(it))
        }
        assertEquals(SessionExerciseLockChange.NONE,
            lock.frame(ExerciseType.SQUAT, candidate = ExerciseType.UNKNOWN))
        assertEquals(ExerciseType.UNKNOWN, lock.sessionExerciseType)
        assertEquals(SessionExerciseLockChange.LOCKED, lock.frame(ExerciseType.SQUAT))
        assertEquals(ExerciseType.SQUAT, lock.sessionExerciseType)

        val alreadyCompleted = SessionExerciseLock()
        assertEquals(SessionExerciseLockChange.NONE, alreadyCompleted.frame(ExerciseType.SQUAT, reps = 1))
        assertEquals(SessionExerciseLockChange.NONE, alreadyCompleted.frame(ExerciseType.SQUAT, reps = 0))
        assertEquals(ExerciseType.UNKNOWN, alreadyCompleted.sessionExerciseType)
        assertFalse(alreadyCompleted.observe(ExerciseType.SQUAT))
    }

    @Test fun mistakenSquatCorrectsOnlyAfterOneFullAdditionalStableWindow() {
        val lock = SessionExerciseLock()
        assertEquals(SessionExerciseLockChange.LOCKED, lock.frame(ExerciseType.SQUAT))
        repeat(ExerciseClassifier.DEFAULT_REQUIRED_STABLE_FRAMES - 1) {
            assertEquals(SessionExerciseLockChange.NONE, lock.frame(ExerciseType.SIDE_BEND))
            assertEquals(ExerciseType.SQUAT, lock.sessionExerciseType)
        }
        assertEquals(SessionExerciseLockChange.CORRECTED, lock.frame(ExerciseType.SIDE_BEND))
        assertEquals(ExerciseType.SIDE_BEND, lock.sessionExerciseType)
    }

    @Test fun unknownUnsupportedOldTypeAndStaleStableResultEachBreakCorrectionEvidence() {
        listOf(
            ExerciseType.UNKNOWN to ExerciseType.UNKNOWN,
            ExerciseType.LUNGE to ExerciseType.LUNGE,
            ExerciseType.IDLE to ExerciseType.IDLE,
            ExerciseType.SQUAT to ExerciseType.SQUAT,
            ExerciseType.SIDE_BEND to ExerciseType.UNKNOWN,
            ExerciseType.SIDE_BEND to ExerciseType.LUNGE,
            ExerciseType.SIDE_BEND to ExerciseType.SQUAT
        ).forEach { (stable, candidate) ->
            val lock = SessionExerciseLock(requiredCorrectionFrames = 3)
            lock.frame(ExerciseType.SQUAT)
            repeat(2) { lock.frame(ExerciseType.SIDE_BEND) }
            assertEquals(SessionExerciseLockChange.NONE, lock.frame(stable, candidate = candidate))
            repeat(2) {
                assertEquals(SessionExerciseLockChange.NONE, lock.frame(ExerciseType.SIDE_BEND))
                assertEquals(ExerciseType.SQUAT, lock.sessionExerciseType)
            }
            assertEquals(SessionExerciseLockChange.CORRECTED, lock.frame(ExerciseType.SIDE_BEND))
        }
    }

    @Test fun alternatingSupportedCandidatesCannotAccumulateCorrectionEvidence() {
        val lock = SessionExerciseLock(requiredCorrectionFrames = 3)
        lock.frame(ExerciseType.SQUAT)
        repeat(20) {
            assertEquals(SessionExerciseLockChange.NONE, lock.frame(
                if (it % 2 == 0) ExerciseType.SIDE_BEND else ExerciseType.SHOULDER_PRESS))
        }
        assertEquals(ExerciseType.SQUAT, lock.sessionExerciseType)
    }

    @Test fun completionBoundaryIsPermanentEvenIfExternalCounterReturnsToZero() {
        val lock = SessionExerciseLock(requiredCorrectionFrames = 3)
        lock.frame(ExerciseType.SQUAT)
        repeat(2) { lock.frame(ExerciseType.SIDE_BEND) }
        assertEquals(SessionExerciseLockChange.NONE, lock.frame(ExerciseType.SIDE_BEND, reps = 1))
        repeat(20) { assertEquals(SessionExerciseLockChange.NONE, lock.frame(ExerciseType.SIDE_BEND)) }
        assertEquals(ExerciseType.SQUAT, lock.sessionExerciseType)
    }

    @Test fun completedRepAlsoLatchesDuringUnknownOrUnsupportedFrame() {
        listOf(ExerciseType.UNKNOWN, ExerciseType.LUNGE).forEach { type ->
            val lock = SessionExerciseLock(requiredCorrectionFrames = 2)
            lock.frame(ExerciseType.SQUAT)
            lock.frame(type, reps = 1)
            repeat(5) { assertEquals(SessionExerciseLockChange.NONE, lock.frame(ExerciseType.SIDE_BEND)) }
            assertEquals(ExerciseType.SQUAT, lock.sessionExerciseType)
        }
    }

    @Test fun oneCorrectionOnlyAndExplicitSessionResetRestoresAllowance() {
        val lock = SessionExerciseLock(requiredCorrectionFrames = 2)
        lock.frame(ExerciseType.SQUAT)
        lock.frame(ExerciseType.SIDE_BEND)
        assertEquals(SessionExerciseLockChange.CORRECTED, lock.frame(ExerciseType.SIDE_BEND))
        repeat(10) { assertEquals(SessionExerciseLockChange.NONE, lock.frame(ExerciseType.SHOULDER_PRESS)) }
        assertEquals(ExerciseType.SIDE_BEND, lock.sessionExerciseType)

        lock.frame(ExerciseType.UNKNOWN, reps = 1)
        lock.reset()
        assertEquals(ExerciseType.UNKNOWN, lock.sessionExerciseType)
        assertEquals(SessionExerciseLockChange.LOCKED, lock.frame(ExerciseType.SHOULDER_PRESS))
        lock.frame(ExerciseType.SIDE_BEND)
        assertEquals(SessionExerciseLockChange.CORRECTED, lock.frame(ExerciseType.SIDE_BEND))
    }

    @Test fun negativeCountFailsClosedAndInterruptsPendingCorrection() {
        val lock = SessionExerciseLock(requiredCorrectionFrames = 2)
        assertEquals(SessionExerciseLockChange.NONE, lock.frame(ExerciseType.SQUAT, reps = -1))
        assertEquals(ExerciseType.UNKNOWN, lock.sessionExerciseType)
        lock.frame(ExerciseType.SQUAT)
        lock.frame(ExerciseType.SIDE_BEND)
        assertEquals(SessionExerciseLockChange.NONE, lock.frame(ExerciseType.SIDE_BEND, reps = -1))
        assertEquals(SessionExerciseLockChange.NONE, lock.frame(ExerciseType.SIDE_BEND))
        assertEquals(SessionExerciseLockChange.CORRECTED, lock.frame(ExerciseType.SIDE_BEND))
    }

    @Test fun legacyObservationNeverCorrectsAndInterruptsFreshCorrectionEvidence() {
        val lock = SessionExerciseLock(requiredCorrectionFrames = 2)
        assertTrue(lock.observe(ExerciseType.SQUAT))
        repeat(10) { assertFalse(lock.observe(ExerciseType.SIDE_BEND)) }
        assertEquals(ExerciseType.SQUAT, lock.sessionExerciseType)
        lock.frame(ExerciseType.SIDE_BEND)
        assertFalse(lock.observe(ExerciseType.SIDE_BEND))
        assertEquals(SessionExerciseLockChange.NONE, lock.frame(ExerciseType.SIDE_BEND))
        assertEquals(SessionExerciseLockChange.CORRECTED, lock.frame(ExerciseType.SIDE_BEND))
    }

    @Test fun invalidEvidenceWindowsAreRejected() {
        listOf(0, -1, Int.MIN_VALUE).forEach { value ->
            try {
                SessionExerciseLock(requiredCorrectionFrames = value)
                fail("non-positive correction window must fail")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
