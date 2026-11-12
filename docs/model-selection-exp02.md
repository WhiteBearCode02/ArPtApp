# 최종 앱 모델 선택: EXP02

결정일: 2026-11-09 · 기본 앱 `com.example.arptapp` · 모델 내장/공개 배포 승인이 아닌 **기본 모델 선택 결정**입니다.

## Production Model / Experimental Model / 현재 결정

- **Production Model: EXP02** — 기본 debug/release 설정의 선택 모델. `YOLO_MODEL_VARIANT=EXP02`.
- **Experimental Model: EXP03 계열** — 원본 모델·소스·Golden PASS/FAIL·카메라 관측·비교 APK 보존.
- **현재 결정: EXP02를 최종 앱 모델로 유지**합니다. 기본 앱은 EXP03 또는 다른 파일로 자동 fallback하지 않습니다.
- Primary=LANDMARK / YOLO=Shadow 정책은 유지합니다. debug에서만 Shadow가 활성화되며 release gate는 OFF입니다.
  따라서 여기서 Production은 모델 선택을 뜻하며 YOLO Primary 전환, 서명 release 배포 또는 법적 승인 선언이 아닙니다.

## 선택 이유와 증거 수준

사용자는 실제 Android 스마트폰 비교에서 EXP02가 스쿼트와 숄더프레스를 더 안정적으로 구별했고,
스쿼트 수행 시 EXP03 계열이 SHOULDER_PRESS를 출력하거나 판단 보류가 늘었다고 보고했습니다.
따라서 오프라인 Test metric만으로 새 모델을 채택하지 않고 실제 앱 환경의 안정성을 우선해 EXP02로 복귀합니다.

**이는 사용자 실기기 관찰 보고이며, 이번 작업에서 네 A/B 세션의 원본 집계를 새로 읽거나 정식 정확도를 측정한 결과는 아닙니다.**
기존에 직접 읽은 [EXP03 2세션 baseline](ml/EXP03_CAMERA_SHADOW_BASELINE.md)은 별도로 보존합니다.
모든 실제 운동을 YOLO가 정확히 인식한다는 보증은 없으며, EXP03의 [actual Golden PASS](ml/EXP03_PREPROCESSING_PARITY.md)는
Python/Android contract 동등성 증거입니다. 그 PASS를 실패로 바꾸거나 실제 운동 정확도 PASS로 확대하지 않습니다.

## 실제 모델 파일과 계약

| 항목 | 확인 내용 |
|---|---|
| 로컬 원본 | `app/src/main/assets/airptcoach_yolo26n_exp02_fp32.tflite` (Git ignored, APK asset 아님) |
| modelId | `yolo26n_exp02_fp32` |
| 크기 | 9,894,030 bytes |
| 재계산 SHA256 | `464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749` — 기준값 일치 |
| 실제 input | FLOAT32 `[1,3,640,640]`, tensor index0 |
| 실제 output | FLOAT32 `[1,6,8400]`, tensor index658 |
| class | `0=SQUAT`, `1=SHOULDER_PRESS` |
| Android 계약 | 기존 `Exp02YoloContract.VERIFIED`; 신규 정확도 승격/검증을 뜻하지 않음 |
| native runtime | 기존 TensorFlow Lite2.14.0, threads2 유지 |

실제 파일의 bounded FlatBuffer metadata를 다시 읽었습니다. JSON evidence는 Git ignored
`build/production-exp02-verification/exp02-metadata-20261109.json`에만 생성했습니다.
그 도구가 표시하는 NOT_VERIFIED/native pending은 **해당 static 검사 자체의 증거 범위**이며,
기존 코드의 검토된 EXP02 contract를 강등하거나 Android 추론 성공을 새로 주장하지 않습니다.
모델 다운로드·재학습·재-export·파일 이동·원본 수정은 하지 않았습니다.

## 저장소 조사와 실제 실행 경로

