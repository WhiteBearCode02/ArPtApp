# Dashboard 운동 화면 UI 정리

2026-10-18: 두 손가락 CameraX 줌과 작은 배율·초기화 버튼을 추가했다. 제스처 경계, 실제 줌 범위, 분석 영향 및 자동/실기기 검증 구분은 [CAMERA_ZOOM_VALIDATION.md](CAMERA_ZOOM_VALIDATION.md)를 참조한다.

2026-10-18 보조 설정 축소: 상단의 큰 설정 카드를 제거하고 횟수 바로 위에 작은 방향/내 기준 버튼을 배치했다. 좁은 폭에서는 공통 `CompactSetupLayout`이 세로로 접힌다. [컴팩트 설정 검증](DASHBOARD_COMPACT_CONTROLS.md)을 참조한다.

## 현재: 고정 헤더·준비/운동 상태 분리 (2026-10-16 2차 UX)

**현재 화면의 기준은 이 절입니다.** 아래 1차 구현 기록은 이전 APK/구조의 이력이며 현재 구조와 구별합니다. Compose/새 디자인 라이브러리로 이동하지 않고 기존 XML·ViewBinding·Material UI를 유지했습니다.

### 구성 요소 트리

```text
CameraZoomGestureLayout (FrameLayout 확장)
├─ 전체 화면 CameraX Preview + 관절 Overlay (기존 좌표계 유지)
└─ dashboardChrome (시스템 바/cutout safe-area padding)
   ├─ 고정 헤더: 뒤로가기 | 제목 + 작은 렌즈 상태 | 전후면 전환
   ├─ bounded NestedScrollView (헤더 아래 ~ CTA 위, 자식 clipping)
   │  ├─ 현재 인식 상태 칩
   │  ├─ 운동 중: 방향/개인 기준 읽기 전용 요약
   │  ├─ 작은 카메라 배율·초기화 버튼
   │  ├─ 빈 가변 공간 (중앙 프리뷰 확보)
   │  ├─ YOLO 진단 패널 (DEBUG, 기본 GONE, 펼친 경우 144dp 내부 스크롤)
   │  └─ 하단 정보: 준비 중 [방향 · 정면] [내 기준 기록] → 횟수 카드 + 작은 진단 버튼 → 안내 배너
   └─ 고정 운동 시작/종료 CTA (시스템 내비게이션 바 위)
```

헤더와 CTA는 스크롤의 형제이며 다른 UI가 헤더 위로 그려지지 않도록 본문 viewport를 둘 사이로 제한하고 clipping을 켰습니다. 세로는 프리뷰 중앙에 안내/메뉴를 띄우지 않으며 남는 높이를 빈 Space에 배분합니다. 가로는 고정 전체 너비 헤더 아래 오른쪽에 설정/하단 정보를 모으고 왼쪽 절반 이상의 카메라 영역을 비웠습니다. 작은 화면/확대 글꼴은 본문과 진단 내부를 스크롤하며, 실제 픽셀 렌더 검증 완료를 주장하지 않습니다.

### 상태 흐름

```text
준비 ── 내 기준 기록 선택 ──> 기준 수집 ── 기준 저장 ──> 준비
  └─ 알려진 촬영 방향 + 운동 시작 ──> 운동 중 (설정 숨김, 요약 표시, 진단 접힘)
                                       └─ 운동 종료 ──> 준비 UI 복구 + 기존 리포트
                                             ├─ 확인: 기존 결과 화면
                                             └─ 뒤로/바깥 터치: 준비 UI 유지
```

- `DashboardUiPolicy.displayState()`는 상태를 변경하지 않고 설정/요약 표시 여부만 반환합니다. 운동 중에는 횟수 위의 보조 설정 그룹 전체가 GONE이 되므로 해당 그룹과 margin이 빈 공간으로 남지 않습니다.
- 요약의 방향은 실제 cameraView입니다. 개인 기준은 운동이 아직 확정되지 않았으면 “운동 감지 후 확인”, 해당 세션 종목/방향의 실제 personalSequence가 있으면 종목명, 없으면 “내 기준 미등록”으로 표시합니다. 선택창에서 고른 종목을 실제 적용 기준처럼 꾸며 표시하지 않습니다.
- 촬영 방향 UNKNOWN·운동 중·수집 중의 진입 방어, 저장/삭제·카메라 전환 제한은 유지합니다. 분석기/카운터/세션 잠금·저장 포맷은 변경하지 않았습니다.
- 정상 리포트를 Back/바깥 터치로 닫으면 종료 버튼만 남아 재시작할 수 없던 기존 표시 결함을 보완했습니다. `restorePreparationUi()`는 버튼·카드·제목만 복구하며 리포트/세션 데이터를 리셋하거나 업로드 로직을 바꾸지 않습니다.

