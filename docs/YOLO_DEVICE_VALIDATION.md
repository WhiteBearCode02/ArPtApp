# YOLO Android Shadow Mode · 구조 검증

기준일: 2026-10-16. 상태: **local development / research validation**.

> Android Shadow Mode 기반 구조는 준비되었으나 EXP02 export contract 미확인으로 실제 prediction은 비활성입니다.

이 문서는 YOLO accuracy 또는 모바일 inference 성공 보고가 아닙니다. export/test 원본 Colab·Python 코드는 현재 환경과 저장소에 없으며, 사용자의 후속 지시에 따라 전처리·후처리 구현을 하지 않았습니다. 메타데이터/공식 라이브러리 소스 참고만으로 원본 실험 계약이 확인되었다고 간주하지 않습니다.

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
- `YoloExerciseDetector`: 모델 존재→native 생성→입출력 개수·shape·dtype 검증→export contract gate 순서. mismatch·native init 실패·run 예외 등은 YOLO 상태만 전환합니다.
- `YoloPreprocessor`, `YoloOutputDecoder`, `YoloPostProcessor`: **인터페이스만 제공**. 현재 구현은 모두 `ExportContractNotVerified`로 fail closed합니다. `TODO(EXP02_EXPORT_CONTRACT)`를 표시합니다.
- `DebugYoloShadowFactory`: DEBUG에서만 생성하고 항상 `UnverifiedYoloContract`를 주입합니다. flag/metadata/model 존재만으로 VERIFIED로 바뀌지 않습니다.
- `YoloShadowRunner`: CameraX/MediaPipe와 별도 native owner worker, 단일 진행·무대기열·250ms admission 정책(최대 약 4 FPS 구조). 현재는 READY가 아니므로 bitmap 복사·preprocess·run이 **실행되지 않습니다**.
- `YoloExerciseStabilizer`: 향후 검증된 canonical 결과에 적용할 최근 5회 중 4회 합의 구조. window/age는 `ENGINEERING_INITIAL`; 모델 confidence 임계값은 **null/NOT_VERIFIED**입니다. 테스트용 숫자는 EXP02 운영 임계값이 아닙니다.
- `DetectorComparison`: raw/stable YOLO, fallback, 고정 session, cameraView, 지연 구간, thermal의 scalar 비교. 다인 입력은 `MULTI_PERSON_UNASSOCIATED` 표시. bbox-to-pose 동일인 매칭은 구현하지 않았습니다.

비교는 비동기 최신 상태이며 같은 프레임/같은 사람에 대한 동기화된 평가가 아닙니다. 다인 경고는 전달된 pose/detection 개수에만 의존하므로 화면의 모든 사람을 검출했다고 보장하지 않습니다. 기본 Pose Landmarker 설정/가림 등으로 여러 사람이 있어도 한 포즈만 반환될 수 있습니다.

현재 YOLO 상태에서 실제 sessionExerciseType으로 이어지는 경로는 없습니다. fallback만 `MainViewModel.observeDetectedExercise`에 전달되며, 세션 종목·횟수·코칭·기존 저장/업로드·이전 기록 읽기 계약은 그대로 유지합니다.

## 명시적으로 미구현 / 미확인

모두 `NOT_VERIFIED / TODO(EXP02_EXPORT_CONTRACT)`입니다.

- RGB/BGR, normalization 범위, 채널 배치에 대한 실제 픽셀 변환.
- resize 보간법, letterbox 여부·padding 값·역변환.
- `[1,6,8400]` 각 채널 의미, xywh/xyxy, 좌표 단위.
- confidence/class score 계산·결합 및 threshold.
- 모델 내부 NMS 여부, Android NMS 구현/활성화.
- 실제 FP32 prediction, golden 이미지/tensor inference 비교, 모바일 정확도·latency·발열 실측.

`DetectionBox`는 **미래 decoder가 제공할 canonical 원본 프레임 pixel box의 API 정의**일 뿐 모델 raw box 형식에 대한 가정이 아닙니다. largest-area 선택기는 synthetic canonical 결과에만 테스트했으며 실제 모델 출력에 적용한 것이 아닙니다.

## 모델 없는 APK에서의 예상 동작

1. 후면 카메라, MediaPipe 스켈레톤, landmark primary는 종전과 같이 동작합니다.
2. DEBUG 진단에 `MODEL_MISSING`, `exportContract=NOT_VERIFIED`, raw/stable UNKNOWN, latency `미측정`이 나오는 것이 정상입니다.
3. 모델이 안전한 로컬 개발 경로에 제공되어 tensor 검사가 통과해도 `CONTRACT_NOT_VERIFIED`로 중단합니다. 원본 픽셀 변환/inference는 하지 않습니다.
4. release에서는 YOLO runner를 생성하지 않고 진단 UI/로그도 숨깁니다. release 실행·서명 배포는 이번 검증 범위가 아닙니다.

원본 `assets` 모델을 복사·삭제·이동하거나 APK에 포함하지 않았습니다. 따라서 이 APK를 설치하는 것만으로 로컬 PC 모델이 휴대폰에 전달되지는 않습니다.
개발용 resolver는 앱의 `noBackupFilesDir/yolo/airptcoach_yolo26n_exp02_fp32.tflite`를 우선 검사한 뒤 asset을 확인합니다. 앱은 모델을 다운로드하거나 자동 복사하지 않습니다. 기기 모델 provisioning은 이번에 수행하지 않았으며, 원본 계약 확보 후 개발자가 로컬 debug 범위에서 별도로 진행해야 합니다. 이를 위해 public APK 정책을 해제하지 마세요.

