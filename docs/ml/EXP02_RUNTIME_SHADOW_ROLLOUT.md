# EXP02 · VERIFIED contract와 DEBUG Shadow rollout

2026-10-17 · 사용자 명시 승인에 따른 코드 변경. **VERIFIED는 runtime contract equivalence를 의미하며 model accuracy 보장은 아니다.**

## 상태와 실행 조건

| 항목 | 이전 | 이번 코드 |
|---|---|---|
| Exp02YoloContract | NOT_VERIFIED | VERIFIED |
| DEBUG Shadow | contract에 결합되어 비활성 | 명시적 BuildConfig.YOLO_SHADOW_ENABLED=true |
| release Shadow | runner 생성 안 함 | 기존대로 생성 안 함, build gate도 false |
| Primary / session lock | LANDMARK | LANDMARK, 변경 없음 |
| 모델 | private noBackup provisioning | 동일, Git/APK 제외·canonical identity 검사 |

```text
predictionAllowed = contract VERIFIED AND shadowRuntimeEnabled AND model READY

DEBUG BuildConfig ──> YoloRuntimePolicy (독립·불변 gate)
private model ──> size/SHA256/native metadata ──> READY
contract + gate + READY ──> YoloShadowRunner ──> local scalar diagnostics
LandmarkExerciseDetector ──> session lock ──> rep / score / routing / coaching
```

이전에는 `DebugYoloShadowFactory → YoloExerciseDetector.initialize()`에서 VERIFIED이면 READY가 되고 runner가 READY만 검사해 복사/추론했습니다. 지금은 detector가 기본 OFF이며 `detect()`가 전처리 전 policy를 검사하고, runner도 `predictionAllowed`를 프레임 복사 전에 검사합니다. VERIFIED+OFF는 모델이 READY여도 추론하지 않습니다. NOT_VERIFIED+ON도 실행할 수 없습니다. gate는 preference/Intent/원격 flag가 아니라 빌드 코드이며 release에서 요청해도 `debug && requested`가 false입니다.

DEBUG를 다시 끄려면 `app/build.gradle.kts`의 debug `YOLO_SHADOW_ENABLED`를 false로 변경하고 rebuild합니다. 모델/fixture 파일 존재만으로 gate나 contract를 승격할 수 없습니다.

## 승격 근거와 제한

승격 직전 [최종 리뷰](EXP02_VERIFIED_REVIEW.md)의 실제 결과를 다시 확인했습니다.

| Contract Evidence | 판정 | 근거 |
|---|---|---|
| Model identity | PASS | canonical filename/9,894,030bytes/SHA256 고정, runtime에서도 검사 |
| Tensor | PASS | FLOAT32 input[1,3,640,640], output[1,6,8400] |
| Preprocessing | PASS | exact8.4.176 RGB/BCHW/FLOAT32/255/center letterbox114 및 actual 비교 |
| Decode / Confidence / BBox | PASS | normalized center xywh·2class mapping·외부 NMS/역변환, 두 class actual 동등 |
| NMS | PASS | actual class0 overlap2종 + negative1 + controlled6종의 Android/Python9종 |
| Synthetic Golden / Actual Negative | PASS | 이전 실행 증거 보존, padding엄격/보간1-byte 한계 유지 |
| SQUAT positive | PASS | formal actual positive2장 |
| SHOULDER_PRESS positive | PASS | 추가 actual positive1장, 신뢰도 차이1.4901161e-7/bbox최대3.0517578e-5px |
| Android native | PASS | 이전 실제 기기의 model load/metadata/native inference 증거 |
| Persistence | PASS(기존) | 실제 사용자 세션 save/restart/readback/Report와 in-memory 회귀 근거 |

위 PASS는 기존 contract 증거이지 **이번 새 APK의 카메라 실시간 성공을 대신하지 않습니다.**

사용자 확인 sanity: SQUAT5장 중 검출4·무검출1(추가2장은 capture/Python 비교), SHOULDER_PRESS3장 중 검출1·무검출2. 약10.8% 양성은 stable minimumConfidence60%보다 낮아 안정된 결과로 취급하지 않습니다. 전체 모델 accuracy, 모든 사람/방향/해상도, 자세 안전성을 보장하지 않습니다. 실제 class1 multi-overlap이나 pose-person association은 확인하지 않았습니다.

raw `.10` / stable `.60` / IoU `.70` / maxDet300 / interval250ms / 최근5회중4회 합의는 변경하지 않았습니다. OpenCV 보간과 모든 입력의 bitwise 동일성을 주장하지 않으며 기존 비교 한계를 넓히지 않았습니다.

