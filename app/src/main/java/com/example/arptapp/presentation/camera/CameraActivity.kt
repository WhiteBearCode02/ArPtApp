package com.example.arptapp.presentation.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.example.arptapp.R
import com.example.arptapp.databinding.ActivityCameraBinding
import com.example.arptapp.domain.analyzer.DTWCalculator
import com.example.arptapp.utils.CoordinateNormalizer
import com.example.arptapp.data.model.NormalizedPoseData
import com.example.arptapp.presentation.report.ReportActivity
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.atan2
import com.google.mediapipe.tasks.core.OutputHandler
import com.google.mediapipe.tasks.core.ErrorListener

/**
 * 실시간 AI 기반 운동 자세 분석 및 교정 액티비티
 *
 * [주요 기능]
 * - CameraX: 실시간 카메라 프레임 캡처
 * - MediaPipe Pose Landmarker: 신체 33개 관절점 추적
 * - DTW 알고리즘: 표준 자세와 사용자 자세 간 유사도 측정
 * - 상태 머신: 운동 동작 단계별 자동 카운팅
 * - 실시간 피드백: 자세 교정 가이드 제공
 *
 * [기술 스택]
 * - CameraX API (Preview + ImageAnalysis)
 * - MediaPipe Tasks Vision API
 * - Dynamic Time Warping (DTW)
 * - Finite State Machine (FSM)
 */
