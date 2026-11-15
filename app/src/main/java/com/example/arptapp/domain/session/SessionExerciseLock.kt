package com.example.arptapp.domain.session

import com.example.arptapp.domain.classifier.ExerciseClassifier
import com.example.arptapp.domain.classifier.ExerciseType

/** A type change is explicit so the owner can discard only unfinished counter/analysis state. */
enum class SessionExerciseLockChange { NONE, LOCKED, CORRECTED }

/** The routing authority is explicit: diagnostics never override a user-selected session. */
enum class SessionExerciseSelectionSource { NONE, LANDMARK_AUTOMATIC, MANUAL_SELECTED }

/**
 * A manually selected session contains one supported exercise from its explicit start until reset.
 *
 * The legacy automatic path retains its existing safety policy: before the first completed rep,
 * one mistaken initial lock may be corrected with a complete additional
 * landmark stabilization window. This is an engineering noise policy, not a biomechanical threshold.
 * Both the stable result and current candidate are required: the classifier intentionally keeps an
 * old stable result during brief UNKNOWN frames, which must not count as fresh correction evidence.
 * YOLO results are never admitted by this API's landmark-only caller.
 */
class SessionExerciseLock(
    private val requiredCorrectionFrames: Int = ExerciseClassifier.DEFAULT_REQUIRED_STABLE_FRAMES
) {
    init { require(requiredCorrectionFrames > 0) }

    var sessionExerciseType = ExerciseType.UNKNOWN
        private set

    var selectionSource = SessionExerciseSelectionSource.NONE
        private set

    private var correctionCandidate = ExerciseType.UNKNOWN
    private var correctionFrames = 0
    private var correctionUsed = false
    private var completedRepObserved = false

    /**
     * Locks the user's supported choice before any counter/analysis work starts.
     * No active lock can be replaced here, even at zero reps; the owner must explicitly [reset]
     * after ending the old session. Rejected choices do not mutate existing session state.
     */
    fun lockSelectedExercise(selectedExercise: ExerciseType): Boolean {
        if (selectedExercise !in supported || sessionExerciseType != ExerciseType.UNKNOWN ||
            completedRepObserved || selectionSource != SessionExerciseSelectionSource.NONE) return false
        sessionExerciseType = selectedExercise
        selectionSource = SessionExerciseSelectionSource.MANUAL_SELECTED
        clearCorrectionEvidence()
        return true
    }

    /** Compatibility path: an isolated stable observation can lock but never authorize correction. */
    fun observe(stableDetectedExercise: ExerciseType): Boolean {
        if (selectionSource == SessionExerciseSelectionSource.MANUAL_SELECTED) return false
        clearCorrectionEvidence()
        if (completedRepObserved || sessionExerciseType != ExerciseType.UNKNOWN ||
            stableDetectedExercise !in supported) return false
        sessionExerciseType = stableDetectedExercise
        selectionSource = SessionExerciseSelectionSource.LANDMARK_AUTOMATIC
        return true
    }

    /**
     * Call once per active landmark frame, before processing that frame's counter/analysis state.
     * The completed-rep boundary latches until [reset], even if an external counter later reads zero.
     */
    fun observeStable(
        stableDetectedExercise: ExerciseType,
        completedRepCount: Int,
        currentCandidate: ExerciseType
    ): SessionExerciseLockChange {
        // A diagnostic result has no routing authority in a manually selected session, including
        // before its first rep. It cannot accumulate the legacy zero-rep correction evidence.
        if (selectionSource == SessionExerciseSelectionSource.MANUAL_SELECTED)
            return SessionExerciseLockChange.NONE
        if (completedRepCount < 0) {
            clearCorrectionEvidence()
            return SessionExerciseLockChange.NONE
        }
        if (completedRepCount > 0) completedRepObserved = true

        if (completedRepObserved || stableDetectedExercise !in supported ||
            currentCandidate != stableDetectedExercise) {
            clearCorrectionEvidence()
            return SessionExerciseLockChange.NONE
        }
        if (sessionExerciseType == ExerciseType.UNKNOWN) {
            sessionExerciseType = stableDetectedExercise
            selectionSource = SessionExerciseSelectionSource.LANDMARK_AUTOMATIC
            clearCorrectionEvidence()
            return SessionExerciseLockChange.LOCKED
        }
        if (correctionUsed || stableDetectedExercise == sessionExerciseType) {
            clearCorrectionEvidence()
            return SessionExerciseLockChange.NONE
        }

        if (correctionCandidate != stableDetectedExercise) {
            correctionCandidate = stableDetectedExercise
            correctionFrames = 1
        } else if (correctionFrames < requiredCorrectionFrames) {
            correctionFrames++
        }
        if (correctionFrames < requiredCorrectionFrames) return SessionExerciseLockChange.NONE

        sessionExerciseType = stableDetectedExercise
        correctionUsed = true
        clearCorrectionEvidence()
        return SessionExerciseLockChange.CORRECTED
    }

    fun reset() {
        sessionExerciseType = ExerciseType.UNKNOWN
        selectionSource = SessionExerciseSelectionSource.NONE
        correctionUsed = false
        completedRepObserved = false
        clearCorrectionEvidence()
    }

    private fun clearCorrectionEvidence() {
        correctionCandidate = ExerciseType.UNKNOWN
        correctionFrames = 0
    }

    companion object { val supported = ExerciseType.supported }
}
