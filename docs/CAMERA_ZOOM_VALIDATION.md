# CameraX 운동 카메라 Zoom

## 구현 범위

- 기존 XML / ViewBinding / CameraX **1.3.1** 유지. 새 SDK, 네트워크 호출, 유료 API 없음.
- `PoseCameraController`가 현재 `bindToLifecycle()`에서 반환된 Camera를 줌 어댑터에 전달한다.
- 화면은 `CameraZoomControl`의 `setZoomRatio`, `scaleZoom`, `resetZoom`, `zoomState`만 사용한다.
- `CameraXZoomController`가 `CameraInfo.zoomState`를 lifecycle 관찰하고 `CameraControl.setZoomRatio()`를 호출한다. 최댓값을 앱에서 정하지 않는다.
- `CameraZoomPolicy`는 실제 min/max clamp, 비정상 수치 거부, 기본 1.0x(범위 밖이면 min), 고정 배율 fallback을 담당한다.
- `CameraZoomSession`은 바인딩 generation / 요청 ID로 이전 렌즈의 observer·future 완료를 무시한다. 빠른 핀치의 요청 배율은 누적하되 표시 배율은 CameraX 관찰값으로만 갱신한다.
- 카메라 재바인딩 시 observer와 이전 카메라 참조를 먼저 해제하고 새 범위로 초기화한다. pause 중 요청은 거부하며 destroy 후에는 무효다.

## 화면 / 제스처

```text
CameraZoomGestureLayout (기존 전체 화면 root 확장)
├─ PreviewView + 기존 관절 Overlay
└─ dashboardChrome
   ├─ 고정 헤더: 뒤로가기 / 제목·렌즈 / 카메라 전환
   ├─ bounded NestedScrollView
   │  ├─ 인식 칩 / 시작 전 설정 또는 운동 중 요약
   │  ├─ 작은 배율·초기화 버튼 (48dp 터치 영역)
   │  ├─ 비어 있는 가변 카메라 공간
   │  ├─ 기존 접이식 YOLO 진단
   │  └─ 운동 횟수·안내
   └─ 고정 시작/종료 CTA
```

세로/가로 레이아웃 모두 본문 흐름 안에 배율 버튼을 둔다. 짧은 화면에서는 기존 본문 스크롤을 사용한다. 헤더/CTA를 덮는 절대 위치 overlay는 추가하지 않는다.

기존 NestedScrollView가 프리뷰 위에 있기 때문에 PreviewView에만 터치 리스너를 설치하지 않는다. root는 **빈 카메라 영역에서 시작한 정확히 두 손가락**만 가져온다. 보호 대상은 헤더, 인식 칩, 설정/요약, YOLO 패널, 횟수/안내, CTA, 배율 버튼이다. 현재 보이는 전역 영역을 검사하므로 스크롤 clipping도 반영한다.

한 손가락 탭·세로 스크롤은 기존 자식 dispatch를 유지한다. 핀치를 가져오는 순간 자식에 CANCEL을 한 번 보내고 마지막 UP까지 소유하여 남은 손가락이 버튼 클릭으로 바뀌지 않게 한다. quick-scale/double-tap 및 stylus-scale은 사용하지 않는다. pause/detach 때 제스처를 취소한다. UI 문자열은 소수 첫째 자리나 사용 가능 상태가 바뀔 때만 갱신한다.

## Preview / ImageAnalysis 영향: UI 확대가 아님