### 진단은 어디서 펼쳐지는가

DEBUG에서만 하단 횟수 옆 작은 **“YOLO 진단 보기”** 버튼이 보입니다. 누르면 횟수/안내 위의 `YoloDiagnosticsPanel`이 **문서 흐름 안에서** 공간을 확보합니다. 다른 요소를 덮는 Popup/모달 BottomSheet가 아닙니다. 헤더·CTA는 고정된 별도 영역이므로 진단이 그 위를 덮지 않습니다. 다시 누르면 패널을 GONE으로 돌리며 raw/stable/session/FPS/latency/thermal/model 정보는 접힌 상태에서 보이지 않습니다. 운동을 시작할 때도 자동으로 접습니다.

진단 내용이 길면 144dp 내부 스크롤을 사용합니다. 공간이 부족한 화면에서는 본문도 스크롤될 수 있지만 횟수/안내와 진단이 서로 포개지지 않습니다. Release에서는 버튼과 패널 모두 코드에서 차단합니다. `YoloDiagnosticsPanel`은 UI 갱신만 담당하며 기존 formatter·진단 로그·YOLO `NOT_VERIFIED`와 Landmark Primary는 유지합니다.

### 확장 가능한 내 기준 목록

`PersonalReferenceDialog`는 `referenceExerciseOptions`를 순회해 `item_personal_reference_option.xml` 행을 생성합니다. 현재는 스쿼트 → 숄더 프레스 순서이며 취소는 목록 아래 별도 버튼입니다. 항목마다 전체 클릭 영역/제목/보조 설명/chevron이 있습니다. UI 행을 추가 복사할 필요는 없지만, 새 운동의 실제 지원은 허용 정책·라벨·기존 수집/검증기 지원을 명시적으로 추가한 뒤 결정해야 합니다. 지원하지 않는 classifier 결과를 자동 노출하지 않습니다.

### 현재 변경 파일

- `app/src/main/java/com/example/arptapp/DashboardActivity.kt`
- `app/src/main/java/com/example/arptapp/ui/dashboard/`: `DashboardUiPolicy.kt`, `PersonalReferenceDialog.kt`, 신규 `YoloDiagnosticsPanel.kt`
- `app/src/main/res/layout/activity_dashboard.xml`, `app/src/main/res/layout-land/activity_dashboard.xml`
- `app/src/main/res/layout/dialog_personal_reference.xml`, 신규 `item_personal_reference_option.xml`, 신규 `view_yolo_diagnostics_panel.xml`
- `app/src/main/res/values/dashboard_ui.xml`, 신규 `dashboard_state.xml`
- `app/src/test/java/com/example/arptapp/ui/dashboard/DashboardUiPolicyTest.kt`
- `scripts/dashboard-ui.test.mjs`, `scripts/yolo-shadow.test.mjs` (후자는 표시 가드만 변경)
- `docs/DASHBOARD_UI_VALIDATION.md`

운동 분석·카메라/native·YOLO 추론 모듈/threshold/세션 lock/Gradle dependency에 diff가 없음을 확인했습니다. 새 외부 API·네트워크·키·사진 저장·유료 기능·원격 배포를 추가하지 않았습니다. 모델의 Git/APK 제외 및 LLM 비활성 정책을 유지합니다. 기존 자산의 재배포 권리 판단은 이번 UI 변경 범위가 아닙니다.

### 2차 UX 최종 검증 결과