## 세션 집계와 History / Report

기존 `YoloSessionDiagnosticsCollector`를 재사용합니다. 별도 프레임 DB/중복 카운터/신규 테이블은 없습니다.

- processed/admitted/seen, raw 및 stable SQUAT/SHOULDER_PRESS/UNKNOWN.
- Landmark 확정 세션과 stable match/mismatch/unknown, 확정 전 표본은 비교에서 제외.
- detection이 있는 선택 대상 프레임만 confidence 평균/최소/최대에 포함(무검출0 합산 금지).
- preprocessing/inference/postprocessing/total 평균, 완료 native frames/세션 monotonic 시간인 FPS, 최대 thermal.
- cameraView, sessionExercise, 기존 model/contract/predictionEnabled.
- 새 `shadowRuntimeEnabled: Boolean?`만 추가: readiness와 독립. 과거 JSON의 누락은 null로 읽어 **기록 없음 · 이전 버전** 표시. 과거 기록을 VERIFIED/ENABLED로 소급 변경하지 않음.

이 필드의 기본 null은 `@EncodeDefault(NEVER)`로 생략해 기존 canonical JSON/hash를 유지합니다. true/false는 명시 저장하며 나머지 strict codec 정책은 보존합니다. 이 annotation은 format의 encodeDefaults와 독립적으로 기본값 생략을 제어합니다([Kotlin 공식 API](https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-core/kotlinx.serialization/-encode-default/)).

기존 nullable JSON 컬럼을 사용하므로 Room schema/DB migration 변경은 없습니다. codec는 새 gate와 prediction 상태 모순을 거부합니다. 모델 없음/실패/OFF는 처리량·latency를 가짜0 측정값으로 만들지 않습니다. History→Report의 기존 DEBUG 상세 mapper가 contract, Shadow Runtime, Primary=LANDMARK 및 저장된 집계를 표시합니다. release에서는 숨깁니다.

## 개인정보·업로드·배포·비용

이번 변경은 프레임을 메모리에서만 처리하고 worker 종료/완료 시 bitmap copy를 해제합니다. 자동 이미지·영상·raw tensor 저장은 없습니다. 사용자가 별도로 누르는 기존 DEBUG 수동 촬영만 별도 private 검증 경로를 사용하며8장 상한/수동 삭제를 유지합니다.

세션 YOLO 집계는 로컬 Room JSON에만 저장합니다. 기존 `SupabaseRepository.uploadSession`/서버 DTO/LLM DTO에 YOLO 진단 필드는 없고 이번에 추가하지 않았습니다. 일반 운동 세션의 기존 업로드 동작 자체는 보존합니다. 검증은 로그인 후 네트워크 OFF 및 “로컬 검증 세션 · 업로드 안 함”을 권장합니다. Supabase 스킬은 이 경계의 읽기 전용 확인에만 사용했고 원격 쿼리/DB migration/deploy/billing/provider 연결은 하지 않습니다.

EXP02 모델·사진·local_validation·venv는 Git 미추적, 모델/검증 입력은 모든 APK에서 제외합니다. 기존 원본 모델/사진 삭제 없음. 새 SDK/API/유료 provider/의존성 추가 없음. 온디바이스 실행은 새 API 호출 과금을 만들지 않지만 CPU·배터리·발열을 사용합니다. 계약 VERIFIED는 데이터/weight 재배포 권리 승인이 아니며 기존 공개/라이선스 보류 정책은 유지합니다.

## 이번 검사와 실기기 상태

최초에는 USB 기기가 없어 device 항목을 NOT_RUN으로 두었으나 이후 사용자가 연결했습니다. 새 DEBUG 앱과 test APK를 데이터 삭제 없이 업데이트 설치했고 canonical private 모델 SHA 및 기존 private RGB fixture 보존을 확인했습니다. **실제 카메라 운동 baseline은 controlled native 검증과 별도**로 기록합니다.

아래 build/JVM/Python 및42개 파일 검사는 앞선 계약 승격 작업의 실행 이력입니다. 후속 사용자 운동의 새 instrumentation3개와 문서 변경 검사는 별도 구분합니다.

