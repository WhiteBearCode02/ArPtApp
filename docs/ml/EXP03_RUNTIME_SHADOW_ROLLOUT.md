# EXP03_FINAL Shadow 전환 기록

2026-11-05 · **구조 전환·native smoke 완료 / actual-image Golden RUN·FAIL / 실제 EXP03 prediction 비활성**

| 경계 | 현재 코드 |
|---|---|
| canonical identity | EXP03_FINAL FP32, 실제 size/hash 확인 |
| Exp03 contract | NOT_VERIFIED (EXP02 VERIFIED를 상속하지 않음) |
| DEBUG build gate | 기존 true 유지. 미검증 contract이므로 prediction=false |
| release build gate | 기존 false, factory=null, preference/Intent/remote flag 우회 없음 |
| Primary | LANDMARK. exercise lock / rep / score / calories / coaching / routing 변경 없음 |
| SDK | 기존 tensorflow-lite 2.14.0/support 0.4.4 유지 |
| APK / Git | 모델 weight 제외 유지. 로컬 원본만 보존 |

`predictionAllowed = contract VERIFIED && shadowRuntimeEnabled && modelStatus READY`를 유지합니다.
현재 EXP03 파일/메타데이터가 맞더라도 CONTRACT_NOT_VERIFIED가 정상입니다.
MODEL_MISSING / MODEL_IDENTITY_MISMATCH / TENSOR_MISMATCH / UNSUPPORTED_RUNTIME /
INITIALIZATION_FAILED 등의 기존 실패 격리는 유지합니다.

## 역사 기록과 공용 알고리즘

KnownYoloModels allowlist는 EXP02+정확한 EXP02 SHA, EXP03+정확한 EXP03 SHA만 허용합니다.
교차 ID/hash·알 수 없는 모델·누락 identity는 거부합니다. 과거 JSON의 gate=null 생략과 bytes/hash를
보존하는 회귀 테스트를 추가했습니다. DB schema/migration이나 Supabase/LLM DTO 변경은 없습니다.
새 세션 collector는 EXP03 identity를 사용합니다. 과거 EXP02 native harness는 명시적으로 EXP02
파일/계약/collector에 묶어 두어 새 canonical 모델로 과거 evidence를 오염시키지 않습니다.

공용 YoloRaw640Algorithms와 YoloBitmapPreprocessor는 기존 구현을 재사용합니다.
raw confidence .10 / stable .60 / NMS IoU .70 / maxDet300 / frame interval250ms /
최근5회중4회 합의는 변경하지 않았습니다. 공유 알고리즘과 모델별 검증 근거는 별개입니다.
기존 DEBUG 수동 후보 검증도 새 모델의 NOT_VERIFIED 상태를 보고합니다. 자동 촬영은 추가하지 않았습니다.
과거 candidate 폴더·EXP02 docs/scripts/원본 모델/사용자 운동 기록을 삭제하거나 재라벨링하지 않습니다.

## 이전 모델 전환·synthetic smoke 검증 (이력)

| 항목 | 결과 |
|---|---|
| 로컬 identity | 9,894,032 bytes 및 사용자 SHA 일치 PASS |
| static FlatBuffer metadata | 실제 input/output FLOAT32 shape 일치 PASS |
| PC LiteRT 2.2.0 synthetic native smoke | 1회 PASS. Android/실제 이미지/Golden 아님 |
| Node guards | 126개 PASS |
| Python bounded parser 회귀 | 13개 PASS |
| raw binary transport | synthetic9종 PASS, EXP02/EXP03 정확한 크기 포함. 실제 ADB 전송 아님 |
| JVM | 62 suites / 592 tests PASS (신규 EXP03 10개 포함), failure/error 0 |
| debug / Android test APK / release | 네 task 전체 BUILD SUCCESSFUL, 최초 전체 실행 7분27초, 최종 증분 재검증 2분2초 |
| APK loose model exclusion | debug/release PASS, MediaPipe task 유지 |
| debug/release/test APK private fixture | loose tflite/pt/pth/onnx, rgb/input.f32, local_validation/reference_results 모두 0 |
| BuildConfig 생성 결과 | debug=true/gate=true, release=false/gate=false 확인 |
| DEBUG 앱 / test APK 업데이트 | authorized USB 1대에 install -r 성공. uninstall / pm clear 없음 |
| Android private provisioning | staging/final 9,894,032 bytes 및 EXP03 SHA 일치 PASS. 기존 EXP02 private SHA도 보존 확인 |
| Android Interpreter / metadata / native smoke | 기존 TF Lite 2.14.0에서 load PASS, FLOAT32 shape 일치 PASS, 합성 입력 invoke 1회 PASS |
| Android instrumentation | Exp03NativeContractDeviceTest 2/2 PASS, 미검증 factory의 prediction=false 및 프레임 제출 거부 확인 |
| EXP03 actual-image Golden | 당시 NOT_RUN. 후속 실제 RUN/FAIL은 아래 참조 |
| 실제 카메라 성능 | NOT_MEASURED |

