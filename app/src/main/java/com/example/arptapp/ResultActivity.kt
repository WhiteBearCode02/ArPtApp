package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.data.AppDatabase
import com.example.arptapp.data.ExerciseRecord
import com.example.arptapp.databinding.ActivityResultBinding
import com.example.arptapp.presentation.report.ReportActivity
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

/**
 * [마스터 솔루션]
 * 기존 모든 기능(DB 저장, 칼로리, 시간 포맷)을 유지하며 리포트 버튼 오류를 수정했습니다.
 */
class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. DashboardActivity에서 전달한 데이터 수집 (리포트용 데이터 포함)
        val finalCount = intent.getIntExtra("TOTAL_COUNT", 0)
        val exerciseTimeInSeconds = intent.getLongExtra("EXERCISE_TIME", 0L)
        val scores = intent.getFloatArrayExtra("SCORES")
        val avgScore = intent.getFloatExtra("AVG_SCORE", 0f)
        val exerciseType = intent.getStringExtra("EXERCISE_TYPE") ?: "스쿼트"

        // 2. 창업자님의 원본 데이터 가공 로직 (유지)
        val formattedTime = formatElapsedTime(exerciseTimeInSeconds)
        val burnedCalories = calculateCalories(finalCount)
        val workoutDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val feedbackMessage = intent.getStringExtra("FEEDBACK_MESSAGE")
            ?.takeIf { it.isNotBlank() }
            ?: feedbackForScore(avgScore)

        // 3. UI 업데이트 및 DB 저장 (유지)
        displayExerciseSummary(finalCount, formattedTime, burnedCalories)
        saveWorkoutToDatabase(
            count = finalCount,
            duration = exerciseTimeInSeconds,
            calories = burnedCalories,
            workoutDate = workoutDate,
            exerciseType = exerciseType,
            averageScore = avgScore,
            scores = scores ?: floatArrayOf(),
            feedbackMessage = feedbackMessage
        )

        // 4. [수정 포인트] 상세 리포트 보기 버튼 설정
        // returnToHome() 내부에 있던 것을 onCreate로 꺼내어 즉시 클릭 가능하게 했습니다.
        binding.btnViewReport.setOnClickListener {
            val reportIntent = Intent(this, ReportActivity::class.java).apply {
                putExtra("EXERCISE_TYPE", exerciseType)
                putExtra("TOTAL_COUNT", finalCount)
                putExtra("AVG_SCORE", avgScore)
                putExtra("SCORES", scores)
                putExtra("WORKOUT_DATE", workoutDate)
                putExtra("EXERCISE_TIME", exerciseTimeInSeconds)
                putExtra("BURNED_CALORIES", burnedCalories)
                putExtra("FEEDBACK_MESSAGE", feedbackMessage)
            }
            startActivity(reportIntent)
        }

        // 5. 메인 허브로 돌아가기 버튼 설정
        binding.btnBackToMain.setOnClickListener {
            returnToHome()
        }
    }

    // === 창업자님의 원본 기능들 (절대 삭제하지 않음) ===

    private fun saveWorkoutToDatabase(
        count: Int,
        duration: Long,
        calories: Double,
        workoutDate: String,
        exerciseType: String,
        averageScore: Float,
        scores: FloatArray,
        feedbackMessage: String
    ) {
        val record = ExerciseRecord(
            date = workoutDate,
            totalCount = count,
            duration = duration,
            burnedCalories = calories,
            exerciseType = exerciseType,
            averageScore = averageScore,
            scoresJson = JSONArray(scores.toList()).toString(),
            feedbackMessage = feedbackMessage
        )
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.exerciseDao().insertRecord(record)
        }
    }

    private fun calculateCalories(count: Int): Double = count * 0.5

    private fun feedbackForScore(score: Float): String = when {
        score >= 90f -> "매우 안정적인 자세를 유지했습니다. 현재 움직임을 계속 유지해 보세요."
        score >= 70f -> "전반적으로 좋습니다. 관절 정렬과 동작 범위를 조금 더 일정하게 유지해 보세요."
        score > 0f -> "속도를 낮추고 관절 위치를 확인하면서 정확하게 반복해 보세요."
        else -> "자세 점수를 계산할 수 있는 운동 데이터가 부족합니다."
    }

    private fun formatElapsedTime(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.getDefault(), "%02d분 %02d초", minutes, remainingSeconds)
    }

    private fun displayExerciseSummary(count: Int, time: String, calories: Double) {
        binding.tvFinalCount.text = "${count}회"
        binding.tvResultTitle.text = when {
            count >= 20 -> "와우! 완벽한 루틴이었어요! 🔥"
            count >= 10 -> "충분히 잘하고 계세요! 👍"
            else -> "시작이 반이에요! 내일은 더 많이 해봐요. 😊"
        }
    }

    private fun returnToHome() {
        val homeIntent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(homeIntent)
        finish()
    }
}