`app/src`의 모델 파일/Interpreter/하드코딩/전처리/decoder/NMS/UNKNOWN/안정화/카메라·세션 연결과
Gradle·테스트·검증 스크립트의 모델 참조를 조사했습니다. 전체 라이선스 감사 또는 모든 파일의 의미 검증을 뜻하지 않습니다.

```text
defaultConfig: YOLO_MODEL_VARIANT=EXP02
  → YoloBuildSelection (정확한 모델/version/applicationId 조합 확인)
  → YoloBuildProfile.identity = KnownYoloModels.EXP02
  → Exp02YoloContract.components(Exp02BitmapPreprocessor())
  → DebugYoloModelSource: noBackupFilesDir/yolo/<EXP02 exact filename>
  → YoloModelIdentity: exact bytes + full SHA256
  → TfliteYoloRuntime: Interpreter 생성 + 실제 tensor 계약 검사
  → Exp02RgbPreprocessor → decoder → class-aware NMS → temporal stabilizer
  → DEBUG 진단/세션 집계만 (운동 종목 확정/점수 경로와 격리)
```

- `YoloModelPolicy`의 기본 descriptor·파일명·hash·bytes를 EXP02로 맞췄습니다.
  `YoloBuildSelection`은 기본 앱에서 EXP03·알 수 없는 variant·잘못된 package 조합을 거부합니다.
- debug/release 기본 모델은 EXP02이며, 별도 `exp03Debug` 비교 앱만 검토된 EXP03 profile을 명시적으로 사용합니다.
- EXP02 전처리는 기존 `Exp02RgbPreprocessor`입니다. EXP03 OpenCV-compatible 전처리를 EXP02에 적용하지 않았습니다.
- 카메라는 기존 rotated, non-mirrored bitmap을 MediaPipe와 Shadow에 전달합니다. 이 처리와 골격 overlay는 바꾸지 않았습니다.
- 세션 선택은 계속 landmark classifier → SessionExerciseLock → 운동별 분석입니다. YOLO 결과를 counter/score/coaching에 연결하지 않습니다.
- collector/live label/수동 validation은 기존 `YoloBuildProfile`을 공유해 모델 표기와 실제 모델이 다르지 않게 합니다.
- 다른 로컬 `.tflite`는 EXP03(9,894,032 bytes)와 `yolo11n-pose_int8.tflite`입니다.
  후자는 휴면 `utils/Yolo26Classifier`에 asset open 코드가 있으나 실제 현재 경로의 생성/호출 참조는 찾지 못했습니다. 삭제하지 않았습니다.
- 파일/asset/network 검색으로 모델을 자동 대체하지 않습니다. 모델 누락·hash/shape 불일치는 기존 실패 격리와 Landmark 경로를 유지합니다.

## 클래스 매핑·후처리 검증

output의 채널0~3은 기존 검증된 normalized cx/cy/w/h, 채널4/5는 class0/class1 score로 해석합니다.
이미 sigmoid된 score에 objectness를 곱하거나 sigmoid를 재적용하지 않습니다.
`YoloModelPolicy.classMapping`은 0=SQUAT / 1=SHOULDER_PRESS이며 enum/string 매핑의 뒤바뀜은 찾지 못했습니다.
두 class channel을 각각 우세하게 만든 합성 출력이 decoder/NMS를 거쳐 정확한 enum으로 나오는 회귀 검증을 추가했습니다.
이는 실제 모델 정확도 테스트가 아니라 **인덱스 해석과 매핑의 회귀 테스트**입니다.

raw confidence .10 / stable confidence .60 / NMS IoU .70 / maxDet300 /
min interval250ms / window5·4-agreement·evidence age2000ms는 변경하지 않았습니다.
UNKNOWN은 별도 학습 class가 아니라 검출 없음/낮은 confidence/합의 부족 등을 위한 앱 상태입니다.

## 개발자 표시와 기존 진단 유지

