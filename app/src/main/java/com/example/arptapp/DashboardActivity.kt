package com.example.arptapp

// [Imports: 안드로이드 프레임워크 유틸리티]
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// [Imports: CameraX 및 MediaPipe AI 라이브러리]
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.mediapipe.tasks.vision.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.example.arptapp.databinding.ActivityDashboardBinding

// [Imports: 비동기 처리를 위한 자바 유틸리티]
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * [ArPtApp - Dashboard Module]
 * 본 클래스는 카메라 제어, AI 모델 구동, 운동 상태 판단 및 결과 전송의 핵심 제어부(Controller)입니다.
 */
class DashboardActivity : AppCompatActivity() {

    // [Architecture: ViewBinding & Threading]
    // XML 뷰 인스턴스에 안전하게 접근하기 위한 바인딩 객체
    private lateinit var binding: ActivityDashboardBinding
    // 카메라 분석 데이터 처리를 위한 단일 스레드 풀
    private lateinit var cameraExecutor: ExecutorService
    
    // [AI Engine: MediaPipe]
    // 포즈 분석 및 관절 랜드마크 추출 엔진
    private var poseLandmarker: PoseLandmarker? = null
    
    // [Business Logic: State Management]
    // 누적 운동 횟수 (스쿼트 개수)
    private var squatCount = 0
    // 하강 상태 여부를 판단하는 플래그 (스쿼트 횟수 중복 카운팅 방지)
    private var isDown = false
    // 운동 시작 버튼 클릭 시점의 타임스탬프 (ms 단위)
    private var startTime: Long = 0

    // [Permission: 카메라 권한 요청 핸들러]
    // 비동기적으로 카메라 권한 승인 여부를 확인하고 콜백을 실행함
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 권한 승인 시 즉시 하드웨어 카메라 구동
            startCamera()
        } else {
            // 거부 시 UX 가이드 제공
            Toast.makeText(this, "카메라 권한이 승인되어야 AI 자세 분석이 가능합니다.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. 레이아웃 인플레이션: XML 코드를 메모리 상의 View 객체로 변환
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. [Event] 운동 시작 버튼 리스너
        binding.btnStartExercise.setOnClickListener {
            // 시작 시점 기록 (측정 시작)
            startTime = System.currentTimeMillis()
            // 런타임 권한 확인 및 카메라 시동
            checkCameraPermission()
            
            // UX 업데이트: 시작 버튼 제거 및 종료 버튼 노출 (상태 전이)
            binding.btnStartExercise.visibility = View.GONE
            binding.btnEndExercise.visibility = View.VISIBLE
        }

        // 3. [Event] 운동 종료 버튼 리스너
        binding.btnEndExercise.setOnClickListener {
            // 현재 시간과 시작 시간을 비교하여 소요 시간(초) 계산
            val elapsedTime = if (startTime > 0L) (System.currentTimeMillis() - startTime) / 1000 else 0L

            // [Intent] ResultActivity로 데이터 패키징 전송
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra("TOTAL_COUNT", squatCount)    // 누적 횟수 데이터 삽입
                putExtra("EXERCISE_TIME", elapsedTime) // 소요 시간 데이터 삽입
            }
            
            // 결과 화면 전환 및 현재 대시보드 스택에서 제거 (보안 및 UX 최적화)
            startActivity(intent)
            finish()
        }

        // 4. 리소스 초기화: 백그라운드 스레드 및 AI 분석 엔진 세팅
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupPoseLandmarker()
    }

    /**
     * [AI 설정] MediaPipe Pose Landmarker 엔진의 옵션을 설정하고 모델을 로드합니다.
     */
    private fun setupPoseLandmarker() {
        // AI 모델 파일(assets) 경로 설정
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")

        // 엔진 가동 옵션 (실시간 스트림 모드, 결과 리스너 등록)
        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ ->
                // AI 분석 결과는 별도 스레드에서 반환되므로 UI 업데이트를 위해 메인 스레드로 전환
                runOnUiThread {
                    if (result.landmarks().isNotEmpty()) {
                        // 첫 번째 감지된 인원의 관절 좌표 리스트 획득
                        val landmarks = result.landmarks()[0]
                        
                        // [알고리즘: 사잇각 산출] 골반-무릎-발목 사이의 각도를 벡터로 계산
                        val kneeAngle = calculateAngle(landmarks[23], landmarks[25], landmarks[27])
                        
                        // [상태 머신: 카운팅 로직]
                        // 1. 각도가 100도 미만일 때 '앉음' 상태로 판단
                        if (kneeAngle < 100.0) {
                            isDown = true
                        } 
                        // 2. 이전에 앉은 상태였고, 현재 다시 일어서서 각도가 160도를 넘으면 1회 성공
                        else if (isDown && kneeAngle > 160.0) {
                            squatCount++
                            isDown = false // 상태 초기화
                            binding.tvDashboardTitle.text = "현재 스쿼트: ${squatCount}회"
                        }
                        
                        // 시각화 레이어(OverlayView)에 최신 좌표 데이터 전달하여 드로잉 요청
                        binding.overlayView.setResults(result)
                    }
                }
            }

        // 설정된 옵션으로 엔진 객체 생성
        poseLandmarker = PoseLandmarker.createFromOptions(this, optionsBuilder.build())
    }

    /**
     * [수학 연산] 세 관절의 랜드마크 좌표를 이용하여 유클리드 사잇각을 도(Degree) 단위로 반환합니다.
     */
    private fun calculateAngle(
        first: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
        mid: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
        last: com.google.mediapipe.tasks.components.containers.NormalizedLandmark
    ): Double {
        // 아크탄젠트 연산을 이용한 두 벡터 사이의 라디안 차이 계산
        val radians = Math.atan2((last.y() - mid.y()).toDouble(), (last.x() - mid.x()).toDouble()) -
                      Math.atan2((first.y() - mid.y()).toDouble(), (first.x() - mid.x()).toDouble())
        var angle = Math.abs(radians * 180.0 / Math.PI)
        
        // 180도를 초과할 경우 내각 산출을 위한 보정
        if (angle > 180.0) angle = 360.0 - angle
        return angle
    }

    /**
     * [카메라 시동] CameraX API를 활용하여 하드웨어 렌즈와 이미지 분석 파이프라인을 바인딩합니다.
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            
            // 1. 영상 송출 레이어 설정
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            
            // 2. 실시간 프레임 분석 레이어 설정
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // 최신 프레임 우선순위 전략
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy -> analyzeImage(imageProxy) }
                }

            try {
                // 기존의 카메라 연결을 모두 해제하고 전면 카메라를 수명 주기에 바인딩
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Toast.makeText(this, "카메라 연결에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * [이미지 처리] 카메라 프레임을 비트맵으로 변환하여 AI 분석 엔진에 실시간으로 공급합니다.
     */
    private fun analyzeImage(imageProxy: ImageProxy) {
        poseLandmarker?.detectAsync(
            com.google.mediapipe.framework.image.BitmapImageBuilder(imageProxy.toBitmap()).build(),
            System.currentTimeMillis()
        )
        // 메모리 누수 방지를 위해 분석 완료 후 프레임 객체 해제
        imageProxy.close()
    }

    /**
     * [리소스 해제] 액티비티가 소멸될 때 백그라운드 스레드 및 AI 엔진을 안전하게 종료합니다.
     */
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        poseLandmarker?.close()
    }

    /**
     * [권한 체크] 런타임에 카메라 권한 상태를 확인합니다.
     */
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}