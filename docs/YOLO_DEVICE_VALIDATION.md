# YOLO Android Shadow Mode · 계약 구현 및 검증

기준일: 2026-10-16. 상태: **local development / research validation**.

> 정확한 8.4.176 소스 기반 전처리·decoder·NMS와 Python/Kotlin 합성 Golden 검증 구조를 추가했습니다. OpenCV 보간 차이의 실제 모델 영향과 Android native 호환성이 미검증이므로 **전체 NOT_VERIFIED / 실제 prediction 비활성 / Landmark Primary**입니다.

이 문서는 YOLO accuracy 또는 모바일 inference 성공 보고가 아닙니다. 이번에 제공된 [canonical concise notebook](ml/AIRPTCoach_EXP02_Export_Verify.ipynb) 전체와 PyPI Ultralytics **8.4.176 wheel**의 실제 source를 대조했습니다. [21개 항목별 근거·hash·predict/val 차이](ml/EXP02_ANDROID_INFERENCE_CONTRACT.md)를 함께 읽어 주세요. Notebook은 실행·수정하지 않았고 과거 test metric을 재측정하지 않았습니다.

## 확인된 모델 정보

| 항목 | 값 |
|---|---|
| 파일 | `app/src/main/assets/airptcoach_yolo26n_exp02_fp32.tflite` (로컬 원본 유지) |
| Model ID | `yolo26n_exp02_fp32` |
| 입력 기대값 | FLOAT32 `[1,3,640,640]` |
| 출력 기대값 | FLOAT32 `[1,6,8400]` |
| Class mapping | `0=SQUAT`, `1=SHOULDER_PRESS`; LUNGE/READY/UNKNOWN은 학습 class 아님 |
| 크기 / SHA-256 | 9,894,030 bytes / `464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749` |
| Git / APK | 기존 ignore 유지, 새 tracking 없음. debug/release 공통 weight asset 제외 정책 유지 |

기존 `utils/Yolo26Classifier.kt`는 YOLO11 pose asset을 3개 점수처럼 해석하는 휴면 코드이므로 재사용·활성화하지 않았고 삭제하지도 않았습니다. 기존 TF Lite 2.14.0 의존성만 재사용합니다. 모델을 새 형식으로 변환하거나 runtime/delegate를 추가하지 않았습니다. 최신 LiteRT export가 기존 Android runtime의 모든 op를 지원하는지는 **별도 native 검사 필요**이며 실패하면 YOLO만 unavailable입니다.

## 구현된 경계

- `ExerciseDetector<F>`: initialize/detect/close 공통 port.
- `LandmarkExerciseDetector`: 원래 `ExerciseClassifier`에 위임하는 실제 primary. 기존 정면/측면 분류·임계값·세션 lock 유지.
- `YoloExerciseDetector`: 모델 존재→canonical 크기·SHA256→native 생성→입출력 개수·shape·dtype 검증→export contract gate 순서. `MODEL_IDENTITY_MISMATCH`, `UNSUPPORTED_RUNTIME`을 전처리 오류와 구분하며 예외는 YOLO 상태만 전환합니다.
- `YoloPreprocessor`, `YoloOutputDecoder`, `YoloPostProcessor`: 인터페이스 유지 + `Exp02BitmapPreprocessor`/`Exp02RgbPreprocessor`, `Exp02OutputDecoder`, `Exp02PostProcessor` 구현. typed 중간 candidate와 실제 geometry를 전달합니다. 미확인 export용 `UnverifiedYoloContract`도 삭제하지 않았습니다.
- `DebugYoloShadowFactory`: DEBUG에서만 source-backed `Exp02YoloContract`를 주입하지만 코드의 verification은 **NOT_VERIFIED**입니다. 세 포트 구현이 존재한다는 이유로 실행하지 않습니다. 환경 변수/UI/property로 승격할 수 없습니다.
- `YoloShadowRunner`: CameraX/MediaPipe와 별도 native owner worker, 단일 진행·무대기열·250ms admission 정책(최대 약 4 FPS 구조). 현재는 READY가 아니므로 bitmap 복사·preprocess·run이 **실행되지 않습니다**.
- `YoloExerciseStabilizer`: 최근 5회 중 4회 합의 구조. raw confidence >0.10, stable vote >=0.60은 **APP_ENGINEERING_INITIAL** 잠정 정책이며 현재 실제 추론에는 사용되지 않습니다. reference predict 0.25 / historical val default 0.001과 구분합니다.
- `DetectorComparison`: raw/stable YOLO, fallback, 고정 session, cameraView, 지연 구간, thermal의 scalar 비교. 다인 입력은 `MULTI_PERSON_UNASSOCIATED` 표시. bbox-to-pose 동일인 매칭은 구현하지 않았습니다.