- BuildConfig `YOLO_MODEL_VARIANT=EXP02`, `YoloModelPolicy.MODEL_VERSION=EXP02`로 구분합니다.
- 실제 hash 검증과 native tensor 확인을 통과한 경우에만 DEBUG `YoloModel` 로그에 modelId/file/input/output/classes를 한 번 표시합니다.
- 기존 기술 정보 끝에 `Production Model: EXP02`, `Model File: airptcoach_yolo26n_exp02_fp32.tflite`를 덧붙였습니다.
- 평균 처리 속도·처리시간·thermal·raw/stable·일치/불일치/판단 보류·분모 설명·미비교 표시는 삭제/단순화하지 않았습니다.
- release 사용자 화면과 로그에는 개발자 정보를 활성화하지 않습니다.
- applicationId `com.example.arptapp`, versionCode1 / versionName1.0과 브랜드·아이콘은 유지합니다.
- 로그인·DB schema·리포트 저장/계산 기능은 이번 수정 대상이 아닙니다. 과거 EXP03 진단 JSON도 기존 allowlist로 읽습니다.

## APK 패키징·서명·권리 경계

**2026-11-09 후속 승인:** 본인 기기 전용 별도 DEBUG APK에 한해 모델 포함을 허용했습니다.
이 문서의 기본/공개용 APK 모델 제외 정책은 그대로 유지합니다.
개인용 opt-in 빌드와 출처 고지는 [PRIVATE_EXP02_APK.md](PRIVATE_EXP02_APK.md)를 참고하세요.

사용자는 2026-11-09 **권리 확인 자료 제공 후 APK 포함을 검토**하기로 선택했습니다.
아직 해당 자료 검토/재배포 승인은 완료되지 않았으므로 Gradle의 loose model 제외와 Git ignore 정책을 유지합니다.
**이번 APK에는 EXP02도 EXP03도 내장하지 않습니다.** 기기 private EXP02가 있는 경우에만 DEBUG YOLO를 실행합니다.
다른 기기에 APK만 전송해서 설치하면 MODEL_MISSING일 수 있습니다. 자동 다운로드는 없습니다.

기존 앱 모듈/root Gradle에는 안전하게 구성된 release signing config가 없습니다. 이번에는 서명 가능한 debug APK만 만듭니다.
새 keystore/비밀번호를 만들거나 하드코딩하지 않습니다. release 모델 gate OFF·debug-only 진단 정책도 유지합니다.

