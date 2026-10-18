# 운동 화면 보조 설정 축소 (2026-10-18)

## Before / After

이전에는 인식 칩 아래 큰 배경 카드 안에 두 개의 14sp 전체 너비 세로 메뉴가 있었다. 이번 변경은 그 카드를 없애고 횟수 바로 위에 같은 스타일의 `방향 · 정면` / `내 기준 기록` 보조 버튼을 둔다. 카메라, 횟수, 피드백, 종료 CTA를 변경하거나 분석 상태를 재설정하지 않는다.

```text
고정 헤더 / 인식 칩 / 기존 운동 중 읽기 전용 요약 / 기존 줌 버튼
카메라 빈 공간
YOLO 상세 패널 (기존처럼 명시적 펼침일 때만)
하단 그룹
  준비·기준 수집 중: [방향 · 정면] [내 기준 기록 또는 기존 저장 액션]
  횟수 + 기존 DEBUG 진단 버튼
  자세 피드백
고정 시작·종료 CTA
```

## UI / 접근성

| 속성 | 공통 SetupChip |
| --- | --- |
| 터치 높이 / 최소 너비 | 48dp 이상, wrap_content |
| 시각적 배경 | 세로 inset 각 8dp; 최소 높이일 때 배경 높이 약 32dp |
| padding | 좌우 10dp / 위아래 6dp |
| 글자 | 12sp, 보통 굵기, 기존 aurora_ice |
| 버튼 좌우 여백 | 각각 4dp; 같은 행의 버튼 간 총 8dp |
| 횟수와 간격 | 그룹 아래 6dp (위 4dp) |
| 큰 카드 / chevron | 제거 |

`CompactSetupLayout`은 두 화면이 공유하는 순수 View 표시 모듈이다. 자식의 실제 측정 너비와 margin을 합해 공간이 충분하면 가운데 정렬 한 줄, 부족하면 세로로 배치한다. 고정 화면 너비 breakpoint를 사용하지 않으므로 번역·큰 글꼴·가로의 좁은 오른쪽 pane에서도 동일한 규칙을 사용한다. 높이는 콘텐츠에 맞춰 늘어나며 한 줄 강제/ellipsis로 긴 텍스트를 숨기지 않는다. 기존 본문 스크롤·safe-area·헤더/CTA 경계는 유지한다.

## Behavior / 불변 조건

- 준비 중 그룹 표시, 운동 중 그룹 전체 GONE. `DashboardUiPolicy.displayState()`와 실제 Activity visibility 적용은 변경하지 않았다. GONE 그룹의 margin도 횟수 위 빈 공간으로 남지 않는다.
- 기존 읽기 전용 운동 요약은 유지한다. 이는 숨기는 두 설정 버튼과 다르다.
- 기준 수집 중 기존 `기준 저장` 액션과 긴 누르기 삭제, dialog의 스쿼트/숄더 프레스 세로 선택·취소, 촬영 방향 UNKNOWN→FRONT→SIDE 순환과 진입 방어는 그대로다.
- 방향 버튼의 **표시 문자열 리소스만** `방향 · %1$s`로 바꿨다. 개인 기준 dialog의 자세한 `촬영 방향: …` 문구는 유지한다.
- 줌 protected view 목록의 ID를 유지했다. 설정 그룹은 기존 보호 대상이며 footer 안으로 옮겨져도 pinch가 버튼을 가져가지 않는다. CameraZoomGestureLayout/CameraZoomUi 코드는 변경하지 않았다.
- CameraX, MediaPipe, YOLO Shadow/threshold/NMS/runtime, LandmarkExerciseDetector, SessionExerciseLock, rep count, score, analyzer, 개인 기준 저장/삭제 코드는 수정하지 않았다. 자동 방향 판별을 추가하지 않았다.
- 새 네트워크/API 호출, 의존성, 권한, 계정/DB 변경, 키·유료 서비스·데이터 전송 없음. 기존 모델/APK 제외 정책 유지.

## 검증

