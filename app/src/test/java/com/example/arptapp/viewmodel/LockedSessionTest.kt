package com.example.arptapp.viewmodel

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.classifier.ExerciseClassifier
import com.example.arptapp.domain.counter.ExerciseCounter
import com.example.arptapp.domain.report.*
import com.example.arptapp.domain.session.SessionExerciseLockChange
import com.example.arptapp.model.RepRecord
import org.junit.Assert.*
import org.junit.Test

class LockedSessionTest {
    @Test fun firstSupportedTypeLocksUntilExplicitReset() {
        val vm = MainViewModel()
        assertFalse(vm.observeDetectedExercise(ExerciseType.LUNGE))
        assertTrue(vm.observeDetectedExercise(ExerciseType.SQUAT))
        listOf(ExerciseType.UNKNOWN, ExerciseType.LUNGE, ExerciseType.SHOULDER_PRESS).forEach {
            assertFalse(vm.observeDetectedExercise(it))
            assertEquals(ExerciseType.SQUAT, vm.sessionExerciseType)
        }
        vm.resetSession()
        assertTrue(vm.observeDetectedExercise(ExerciseType.SHOULDER_PRESS))
    }
    @Test fun noisyDetectionsDoNotResetCounterEngineRecordsOrFinalType() {
        val vm = MainViewModel()
        val counter = ExerciseCounter()
        val engine = RepAnalysisEngine()
        vm.observeDetectedExercise(ExerciseType.SQUAT)
        val noise = listOf(ExerciseType.SQUAT, ExerciseType.LUNGE, ExerciseType.UNKNOWN, ExerciseType.SHOULDER_PRESS)
        val angles = listOf(170.0, 140.0, 110.0, 80.0, 80.0, 80.0, 100.0, 130.0, 160.0, 170.0, 170.0)
        repeat(2) { cycle ->
            angles.forEachIndexed { index, angle ->
                vm.observeDetectedExercise(noise[index % noise.size])
                val time = 10_000L + (cycle * angles.size + index) * 200L
                val type = vm.sessionExerciseType.name
                engine.observe(RepFrameFeatures(time, type, CameraView.FRONT, angle,
                    leftKnee = angle, rightKnee = angle))
                if (counter.processAngle(type, angle, time)) {
                    val number = counter.getRepCount()
                    assertNotNull(vm.addCompletedRep(RepRecord(number, counter.getCurrentMaxAngle(), 0f),
                        engine.complete(number), "FRONT"))
                }
            }
        }
        assertEquals(2, counter.getRepCount())
        assertEquals(2, vm.getRepRecords().size)
        assertEquals(2, vm.structuredReport("LUNGE").repAnalyses.size)
        assertEquals("SQUAT", vm.generateFinalReport("SHOULDER_PRESS").exerciseType)
        assertEquals("SQUAT", vm.generateLockedSessionReport()!!.exerciseType)
        assertEquals(2, vm.generateLockedSessionReport()!!.totalReps)
        assertEquals(2, vm.getRepAnalyses("LUNGE").size)
    }
    @Test fun unsupportedOrUnconfirmedSessionsCannotProduceReport() {
        val vm = MainViewModel()
        listOf(ExerciseType.UNKNOWN, ExerciseType.LUNGE, ExerciseType.IDLE).forEach(vm::observeDetectedExercise)
        assertNull(vm.addCompletedRep(RepRecord(1, 90.0, 0f), RepScenarioFixtures.squat().copy(repNumber = 1), "FRONT"))
        assertNull(vm.generateLockedSessionReport())
        assertTrue(vm.getRepRecords().isEmpty())
    }
    @Test fun noCompletedRepDoesNotCreateZeroReport() {
        val vm = MainViewModel()
        vm.observeDetectedExercise(ExerciseType.SQUAT)
        assertNull(vm.generateLockedSessionReport())
    }
    @Test fun missingStructuralEvidenceDoesNotFallbackToDtwOrLegacyScore() {
        val vm = MainViewModel()
        vm.observeDetectedExercise(ExerciseType.SQUAT)
        val rep = vm.addCompletedRep(RepRecord(1, 80.0, 0f, poseScore = 99f), null, "FRONT", 99f)!!
        assertNull(rep.score)
        assertEquals(99f, rep.referenceSimilarity!!, .01f)
        assertTrue(vm.getRepAnalyses("SQUAT").isEmpty())
        assertNull(vm.generateLockedSessionReport())
    }
    @Test fun completedPressScoresWithoutPersonalReference() {
        val vm = MainViewModel()
        vm.observeDetectedExercise(ExerciseType.SHOULDER_PRESS)
        val rep = vm.addCompletedRep(RepRecord(1, 170.0, 0f), RepScenarioFixtures.press(difference = 0.0), "FRONT")!!
        assertNotNull(rep.score)
        assertNull(rep.referenceSimilarity)
        assertEquals(rep.score!!, vm.getRepAnalyses("LUNGE").single().score, .01f)
        assertEquals("SHOULDER_PRESS", vm.generateLockedSessionReport()!!.exerciseType)
    }
    @Test fun duplicateOrWrongTypeRepDoesNotCorruptSession() {
        val vm = MainViewModel()
        vm.observeDetectedExercise(ExerciseType.SQUAT)
        val record = RepRecord(1, 80.0, 0f)
        assertNull(vm.addCompletedRep(record, RepScenarioFixtures.press(), "FRONT"))
        assertNotNull(vm.addCompletedRep(record, RepScenarioFixtures.squat().copy(repNumber = 1), "FRONT"))
        assertNull(vm.addCompletedRep(record, RepScenarioFixtures.squat().copy(repNumber = 1), "FRONT"))
        assertEquals(1, vm.getRepRecords().size)
    }
    @Test fun unscoredGapDoesNotRenumberValidRep() {
        val vm = MainViewModel()
        vm.observeDetectedExercise(ExerciseType.SQUAT)
        vm.addCompletedRep(RepRecord(1, 80.0, 0f), null, "FRONT")
        vm.addCompletedRep(RepRecord(2, 80.0, 0f), RepScenarioFixtures.squat().copy(repNumber = 2), "FRONT")
        assertEquals(2, vm.getRepAnalyses("SQUAT").single().repNumber)
        assertEquals(2, vm.generateLockedSessionReport()!!.totalReps)
    }