이 구현은 `scaleX/scaleY`, Preview transform, Bitmap 확대를 사용하지 않는다. [공식 CameraX 설명](https://developer.android.com/media/camera/camerax/configuration#control-camera-output)에 따르면 CameraControl의 공통 제어는 같은 Camera에 바인딩된 모든 use case에 적용된다. 현재 Preview와 RGBA ImageAnalysis는 같은 Camera로 바인딩된다.

로컬에 캐시된 정확한 **camera-camera2 1.3.1 sources.jar**도 확인했다.

- `Camera2CameraControlImpl`: 공통 request config에 `mZoomControl.addZoomOption(builder)` 추가.
- `AndroidRZoomImpl`: `CaptureRequest.CONTROL_ZOOM_RATIO` 설정.
- `CropRegionZoomImpl`: 대체 구현에서 `CaptureRequest.SCALER_CROP_REGION` 설정.

따라서 화면만 커지고 분석 버퍼가 원래 화각을 유지하는 UI-only zoom이 아니라 카메라 요청 기반 zoom이다. 분석 입력은 계속 기존 `ImageProxy → RGBA Bitmap → 회전 정규화 → MediaPipe + YOLO Shadow` 경로로 전달하며 별도 후처리 crop/resize를 추가하지 않았다. [setZoomRatio API](https://developer.android.com/reference/androidx/camera/core/CameraControl#setZoomRatio(float))의 비동기 실패·후속 요청 취소는 어댑터에서 격리한다.

**화각/좌표 정합성의 한계:** 공통 zoom 적용이 Preview와 ImageAnalysis의 픽셀 단위 동일 화각을 보장하지는 않는다. 기존 PreviewView `FILL_CENTER`의 표시 crop과 독립 use case의 종횡비·회전·전면 미러링이 남아 있다. 이번 작업에서 UseCaseGroup/ViewPort 또는 Overlay 좌표계는 변경하지 않았다. 실제 사람의 관절선 정합성·전신 잘림·운동 정확도는 사용자 실기기 운동 확인 항목이며 자동 테스트 통과와 구분한다.

## 분석 불변 조건

Landmark Primary, SessionExerciseLock, 운동 분류, squat/shoulder analyzer, rep count, 점수, 자세 평가, YOLO decoder/NMS/threshold/stabilizer/Shadow runtime 및 session diagnostics 저장 코드는 수정하지 않았다. 줌 모듈은 이들 도메인을 참조하지 않는다.

줌이 커져 신체가 잘리면 **같은 알고리즘이라도** 관절 인식과 결과가 달라질 수 있다. 정상 운동 검증은 전신이 보이는 배율에서 수행해야 한다. 콜백 수신은 실제 운동의 rep/score 정답 검증을 의미하지 않는다.

## 검증 기록 (2026-10-18)

| 항목 | 결과 / 증거 범위 |
| --- | --- |
| JVM | 552개 PASS (줌 policy/session 신규 20개 포함) |
| Node | 107개 PASS (기존 UI/보안 회귀 + 줌 구조 12개) |
| Python | EXP02 기존 회귀 121개 PASS, 실제 모델 Golden 재실행 아님 |
| Debug / Release / androidTest APK | assembleDebug / testDebugUnitTest / assembleRelease / assembleDebugAndroidTest PASS |
| APK 모델 배제 | debug/release 모두 기존 공개 APK 검사 PASS; MediaPipe task 유지 |
| 변경 파일 비밀값 검사 | 변경된 공개 텍스트의 secret 패턴 검사 PASS; 저장소 전체/이력 보안 감사 아님 |
| 첫 실기기 제어 확인 | 연결된 SM-S908N 후면 0.6–10.0x / 전면 1.0–8.0x, 확대·min/max clamp·reset, rear 재바인딩 PASS |
| 첫 실기기 MediaPipe | 1.3x 요청 후 pose result 콜백 수신 PASS (사람 검출/정답/overlay 시각 검증과 다름) |
| 세로/가로 실제 layout | 배율 버튼의 표시·48dp 터치 높이·접근성 설명 및 헤더/하단 정보/CTA 비중첩 PASS |
| YOLO Shadow native | 기존 private 모델로 READY 및 1.3x 요청 후 timings 포함 실제 추론 콜백 수신 PASS. 종목 정답·stable lock·운동 결과 검증과 다름 |
| 전체 기기 회귀 | SM-S908N instrumentation **3개 PASS, 0 failure, skip 없음** (잠금 해제 후 최종 실행) |
| 제스처·스크롤 | 두 손가락 scale 수신, 한 손가락 Window swipe/스크롤, 보호 버튼 탭, 보호 영역 pinch 제외, 제스처 취소 PASS. 미지원 상태는 검증용 flag/JVM으로 확인했으며 실제 고정 줌 기기 검증은 아님 |
| 전면 분석·수명주기 | 전면 1.3x 요청 후 새 MediaPipe callback 3개, rear→front→rear 기본 배율 복귀, pause 중 명령 거부, resume 이후 새 MediaPipe callback 3개, close 후 명령 거부 PASS |
| 실제 운동 / 시각적 관절 정합성 | 사용자 확인 대기 |

중간 테스트의 실패도 최종 통과와 구분한다. 자동 잠금(Dozing) 상태 실행은 최종 근거에서 제외했다. 이후 스크롤 테스트의 빈 FrameLayout은 ScrollView 측정에서 자체 콘텐츠 높이가 없었으므로 테스트 fixture에 명시적인 최소 높이를 주고 실제 scroll range를 사전 assert했다. 버튼 클릭은 UP와 같은 main-thread turn이 아니라 posted click 처리 이후에 검사한다. 한 손가락 스크롤은 Espresso Window swipe로 확인한다. 이 보완은 androidTest만 변경했고 실제 앱의 제스처·분석 코드를 통과시키기 위해 우회하거나 바꾸지 않았다.

DEBUG 전용·unexported `CameraZoomVerificationActivity`는 실제 layout을 inflate하는 instrumentation host다. 계정/DB를 읽거나 기록하지 않고 자체 카메라도 시작하지 않는다. 테스트가 명시적으로 controller를 만들고 닫는다. 테스트가 보이는 동안만 KEEP_SCREEN_ON을 사용하며 시스템 잠금 설정을 바꾸지 않는다. release manifest에는 포함되지 않는다.

테스트는 사진·프레임을 저장하거나 전송하지 않는다. private YOLO 모델이 없거나 미검증이면 그 상태를 그대로 기록하며 모델을 assets/APK에 추가하지 않는다. 프레임 콜백은 메모리 내 카운터로만 확인한다.

### 재실행

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest assembleRelease --offline --no-daemon --no-problems-report -PverificationBuildDir=dashboard-ui-verification
node --test scripts/*.test.mjs
# 설치 후, 잠금 해제·기존 카메라 권한 승인된 기기에서:
adb shell am instrument -w -r -e class com.example.arptapp.ui.camera.CameraZoomDeviceTest com.example.arptapp.test/androidx.test.runner.AndroidJUnitRunner
```

이 작업에서 확인한 debug APK: `build/dashboard-ui-verification/outputs/apk/debug/app-debug.apk`.

## 변경 파일 전체

- `app/src/main/java/com/example/arptapp/DashboardActivity.kt`
- `app/src/main/java/com/example/arptapp/ui/camera/PoseCameraController.kt`
- `app/src/main/java/com/example/arptapp/ui/camera/CameraZoomPolicy.kt`
- `app/src/main/java/com/example/arptapp/ui/camera/CameraXZoomController.kt`
- `app/src/main/java/com/example/arptapp/ui/camera/CameraZoomGestureLayout.kt`
- `app/src/main/java/com/example/arptapp/ui/dashboard/CameraZoomUi.kt`
- `app/src/main/res/layout/activity_dashboard.xml`
- `app/src/main/res/layout-land/activity_dashboard.xml`
- `app/src/main/res/values/camera_zoom.xml`
- `app/src/debug/AndroidManifest.xml`
- `app/src/debug/java/com/example/arptapp/ui/camera/CameraZoomVerificationActivity.kt`
- `app/src/test/java/com/example/arptapp/ui/camera/CameraZoomPolicyTest.kt`
- `app/src/test/java/com/example/arptapp/ui/camera/CameraZoomSessionTest.kt`
- `app/src/androidTest/java/com/example/arptapp/ui/camera/CameraZoomDeviceTest.kt`
- `scripts/camera-zoom.test.mjs`
- `docs/CAMERA_ZOOM_VALIDATION.md`
- `docs/DASHBOARD_UI_VALIDATION.md`

자동 commit/push 없음. build.gradle, 의존성, manifest 권한, 계정/원격 DB, 모델/검증 이미지, 요금 설정은 변경하지 않았다.
