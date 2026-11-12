package com.example.arptapp.domain.report

import com.example.arptapp.domain.classifier.ExerciseType
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ExpandedAiReportValidatorTest {
    private fun input(type: String): StructuredWorkoutAnalysis {
        val joints=when(type) {
            "SQUAT"->JointMetrics(leftKneeMinAngle=80.0)
            "SHOULDER_PRESS"->JointMetrics(leftElbowMaxAngle=170.0)
            "SIDE_BEND"->JointMetrics(peakLateralFlexionDeg=25.0,returnToNeutralDeviationDeg=1.0)
            else->JointMetrics(leftElbowMinAngle=60.0,leftElbowMaxAngle=170.0,leftElbowRomDeg=110.0)
        }
        val rep=StructuredRepAnalysis(1,type,"FRONT",1200,PhaseDurations(500,200,500),joints,AsymmetryMetrics(),
            emptyList(),emptyList(),emptyList(),ExerciseReportLanguage.defaultFeedback(type),80f,12,
            phaseModelVersion=2,activeSide=when(type){"BICEP_CURL"->BodySide.LEFT;"SIDE_BEND"->BodySide.RIGHT;else->null})
        return WorkoutAnalysisAggregator.aggregate(type,listOf(rep))
    }
    private fun report(type:String)=AiWorkoutReport(type,"1회가 기록되었습니다.",emptyList(),emptyList(),"측정 내용을 확인해 보세요.",
        "같은 촬영 조건을 확인해 보세요.",listOf(AiRepFeedback(1,"측정 범위가 기록되었습니다.","관찰 구간을 기록했습니다.",
            "편안하게 반복해 보세요.",listOf(when(type){"SQUAT"->"jointMetrics.leftKneeMinAngle";"SHOULDER_PRESS"->"jointMetrics.leftElbowMaxAngle";
                "SIDE_BEND"->"jointMetrics.peakLateralFlexionDeg";else->"jointMetrics.leftElbowRomDeg"}))))
    private fun decode(r:AiWorkoutReport,i:StructuredWorkoutAnalysis)=AiReportValidator.decode(Json.encodeToString(AiWorkoutReport.serializer(),r),i)
    @Test fun allSevenAcceptEvidenceAndRejectAllSixOtherExerciseNames() {
        for(type in ExerciseType.supported) {
            val input=input(type.name);val report=report(type.name)
            assertEquals(report,decode(report,input))
            for(other in ExerciseType.supported.filter {it!=type}) assertTrue(runCatching {
                decode(report.copy(summary="${other.displayName} 동작을 확인해 보세요."),input)
            }.isFailure)
        }
    }
    @Test fun sameNumberFromAnotherBodyPartIsNotEvidence() {
        val i=input("BICEP_CURL");val r=report("BICEP_CURL")
        assertTrue(runCatching {decode(r.copy(summary="무릎 각도 60도입니다."),i)}.isFailure)
        assertTrue(runCatching {decode(r.copy(summary="척추 부하를 측정했습니다."),i)}.isFailure)
    }
    @Test fun activeSideIsOnlyAnIdentityFactNotAnUnobservedSideOrPhaseIssue() {
        val i=input("BICEP_CURL");val r=report("BICEP_CURL")
        val valid=r.copy(repFeedback=r.repFeedback.map {it.copy(summary="사용 팔: 왼쪽",evidenceKeys=it.evidenceKeys+"activeSide")})
        assertEquals(valid,decode(valid,i))
        assertTrue(runCatching {decode(valid.copy(repFeedback=valid.repFeedback.map {it.copy(summary="사용 팔: 오른쪽")}),i)}.isFailure)
        assertTrue(runCatching {decode(valid.copy(repFeedback=valid.repFeedback.map {it.copy(summary="팔을 굽히는 구간에서 왼쪽 상완 변화가 관찰되었습니다.")}),i)}.isFailure)
    }
    @Test fun inventedNumbersDiagnosisAndUnreferencedMetricsFailClosed() {
        for(type in ExerciseType.supported) {
            val i=input(type.name);val r=report(type.name)
            for(text in listOf("179.9도가 측정되었습니다.","정상 자세입니다.","부상 위험이 높습니다."))
                assertTrue(runCatching {decode(r.copy(summary=text),i)}.isFailure)
            assertTrue(runCatching {decode(r.copy(repFeedback=r.repFeedback.map {it.copy(evidenceKeys=listOf("jointMetrics.notMeasured"))}),i)}.isFailure)
        }
    }
}