비교는 비동기 최신 상태이며 같은 프레임/같은 사람에 대한 동기화된 평가가 아닙니다. 다인 경고는 전달된 pose/detection 개수에만 의존하므로 화면의 모든 사람을 검출했다고 보장하지 않습니다. 기본 Pose Landmarker 설정/가림 등으로 여러 사람이 있어도 한 포즈만 반환될 수 있습니다.

현재 YOLO 상태에서 실제 sessionExerciseType으로 이어지는 경로는 없습니다. fallback만 `MainViewModel.observeDetectedExercise`에 전달되며, 세션 종목·횟수·코칭·기존 저장/업로드·이전 기록 읽기 계약은 그대로 유지합니다.

## Source 확인 및 아직 남은 검증

Source로 확인한 계약: logical RGB → centered aspect-preserving LetterBox 640×640(114 pad, ties-to-even rounded size) → FLOAT32 BCHW `/255`. Raw BCN은 normalized center xywh + 이미 sigmoid된 class 0/1 점수이며 objectness 곱/재-sigmoid를 하지 않습니다. 모델 밖에서 strict confidence `>` → class-aware NMS IoU>0.7, 최대 300 → rounded X/Y gain 역변환 → 원본 clip 순서입니다. `predict`와 historical `val` loader는 동일하지 않습니다.

현재 Kotlin half-pixel bilinear와 OpenCV `INTER_LINEAR`는 resampled uint8 channel에서 **최대 1 byte 차이가 관찰**됐습니다. Golden은 이 한정된 차이만 `1/255 + 1e-6`로 허용하고 padding/native 크기·RGB/layout은 `1e-6`로 검사합니다. 이는 bitwise 동일성도, 모델 출력이 같은지의 증거도 아닙니다. 이 차이를 검토할 actual-model golden이 없으므로 `TODO(EXP02_EXPORT_CONTRACT)`/전체 NOT_VERIFIED를 유지합니다.

아직 미검증: 실제 Android tensor metadata 재검사·native init/run·actual-model golden·모바일 accuracy/latency/thermal. `DetectionBox`는 후처리 후 원본 분석 frame pixel box이며 Preview crop/mirror나 pose-person association을 해결하지 않습니다. 비유한 값·면적 overflow는 거부하고 완전 clip되어 퇴화한 box는 추가 안전정책으로 폐기합니다. NMS 동률은 anchor index 순서로 결정하며 Python 핵심 Golden은 동률을 피합니다.

## 모델 없는 APK에서의 예상 동작

1. 후면 카메라, MediaPipe 스켈레톤, landmark primary는 종전과 같이 동작합니다.
2. DEBUG 진단에 `MODEL_MISSING`, `exportContract=NOT_VERIFIED`, raw/stable UNKNOWN, latency `미측정`이 나오는 것이 정상입니다.
3. 모델이 안전한 로컬 개발 경로에 제공되어 identity/native/tensor 검사가 통과해도 `CONTRACT_NOT_VERIFIED`로 중단합니다. native 호환 실패는 `UNSUPPORTED_RUNTIME` 또는 일반 `INITIALIZATION_FAILED`, 잘못된 파일은 `MODEL_IDENTITY_MISMATCH`로 별도 표시합니다. 픽셀 변환/run은 하지 않습니다.
4. release에서는 YOLO runner를 생성하지 않고 진단 UI/로그도 숨깁니다. release 실행·서명 배포는 이번 검증 범위가 아닙니다.

원본 `assets` 모델을 복사·삭제·이동하거나 APK에 포함하지 않았습니다. 따라서 이 APK를 설치하는 것만으로 로컬 PC 모델이 휴대폰에 전달되지는 않습니다.
개발용 resolver는 앱의 `noBackupFilesDir/yolo/airptcoach_yolo26n_exp02_fp32.tflite`만 검사합니다. Asset fallback은 제거했습니다. 앱은 모델을 다운로드하거나 자동 복사하지 않습니다. 기기 모델 provisioning은 이번에 수행하지 않았습니다. 이를 위해 public APK 정책을 해제하지 마세요.