| 이번 검사 | 결과 |
|---|---|
| assembleDebug / assembleDebugAndroidTest / assembleRelease | PASS, 최종 코드 offline build1분23초 |
| JVM testDebugUnitTest | fresh532개, failure/error/skip0 |
| Node | fresh95개 PASS |
| Python EXP02 scripts | fresh121개 PASS(26+13+11+23+11+19+18) |
| canonical local model / 기존 actual 증거 재확인 | SHA/크기 및 SQUAT positive2·class1 positive1·NMS9종 PASS |
| DEBUG / release main APK | loose YOLO weight 없음, MediaPipe task 유지 |
| DEBUG / release / test APK | private PNG/reference/raw/tensor 미포함 |
| release DEX | DEBUG capture/native-validation/test 구현 미포함 |
| buildConfig variant gate | DEBUG true / release false 생성 결과 확인 |
| Git / security | 모델·사진·venv ignored/untracked, 변경 public text42개 secret-pattern 검사 PASS, diff whitespace PASS |
| 이번 device controlled runner / private 재시작 | 4tests PASS, 실제 native4회 포함, live camera와 구분 |
| private storage / Room / migration | 13tests PASS(6+2+5) |
| 기존 실제 사용자 행 restart / Report UI | 2tests PASS, canonical 진단 SHA 유지, 읽기 전용 |
| 새 실제 운동 baseline | 실제 카메라 추론 집계·사용자 저장·새 프로세스 재조회·Report 렌더링 PASS, 후속 검증은 아래 참조 |

새 코드의 JVM 검사는 VERIFIED+OFF/ON, NOT_VERIFIED+ON, release 강제OFF, runner 무복사 차단, gate와 진단 호환성, 반대 YOLO 결과에도 Primary/rep/score/report 불변을 포함합니다. Gradle10 deprecation 안내는 기존 비차단 경고이며 새 의존성/버전 변경으로 해결하려 하지 않았습니다.

추가한 `YoloRealtimeShadowDeviceTest`는 기존 private Golden raster를 재사용하는 **controlled 반복 입력** 검사입니다. 새 촬영·실제 사용자 운동이 아니며 named 사용자 DB를 수정하지 않습니다. DEBUG factory/진짜 native runner·집계·in-memory Room/History mapper와 process restart 후 private scalar readback을 구분합니다. OFF/미검증 gate 차단도 실제 모델 load 상태에서 검사합니다.

초기 ON 검사에서는 결과 callback 직후 항상 다음 admission이 성공한다고 가정한 harness가 FAIL했습니다. callback은 worker finally/release보다 앞서며 real scheduler에서는 single-flight 거절이 정상일 수 있습니다. 기존250ms gate와2초 timeout을 변경하지 않고, harness만 bounded admission 재시도로 수정했습니다. 새 run의 실제 native4회/in-memory Room 및 재시작 private scalar 재조회는 PASS했습니다. 이 결과를 live camera 또는 사용자 DB 영속성으로 부르지 않습니다.

최종 controlled repeat4회 실측: raw SQUAT4, stable SQUAT1/UNKNOWN3, match1/mismatch0/unknown3, confidence 평균·최대 약0.91452229. 평균pre183ms/infer128.25ms/post45ms/total356.25ms, processedFPS2.772이며 test thermal은 미측정(null), cameraView는 UNKNOWN입니다. 이는 고정된 private raster 반복 처리 속도이지 CameraX+MediaPipe와 함께 실행한 실제 운동 baseline이 아닙니다.

새 앱으로 기존 실제 사용자 행을 재시작·재조회하고 Report 접힘/펼침/각 metric 표시를 검증했습니다. 신규 gate-null 생략으로 과거 진단 canonical hash도 그대로입니다. 그 후 MAX(id)만 읽는 새 baseline을 만들고 사용자에게 실제 운동·저장을 요청했으며, 후속 검증에서 새 사용자 저장 행과 실제 카메라 집계를 확인했습니다.

기기 연결 시 `shadowEvidenceRun=<새 안전한 ID>`를 지정해 `verifiedDefaultOffLoadsModelWithoutPreprocessingOrNativeRun`, `unverifiedContractCannotRunEvenWithDebugGateOn`, `enabledFactoryRunsNativeAndRoundTripsOnlyAggregates`를 method별로 실행합니다. 마지막 controlled 결과를 만든 후 별도 force-stop/process에서 `readControlledAggregateAfterRestart`를 실행합니다. 입력은 기존 private `yolo/positive-golden/reference.json`의 candidate_02 RGB가 필요하며 없는 경우 임의 생성하지 않습니다. `controlled-shadow.json`/`controlled-readback.json`은 private noBackup `yolo/realtime-shadow/<run>/`에만 배타 생성하며 기존 실행을 덮어쓰지 않습니다. 이 검사를 실제 사용자 DB 재시작 검증으로 표현하지 않습니다.