- `assembleDebug testDebugUnitTest`: **BUILD SUCCESSFUL**, 최종 7분 22초(10 executed / 39 up-to-date). 처음 발생한 AAPT `Widget.AIRPTCoach.Dashboard` 부모 스타일 참조 오류는 SummaryText의 암묵적 상속을 `parent=""`로 해제해 해결했고 회귀 검사를 추가했습니다.
- JVM: **44 suites / 369 tests / failures 0 / errors 0 / skipped 0**. 기존 363개 testcase 이름 비교 누락 0개, 상태 표시 정책 테스트 6개 추가.
- Node: **76 tests / failures 0 / skipped 0**. 고정 헤더·클리핑·아래쪽 정보 순서·설정/요약 배타성·동적 목록·DEBUG 표시·스타일 부모·리포트 취소 후 준비 UI 복귀를 검사합니다. 기존 YOLO/fallback/security 경계 검사도 유지했습니다.
- 변경된 공개 텍스트 **15개** scoped 비밀정보 패턴 검사와 `git diff --check` 통과. 키 값 미출력, 전체 저장소/라이선스/Git history 재스캔 없음.
- APK 패키징 PASS: loose YOLO/미검토 weights 없음, MediaPipe task 유지. 마지막 준비 UI 복구 메서드가 실제 APK DEX에 포함된 것도 별도로 확인했습니다. 작업 중 생성된 이전 APK를 완료본으로 안내하지 않았습니다.
- 최종 APK: **`build/dashboard-ui-verification/outputs/apk/debug/app-debug.apk`**, **37,572,389 bytes**.
- APK SHA256: `A0F50135F0DCCA819607532B1D237FD4B3266539CDE4A7CF766B98A7A9561FD4`.
- 현재 ADB 조회: online/offline/unauthorized 모두 **0**, serial 미출력. 설치·스크린샷·실제 큰 글꼴/TalkBack/휴대폰 터치 E2E는 **미실행**입니다. 구조 검사와 빌드 통과를 실기기 픽셀 검증이라고 표현하지 않습니다.
- 캡처·카운팅/분석·기준 비교·대상 운동 선택·결과 이동의 핵심 6개 메서드는 HEAD와 동일합니다. 카메라/도메인 모듈·Gradle dependency diff 없음. onPoseFrame과 diagnostic renderer는 표시 대상을 새 패널로 옮기는 UI 갱신 부분만 변경했습니다.
- 기본 caption/설정은 기존 한국어를 유지하고 safe-area 처리를 유지했습니다. TensorFlow namespace/Gradle deprecation 등 기존 의존성 경고는 이번 UI 변경에서 임의 업그레이드하지 않았습니다.

실기기 수동 확인: 헤더가 스크롤/진단에 덮이지 않는지, 시작 시 설정 카드가 사라지는지, 요약이 실제 적용 기준과 맞는지, count→안내→CTA 순서와 navigation bar 여백, 진단 열림/접힘·내부 스크롤, 동적 기준 목록/취소·기존 저장/삭제, 리포트 Back 후 재시작을 확인하세요. 이번 작업에서는 자동 commit/push나 원격/요금제/모델 변경을 하지 않았습니다.

커밋 메시지 제안: `feat: 운동 화면 전후 상태 분리와 하단 진단 패널 재구성`

## 이전: 1차 UI 구현/검증 이력

기준일: 2026-10-16. 범위는 UI 배치·개인 기준 선택 방식·표시 상태입니다. 실제 운동 인식/카운팅/분석/보고 로직의 변경이나 YOLO 활성화 작업이 아닙니다.

## 화면 구성

- 세로: 뒤로가기·운동 준비·현재 렌즈·카메라 전환 → 인식 상태 → 촬영 방향/내 기준 기록 세로 메뉴 → 전신 위치/피드백 안내 → 횟수 카드 → 접힌 DEBUG 진단 → 하단 고정 운동 시작/종료.
- 가로: 전체 너비 헤더, 왼쪽 카메라/피드백 영역, 오른쪽 설정·횟수·진단 패널과 하단 고정 CTA. 왼쪽 안내와 오른쪽 콘텐츠는 독립적으로 스크롤됩니다.
- Preview/Overlay는 전체 화면을 유지합니다. UI chrome에만 시스템 바·display cutout insets를 기존 padding에 더하며 중복 적용/누적을 피합니다.
- 최소 48dp 터치 영역, 공통 row/CTA 스타일, 내용에 맞춰 늘어나는 텍스트/버튼 높이를 사용합니다. 작은 화면·큰 글꼴에서는 콘텐츠를 스크롤하며 CTA는 별도 하단 영역에 남습니다. 실제 픽셀 단위 겹침·TalkBack·제스처/3버튼 내비게이션은 실기기 확인이 필요합니다.

## 내 기준 기록

`PersonalReferenceDialog.show(context, cameraView, onSelected)`는 목록 UI만 담당합니다. 스쿼트 → 숄더 프레스 고정 순서이며 각 행 전체를 누를 수 있고, 제목·보조 설명·이동 표시를 제공합니다. 현재 촬영 방향, 기존 기록 절차/로컬 저장/영상 미저장/올바른 자세 인증 아님/길게 눌러 삭제 안내도 유지했습니다. 취소는 맨 아래 별도 버튼 한 개입니다.