user-provided PT/TFLite metrics와 변환 delta는 [EXP03 계약 문서](EXP03_ANDROID_INFERENCE_CONTRACT.md)에
별도 표시합니다. Android 수치로 가져오거나 이번 실행으로 주장하지 않습니다.

Android load/metadata/native smoke는 확인했습니다. 후속 EXP03-specific actual-image Golden이 실패했으므로
NOT_VERIFIED를 유지하며, 재검증에서 모두 통과하기 전에는 승격하지 않습니다.
그 전에는 정상 Landmark 운동을 유지하며 실시간 YOLO를 억지로 켜지 않습니다.
정확한 절차: [실기기 체크리스트](EXP03_ANDROID_DEVICE_SHADOW_CHECKLIST.md).

실기기 smoke evidence는 private `no_backup/yolo/exp03-native/runs/<UUID>/native-smoke.json`으로 확인했습니다.
modelId/SHA/bytes 일치, nativeInferenceCount=1, contractStatus=NOT_VERIFIED,
contractPromotionPerformed=false, cameraPerformance=NOT_MEASURED입니다.
단색 합성 입력의 nativeMs=128.967395, detectionsAfterNms=0은 단일 smoke 관찰값이지
운동 정확도·Golden 동등성·카메라 지연/FPS가 아닙니다. 사진·사용자 DB를 읽거나 전송하지 않았습니다.

## 후속 EXP03 actual-image Golden 검증

이번 단계에서는 권리 확인된 로컬 원본4장만 재사용하고, EXP03 모델로 **새 Python inference4회**,
새 reference/RGB/input/raw를 생성했습니다. 사용자 DB/EXP02 출력은 읽지 않았습니다.
정확한 **Ultralytics8.4.202** wheel source를 SHA 고정해 실행했습니다(기존8.4.176 source 아님).
실제 authorized 기기에 private bundle13개만 전송해 Android native **8회**를 실행했습니다.

| 경로 | 결과 |
|---|---|
| 같은 RGB → 기존 Android 전처리 → native → decoder/NMS | **1/4 PASS, 전체 FAIL** |
| Python의 동일 tensor → Android native → decoder/NMS | **4/4 PASS** |
| Python의 실제 raw → Android decoder/NMS | **4/4 PASS**, confidence/bbox 오차0 |
| class / count / ordering | 4장 모두 exact |
| production 전처리 vs OpenCV | padding/geometry 일치, resized RGB 최대1byte 수준 차이 |
| 실패 원인 | 작은 resize 차이로 confidence 최대0.00283441 차이, 기존0.001 초과 |
| contract / prediction | NOT_VERIFIED 유지 / false, 자동 승격 없음 |
| 실제 camera | NOT_MEASURED |

새 source/도구/권리/provenance/개별 결과/수정 파일은
[EXP03_ACTUAL_GOLDEN_VALIDATION.md](EXP03_ACTUAL_GOLDEN_VALIDATION.md)에 기록했습니다.
confidence·bbox tolerance나 threshold를 넓혀 통과시키지 않았습니다. 실패 run도 로컬/기기에 그대로
남겼으며, production 전처리와 기존 EXP02 알고리즘은 이 작업에서 바꾸지 않았습니다.

### 이번 최종 회귀 결과

