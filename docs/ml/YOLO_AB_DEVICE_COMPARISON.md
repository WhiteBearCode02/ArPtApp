# EXP02 / EXP03 · 동일 휴대폰 DEBUG A/B 비교

## 범위와 빌드 구조

기존 `debug` / `release` / `com.example.arptapp`는 유지합니다. flavor로 모든 release variant를
증식시키지 않고, debug에서 `initWith`한 두 build type만 추가했습니다.
이 방식은 [Android 공식 build type 구성](https://developer.android.com/build/build-variants)을 따릅니다.

| 앱 | Build type | 실제 applicationId | modelId | 전처리 |
|---|---|---|---|---|
| AIRPTCoach V1 - EXP02 | exp02Debug | com.example.arptapp.exp02 | yolo26n_exp02_fp32 | 기존 Exp02BitmapPreprocessor → Exp02RgbPreprocessor |
| AIRPTCoach V2 - EXP03 | exp03Debug | com.example.arptapp.exp03 | yolo26n_exp03_final_fp32 | YoloBitmapPreprocessor → YoloOpenCvLinearRgbPreprocessor |
| 기존 AIRPTCoach | debug / release | com.example.arptapp | 기존 EXP03 유지 | 기존 EXP03 유지 |

`YOLO_MODEL_VARIANT` / `YOLO_AB_COMPARISON`은 BuildConfig 상수입니다. `YoloBuildSelection`이
debug 여부·package·모델 조합을 검증합니다. Intent/preference/remote flag/file discovery로 바꾸지 못합니다.
`YoloBuildProfile` 하나가 factory runtime/contract/preprocessor, collector, live label,
수동 validation의 identity를 공급합니다. canonical `YoloModelPolicy`를 EXP02로 바꾸지 않았습니다.
따라서 V1에서 기록과 진단만 EXP03으로 잘못 표기되는 경로도 막습니다.

두 앱 모두 Primary=LANDMARK / YOLO=Shadow입니다. exercise lock, rep, score, calories,
posture feedback, coaching, routing, MediaPipe, DB schema는 바꾸지 않았습니다.
raw .10 / stable .60 / NMS IoU .70 / maxDet300 / interval250ms /
window5·agreement4·evidence age2000ms / TF Lite2.14.0 / Interpreter threads2는 공통입니다.
모델별 이미 검증된 전처리 차이는 의도적으로 유지합니다. 따라서 **모델+검증된 배포 경로 비교**이지
전처리까지 완전히 같은 모델 weight만의 대조 실험이라고 주장하지 않습니다.

## 모델 파일과 private sandbox

| 모델 | bytes | SHA256 |
|---|---:|---|
| EXP02 | 9,894,030 | 464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749 |
| EXP03 | 9,894,032 | 3d2b6bc537d8aef5a19b671226d73c98b49411574a193be72e225086202d1167 |

모델 원본은 기존 gitignored 로컬 경로 그대로입니다. 모든 APK에서 loose tflite/pt/pth/onnx를
계속 제외합니다. `pose_landmarker_lite.task`와 source BSD notice는 유지합니다.
각 앱의 `noBackupFilesDir/yolo/<exact filename>`으로만 넣으며 서로의 sandbox나 원본 앱의
모델/계정/DB를 복사하지 않습니다. shared storage/root/network download는 사용하지 않습니다.

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
# 먼저 authorized device가 정확히 한 대인지 확인합니다. 모델 검증만 하면 ADB를 호출하지 않습니다.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provision-yolo-ab-model.ps1 -ModelVariant EXP02 -ValidateLocalOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provision-yolo-ab-model.ps1 -ModelVariant EXP03 -ValidateLocalOnly
# WhatIf는 read-only preflight까지만, 실제 호출은 선택한 비교 앱만 종료·주입합니다.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provision-yolo-ab-model.ps1 -ModelVariant EXP02 -AdbPath $adb -WhatIf
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provision-yolo-ab-model.ps1 -ModelVariant EXP02 -AdbPath $adb
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provision-yolo-ab-model.ps1 -ModelVariant EXP03 -AdbPath $adb
```

원래 `provision-exp03-model.ps1`의 기본 호출은 계속 원본 앱 EXP03에만 적용됩니다.
추가 매개변수는 closed enum과 `ComparisonApp`뿐이며 임의 package 입력은 없습니다.
EXP02는 ComparisonApp 없이 새 공통 경로로 원본 앱에 주입할 수 없습니다.
기존 raw transport/helper를 재사용해 filename/size/hash, symlink, exact run-as root, staging hash,
atomic move, final device hash를 검사합니다. 기존 모델 삭제·앱 데이터 초기화는 없습니다.

## 로그인·동기화·비용

각 앱에 **사용자가 기존 이메일·비밀번호로 직접 로그인**합니다. 계정/DB 복제나 인증 우회는 없습니다.
토큰·비밀번호를 도구에 제공하지 마세요. 앱 간 로그인 상태/기록/개인설정은 별개입니다.

기존 `arptapp://auth/...`를 세 앱이 동시에 받으면 잘못된 앱으로 OAuth/recovery가 전달될 수 있습니다.
비교 manifest는 두 Auth intent-filter를 제거하고 해당 activities를 non-exported로 제한합니다.
비교 앱의 Google 로그인/가입/비밀번호 복구 버튼은 비활성화하고 이메일 로그인 안내를 표시합니다.
원본 앱의 deep link/복구 strict validation 및 서버 redirect 설정은 변경하지 않았습니다.
[공식 deep-link 설명](https://supabase.com/docs/guides/auth/native-mobile-deep-linking).

비교 앱은 local-only 체크를 고정하고 workout sync의 기존 `enabled` port를 false로 공급합니다.
Room 저장은 계속되고 운동 업로드/다운로드만 하지 않습니다. canonical 앱의 port는 계속 true입니다.
로그인 등 기존 Auth 요청은 서버를 사용합니다. 로그인 후 네트워크를 꺼서 카메라를 검증하세요.
새 유료 API/SDK/provider/billing 변경/원격 SQL/Edge 배포는 없습니다. 기존 Supabase 요금제 전체의
무과금 보증이나 모델 재배포 권리 승인을 의미하지 않습니다. model/private evidence는 Git/APK 제외입니다.

## 재현 빌드 및 APK 확인

```powershell
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
$env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE '.android'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleExp02Debug :app:assembleExp03Debug :app:assembleExp02DebugAndroidTest --offline --no-daemon --no-problems-report --no-build-cache --max-workers=2 '-PverificationBuildDir=yolo-ab-verification' '-PabTestBuildType=exp02Debug'
.\gradlew.bat :app:assembleExp03DebugAndroidTest --offline --no-daemon --no-problems-report --no-build-cache --max-workers=2 '-PverificationBuildDir=yolo-ab-verification' '-PabTestBuildType=exp03Debug'
# 일반 assembleDebugAndroidTest는 abTestBuildType을 생략하면 기존 debug를 대상으로 합니다.
```

`abTestBuildType`은 AGP의 instrumentation 대상 선택이며 앱의 모델을 바꾸는 옵션이 아닙니다.
기본 debug는 계속 EXP03입니다. A/B 앱은 각각의 task로만 만듭니다.
실제 APK badging의 applicationId·launcher label·debuggable과 binary manifest의 deep-link 부재는
`scripts/verify-yolo-ab-apks.ps1`로 확인합니다. 모델 제외와 source notice 검사는 기존 APK 검사기를 재사용합니다.

## 런타임 확인

`YoloAbRuntimeDeviceTest`는 expectedVariant를 명시해 실제 target package·launcher label·contract·
preprocessor class·factory READY·prediction 허용을 검사합니다. 동일 factory에 합성 단색 한 장을
제출해 실제 native 완료와 collector identity도 확인합니다. 사진/카메라/계정/Room은 사용하지 않습니다.
private `no_backup/yolo/ab-runtime/<UUID>/native-profile.json`에 메타데이터만 배타적으로 생성합니다.
**이는 실제 운동 정확도·Camera FPS·Golden 재통과 증거가 아닙니다.**

## 사용자가 수행할 4세션

각 앱에서 로그인 후 네트워크를 끄고 `운동 준비 → YOLO 진단 보기`에서 모델 버전을 확인합니다.
`READY / VERIFIED / prediction 활성 / Primary LANDMARK`여야 합니다.

1. **V1 EXP02**: 촬영 방향 정면 → 스쿼트 약 10회 → 종료 → 결과 저장.
2. **V2 EXP03**: 동일한 정면 조건 → 스쿼트 약 10회 → 종료 → 결과 저장.
3. **V1 EXP02**: 촬영 방향 측면 → 스쿼트 약 10회 → 종료 → 결과 저장.
4. **V2 EXP03**: 동일한 측면 조건 → 스쿼트 약 10회 → 종료 → 결과 저장.

같은 렌즈(전/후면), 카메라 위치/거리/높이, zoom, 장소/조명, 전신 구도, 가능한 한 비슷한 속도를 유지합니다.
다른 앱은 운동을 종료한 상태로 두고 두 카메라를 동시에 사용하지 마세요.
앞선 테스트로 뜨거워졌으면 온도가 안정될 때까지 쉬어 동일 조건에 가깝게 합니다.
`촬영 방향 FRONT/SIDE`는 몸의 방향이지 렌즈 방향이 아닙니다. 프레임 저장 버튼을 누를 필요는 없습니다.
이번 목적에는 동영상/사진 수집이 필요하지 않습니다.

모두 저장한 후 `V1 정면/측면, V2 정면/측면 저장 완료`라고 알려주세요.
그때 각 sandbox에서 정확한 두 기록을 읽어 아래 집계만 비교합니다. 앱 삭제·모델 재주입은 필요 없습니다.

## 비교표와 분모

| Model | Exercise | View | Raw SQUAT/PRESS/UNKNOWN | Stable SQUAT/PRESS/UNKNOWN | Avg confidence | YOLO FPS | Inference ms | Total ms |
|---|---|---|---|---|---|---|---|---|
| EXP02 | SQUAT | FRONT | NOT_MEASURED | NOT_MEASURED | — | — | — | — |
| EXP03 | SQUAT | FRONT | NOT_MEASURED | NOT_MEASURED | — | — | — | — |
| EXP02 | SQUAT | SIDE | NOT_MEASURED | NOT_MEASURED | — | — | — | — |
| EXP03 | SQUAT | SIDE | NOT_MEASURED | NOT_MEASURED | — | — | — | — |

위 표는 비교 앱에서 사용자가 운동하기 전까지 채우지 않습니다. 이전 원본 앱 세션을 새 비교 앱 세션으로 옮기지 않습니다.
preprocess/postprocess 평균과 thermal도 함께 기록합니다. confidence는 detection이 선택된 표본만의 값이며 정확도가 아닙니다.
FPS는 YOLO 완료 프레임/active duration으로 CameraX/MediaPipe FPS가 아닙니다.

Primary lock 후 비교 표본 분모 `D = matchingStableFrames + mismatchingStableFrames + unknownStableFrames`로:

- Stable correct % = matchingStableFrames / D × 100.
- Stable mismatch % = mismatchingStableFrames / D × 100.
- Stable unknown % = unknownStableFrames / D × 100.
- D=0이면 세 비율 모두 N/A. lock 전 unknownSessionFrames는 따로 표시합니다.
- 기존 knownMatchRate = match/(match+mismatch)는 UNKNOWN 제외 분모이므로 별도 항목으로 유지합니다.

이것은 사용자가 확인한 운동과 Primary lock에 대한 **세션 수준 일치 지표**입니다.
프레임별 사람이 직접 붙인 정답이 아니므로 모델 전체 accuracy/precision/recall이라고 부르지 않습니다.

## 실제 검증 기록

2026-11-06 · 실제 A/B 운동 성능은 **사용자 실행 대기 / NOT_MEASURED**입니다.

| 검증 | 직접 확인한 결과 |
|---|---|
| 원본 debug / release + 비교 앱2 + EXP02 Android test APK 빌드 | BUILD SUCCESSFUL, fresh output 22분 46초, 221 tasks |
| EXP03 Android test APK 빌드 | BUILD SUCCESSFUL, 2분 19초, 58 tasks |
| JVM | 65 suites / 667 tests / failures·errors·skipped 0. 기존660 + selection7 |
| Node | 143/143 PASS. 기존137 + A/B guard6 |
| 실제 APK package/label/DEBUG | V1/V2 모두 exact PASS, APK 파일명만 바꾼 것이 아님 |
| 비교 앱 binary manifest | production Auth BROWSABLE filters 없음 PASS |
| 원본 debug/release + A/B 앱2 | loose model/private fixture 제외, MediaPipe task/exact source BSD notice 유지 PASS |
| A/B test APK2 | private model/RGB/tensor/reference/result 제외 PASS |
| 설치 | authorized USB1대, owner user0에 두 앱 및 각 test APK install -r 성공 |
| private model provisioning | 각각 exact filename/bytes/SHA, staged/final 검증 PASS |
| V1 runtime | 실제 EXP02 factory READY/VERIFIED/prediction=true, Exp02BitmapPreprocessor, native1회·collector identity PASS |
| V2 runtime | 실제 EXP03 factory READY/VERIFIED/prediction=true, YoloBitmapPreprocessor, native1회·collector identity PASS |
| Android instrumentation | V1 1/1 PASS, V2 1/1 PASS. 전부 합성 단색, 카메라 사용 없음 |
| 기존 AIRPTCoach | 설치 유지. 원본 sandbox EXP02/EXP03 모델 SHA 전후 동일. 원본 앱 업데이트/종료/DB 조작 없음 |
| Git safety | 모델2·local_validation·generated APK 모두 ignored/untracked. 변경24개 public text secret-pattern PASS |
| `git diff --check` | PASS. 일부 기존 파일 CRLF→LF 안내만 있음 |

기존 TF Lite namespace 중복·native strip 불가·Gradle10 deprecation·test Json 생성 경고는
비차단 경고로 남았습니다. SDK/Gradle/Interpreter 버전을 올리거나 경고를 숨기지 않았습니다.
사용자 로그인·실제 Camera·운동 저장/History·4세션 성능은 새 비교 앱에서 아직 검증하지 않았습니다.
기존 원본 앱의 과거 운동 결과를 새 A/B 성능으로 옮겨 적지 않았습니다.

### 전달 APK

동일한 실제 빌드 산출물의 byte-identical 복사본이며 두 파일의 applicationId는 서로 다릅니다.

- `build/yolo-ab-verification/AB_APKs/AIRPTCoach-V1-EXP02-debug.apk` — 36,701,958 bytes.
- `build/yolo-ab-verification/AB_APKs/AIRPTCoach-V2-EXP03-debug.apk` — 36,701,978 bytes.

원본 산출물은 `outputs/apk/exp02Debug/app-exp02Debug.apk`,
`outputs/apk/exp03Debug/app-exp03Debug.apk`입니다. APK에 모델은 없으며 현재 연결했던 휴대폰의
각 private sandbox에 별도 provisioning을 완료한 상태입니다. 다른 휴대폰에는 APK 설치만으로 모델이 생기지 않습니다.

### 변경 파일 (24개)

- `app/build.gradle.kts`: DEBUG build types/compile-time constants/공유 debug source/test target.
- `app/src/abComparison/AndroidManifest.xml`: 비교 앱 Auth link 격리와 non-exported 진입점.
- `app/src/main/java/com/example/arptapp/domain/detection/YoloBuildSelection.kt`: 순수 model/package 조합 검증 API.
- `app/src/main/java/com/example/arptapp/ui/camera/yolo/YoloBuildProfile.kt`: identity/contract/preprocessor 조립 API.
- `app/src/main/java/com/example/arptapp/ui/camera/yolo/TfliteYoloRuntime.kt`: factory에 명시적인 모델 identity 주입.
- `app/src/main/java/com/example/arptapp/DashboardActivity.kt`: collector/live label에 같은 build identity 주입.
- `app/src/main/java/com/example/arptapp/domain/detection/DetectorComparison.kt`: 명시적 live model label.
- `app/src/main/java/com/example/arptapp/presentation/diagnostics/YoloLiveDiagnosticsPresentation.kt`: 사람이 읽는 모델 표시.
- `app/src/main/java/com/example/arptapp/MainActivity.kt`: A/B 이메일 로그인 안내, redirect 기반 UI 비활성화.
- `app/src/main/java/com/example/arptapp/data/history/WorkoutHistoryProvider.kt`: 기존 enabled port로 비교 앱 sync만 비활성화.
- `app/src/debug/java/com/example/arptapp/ui/dashboard/DebugPositiveValidationControls.kt`: A/B local-only 고정·실제 모델명 안내.
- `app/src/debug/java/com/example/arptapp/ui/camera/yolo/validation/DebugPositiveNativeInference.kt`: 수동 도구도 build-specific 모델/전처리 사용.
- `app/src/debug/java/com/example/arptapp/ui/camera/yolo/validation/DebugPositiveValidationFactory.kt`: 실패 메시지 identity/계약 표시.
- `app/src/test/java/com/example/arptapp/domain/detection/YoloBuildSelectionTest.kt`: 순수 선택/실패/공통 정책/collector/label7개.
- `app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/YoloAbRuntimeDeviceTest.kt`: 실제 native factory의 opt-in 비교 앱 검증.
- `scripts/provision-exp03-model.ps1`: 기존 default 보존, exact comparison package mapping 지원.
- `scripts/provision-yolo-ab-model.ps1`: 닫힌 EXP02/EXP03 wrapper, 같은 안전한 transport 재사용.
- `scripts/verify-yolo-ab-apks.ps1`: 실제 binary APK identity/label/debug/link 및 기존 packaging guard.
- `scripts/yolo-ab-builds.test.mjs`: 새 A/B 구조/격리6개.
- `scripts/yolo-shadow.test.mjs`: factory 조립 위치가 바뀐 guard 갱신, EXP03 기본값 검증 보존.
- `scripts/exp03-shadow.test.mjs`: model-specific 조립과 실제 수동 도구 provenance guard.
- `scripts/exp02-positive.test.mjs`: 수동 도구의 명시적 profile/native identity 연결 검증.
- `docs/ml/YOLO_AB_DEVICE_COMPARISON.md`: 구조/검증/사용 절차/분모/관측 한계.
- `README.md`: 새 절차 문서 연결.

원본 모델·Golden PASS/FAIL 증거·기존 권리 기록은 변경하지 않았습니다.
재학습/threshold/NMS/stabilization/MediaPipe/DB schema/서버 설정/billing/배포 변경은 없습니다.
자동 commit/push/PR/merge는 모두 수행하지 않았고 브랜치는 `ai-v2`를 유지했습니다.
