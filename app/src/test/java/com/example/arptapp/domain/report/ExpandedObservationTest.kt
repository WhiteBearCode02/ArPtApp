package com.example.arptapp.domain.report

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.counter.ExerciseCounter
import com.example.arptapp.domain.classifier.ExerciseType
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExpandedObservationTest {
    @Test fun eachNewExerciseProducesOnlyItsMeasuredEventWithoutRejectingTheRep() {
        for(type in ExerciseType.supported.drop(2).map {it.name}) {
            val engine=RepAnalysisEngine();val counter=ExerciseCounter();var time=0L
            val side=type in setOf("PUSH_UP","BENT_OVER_ROW")
            val bend=type=="SIDE_BEND";val tri=type=="TRICEPS_EXTENSION";val curl=type=="BICEP_CURL"
            val baseline=if(bend)0.0 else if(tri)60.0 else 170.0
            val turn=if(bend)25.0 else if(tri)170.0 else 60.0
            var result:StructuredRepAnalysis?=null
            val steps=listOf(baseline,(baseline+turn)/2,turn,(baseline+turn)/2,if(bend)4.0 else baseline)
            steps.forEachIndexed { index,angle -> repeat(3) {
                val left=if(!bend)angle else null
                val right=if(curl)170.0 else if(!side&&!bend)angle else null
                engine.observe(RepFrameFeatures(time,type,if(side)CameraView.SIDE else CameraView.FRONT,angle,
                    leftElbow=left,rightElbow=right,visibleSide=if(side)BodySide.LEFT else null,
                    upperArmLeft=if(curl||tri) if(index in 1..3)21.0 else 0.0 else null,
                    upperArmRight=if(tri)0.0 else null,
                    torsoInclination=if(type=="BENT_OVER_ROW") if(index in 1..3)51.0 else 30.0 else null,
                    bodyLineDeviation=if(type=="PUSH_UP") if(index in 1..3)21.0 else 0.0 else null,
                    lateralFlexion=if(bend)angle else null))
                counter.processMeasurement(type,angle,time,left,right).forEach { c ->
                    result=RepScoreCalculator.score(requireNotNull(engine.complete(c.repNumber,activeSide=c.activeSide,motionStartedAtMs=c.motionStartedAtMs)))
                };time+=150
            } }
            val rep=requireNotNull(result);assertEquals(1,counter.getRepCount());assertFalse("$type events",rep.errorEvents.isEmpty())
            assertTrue(rep.errorEvents.all {it.occurrenceFrames>=3 && it.phase!=null})
            assertFalse(rep.localPriorityFeedback.contains("부상"));assertFalse(rep.localPriorityFeedback.contains("정상"))
            val root=File(requireNotNull(System.getProperty("user.dir"))).let { if(File(it,"settings.gradle.kts").isFile)it else it.parentFile }
            val directory=File(root,"build/exercise-seven-contract-fixtures");check(directory.isDirectory||directory.mkdirs())
            File(directory,"$type-event.json").writeText(Json { encodeDefaults=true;explicitNulls=true }.encodeToString(
                StructuredWorkoutAnalysis.serializer(),WorkoutAnalysisAggregator.forNetwork(WorkoutAnalysisAggregator.aggregate(type,listOf(rep)))))
        }
    }
    @Test fun completingLeftCurlDoesNotDiscardTheRightBuffer() {
        val engine=RepAnalysisEngine();var time=0L
        fun feed(l:Double,r:Double) {engine.observe(RepFrameFeatures(time,"BICEP_CURL",CameraView.FRONT,minOf(l,r),leftElbow=l,rightElbow=r));time+=150}
        for(a in listOf(170.0,120.0,60.0,120.0,170.0)) repeat(3){feed(a,if(a==170.0)170.0 else 120.0)}
        val left=requireNotNull(engine.complete(1,activeSide=BodySide.LEFT));assertEquals(BodySide.LEFT,left.activeSide)
        for(a in listOf(60.0,120.0,170.0))repeat(3){feed(170.0,a)}
        val right=requireNotNull(engine.complete(2,activeSide=BodySide.RIGHT));assertEquals(BodySide.RIGHT,right.activeSide)
        assertNull(right.jointMetrics.leftElbowRomDeg);assertNotNull(right.jointMetrics.rightElbowRomDeg)
        assertNotNull(right.asymmetryMetrics.contralateralRomDifferenceDeg)
    }
    @Test fun newScoreExcludesUnmeasuredWeightsRatherThanGivingThemZero() {
        val r=StructuredRepAnalysis(1,"PUSH_UP","SIDE",1500,PhaseDurations(600,100,800),
            JointMetrics(leftElbowMinAngle=60.0,leftElbowMaxAngle=170.0,leftElbowRomDeg=110.0),AsymmetryMetrics(),
            emptyList(),emptyList(),emptyList(),ExerciseReportLanguage.defaultFeedback("PUSH_UP"),observedFrames=15,phaseModelVersion=2)
        val scored=RepScoreCalculator.calculate(r)
        assertEquals(55f,scored.availableWeight,0f)
        assertNull(scored.components.single {it.component==RepScoreComponent.SYMMETRY}.earned)
        assertNull(scored.components.single {it.component==RepScoreComponent.STABILITY}.earned)
        assertEquals(100f,scored.normalizedScore!!,0f)
    }
}
