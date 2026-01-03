package com.example.arptapp

// [Imports: 안드로이드 기본 UI 및 데이터 전달 도구]
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.arptapp.databinding.ActivityResultBinding

/**
 * [ArPtApp - 운동 결과 리포트 모듈]
 * 역할: DashboardActivity로부터 전달받은 운동 데이터를 분석하여 
 * 사용자에게 소모 칼로리 및 운동 시간을 시각화하여 보고함.
 */
class ResultActivity : AppCompatActivity() {

    // [Architecture: ViewBinding]
    // XML 레이아웃(activity_result)의 컴포넌트에 접근하기 위한 바인딩 객체
    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. UI 초기화: 레이아웃 인플레이션 및 화면 설정 (ViewBinding 적용)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. [Data Retrieval] Intent를 통해 전달된 운동 데이터 수신
        // DashboardActivity에서 설정한 Key값("TOTAL_COUNT", "EXERCISE_TIME")과 정확히 일치해야 함.
        val finalCount = intent.getIntExtra("TOTAL_COUNT", 0)
        val exerciseTimeInSeconds = intent.getLongExtra("EXERCISE_TIME", 0L)

        // 3. [Logic: 데이터 가공] 원천 데이터를 사용자 친화적인 정보로 변환
        // [변경점] 단순히 데이터 수신에서 그치지 않고, 포맷팅과 칼로리 계산 로직을 거침
        val formattedTime = formatElapsedTime(exerciseTimeInSeconds)
        val burnedCalories = calculateCalories(finalCount)

        // 4. [UI Update] 가공된 데이터를 화면의 TextView들에 매핑
        displayExerciseSummary(finalCount, formattedTime, burnedCalories)

        // 5. [Event] 홈으로 돌아가기 버튼 클릭 리스너 (Main 화면으로 복귀)
        binding.btnBackToMain.setOnClickListener {
            returnToHome()
        }
    }

    /**
     * [수학적 계산] 운동 횟수를 기반으로 예상 소모 칼로리를 산출합니다.
     * 공식: 스쿼트 1회당 약 0.5kcal 소모 (일반적인 성인 평균치 적용)
     * @param count 총 스쿼트 횟수
     * @return 계산된 칼로리 (Double)
     */
    private fun calculateCalories(count: Int): Double {
        return count * 0.5
    }

    /**
     * [데이터 가공] 초(Long) 단위의 소요 시간을 "00분 00초" 형식의 문자열로 포맷팅합니다.
     * @param seconds 초 단위 시간
     * @return 포맷팅된 시간 문자열
     */
    private fun formatElapsedTime(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        // String.format을 사용하여 1자리 숫자일 경우 앞에 0을 붙여 2자리로 유지 (예: 05초)
        return String.format("%02d분 %02d초", minutes, remainingSeconds)
    }

    /**
     * [UI 매핑] 최종 분석 결과를 화면에 전시하고 사용자의 성취도에 따라 다정한 피드백을 제공합니다.
     */
    private fun displayExerciseSummary(count: Int, time: String, calories: Double) {
        // [핵심] 총 횟수 텍스트 뷰 업데이트
        binding.tvFinalCount.text = "${count}회"
        
        // [피드백 UX] 성취도(횟수)에 따른 동적 타이틀 메시지 변경 로직
        if (count >= 20) {
            binding.tvResultTitle.text = "와우! 완벽한 루틴이었어요! 🔥"
        } else if (count >= 10) {
            binding.tvResultTitle.text = "충분히 잘하고 계세요! 계속 가볼까요? 👍"
        } else {
            binding.tvResultTitle.text = "시작이 반이에요! 내일은 더 많이 해봐요. 😊"
        }
        
        // 참고: 시간(time)과 칼로리(calories) 데이터는 현재 로그나 추가 UI가 있다면 여기에 연결 가능합니다.
    }

    /**
     * [Navigation] 메인 화면으로 돌아가며 백스택을 정리하여 보안 및 UX 안정성을 확보합니다.
     */
    private fun returnToHome() {
        val intent = Intent(this, MainActivity::class.java).apply {
            // FLAG_ACTIVITY_CLEAR_TOP: 이동할 액티비티 위에 쌓인 다른 액티비티를 모두 제거
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish() // 현재 결과창 액티비티를 종료하여 메모리 반환
    }
}