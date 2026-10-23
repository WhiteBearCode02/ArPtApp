# YOLO Shadow 진단 읽기 (2026-10-23)

## 이 섹션은 무엇인가요?

YOLO는 **운동 종류를 옆에서 시험 인식하는 보조 AI**다. 현재 앱의 운동 종류 확정·횟수·자세·점수는 기존 관절 분석이 담당한다. YOLO 수치는 운동 성적표가 아니라 개발/검증용 비교 정보다. `Shadow`는 이처럼 실제 판정에 개입하지 않고 관찰한다는 뜻이다.

## 화면 구성

운동 중 진단 패널과 저장된 상세 리포트에 동일한 읽기 원칙을 적용한다.

1. 쉬운 한글 요약: 모델 상태, 관절 분석/YOLO/기록 운동을 구분한다.
2. 인식 결과: 한 화면의 판단과 여러 화면을 모은 판단을 분리한다.
3. 처리 현황: 화면 장수·속도·시간·발열을 각각 설명한다.
4. **각 항목은 무슨 뜻인가요?**: 공통 용어 설명을 눌러 펼친다. 기본은 접힘.
5. **개발자용 기술 정보 보기**: 기존 코드명·원본 수치·SHA 등은 별도 접기 안에 유지한다. 기본은 접힘.

기존 DEBUG 전용·기본 접힘 정책, 카메라 패널의 144dp 내부 스크롤 및 헤더/횟수/CTA 경계는 유지한다. 설명은 12~14sp, 행간과 제목 구분을 적용한다. 버튼은 48dp 이상 터치 영역, 줄바꿈과 접근성 펼침 상태를 제공한다. 영문 원본 진단 TextView를 접근성에서 완전히 제외하던 설정도 제거했다.

## 꼭 구분해야 할 용어

| 표시 | 의미 | 뜻하지 않는 것 |
| --- | --- | --- |
| 이번 화면 (raw) | 한 화면에서 YOLO가 고른 운동 | 최종 세션 운동 |
| 연속 화면의 판단 (stable) | 여러 관측을 모은 결과 | 실제 정답 또는 사용자 신원 |
| 관절 기반 감지 (fallback) | 기존 관절 좌표 기반 운동 감지 | YOLO 실패 때만 동작하는 새 기능 |
| 기록 중인 운동 (session) | 기존 로직이 확정한 기록 운동 | raw 결과를 강제로 따라가는 값 |
| 모델 확신도 | 모델의 판단 강도 | 정답률·자세 점수 |
| 처리 화면 수 | AI가 처리한 화면 장수 | 운동 횟수 |
| YOLO 처리 속도 | 완료 장수 / 활성 세션 시간 | 카메라 FPS·관절 분석 FPS·정확도 |
| 처리 시간 | 화면 준비 + 추론 + 결과 정리 시간 | 운동 시간; 1,000ms = 1초 |
| 판정 일치 비율 | YOLO와 앱이 확정한 운동이 같은 비율 | 실제 정답을 사용한 accuracy |
| 기기 발열 | Android의 기기 성능 제한 단계 | 섭씨 온도·체온 |

