package com.example.arptapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.example.arptapp.databinding.ActivityDashboardBinding
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 실시간 AI 기반 운동 자세 분석 대시보드
 *
 * [핵심 기능]
 * - 가로/세로 자동 대응
 * - 다중 인식 필터링 (가장 큰 사람만 추적)
 * - MediaPipe Pose Landmarker 실시간 추론
 */
class DashboardActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "DashboardActivity"

        init {
            try {
                System.loadLibrary("mediapipe_tasks_vision_jni")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "MediaPipe JNI 라이브러리 로드 실패: ${e.message}")
            }
        }
    }

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var cameraExecutor: ExecutorService
    private var poseLandmarker: PoseLandmarker? = null
    private var tts: TextToSpeech? = null

    // 운동 카운팅 상태
    private var squatCount = 0
    private var isDown = false
    private var startTime: Long = 0
    private var isExercising = false

    // 카메라 관련
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tts = TextToSpeech(this, this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupPoseLandmarker()
        checkCameraPermission()
        setupButtons()
    }

    private fun setupButtons() {
        // 닫기 버튼
        binding.btnClose.setOnClickListener {
            finish()
        }

        // 카메라 전환 버튼
        binding.btnSwitchCamera.setOnClickListener {
            switchCamera()
        }

        // 운동 시작 버튼
        binding.btnStartExercise.setOnClickListener {
            startExercise()
        }

        // 운동 종료 버튼
        binding.btnEndExercise.setOnClickListener {
            endExercise()
        }
    }

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        bindCameraUseCases()
        Toast.makeText(
            this,
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) "전면 카메라" else "후면 카메라",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun startExercise() {
        isExercising = true
        startTime = System.currentTimeMillis()
        squatCount = 0
        binding.tvCount.text = "0"

        binding.tvDashboardTitle.text = "운동 중"
        binding.btnStartExercise.visibility = View.GONE
        binding.btnEndExercise.visibility = View.VISIBLE

        speakOut("운동을 시작합니다")
    }

    private fun endExercise() {
        isExercising = false
        val elapsedTime = if (startTime > 0L) {
            (System.currentTimeMillis() - startTime) / 1000
        } else 0L

        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra("TOTAL_COUNT", squatCount)
            putExtra("EXERCISE_TIME", elapsedTime)
        }
        startActivity(intent)
        finish()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.KOREAN
        }
    }

    private fun speakOut(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    /**
     * MediaPipe Pose Landmarker 초기화
     */
    private fun setupPoseLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")

        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, inputImage ->
                runOnUiThread {
                    // 다중 인식 필터링: 가장 큰 사람만 선택
                    val mainPersonLandmarks = selectMainPerson(result)

                    if (mainPersonLandmarks != null) {
                        if (isExercising) {
                            processLandmarks(mainPersonLandmarks)
                        }
                        // 오버레이 업데이트 (필터링된 결과 전달)
                        updateOverlay(mainPersonLandmarks, inputImage)
                    }
                }
            }
            .setErrorListener { error ->
                Log.e(TAG, "PoseLandmarker 오류: ${error.message}")
            }

        try {
            poseLandmarker = PoseLandmarker.createFromOptions(this, optionsBuilder.build())
        } catch (e: Exception) {
            Log.e(TAG, "PoseLandmarker 초기화 실패", e)
        }
    }

    /**
     * 다중 인식 필터링: 가장 큰 바운딩 박스를 가진 사람 선택
     *
     * [로직]
     * 1. 여러 사람 감지 시 바운딩 박스 면적 계산
     * 2. 가장 큰 면적을 가진 사람 = 카메라에 가장 가까운 사람
     * 3. 거울 반사나 배경 사람 자동 필터링
     */
    private fun selectMainPerson(
        result: com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
    ): List<NormalizedLandmark>? {
        val allLandmarks = result.landmarks()

        if (allLandmarks.isEmpty()) {
            return null
        }

        // 사람이 1명이면 바로 반환
        if (allLandmarks.size == 1) {
            return allLandmarks[0]
        }

        // 여러 사람 중 바운딩 박스가 가장 큰 사람 선택
        return allLandmarks.maxByOrNull { landmarks ->
            calculateBoundingBoxArea(landmarks)
        }
    }

    /**
     * 바운딩 박스 면적 계산
     *
     * @param landmarks 한 사람의 33개 관절 좌표
     * @return 면적 (0~1 범위)
     */
    private fun calculateBoundingBoxArea(landmarks: List<NormalizedLandmark>): Float {
        val xCoords = landmarks.map { it.x() }
        val yCoords = landmarks.map { it.y() }

        val minX = xCoords.minOrNull() ?: 0f
        val maxX = xCoords.maxOrNull() ?: 0f
        val minY = yCoords.minOrNull() ?: 0f
        val maxY = yCoords.maxOrNull() ?: 0f

        val width = maxX - minX
        val height = maxY - minY

        return width * height
    }

    /**
     * 운동 동작 분석 (스쿼트 카운팅)
     */
    private fun processLandmarks(landmarks: List<NormalizedLandmark>) {
        // 무릎 각도 계산 (힙-무릎-발목)
        val kneeAngle = calculateAngle(
            landmarks[23], // 왼쪽 힙
            landmarks[25], // 왼쪽 무릎
            landmarks[27]  // 왼쪽 발목
        )

        // 상태 머신 기반 카운팅
        if (kneeAngle < 100.0) {
            isDown = true
        } else if (isDown && kneeAngle > 160.0) {
            squatCount++
            isDown = false
            binding.tvCount.text = squatCount.toString()
            speakOut(squatCount.toString())
        }
    }

    /**
     * 오버레이 업데이트
     *
     * [중요] 가로/세로 자동 대응을 위해 원본 이미지 크기 그대로 전달
     */
    private fun updateOverlay(
        landmarks: List<NormalizedLandmark>,
        inputImage: com.google.mediapipe.framework.image.MPImage
    ) {
        // 필터링된 단일 사람 데이터를 OverlayView에 전달
        binding.overlayView.setResults(
            landmarks,
            inputImage.width,
            inputImage.height,
            lensFacing == CameraSelector.LENS_FACING_FRONT
        )
    }

    /**
     * 3점 기반 각도 계산
     */
    private fun calculateAngle(
        first: NormalizedLandmark,
        mid: NormalizedLandmark,
        last: NormalizedLandmark
    ): Double {
        val radians = Math.atan2((last.y() - mid.y()).toDouble(), (last.x() - mid.x()).toDouble()) -
                Math.atan2((first.y() - mid.y()).toDouble(), (first.x() - mid.x()).toDouble())
        var angle = Math.abs(radians * 180.0 / Math.PI)
        if (angle > 180.0) angle = 360.0 - angle
        return angle
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    analyzeImage(imageProxy)
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
        } catch (exc: Exception) {
            Log.e(TAG, "카메라 바인딩 실패", exc)
            Toast.makeText(this, "카메라 연결 실패", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 프레임 분석
     *
     * [중요] 회전 정보 없이 전달 → MediaPipe 자동 처리 비활성화
     */
    private fun analyzeImage(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap).build()

        // [로그 추가] 센서가 이미지를 몇 도 돌려서 보내주는지 확인합니다.
        android.util.Log.d("ARPT_DEBUG", "3. 프레임 회전 각도: ${bitmap}도")

        // 회전 정보 없이 전달 (MediaPipe가 원본 그대로 처리)
        val frameTime = System.currentTimeMillis()
        poseLandmarker?.detectAsync(mpImage, frameTime)

        imageProxy.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        cameraExecutor.shutdown()
        poseLandmarker?.close()
    }
}