필요할 때만 다음 명령으로 **정확한 기본 앱의 DEBUG private sandbox**에 검증된 EXP02를 넣습니다.
기존 원본 앱의 모델·운동 기록 삭제/초기화는 하지 않습니다. 이 명령은 앱을 종료하므로 운동/저장 중에는 사용하지 마세요.

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provision-production-exp02.ps1 -ValidateLocalOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provision-production-exp02.ps1 -AdbPath $adb -WhatIf
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provision-production-exp02.ps1 -AdbPath $adb
```

새 wrapper는 fixed EXP02/fixed 기본 package만 허용하며, 비교 앱 도구와 같은 exact root·symlink·bytes·SHA·atomic move·device hash 검사를 재사용합니다.
기존 EXP03 기본 provisioning 명령과 A/B wrapper의 동작은 보존합니다. 잘못된 EXP03 ProductionApp 요청은 거부합니다.

원격 Supabase/Edge/SQL·billing·새 SDK/유료 API·외부 업로드·LLM 연결은 수행하지 않습니다.
모델 선택이 배포 권리 확인이나 앱 전체 무과금 보증을 대신하지 않습니다.

## 향후 논문·포트폴리오 분석 항목

| 항목 | EXP02 / EXP03 동일 조건 신규 비교 상태 |
|---|---|
| 동일 Test set·split·subject leakage 검증 | TBD / 추가 실험 필요 |
| Precision / Recall / F1 | TBD / 추가 실험 필요 |
| mAP50 / mAP50-95 | TBD / 추가 실험 필요 |
| Android 운동 분류 결과 / 판단 보류율 | 사용자 관찰 보고 있음. 정량 집계·반복 실험 추가 필요 |
| 평균 confidence / FPS / inference latency | TBD / 동일 조건 신규 측정 필요 |
| 오프라인 평가와 실사용 평가 차이 | 추가 실험 필요. 두 평가의 의미를 구분할 것 |
| pseudo-label 품질 | 추가 실험 필요. 퇴화 원인으로 단정하지 않음 |
| 추가학습 후 성능 퇴화 가능성 | 검증할 가설이며 확정 결론 아님 |
| catastrophic forgetting 가능성 | 추가 실험 필요. 현재 관찰만으로 입증되지 않음 |

## 이번 검증·산출물

2026-11-09, 기존 로컬 SDK/Gradle 캐시로 아래 결과를 확인했습니다.

| 검증 | 실제 결과 |
|---|---|
| Gradle 프로젝트 구성 (`:app:help`) | PASS. Android Studio GUI Sync는 별도로 실행하지 않음 |
| `:app:testDebugUnitTest` | 66 suites / 671 tests, failure 0 / error 0 / skipped 0 |
| `node --test --test-reporter=dot scripts/*.test.mjs` | 147 / 147 PASS |
| `:app:lintDebug` | error/fatal 0, warning 298. 경고를 숨기거나 검사 규칙을 완화하지 않음 |
| `:app:assembleDebug` / `:app:assembleDebugAndroidTest` | PASS. 최종 재검증 `BUILD SUCCESSFUL` (1분 47초) |
| 실제 DEBUG APK 서명 검사 | `apksigner verify` PASS, v2 서명 확인 |
| APK 공개 파일 검사 | PASS. loose TFLite/모델·private 검증 자료 미포함, MediaPipe task/고지 유지 |
| 변경 파일 보안 검사 | public text 20개 secret-pattern 검사 PASS, `git diff --check` PASS. Git 이력·전체 법률 감사는 범위 밖 |
| 로컬 EXP02 provisioning 검증 | `-ValidateLocalOnly` PASS. 기기 전송은 실행하지 않음 |
| 이번 변경본 Android 설치·native inference | **NOT_RUN — USB 연결 기기 없음** |
| release APK | **NOT_BUILT — 기존 release signing config 없음** |

재현 명령은 다음과 같습니다. `--no-build-cache`를 사용했고, 기존 SDK와 캐시를 재사용하기 위해 offline으로 실행했습니다.

```powershell
$env:GRADLE_USER_HOME = 'C:\Users\dlgus\.gradle'
$env:ANDROID_USER_HOME = 'C:\Users\dlgus\.android'
.\gradlew.bat :app:help :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --offline --no-daemon --no-problems-report --no-build-cache --max-workers=2 '-PverificationBuildDir=production-exp02-verification'
node --test --test-reporter=dot scripts/*.test.mjs
```

### 설치용 DEBUG APK

- 전달용: `build/production-exp02-verification/APK/AIRPTCoach-Production-EXP02-debug.apk`
- 원본: `build/production-exp02-verification/outputs/apk/debug/app-debug.apk`
- 크기: **36,702,586 bytes** (전달용/원본 SHA-256 일치)
- SHA-256: `c64eb286dd4253b63f822c3471ae26b9f797d64b494364caae81829e79cdf174`
- 실제 APK package: `com.example.arptapp`, label: `AIRPTCoach`
- versionCode: `1`, versionName: `1.0`, minSdk: `26`, targetSdk: `35`
- `debuggable=true`; 공개 배포용 release APK가 아닙니다.
- Lint 보고서: `build/production-exp02-verification/reports/lint-results-debug.html`

**모델 내장은 권리 자료 검토 대기입니다. APK 단독 설치로 YOLO 모델이 공급되지는 않습니다.**
폰의 기존 앱 private 저장소에 올바른 EXP02가 있거나, 앞 절의 provisioning을 별도로 수행해야 합니다.
기존 private 모델 유무·이번 APK 업데이트 후 실제 운동 성능은 이번에 확인하지 않았습니다.

### 다음 실기기 검증

1. 휴대폰을 연결하고 잠금을 푼 후 USB 디버깅을 허용합니다. 운동·저장 중이 아닌지 확인합니다.
2. 위 전달용 APK로 기존 기본 앱을 **업데이트**합니다. 삭제/데이터 초기화는 하지 않습니다. 서명 불일치가 나면 삭제하지 말고 중단합니다.
3. EXP02가 없으면 앞 절의 `provision-production-exp02.ps1`로 기본 앱 private 저장소에 넣습니다. 이 명령은 앱을 종료할 수 있습니다.
4. DEBUG 앱의 운동 준비 → YOLO 진단 → 기술 정보에서 `Production Model: EXP02`, 정확한 파일명과 모델 상태를 확인합니다.
5. 운동 후 기록/상세 리포트의 기존 YOLO Shadow 진단을 확인합니다. YOLO는 여전히 Shadow이며, 운동 결정·횟수/점수의 Primary는 landmark입니다.

선택적으로 test APK를 업데이트한 뒤 아래 **명시적 단일 테스트만** 실행할 수 있습니다.
이 테스트는 로그인/DB/카메라를 사용하지 않고, 기본 앱 private EXP02의 실제 tensor와 synthetic 1-frame native inference를 확인합니다.
실제 운동 정확도 검증은 아니며, 검증용 출력은 앱 private `noBackupFilesDir/yolo/production-runtime/<UUID>/native-profile.json`에만 기록됩니다.

```powershell
& $adb -s SERIAL install -r build/production-exp02-verification/APK/AIRPTCoach-Production-EXP02-debug.apk
& $adb -s SERIAL install -r build/production-exp02-verification/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb -s SERIAL shell am instrument -w -r -e class com.example.arptapp.ui.camera.yolo.ProductionExp02RuntimeDeviceTest com.example.arptapp.test/androidx.test.runner.AndroidJUnitRunner
```

`SERIAL`은 `adb devices`의 실제 기기 ID로 바꾸고, `$adb`는 앞 절의 SDK 실행 경로를 사용합니다.
두 APK 설치와 instrumentation은 이번 작업에서는 실행하지 않았습니다.

### 변경 파일 전체 (20개)

앱 설정/런타임/진단:

- `app/build.gradle.kts`
- `app/src/main/java/com/example/arptapp/domain/detection/YoloExportContract.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/YoloBuildSelection.kt`
- `app/src/main/java/com/example/arptapp/ui/camera/yolo/YoloBuildProfile.kt`
- `app/src/main/java/com/example/arptapp/ui/camera/yolo/Exp02BitmapPreprocessor.kt` (설명 주석만)
- `app/src/main/java/com/example/arptapp/ui/camera/yolo/TfliteYoloRuntime.kt`
- `app/src/main/java/com/example/arptapp/DashboardActivity.kt`

테스트:

- `app/src/test/java/com/example/arptapp/domain/detection/YoloBuildSelectionTest.kt`
- `app/src/test/java/com/example/arptapp/domain/detection/Exp03ContractTest.kt`
- `app/src/testDebug/java/com/example/arptapp/domain/detection/ProductionExp02ModelTest.kt` (신규, 기본 debug 전용)
- `app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp03NativeContractDeviceTest.kt`
- `app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/ProductionExp02RuntimeDeviceTest.kt` (신규, 컴파일 완료/기기 실행 보류)
- `scripts/exp03-shadow.test.mjs`
- `scripts/production-exp02.test.mjs` (신규)

provisioning/문서:

- `scripts/provision-exp03-model.ps1`
- `scripts/provision-production-exp02.ps1` (신규)
- `docs/model-selection-exp02.md` (이 문서, 신규)
- `docs/ml/YOLO_AB_DEVICE_COMPARISON.md`
- `MODEL_CARD.md`
- `README.md`

EXP02/EXP03 모델 원본·기존 실험 결과는 수정/삭제하지 않았습니다.
새 네트워크 호출·유료 API·billing 변경·원격 SQL/배포·자동 commit/push는 수행하지 않았습니다.