발열 단계 이름은 [Android PowerManager 공식 정의](https://developer.android.com/reference/android/os/PowerManager.html#THERMAL_STATUS_NONE)에 대응한다. 단계 0은 열 제한 없음, 1~6은 제한/심각도가 증가하는 상태다. 미지원·미측정은 단계 0으로 간주하지 않는다. 기존 측정 주기나 기기 제어는 바꾸지 않았다.

## 오해 방지

- 검출 표본이 없으면 **미측정**이다. 0%로 대체하지 않는다.
- 판단 보류(UNKNOWN)는 오답이 아니다. 일치 비율에서 보류를 제외한 분모와 보류 비율의 분모를 명시한다.
- 모델 준비 완료와 실제 분석 완료는 다르다. native 추론 시간이 없는 상태는 첫 결과 대기다.
- 검증되지 않았거나 실행 허용이 꺼진 관측에서 선택값이 전달되더라도 쉬운 요약에 활성 예측으로 표시하지 않는다.
- 저장된 기록은 종료 시 모델 상태와 누적 완료 장수를 별도로 보여준다. 종료 때 오류가 났다고 이전 완료 표본을 지우지 않는다.
- 여러 검출이 있으면 YOLO와 관절 분석이 같은 사람을 가리킨다고 단정하지 않는다.
- 모델 계약 검증은 실행 규칙의 동등성 검증이다. 모델 정확도·배포 권리 보증이 아니다.

## 유지보수 / 보안 / 비용

- `YoloDiagnosticGuide`: 공통 상태·운동명·확신도·발열의 순수 표시 함수.
- `YoloLiveDiagnosticsPresentation`: 일시적 scalar 관측 → 읽기 쉬운 화면 데이터. 닫힌 패널은 관측만 캐시하며 보이는 요약은 최대 초당 2회 갱신한다. 매번 View를 생성하지 않는다.
- `YoloDiagnosticsHelp`: 두 화면이 재사용하는 로컬 도움말 접기. 계정/환경 설정으로 저장하지 않는다.
- 리포트 `groups()`는 한글 요약, `technicalGroups()`는 기존 손실 없는 개발자 필드다. DB JSON·Room schema·codec는 변경하지 않았다.
- `DetectorComparisonFormatter`와 Logcat 구조는 그대로다. 기술 진단 자동화 호환성을 유지한다.
- CameraX/MediaPipe/YOLO runtime·threshold/NMS/stabilizer/모델/세션 잠금/횟수/점수·저장·업로드 로직은 변경하지 않았다.
- 새 SDK, 권한, API 호출, 외부 서버, 원격 배포, 결제/요금 설정 변경 없음. 사진·모델·검증 자료를 Git/APK에 새로 넣지 않는다.

## 검증 상태

- Node: 기존 회귀 + 진단 도움말 구조 **114개 PASS**.
- JVM: **564개 PASS** (신규 12개 포함). 상태 번역, 누락/비정상 수치, 확신도·정답률 구분, 비활성/첫 결과 대기, raw/stable/session 분리, 비교 분모, 원본 불변을 검증했다. 빌드 중 소스 수정 시점이 섞인 첫 실행의 실패 2개는 현재 소스 전체 재실행에서 해소됐다.
- Android: 연결된 SM-S908N에 새 debug APK를 기존 데이터 삭제 없이 설치했다. 새 도움말/원본 접기·재바인딩 **2개**, 기존 compact 배치/개인 기준 dialog 회귀 **2개**, 총 **4개 PASS / skip 0**. 메모리의 가짜 관측만 사용했고 카메라·계정·DB·운동 기록을 읽거나 저장하지 않았다. 실제 카메라 추론·운동 정답 검증을 이번 UI 테스트로 주장하지 않는다.
- Gradle: `assembleDebug testDebugUnitTest assembleDebugAndroidTest assembleRelease --offline --no-daemon --no-problems-report --max-workers=2 -PverificationBuildDir=dashboard-ui-verification` **PASS**. 기존 Gradle 10 호환성 경고 외 빌드 오류 없음. 도움말의 `%`는 치환 인자가 아니므로 `formatted="false"`를 명시했다.
- 보안: 변경된 공개 텍스트 **21개 secret 패턴 검사 PASS**, debug/release 모두 기존 공개 APK 모델 제외 검사 **PASS**. MediaPipe task는 유지하고 미확인 YOLO weights는 APK에 넣지 않았다. 전체 저장소 이력·원격 서비스의 포괄 보안 감사를 수행했다는 뜻은 아니다.
- 과거 실제 모델 Golden 결과를 이번 UI의 정답/시각 검증으로 재사용하지 않는다.

## 변경 파일

- `app/src/main/java/com/example/arptapp/DashboardActivity.kt`
- `app/src/main/java/com/example/arptapp/ui/dashboard/YoloDiagnosticsPanel.kt`
- `app/src/main/java/com/example/arptapp/presentation/diagnostics/YoloDiagnosticGuide.kt`
- `app/src/main/java/com/example/arptapp/presentation/diagnostics/YoloLiveDiagnosticsPresentation.kt`
- `app/src/main/java/com/example/arptapp/presentation/diagnostics/YoloDiagnosticsHelp.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/YoloSessionDiagnosticsPresentation.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/YoloSessionDiagnosticsRenderer.kt`
- `app/src/main/res/layout/view_yolo_diagnostics_panel.xml`
- `app/src/main/res/layout/view_yolo_diagnostics_help.xml`
- `app/src/main/res/layout/activity_report.xml`
- `app/src/main/res/values/yolo_diagnostics_help.xml`
- `app/src/main/res/values/dashboard_state.xml`
- `app/src/main/res/values/yolo_report.xml`
- `app/src/test/java/com/example/arptapp/presentation/diagnostics/YoloDiagnosticsGuideTest.kt`
- `app/src/test/java/com/example/arptapp/presentation/report/YoloSessionDiagnosticsPresentationTest.kt`
- `app/src/androidTest/java/com/example/arptapp/ui/dashboard/YoloDiagnosticsHelpDeviceTest.kt`
- `app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/YoloRealtimeShadowDeviceTest.kt` (기존 원본 필드 검증이 `technicalGroups()`를 사용하도록 연결만 변경)
- `scripts/yolo-diagnostics-help.test.mjs`
- `scripts/report-ui.test.mjs`
- `scripts/yolo-session-wiring.test.mjs`
- `docs/YOLO_DIAGNOSTICS_UX.md`

APK: `build/dashboard-ui-verification/outputs/apk/debug/app-debug.apk`. 자동 commit/push 없음.
