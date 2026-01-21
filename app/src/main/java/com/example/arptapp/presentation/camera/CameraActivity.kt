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
import com.example.arptapp.R
import com.example.arptapp.databinding.ActivityCameraBinding
import com.example.arptapp.domain.analyzer.DTWCalculator
import com.example.arptapp.utils.CoordinateNormalizer
import com.example.arptapp.data.model.NormalizedPoseData
import com.example.arptapp.presentation.report.ReportActivity
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
// [수정] MediaPipe 최신 버전의 BaseOptions 경로를 정확히 지정합니다.
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.atan2

/**
 * CameraActivity: AI 실시간 자세 교정 및 운동 카운팅 엔진
 * - CameraX: 실시간 프레임 캡처 및 분석 스트림 제공
 * - MediaPipe: Pose Landmarker 모델을 통한 신체 33개 주요 관점 추출
 * - DTW Algorithm: 표준 시퀀스와 사용자 시퀀스 간의 동적 시간 왜곡 유사도 측정
 */
// [수정] PoseLandmarker 내부의 인터페이스인 PoseLandmarkerListener를 명시적으로 상속합니다.
class CameraActivity : AppCompatActivity(), PoseLandmarker.PoseLandmarkerListener {

    private lateinit var binding: ActivityCameraBinding

    // --- Camera Hardware Resources ---
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isFrontCamera = true

    // --- AI Inference Engine (MediaPipe) ---
    private lateinit var poseLandmarker: PoseLandmarker

    // --- Exercise Analysis & Scoring Logic ---
    private val dtwCalculator = DTWCalculator()
    private val coordinateNormalizer = CoordinateNormalizer()
    private val userPoseSequence = mutableListOf<NormalizedPoseData>()
    private var isRecording = false
    private var currentScore = 0f
    private val scoreHistory = mutableListOf<Float>()

    // --- State Machine for Exercise Counting (Finite State Machine) ---
    private var squatCount = 0
    private var targetCount = 10
    private var currentState = ExerciseState.STANDING

    private var standardPoseData: List<FloatArray> = listOf()

