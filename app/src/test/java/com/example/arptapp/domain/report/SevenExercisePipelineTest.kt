package com.example.arptapp.domain.report

import com.example.arptapp.data.StructuredReportCodec
import com.example.arptapp.data.RepAnalysisCodec
import com.example.arptapp.data.history.WorkoutPayloadCodec
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.analyzer.PoseAngleExtractor
import com.example.arptapp.domain.analyzer.SevenPoseFixtures
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.counter.ExerciseCounter
import com.example.arptapp.presentation.report.RepReportUiMapper
import com.example.arptapp.presentation.report.WorkoutReportData
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.tan
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

/** Signed landmark-to-local-report regression. No model inference, cloud call or camera claim. */
class SideBendSignedPipelineTest {
    private val sequence = listOf(0.0, 5.0, 10.0, 16.0, 20.0, 25.0, 20.0, 10.0, 4.0, 0.0)

    private fun assertSignedReport(sign: Double, side: BodySide, sideLabel: String) {
        val counter = ExerciseCounter()
        val engine = RepAnalysisEngine()
        val completed = mutableListOf<StructuredRepAnalysis>()
        var timestamp = 0L
        for (sample in sequence) repeat(3) {
            // Fixture shoulder-to-hip vertical span is .4; translate both shoulders equally to
            // form the requested signed image-plane angle, keeping visibility and both axes valid.
            val points = SevenPoseFixtures.points((tan(Math.toRadians(sample * sign)) * .4).toFloat())
            val measurement = PoseAngleExtractor.measurePose("SIDE_BEND", SevenPoseFixtures.pose(points), CameraView.FRONT)
            assertTrue(measurement.isEvaluable)
            assertEquals(sample * sign, requireNotNull(measurement.valueDeg), .001)
            val features = RepFrameFeatureExtractor.extract(points, "SIDE_BEND", CameraView.FRONT,
                1.0, timestamp, measurement.valueDeg)
            assertEquals(measurement.valueDeg, features.lateralFlexion)
            assertNull(features.leftKnee)
            assertNull(features.rightKnee)
            engine.observe(features)
            counter.processMeasurement("SIDE_BEND", measurement.valueDeg, timestamp).forEach { result ->
                completed += RepScoreCalculator.score(requireNotNull(engine.complete(result.repNumber,
                    activeSide = result.activeSide, motionStartedAtMs = result.motionStartedAtMs)))
            }
            timestamp += 150L // default dwell and 600 ms minimum remain unchanged
        }

        assertEquals(1, counter.getRepCount())
        val rep = completed.single()
        assertEquals("SIDE_BEND", rep.exerciseType)
        assertEquals(side, rep.activeSide)
        assertEquals(25.0, requireNotNull(rep.jointMetrics.peakLateralFlexionDeg), .001)
        assertNull(rep.jointMetrics.leftKneeMinAngle)
        assertNull(rep.jointMetrics.rightKneeMinAngle)
        assertEquals(ExerciseReportLanguage.defaultFeedback("SIDE_BEND"), rep.localPriorityFeedback)

        val card = RepReportUiMapper.map(emptyList(), listOf(rep)).single()
        assertEquals(rep.localPriorityFeedback, card.priorityTip)
        assertTrue(card.metricGroups.any { it.title == "몸통 측굴 관찰" })
        assertEquals(sideLabel, card.metricGroups.single { it.title == "움직인 방향" }.rows.single().value)
        assertTrue(card.metricGroups.flatMap { it.rows }.none { it.label.contains("무릎") })
        val phaseLabels = card.metricGroups.single { it.title == "동작 시간" }.rows.map { it.label }
        assertTrue(phaseLabels.contains("옆으로 기울이는 구간"))
        assertTrue(phaseLabels.contains("중앙으로 돌아오는 구간"))
        assertFalse(phaseLabels.contains("내려가는 구간"))

        val structured = WorkoutAnalysisAggregator.aggregate("SIDE_BEND", listOf(rep))
        val encoded = StructuredReportCodec.encode(emptyList(), structured)
        val report = WorkoutReportData.create(exerciseType = ExerciseType.SIDE_BEND.displayName,
            totalCount = 1, averageScore = requireNotNull(rep.score), analysesJson = encoded,
            feedbackMessage = rep.localPriorityFeedback, workoutDate = "2026-11-15 12:00",
            durationSeconds = 4, cloudEligible = false)
        val restored = WorkoutReportData.fromRecord(report.toRecord("fixture-owner"))
        assertEquals(ExerciseType.SIDE_BEND.displayName, restored.exerciseType)
        assertEquals(1, restored.totalCount)
        assertEquals(rep.localPriorityFeedback, restored.feedbackMessage)
        assertEquals(structured, StructuredReportCodec.decode(restored.analysesJson))
    }

    @Test fun positiveLandmarkSequencePreservesRightSideFeedbackAndReportLabel() {
        assertSignedReport(1.0, BodySide.RIGHT, "오른쪽")
    }

    @Test fun negativeLandmarkSequencePreservesLeftSideFeedbackAndReportLabel() {
        assertSignedReport(-1.0, BodySide.LEFT, "왼쪽")
    }

    @Test fun anyRequiredShoulderOrHipOcclusionStaysUnavailableRatherThanNeutral() {
        for (joint in listOf(11, 12, 23, 24)) {
            val points = SevenPoseFixtures.points(.1f).toMutableList()
            points[joint] = points[joint].copy(visibility = .64f)
            val measurement = PoseAngleExtractor.measurePose("SIDE_BEND", SevenPoseFixtures.pose(points), CameraView.FRONT)
            assertFalse("$joint must gate signed lateral measurement", measurement.isEvaluable)
            assertNull(measurement.valueDeg)
            val features = RepFrameFeatureExtractor.extract(points, "SIDE_BEND", CameraView.FRONT,
                1.0, 0L, measurement.valueDeg)
            assertNull(features.representativeAngle)
            assertNull(features.lateralFlexion)
        }
    }
}
