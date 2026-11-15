package com.example.arptapp.domain.counter

import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.report.BodySide
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SevenExerciseCounterTest(private val type: String) {
    companion object { @JvmStatic @Parameterized.Parameters(name = "{0}") fun types() = ExerciseType.supported.map { arrayOf(it.name) } }
    private val counter = ExerciseCounter()
    private var timestamp = 0L
    private val baseline get() = when (type) { "SIDE_BEND" -> 0.0; "TRICEPS_EXTENSION" -> 60.0; else -> 170.0 }
    private val turn get() = when (type) { "SIDE_BEND" -> 25.0; "TRICEPS_EXTENSION" -> 170.0; else -> 60.0 }
    private fun feed(value: Double?, step: Long = 150) {
        counter.processMeasurement(type, value, timestamp, if (type == "BICEP_CURL") value else null,
            if (type == "BICEP_CURL") 170.0 else null)
        timestamp += step
    }
    private fun hold(value: Double?, count: Int = 3, step: Long = 150) = repeat(count) { feed(value, step) }
    private fun cycle(step: Long = 150) {
        hold(baseline, step = step); hold((baseline + turn) / 2, step = step); hold(turn, step = step)
        hold((baseline + turn) / 2, step = step); hold(baseline, step = step)
    }
    @Test fun oneCompleteCycle() { cycle(); assertEquals(1, counter.getRepCount()) }
    @Test fun tenCompleteCycles() { repeat(10) { cycle() }; assertEquals(10, counter.getRepCount()) }
    @Test fun oneFrameCrossingCannotCount() { hold(baseline); hold(turn, 1); hold(baseline, 8); assertEquals(0, counter.getRepCount()) }
    @Test fun thresholdNoiseCannotCount() {
        hold(baseline)
        val threshold = when(type) { "SQUAT" -> 90.0; "SHOULDER_PRESS" -> 70.0; "BICEP_CURL" -> 70.0; "PUSH_UP" -> 100.0; "BENT_OVER_ROW" -> 90.0; "TRICEPS_EXTENSION" -> 155.0; else -> 15.0 }
        repeat(30) { feed(threshold + if (it % 2 == 0) -1 else 1) }
        assertEquals(0, counter.getRepCount())
    }
    @Test fun tooFastFakeCycleCannotCount() { cycle(step = 10); assertEquals(0, counter.getRepCount()) }
    @Test fun prolongedVisibilityLossInvalidatesIncompleteCycle() {
        hold(baseline); hold((baseline + turn) / 2); hold(turn)
        val alreadyCompleted = counter.getRepCount()
        hold(null, 9); hold(baseline, 5) // >= MAX_FRAME_GAP_MS; one absent frame now preserves state.
        assertEquals(alreadyCompleted, counter.getRepCount())
        if (type != "TRICEPS_EXTENSION") assertEquals(0, alreadyCompleted)
    }
    @Test fun sessionResetDropsPendingAndCompletedState() {
        cycle(); counter.resetSession(); assertEquals(0, counter.getRepCount())
        cycle(); assertEquals(1, counter.getRepCount())
    }
    @Test fun exerciseSwitchCannotCompletePreviousMotion() {
        hold(baseline); hold(turn)
        val completed = counter.getRepCount()
        counter.processMeasurement("UNKNOWN", null, timestamp); timestamp += 150
        hold(baseline, 5); assertEquals(completed, counter.getRepCount())
    }
}

class SideAwareCounterTest {
    @Test fun abortedMotionCannotSubsidizeAFastLaterRepetition() {
        for(type in ExerciseType.supported.drop(2).map {it.name}) {
            val c=ExerciseCounter();var t=0L
            val baseline=if(type=="SIDE_BEND")0.0 else if(type=="TRICEPS_EXTENSION")60.0 else 170.0
            val turn=if(type=="SIDE_BEND")25.0 else if(type=="TRICEPS_EXTENSION")170.0 else 60.0
            fun hold(a:Double,step:Long)=repeat(3){c.processMeasurement(type,a,t,if(type=="BICEP_CURL")a else null,if(type=="BICEP_CURL")170.0 else null);t+=step}
            hold(baseline,150);hold((baseline+turn)/2,150);hold(baseline,150)
            hold((baseline+turn)/2,10);hold(turn,10);hold(baseline,10)
            assertEquals("$type aborted start must be discarded",0,c.getRepCount())
        }
    }
    @Test fun alternatingCurlArmsHaveIndependentStateAndTotal() {
        val c = ExerciseCounter(); var time = 0L
        fun hold(left: Double, right: Double) = repeat(3) { c.processMeasurement("BICEP_CURL", minOf(left, right), time, left, right); time += 150 }
        hold(170.0,170.0); hold(120.0,170.0); hold(60.0,170.0); hold(120.0,170.0); hold(170.0,170.0)
        assertEquals(1, c.getRepCount())
        hold(170.0,170.0); hold(170.0,120.0); hold(170.0,60.0); hold(170.0,120.0); hold(170.0,170.0)
        assertEquals(2, c.getRepCount())
    }
    @Test fun simultaneousCurlsProduceTwoSideTaggedCompletions() {
        val c = ExerciseCounter(); var time = 0L
        val out = mutableListOf<ExerciseCounter.CompletedRep>()
        for (angle in listOf(170.0,120.0,60.0,120.0,170.0)) repeat(3) {
            out += c.processMeasurement("BICEP_CURL", angle, time, angle, angle); time += 150
        }
        assertEquals(listOf(BodySide.LEFT, BodySide.RIGHT), out.map { it.activeSide })
        assertEquals(listOf(1,2), out.map { it.repNumber })
    }
    @Test fun lateralSignIsRetainedAndSmallSwayDoesNotCount() {
        val c = ExerciseCounter(); var time = 0L
        val out = mutableListOf<ExerciseCounter.CompletedRep>()
        fun hold(angle: Double) = repeat(3) { out += c.processMeasurement("SIDE_BEND", angle, time); time += 150 }
        repeat(3) { hold(0.0); hold(4.0); hold(-4.0) }; assertEquals(0,c.getRepCount())
        hold(0.0); hold(-8.0); hold(-25.0); hold(-8.0); hold(0.0)
        hold(0.0); hold(8.0); hold(25.0); hold(8.0); hold(0.0)
        assertEquals(listOf(BodySide.LEFT,BodySide.RIGHT),out.map { it.activeSide })
    }
    @Test fun overheadTopJitterNeedsAnotherFlexedBaseline() {
        val c = ExerciseCounter(); var time = 0L
        for (angle in listOf(60.0,110.0,170.0,169.0,170.0,166.0,170.0)) repeat(3) {
            c.processMeasurement("TRICEPS_EXTENSION",angle,time);time+=150
        }
        assertEquals(1,c.getRepCount())
    }
    @Test fun lossOfLeftArmDoesNotEraseRightArmCycle() {
        val c = ExerciseCounter(); var time = 0L
        val out = mutableListOf<ExerciseCounter.CompletedRep>()
        for (angle in listOf(170.0,120.0,60.0,120.0,170.0)) repeat(3) {
            out += c.processMeasurement("BICEP_CURL",angle,time,null,angle);time+=150
        }
        assertEquals(listOf(BodySide.RIGHT),out.map { it.activeSide })
    }
}