    @Test fun zeroRepInitialCorrectionUpdatesObservableTypeAndRoutesNewSideBendReport() {
        val vm = MainViewModel()
        assertTrue(vm.observeDetectedExercise(ExerciseType.SQUAT))
        repeat(ExerciseClassifier.DEFAULT_REQUIRED_STABLE_FRAMES - 1) {
            assertEquals(SessionExerciseLockChange.NONE, vm.observeStableDetectedExercise(
                ExerciseType.SIDE_BEND, ExerciseType.SIDE_BEND, 0))
            assertEquals("SQUAT", vm.exerciseType.value)
        }
        assertEquals(SessionExerciseLockChange.CORRECTED, vm.observeStableDetectedExercise(
            ExerciseType.SIDE_BEND, ExerciseType.SIDE_BEND, 0))
        assertEquals(ExerciseType.SIDE_BEND, vm.sessionExerciseType)
        assertEquals("SIDE_BEND", vm.exerciseType.value)
        assertTrue(vm.getRepRecords().isEmpty())
        assertNull(vm.generateLockedSessionReport())

        // Fresh engines represent the Activity's correction-only reset, not a physical exercise test.
        val counter = ExerciseCounter()
        val engine = RepAnalysisEngine()
        var timestamp = 1_000L
        listOf(0.0, 10.0, 25.0, 10.0, 0.0).forEach { angle -> repeat(3) {
            engine.observe(RepFrameFeatures(timestamp, "SIDE_BEND", CameraView.FRONT, angle,
                lateralFlexion = angle))
            counter.processMeasurement("SIDE_BEND", angle, timestamp).forEach { completion ->
                assertNotNull(vm.addCompletedRep(RepRecord(completion.repNumber, completion.peakAngle, 0f),
                    engine.complete(completion.repNumber, activeSide = completion.activeSide,
                        motionStartedAtMs = completion.motionStartedAtMs), "FRONT"))
            }
            timestamp += 150L
        } }
        assertEquals(1, counter.getRepCount())
        val report = requireNotNull(vm.generateLockedSessionReport())
        assertEquals("SIDE_BEND", report.exerciseType)
        assertEquals(1, report.totalReps)
        assertEquals("SIDE_BEND", vm.structuredReport("SQUAT").exerciseType)
        assertEquals(1, vm.getRepAnalyses("SQUAT").size)
    }