### 개발자가 직접 하는 DEBUG local provisioning (기기 미연결로 미실행)

현재 loader는 `noBackupFilesDir/yolo/airptcoach_yolo26n_exp02_fp32.tflite`만 읽습니다. Assets fallback은 제거했습니다. 모델 파일을 PC assets 폴더에 보관하는 기존 정책과 APK 패키징 제외 정책은 그대로이며, 휴대폰으로 자동 복사하지 않습니다.

Windows에서는 검토된 [provision-exp02-model.ps1](../scripts/provision-exp02-model.ps1)을 사용합니다. 이 helper는 debuggable `run-as`, 모델 이름/크기/SHA256, 선택한 기기를 확인하고 binary-safe stream으로 **앱 전용 no_backup** 안에 원자적으로 전달합니다. PowerShell 텍스트 파이프나 `/sdcard`, `/data/local/tmp` 공용 모델 staging을 사용하지 않습니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/provision-exp02-model.ps1 -AdbPath 'C:\Users\dlgus\AppData\Local\Android\Sdk\platform-tools\adb.exe' -DeviceSerial '본인_기기_serial'
```

모델 제공만으로 contract가 활성화되지 않습니다. 실제 이미지·Python reference·Android native Golden 순서, 선택 Golden bundle 제공 방법 및 코드 승격 조건은 [새 체크리스트](ml/EXP02_ANDROID_DEVICE_SHADOW_CHECKLIST.md)를 따르세요. 모델 재배포 권리 정책은 변경하지 않았습니다.

## 동시성·실패·종료

- initialization deadline 5초, future inference deadline 2초는 측정값이 아닌 초기 운영 안전장치입니다.
- timeout은 새 제출을 막고 늦은 결과를 버립니다. native run 중 다른 thread에서 Interpreter를 강제로 닫지 않습니다. 종료/close는 해당 owner worker에서 직렬 처리합니다.
- native 호출이 영구 정지하면 강제 중단을 보장할 수 없습니다. UI/MediaPipe가 그 worker를 기다리지는 않으며 병렬 재시도·추가 worker를 만들지 않습니다.
- 모델은 tensor 검증 후 계약 미확인이면 즉시 닫습니다. 미확인 상태에서 매 프레임 재초기화하지 않습니다.
- 카메라 렌즈/촬영 방향/새 세션에서 과거 shadow evidence를 폐기하고 originating epoch로 UI 지연 콜백까지 걸러냅니다. initialization은 frame gate를 취득하지 않았으므로 완료 시 gate를 잘못 해제하지 않습니다. MediaPipe 원본 bitmap을 YOLO가 recycle하지 않습니다. 향후 READY일 때만 single-flight 승인 뒤 소유 복사본 하나를 사용합니다.

## 현재 기기 확인 체크리스트 (실기기 미검증)

- [ ] 모델 없는 최신 debug APK 설치 후 카메라·스켈레톤·TTS·기록 기능이 유지된다.
- [ ] 진단이 MODEL_MISSING/NOT_VERIFIED이며 confidence·지연시간을 만들어 표시하지 않는다.
- [ ] 촬영 방향과 렌즈를 바꾸어도 앱이 종료되지 않는다. 가로/세로 레이아웃에 진단 항목이 있다.
- [ ] 스쿼트/숄더프레스 세션이 기존 fallback으로만 확정되고 YOLO 상태가 종목/횟수/점수를 바꾸지 않는다.
- [ ] 원격 업로드·영상 저장·자동 네트워크 연결이 YOLO 때문에 추가되지 않는다.

## 계약 확보 후에만 진행할 실기기 비교

**지금은 아래를 수행해도 YOLO prediction이 나오지 않습니다.** 다음은 future validation 계획이며 완료 표시/정확도 수치를 생성하지 않습니다.

| 운동 | 촬영 | 수행 | raw / stable / confidence | pre / infer / post / total | fallback / session / thermal |
|---|---|---:|---|---|---|
| SQUAT | FRONT | 10회 | 미측정 | 미측정 | 미기록 |
| SQUAT | 45° | 10회 | 미측정 | 미측정 | 미기록 |
| SQUAT | SIDE | 10회 | 미측정 | 미측정 | 미기록 |
| SHOULDER_PRESS | FRONT | 10회 | 미측정 | 미측정 | 미기록 |
| SHOULDER_PRESS | 45° | 10회 | 미측정 | 미측정 | 미기록 |
| SHOULDER_PRESS | SIDE | 10회 | 미측정 | 미측정 | 미기록 |

현재 앱의 cameraView는 FRONT/SIDE/UNKNOWN만 있습니다. 향후 45° 비교 시 실제 각도는 수동 검증 메모에 별도 기록하고 앱 설정값도 함께 적으세요. 45°를 새로운 임상 분석 모드로 간주하지 않습니다. 다인·background·가림·조명별 비교도 필요합니다.
개인 식별 사진·영상·userId·이메일·토큰·키를 공개 검증 기록에 추가하지 마세요. Colab의 기존 113ms를 모바일 속도로 사용하지 않습니다.

## 테스트와 재현 명령

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user-home'
$env:ANDROID_USER_HOME = Join-Path $env:GRADLE_USER_HOME 'android'
.\gradlew.bat assembleDebug testDebugUnitTest --offline --no-daemon --no-problems-report --console=plain -PverificationBuildDir=yolo-contract-verification --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8'
node --experimental-strip-types --test scripts/*.test.mjs supabase/functions/generate-workout-report/*.test.ts supabase/functions/youtube-ranking/*.test.ts
node scripts/verify-coaching-security.mjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-public-apk.ps1 -ApkPath build/yolo-contract-verification/outputs/apk/debug/app-debug.apk
git diff --check
```

