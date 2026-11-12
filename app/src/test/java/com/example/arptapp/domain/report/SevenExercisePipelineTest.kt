package com.example.arptapp.domain.report

import com.example.arptapp.data.StructuredReportCodec
import com.example.arptapp.data.RepAnalysisCodec
import com.example.arptapp.data.history.WorkoutPayloadCodec
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.counter.ExerciseCounter
import com.example.arptapp.presentation.report.RepReportUiMapper
import com.example.arptapp.presentation.report.WorkoutReportData
import kotlinx.serialization.json.Json
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Synthetic scalar integration fixtures, NOT model Golden/clinical/real-camera evidence. */
@RunWith(Parameterized::class)
class SevenExercisePipelineTest(private val exercise: String) {
    companion object { @JvmStatic @Parameterized.Parameters(name="{0}") fun types()=ExerciseType.supported.map {arrayOf(it.name)} }
    private fun completed(): StructuredRepAnalysis {
        val counter=ExerciseCounter();val engine=RepAnalysisEngine(); var time=0L
        val baseline=when(exercise){"SIDE_BEND"->0.0;"TRICEPS_EXTENSION"->60.0;else->170.0}
        val turn=when(exercise){"SIDE_BEND"->25.0;"TRICEPS_EXTENSION"->170.0;else->60.0}
        var rep: StructuredRepAnalysis?=null
        for(angle in listOf(baseline,(baseline+turn)/2,turn,(baseline+turn)/2,baseline)) repeat(3){
            val curl=exercise=="BICEP_CURL";val squat=exercise=="SQUAT";val bend=exercise=="SIDE_BEND"
            val left=if(!squat&&!bend)angle else null
            val right=if(curl)170.0 else left
            engine.observe(RepFrameFeatures(time,exercise,CameraView.FRONT,angle,
                leftKnee=if(squat)angle else null,rightKnee=if(squat)angle+2 else null,
                leftElbow=left,rightElbow=right,upperArmLeft=if(curl||exercise=="TRICEPS_EXTENSION")0.0 else null,
                upperArmRight=if(exercise=="TRICEPS_EXTENSION")0.0 else null,
                lateralFlexion=if(bend)angle else null))
            counter.processMeasurement(exercise,angle,time,left,right).forEach { c ->
                rep=RepScoreCalculator.score(requireNotNull(engine.complete(c.repNumber,activeSide=c.activeSide,motionStartedAtMs=c.motionStartedAtMs)))
            }
            time+=150
        }
        return requireNotNull(rep)
    }
    @Test fun measuredRepHasScorePhaseAndExerciseSpecificUiWithoutRawIdentifiers() {
        val rep=completed();assertNotNull(rep.score);assertTrue(rep.observedFrames>=3)
        val card=RepReportUiMapper.map(emptyList(),listOf(rep)).single()
        assertFalse(card.metricGroups.isEmpty());assertNotNull(card.priorityTip)
        assertTrue(card.metricGroups.any {it.title=="동작 시간"})
        assertTrue(card.metricGroups.flatMap {it.rows}.none {it.value=="측정 불가"})
        if(exercise in setOf("BICEP_CURL","SIDE_BEND")) assertNotNull(rep.activeSide)
    }
    @Test fun roomRecordAndHistoryRoundTripPreserveNullableV2Facts() {
        val rep=completed();val structured=WorkoutAnalysisAggregator.aggregate(exercise,listOf(rep))
        val analyses=RepReportAnalyzer().analyze(listOf(com.example.arptapp.model.RepRecord(1,90.0,0f)),exercise,listOf(rep))
        val encoded=StructuredReportCodec.encode(analyses,structured)
        val data=WorkoutReportData.create(exerciseType=exercise,totalCount=1,averageScore=rep.score!!,
            analysesJson=encoded,workoutDate="2026-11-12 12:00",durationSeconds=3,cloudEligible=false)
        val record=data.toRecord("fixture-owner")
        val loaded=WorkoutReportData.fromRecord(record)
        assertEquals(structured,StructuredReportCodec.decode(loaded.analysesJson))
        assertEquals(analyses,RepAnalysisCodec.decode(loaded.analysesJson))
        assertEquals(encoded,WorkoutPayloadCodec.analyses(encoded))
        assertEquals(2,structured.schemaVersion)
        assertFalse(encoded.contains("landmarks"));assertFalse(encoded.contains("fixture-owner"))
    }
    @Test fun partialUnavailableRepIsNotZeroOrGradedFromLegacyAngle() {
        val r=StructuredRepAnalysis(1,exercise,"UNKNOWN",null,PhaseDurations(),JointMetrics(),AsymmetryMetrics(),
            emptyList(),emptyList(),emptyList(),ExerciseReportLanguage.defaultFeedback(exercise),observedFrames=3,partialCapture=true,phaseModelVersion=2)
        val scored=RepScoreCalculator.score(r);assertNull(scored.score)
        assertTrue(scored.localScore!!.components.all {it.earned==null})
        val card=RepReportUiMapper.map(emptyList(),listOf(scored)).single()
        assertNull(card.score);assertTrue(card.metricGroups.isEmpty());assertTrue(card.notes.any {it.contains(" 일부")})
    }
    @Test fun existingRemoteMapperPreservesSevenExerciseNamesAndStructuredDetailsWithoutLocalDiagnostics() {
        val rep = completed()
        val structured = WorkoutAnalysisAggregator.aggregate(exercise, listOf(rep))
        val encoded = StructuredReportCodec.encode(emptyList(), structured)
        val row = WorkoutReportData.create(exerciseType = ExerciseType.fromName(exercise)!!.displayName,
            totalCount = 1, averageScore = rep.score!!, analysesJson = encoded,
            workoutDate = "2026-11-12 12:00", durationSeconds = 3, cloudEligible = true)
            .forSaving("fixture-session", "fixture-owner", "2026-11-12T03:00:00Z").toRecord("fixture-owner")
            .copy(yoloDiagnosticsJson = "local-only")
        val upload = com.example.arptapp.data.remote.WorkoutRemoteMapper.upload(row)
        assertEquals(exercise, upload.exerciseType)
        assertEquals(structured, StructuredReportCodec.decode(upload.reportJson))
        val restored = com.example.arptapp.data.remote.WorkoutRemoteMapper.summary(
            com.example.arptapp.data.remote.RemoteExerciseSession(upload.id, upload.userId, upload.exerciseType,
                upload.totalReps, upload.averageScore, upload.feedbackMessage, upload.durationSeconds,
                upload.burnedCalories, upload.createdAt, upload.workoutDate, upload.exactAverageScore), "fixture-owner")
        assertEquals(row.exerciseType, restored.exerciseType)
        assertNull(restored.yoloDiagnosticsJson)
        assertFalse(upload.reportJson.contains("local-only"))
    }
    @Test fun writesActualAndroidWireFixtureForDependencyFreeServerContractCheck() {
        val input=WorkoutAnalysisAggregator.forNetwork(WorkoutAnalysisAggregator.aggregate(exercise,listOf(completed())))
        val working=File(requireNotNull(System.getProperty("user.dir")))
        val root=if(File(working,"settings.gradle.kts").isFile)working else working.parentFile
        val directory=File(root,"build/exercise-seven-contract-fixtures");check(directory.isDirectory||directory.mkdirs())
        File(directory,"$exercise.json").writeText(Json {encodeDefaults=true;explicitNulls=true}
            .encodeToString(StructuredWorkoutAnalysis.serializer(),input),Charsets.UTF_8)
    }
    @Test fun writesMissingCaptureWireFixtureWithoutFabricatedMetricsOrScore() {
        // Same zero-observation placeholder as MainViewModel.addCompletedRep when capture is absent.
        val rep = RepScoreCalculator.score(StructuredRepAnalysis(1, exercise, "FRONT", null,
            PhaseDurations(), JointMetrics(), AsymmetryMetrics(), emptyList(), emptyList(), emptyList(),
            "이 회차의 측정 근거가 부족합니다.", partialCapture = true, phaseModelVersion = 2))
        assertNull(rep.score)
        val input = WorkoutAnalysisAggregator.forNetwork(WorkoutAnalysisAggregator.aggregate(exercise, listOf(rep)))
        val working = File(requireNotNull(System.getProperty("user.dir")))
        val root = if (File(working, "settings.gradle.kts").isFile) working else working.parentFile
        val directory = File(root, "build/exercise-seven-contract-fixtures"); check(directory.isDirectory || directory.mkdirs())
        File(directory, "$exercise-partial.json").writeText(Json { encodeDefaults = true; explicitNulls = true }
            .encodeToString(StructuredWorkoutAnalysis.serializer(), input), Charsets.UTF_8)
    }
}