| 검사 | 실제 결과 |
|---|---|
| JVM | testDebugUnitTest `--rerun` 실제 재실행, 592 tests / failure·error·skip 0 |
| Node | 전체131/131 PASS (새 Golden 구조 검사5개 포함) |
| Python | 관련71/71 PASS: 새 도구14 + EXP02 actual26 + model inspection13 + binary transfer18 |
| assembleDebugAndroidTest | 새 instrumentation 컴파일 BUILD SUCCESSFUL, 2분4초 |
| testDebugUnitTest / assembleDebug / assembleDebugAndroidTest / assembleRelease | 네 task 실행 BUILD SUCCESSFUL, 1분5초. 이후 JVM만 별도 실제 재실행1분3초 |
| Android Golden instrumentation | **1개 FAIL**. actual native8회 성공, A1/4·B4/4·C4/4. 실패를 전체 테스트 PASS로 숨기지 않음 |
| APK | debug/release model 제외·MediaPipe task 유지 PASS, test 포함3종 private RGB/input/output/reference/model 모두0 |
| 보안 | 변경 public text10개 secret-pattern 검사 PASS. 전체 Git 이력/법적 감사 아님 |
| Git | ai-v2 유지, staged binary/media0, EXP02 원본/증거/사용자 데이터 보존, commit/push/PR/merge 없음 |

검증 대상 production/main source, dependencies, thresholds, Primary는 이 단계에서 변경하지 않았습니다.
현재 남은 Gradle10 deprecation 경고는 비차단이며 버전 업그레이드는 하지 않았습니다.

## 보안·비용·배포

새 SDK·유료 API·cloud inference·LLM·원격 SQL·서버 배포·billing 변경 없음.
모델/사진/영상/raw/tensor/계정 데이터의 외부 업로드 없음. 세션 진단은 기존 local-only scalar JSON입니다.
기존 앱의 일반 네트워크 기능/요금제는 이번 범위에서 변경하지 않았으며 앱 전체 0원이나 법적 승인을 보증하지 않습니다.
모델 재배포 권리 확인 전 public APK/Git 포함을 차단합니다. release APK는 unsigned 검사용이며 배포하지 않습니다.
전체 이력/라이선스 감사를 다시 하지 않고 변경 파일·패키징·직접 관련 회귀를 확인합니다.
자동 commit/push/PR/merge는 하지 않습니다.

## 재현 명령과 산출물

```powershell
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
$env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE '.android'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --offline --no-daemon --no-problems-report --max-workers=2 '-PverificationBuildDir=dashboard-ui-verification'
node --test scripts/*.test.mjs
& '.\local_validation\.venv\Scripts\python.exe' scripts/test-exp02-model-inspection.py
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provision-exp03-model.ps1 -ValidateLocalOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/test-exp02-provision-transport.ps1 -PythonPath (Resolve-Path local_validation/.venv/Scripts/python.exe).Path -AdditionalSizes 9894032
```

`build/dashboard-ui-verification/outputs/apk/` 아래 debug/app-debug.apk,
androidTest/debug/app-debug-androidTest.apk, release/app-release-unsigned.apk를 생성했습니다.
release는 서명·배포하지 않았습니다. PC native smoke는 기존 local_validation/.venv의 Python3.11.9,
ai-edge-litert2.2.0으로 실행했으며 새 설치/Android dependency upgrade는 없습니다.
Gradle 10 deprecation 및 테스트 내 Json format 생성 관련 비차단 경고가 남아 있습니다.

## 이전 모델 전환 단계의 변경 파일과 이유 (이력)

아래 경로의 `main/`, `debug/`, `androidTest/`, `test/`는 `app/src/` 기준이며,
Kotlin 기본 prefix는 각 source set의 `java/com/example/arptapp/`입니다. 기존 EXP02 문서와
`scripts/provision-exp02-model.ps1`, `app/build.gradle.kts`는 변경하지 않았습니다.