JVM는 fake runtime/contract 외에 실제 pure Kotlin RGB/letterbox/decoder/NMS를 synthetic exact-source Python Golden과 대조합니다. VERIFIED 테스트 더블은 실제 모델 계약을 승격하지 않습니다. `scripts/generate-exp02-golden.py`는 pinned wheel SHA와 source byte identity를 검사하고 원본 함수를 AST 추출 실행합니다. LiteRT backend에는 **fake interpreter**를 사용하므로 실제 모델 inference가 아닙니다. Python reference 재생성은 ignored 로컬 NumPy/OpenCV/Torch 환경에서만 실행하며 Android dependency는 추가하지 않았습니다.

### 현재 계약 구현 단계 실행 결과 (2026-10-16)

- `assembleDebug testDebugUnitTest`: **BUILD SUCCESSFUL**. 최초 전체 5분 15초, 마지막 overflow/geometry/lazy-allocation 보완 포함 재실행 **3분 24초**(49 tasks: 10 executed / 39 up-to-date). 진행 출력이 있었으며 3분 무응답 중단 대상 명령은 없었습니다.
- Android JVM: **43 suites / 351 tests / 실패·오류·skip 0**. 이전 300개 testcase 이름과 비교해 누락 **0개**, 새 51개(Exp02 contract/Golden 39, detector/identity/runtime 9, worker regression 3).
- Node 서버·보안·배선: **63 tests / 실패·skip 0**. 원격 DB/Edge E2E가 아닌 local fake HTTP·정적 경계 검사입니다.
- Python: exact-version original source AST 기반 fixture 재생성 `--check` 통과. 실제 NumPy 2.4.6 / OpenCV-headless 4.13.0.92 / Torch 2.9.1+cpu, LiteRT backend에는 fake interpreter 사용. 모델은 실행하지 않았습니다. 환경과 wheel은 ignored `build/` 아래에만 있습니다.
- Preprocess golden 6종: landscape, portrait, square, odd padding, downscale, native size. Native/padding 오차 <=1e-6, resized sample은 <=1/255+1e-6. 별도 전체 합성 RGB 배열 비교에서도 관찰 최대 차이 1 byte였으나 모든 이미지/모델의 동등성 보장은 아닙니다.
- Decoder golden과 5개 NMS fixture의 class/confidence/box 대조 통과. bbox 1e-4 pixel, confidence 1e-6; NMS 선택/순서는 동일해야 합니다. No actual-model golden.
- APK 패키징 PASS: loose YOLO/미검토 weights 없음, `pose_landmarker_lite.task` 유지. 최신 APK: **`build/yolo-contract-verification/outputs/apk/debug/app-debug.apk`**, **36,499,422 bytes**.
- APK SHA256: `823D279BA1DE36A2820291C3658AD7BEFFF9C17FE5C5BD9989FBE72B98803BD5`.
- 변경된 공개 텍스트 27개 scoped secret-pattern 검사 및 `git diff --check` 통과. Python/JSON/notebook까지 검사 확장, 실제 비밀값 출력 없음. Canonical notebook은 13 cells / output 0 / embedded images 0. 큰 과거 notebook은 local-only ignore로 보호했습니다. Git history/전체 저장소 라이선스 스캔은 하지 않았습니다.
- Canonical 모델 SHA256 유지, 복사·삭제·이동·재-export 없음. Gradle/Android dependencies 변경 없음. ADB 확인: online/offline/unauthorized 기기 모두 **0**, serial 미출력. 실제 Android native init/run·UI·latency·accuracy·thermal은 **미실행/미측정**입니다.

