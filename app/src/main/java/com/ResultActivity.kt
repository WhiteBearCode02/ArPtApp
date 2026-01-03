package com.example.arptapp

// [Imports: 안드로이드 기본 UI 및 데이터 전달 도구]
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope // 액티비티 생명주기에 종속된 비동기 작업 지원
import com.example.arptapp.data.AppDatabase // Room DB 관리 클래스
import com.example.arptapp.data.ExerciseRecord // DB 엔티티(테이블 설계도)
import com.example.arptapp.databinding.ActivityResultBinding // 뷰 바인딩
import kotlinx.coroutines.launch // 비동기 코루틴 실행 도구
import java.text.SimpleDateFormat // 날짜 형식 가공 도구
import java.util.* // 자바 유틸리티 (날짜 등)

/**
 * [ArPtApp - 운동 결과 분석 및 영속성 저장 모듈]
 * 역할: 
 * 1. DashboardActivity로부터 전달받은 운동 데이터를 분석하여 시각화합니다.
 * 2. 분석된 최종 데이터를 로컬 데이터베이스(Room)에 비동기적으로 저장하여 기록을 보존합니다.
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

        // 2. [Data Retrieval] Intent를 통해 전달된 원천 운동 데이터 수신
        // DashboardActivity에서 설정한 Key값과 정확히 일치해야 데이터를 정상 수신함
        val finalCount = intent.getIntExtra("TOTAL_COUNT", 0)
        val exerciseTimeInSeconds = intent.getLongExtra("EXERCISE_TIME", 0L)

        // 3. [Logic: 데이터 가공] 수신된 데이터를 사용자 친화적인 정보(시간 포맷, 칼로리)로 변환
        val formattedTime = formatElapsedTime(exerciseTimeInSeconds)
        val burnedCalories = calculateCalories(finalCount)

        // 4. [UI Update] 가공된 최종 데이터를 화면의 TextView들에 매핑하여 전시
        displayExerciseSummary(finalCount, formattedTime, burnedCalories)

        // 5. [Persistence: 데이터베이스 저장] 
        // 결과 화면이 생성되는 시점에 자동으로 DB에 운동 기록을 영구 저장함
        saveWorkoutToDatabase(finalCount, exerciseTimeInSeconds, burnedCalories)

        // 6. [Event] 홈으로 돌아가기 버튼 클릭 리스너 (Main 화면으로 복귀)
        binding.btnBackToMain.setOnClickListener {
            returnToHome()
        }
    }

    /**
     * [Database Logic] 수신된 운동 데이터를 Room DB에 비동기적으로 저장합니다.
     * @param count 최종 횟수
     * @param duration 소요 시간(초)
     * @param calories 소모 칼로리
     */
    private fun saveWorkoutToDatabase(count: Int, duration: Long, calories: Double) {
        // 현재 날짜 및 시간을 "yyyy-MM-dd HH:mm" 형식의 문자열로 생성
        val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        // DB에 삽입할 행(Row) 객체 생성
        val record = ExerciseRecord(
            date = currentDate,
            totalCount = count,
            duration = duration,
            burnedCalories = calories
        )

        /**
         * [Coroutines: 비동기 처리]
         * DB 작업은 메인 UI 스레드를 차단(Block)할 수 있으므로 반드시 백그라운드에서 수행해야 합니다.
         * lifecycleScope.launch는 액티비티가 파괴되면 작업도 자동으로 취소해주는 안전한 코루틴 블록입니다.
         */
        lifecycleScope.launch {
            // DB 인스턴스 획득 및 데이터 삽입(Insert) 실행
            val db = AppDatabase.getDatabase(applicationContext)
            db.exerciseDao().insertRecord(record)
        }
    }

    /**
     * [수학적 계산] 운동 횟수를 기반으로 예상 소모 칼로리를 산출합니다.
     * 공식: 스쿼트 1회당 약 0.5kcal 소모 (일반적인 성인 평균치 적용)
     */
    private fun calculateCalories(count: Int): Double {
        return count * 0.5
    }

    /**
     * [데이터 가공] 초(Long) 단위의 소요 시간을 "00분 00초" 형식의 문자열로 포맷팅합니다.
     */
    private fun formatElapsedTime(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        // String.format을 사용하여 1자리 숫자일 경우 앞에 0을 붙여 2자리로 유지 (예: 05초)
        return String.format("%02d분 %02d초", minutes, remainingSeconds)
    }

    /**
     * [UI 매핑] 최종 분석 결과를 화면에 전시하고 사용자의 성취도에 따라 동적 피드백을 제공합니다.
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
        
        // 참고: 필요 시 가공된 time과 calories 데이터를 레이아웃의 추가 뷰에 연결할 수 있습니다.
    }

    /**
     * [Navigation] 메인 화면으로 돌아가며 백스택을 정리하여 보안 및 UX 안정성을 확보합니다.
     */
    private fun returnToHome() {
        val intent = Intent(this, MainActivity::class.java).apply {
            // FLAG_ACTIVITY_CLEAR_TOP: 이동할 액티비티 위에 쌓인 다른 액티비티를 모두 제거하여 앱 흐름을 초기화함
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish() // 현재 결과창 액티비티를 종료하여 메모리 반환
    }
}