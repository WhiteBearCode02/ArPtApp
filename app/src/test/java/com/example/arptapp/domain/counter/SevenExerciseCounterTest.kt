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
    @Test fun visibilityLossInvalidatesIncompleteCycle() {
        hold(baseline); hold((baseline + turn) / 2); hold(turn)
        val alreadyCompleted = counter.getRepCount()
        feed(null); hold(baseline, 5)
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
