package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.data.AppDatabase
import com.example.arptapp.data.ExerciseRecord
import com.example.arptapp.databinding.ActivityResultBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 운동 데이터를 요약 전시하고 Room 데이터베이스에 최종 기록을 저장하는 화면입니다.
 */
class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // DashboardActivity에서 전달한 운동 데이터(횟수, 시간) 수신
        val finalCount = intent.getIntExtra("TOTAL_COUNT", 0)
        val exerciseTimeInSeconds = intent.getLongExtra("EXERCISE_TIME", 0L)

        // 데이터 가공: 시간 포맷팅 및 칼로리 계산
        val formattedTime = formatElapsedTime(exerciseTimeInSeconds)
        val burnedCalories = calculateCalories(finalCount)

        // 화면 UI 업데이트: 가공된 데이터 매핑
        displayExerciseSummary(finalCount, formattedTime, burnedCalories)

        // 데이터베이스 영구 저장 실행
        saveWorkoutToDatabase(finalCount, exerciseTimeInSeconds, burnedCalories)

        // 메인 허브(HomeActivity)로 돌아가기 버튼 설정
        binding.btnBackToMain.setOnClickListener {
            returnToHome()
        }
    }

    // Room DB에 운동 기록 객체 생성 및 비동기 저장
    private fun saveWorkoutToDatabase(count: Int, duration: Long, calories: Double) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        val record = ExerciseRecord(
            date = currentDate,
            totalCount = count,
            duration = duration,
            burnedCalories = calories
        )

        // lifecycleScope를 사용한 비동기 DB Insert 작업
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.exerciseDao().insertRecord(record)
        }
    }

    // 스쿼트 횟수 기반 칼로리 산출 (1회당 0.5kcal)
    private fun calculateCalories(count: Int): Double {
        return count * 0.5
    }

    // 초 단위 시간을 "00분 00초" 형식의 문자열로 변환
    private fun formatElapsedTime(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d분 %02d초", minutes, remainingSeconds)
    }

    // 운동 성취도에 따른 제목 메시지 및 결과 데이터 전시
    private fun displayExerciseSummary(count: Int, time: String, calories: Double) {
        binding.tvFinalCount.text = "${count}회"
        
        // 횟수에 따른 동적 피드백 로직
        binding.tvResultTitle.text = when {
            count >= 20 -> "와우! 완벽한 루틴이었어요! 🔥"
            count >= 10 -> "충분히 잘하고 계세요! 👍"
            else -> "시작이 반이에요! 내일은 더 많이 해봐요. 😊"
        }
        
        // 추가 데이터(시간, 칼로리) 표시 (필요 시 레이아웃에 뷰 추가 가능)
    }

    // 홈 화면으로 복귀하며 액티비티 스택 정리
    private fun returnToHome() {
        // 인증 후 진입점인 HomeActivity로 이동 (로그인 화면이 아님)
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }
}