기존 `YoloSessionPersistenceDeviceTest`의 readBaseline→사용자 실제 저장→captureNewlySavedRecord→force-stop→verifySameRecordAfterRestart→openSelectedReport 절차도 유지합니다. 과거 행 회귀에는 새 `sessionEvidenceRun` 및 기존 `sessionSourceEvidenceRun`을 사용해 원본 증거를 보존합니다. 새 실시간 세션 저장 결과는 사용자의 실제 운동 후에만 보고할 수 있습니다.

### 후속 실제 카메라 세션 검증 (2026-10-17)

사용자가 실제 운동을 마친 뒤 운동 종류와 촬영 방향을 명시했습니다. baseline 이후 새 사용자 저장 행이 정확히 하나 있었고, 저장된 Landmark 세션 종목·촬영 방향이 사용자 답변과 일치했습니다. 진단은 `modelStatus=READY`, `contractStatus=VERIFIED`, `shadowRuntimeEnabled=true`, `predictionEnabled=true`이며 실제 카메라 native 완료 프레임과 유효한 confidence/latency/FPS 집계를 포함했습니다.

- 실제 사용자 저장 행 확인: **PASS**. harness가 운동을 삽입하거나 추론을 대신 실행하지 않았습니다.
- force-stop 후 새 PID의 동일 record/횟수/시간/진단 내용/canonical SHA 재조회: **PASS**.
- 저장된 상세 Report의 초기 접힘·펼침·모든 metric label/value 렌더링: **PASS**.
- 이번에 새로 실행한 instrumentation은 위3개이며 모두 통과했습니다. 이전 build/JVM/Node/Python 이력을 이번 fresh 실행으로 표시하지 않습니다. 앱 소스 변경이 없어 build/JVM은 재실행하지 않았습니다.
- 자동 Report 진입 검증이며 실제 History 행을 사용자가 탭했다는 증거로 확대하지 않습니다(`historyRowTapPerformed=false`).

개인 운동 기록 식별자·횟수·시간·개별 집계 수치·진단 SHA 및 scalar 원본은 기기 private noBackup과 Git ignored `local_validation/exp02_runtime_rollout/`의 **로컬 전용 보고서**에만 보존합니다. 공개 문서에는 개별 baseline 수치를 복사하지 않습니다. 이미지·영상·프레임·전체 Logcat·계정/프로필·자세 분석 원본은 수집/업로드하지 않았습니다.

이번 세션은 실제 카메라 Shadow baseline 및 저장 경로의 근거입니다. raw/stable은 프레임별 관측이며 Landmark 반복 횟수와 다릅니다. UNKNOWN과 세션 확정 전 비교 제외를 유지하며, known-match만으로 전체 모델 accuracy를 주장하지 않습니다. 임계값·안정화·Primary·rep/score/routing/coaching 변경 없이 결과를 기록했습니다.

## 다음 실기기 절차 (최대5)

1. USB 연결/잠금 해제 후 새 DEBUG APK를 **업데이트 설치**합니다(`adb install -r`): 앱 삭제·data clear 금지. private 모델과 실제 운동 기록을 보존합니다.
2. 로그인 후 네트워크를 끄고 운동 준비→YOLO 진단 보기에서 `exportContract=VERIFIED`, `shadow=ENABLED`, `primary=LANDMARK`, `model=READY`를 확인합니다. `MODEL_MISSING`이면 기존 private provisioning 절차를 쓰며 assets에 넣지 않습니다.
3. 촬영 방향을 고르고 “로컬 검증 세션 · 업로드 안 함” 체크 후 실제 운동을 수행합니다. 프레임 저장 버튼은 누를 필요 없습니다. raw/stable/session, FPS/latency/thermal을 구분합니다. 낮은 confidence에서 stable UNKNOWN은 정상일 수 있습니다.
4. 종료·결과 저장 후 History→그 운동→상세 Report→YOLO 진단에서 실제 processedFrames와 집계/confidence/latency를 확인합니다. raw/stable 일치율은 동기화된 정답 accuracy가 아닙니다.
5. 앱을 종료·재실행하고 같은 기록/진단이 유지되는지 확인합니다. 이후 운동·방향별 baseline을 비교하되 threshold/stabilizer tuning은 별도 요청으로 진행합니다.

## 변경 파일 (계약 승격 단계)