### 이전 구조-only 단계 실행 기록 (2026-10-16, 현재 APK와 다름)

- `assembleDebug testDebugUnitTest`: **BUILD SUCCESSFUL**, 14분 39초. 격리된 새 build directory에서 49개 task 실행, 진행 출력 확인. 명령 중단은 없었습니다.
- Android JVM: **42 suites / 300 tests / 실패·오류·skip 0**. 기존 265개 유지 + 새 35개(계약/실패 18, 안정화/비교 10, worker/timeout 7).
- Node 서버·보안·배선 검사: **62 tests / 실패 0**. 기존 56개 유지 + Shadow guard 6개. fake HTTP/정적 배선 검사이며 실제 원격 배포/E2E가 아닙니다.
- APK 패키징 검사: YOLO/미검토 loose weight 없음, `pose_landmarker_lite.task` 유지. 생성 APK와 EXP02 원본의 Git ignore 유지 확인.
- 변경된 공개 텍스트 **22개 한정** 비밀정보 패턴 검사와 `git diff --check` 통과. 키 값은 출력하지 않았고 Git history/전체 라이선스 스캔은 하지 않았습니다.
- 모델 원본 hash는 위 값과 일치, 파일 복사·삭제·이동 없음. 실제 모델의 **Android native init/inference·실기기 UI·latency·accuracy는 미실행/미측정**입니다.
- 테스트 APK: `build/yolo-shadow-verification/outputs/apk/debug/app-debug.apk`, 36,407,571 bytes.
- APK SHA-256: `CCB825EFD122EAB4C536E6C9CF1747D44D8C11DFD91A83BE7C34D9295E4E1AAC`.

TensorFlow 기존 중복 namespace, native symbol strip 불가, Gradle 10 대비 deprecation 경고는 남아 있습니다. 이번 빌드 실패는 아니며 의존성을 임의로 바꾸지 않았습니다. 이전 `pose-stability-verification` / `security-hardening-verification` 폴더의 APK는 이번 소스로 자동 갱신되지 않습니다.

## 비용·공개·보안

새 외부 API·키·SDK·cloud inference·frame/image upload·관절 좌표 저장을 추가하지 않았습니다. 최신 단계에서는 DEBUG 세션의 scalar/enum **집계만 별도 nullable Room JSON에 로컬 저장**하고 과거 기록/리포트에서 DEBUG only로 표시합니다. 프레임별 bbox/confidence 로그·원본 미디어·landmark 배열은 저장하지 않으며, 기존 서버/LLM payload에도 추가하지 않습니다. LLM false/Unavailable, 기존 backup 금지, 모델 Git ignore·APK 제외 정책을 유지합니다. 기존 다른 앱 기능의 서버/계정 요금제에 대한 무과금 보장은 이 코드 변경 범위가 아닙니다.

**공개 재배포 권리 미확정**, public release ready 아님. 원격 DB migration/Edge Function 배포/billing 변경/자동 commit/push를 하지 않습니다.

## 다음 작업

1. Kotlin/OpenCV 보간 차이의 영향을 확인할 권리 문제 없는 **actual-model Python↔Android Golden** 확보. 필요시 exact interpolation 구현 보완. 현재 synthetic 허용오차 통과만으로 gate를 승격하지 않음.
2. Canonical 모델을 로컬 debug 기기에 수동 제공하고 Android runtime init 및 metadata 검사. unsupported op라면 원인을 먼저 보고하고 dependency/재-export는 별도 결정. 이 검증 환경에서는 연결 기기가 없어 미실행.
3. 실제 Android runtime init/inference와 FRONT/45°/SIDE 비교, YOLO vs fallback, latency·thermal 측정.
4. confidence/stabilization 조정 후 YOLO primary 전환 여부를 별도 승인/판단. 현재는 절대 primary로 사용하지 않음.
5. 필요할 때 FP16/INT8 최적화를 별도 수행. 현재 FP32 모델 파일은 그대로 유지.

