# Dashboard 운동 화면 UI 정리

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
