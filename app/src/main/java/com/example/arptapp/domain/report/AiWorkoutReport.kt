package com.example.arptapp.domain.report

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class AiRepFeedback(val repNumber: Int, val summary: String, val positive: String,
    val improvement: String, val evidenceKeys: List<String>)
@Serializable data class AiWorkoutReport(val exerciseType: String, val summary: String,
    val strengths: List<String>, val improvements: List<String>, val priority: String,
    val nextWorkoutGoal: String, val repFeedback: List<AiRepFeedback>)

/** Strict syntactic + evidence-reference validation. Free prose still requires human review. */
object AiReportValidator {
    private val json = Json { ignoreUnknownKeys = false }
    private val prohibited = Regex("진단|치료|질환|부상|완벽|완전히 안전|diagnos|cure|injur|perfect", RegexOption.IGNORE_CASE)
    fun evidenceKeys(rep: StructuredRepAnalysis): Set<String> = buildSet {
        if (rep.score != null) add("score")
        if (rep.totalDurationMs != null) add("totalDurationMs")
        rep.strengths.forEach { add("strength:$it") }
        rep.errorEvents.forEach { add("issue:${it.type.name}"); add(DirectionalClaimValidator.key(it)) }
        val j = rep.jointMetrics
        listOf("leftKneeMinAngle" to j.leftKneeMinAngle, "rightKneeMinAngle" to j.rightKneeMinAngle,
            "leftHipMinAngle" to j.leftHipMinAngle, "rightHipMinAngle" to j.rightHipMinAngle,
            "leftElbowMaxAngle" to j.leftElbowMaxAngle, "rightElbowMaxAngle" to j.rightElbowMaxAngle,
            "torsoLeanMaxDeg" to j.torsoLeanMaxDeg).forEach { (k, v) -> if (v != null) add("jointMetrics.$k") }
        val a = rep.asymmetryMetrics
        listOf("kneeAngleDifferenceDeg" to a.kneeAngleDifferenceDeg, "hipAngleDifferenceDeg" to a.hipAngleDifferenceDeg,
            "elbowAngleDifferenceDeg" to a.elbowAngleDifferenceDeg).forEach { (k, v) -> if (v != null) add("asymmetryMetrics.$k") }
        val p = rep.phaseDurations
        listOf("descentMs" to p.descentMs, "bottomMs" to p.bottomMs, "ascentMs" to p.ascentMs)
            .forEach { (k, v) -> if (v != null) add("phaseDurations.$k") }
    }
    fun decode(raw: String, input: StructuredWorkoutAnalysis): AiWorkoutReport {
        require(raw.toByteArray(Charsets.UTF_8).size <= 32_768)
        val report = json.decodeFromString<AiWorkoutReport>(raw)
        require(report.exerciseType == input.exerciseType)
        val texts = listOf(report.summary, report.priority, report.nextWorkoutGoal) + report.strengths +
            report.improvements + report.repFeedback.flatMap { listOf(it.summary, it.positive, it.improvement) }
        require(report.strengths.size <= 5 && report.improvements.size <= 5)
        require(texts.all { it.isNotBlank() && it.length <= 500 && !prohibited.containsMatchIn(it) &&
            it.none { c -> c.isISOControl() && c != '\n' } })
        val wrongExercise = if (input.exerciseType == "SQUAT") Regex("숄더.?프레스|shoulder.?press", RegexOption.IGNORE_CASE)
            else Regex("스쿼트|squat", RegexOption.IGNORE_CASE)
        require(texts.none { wrongExercise.containsMatchIn(it) })
        val reps = input.repAnalyses.associateBy { it.repNumber }
        DirectionalClaimValidator.validate(listOf(report.summary, report.priority, report.nextWorkoutGoal) +
            report.strengths + report.improvements, input.repAnalyses)
        MeasuredClaimValidator.validate(listOf(report.summary, report.priority, report.nextWorkoutGoal) +
            report.strengths + report.improvements, input.repAnalyses,
            totals = listOf(input.totalReps.toDouble(), input.omittedReps.toDouble()) +
                listOfNotNull(input.bestRepNumber, input.weakestRepNumber).map { it.toDouble() }, averageScore = input.averageScore)
        require(report.repFeedback.map { it.repNumber }.toSet() == reps.keys && report.repFeedback.size == reps.size)
        report.repFeedback.forEach { feedback ->
            require(feedback.evidenceKeys.size in 1..12 && feedback.evidenceKeys.distinct().size == feedback.evidenceKeys.size)
            require(evidenceKeys(reps.getValue(feedback.repNumber)).containsAll(feedback.evidenceKeys))
            DirectionalClaimValidator.validate(listOf(feedback.summary, feedback.positive, feedback.improvement),
                listOf(reps.getValue(feedback.repNumber)), feedback.evidenceKeys.toSet())
            MeasuredClaimValidator.validate(listOf(feedback.summary, feedback.positive, feedback.improvement),
                listOf(reps.getValue(feedback.repNumber)), feedback.evidenceKeys.toSet())
        }
        return report
    }
}

sealed interface AiReportState {
    data object Idle : AiReportState
    data object Loading : AiReportState
    data class Success(val report: AiWorkoutReport) : AiReportState
    data class Error(val message: String) : AiReportState
    data class Unavailable(val message: String) : AiReportState
}
