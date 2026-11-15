package com.example.arptapp.viewmodel

import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.report.RepScenarioFixtures
import com.example.arptapp.domain.session.SessionExerciseLockChange
import com.example.arptapp.domain.session.SessionExerciseSelectionSource
import com.example.arptapp.model.RepRecord
import org.junit.Assert.*
import org.junit.Test

class ManualSelectedSessionTest {
    @Test fun everySupportedSelectionSetsRoutingAndObservableStateBeforeTheFirstFrame() {
        ExerciseType.supported.forEach { selected ->
            val vm = MainViewModel()
            assertTrue(vm.beginSession(selected))
            assertEquals(selected, vm.sessionExerciseType)
            assertEquals(selected.name, vm.exerciseType.value)
            assertEquals(SessionExerciseSelectionSource.MANUAL_SELECTED, vm.sessionSelectionSource)
            assertTrue(vm.getRepRecords().isEmpty())
            assertNull(vm.generateLockedSessionReport())
        }
    }

    @Test fun sideBendOrCurlSelectedWithSquatDiagnosticsStaysSelectedAtZeroReps() {
        listOf(ExerciseType.SIDE_BEND, ExerciseType.BICEP_CURL).forEach { selected ->
            val vm = MainViewModel()
            assertTrue(vm.beginSession(selected))
            repeat(30) {
                assertFalse(vm.observeDetectedExercise(ExerciseType.SQUAT))
                assertEquals(SessionExerciseLockChange.NONE,
                    vm.observeStableDetectedExercise(ExerciseType.SQUAT, ExerciseType.SQUAT, 0))
            }
            assertEquals(selected, vm.sessionExerciseType)
            assertEquals(selected.name, vm.exerciseType.value)
        }
    }

    @Test fun mixedClassifierResultsAndLegacyUiUpdatesCannotChangeManualRouting() {
        ExerciseType.supported.forEach { selected ->
            val vm = MainViewModel()
            assertTrue(vm.beginSession(selected))
            ExerciseType.entries.forEach { detected ->
                vm.observeDetectedExercise(detected)
                vm.observeStableDetectedExercise(detected, detected, 0)
                vm.updateExerciseType(detected.name)
                assertEquals(selected, vm.sessionExerciseType)
                assertEquals(selected.name, vm.exerciseType.value)
            }
            assertEquals(selected.name, vm.structuredReport("SQUAT").exerciseType)
            assertEquals(selected.name, vm.generateFinalReport("SQUAT").exerciseType)
        }
    }

    @Test fun rejectedStartDoesNotClearCompletedRecordsOrStructuredAnalysis() {
        val vm = MainViewModel()
        assertTrue(vm.beginSession(ExerciseType.SQUAT))
        assertNotNull(vm.addCompletedRep(RepRecord(1, 80.0, 0f),
            RepScenarioFixtures.squat().copy(repNumber = 1), "FRONT"))
        val beforeRecords = vm.getRepRecords()
        val beforeStructured = vm.structuredReport("SQUAT")
        val beforeReport = vm.generateLockedSessionReport()
        (ExerciseType.supported + listOf(ExerciseType.LUNGE, ExerciseType.UNKNOWN, ExerciseType.IDLE))
            .forEach { rejected ->
                assertFalse(vm.beginSession(rejected))
                assertEquals(beforeRecords, vm.getRepRecords())
                assertEquals(beforeStructured, vm.structuredReport("SQUAT"))
                assertEquals(beforeReport, vm.generateLockedSessionReport())
                assertEquals(ExerciseType.SQUAT, vm.sessionExerciseType)
            }
    }

    @Test fun invalidChoiceDoesNotErasePendingStateAndValidBeginClearsItOnlyAfterAdmission() {
        val vm = MainViewModel()
        // Compatibility callers may have staged a record before an explicit session boundary.
        vm.addRepRecord(1, 80.0, 0f)
        listOf(ExerciseType.UNKNOWN, ExerciseType.IDLE, ExerciseType.LUNGE).forEach { rejected ->
            assertFalse(vm.beginSession(rejected))
            assertEquals(1, vm.getRepRecords().size)
            assertEquals(ExerciseType.UNKNOWN, vm.sessionExerciseType)
            assertEquals("IDLE", vm.exerciseType.value)
        }
        assertTrue(vm.beginSession(ExerciseType.BICEP_CURL))
        assertTrue(vm.getRepRecords().isEmpty())
        assertTrue(vm.structuredReport("SQUAT").repAnalyses.isEmpty())
        assertEquals("BICEP_CURL", vm.exerciseType.value)
    }

    @Test fun endingOneSessionAllowsSelectingADifferentExerciseAndClearsTheOldReport() {
        val vm = MainViewModel()
        assertTrue(vm.beginSession(ExerciseType.SQUAT))
        assertNotNull(vm.addCompletedRep(RepRecord(1, 80.0, 0f),
            RepScenarioFixtures.squat().copy(repNumber = 1), "FRONT"))
        assertNotNull(vm.generateLockedSessionReport())
        vm.resetSession()
        assertEquals(SessionExerciseSelectionSource.NONE, vm.sessionSelectionSource)
        assertTrue(vm.beginSession(ExerciseType.SHOULDER_PRESS))
        assertEquals(ExerciseType.SHOULDER_PRESS, vm.sessionExerciseType)
        assertEquals("SHOULDER_PRESS", vm.exerciseType.value)
        assertTrue(vm.getRepRecords().isEmpty())
        assertNull(vm.generateLockedSessionReport())
        assertTrue(vm.structuredReport("SQUAT").repAnalyses.isEmpty())
    }

    @Test fun incompatibleStructuredRepCannotEnterASelectedSession() {
        val vm = MainViewModel()
        assertTrue(vm.beginSession(ExerciseType.SIDE_BEND))
        assertNull(vm.addCompletedRep(RepRecord(1, 80.0, 0f),
            RepScenarioFixtures.squat().copy(repNumber = 1), "FRONT"))
        assertTrue(vm.getRepRecords().isEmpty())
        assertNull(vm.generateLockedSessionReport())
    }
}