- Node: UI ancestry/순서/공통 스타일, touch target, 긴 텍스트 허용, compact wrapping, 기존 상태·dialog·zoom 보호 영역 회귀 **108개 PASS**.
- Gradle: `assembleDebug testDebugUnitTest assembleDebugAndroidTest assembleRelease --offline --no-daemon --no-problems-report -PverificationBuildDir=dashboard-ui-verification` **PASS**, JVM **552개 / failure 0**. 새 Android 측정·dialog 테스트도 컴파일 완료. Gradle 10 호환성 deprecation 경고는 기존 경고이며 이번 빌드 오류는 없다.
- 보안: 변경된 공개 텍스트 **9개**의 secret 패턴 검사 PASS, debug/release 공개 APK 모델 제외 검사 PASS. 저장소 전체·Git 이력·원격 DB의 보안 감사를 했다는 의미는 아니다.
- 기기 (2026-10-19): 연결된 SM-S908N에 debug 앱·테스트 APK를 기존 데이터 삭제 없이 업데이트하고 instrumentation **5개 PASS / failure 0 / skip 없음**을 확인했다. 이 결과는 이번 컴팩트 UI APK를 대상으로 한다.
- 새 compact instrumentation **2개 PASS**: 실제 Android View를 360×800 / 640×360 / 240×640 / 480×320dp, 글꼴 1.0 / 1.8의 제어된 환경에서 측정했다. setup/count 순서, 48dp target, 긴 문구 전체 표시, 세로 접힘, GONE 시 그룹 제거, 진단 펼침을 검사했다. 별도 dialog 테스트는 가짜 선택 callback만 사용하며 스쿼트 선택·취소 및 기존 세로 목록을 검증했다. 기준 동작/DB에 아무것도 저장하지 않았다. 이는 제어된 View 측정이지 모든 기기의 사람에 의한 시각 검수는 아니다.
- 기존 줌 instrumentation **3개 PASS**: 실제 화면의 세로/가로 전환과 배율 칩/헤더/하단 정보/CTA 비중첩, 한 손가락 Window 스크롤·버튼 탭·두 손가락 제스처, 전후면 zoom/clamp/reset·재바인딩·pause/resume/close 검증을 통과했다. 후면 0.6–10.0x / 전면 1.0–8.0x를 확인했다. 후면·전면 1.3x 요청 후 새 MediaPipe 결과 콜백, YOLO READY 및 timings를 포함한 실제 Shadow 추론 콜백을 확인했다. 실제 운동의 종목/횟수/점수 정답과 관절 overlay의 사람 시각 검증은 별도다.

재검증은 새 debug APK와 androidTest APK를 설치하고 `com.example.arptapp.ui.dashboard.DashboardCompactControlsDeviceTest` 및 `com.example.arptapp.ui.camera.CameraZoomDeviceTest`를 실행한다. 실제 운동의 카운트/자세 정답은 이 자동 측정 테스트와 별도로 확인한다. **새 APK는 현재 연결한 휴대폰에 설치 완료했다.** 테스트 중 사진·프레임 저장, 외부 전송, 계정/기록 DB 변경은 하지 않았다. 시스템 자동 잠금 설정도 바꾸지 않았다.

## 변경 파일 전체

- `app/src/main/res/layout/activity_dashboard.xml`
- `app/src/main/res/layout-land/activity_dashboard.xml`
- `app/src/main/res/values/dashboard_ui.xml`
- `app/src/main/java/com/example/arptapp/ui/dashboard/CompactSetupLayout.kt`
- `app/src/main/java/com/example/arptapp/DashboardActivity.kt` (방향 표시 문자열 두 곳만)
- `app/src/androidTest/java/com/example/arptapp/ui/dashboard/DashboardCompactControlsDeviceTest.kt`
- `scripts/dashboard-ui.test.mjs`
- `docs/DASHBOARD_UI_VALIDATION.md`
- `docs/DASHBOARD_COMPACT_CONTROLS.md`

자동 commit/push 없음. 검증 APK: `build/dashboard-ui-verification/outputs/apk/debug/app-debug.apk`.
