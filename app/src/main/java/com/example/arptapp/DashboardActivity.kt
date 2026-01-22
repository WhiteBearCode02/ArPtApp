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
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions // [추가] 이미지 처리 옵션을 위한 임포트
import com.example.arptapp.databinding.ActivityDashboardBinding
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 실시간 카메라 분석을 통한 운동 카운팅 및 음성 피드백 제어부입니다.
 */
class DashboardActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        init {
            try {
                // 시스템에게 명시적으로 라이브러리 로드를 명령합니다.
                System.loadLibrary("mediapipe_tasks_vision_jni")
            } catch (e: UnsatisfiedLinkError) {
                // 에러 발생 시 로그를 통해 원인을 파악합니다.
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) startCamera() else Toast.makeText(this, "권한 필요", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 음성 출력 엔진 초기화
        tts = TextToSpeech(this, this)

        binding.btnStartExercise.setOnClickListener {
            startTime = System.currentTimeMillis()
            checkCameraPermission()
            binding.btnStartExercise.visibility = View.GONE
            binding.btnEndExercise.visibility = View.VISIBLE
            speakOut("운동을 시작합니다.")
        }

        binding.btnEndExercise.setOnClickListener {
            val elapsedTime = if (startTime > 0L) (System.currentTimeMillis() - startTime) / 1000 else 0L
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra("TOTAL_COUNT", squatCount)
                putExtra("EXERCISE_TIME", elapsedTime)
            }
            startActivity(intent)
            finish()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupPoseLandmarker()
    }

    // TTS 초기화 완료 시 호출되는 콜백
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.KOREAN
        }
    }

    // 텍스트를 음성으로 변환하여 출력
    private fun speakOut(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun setupPoseLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task")
        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            // [수정] 두 번째 파라미터인 inputImage를 활용하여 실제 분석된 이미지의 크기를 가져옵니다.
            .setResultListener { result, inputImage ->
                runOnUiThread {
                    if (result.landmarks().isNotEmpty()) {
                        val landmarks = result.landmarks()[0]
                        val kneeAngle = calculateAngle(landmarks[23], landmarks[25], landmarks[27])

                        if (kneeAngle < 100.0) {
                            isDown = true
                        } else if (isDown && kneeAngle > 160.0) {
                            squatCount++
                            isDown = false
                            binding.tvCount.text = squatCount.toString()
                            // 카운트 시 음성 피드백 제공
                            speakOut(squatCount.toString())
                        }

                        // [수정] 480, 640 고정값 대신 분석 엔진이 실제로 처리한 이미지의 너비와 높이를 전달합니다.
                        // 이를 통해 OverlayView가 뼈대를 정확한 비율로 화면에 그릴 수 있게 됩니다.
                        binding.overlayView.setResults(
                            result,
                            inputImage.height,
                            inputImage.width,
                            RunningMode.LIVE_STREAM
                        )
                    }
                }
            }
        poseLandmarker = PoseLandmarker.createFromOptions(this, optionsBuilder.build())
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

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor) { imageProxy -> analyzeImage(imageProxy) } }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Toast.makeText(this, "카메라 연결 실패", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        // [추가] 카메라 센서의 물리적 회전 각도를 가져옵니다 (세로 모드 시 보통 270도).
        val imageRotation = imageProxy.imageInfo.rotationDegrees

        val bitmap = imageProxy.toBitmap()
        val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap).build()

        // [추가] MediaPipe 분석 엔진에게 "이 이미지는 회전되어 있다"는 정보를 전달할 옵션을 구성합니다.
        // 이 과정이 없으면 엔진은 이미지가 누워있다고 판단하여 좌표를 거꾸로 계산합니다.
        val imageProcessingOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(imageRotation)
            .build()

        // [수정] 회전 옵션을 포함하여 비동기 분석을 요청합니다.
        poseLandmarker?.detectAsync(
            mpImage,
            imageProcessingOptions,
            System.currentTimeMillis()
        )

        imageProxy.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        cameraExecutor.shutdown()
        poseLandmarker?.close()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}