class CameraActivity : AppCompatActivity(),
    OutputHandler.ResultListener<PoseLandmarkerResult, MPImage>,
    ErrorListener {

    private lateinit var binding: ActivityCameraBinding

    // === 카메라 하드웨어 리소스 ===
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isFrontCamera = true

    // === AI 추론 엔진 (MediaPipe) ===
    private lateinit var poseLandmarker: PoseLandmarker

    // === 운동 분석 및 채점 로직 ===
    private val dtwCalculator = DTWCalculator()
    private val coordinateNormalizer = CoordinateNormalizer()
    private val userPoseSequence = mutableListOf<NormalizedPoseData>()
    private var isRecording = false
    private var currentScore = 0f
    private val scoreHistory = mutableListOf<Float>()

    // === 운동 카운팅 상태 머신 ===
    private var squatCount = 0
    private var targetCount = 10
    private var currentState = ExerciseState.STANDING

    // === 표준 자세 데이터 (벤치마크) ===
    private var standardPoseData: List<FloatArray> = listOf()

    /**
     * 카메라 권한 요청 결과 처리 (Jetpack Activity Result API)
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupCamera()
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intent로 목표 횟수 받기
        targetCount = intent.getIntExtra("TARGET_COUNT", 10)
        updateCountUI()

        // 백그라운드 스레드 풀 (프레임 순차 처리 보장)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // 초기화 순서 (의존성 고려)
        setupPoseLandmarker()
        checkCameraPermission()
        setupButtons()
        loadStandardPoseData()
    }

    /**
     * UI 버튼 이벤트 바인딩
     */
    private fun setupButtons() {
        // 닫기 버튼 (새로 추가)
        binding.btnClose?.setOnClickListener {
            finish()
        }

        // 카메라 전환 버튼 (전면 ↔ 후면)
        binding.btnSwitchCamera?.setOnClickListener {
            isFrontCamera = !isFrontCamera
            setupCamera()
            Toast.makeText(
                this,
                if (isFrontCamera) "전면 카메라" else "후면 카메라",
                Toast.LENGTH_SHORT
            ).show()
        }

        // 일시정지/재개 버튼
        binding.btnPause?.setOnClickListener {
            isRecording = !isRecording
            try {
                binding.btnPause.setImageResource(
                    if (isRecording) R.drawable.ic_pause else R.drawable.ic_play_arrow
                )
            } catch (e: Exception) {
                Log.e("UI_ERROR", "아이콘 리소스 없음: ${e.message}")
            }
            Toast.makeText(
                this,
                if (isRecording) "운동 재개" else "일시정지",
                Toast.LENGTH_SHORT
            ).show()
        }

        // 운동 종료 버튼
        binding.btnStop?.setOnClickListener {
            showResultDialog()
        }
    }

    /**
     * 카메라 권한 확인 및 요청
     */
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * MediaPipe Pose Landmarker 초기화
     *
     * [설정]
     * - 모델: pose_landmarker_lite.task (경량 버전, 30fps 목표)
     * - 모드: LIVE_STREAM (저지연 실시간 스트리밍)
     * - 결과 리스너: 비동기 콜백 (run 메서드)
     */
    private fun setupPoseLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(this)
            .setErrorListener(this)
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(this, options)
    }

    /**
     * CameraX 초기화 및 라이프사이클 바인딩
     */
    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e("CAMERA_X", "카메라 프로바이더 초기화 실패", e)
                Toast.makeText(this, "카메라 시작 실패", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * 카메라 UseCase 바인딩
     *
     * [UseCase 구성]
     * - Preview: 화면에 카메라 프리뷰 표시
     * - ImageAnalysis: MediaPipe로 프레임 전송 (RGBA_8888 포맷)
     */
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        // 카메라 선택 (전면/후면)
        val cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // 프리뷰 설정
        preview = Preview.Builder()
            .setTargetRotation(binding.viewFinder.display.rotation)
            .build()
            .also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

        // 이미지 분석 설정 (MediaPipe 호환 포맷)
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    detectPose(imageProxy)
                }
            }

        try {
            // 기존 바인딩 해제 후 재바인딩
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        } catch (e: Exception) {
            Log.e("CAMERA_X", "카메라 바인딩 실패", e)
            Toast.makeText(this, "카메라 연결 실패", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 프레임별 포즈 감지 로직
     *
     * [처리 순서]
     * 1. ImageProxy → Bitmap 변환
     * 2. 회전 및 미러링 보정
     * 3. MediaPipe 이미지 형식 변환
     * 4. 비동기 추론 시작
     * 5. 리소스 해제 (imageProxy.close())
     */
    private fun detectPose(imageProxy: ImageProxy) {
        val frameTime = SystemClock.uptimeMillis()

        try {
            // 1. RGBA_8888 비트맵 생성
            val bitmapBuffer = createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
            imageProxy.use {
                bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
            }

            // 2. 디바이스 회전 및 전면 카메라 미러링 처리
            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                if (isFrontCamera) {
                    postScale(-1f, 1f, imageProxy.width / 2f, imageProxy.height / 2f)
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

            // 3. MediaPipe 이미지로 변환 및 비동기 추론
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            poseLandmarker.detectAsync(mpImage, frameTime)

        } catch (e: Exception) {
            Log.e("AI_ENGINE", "프레임 처리 오류: ${e.message}")
        } finally {
            // 필수: 다음 프레임을 위한 리소스 해제
            imageProxy.close()
        }
    }

    /**
     * MediaPipe 추론 결과 콜백 (비동기 호출)
     *
     * [처리 단계]
     * 1. 랜드마크 좌표 정규화
     * 2. 관절 각도 계산
     * 3. 운동 상태 머신 업데이트
     * 4. 그래픽 오버레이 렌더링
     * 5. 실시간 피드백 제공
     */
    override fun run(result: PoseLandmarkerResult, input: MPImage) {
        runOnUiThread {
            val allLandmarks = result.landmarks()
            if (!allLandmarks.isNullOrEmpty()) {
                val landmarks = allLandmarks[0]

                // 좌표 정규화 (체형 독립적 분석)
                val normalizedPose = coordinateNormalizer.normalize(
                    result,
                    isMirrored = isFrontCamera
                )

                if (isRecording && normalizedPose != null) {
                    userPoseSequence.add(normalizedPose)
                }

                // 무릎 굴곡 각도 계산 (힙-무릎-발목)
                val kneeAngle = calculateAngle(
                    landmarks[23].x(), landmarks[23].y(),
                    landmarks[25].x(), landmarks[25].y(),
                    landmarks[27].x(), landmarks[27].y()
                )

                // 운동 상태 머신 업데이트
                handleSquatLogic(kneeAngle)

                // 화면에 스켈레톤 오버레이 그리기
                binding.overlay?.setResults(
                    result,
                    input.height,
                    input.width,
                    RunningMode.LIVE_STREAM
                )

                // 실시간 자세 피드백 표시
                provideFeedback(kneeAngle)
            }
        }
    }

    /**
     * 스쿼트 상태 머신 (Finite State Machine)
     *
     * [상태 전이]
     * STANDING → GOING_DOWN → DOWN → GOING_UP → STANDING (1회 완료)
     *
     * @param angle 무릎 굴곡 각도 (0~180도)
     */
    private fun handleSquatLogic(angle: Float) {
        when (currentState) {
            ExerciseState.STANDING -> {
                if (angle < 100) {
                    currentState = ExerciseState.GOING_DOWN
                    isRecording = true
                }
            }
            ExerciseState.GOING_DOWN -> {
                if (angle < 70) {
                    currentState = ExerciseState.DOWN
                }
            }
            ExerciseState.DOWN -> {
                if (angle > 100) {
                    currentState = ExerciseState.GOING_UP
                }
            }
            ExerciseState.GOING_UP -> {
                if (angle > 160) {
                    // 1회 완료
                    currentState = ExerciseState.STANDING
                    squatCount++
                    updateCountUI()

                    // DTW 유사도 계산 (최소 10프레임 필요)
                    if (userPoseSequence.size >= 10) {
                        calculateSimilarityScore()
                    }

                    // 목표 달성 시 결과 화면 표시
                    if (squatCount >= targetCount) {
                        showResultDialog()
                    }

                    isRecording = false
                }
            }
        }
    }

    /**
     * MediaPipe 런타임 에러 핸들러
     */
    override fun onError(error: RuntimeException) {
        Log.e("AI_ENGINE", "MediaPipe 런타임 오류: ${error.message}")
        runOnUiThread {
            Toast.makeText(this, "AI 추론 오류 발생", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 3점 기반 관절 각도 계산
     *
     * @return 각도 (0~180도)
     */
    private fun calculateAngle(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        x3: Float, y3: Float
    ): Float {
        val radians = atan2(y3 - y2, x3 - x2) - atan2(y1 - y2, x1 - x2)
        var angle = Math.toDegrees(radians.toDouble()).toFloat()
        if (angle < 0) angle += 360f
        if (angle > 180) angle = 360f - angle
        return angle
    }

    /**
     * DTW 알고리즘 기반 자세 유사도 점수 계산
     *
     * [알고리즘]
     * 1. 사용자 시퀀스와 표준 시퀀스 간 DTW 거리 계산
     * 2. 거리를 0~100점 스케일로 변환
     * 3. 점수 히스토리에 기록
     */
    private fun calculateSimilarityScore() {
        if (standardPoseData.isEmpty() || userPoseSequence.isEmpty()) return

        // 각도 데이터만 추출
        val userSequence = userPoseSequence.map {
            it.angles.values.toFloatArray()
        }

        // 운동별 가중치 적용
        val weights = dtwCalculator.getExerciseWeights("SQUAT")

        // DTW 거리 계산
        val dtwDistance = dtwCalculator.calculateDTWDistance(
            userSequence,
            standardPoseData,
            weights
        )

        // 점수 변환 (거리 → 0~100점)
        currentScore = dtwCalculator.convertToScore(dtwDistance)
        scoreHistory.add(currentScore)

        // UI 업데이트
        binding.tvScore?.text = getString(R.string.accuracy_format, currentScore.toInt())
        binding.tvScore?.setTextColor(
            when {
                currentScore >= 90 -> getColor(android.R.color.holo_green_light)
                currentScore >= 70 -> getColor(android.R.color.holo_orange_light)
                else -> getColor(android.R.color.holo_red_light)
            }
        )

        // 다음 회차를 위한 초기화
        userPoseSequence.clear()
    }

    /**
     * 실시간 자세 피드백 제공
     *
     * @param kneeAngle 무릎 각도
     */
    private fun provideFeedback(kneeAngle: Float) {
        val feedback = when {
            kneeAngle < 50 -> "조금만 덜 앉으세요"
            kneeAngle in 50f..90f && currentState == ExerciseState.DOWN -> "완벽한 깊이입니다!"
            kneeAngle > 170 && currentState != ExerciseState.STANDING -> "무릎을 조금 더 굽히세요"
            else -> null
        }

        feedback?.let {
            binding.tvFeedback?.text = it
            binding.tvFeedback?.visibility = View.VISIBLE
            // 2초 후 자동 숨김
            binding.tvFeedback?.postDelayed({
                binding.tvFeedback?.visibility = View.GONE
            }, 2000)
        }
    }

    /**
     * 카운트 UI 업데이트
     */
    private fun updateCountUI() {
        binding.tvCount?.text = getString(R.string.count_format, squatCount, targetCount)
    }

    /**
     * 운동 결과 리포트 화면 표시
     */
    private fun showResultDialog() {
        val intent = Intent(this, ReportActivity::class.java).apply {
            putExtra("EXERCISE_TYPE", "스쿼트")
            putExtra("TOTAL_COUNT", squatCount)
            putExtra("AVG_SCORE",
                if (scoreHistory.isNotEmpty()) scoreHistory.average().toFloat() else 0f
            )
            putExtra("SCORES", scoreHistory.toFloatArray())
        }
        startActivity(intent)
        finish()
    }

    /**
     * 표준 자세 데이터 로드
     *
     * TODO: 실제 서비스에서는 JSON 파일 또는 서버 API에서 로드
     */
    private fun loadStandardPoseData() {
        // Mock 데이터: 15프레임 스쿼트 시퀀스
        standardPoseData = List(15) { index ->
            floatArrayOf(
                180f - (index * 10f), // LEFT_KNEE
                180f - (index * 10f), // RIGHT_KNEE
                180f - (index * 8f),  // LEFT_HIP
                180f - (index * 8f)   // RIGHT_HIP
            )
        }
    }

    /**
     * 액티비티 종료 시 리소스 정리
     */
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()

        if (::poseLandmarker.isInitialized) {
            poseLandmarker.close()
        }
    }
}

/**
 * 운동 상태 열거형
 *
 * 한 회의 운동을 4단계로 구분하여 정확한 카운팅 보장
 */
enum class ExerciseState {
    STANDING,   // 준비 자세 (무릎 펴짐)
    GOING_DOWN, // 하강 중
    DOWN,       // 최저점 도달
    GOING_UP    // 상승 중
}