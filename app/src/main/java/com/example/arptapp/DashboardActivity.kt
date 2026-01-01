package com.example.arptapp

// [Imports: 안드로이드 기본 및 UI 도구]
import android.Manifest     // 카메라 등 시스템 권한의 명칭을 담고 있는 클래스
import android.content.pm.PackageManager    // 현재 앱의 권한 승인 상태를 확인하는 도구
import android.os.Bundle    // 액티비티 상태 데이터를 전달하는 바구니
import android.widget.Toast // 사용자에게 짧은 알림 메시지를 보여주는 기능
import androidx.appcompat.app.AppCompatActivity // 안드로이드의 기본 액티비티 기능을 제공하는 상위 클래스

// [Imports: Jetpack & CameraX 라이브러리]
import androidx.activity.result.contract.ActivityResultContracts // 최신 권한 요청 시스템(Launcher)을 위한 도구
import androidx.core.content.ContextCompat      // 버전 호환성을 지키며 시스템 기능을 호출하는 유틸리티
import androidx.camera.lifecycle.ProcessCameraProvider // 카메라의 수명 주기를 앱과 결합해주는 핵심 클래스
import androidx.camera.core.Preview            // 카메라 영상을 화면에 보여주는 '유즈케이스'
import androidx.camera.core.CameraSelector     // 전면/후면 카메라를 선택하는 도구
import com.example.arptapp.databinding.ActivityDashboardBinding // XML 뷰들을 코틀린과 연결하는 바인딩 클래스

// [Imports: 자바 동시성 및 스레드 도구]
import java.util.concurrent.ExecutorService    // 백그라운드에서 작업을 처리할 스레드 관리자
import java.util.concurrent.Executors          // 스레드 풀을 생성해주는 팩토리 클래스

/**
 * [ArPtApp - Dashboard Module]
 * 역할: 로그인 후 메인 대시보드 관리, 카메라 권한 요청, 실시간 영상 송출
 */
class DashboardActivity : AppCompatActivity() {

    // [Architecture] 뷰 바인딩 및 카메라 스레드 매니저 선언
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var cameraExecutor: ExecutorService

    // [Feature 1: 권한 요청 실행기] 
    // 사용자에게 권한 팝업을 띄우고, 그 결과를 비동기로 전달받아 다음 행동(카메라 실행)을 결정함
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 사용자가 '허용'을 누르면 즉시 카메라 엔진을 실행함
            startCamera()
        } else {
            // 사용자가 '거부'하면 기능 이용이 불가함을 알림
            Toast.makeText(this, "카메라 권한이 없으면 AI 분석을 시작할 수 없습니다.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. 뷰 바인딩 초기화: XML 레이아웃을 메모리에 올림
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. 버튼 이벤트 설정: 'AI 운동 분석 시작하기' 클릭 시 권한 체크 함수 실행
        binding.btnStartExercise.setOnClickListener {
            checkCameraPermission()
        }

        // 3. 카메라 처리용 전용 스레드 생성 (메인 화면이 멈추지 않게 백그라운드에서 처리)
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    /**
     * [Logic: 권한 상태 체크]
     * 이미 권한이 있는지 확인하고, 상황에 따라 카메라를 켜거나 권한을 요청함
     */
    private fun checkCameraPermission() {
        when {
            // 이미 권한을 허용한 경우: 바로 카메라 시동
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                    == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            // 권한이 없는 경우: 사용자에게 시스템 팝업을 띄움
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * [Feature 2: CameraX 구동 엔진]
     * 하드웨어 렌즈를 깨우고, 실시간 영상을 XML의 PreviewView에 연결함
     */
    private fun startCamera() {
        // 카메라 프로바인더 객체 획득 (비동기 방식)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // 현재 액티비티의 수명 주기에 바인딩할 준비
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // [Step A] 프리뷰 설정: 화면에 보여줄 영상의 옵션을 정함
            val preview = Preview.Builder().build().also {
                // XML에 정의한 viewFinder(PreviewView)의 Surface와 연결
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            // [Step B] 카메라 선택: 본인 자세를 체크해야 하므로 전면(Front) 카메라 선택
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // [Step C] 기존 연결 해제 후 새로운 수명 주기에 카메라를 결합
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch(exc: Exception) {
                // 기기 문제나 다른 앱 점유 등으로 실패 시 안내
                Toast.makeText(this, "카메라 연결에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this)) // 메인 스레드에서 UI를 업데이트하도록 설정
    }

    // [Cleanup: 리소스 정리]
    // 액티비티가 닫힐 때 백그라운드 스레드를 안전하게 종료하여 메모리 누수를 방지함
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}