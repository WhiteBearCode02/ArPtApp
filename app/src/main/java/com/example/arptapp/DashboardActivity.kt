package com.example.arptapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.mediapipe.tasks.vision.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.example.arptapp.databinding.ActivityDashboardBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 실시간 카메라 피드를 통해 사용자의 포즈를 분석하고 운동 횟수를 측정하는 화면입니다.
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var cameraExecutor: ExecutorService
    private var poseLandmarker: PoseLandmarker? = null
    
    // 운동 상태 및 횟수 관리 변수
    private var squatCount = 0
    private var isDown = false
    private var startTime: Long = 0

    // 카메라 권한 요청 콜백
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 운동 시작 버튼: 시간 측정 시작 및 카메라 구동
        binding.btnStartExercise.setOnClickListener {
            startTime = System.currentTimeMillis()
            checkCameraPermission()
            binding.btnStartExercise.visibility = View.GONE
            binding.btnEndExercise.visibility = View.VISIBLE
        }

        // 운동 종료 버튼: 결과 화면으로 데이터 전달 및 이동
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

    // MediaPipe 포즈 랜드마크 엔진 초기 설정
    private fun setupPoseLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")

        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ ->
                runOnUiThread {
                    if (result.landmarks().isNotEmpty()) {
                        val landmarks = result.landmarks()[0]
                        
                        // 골반(23), 무릎(25), 발목(27) 사이의 각도 계산
                        val kneeAngle = calculateAngle(landmarks[23], landmarks[25], landmarks[27])
                        
                        // 스쿼트 상태 판단 로직 (100도 미만 하강, 160도 이상 상승 시 1회)
                        if (kneeAngle < 100.0) {
                            isDown = true
                        } else if (isDown && kneeAngle > 160.0) {
                            squatCount++
                            isDown = false
                            binding.tvDashboardTitle.text = "현재 스쿼트: ${squatCount}회"
                        }
                        
                        // 분석 결과를 화면 오버레이 레이어에 전달
                        binding.overlayView.setResults(result)
                    }
                }
            }

        poseLandmarker = PoseLandmarker.createFromOptions(this, optionsBuilder.build())
    }

    // 세 좌표를 이용한 사잇각 계산 (벡터 연산)
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

    // CameraX 초기화 및 분석기(Analyzer) 바인딩
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy -> analyzeImage(imageProxy) }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Toast.makeText(this, "카메라 연결 실패", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // 카메라 프레임을 비트맵으로 변환하여 AI 엔진에 전달
    private fun analyzeImage(imageProxy: ImageProxy) {
        poseLandmarker?.detectAsync(
            com.google.mediapipe.framework.image.BitmapImageBuilder(imageProxy.toBitmap()).build(),
            System.currentTimeMillis()
        )
        imageProxy.close()
    }

    // 액티비티 종료 시 리소스 해제
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        poseLandmarker?.close()
    }

    // 카메라 권한 상태 확인
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}