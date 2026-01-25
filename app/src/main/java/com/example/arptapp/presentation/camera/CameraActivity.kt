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
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
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
 * [방법1] 프레임을 정규화(0도)하여 MediaPipe에 전달
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

    // === 카메라 권한 요청 결과 처리 (Activity Result API) ===
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) setupCamera() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetCount = intent.getIntExtra("TARGET_COUNT", 10)
        updateCountUI()

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupPoseLandmarker()
        checkCameraPermission()
        setupButtons()
        loadStandardPoseData()
    }

    // === UI 버튼 이벤트 바인딩 ===
    private fun setupButtons() {
        binding.btnClose?.setOnClickListener { finish() }
        binding.btnSwitchCamera?.setOnClickListener {
            isFrontCamera = !isFrontCamera
            setupCamera()
        }
        binding.btnPause?.setOnClickListener {
            isRecording = !isRecording
            binding.btnPause.setImageResource(if (isRecording) R.drawable.ic_pause else R.drawable.ic_play_arrow)
        }
        binding.btnStop?.setOnClickListener { showResultDialog() }
    }

    // === 카메라 권한 확인 및 요청 ===
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // === MediaPipe Pose Landmarker 초기화 ===
    private fun setupPoseLandmarker() {
        val baseOptions = BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(this)
            .setErrorListener(this)
            .build()
        poseLandmarker = PoseLandmarker.createFromOptions(this, options)
    }

    // === CameraX 초기화 및 라이프사이클 바인딩 ===
    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e("CAMERA_X", "초기화 실패", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // === 카메라 UseCase 바인딩 ===
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        val cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        preview = Preview.Builder().setTargetRotation(binding.viewFinder.display.rotation).build()
            .also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(cameraExecutor) { imageProxy -> detectPose(imageProxy) } }

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
        } catch (e: Exception) {
            Log.e("CAMERA_X", "바인딩 실패", e)
        }
    }

    // === 프레임별 포즈 감지 로직 ===
    private fun detectPose(imageProxy: ImageProxy) {
        val frameTime = SystemClock.uptimeMillis()
        try {
            val bitmapBuffer = createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            Log.d("ARPT_METHOD1", "원본 프레임 회전 각도: ${rotationDegrees}도")

            // [방법1 핵심] 비트맵을 회전하여 정규화
            val rotatedBitmap = if (rotationDegrees != 0) {
                rotateMatrix(bitmapBuffer, rotationDegrees)
            } else {
                bitmapBuffer
            }

            Log.d("ARPT_METHOD1", "정규화 후 비트맵 크기: ${rotatedBitmap.width}x${rotatedBitmap.height}")

            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            poseLandmarker.detectAsync(mpImage, frameTime)
        } finally {
            imageProxy.close()
        }
    }

    /**
     * 비트맵 회전 함수
     *
     * @param bitmap 원본 비트맵
     * @param degrees 회전 각도 (270 또는 90)
     * @return 회전된 비트맵
     */
    private fun rotateMatrix(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap

        val matrix = Matrix().apply {
            postRotate(degrees.toFloat(), bitmap.width / 2f, bitmap.height / 2f)
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )

        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }

        return rotatedBitmap
    }

    // === MediaPipe 추론 결과 콜백 (비동기 호출) ===
    override fun run(result: PoseLandmarkerResult, input: MPImage) {
        runOnUiThread {
            val allLandmarks = result.landmarks()
            if (!allLandmarks.isNullOrEmpty()) {
                val landmarks = allLandmarks[0]

                val normalizedPose = coordinateNormalizer.normalize(result, isMirrored = isFrontCamera)
                if (isRecording && normalizedPose != null) userPoseSequence.add(normalizedPose)

                val kneeAngle = calculateAngle(
                    landmarks[23].x(), landmarks[23].y(),
                    landmarks[25].x(), landmarks[25].y(),
                    landmarks[27].x(), landmarks[27].y()
                )

                handleSquatLogic(kneeAngle)

                // [방법1] 프레임이 이미 정규화되었으므로
                // input.width, input.height는 회전 후의 최종 크기
                binding.overlay?.setResults(
                    landmarks,
                    input.width,
                    input.height,
                    isFrontCamera
                )

                provideFeedback(kneeAngle)
            }
        }
    }

    // === 스쿼트 상태 머신 (Finite State Machine) ===
    private fun handleSquatLogic(angle: Float) {
        when (currentState) {
            ExerciseState.STANDING -> if (angle < 100) { currentState = ExerciseState.GOING_DOWN; isRecording = true }
            ExerciseState.GOING_DOWN -> if (angle < 70) currentState = ExerciseState.DOWN
            ExerciseState.DOWN -> if (angle > 100) currentState = ExerciseState.GOING_UP
            ExerciseState.GOING_UP -> if (angle > 160) {
                currentState = ExerciseState.STANDING; squatCount++; updateCountUI()
                if (userPoseSequence.size >= 10) calculateSimilarityScore()
                if (squatCount >= targetCount) showResultDialog()
                isRecording = false
            }
        }
    }

    // === MediaPipe 런타임 에러 핸들러 ===
    override fun onError(error: RuntimeException) { Log.e("AI_ENGINE", "오류: ${error.message}") }

    // === 3점 기반 관절 각도 계산 ===
    private fun calculateAngle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): Float {
        val radians = atan2(y3 - y2, x3 - x2) - atan2(y1 - y2, x1 - x2)
        var angle = Math.toDegrees(radians.toDouble()).toFloat()
        if (angle < 0) angle += 360f
        if (angle > 180) angle = 360f - angle
        return angle
    }

    // === DTW 알고리즘 기반 자세 유사도 점수 계산 ===
    private fun calculateSimilarityScore() {
        if (standardPoseData.isEmpty() || userPoseSequence.isEmpty()) return
        val userSequence = userPoseSequence.map { it.angles.values.toFloatArray() }
        val weights = dtwCalculator.getExerciseWeights("SQUAT")
        val dtwDistance = dtwCalculator.calculateDTWDistance(userSequence, standardPoseData, weights)
        currentScore = dtwCalculator.convertToScore(dtwDistance)
        scoreHistory.add(currentScore)
        binding.tvScore?.text = getString(R.string.accuracy_format, currentScore.toInt())
        userPoseSequence.clear()
    }

    // === 실시간 자세 피드백 제공 ===
    private fun provideFeedback(kneeAngle: Float) {
        val feedback = if (kneeAngle < 50) "조금만 덜 앉으세요" else if (kneeAngle in 50f..90f && currentState == ExerciseState.DOWN) "완벽한 깊이입니다!" else null
        feedback?.let {
            binding.tvFeedback?.text = it
            binding.tvFeedback?.visibility = View.VISIBLE
            binding.tvFeedback?.postDelayed({ binding.tvFeedback?.visibility = View.GONE }, 2000)
        }
    }

    // === 카운트 UI 업데이트 ===
    private fun updateCountUI() { binding.tvCount?.text = getString(R.string.count_format, squatCount, targetCount) }

    // === 운동 결과 리포트 화면 표시 ===
    private fun showResultDialog() {
        val intent = Intent(this, ReportActivity::class.java).apply {
            putExtra("TOTAL_COUNT", squatCount)
            putExtra("AVG_SCORE", if (scoreHistory.isNotEmpty()) scoreHistory.average().toFloat() else 0f)
        }
        startActivity(intent)
        finish()
    }

    // === 표준 자세 데이터 로드 ===
    private fun loadStandardPoseData() { standardPoseData = List(15) { index -> floatArrayOf(180f - (index * 10f), 180f - (index * 10f), 180f - (index * 8f), 180f - (index * 8f)) } }

    // === 액티비티 종료 시 리소스 정리 ===
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (::poseLandmarker.isInitialized) poseLandmarker.close()
    }
}

// === 운동 상태 열거형 ===
enum class ExerciseState { STANDING, GOING_DOWN, DOWN, GOING_UP }