/** User-reported signed trajectory, not a classifier/YOLO or real-camera validation fixture. */
class SideBendSignedSequenceCounterTest {
    private val rightSequence = listOf(0.0, 5.0, 10.0, 16.0, 20.0, 25.0, 20.0, 10.0, 4.0, 0.0)

    private class Frames {
        val counter = ExerciseCounter()
        val completed = mutableListOf<ExerciseCounter.CompletedRep>()
        private var timestamp = 0L

        fun sample(angle: Double?, frames: Int = 3) = repeat(frames) {
            completed += counter.processMeasurement("SIDE_BEND", angle, timestamp)
            timestamp += 150L // contiguous camera frames: below MAX_FRAME_GAP_MS, not a timing override
        }
    }

    private fun assertHeldSequence(sign: Double, expectedSide: BodySide) {
        val frames = Frames()
        // Each requested sample is held for the default three-frame stable transition. This
        // preserves the exact 15/5 degree hysteresis and the default minimum 600 ms duration.
        rightSequence.forEach { frames.sample(it * sign) }
        assertEquals(1, frames.counter.getRepCount())
        val completed = frames.completed.single()
        assertEquals(1, completed.repNumber)
        assertEquals(expectedSide, completed.activeSide)
        assertEquals(25.0, completed.peakAngle, 0.0)
        assertNotNull(completed.motionStartedAtMs)
        assertTrue(completed.outwardDurationMs > 0)
        assertTrue(completed.returnDurationMs > 0)
        assertTrue(completed.outwardDurationMs + completed.returnDurationMs >= 600L)
    }

    @Test fun exactPositiveHeldSequenceCountsOneRightRepetition() {
        assertHeldSequence(1.0, BodySide.RIGHT)
    }

    @Test fun exactNegativeHeldSequenceCountsOneLeftRepetition() {
        assertHeldSequence(-1.0, BodySide.LEFT)
    }

    @Test fun oneFramePerRequestedSampleDoesNotInventInitialStableBaseline() {
        for (sign in listOf(1.0, -1.0)) {
            val frames = Frames()
            rightSequence.forEach { frames.sample(it * sign, frames = 1) }
            // Initial 0/5 and final 4/0 each provide only two neutral frames, not the required three.
            assertEquals("$sign: default baseline dwell must not be bypassed", 0, frames.counter.getRepCount())
            assertTrue(frames.completed.isEmpty())
        }
    }

    @Test fun preArmedSingleFrameSequenceStillNeedsThreeReturnFrames() {
        for (sign in listOf(1.0, -1.0)) {
            val frames = Frames()
            frames.sample(0.0) // establish the initial three-frame baseline, without changing policy
            rightSequence.forEach { frames.sample(it * sign, frames = 1) }
            assertEquals("$sign: final 4/0 is only two stable return frames", 0, frames.counter.getRepCount())
            frames.sample(0.0, frames = 1)
            assertEquals(1, frames.counter.getRepCount())
            assertEquals(if (sign < 0) BodySide.LEFT else BodySide.RIGHT, frames.completed.single().activeSide)
        }
    }

    @Test fun directionSwitchWithoutNeutralCannotStitchTwoSidesIntoARepetition() {
        val frames = Frames()
        listOf(0.0, 10.0, 20.0, -20.0, 0.0).forEach { frames.sample(it) }
        assertEquals(0, frames.counter.getRepCount())
        rightSequence.forEach { frames.sample(-it) }
        assertEquals(1, frames.counter.getRepCount())
        assertEquals(BodySide.LEFT, frames.completed.single().activeSide)
    }

    @Test fun prolongedUnavailableMeasurementDropsPendingBendWithoutInventingNeutral() {
        val frames = Frames()
        listOf(0.0, 10.0, 20.0).forEach { frames.sample(it) }
        frames.sample(null, frames = 9)
        frames.sample(0.0)
        assertEquals(0, frames.counter.getRepCount())
        assertTrue(frames.completed.isEmpty())
        rightSequence.forEach { frames.sample(-it) }
        assertEquals(1, frames.counter.getRepCount())
        assertEquals(BodySide.LEFT, frames.completed.single().activeSide)
    }
}
