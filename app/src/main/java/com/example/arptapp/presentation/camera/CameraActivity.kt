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
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.atan2

class CameraActivity : AppCompatActivity(), PoseLandmarker.PoseLandmarkerListener {

    private lateinit var binding: ActivityCameraBinding
    
    // 카메라 관련
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isFrontCamera = true
    
    // MediaPipe 관련
    private lateinit var poseLandmarker: PoseLandmarker
    
    // DTW 및 분석 관련
    private val dtwCalculator = DTWCalculator()
    private val coordinateNormalizer = CoordinateNormalizer()
    private val userPoseSequence = mutableListOf<NormalizedPoseData>()
    private var isRecording = false
    private var currentScore = 0f
    private val scoreHistory = mutableListOf<Float>()
    
    // 운동 카운팅 관련
    private var squatCount = 0
    private var targetCount = 10
    private var currentState = ExerciseState.STANDING
    
    // 표준 자세 데이터 (추후 JSON에서 로드)
    private var standardPoseData: List<FloatArray> = listOf()
    
    // 권한 요청
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
        
        // Intent에서 목표 횟수 받기
        targetCount = intent.getIntExtra("TARGET_COUNT", 10)
        
        // UI 초기화
        updateCountUI()
        
        // Executor 초기화
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // MediaPipe 초기화
        setupPoseLandmarker()
        
        // 권한 확인 및 카메라 시작
        checkCameraPermission()
        
        // 버튼 리스너 설정
        setupButtons()
        
        // 표준 자세 데이터 로드 (임시 더미 데이터)
        loadStandardPoseData()
    }
    
    private fun setupButtons() {
        // 일시정지 버튼
        binding.btnPause.setOnClickListener {
            isRecording = !isRecording
            if (isRecording) {
                binding.btnPause.setImageResource(R.drawable.ic_pause)
                Toast.makeText(this, "운동 재개", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnPause.setImageResource(R.drawable.ic_play_arrow)
                Toast.makeText(this, "일시정지", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 종료 버튼
        binding.btnStop.setOnClickListener {
            showResultDialog()
        }
        
        // 카메라 전환 버튼
        binding.btnFlipCamera.setOnClickListener {
            isFrontCamera = !isFrontCamera
            setupCamera()
        }
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                setupCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun setupPoseLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_heavy.task")
            .build()
        
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(this)
            .setErrorListener { error ->
                Log.e("CameraActivity", "PoseLandmarker error: ${error.message}")
            }
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
        
        val cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        
        // Preview
        preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
        
        // ImageAnalysis
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
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        } catch (e: Exception) {
            Log.e("CameraActivity", "Camera binding failed", e)
        }
    }
    
    private fun detectPose(imageProxy: ImageProxy) {
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )
        imageProxy.use { 
            bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) 
        }
        
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
        
        poseLandmarker.detectAsync(mpImage, frameTime)
        
        imageProxy.close()
    }
    
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
                
                // 3. 무릎 각도 계산
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
                            currentState = ExerciseState.STANDING
                            
                            // 카운트 증가
                            squatCount++
                            updateCountUI()
                            
                            // DTW 유사도 측정
                            if (userPoseSequence.size >= 10) {
                                calculateSimilarityScore()
                            }
                            
                            // 목표 달성 시
                            if (squatCount >= targetCount) {
                                showResultDialog()
                            }
                            
                            isRecording = false
                        }
                    }
                }
                
                // 5. 오버레이 업데이트
                binding.overlay.setResults(
                    result,
                    input.height,
                    input.width,
                    RunningMode.LIVE_STREAM
                )
                
                // 6. 실시간 피드백
                provideFeedback(angle)
            }
        }
    }
    
    override fun onError(error: RuntimeException) {
        Log.e("CameraActivity", "PoseLandmarker error: ${error.message}")
    }
    
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
    
    private fun calculateSimilarityScore() {
        if (standardPoseData.isEmpty() || userPoseSequence.isEmpty()) return
        
        val userSequence = userPoseSequence.map { pose ->
            pose.angles.values.toFloatArray()
        }
        
        val weights = dtwCalculator.getExerciseWeights("SQUAT")
        val dtwDistance = dtwCalculator.calculateDTWDistance(
            userSequence,
            standardPoseData,
            weights
        )
        
        currentScore = dtwCalculator.convertToScore(dtwDistance)
        scoreHistory.add(currentScore)
        
        runOnUiThread {
            binding.tvScore.text = "정확도: ${currentScore.toInt()}%"
            binding.tvScore.setTextColor(
                when {
                    currentScore >= 90 -> getColor(android.R.color.holo_green_light)
                    currentScore >= 70 -> getColor(android.R.color.holo_orange_light)
                    else -> getColor(android.R.color.holo_red_light)
                }
            )
        }
        
        userPoseSequence.clear()
    }
    
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
            
            binding.tvFeedback.postDelayed({
                binding.tvFeedback.visibility = View.GONE
            }, 3000)
        }
    }
    
    private fun updateCountUI() {
        binding.tvCount.text = "$squatCount / $targetCount"
    }
    
    private fun showResultDialog() {
        val intent = Intent(this, ReportActivity::class.java).apply {
            putExtra("EXERCISE_TYPE", "스쿼트")
            putExtra("TOTAL_COUNT", squatCount)
            putExtra("AVG_SCORE", if (scoreHistory.isNotEmpty()) {
                scoreHistory.average().toFloat()
            } else 0f)
            putExtra("SCORES", scoreHistory.toFloatArray())
        }
        startActivity(intent)
        finish()
    }
    
    private fun loadStandardPoseData() {
        // TODO: Assets에서 JSON 로드
        // 임시 더미 데이터
        standardPoseData = List(15) { index ->
            floatArrayOf(
                180f - (index * 10f), // LEFT_KNEE
                180f - (index * 10f), // RIGHT_KNEE
                180f - (index * 8f),  // LEFT_HIP
                180f - (index * 8f)   // RIGHT_HIP
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        poseLandmarker.close()
    }
}

enum class ExerciseState {
    STANDING,
    GOING_DOWN,
    DOWN,
    GOING_UP
}
