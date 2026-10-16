package com.example.arptapp.presentation.report

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.arptapp.R
import com.example.arptapp.data.StructuredReportCodec
import com.example.arptapp.data.remote.AiCoachingPolicy
import com.example.arptapp.databinding.ActivityReportBinding
import com.example.arptapp.domain.report.AiReportState
import com.example.arptapp.domain.report.StructuredWorkoutAnalysis
import com.example.arptapp.model.RepAnalysis
import kotlinx.coroutines.launch

/** Presentation only. History/Room contracts, measurement decisions and score formulas remain unchanged. */
class ReportActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReportBinding
    private val aiViewModel: AiReportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ReportWindowInsets.apply(this, binding.root)

        // Read the same saved envelope as before; malformed/legacy structured data stays optional.
        val report = ReportNavigation.read(intent)
        val analyses = ReportNavigation.analyses(report)
        val structured = StructuredReportCodec.decode(report.analysesJson)
        setupSummary(report, analyses, structured)
        setupRepDetails(analyses, structured)
        setupImprovementSummary(structured, report.feedbackMessage)
        YoloSessionDiagnosticsRenderer(layoutInflater).render(binding, report.yoloDiagnosticsJson)
        setupAiExplanation(structured)
        binding.btnClose.setOnClickListener { finish() }
    }

    private fun setupSummary(report: WorkoutReportData, analyses: List<RepAnalysis>, structured: StructuredWorkoutAnalysis?) {
        binding.tvExerciseType.text = when (report.exerciseType) {
            "SQUAT" -> "스쿼트"
            "SHOULDER_PRESS" -> "숄더 프레스"
            else -> report.exerciseType
        }
        binding.tvWorkoutDate.text = report.workoutDate.ifBlank { "운동 일시 정보 없음" }
        binding.tvTotalCount.text = getString(R.string.report_summary_count, report.totalCount)
        binding.tvDuration.text = getString(R.string.report_summary_duration,
            WorkoutReportPresentation.formatDuration(report.durationSeconds))
        binding.tvCalories.text = report.burnedCalories?.let { getString(R.string.report_summary_calories, it) }
            ?: getString(R.string.report_summary_calories_missing)

        // Preserve the existing legacy display rule; structured-only records use their stored average, not a new formula.
        val displayScore = WorkoutReportPresentation.displayScore(report, analyses)
            ?: structured?.averageScore?.takeIf { score ->
                score.isFinite() && score in 0f..100f && structured.repAnalyses.any { it.score != null }
            }
        binding.tvAvgScore.text = displayScore?.let { getString(R.string.report_summary_average, it.toInt()) }
            ?: getString(R.string.report_summary_average_missing)
        binding.tvFeedback.text = report.feedbackMessage.ifBlank {
            if (displayScore == null) "이 기록에는 점수로 표시할 회차별 측정이 없습니다. 저장된 관찰 내용이 있으면 아래에서 확인할 수 있습니다."
            else WorkoutReportPresentation.feedback(displayScore)
        }
    }

    private fun setupRepDetails(analyses: List<RepAnalysis>, structured: StructuredWorkoutAnalysis?) {
        // Card existence is independent of chart scores: an unscored measured rep must not disappear.
        val cards = RepReportUiMapper.map(analyses, structured?.repAnalyses.orEmpty())
        RepAnalysisCardRenderer(layoutInflater).render(binding.repAnalysisContainer, cards)
        binding.repAnalysisContainer.visibility = visible(cards.isNotEmpty())
        binding.tvRepDetailsHeader.visibility = visible(cards.isNotEmpty())
        binding.tvRepAnalysisNote.visibility = visible(cards.isNotEmpty())
        binding.tvRepEmpty.visibility = visible(cards.isEmpty())
        ViewCompat.setAccessibilityHeading(binding.tvRepDetailsHeader, true)

        val chartScores = cards.mapNotNull { card -> card.score?.let { RepAnalysis(card.repNumber, it, "") } }
        binding.chart.visibility = visible(chartScores.isNotEmpty())
        binding.tvChartEmpty.visibility = visible(chartScores.isEmpty())
        if (chartScores.isNotEmpty()) ReportChartRenderer.render(binding.chart, chartScores)
    }

    private fun setupImprovementSummary(structured: StructuredWorkoutAnalysis?, feedback: String) {
        val summary = RepReportUiMapper.sessionSummary(structured, feedback)
        binding.improvementSummarySection.visibility = visible(summary.isNotEmpty())
        binding.tvImprovementSummary.text = summary.joinToString("\n\n") { "• $it" }
    }

    /** No entry-time generate call. Both user actions re-check policy, data and duplicate-request state. */
    private fun setupAiExplanation(structured: StructuredWorkoutAnalysis?) {
        val hasMeasuredReps = structured?.repAnalyses?.isNotEmpty() == true
        binding.tvAiStatus.setText(when {
            !AiCoachingPolicy.ENABLED -> R.string.report_ai_disabled
            !hasMeasuredReps -> R.string.report_ai_unmeasured
            else -> R.string.report_ai_available
        })
        binding.btnGenerateAi.setText(if (AiCoachingPolicy.ENABLED) R.string.report_ai_show else R.string.report_ai_preparing)
        binding.btnGenerateAi.isEnabled = canGenerateAi(structured)
        binding.btnGenerateAi.setOnClickListener {
            if (!canGenerateAi(structured) || structured == null) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("AI 설명 생성")
                .setMessage("회차별 관절 각도와 관찰 요약을 서버 및 승인된 LLM 제공자에 전달합니다. 영상·관절 좌표·이메일은 전달하지 않습니다. AI 설명은 참고용이며 로컬 분석을 대체하지 않습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("생성") { _, _ ->
                    if (canGenerateAi(structured)) aiViewModel.generate(structured)
                }.show()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                aiViewModel.state.collect { state ->
                    binding.aiProgress.visibility = visible(state is AiReportState.Loading)
                    binding.btnGenerateAi.isEnabled = canGenerateAi(structured)
                    when (state) {
                        AiReportState.Idle -> Unit
                        AiReportState.Loading -> binding.tvAiStatus.text = "AI 설명을 생성하고 있습니다. 로컬 분석은 계속 확인할 수 있습니다."
                        is AiReportState.Unavailable -> binding.tvAiStatus.text = state.message
                        is AiReportState.Error -> binding.tvAiStatus.text = state.message
                        is AiReportState.Success -> {
                            binding.tvAiStatus.text = "AI가 로컬 측정을 풀어 설명했습니다. 수치와 관찰 내용은 로컬 자세 분석을 우선해 주세요."
                            binding.tvAiReport.visibility = View.VISIBLE
                            binding.tvAiReport.text = with(state.report) {
                                "$summary\n\n관찰된 강점\n${strengths.joinToString("\n")}\n\n확인할 점\n${improvements.joinToString("\n")}\n\n우선 코칭\n$priority\n\n다음 운동 목표\n$nextWorkoutGoal\n\n" +
                                    repFeedback.joinToString("\n\n") { "${it.repNumber}회\n${it.summary}\n${it.positive}\n${it.improvement}" }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun canGenerateAi(structured: StructuredWorkoutAnalysis?): Boolean {
        val state = aiViewModel.state.value
        return ReportAiUiPolicy.canRequest(AiCoachingPolicy.ENABLED,
            structured?.repAnalyses?.isNotEmpty() == true,
            loading = state is AiReportState.Loading, completed = state is AiReportState.Success)
    }

    private fun visible(show: Boolean): Int = if (show) View.VISIBLE else View.GONE
}