## 이전 구조-only 단계 변경 파일 기록

- `app/src/main/java/com/example/arptapp/domain/detection/`: `ExerciseDetector.kt`, `YoloExportContract.kt`, `YoloExerciseDetector.kt`, `YoloExerciseStabilizer.kt`, `YoloShadowRunner.kt`, `DetectorComparison.kt` (신규).
- `app/src/main/java/com/example/arptapp/ui/camera/`: `LandmarkExerciseDetector.kt`, `yolo/TfliteYoloRuntime.kt` (신규), `PoseCameraController.kt` (수정).
- `app/src/main/java/com/example/arptapp/DashboardActivity.kt` (수정).
- `app/src/main/res/layout/activity_dashboard.xml`, `app/src/main/res/layout-land/activity_dashboard.xml` (수정).
- `app/src/test/java/com/example/arptapp/domain/detection/`: `YoloContractTest.kt`, `YoloStabilizerTest.kt`, `YoloShadowRunnerTest.kt` (신규).
- `scripts/yolo-shadow.test.mjs` (신규), `scripts/pose-stability.test.mjs` (adapter 배선에 맞춰 수정).
- `README.md`, `MODEL_CARD.md`, `docs/ML_EXPERIMENTS.md`, `RELEASE_READINESS.md` (수정), 이 문서 (신규).

이전 구조-only 단계에서는 `app/build.gradle.kts`, `.gitignore`, Room schema/entity, Supabase 소스·SQL, LLM provider, 원본 모델 파일을 변경하지 않았습니다. 현재 단계에서는 `.gitignore`에 큰 과거 notebook의 정확한 경로만 추가했습니다. 파일은 로컬에 그대로 보존하며 canonical concise notebook과 공개용 합성 fixture만 검토 대상으로 둡니다.

## 현재 계약 구현 단계 변경 파일 목록

아래 27개 구현·검증·문서 파일을 변경/추가했습니다. 별도로 사용자가 제공한 concise notebook 1개를 읽기 전용 근거로 사용했습니다(총 공개 변경 대상 28개, `.gitignore` 포함). 큰 과거 notebook은 수정·삭제하지 않았습니다.

| 경로 | 파일 |
|---|---|
| `app/src/main/java/com/example/arptapp/domain/detection/` | 수정: `ExerciseDetector.kt`, `YoloExportContract.kt`, `YoloExerciseDetector.kt`, `YoloShadowRunner.kt`, `DetectorComparison.kt`; 추가: `Exp02Preprocessor.kt`, `Exp02PostProcessor.kt`, `Exp02YoloContract.kt`, `YoloModelIdentity.kt` |
| `app/src/main/java/com/example/arptapp/ui/camera/` | 수정: `PoseCameraController.kt`, `yolo/TfliteYoloRuntime.kt`; 추가: `yolo/Exp02BitmapPreprocessor.kt` |
| `app/src/main/java/com/example/arptapp/` | 수정: `DashboardActivity.kt` (촬영 방향/새 세션의 오래된 Shadow 진단만 무효화) |
| `app/src/test/java/com/example/arptapp/domain/detection/` | 수정: `YoloContractTest.kt`, `YoloShadowRunnerTest.kt`; 추가: `Exp02ContractTest.kt` |
| `app/src/test/resources/yolo/` | 추가: `exp02-predict-golden.json` (합성 수치만) |
| `scripts/` | 수정: `yolo-shadow.test.mjs`, `verify-coaching-security.mjs`; 추가: `generate-exp02-golden.py` |
| 문서/공개 정책 | 수정: `.gitignore`, `README.md`, `MODEL_CARD.md`, `RELEASE_READINESS.md`, `docs/ML_EXPERIMENTS.md`, `docs/YOLO_DEVICE_VALIDATION.md`; 추가: `docs/ml/EXP02_ANDROID_INFERENCE_CONTRACT.md` |
| 사용자 제공 근거 (내용 변경 없음) | `docs/ml/AIRPTCoach_EXP02_Export_Verify.ipynb` |

Room·DB·SQL·Edge Function·LLM·Gradle dependency·원본 모델 자산은 이번 단계에서 변경하지 않았습니다. 실제 모델 자동 제공/다운로드, 원격 배포, 자동 commit/push도 하지 않았습니다.
