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
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.example.arptapp.databinding.ActivityDashboardBinding
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DashboardActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        init {
            try {
                System.loadLibrary("mediapipe_tasks_vision_jni")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("JNI_ERROR", "라이브러리를 찾을 수 없습니다: ${e.message}")
            }
        }
    }

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var cameraExecutor: ExecutorService
    private var poseLandmarker: PoseLandmarker? = null
    private var tts: TextToSpeech? = null

    private var squatCount = 0
    private var isDown = false
    private var startTime: Long = 0
    private var isExercising = false // 운동 중인지 여부

    // 카메라 관련 변수
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT // 기본: 전면 카메라
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

        // 앱 시작 시 바로 카메라 켜기
        checkCameraPermission()

        // X버튼 - 화면 닫기
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

    // 카메라 전환 (전면 ↔ 후면)
    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }

        // 카메라 재시작
        bindCameraUseCases()
    }

    // 운동 시작
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

    // 운동 종료
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

    private fun setupPoseLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")

        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, inputImage ->
                runOnUiThread {
                    if (result.landmarks().isNotEmpty()) {
                        // 운동 중일 때만 카운트
                        if (isExercising) {
                            processLandmarks(result, inputImage)
                        } else {
                            // 운동 전에도 skeleton은 표시
                            updateOverlay(result, inputImage)
                        }
                    }
                }
            }

        poseLandmarker = PoseLandmarker.createFromOptions(this, optionsBuilder.build())
    }

    private fun processLandmarks(
        result: com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult,
        inputImage: com.google.mediapipe.framework.image.MPImage
    ) {
        val landmarks = result.landmarks()[0]

        // 무릎 각도 계산
        val kneeAngle = calculateAngle(landmarks[23], landmarks[25], landmarks[27])

        if (kneeAngle < 100.0) {
            isDown = true
        } else if (isDown && kneeAngle > 160.0) {
            squatCount++
            isDown = false
            binding.tvCount.text = squatCount.toString()
            speakOut(squatCount.toString())
        }

        updateOverlay(result, inputImage)
    }

    private fun updateOverlay(
        result: com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult,
        inputImage: com.google.mediapipe.framework.image.MPImage
    ) {
        binding.overlayView.setResults(
            result,
            inputImage.height,
            inputImage.width,
            RunningMode.LIVE_STREAM
        )
    }

    private fun calculateAngle(
        first: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
        mid: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
        last: com.google.mediapipe.tasks.components.containers.NormalizedLandmark
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

        // 현재 선택된 카메라
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        // Preview 설정
        preview = Preview.Builder()
            .setTargetRotation(binding.viewFinder.display.rotation)
            .build()
            .also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

        // ImageAnalysis 설정
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetRotation(binding.viewFinder.display.rotation)
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
            Log.e("DashboardActivity", "카메라 바인딩 실패", exc)
            Toast.makeText(this, "카메라 연결 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val bitmap = imageProxy.toBitmap()
        val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap).build()

        // MediaPipe에 회전 정보 전달
        val imageProcessingOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(rotationDegrees)
            .build()

        val frameTime = System.currentTimeMillis()
        poseLandmarker?.detectAsync(mpImage, imageProcessingOptions, frameTime)

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