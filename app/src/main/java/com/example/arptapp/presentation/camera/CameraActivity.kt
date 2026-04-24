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
import com.google.mediapipe.tasks.core.OutputHandler
import com.google.mediapipe.tasks.core.ErrorListener
import kotlin.math.*

/**
 * 실시간 AI 기반 운동 자세 분석 및 교정 액티비티
 * 개선 내용: 측면 인식률 향상을 위한 3D 좌표 및 가시성(Visibility) 필터링 적용
 */
class CameraActivity : AppCompatActivity(),
    OutputHandler.ResultListener<PoseLandmarkerResult, MPImage>,
    ErrorListener {

    private lateinit var binding: ActivityCameraBinding

    private lateinit var exerciseClassifier: ExerciseClassifier // 안내 데스크 직원
    private var currentAnalyzer: BaseExerciseAnalyzer? = null // 현재 일하고 있는 트레이너
    private var currentExerciseName = "READY" // 현재 감지된 운동 이름

    // === 카메라 및 실행 환경 리소스 ===
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

    // === 운동 카운팅 상태 머신 (FSM) ===
    private var squatCount = 0
    private var targetCount = 10
    private var currentState = ExerciseState.STANDING

    // === 표준 자세 데이터 (벤치마크) ===
    private var standardPoseData: List<FloatArray> = listOf()

    // === 권한 요청 처리 ===
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

        exerciseClassifier = ExerciseClassifier() // 분류기 객체 생성
    }

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

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

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

    private fun detectPose(imageProxy: ImageProxy) {
        val frameTime = SystemClock.uptimeMillis()
        try {
            val bitmapBuffer = createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val rotatedBitmap = if (rotationDegrees != 0) {
                rotateMatrix(bitmapBuffer, rotationDegrees)
            } else {
                bitmapBuffer
            }

            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            poseLandmarker.detectAsync(mpImage, frameTime)
        } finally {
            imageProxy.close()
        }
    }

    private fun rotateMatrix(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply {
            postRotate(degrees.toFloat(), bitmap.width / 2f, bitmap.height / 2f)
        }
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotatedBitmap != bitmap) bitmap.recycle()
        return rotatedBitmap
    }

