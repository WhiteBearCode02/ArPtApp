// 파일 상단에 import 추가
import com.example.arptapp.domain.analyzer.DTWCalculator
import com.example.arptapp.utils.CoordinateNormalizer
import com.example.arptapp.data.model.NormalizedPoseData

class CameraActivity : AppCompatActivity() {
    
    // 기존 변수들...
    
    // 신규 추가
    private val dtwCalculator = DTWCalculator()
    private val coordinateNormalizer = CoordinateNormalizer()
    private val userPoseSequence = mutableListOf<NormalizedPoseData>()
    private var isRecording = false
    private var currentScore = 0f
    
    // 표준 자세 데이터 (추후 assets에서 로드)
    private lateinit var standardPoseData: List<FloatArray>
    
    // ... 기존 onCreate, setupCamera 등 ...
    
    /**
     * MediaPipe 결과 처리 부분 수정
     */
    private fun detectPose(imageProxy: ImageProxy) {
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()
        
        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (isFrontCamera) {
                postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
            }
        }
        
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer,
            0, 0,
            bitmapBuffer.width,
            bitmapBuffer.height,
            matrix,
            true
        )
        
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        val frameTime = SystemClock.uptimeMillis()
        
        // MediaPipe 포즈 추정 실행
        poseLandmarker.detectAsync(mpImage, frameTime)
    }
    
    /**
     * 포즈 추정 결과 콜백 (기존 코드 개선)
     */
    override fun onResults(result: PoseLandmarkerResult, input: MPImage) {
        runOnUiThread {
            if (result.landmarks().isNotEmpty()) {
                val landmarks = result.landmarks()[0]
                
                // 1. 좌표 정규화
                val normalizedPose = coordinateNormalizer.normalize(result)
                
                // 2. 운동 중이면 시퀀스에 추가
                if (isRecording && normalizedPose != null) {
                    userPoseSequence.add(normalizedPose)
                }
                
                // 3. 기존 각도 계산 및 카운팅 로직
                val leftHip = landmarks[23]
                val leftKnee = landmarks[25]
                val leftAnkle = landmarks[27]
                
                val angle = calculateAngle(
                    leftHip.x(), leftHip.y(),
                    leftKnee.x(), leftKnee.y(),
                    leftAnkle.x(), leftAnkle.y()
                )
                
                // 4. 상태 머신 기반 카운팅
                when (currentState) {
                    ExerciseState.STANDING -> {
                        if (angle < 100) { // 앉기 시작
                            currentState = ExerciseState.GOING_DOWN
                        }
                    }
                    ExerciseState.GOING_DOWN -> {
                        if (angle < 70) { // 완전히 앉음
                            currentState = ExerciseState.DOWN
                        }
                    }
                    ExerciseState.DOWN -> {
                        if (angle > 100) { // 일어나기 시작
                            currentState = ExerciseState.GOING_UP
                        }
                    }
                    ExerciseState.GOING_UP -> {
                        if (angle > 160) { // 완전히 일어남
                            currentState = ExerciseState.STANDING
                            
                            // 카운트 증가 및 DTW 분석
                            squatCount++
                            updateCountUI()
                            
                            // 5. 한 사이클 완료 시 DTW 유사도 측정
                            if (userPoseSequence.size >= 10) {
                                calculateSimilarityScore()
                            }
                            
                            // 6. 목표 달성 시
                            if (squatCount >= targetCount) {
                                saveExerciseRecord()
                                showCompletionDialog()
                            }
                        }
                    }
                }
                
                // 7. 오버레이 업데이트
                overlayView.setResults(
                    result,
                    input.height,
                    input.width,
                    RunningMode.LIVE_STREAM
                )
                
                // 8. 실시간 피드백 (각도 기반)
                provideFeedback(angle)
            }
        }
    }
    
    /**
     * DTW 기반 유사도 점수 계산 (신규)
     */
    private fun calculateSimilarityScore() {
        if (!::standardPoseData.isInitialized || userPoseSequence.isEmpty()) return
        
        // 사용자 시퀀스를 FloatArray 형식으로 변환
        val userSequence = userPoseSequence.map { pose ->
            pose.angles.values.toFloatArray()
        }
        
        // DTW 거리 계산
        val weights = dtwCalculator.getExerciseWeights("SQUAT")
        val dtwDistance = dtwCalculator.calculateDTWDistance(
            userSequence,
            standardPoseData,
            weights
        )
        
        // 점수 변환 (0~100)
        currentScore = dtwCalculator.convertToScore(dtwDistance)
        
        // UI 업데이트
        runOnUiThread {
            binding.tvScore.text = "정확도: ${currentScore.toInt()}%"
            
            // 점수에 따른 색상 변경
            binding.tvScore.setTextColor(
                when {
                    currentScore >= 90 -> Color.GREEN
                    currentScore >= 70 -> Color.YELLOW
                    else -> Color.RED
                }
            )
        }
        
        // 시퀀스 초기화 (다음 사이클을 위해)
        userPoseSequence.clear()
    }
    
    /**
     * 실시간 피드백 제공 (신규)
     */
    private fun provideFeedback(kneeAngle: Float) {
        val feedback = when {
            kneeAngle < 50 -> "너무 깊이 앉았습니다"
            kneeAngle in 50f..90f && currentState == ExerciseState.DOWN -> "좋습니다!"
            kneeAngle > 170 -> "무릎을 완전히 펴세요"
            else -> null
        }
        
        feedback?.let {
            binding.tvFeedback.text = it
            binding.tvFeedback.visibility = View.VISIBLE
            
            // 3초 후 숨김
            binding.tvFeedback.postDelayed({
                binding.tvFeedback.visibility = View.GONE
            }, 3000)
        }
    }
    
    // ... 기존 메서드들 ...
}

/**
 * 운동 상태 열거형
 */
enum class ExerciseState {
    STANDING,
    GOING_DOWN,
    DOWN,
    GOING_UP
}
