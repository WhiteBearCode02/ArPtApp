package com.example.arptapp.ui.dashboard

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseClassifier
import com.example.arptapp.domain.classifier.ExerciseFeatures
import com.example.arptapp.domain.classifier.ExerciseSideFeatures
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.session.SessionExerciseLockChange
import com.example.arptapp.viewmodel.MainViewModel
import org.junit.Assert.*
import org.junit.Test

/** Scalar/classifier-to-UI regression, not a physical exercise or rendered-device verification. */
class SideBendSessionRoutingTest {
    private val lowering = ExerciseSideFeatures(170.0, 170.0, 160.0, false, 1f, 1f,
        torsoInclination = 25.0, upperArmDownInclination = 0.0, upperArmUpInclination = 180.0)

    @Test fun stableLandmarkSideBendCorrectsInitialSquatAndProjectsOneConsistentUi() {
        val classifier = ExerciseClassifier()
        val vm = MainViewModel()
        val squat = lowering.copy(knee = 90.0, hip = 110.0, torsoInclination = 20.0)
        repeat(ExerciseClassifier.DEFAULT_REQUIRED_STABLE_FRAMES) {
            val stable = classifier.detectFeatures(ExerciseFeatures(squat, squat, 0.0), CameraView.FRONT)
            vm.observeStableDetectedExercise(stable, classifier.candidateExercise, 0)
        }
        assertEquals(ExerciseType.SQUAT, vm.sessionExerciseType)
        val bend = ExerciseFeatures(lowering, lowering.copy(elbow = 90.0, armVisibility = .4f), 25.0)
        var corrections = 0
        repeat(ExerciseClassifier.DEFAULT_REQUIRED_STABLE_FRAMES * 4) {
            val stable = classifier.detectFeatures(bend, CameraView.FRONT)
            if (vm.observeStableDetectedExercise(stable, classifier.candidateExercise, 0) == SessionExerciseLockChange.CORRECTED) corrections++
        }
        assertEquals(1, corrections)
        assertEquals(ExerciseType.SIDE_BEND, vm.sessionExerciseType)
        val ui = WorkoutExerciseUiFormatter.format(true, vm.sessionExerciseType, classifier.stableDetectedExercise)
        assertEquals("세션: 덤벨 사이드 벤드 · 감지: 덤벨 사이드 벤드", ui.detectedText)
        assertEquals("덤벨 사이드 벤드 운동 중", ui.title)
        assertEquals("덤벨 사이드 벤드", ui.countUnit)
        assertEquals("SIDE_BEND", vm.exerciseType.value)
        assertTrue(vm.getRepRecords().isEmpty())
        assertNull(vm.generateLockedSessionReport())
    }

    @Test fun oneCompletedRepKeepsSessionTitleAndCountUnitDespiteOtherDetection() {
        val vm = MainViewModel()
        vm.observeStableDetectedExercise(ExerciseType.SQUAT, ExerciseType.SQUAT, 0)
        repeat(20) {
            assertEquals(SessionExerciseLockChange.NONE,
                vm.observeStableDetectedExercise(ExerciseType.SIDE_BEND, ExerciseType.SIDE_BEND, 1))
        }
        val ui = WorkoutExerciseUiFormatter.format(true, vm.sessionExerciseType, ExerciseType.SIDE_BEND)
        assertEquals("세션: 스쿼트 · 감지: 덤벨 사이드 벤드", ui.detectedText)
        assertEquals("스쿼트 운동 중", ui.title)
        assertEquals("스쿼트", ui.countUnit)
    }

    @Test fun preparationHasNoLockedSessionTitleOrUnitEvenAfterSessionEnds() {
        val ui = WorkoutExerciseUiFormatter.format(false, ExerciseType.SIDE_BEND, ExerciseType.SIDE_BEND)
        assertEquals("인식 중: 덤벨 사이드 벤드", ui.detectedText)
        assertNull(ui.title)
        assertNull(ui.countUnit)
    }

    @Test fun unknownAndIdleRemainWaitingNotSquat() {
        val ui = WorkoutExerciseUiFormatter.format(true, ExerciseType.UNKNOWN, ExerciseType.IDLE)
        assertEquals("인식 중: 운동 감지 중", ui.detectedText)
        assertNull(ui.title)
        assertNull(ui.countUnit)
    }
}
