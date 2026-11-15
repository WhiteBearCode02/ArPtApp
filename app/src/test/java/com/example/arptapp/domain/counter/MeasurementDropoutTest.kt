package com.example.arptapp.domain.counter

import com.example.arptapp.domain.analyzer.ExerciseMotionPolicy
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.report.RepAnalysisEngine
import com.example.arptapp.domain.report.RepFrameFeatures
import com.example.arptapp.domain.analyzer.CameraView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Synthetic timestamped scalar regression, NOT proof of physical-device counting. */
@RunWith(Parameterized::class)
class MeasurementDropoutTest(private val type: String) {
    companion object { @JvmStatic @Parameterized.Parameters(name = "{0}") fun types() = ExerciseType.supported.map { arrayOf(it.name) } }
    private val counter = ExerciseCounter()
    private var time = 0L
    private val baseline get() = if (type == "SIDE_BEND") 0.0 else if (type == "TRICEPS_EXTENSION") 60.0 else 170.0
    private val turn get() = if (type == "SIDE_BEND") 25.0 else if (type == "TRICEPS_EXTENSION") 170.0 else 60.0
    private fun sample(value: Double?): ExerciseCounter.FrameResult {
        val frame = counter.processFrame(type, value, time, if (type == "BICEP_CURL") value else null)
        time += 150 // preserves the existing 600 ms minimum even for bottom->top triceps completion
        return frame
    }
    private fun hold(value: Double?, frames: Int = 3) = repeat(frames) { sample(value) }
    private fun cycle() { hold(baseline); hold((baseline + turn) / 2); hold(turn); hold((baseline + turn) / 2); hold(baseline) }

    @Test fun twoAbsentFramesDuringTransitionPreserveStateWithoutCounting() {
        hold(baseline)
        hold(turn, 2) // insufficient stable frames yet
        val phase = counter.diagnosticPhase()
        repeat(2) {
            val missing = sample(null)
            assertFalse(missing.evaluable); assertFalse(missing.pendingReset)
            assertTrue(missing.completions.isEmpty())
            assertEquals(phase, counter.diagnosticPhase())
            assertEquals(0, counter.getRepCount())
        }
        hold(turn, 6); hold(baseline)
        assertEquals(1, counter.getRepCount())
    }

    @Test fun prolongedDropoutResetsIncompleteOnlyAndRequiresFreshCycle() {
        cycle()
        assertEquals(1, counter.getRepCount())
        hold(baseline); hold(turn, 2)
        val invalidAt = time
        assertFalse(sample(null).pendingReset)
        time = invalidAt + 999
        assertFalse(sample(null).pendingReset)
        time = invalidAt + 1000
        val expired = sample(null) // first invalid + exactly 1000 ms
        assertEquals(ExerciseMotionPolicy.MAX_FRAME_GAP_MS, expired.invalidDurationMs)
        assertTrue(expired.pendingReset)
        assertFalse(sample(null).pendingReset) // one reset per missing episode
        hold(baseline)
        assertEquals(1, counter.getRepCount())
        cycle()
        assertEquals(2, counter.getRepCount())
    }

    @Test fun recoveryAtTimeoutCannotCompleteStaleCycle() {
        hold(baseline); hold(turn, 2)
        sample(null)
        time += ExerciseMotionPolicy.MAX_FRAME_GAP_MS
        assertTrue(sample(baseline).pendingReset)
        hold(baseline)
        assertEquals(0, counter.getRepCount())
    }

    @Test fun missingCallbacksAlsoExpireForLegacyAndExpandedCounters() {
        hold(baseline); hold(turn, 2)
        time += ExerciseMotionPolicy.MAX_FRAME_GAP_MS
        assertTrue(sample(baseline).pendingReset)
        hold(baseline)
        assertEquals(0, counter.getRepCount())
    }