선택은 기존 `beginReference()`로 연결하며, 선택창을 연 당시의 촬영 방향/계정과 현재 상태를 재확인합니다. 더블 탭·중복 dialog·종료된 Activity에서 기록 시작을 차단하고 Activity 종료 시 선택창을 닫습니다.

`DashboardUiPolicy`는 상태를 바꾸지 않는 화면 전용 정책입니다.

| 상태 | 촬영 방향/카메라 전환 | 내 기준 기록 | 운동 시작 |
|---|---|---|---|
| 준비 | 허용(렌즈 전환은 기기 지원 필요) | 선택창 열기 | 기존처럼 촬영 방향 미선택 시 안내 |
| cameraView UNKNOWN | 선택 가능 | “촬영 방향을 먼저 선택해 주세요” 후 기록 금지 | 기존 안내 후 시작 금지 |
| 운동 중 | 차단 | 차단 | 중복 시작 차단, 종료 버튼 표시 |
| 기준 기록 중 | 차단 | **기준 저장** 유지 | 차단 |

저장 성공/데이터 부족 모두 기존대로 수집 종료·frames clear·버튼 복원합니다. 길게 누르기 삭제는 현재 계정/촬영 방향의 두 운동 기준만 삭제하며 새로운 원격 저장·사진 저장은 없습니다.

## YOLO 및 기능 보존

- 진단은 횟수와 CTA 사이, DEBUG에서만 보이며 처음에는 접힙니다. 펼치기/접기는 표시만 바꾸고 기존 진단 formatter·로그·관찰 결과는 유지합니다. Release/non-debug 경로는 그룹과 상세 내용을 모두 숨깁니다.
- `captureReferenceFrame`, `onPoseFrame`, `renderYoloShadowDiagnostic`, `processLandmarks`, `calculateReferenceSimilarity`, `updateOverlay`, `updateCurrentExerciseType`, `openResultActivity` 8개 메서드를 기존 HEAD와 비교해 변경 없음을 확인했습니다.
- `domain/detection`, `domain/analyzer`, `domain/counter`, `domain/report`, `domain/session`, `ui/camera`, `app/build.gradle.kts` diff 없음. YOLO preprocess/decoder/NMS/runner/threshold/stabilizer와 session lock, MediaPipe/카메라 컨트롤러, 회차/리포트 포맷을 변경하지 않았습니다.
- 기존 Landmark Primary와 전체 YOLO `NOT_VERIFIED`/prediction 비활성 상태 유지. 이번 APK에서 YOLO가 새로 작동한다고 해석하지 마세요.

