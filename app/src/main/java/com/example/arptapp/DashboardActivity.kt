package com.example.arptapp

// [안드로이드 기본 및 프레임워크 도구]
import android.Manifest     // 카메라 권한 등 시스템 권한 명칭 관리
import android.content.pm.PackageManager    // 현재 앱의 권한 승인 상태 확인
import android.os.Bundle    // 액티비티 간 데이터 전달 및 상태 보존
import android.widget.Toast // 사용자 알림용 메시지 출력
import androidx.appcompat.app.AppCompatActivity // 호환성을 고려한 기본 액티비티 클래스

// [Jetpack & CameraX 하드웨어 제어 도구]
import androidx.activity.result.contract.ActivityResultContracts // 비동기 권한 요청 시스템
import androidx.core.content.ContextCompat      // 시스템 리소스 및 기능 접근 유틸리티
import androidx.camera.lifecycle.ProcessCameraProvider // 카메라와 액티비티 생명주기 결합
import androidx.camera.core.Preview            // 실시간 카메라 프리뷰 유즈케이스
import androidx.camera.core.CameraSelector     // 전면/후면 카메라 선택
import androidx.camera.core.ImageAnalysis      // AI 분석용 이미지 추출 유즈케이스
import androidx.camera.core.ImageProxy          // 추출된 개별 이미지 데이터 객체
import com.example.arptapp.databinding.ActivityDashboardBinding // UI 컴포넌트 접근용 바인딩 클래스

// [자바 동시성 및 백그라운드 처리 도구]
import java.util.concurrent.ExecutorService    // 비동기 작업을 위한 스레드 관리자
import java.util.concurrent.Executors          // 스레드 풀 생성 유틸리티

// [MediaPipe AI 분석 엔진 도구]
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker // 포즈 분석 엔진 핵심 클래스
import com.google.mediapipe.tasks.vision.core.BaseOptions             // AI 모델 경로 및 옵션 설정
import com.google.mediapipe.tasks.vision.core.RunningMode             // 실시간 스트림 분석 모드 설정

/**
 * [ArPtApp - 대시보드 모듈]
 * 역할: 카메라 권한 획득, 실시간 영상 송출, AI 관절 분석 엔진 구동 및 결과 전달
 */
class DashboardActivity : AppCompatActivity() {

    // [Architecture] 뷰 바인딩 및 백그라운드 스레드 선언
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var cameraExecutor: ExecutorService
    
    // AI 분석 엔진 변수 (MediaPipe Pose Landmarker)
    private var poseLandmarker: PoseLandmarker? = null

    // [기능 1: 권한 요청 실행기] 
    // 사용자에게 권한을 요청하고 승인 여부에 따라 카메라 구동을 결정합니다.
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 권한 허용 시 즉시 카메라 시동
            startCamera()
        } else {
            // 거부 시 사용자에게 기능 제한 안내 (UX 최적화)
            Toast.makeText(this, "카메라 권한이 없으면 AI 분석을 시작할 수 없습니다.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. UI 초기화: ViewBinding을 통해 레이아웃을 메모리에 로드
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. 상호작용 설정: 버튼 클릭 시 카메라 권한 체크 로직 실행
        binding.btnStartExercise.setOnClickListener {
            checkCameraPermission()
        }

        // 3. 엔진 초기화: 분석용 백그라운드 스레드와 AI 모델 세팅
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupPoseLandmarker()
    }

    /**
     * [AI 엔진 초기화] assets 폴더에 배치한 인공지능 모델을 불러옵니다.
     */
    private fun setupPoseLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task") // AI 모델 파일 이름

        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setRunningMode(RunningMode.LIVE_STREAM) // 실시간 비디오 스트림 모드
            .setResultListener { result, _ ->
                // AI 분석 결과가 나오면 UI 스레드에서 시각화 레이어로 전달
                runOnUiThread {
                    if (result.landmarks().isNotEmpty()) {
                        // OverlayView에 분석된 관절 좌표 전달
                        binding.overlayView.setResults(result)
                    }
                }
            }

        poseLandmarker = PoseLandmarker.createFromOptions(this, optionsBuilder.build())
    }

    /**
     * [권한 상태 체크] 현재 앱의 카메라 접근 권한 유무를 판단합니다.
     */
    private fun checkCameraPermission() {
        when {
            // 이미 승인된 경우 바로 카메라 실행
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                    == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            // 미승인 시 권한 요청 팝업 출력
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * [CameraX 엔진 구동] 카메라 렌즈를 활성화하고 영상 출력과 분석 데이터를 연결합니다.
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // 액티비티 생명주기에 종속된 카메라 공급자 획득
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // [Step A] 프리뷰 설정: 화면 송출용 유즈케이스 연결
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            // [Step B] 이미지 분석 설정: AI 엔진에 실시간 데이터를 공급하는 통로
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // 지연 없는 최신 프레임 처리
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        // 분석 전용 함수로 데이터 전달
                        analyzeImage(imageProxy)
                    }
                }

            // [Step C] 카메라 선택: 본인 자세 체크용 전면 카메라 기본값 설정
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // 기존 바인딩 해제 후 프리뷰와 분석 유즈케이스를 동시에 결합
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                
            } catch(exc: Exception) {
                Toast.makeText(this, "카메라 연결에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * [실시간 AI 분석] 카메라의 각 프레임을 AI 엔진이 이해할 수 있는 형식으로 변환합니다.
     */
    private fun analyzeImage(imageProxy: ImageProxy) {
        poseLandmarker?.detectAsync(
            // ImageProxy 데이터를 비트맵으로 변환하여 분석기에 전달
            com.google.mediapipe.framework.image.BitmapImageBuilder(imageProxy.toBitmap()).build(),
            System.currentTimeMillis()
        )
        // 분석이 완료된 프레임은 즉시 해제하여 메모리 관리 (필수)
        imageProxy.close()
    }

    /**
     * [자원 정리] 앱 종료 시 백그라운드 엔진들을 안전하게 폐기하여 메모리 누수를 방지합니다.
     */
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        poseLandmarker?.close()
    }
}