    @Test fun completedRecordBlocksCorrectionEvenWhenExternalCounterIncorrectlyReportsZero() {
        val vm = MainViewModel()
        vm.observeDetectedExercise(ExerciseType.SQUAT)
        assertNotNull(vm.addCompletedRep(RepRecord(1, 80.0, 0f),
            RepScenarioFixtures.squat().copy(repNumber = 1), "FRONT"))
        val before = vm.getRepRecords()
        repeat(ExerciseClassifier.DEFAULT_REQUIRED_STABLE_FRAMES * 3) {
            assertEquals(SessionExerciseLockChange.NONE, vm.observeStableDetectedExercise(
                ExerciseType.SIDE_BEND, ExerciseType.SIDE_BEND, 0))
        }
        assertEquals(ExerciseType.SQUAT, vm.sessionExerciseType)
        assertEquals("SQUAT", vm.exerciseType.value)
        assertEquals(before, vm.getRepRecords())
        assertEquals("SQUAT", vm.generateLockedSessionReport()!!.exerciseType)
        assertEquals(1, vm.generateLockedSessionReport()!!.totalReps)
    }

    @Test fun unscoredCompletedRecordAlsoProtectsLockedTypeAndIsNotErased() {
        val vm = MainViewModel()
        vm.observeDetectedExercise(ExerciseType.SQUAT)
        assertNotNull(vm.addCompletedRep(RepRecord(1, 80.0, 0f), null, "FRONT"))
        val before = vm.getRepRecords()
        repeat(ExerciseClassifier.DEFAULT_REQUIRED_STABLE_FRAMES * 2) {
            assertEquals(SessionExerciseLockChange.NONE, vm.observeStableDetectedExercise(
                ExerciseType.SIDE_BEND, ExerciseType.SIDE_BEND, 0))
        }
        assertEquals(ExerciseType.SQUAT, vm.sessionExerciseType)
        assertEquals(before, vm.getRepRecords())
        assertNull(vm.generateLockedSessionReport())
    }

    @Test fun observedCompletionWithoutStoredRecordLatchesUntilExplicitSessionReset() {
        val vm = MainViewModel()
        vm.observeDetectedExercise(ExerciseType.SQUAT)
        assertEquals(SessionExerciseLockChange.NONE, vm.observeStableDetectedExercise(
            ExerciseType.SIDE_BEND, ExerciseType.SIDE_BEND, 1))
        repeat(ExerciseClassifier.DEFAULT_REQUIRED_STABLE_FRAMES * 2) {
            assertEquals(SessionExerciseLockChange.NONE, vm.observeStableDetectedExercise(
                ExerciseType.SIDE_BEND, ExerciseType.SIDE_BEND, 0))
        }
        assertEquals(ExerciseType.SQUAT, vm.sessionExerciseType)
        vm.resetSession()
        assertEquals(SessionExerciseLockChange.LOCKED, vm.observeStableDetectedExercise(
            ExerciseType.SIDE_BEND, ExerciseType.SIDE_BEND, 0))
        assertEquals("SIDE_BEND", vm.exerciseType.value)
    }
}