class SevenExerciseCompatibilityTest {
    @Test fun oldV1SquatAndPressDecodeWithoutInventedNewMetrics() {
        for(type in listOf("SQUAT","SHOULDER_PRESS")) {
            val r=StructuredRepAnalysis(1,type,"FRONT",null,PhaseDurations(),JointMetrics(),AsymmetryMetrics(),
                emptyList(),emptyList(),emptyList(),LocalRepFeedback.forIssue(null))
            val v1=StructuredWorkoutAnalysis(schemaVersion=1,exerciseType=type,totalReps=1,repAnalyses=listOf(r))
            val loaded=requireNotNull(StructuredReportCodec.decode(StructuredReportCodec.encode(emptyList(),v1)))
            assertEquals(v1,loaded);assertNull(loaded.repAnalyses.single().jointMetrics.leftElbowRomDeg)
            assertNull(loaded.repAnalyses.single().activeSide)
        }
    }
    @Test fun v1NeverAcceptsV2MetricsOrNewExerciseNames() {
        val r=StructuredRepAnalysis(1,"BICEP_CURL","FRONT",null,PhaseDurations(),JointMetrics(leftElbowRomDeg=90.0),
            AsymmetryMetrics(),emptyList(),emptyList(),emptyList(),"",activeSide=BodySide.LEFT)
        assertNull(StructuredReportCodec.decode(StructuredReportCodec.encode(emptyList(),
            StructuredWorkoutAnalysis(schemaVersion=1,exerciseType="BICEP_CURL",totalReps=1,repAnalyses=listOf(r)))))
    }
    @Test fun newExerciseNeverUsesOldUnstructuredAngleGrading() {
        for(type in ExerciseType.supported.drop(2)) assertTrue(RepReportAnalyzer().analyze(
            listOf(com.example.arptapp.model.RepRecord(1,180.0,0f)),type.name).isEmpty())
    }
}
