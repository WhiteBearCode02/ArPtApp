package com.example.arptapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
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
import com.example.arptapp.domain.analyzer.BaseExerciseAnalyzer
import com.example.arptapp.domain.analyzer.SquatAnalyzer
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
 * - [방법1] 프레임 회전 정규화: 270도/90도 프레임을 0도로 변환하여 MediaPipe에 전달
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
    private var startTime: Long = 0
    private var isExercising = false
    // BaseExerciseAnalyzer 규격을 따르는 SquatAnalyzer를 기본값으로 세팅합니다.
    private var exerciseAnalyzer: BaseExerciseAnalyzer = SquatAnalyzer()

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

        exerciseAnalyzer.reset()
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
                        // [방법1] 이미 정규화된 비트맵으로 처리되었으므로
                        // imageWidth, imageHeight는 회전 후의 최종 크기
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
        // 1. 전문가(Analyzer)에게 분석을 시키고 최신 카운트 숫자를 받아옵니다.
        val currentCount = exerciseAnalyzer.analyze(landmarks)

        // 2. 숫자가 올라갔다면 (운동 1번 성공), 화면을 갱신하고 목소리로 알려줍니다.
        if (currentCount > squatCount) {
            squatCount = currentCount
            binding.tvCount.text = squatCount.toString()
            speakOut(squatCount.toString())
        }
    }

    /**
     * 오버레이 업데이트
     *
     * [중요] 방법1: 이미 회전된 비트맵으로 처리되었으므로
     * inputImage의 width, height는 회전 후의 최종 크기입니다.
     * OverlayView에서는 단순한 좌표 변환만 수행하면 됩니다.
     */
    private fun updateOverlay(
        landmarks: List<NormalizedLandmark>,
        inputImage: com.google.mediapipe.framework.image.MPImage
    ) {
        // 필터링된 단일 사람 데이터를 OverlayView에 전달
        // rotationDegrees 파라미터 제거 - 이미 정규화됨
        binding.overlayView.setResults(
            landmarks,
            inputImage.width,
            inputImage.height,
            lensFacing == CameraSelector.LENS_FACING_FRONT
        )
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
     * [방법1 핵심] 여기서 회전된 프레임을 정규화(0도)로 변환하여 MediaPipe에 전달
     * - 전면 카메라: 270도 회전 프레임 → 0도로 정규화
     * - 후면 카메라: 90도 회전 프레임 → 0도로 정규화
     */
    private fun analyzeImage(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        Log.d("ARPT_METHOD1", "원본 프레임 회전 각도: ${rotationDegrees}도")

        // [방법1 핵심] 비트맵을 회전하여 0도로 정규화
        val rotatedBitmap = if (rotationDegrees != 0) {
            rotateMatrix(bitmap, rotationDegrees)
        } else {
            bitmap
        }

        Log.d("ARPT_METHOD1", "정규화 후 비트맵 크기: ${rotatedBitmap.width}x${rotatedBitmap.height}")

        // 정규화된 비트맵으로 MPImage 생성
        val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(rotatedBitmap).build()

        // MediaPipe에 전달 (회전 정보 없음 - 이미 0도)
        val frameTime = System.currentTimeMillis()
        poseLandmarker?.detectAsync(mpImage, frameTime)

        imageProxy.close()
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
            // 비트맵 중심을 기준으로 회전
            postRotate(degrees.toFloat(), bitmap.width / 2f, bitmap.height / 2f)
        }

        // 회전된 비트맵 생성
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )

        // 원본 비트맵과 다른 객체면 메모리 정리
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }

        return rotatedBitmap
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        cameraExecutor.shutdown()
        poseLandmarker?.close()
    }
}