전체42개(public source/test/docs)입니다. 기존 모델·사진·과거 증거·사용자 운동 DB/분석 로직은 변경하지 않았습니다. 자동 commit/push는 하지 않습니다.

후속 실제 카메라 baseline 검증에서는 앱 소스 변경 없이 이 문서1개만 공개 변경했습니다. 개별 수치 보고서와 검증 상태 갱신은 ignored 로컬 자료이며 아래42개는 앞선 계약 승격 작업의 이력입니다.

```text
README.md
app/build.gradle.kts
app/src/main/java/com/example/arptapp/data/YoloSessionDiagnosticsCodec.kt
app/src/main/java/com/example/arptapp/domain/detection/DetectorComparison.kt
app/src/main/java/com/example/arptapp/domain/detection/ExerciseDetector.kt
app/src/main/java/com/example/arptapp/domain/detection/Exp02YoloContract.kt
app/src/main/java/com/example/arptapp/domain/detection/YoloExerciseDetector.kt
app/src/main/java/com/example/arptapp/domain/detection/YoloRuntimePolicy.kt
app/src/main/java/com/example/arptapp/domain/detection/YoloSessionDiagnostics.kt
app/src/main/java/com/example/arptapp/domain/detection/YoloShadowRunner.kt
app/src/main/java/com/example/arptapp/presentation/report/YoloSessionDiagnosticsPresentation.kt
app/src/main/java/com/example/arptapp/ui/camera/yolo/TfliteYoloRuntime.kt
app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp02NmsContractTest.kt
app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp02PositiveModelGoldenTest.kt
app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/YoloRealtimeShadowDeviceTest.kt
app/src/test/java/com/example/arptapp/data/YoloSessionDiagnosticsCodecTest.kt
app/src/test/java/com/example/arptapp/domain/detection/YoloContractTest.kt
app/src/test/java/com/example/arptapp/domain/detection/YoloRuntimePolicyTest.kt
app/src/test/java/com/example/arptapp/domain/detection/YoloSessionDiagnosticsBridgeTest.kt
app/src/test/java/com/example/arptapp/domain/detection/YoloSessionDiagnosticsCollectorTest.kt
app/src/test/java/com/example/arptapp/domain/detection/YoloShadowObserverIntegrationTest.kt
app/src/test/java/com/example/arptapp/domain/detection/YoloShadowRunnerTest.kt
app/src/test/java/com/example/arptapp/presentation/report/YoloSessionDiagnosticsPresentationTest.kt
docs/YOLO_DEVICE_VALIDATION.md
docs/ml/EXP02_ANDROID_INFERENCE_CONTRACT.md
docs/ml/EXP02_POSITIVE_VALIDATION.md
docs/ml/EXP02_VERIFIED_REVIEW.md
docs/ml/EXP02_RUNTIME_SHADOW_ROLLOUT.md
scripts/exp02-next-stage.test.mjs
scripts/exp02-positive.test.mjs
scripts/exp02-private-input.test.mjs
scripts/generate-exp02-actual-golden.py
scripts/generate-exp02-nms-reference.py
scripts/generate-exp02-positive-reference.py
scripts/summarize-exp02-positive-comparison.py
scripts/test-exp02-positive-comparison.py
scripts/test-exp02-positive-reference.py
scripts/test-exp02-positive-transfer.py
scripts/transfer-exp02-positive.py
scripts/yolo-session-wiring.test.mjs
scripts/yolo-shadow.test.mjs
scripts/yolo-runtime-gate.test.mjs
```

## 커밋 메시지 제안 (계약 승격 단계)

```text
feat: EXP02 계약 검증과 DEBUG Shadow 실행 게이트 분리

- actual Golden 근거와 승인에 따라 contract VERIFIED 승격
- DEBUG Shadow 명시적 ON 및 release 강제 OFF, Landmark Primary 유지
- 로컬 세션 gate 상태와 과거 JSON 호환성 및 리포트 진단 보강
- gate/Primary 안전 회귀와 실기기 controlled runner 검사 준비
- 실시간 baseline 미실행 및 모델 정확도 한계를 문서화
```

후속 실제 카메라 baseline 검증의 커밋 제안(공개 문서만 변경):

```text
docs: 실제 카메라 Shadow 세션과 저장 회귀 검증 결과 기록

- 사용자 저장 기록·재시작·상세 리포트 검증 PASS 반영
- 개별 운동 수치와 진단 원본은 Git 제외 로컬 보고서에만 보존
- 프레임 집계와 반복 횟수·모델 정확도의 차이 명시
```