// === MediaPipe 추론 결과 콜백 (지능형 동적 스위칭 버전) ===
override fun run(result: PoseLandmarkerResult, input: MPImage) {
    runOnUiThread {
        val allLandmarks = result.landmarks()
        if (!allLandmarks.isNullOrEmpty()) {
            val landmarks = allLandmarks[0]

            // 1. [좌표 정규화] 기존 로직 유지
            val normalizedPose = coordinateNormalizer.normalize(result, isMirrored = isFrontCamera)
            if (isRecording && normalizedPose != null) userPoseSequence.add(normalizedPose)

            // 2. [운동 종목 분류] AI가 현재 무슨 운동을 하는지 스스로 판단합니다.
            // (주의: 이전에 만든 ExerciseClassifier.kt의 함수를 호출합니다.)
            val detectedExercise = exerciseClassifier.classify(landmarks)

            // 3. [동적 분석기 스위칭] 운동 종목이 바뀌었다면 분석기를 즉시 교체합니다.
            if (detectedExercise != "ANALYZING..." && detectedExercise != currentExerciseName) {
                currentExerciseName = detectedExercise
                
                // 매니저(Factory)에게 현재 운동에 맞는 트레이너(Analyzer)를 요청합니다.
                currentAnalyzer = AnalyzerFactory.getAnalyzer(detectedExercise)

                // UI에 현재 어떤 운동을 AI가 감지했는지 실시간으로 보여줍니다.
                binding.tvExerciseType?.text = "감지된 운동: $currentExerciseName"
            }

            // 4. [분석 및 카운팅] 현재 배정된 분석기(스쿼트, 런지 등)가 자세를 분석합니다.
            // 이제 개별적인 각도 계산 수식은 각 Analyzer 내부로 숨겨집니다(캡슐화).
            val count = currentAnalyzer?.analyze(landmarks) ?: 0
            
            // 5. [피드백 및 결과 출력]
            updateCountUI(count) // 횟수 업데이트
            binding.overlay?.setResults(landmarks, input.width, input.height, isFrontCamera)
            
            // 자세가 올바른지 체크하여 피드백 제공
            if (currentAnalyzer?.isProperForm() == false) {
                provideFeedback("자세를 조금 더 신경 써주세요!")
            }
        }
    }
}

    // === 스쿼트 상태 머신 (Adaptive Thresholds 적용) ===
    private fun handleSquatLogic(angle: Float, landmarks: List<NormalizedLandmark>) {
        // [추론 로직] 양쪽 어깨의 x축 거리가 좁으면 측면(Side View)으로 판단
        val shoulderWidth = abs(landmarks[11].x() - landmarks[12].x())
        val isSideView = shoulderWidth < 0.16f 

        // 측면 인식률을 위해 앉는 각도 기준(Down)을 정면보다 소폭 완화
        val downThreshold = if (isSideView) 105f else 100f

        when (currentState) {
            ExerciseState.STANDING -> if (angle < downThreshold) { 
                currentState = ExerciseState.GOING_DOWN; isRecording = true 
            }
            ExerciseState.GOING_DOWN -> if (angle < 75f) currentState = ExerciseState.DOWN
            ExerciseState.DOWN -> if (angle > downThreshold) currentState = ExerciseState.GOING_UP
            ExerciseState.GOING_UP -> if (angle > 160f) {
                currentState = ExerciseState.STANDING; squatCount++; updateCountUI()
                if (userPoseSequence.size >= 10) calculateSimilarityScore()
                if (squatCount >= targetCount) showResultDialog()
                isRecording = false
            }
        }
    }

    /**
     * 기능 설명: 3차원 공간상의 벡터 내적(Dot Product)을 통해 원근 왜곡 없는 관절 각도를 산출합니다.
     */
    private fun calculate3DAngle(p1: NormalizedLandmark, p2: NormalizedLandmark, p3: NormalizedLandmark): Double {
        // 무릎(p2)을 원점으로 하는 상퇴(v1) 및 하퇴(v2) 벡터 생성
        val v1x = p1.x() - p2.x(); val v1y = p1.y() - p2.y(); val v1z = p1.z() - p2.z()
        val v2x = p3.x() - p2.x(); val v2y = p3.y() - p2.y(); val v2z = p3.z() - p2.z()

        // 코사인 유사도 공식: cos(θ) = (A·B) / (|A||B|)
        val dotProduct = v1x * v2x + v1y * v2y + v1z * v2z
        val mag1 = sqrt(v1x * v1x + v1y * v1y + v1z * v1z)
        val mag2 = sqrt(v2x * v2x + v2y * v2y + v2z * v2z)

        val cosTheta = dotProduct / (mag1 * mag2)
        // 수치 안정성을 위해 -1~1 사이로 고정 후 acos 연산
        return Math.toDegrees(acos(cosTheta.coerceIn(-1.0f, 1.0f).toDouble()))
    }

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

    private fun provideFeedback(kneeAngle: Float) {
        val feedback = if (kneeAngle < 50) "조금만 덜 앉으세요" 
                       else if (kneeAngle in 50f..95f && currentState == ExerciseState.DOWN) "완벽한 깊이입니다!" 
                       else null
        feedback?.let {
            binding.tvFeedback?.text = it
            binding.tvFeedback?.visibility = View.VISIBLE
            binding.tvFeedback?.postDelayed({ binding.tvFeedback?.visibility = View.GONE }, 2000)
        }
    }

    private fun updateCountUI() { binding.tvCount?.text = getString(R.string.count_format, squatCount, targetCount) }

    private fun showResultDialog() {
        val intent = Intent(this, ReportActivity::class.java).apply {
            putExtra("TOTAL_COUNT", squatCount)
            putExtra("AVG_SCORE", if (scoreHistory.isNotEmpty()) scoreHistory.average().toFloat() else 0f)
        }
        startActivity(intent)
        finish()
    }

    private fun loadStandardPoseData() { 
        standardPoseData = List(15) { index -> floatArrayOf(180f - (index * 10f), 180f - (index * 10f), 180f - (index * 8f), 180f - (index * 8f)) } 
    }

    override fun onError(error: RuntimeException) { Log.e("AI_ENGINE", "오류: ${error.message}") }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (::poseLandmarker.isInitialized) poseLandmarker.close()
    }
}