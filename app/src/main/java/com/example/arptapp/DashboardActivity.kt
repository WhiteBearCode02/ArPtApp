package com.example.arptapp

import android.Manifest     // 안드로이드 시스템 권한 목록
import android.content.content.pm.PackageManager    // 권한 상태 확인용 도구
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts    // 최신 권한 요청 계약 객체
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat      // 구버전 호환성을 위한 권한 체크 도구 
import com.example.arptapp.databinding.ActivityDashboardBinding


 // ArPtApp -> Module: Dashboard
 // 운동 시작 및 개인 맞춤형 데이터 요약 화면 제어 및 권한 획득 관리
 // ViewBinding을 통한 안전한 UI 참조 및 인터랙션 처리
 // 최신 Jetpack Activity Result API를 사용하여 권한 로직을 모듈화

// [1. 추가] 권한 요청 실행기 (Launcher)
    // 사용자의 승인/거절 결과를 비동기로 받아서 처리하는 콜백 객체
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 승인 시: AI 분석을 위한 다음 단계 진행
            Toast.makeText(this, "카메라 권한이 승인되었습니다!", Toast.LENGTH_SHORT).show()
            // TODO: CameraX 프리뷰 화면 실행 로직 추가 예정 [cite: 2025-12-17]
        } else {
            // 거부 시: 사용자에게 권한 필요성을 안내 (UX 배려) [cite: 2025-10-30]
            Toast.makeText(this, "카메라 권한이 없으면 AI 자세 분석 기능을 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // [2. 수정] 버튼 클릭 시 직접 토스트를 띄우지 않고 권한 체크 함수를 호출
        binding.btnStartExercise.setOnClickListener {
            checkCameraPermission()
        }
    }

    /**
     * [3. 추가] 권한 체크 및 요청 로직
     * 현재 앱이 카메라에 접근할 수 있는지 확인하고, 필요시 팝업을 띄움
     */
    private fun checkCameraPermission() {
        when {
            // A. 이미 권한이 있는 경우
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                Toast.makeText(this, "AI 카메라를 준비하고 있습니다...", Toast.LENGTH_SHORT).show()
                // TODO: 실제 카메라 액티비티 실행 코드 진입 지점
            }
            // B. 권한이 없어 요청이 필요한 경우
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }