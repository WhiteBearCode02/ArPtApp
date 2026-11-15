package com.example.arptapp.domain.session

import com.example.arptapp.domain.classifier.ExerciseClassifier
import com.example.arptapp.domain.classifier.ExerciseType

/** A type change is explicit so the owner can discard only unfinished counter/analysis state. */
enum class SessionExerciseLockChange { NONE, LOCKED, CORRECTED }

/**
 * A session contains one supported exercise once its first repetition is complete.
 *
 * Before that boundary, one mistaken initial lock may be corrected with a complete additional
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

    private var correctionCandidate = ExerciseType.UNKNOWN
    private var correctionFrames = 0
    private var correctionUsed = false
    private var completedRepObserved = false

    /** Compatibility path: an isolated stable observation can lock but never authorize correction. */
    fun observe(stableDetectedExercise: ExerciseType): Boolean {
        clearCorrectionEvidence()
        if (completedRepObserved || sessionExerciseType != ExerciseType.UNKNOWN ||
            stableDetectedExercise !in supported) return false
        sessionExerciseType = stableDetectedExercise
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