## 검증 명령과 한계

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user-home'
$env:ANDROID_USER_HOME = Join-Path $env:GRADLE_USER_HOME 'android'
.\gradlew.bat assembleDebug testDebugUnitTest --offline --no-daemon --no-problems-report --console=plain -PverificationBuildDir=dashboard-ui-verification --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8'
node --experimental-strip-types --test scripts/*.test.mjs supabase/functions/generate-workout-report/*.test.ts supabase/functions/youtube-ranking/*.test.ts
node scripts/verify-coaching-security.mjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-public-apk.ps1 -ApkPath build/dashboard-ui-verification/outputs/apk/debug/app-debug.apk
git diff --check
```

Node 신규 8개는 두 방향의 XML 구조/스타일 터치 크기·고정 CTA·목록 순서/연결·DEBUG gate를 검사합니다. JVM 신규 12개는 순수 표시 정책을 검사합니다. 이들은 Android UI를 렌더하거나 실제 기기 터치를 수행하는 instrumentation/E2E 테스트가 아닙니다. 연결된 ADB 기기는 online/offline/unauthorized 모두 0이며 설치·실행·스크린샷 검증은 수행하지 않았습니다.

### 실행 결과 (2026-10-16)

- `assembleDebug testDebugUnitTest`: **BUILD SUCCESSFUL**. 첫 전체 빌드 6분 52초, 최종 문구/횟수 표시 초기화를 포함한 재실행 8분 15초(18 executed / 31 up-to-date). 진행 출력이 있었으며 3분 무응답 중단 대상 명령은 없었습니다.
- JVM **44 suites / 363 tests / failures 0 / errors 0 / skipped 0**. 이전 351개 testcase 이름과 비교해 누락 0개, 새 UI 정책 테스트 12개 추가.
- Node **71 tests / failures 0 / skipped 0**. 이전 63개 + 새 Dashboard 구조/배선 검사 8개. 실제 원격 서비스/폰 UI E2E가 아닙니다.
- portrait/landscape, dialog, styles/drawables의 XML 파싱·Android resource linking·ViewBinding·Kotlin 컴파일 성공. 실제 픽셀 렌더 검증과 구분합니다.
- 변경된 공개 텍스트 17개 한정 secret-pattern 검사와 `git diff --check` 통과. 키 원문 미출력, 전체 저장소/라이선스/history 재스캔 없음.
- 최신 APK 패키징 검사 PASS: loose YOLO/미검토 weight 없음, `pose_landmarker_lite.task` 유지.
- APK: **`build/dashboard-ui-verification/outputs/apk/debug/app-debug.apk`**, **37,568,082 bytes**.
- APK SHA256: `92E11EDDBBB261B39392C7EECD7DBE5DBE69F593A9D0870F212D1887CD783D84`.
- 기존 TensorFlow 중복 namespace·native symbol strip·Gradle deprecation 경고는 남아 있으나 빌드 실패는 아니며 이번 UI 작업에서 dependency는 변경하지 않았습니다.

이전 `yolo-contract-verification`/`yolo-shadow-verification`/기본 `build/app` APK는 이번 명령으로 갱신되지 않습니다. 휴대폰 UI 확인에는 위 `dashboard-ui-verification` 파일을 사용하세요. 설치/실기기 확인표는 아래와 같이 미실행 상태로 남깁니다.

## 보안·비용

새 dependency/외부 API/키/유료 기능/네트워크 요청·영상 업로드를 추가하지 않았습니다. 모델 Git ignore/APK 제외와 LLM 비활성 정책을 유지합니다. 원격 DB/Edge Function/요금제 변경이나 자동 commit/push를 하지 않았습니다. UI 소스·직접 작성한 단순 vector/shape만 추가했으며 외부 이미지/템플릿을 반입하지 않았습니다. 기존 자산의 재배포 권리 미확정 상태는 그대로이며 이번 UI 작업이 공개 배포 승인은 아닙니다.

## 변경 파일

- 수정: `app/src/main/java/com/example/arptapp/DashboardActivity.kt`
- 추가: `app/src/main/java/com/example/arptapp/ui/dashboard/DashboardUiPolicy.kt`, `PersonalReferenceDialog.kt`
- 수정: `app/src/main/res/layout/activity_dashboard.xml`, `app/src/main/res/layout-land/activity_dashboard.xml`
- 추가: `app/src/main/res/layout/dialog_personal_reference.xml`
- 추가: `app/src/main/res/values/dashboard_ui.xml`, `personal_reference_ui.xml`
- 추가: `app/src/main/res/drawable/bg_dashboard_glass_panel.xml`, `bg_personal_reference_dialog.xml`, `bg_personal_reference_option.xml`, `ic_dashboard_back.xml`, `ic_dashboard_chevron_right.xml`, `ic_dashboard_expand_more.xml`
- 추가: `app/src/test/java/com/example/arptapp/ui/dashboard/DashboardUiPolicyTest.kt`, `scripts/dashboard-ui.test.mjs`
- 추가: 이 문서 `docs/DASHBOARD_UI_VALIDATION.md`

## 휴대폰 수동 확인표 (미실행)

- [ ] 세로/가로, 작은 화면, 글꼴 확대에서 안내·메뉴·횟수·진단·CTA가 겹치지 않고 스크롤 가능.
- [ ] 제스처/3버튼 내비게이션 및 cutout에서 뒤로가기/카메라 전환/시작이 시스템 바를 피함.
- [ ] 정면/측면 선택 상태가 표시되고 미선택 상태의 기준 기록/운동 시작은 차단.
- [ ] 스쿼트/숄더 프레스 선택 → 기존 각도 수집 → 기준 저장/길게 삭제 정상, 취소는 기록을 시작하지 않음.
- [ ] 운동 중 촬영 방향/기준 기록/카메라 전환 차단, 기준 기록 중 방향/시작 차단 및 저장 허용.
- [ ] DEBUG 진단 펼침/접힘 후에도 횟수·시작/종료와 겹치지 않음. 카메라/스켈레톤·리포트 기능 유지.
- [ ] TalkBack에서 뒤로가기/카메라 전환·목록 제목/보조 설명·단일 취소·진단 접힘 상태를 이해할 수 있음.

커밋 메시지 제안: `feat: 운동 대시보드 재배치와 개인 기준 목록 선택 UI 개선`
