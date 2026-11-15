package com.example.arptapp.ui.dashboard

import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.report.ExerciseSpeechFormatter
import com.example.arptapp.viewmodel.MainViewModel
import org.junit.Assert.*
import org.junit.Test

/** Pure state/string checks, not a rendered Android or physical exercise verification. */
class ManualWorkoutUiTest {
    @Test fun allSupportedSelectionsImmediatelyShowConsistentTitleUnitAndStartSpeech() {
        ExerciseType.supported.forEach { selected ->
            val vm = MainViewModel()
            assertTrue(vm.beginSession(selected))
            val ui = WorkoutExerciseUiFormatter.formatManualSession(true, vm.sessionExerciseType)
            assertEquals("선택 운동: ${selected.displayName}", ui.detectedText)
            assertEquals("${selected.displayName} 운동 중", ui.title)
            assertEquals(selected.displayName, ui.countUnit)
            assertEquals("${selected.displayName} 운동을 시작합니다.", ExerciseSpeechFormatter.sessionStart(vm.sessionExerciseType))
        }
    }

    @Test fun conflictingDetectionsNeverChangeTheSelectedUi() {
        listOf(ExerciseType.SIDE_BEND, ExerciseType.BICEP_CURL).forEach { selected ->
            val vm = MainViewModel()
            assertTrue(vm.beginSession(selected))
            val before = WorkoutExerciseUiFormatter.formatManualSession(true, selected)
            ExerciseType.entries.forEach { detected ->
                repeat(20) { vm.observeStableDetectedExercise(detected, detected, 0) }
                assertEquals(before, WorkoutExerciseUiFormatter.formatManualSession(true, vm.sessionExerciseType))
                assertEquals(selected, vm.sessionExerciseType)
            }
        }
    }

    @Test fun endedOrUnselectedSessionsDoNotAdvertiseAutomaticExerciseTitles() {
        ExerciseType.entries.forEach { previous ->
            val ui = WorkoutExerciseUiFormatter.formatManualSession(false, previous)
            assertEquals("운동 시작을 눌러 종목을 선택해 주세요", ui.detectedText)
            assertNull(ui.title)
            assertNull(ui.countUnit)
        }
        assertNull(WorkoutExerciseUiFormatter.formatManualSession(true, ExerciseType.UNKNOWN).title)
        assertNull(WorkoutExerciseUiFormatter.formatManualSession(true, ExerciseType.LUNGE).title)
    }
}
