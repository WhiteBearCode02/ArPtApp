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

    // [추가] 실시간 회전 정보를 저장할 변수입니다.
    private var currentRotation = 0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) startCamera() else Toast.makeText(this, "권한 필요", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
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

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.KOREAN
        }
    }

    private fun speakOut(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun setupPoseLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task")
        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setRunningMode(RunningMode.LIVE_STREAM)
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
                            speakOut(squatCount.toString())
                        }

                        // [수정] inputImage.imageInfo 대신 멤버 변수 currentRotation을 사용합니다.
                        // MPImage 객체에는 imageInfo가 없으므로 발생하는 에러를 해결합니다.
                        val isRotated = currentRotation == 90 || currentRotation == 270
                        val rotatedWidth = if (isRotated) inputImage.height else inputImage.width
                        val rotatedHeight = if (isRotated) inputImage.width else inputImage.height

                        binding.overlayView.setResults(
                            result,
                            rotatedHeight,
                            rotatedWidth,
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
        // [수정] 여기서 실시간 회전 각도를 업데이트하여 멤버 변수에 저장합니다.
        currentRotation = imageProxy.imageInfo.rotationDegrees

        val bitmap = imageProxy.toBitmap()
        val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap).build()
        val imageProcessingOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(currentRotation)
            .build()

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