## 동시성·실패·종료

- initialization deadline 5초, future inference deadline 2초는 측정값이 아닌 초기 운영 안전장치입니다.
- timeout은 새 제출을 막고 늦은 결과를 버립니다. native run 중 다른 thread에서 Interpreter를 강제로 닫지 않습니다. 종료/close는 해당 owner worker에서 직렬 처리합니다.
- native 호출이 영구 정지하면 강제 중단을 보장할 수 없습니다. UI/MediaPipe가 그 worker를 기다리지는 않으며 병렬 재시도·추가 worker를 만들지 않습니다.
- 모델은 tensor 검증 후 계약 미확인이면 즉시 닫습니다. 미확인 상태에서 매 프레임 재초기화하지 않습니다.
- 카메라 전환 시 과거 shadow evidence를 폐기하고, 화면 종료 후 콜백을 막습니다. MediaPipe 원본 bitmap을 YOLO가 recycle하지 않습니다. 향후 READY일 때만 single-flight 승인 뒤 소유 복사본 하나를 사용합니다.

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
.\gradlew.bat assembleDebug testDebugUnitTest --offline --no-daemon --no-problems-report -PverificationBuildDir=yolo-shadow-verification --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8'
node --experimental-strip-types --test scripts/*.test.mjs supabase/functions/generate-workout-report/*.test.ts supabase/functions/youtube-ranking/*.test.ts
node scripts/verify-coaching-security.mjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-public-apk.ps1 -ApkPath build/yolo-shadow-verification/outputs/apk/debug/app-debug.apk
git diff --check
```

JVM는 fake runtime/contract·synthetic canonical detection으로 메타데이터 mismatch, 미확인 계약 0회 추론, 예외 격리, throttling/single-flight, timeout/late result/close, 안정화·세션 독립을 검사합니다. VERIFIED 테스트 더블은 실제 모델 계약의 검증 근거가 아닙니다. 실제 resize/letterbox/NMS 테스트는 구현하지 않은 기능이므로 통과했다고 주장하지 않습니다.

### 이번 로컬 실행 결과 (2026-10-16)

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

새 외부 API·키·SDK·cloud inference·frame/image upload·관절 좌표 저장을 추가하지 않았습니다. 진단은 DEBUG에서 scalar/enum만 사용하고 DB/리포트/서버 payload에 넣지 않습니다. LLM false/Unavailable, 기존 backup 금지, 모델 Git ignore·APK 제외 정책을 유지합니다. 기존 다른 앱 기능의 서버/계정 요금제에 대한 무과금 보장은 이 코드 변경 범위가 아닙니다.

**공개 재배포 권리 미확정**, public release ready 아님. 원격 DB migration/Edge Function 배포/billing 변경/자동 commit/push를 하지 않습니다.

## 나중에 필요한 자료와 다음 작업

1. EXP02 원본 export/test 코드, 정확한 옵션·버전·전처리·출력 해석·threshold·NMS 계약 확보.
2. `YoloPreprocessor` / `YoloOutputDecoder` / `YoloPostProcessor`만 검증된 구현으로 교체하고 synthetic 및 권리 확인 golden fixture를 비교. 이때만 verification·confidence 정책을 명시적으로 변경.
3. 실제 Android runtime init/inference와 FRONT/45°/SIDE 비교, YOLO vs fallback, latency·thermal 측정.
4. confidence/stabilization 조정 후 YOLO primary 전환 여부를 별도 승인/판단. 현재는 절대 primary로 사용하지 않음.
5. 필요할 때 FP16/INT8 최적화를 별도 수행. 현재 FP32 모델 파일은 그대로 유지.

## 변경 파일 목록

- `app/src/main/java/com/example/arptapp/domain/detection/`: `ExerciseDetector.kt`, `YoloExportContract.kt`, `YoloExerciseDetector.kt`, `YoloExerciseStabilizer.kt`, `YoloShadowRunner.kt`, `DetectorComparison.kt` (신규).
- `app/src/main/java/com/example/arptapp/ui/camera/`: `LandmarkExerciseDetector.kt`, `yolo/TfliteYoloRuntime.kt` (신규), `PoseCameraController.kt` (수정).
- `app/src/main/java/com/example/arptapp/DashboardActivity.kt` (수정).
- `app/src/main/res/layout/activity_dashboard.xml`, `app/src/main/res/layout-land/activity_dashboard.xml` (수정).
- `app/src/test/java/com/example/arptapp/domain/detection/`: `YoloContractTest.kt`, `YoloStabilizerTest.kt`, `YoloShadowRunnerTest.kt` (신규).
- `scripts/yolo-shadow.test.mjs` (신규), `scripts/pose-stability.test.mjs` (adapter 배선에 맞춰 수정).
- `README.md`, `MODEL_CARD.md`, `docs/ML_EXPERIMENTS.md`, `RELEASE_READINESS.md` (수정), 이 문서 (신규).

`app/build.gradle.kts`, `.gitignore`, Room schema/entity, Supabase 소스·SQL, LLM provider, 원본 모델 파일은 변경하지 않았습니다.