    @Test fun turnAndReturnJitterCannotDuplicateACompletedRepetition() {
        val thresholds = ExerciseMotionPolicy.forExercise(type)
        val boundary = thresholds?.let { if (type == "TRICEPS_EXTENSION") it.returnDeg else it.turnDeg }
            ?: if (type == "SQUAT") 90.0 else 70.0
        hold(baseline)
        repeat(20) { sample(boundary + if (it % 2 == 0) -3 else 3) }
        assertEquals(0, counter.getRepCount())
        hold(turn, 8); hold(baseline)
        assertEquals(1, counter.getRepCount())
        val returnBoundary = thresholds?.let { if (type == "TRICEPS_EXTENSION") it.turnDeg else it.returnDeg } ?: 160.0
        repeat(20) { sample(returnBoundary + if (it % 2 == 0) -3 else 3) }
        repeat(30) { sample(baseline + if (it % 2 == 0) -2 else 2) }
        assertEquals(1, counter.getRepCount())
    }

    @Test fun analysisAndCounterConsumeSameContinuityVerdict() {
        val engine = RepAnalysisEngine()
        var completions = 0
        fun frame(value: Double?) {
            val timestamp = time
            val result = sample(value)
            if (result.pendingReset) engine.reset()
            if (!result.evaluable) return
            engine.observe(RepFrameFeatures(timestamp, type, CameraView.FRONT, value,
                leftElbow = if (type == "BICEP_CURL") value else null,
                lateralFlexion = if (type == "SIDE_BEND") value else null))
            for (rep in result.completions) {
                val analysis = engine.complete(rep.repNumber, activeSide = rep.activeSide, motionStartedAtMs = rep.motionStartedAtMs)
                assertNotNull(analysis)
                assertEquals(type, analysis!!.exerciseType)
                completions++
            }
        }
        repeat(3) { frame(baseline) }
        repeat(2) { frame((baseline + turn) / 2) }
        repeat(2) { frame(null) }
        repeat(6) { frame(turn) }
        repeat(3) { frame(baseline) }
        assertEquals(1, completions)
    }
}

class MeasurementContinuityTest {
    @Test fun timeoutIsMeasuredFromFirstInvalidAndNotRepeated() {
        val gate = MeasurementContinuity()
        assertFalse(gate.observe(true, 0).resetPending)
        assertFalse(gate.observe(false, 100).resetPending)
        assertFalse(gate.observe(false, 1099).resetPending)
        assertTrue(gate.observe(false, 1100).resetPending)
        assertFalse(gate.observe(false, 1200).resetPending)
        assertFalse(gate.observe(true, 1300).resetPending)
    }
    @Test fun nonMonotonicTimestampNeverCounts() {
        val counter = ExerciseCounter()
        counter.processFrame("SQUAT", 60.0, 100)
        val stale = counter.processFrame("SQUAT", 170.0, 100)
        assertFalse(stale.evaluable); assertTrue(stale.pendingReset)
        assertEquals(0, counter.getRepCount())
    }
    @Test fun oneMissingCurlArmCannotResetOtherArmAndCanRecoverItself() {
        val counter = ExerciseCounter()
        var time = 0L
        fun both(l: Double?, r: Double?) { counter.processFrame("BICEP_CURL", l ?: r, time, l, r); time += 100 }
        repeat(3) { both(170.0, 170.0) }
        repeat(2) { both(60.0, 60.0) }
        repeat(2) { both(null, 60.0) }
        repeat(6) { both(60.0, 60.0) }
        repeat(3) { both(170.0, 170.0) }
        assertEquals(2, counter.getRepCount())
    }

    @Test fun prolongedLeftCurlLossExpiresOnlyLeftWhileRightCompletes() {
        val counter = ExerciseCounter()
        var time = 0L
        fun both(l: Double?, r: Double?) { counter.processFrame("BICEP_CURL", l ?: r, time, l, r); time += 150 }
        repeat(3) { both(170.0, 170.0) }
        repeat(2) { both(60.0, 60.0) }
        repeat(9) { both(null, 60.0) }
        repeat(3) { both(170.0, 170.0) }
        assertEquals(1, counter.getRepCount())
    }
}