    // Permission Handling using Jetpack Activity Results API
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) setupCamera()
        else {
            Toast.makeText(this, "카메라 권한이 거부되어 앱을 종료합니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetCount = intent.getIntExtra("TARGET_COUNT", 10)
        updateCountUI()

        // 싱글 스레드 익스큐터 할당 (프레임 순서 보장 및 경쟁 상태 방지)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupPoseLandmarker()
        checkCameraPermission()
        setupButtons()
        loadStandardPoseData()
    }

    private fun setupButtons() {
        binding.btnPause.setOnClickListener {
            isRecording = !isRecording
            // [주의] R.drawable.ic_pause 리소스가 프로젝트에 추가되어 있어야 합니다.
            try {
                binding.btnPause.setImageResource(if (isRecording) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            } catch (e: Exception) {
                Log.e("UI_ERROR", "Drawable missing: ic_pause or ic_play_arrow")
            }
            val msg = if (isRecording) "운동 재개" else "일시정지"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnStop.setOnClickListener { showResultDialog() }

        binding.btnFlipCamera.setOnClickListener {
            isFrontCamera = !isFrontCamera
            setupCamera()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * AI 모델 로드 및 추론 옵션 설정
     * RunningMode.LIVE_STREAM: 저지연 실시간 분석을 위한 스트리밍 모드
     */
    private fun setupPoseLandmarker() {
        // [수정] 실제 assets 폴더에 있는 파일명 'pose_landmarker_lite.task'로 경로를 구성합니다.
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(this)
            .setErrorListener { error -> Log.e("AI_ENGINE", "ML Error: ${error.message}") }
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(this, options)
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        val cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        }

        // ImageAnalysis 설정: RGBA_8888 포맷이 MediaPipe 호환성이 가장 높음
        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    detectPose(imageProxy)
                }
            }

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
        } catch (e: Exception) {
            Log.e("CAMERA_X", "Binding Failed", e)
        }
    }

    /**
     * 프레임 분석 로직 (에러 수정됨)
     * imageProxy.close()는 반드시 분석 완료 후 혹은 오류 시 호출되어야 함
     */
    private fun detectPose(imageProxy: ImageProxy) {
        val frameTime = SystemClock.uptimeMillis()

        try {
            // 1. 비트맵 변환 (RGBA_8888)
            val bitmapBuffer = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

            // 2. 디바이스 회전값 및 전면 카메라 좌우 반전 처리
            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                if (isFrontCamera) postScale(-1f, 1f, imageProxy.width / 2f, imageProxy.height / 2f)
            }

            val rotatedBitmap = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true)

            // 3. MediaPipe 이미지 빌드 및 비동기 추론 시작
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            poseLandmarker.detectAsync(mpImage, frameTime)

        } catch (e: Exception) {
            Log.e("AI_ENGINE", "Frame Process Error: ${e.message}")
        } finally {
            // imageProxy는 반드시 명시적으로 닫아야 다음 프레임이 전달됨
            imageProxy.close()
        }
    }

    /**
     * AI 추론 결과 콜백 (비동기 호출)
     */
    override fun onResults(result: PoseLandmarkerResult, input: MPImage) {
        runOnUiThread {
            // [수정] 랜드마크 데이터 리스트가 null이 아니고 비어있지 않은지 안전하게 확인합니다.
            val allLandmarks = result.landmarks()
            if (!allLandmarks.isNullOrEmpty()) {
                val landmarks = allLandmarks[0]

                // 좌표 정규화 프로세스 (신체 크기에 관계없는 분석 보장)
                val normalizedPose = coordinateNormalizer.normalize(result)
                if (isRecording && normalizedPose != null) userPoseSequence.add(normalizedPose)

                // 운동역학적 각도 계산 (무릎 굴곡도 - 힙(23), 무릎(25), 발목(27))
                val angle = calculateAngle(
                    landmarks[23].x(), landmarks[23].y(),
                    landmarks[25].x(), landmarks[25].y(),
                    landmarks[27].x(), landmarks[27].y()
                )

                handleSquatLogic(angle)

                // 그래픽 오버레이 업데이트
                binding.overlay.setResults(result, input.height, input.width, RunningMode.LIVE_STREAM)
                provideFeedback(angle)
            }
        }
    }

    /**
     * Squat 상태 관리 로직 (Finite State Machine)
     */
    private fun handleSquatLogic(angle: Float) {
        when (currentState) {
            ExerciseState.STANDING -> {
                if (angle < 100) { currentState = ExerciseState.GOING_DOWN; isRecording = true }
            }
            ExerciseState.GOING_DOWN -> if (angle < 70) currentState = ExerciseState.DOWN
            ExerciseState.DOWN -> if (angle > 100) currentState = ExerciseState.GOING_UP
            ExerciseState.GOING_UP -> {
                if (angle > 160) {
                    currentState = ExerciseState.STANDING
                    squatCount++
                    updateCountUI()
                    if (userPoseSequence.size >= 10) calculateSimilarityScore()
                    if (squatCount >= targetCount) showResultDialog()
                    isRecording = false
                }
            }
        }
    }

    override fun onError(error: RuntimeException) {
        Log.e("AI_ENGINE", "MediaPipe Runtime Error: ${error.message}")
    }

    private fun calculateAngle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): Float {
        val radians = atan2(y3 - y2, x3 - x2) - atan2(y1 - y2, x1 - x2)
        var angle = Math.toDegrees(radians.toDouble()).toFloat()
        if (angle < 0) angle += 360f
        if (angle > 180) angle = 360f - angle
        return angle
    }

    /**
     * DTW 알고리즘 기반 사용자 시퀀스와 표준 데이터 간 유사도 환산
     */
    private fun calculateSimilarityScore() {
        if (standardPoseData.isEmpty() || userPoseSequence.isEmpty()) return

        val userSequence = userPoseSequence.map { it.angles.values.toFloatArray() }
        val weights = dtwCalculator.getExerciseWeights("SQUAT")
        val dtwDistance = dtwCalculator.calculateDTWDistance(userSequence, standardPoseData, weights)

        currentScore = dtwCalculator.convertToScore(dtwDistance)
        scoreHistory.add(currentScore)

        binding.tvScore.text = "정확도: ${currentScore.toInt()}%"
        binding.tvScore.setTextColor(
            when {
                currentScore >= 90 -> getColor(android.R.color.holo_green_light)
                currentScore >= 70 -> getColor(android.R.color.holo_orange_light)
                else -> getColor(android.R.color.holo_red_light)
            }
        )
        userPoseSequence.clear()
    }

    private fun provideFeedback(kneeAngle: Float) {
        val feedback = when {
            kneeAngle < 50 -> "조금만 덜 앉으세요"
            kneeAngle in 50f..90f && currentState == ExerciseState.DOWN -> "완벽한 깊이입니다!"
            kneeAngle > 170 && currentState != ExerciseState.STANDING -> "무릎을 조금 더 굽히세요"
            else -> null
        }

        feedback?.let {
            binding.tvFeedback.text = it
            binding.tvFeedback.visibility = View.VISIBLE
            binding.tvFeedback.postDelayed({ binding.tvFeedback.visibility = View.GONE }, 2000)
        }
    }

    private fun updateCountUI() {
        binding.tvCount.text = "$squatCount / $targetCount"
    }

    private fun showResultDialog() {
        val intent = Intent(this, ReportActivity::class.java).apply {
            putExtra("EXERCISE_TYPE", "스쿼트")
            putExtra("TOTAL_COUNT", squatCount)
            putExtra("AVG_SCORE", if (scoreHistory.isNotEmpty()) scoreHistory.average().toFloat() else 0f)
            putExtra("SCORES", scoreHistory.toFloatArray())
        }
        startActivity(intent)
        finish()
    }

    private fun loadStandardPoseData() {
        // Mock Data: 실 서비스에서는 JSON 또는 서버 데이터를 파싱하여 로드함
        standardPoseData = List(15) { index ->
            floatArrayOf(180f - (index * 10f), 180f - (index * 10f), 180f - (index * 8f), 180f - (index * 8f))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        // [수정] 리소스 해제 전 초기화 여부를 확인합니다.
        if (::poseLandmarker.isInitialized) {
            poseLandmarker.close()
        }
    }
}

/**
 * ExerciseState: 운동 한 회를 인식하기 위한 순차적 상태 정의
 */
enum class ExerciseState {
    STANDING,   // 초기 준비 자세
    GOING_DOWN, // 하강 중
    DOWN,       // 최저점 도달
    GOING_UP    // 상승 중
}