| 파일 | 변경 이유 |
|---|---|
| `.gitignore` | 새 EXP03 binary Git 제외 |
| `MODEL_CARD.md` | EXP03 identity·제공 metric·Android smoke/Golden 대기를 역사와 구분 |
| `README.md` | 현재 canonical 모델/비활성 상태 안내 |
| `main/.../domain/detection/YoloExportContract.kt` | 정확한 EXP02/03 registry, 현재 identity, runtime/contract modelId API |
| `main/.../domain/detection/YoloModelIdentity.kt` | known profile별 size/hash/filename 비교 |
| `main/.../domain/detection/Exp02YoloContract.kt` | EXP02 identity를 명시하며 기존 VERIFIED 보존 |
| `main/.../domain/detection/Exp03YoloContract.kt` (신규) | EXP03 독립 NOT_VERIFIED provenance |
| `main/.../domain/detection/YoloRaw640Algorithms.kt` (신규) | 기존 수학/임계값을 복제하지 않는 공통 조립 API |
| `main/.../domain/detection/YoloExerciseDetector.kt` | 다른 모델의 VERIFIED 계약 사용 차단 |
| `main/.../domain/detection/YoloSessionDiagnostics.kt` | 기본 EXP03/명시적 과거 검증용 collector identity |
| `main/.../data/YoloSessionDiagnosticsCodec.kt` | 과거 EXP02와 신규 EXP03 exact pair allowlist, 누락 identity 거부 |
| `main/.../ui/camera/yolo/Exp02BitmapPreprocessor.kt` | 공통 YoloBitmapPreprocessor + 이전 이름 typealias |
| `main/.../ui/camera/yolo/TfliteYoloRuntime.kt` | canonical EXP03 기본값·검증된 identity 보고·새 계약 연결 |
| `debug/.../ui/camera/yolo/validation/DebugPositiveNativeInference.kt` | 공유 알고리즘 사용, EXP03 NOT_VERIFIED 상태의 수동 검증 |
| `debug/.../ui/camera/yolo/validation/DebugPositiveValidationFactory.kt` | 수동 worker/오류 메시지의 모델 provenance 정리 |
| `test/.../domain/detection/YoloContractTest.kt` | 기존 EXP02 검증 테스트를 legacy ID에 명시 결합 |
| `test/.../domain/detection/Exp03ContractTest.kt` (신규) | identity·과거 JSON/hash·교차 pair·계약 상속 차단 10개 |
| `androidTest/.../ui/camera/yolo/Exp02ActualModelGoldenTest.kt` | 기존 실제 Golden을 계속 EXP02 모델로 실행 |
| `androidTest/.../ui/camera/yolo/Exp02PositiveModelGoldenTest.kt` | 기존 양성 evidence EXP02 결합 유지 |
| `androidTest/.../ui/camera/yolo/Exp02NmsContractTest.kt` | 기존 NMS evidence SHA를 EXP02로 고정 |
| `androidTest/.../ui/camera/yolo/YoloRealtimeShadowDeviceTest.kt` | 역사적 EXP02 runner/native/collector를 새 canonical과 분리 |
| `androidTest/.../ui/camera/yolo/Exp03NativeContractDeviceTest.kt` (신규) | 새 모델 load/metadata/synthetic native 및 현재 factory 차단 검증, 실기기 2/2 PASS |
| `scripts/inspect-exp02-model.py` | 기존 기본값을 유지하며 bounded parser identity 인자를 재사용 가능하게 함 |
| `scripts/inspect-exp03-model.py` (신규) | 실제 새 모델 정적 tensor/옵션 PC smoke, 승격 불가 |
| `scripts/provision-exp03-model.ps1` (신규) | DEBUG private noBackup 주입, size/hash·link/path·atomic 검증 |
| `scripts/test-exp02-provision-transport.ps1` | 기존 크기8종 유지 + 선택적 EXP03 크기 테스트 |
| `scripts/exp02-actual-golden.test.mjs` | legacy 명시 identity 바인딩 guard 갱신 |
| `scripts/exp02-positive.test.mjs` | 공통 후보 알고리즘 API guard 갱신 |
| `scripts/yolo-session-wiring.test.mjs` | Interpreter 이전 identity 검증의 새 함수 인자 검사 |
| `scripts/yolo-shadow.test.mjs` | 새 factory/정확한 identity 검사 구조 guard |
| `scripts/exp03-shadow.test.mjs` (신규) | packaging/역사/계약/provisioning/smoke 경계 6개 |
| `docs/ml/EXP03_ANDROID_INFERENCE_CONTRACT.md` (신규) | 실제 식별·제공 metric·후보 계약/미검증 경계 |
| `docs/ml/EXP03_ANDROID_DEVICE_SHADOW_CHECKLIST.md` (신규) | 데이터 삭제 없는 실기기 절차 |
| 이 문서 (신규) | 전체 상태/검증/변경 이유·비용/보안 경계 |

모델 binary, PC inspection JSON, synthetic transport 임시 파일, APK/빌드 파일은 공개 변경 목록에 넣지 않습니다.
