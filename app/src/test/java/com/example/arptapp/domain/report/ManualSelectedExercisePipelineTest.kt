package com.example.arptapp.domain.report

import com.example.arptapp.data.StructuredReportCodec
import com.example.arptapp.data.model.Landmark
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.analyzer.CoachingFeedbackFactory
import com.example.arptapp.domain.analyzer.PoseAngleExtractor
import com.example.arptapp.domain.analyzer.SevenPoseFixtures
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.counter.ExerciseCounter
import com.example.arptapp.domain.session.SessionExerciseLockChange
import com.example.arptapp.model.RepRecord
import com.example.arptapp.presentation.report.WorkoutReportData
import com.example.arptapp.viewmodel.MainViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Pure JVM routing proof: generated geometric landmarks, never a device/YOLO/accuracy claim.
 * The same selected type enters measurement, scalar extraction, counter, analysis and history DTO.
 */
@RunWith(Parameterized::class)
class ManualSelectedExercisePipelineTest(private val selected: ExerciseType) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun exercises() = ExerciseType.supported.map { arrayOf(it) }
    }

    private val view get() = when (selected) {
        ExerciseType.PUSH_UP, ExerciseType.BENT_OVER_ROW -> CameraView.SIDE
        else -> CameraView.FRONT
    }
    private val baseline get() = when (selected) {
        ExerciseType.SIDE_BEND -> 0.0
        ExerciseType.TRICEPS_EXTENSION -> 60.0
        else -> 170.0
    }
    private val turningPoint get() = when (selected) {
        ExerciseType.SIDE_BEND -> 25.0
        ExerciseType.TRICEPS_EXTENSION -> 170.0
        else -> 60.0
    }

    /** Shapes are geometry fixtures, not physiologically realistic exercise photographs. */
    private fun points(angle: Double): List<Landmark> {
        if (selected == ExerciseType.SIDE_BEND) {
            return SevenPoseFixtures.points((tan(Math.toRadians(angle)) * .4).toFloat())
        }
        val points = SevenPoseFixtures.points().toMutableList()
        fun interior(first: Int, middle: Int, last: Int, degrees: Double) {
            val center = points[middle]
            val firstDirection = atan2((points[first].y - center.y).toDouble(),
                (points[first].x - center.x).toDouble())
            val direction = firstDirection + Math.toRadians(degrees)
            points[last] = points[last].copy(x = center.x + (.15 * cos(direction)).toFloat(),
                y = center.y + (.15 * sin(direction)).toFloat())
        }
        if (selected == ExerciseType.SQUAT) {
            interior(23, 25, 27, angle)
            interior(24, 26, 28, angle)
        } else {
            if (selected == ExerciseType.TRICEPS_EXTENSION) {
                points[13] = points[13].copy(y = .1f)
                points[14] = points[14].copy(y = .1f)
            }
            interior(11, 13, 15, angle)
            // A curl's opposite arm stays neutral, so one cycle means one completion, not two.
            interior(12, 14, 16, if (selected == ExerciseType.BICEP_CURL) baseline else angle)
        }
        return points
    }

    private fun noise(vm: MainViewModel, repetitions: Int) {
        for (detected in ExerciseType.entries) {
            assertFalse("Legacy detector cannot replace $selected", vm.observeDetectedExercise(detected))
            assertEquals(SessionExerciseLockChange.NONE,
                vm.observeStableDetectedExercise(detected, detected, repetitions))
            vm.updateExerciseType(detected.name)
            assertEquals(selected, vm.sessionExerciseType)
            assertEquals(selected.name, vm.exerciseType.value)
        }
    }

    @Test fun selectedTypeIsImmediateAndStartSpeechDoesNotUseAutomaticSquat() {
        val vm = MainViewModel()
        assertTrue(vm.beginSession(selected))
        assertEquals(selected, vm.sessionExerciseType)
        assertEquals(selected.name, vm.exerciseType.value)
        assertEquals("${selected.displayName} 운동을 시작합니다.",
            ExerciseSpeechFormatter.sessionStart(vm.sessionExerciseType))
        assertTrue(vm.getRepRecords().isEmpty())
        assertNull(vm.generateLockedSessionReport())
    }

    @Test fun repeatedDisagreementCannotApplyZeroRepAutomaticCorrection() {
        val vm = MainViewModel()
        assertTrue(vm.beginSession(selected))
        repeat(20) { noise(vm, repetitions = 0) }
        assertEquals(selected, vm.sessionExerciseType)
        assertTrue(vm.getRepRecords().isEmpty())
    }

    @Test fun briefLowVisibilityThroughRealMeasurementPathPreservesSelectedCounterAndAnalysis() {
        val counter = ExerciseCounter()
        val engine = RepAnalysisEngine()
        var time = 1000L
        var completed = 0
        fun feed(angle: Double, visible: Boolean = true) {
            val landmarks = points(angle).let { if (visible) it else it.map { p -> p.copy(visibility = .1f) } }
            val measurement = PoseAngleExtractor.measurePose(selected.name, SevenPoseFixtures.pose(landmarks), view)
            val features = if (measurement.isEvaluable) RepFrameFeatureExtractor.extract(landmarks, selected.name, view,
                1.0, time, measurement.valueDeg) else null
            val result = counter.processFrame(selected.name, measurement.valueDeg, time, features?.leftElbow, features?.rightElbow)
            time += 150
            assertFalse("Short visibility dropout must not reset $selected", result.pendingReset)
            if (!visible) { assertFalse(result.evaluable); assertTrue(result.completions.isEmpty()); return }
            engine.observe(requireNotNull(features))
            for (rep in result.completions) {
                val analysis = requireNotNull(engine.complete(rep.repNumber, activeSide = rep.activeSide,
                    motionStartedAtMs = rep.motionStartedAtMs))
                assertEquals(selected.name, analysis.exerciseType)
                completed++
            }
        }
        repeat(3) { feed(baseline) }
        repeat(2) { feed((baseline + turningPoint) / 2) }
        repeat(2) { feed(turningPoint, visible = false) }
        repeat(3) { feed(turningPoint) }
        repeat(3) { feed((baseline + turningPoint) / 2) }
        repeat(3) { feed(baseline) }
        assertEquals(1, counter.getRepCount())
        assertEquals(1, completed)
    }

    @Test fun fiveSelectedCyclesPreserveTypeThroughMeasurementAnalysisSpeechReportAndHistoryDto() {
        val vm = MainViewModel()
        val counter = ExerciseCounter()
        val engine = RepAnalysisEngine()
        assertTrue(vm.beginSession(selected))
        var timestamp = 1_000L
        repeat(5) {
            for (angle in listOf(baseline, (baseline + turningPoint) / 2, turningPoint,
                (baseline + turningPoint) / 2, baseline)) repeat(3) {
                noise(vm, counter.getRepCount())
                val type = vm.sessionExerciseType.name
                val landmarks = points(angle)
                val measurement = PoseAngleExtractor.measurePose(type, SevenPoseFixtures.pose(landmarks), view)
                assertTrue("$selected/$angle must be measurable", measurement.isEvaluable)
                assertEquals(angle, requireNotNull(measurement.valueDeg), .001)
                val features = RepFrameFeatureExtractor.extract(landmarks, type, view, 1.0,
                    timestamp, measurement.valueDeg)
                assertEquals(selected.name, features.exerciseType)
                if (selected != ExerciseType.SQUAT) {
                    assertNull(features.leftKnee)
                    assertNull(features.rightKnee)
                    assertFalse(CoachingFeedbackFactory.create(measurement, emptyList()).message.contains("무릎"))
                }
                engine.observe(features)
                counter.processMeasurement(type, measurement.valueDeg, timestamp,
                    features.leftElbow, features.rightElbow).forEach { completion ->
                    val measured = requireNotNull(engine.complete(completion.repNumber,
                        activeSide = completion.activeSide, motionStartedAtMs = completion.motionStartedAtMs))
                    assertEquals(selected.name, measured.exerciseType)
                    val scored = requireNotNull(vm.addCompletedRep(RepRecord(completion.repNumber,
                        completion.peakAngle, 0f), measured, view.name))
                    assertNotNull(scored.score)
                    val speech = ExerciseSpeechFormatter.repCompleted(completion.repNumber, scored.localPriorityFeedback)
                    assertTrue(speech.startsWith("${completion.repNumber}회 완료."))
                    if (selected != ExerciseType.SQUAT) {
                        assertNull(scored.jointMetrics.leftKneeMinAngle)
                        assertNull(scored.jointMetrics.rightKneeMinAngle)
                        assertTrue(scored.errorEvents.none { it.type in setOf(FormErrorType.KNEE_VALGUS,
                            FormErrorType.KNEE_ASYMMETRY, FormErrorType.SHALLOW_DEPTH) })
                        assertFalse(speech.contains("무릎"))
                    }
                }
                timestamp += 150L // Existing three-frame dwell/minimum duration remain unchanged.
            }
        }
        assertEquals(5, counter.getRepCount())
        assertEquals(5, vm.getRepRecords().size)
        val intentionallyWrongLegacyType = if (selected == ExerciseType.SQUAT) "SIDE_BEND" else "SQUAT"
        val session = requireNotNull(vm.generateLockedSessionReport())
        assertEquals(selected.name, session.exerciseType)
        assertEquals(selected.name, vm.generateFinalReport(intentionallyWrongLegacyType).exerciseType)
        assertEquals(5, session.totalReps)
        val structured = vm.structuredReport(intentionallyWrongLegacyType)
        assertEquals(selected.name, structured.exerciseType)
        assertEquals(5, structured.repAnalyses.size)
        assertTrue(structured.repAnalyses.all { it.exerciseType == selected.name && it.cameraView == view.name })
        val json = StructuredReportCodec.encode(vm.getRepAnalyses(intentionallyWrongLegacyType), structured)
        val data = WorkoutReportData.create(exerciseType = selected.displayName,
            totalCount = session.totalReps, averageScore = session.averageScore.toFloat(), analysesJson = json,
            workoutDate = "2026-11-15 12:00", durationSeconds = timestamp / 1000,
            cloudEligible = false, feedbackMessage = session.feedbackMessage)
        val record = data.toRecord("local-fixture-owner")
        val reopened = WorkoutReportData.fromRecord(record)
        assertEquals(selected.displayName, record.exerciseType)
        assertEquals(selected.displayName, reopened.exerciseType)
        assertEquals(5, reopened.totalCount)
        assertEquals(structured, StructuredReportCodec.decode(reopened.analysesJson))
        assertFalse(record.cloudEligible)
        assertEquals("LOCAL_ONLY", record.syncState)
    }

    @Test fun endingAndResettingAllowsAnotherExplicitSelectionWithoutReusingRecords() {
        val vm = MainViewModel()
        assertTrue(vm.beginSession(selected))
        assertNotNull(vm.addCompletedRep(RepRecord(1, turningPoint, 0f), null, view.name))
        val other = ExerciseType.supported.first { it != selected }
        assertFalse(vm.beginSession(other))
        assertEquals(selected, vm.sessionExerciseType)
        assertEquals(1, vm.getRepRecords().size)
        vm.resetSession()
        assertTrue(vm.beginSession(other))
        assertEquals(other, vm.sessionExerciseType)
        assertEquals(other.name, vm.exerciseType.value)
        assertTrue(vm.getRepRecords().isEmpty())
        assertNull(vm.generateLockedSessionReport())
    }
}
