# EXP02 FP32 Android Shadow 실기기 검증

## 현재 상태와 검증 경계

- **Production export contract: `NOT_VERIFIED` 유지.** 모델 주입/파일 존재/설정값만으로 승격하지 않습니다.
- Android Shadow 구조, actual-model Golden 생성·비교 도구, DEBUG 로컬 진단 저장 경로를 준비했습니다.
- 2026-10-17 사용자 Android DEBUG 기기에 모델을 주입했습니다. Interpreter/metadata 확인과 native inference4회가 성공했고 Python/Android 모두 무검출로 **negative-path equivalence PASS**입니다. positive bbox/confidence/NMS는 미검증이므로 전체 contract는 **NOT_VERIFIED**입니다. 최신 증거는 아래12절이며 이전 미연결 기록과 구분합니다.
- 기존 `LandmarkExerciseDetector`가 Primary이며 YOLO는 비교 전용입니다. 회차·점수·칼로리·운동 종류 lock에 YOLO 진단을 사용하지 않습니다.
- 모델·실제 검증 이미지·RGB raster·실제 reference/result JSON·venv는 Git에 올리지 않습니다. 승인된 공식 패키지 설치 외 유료 API·서버 업로드·GPU/양자화 최적화는 하지 않습니다.

양성 후보의 DEBUG 수동 촬영, private 전송, 실제 Python/native 비교 및 사용자 운동의 저장/재시작 검증은 [EXP02_POSITIVE_VALIDATION.md](EXP02_POSITIVE_VALIDATION.md)를 따릅니다. 도구 구현/합성 저장 테스트 통과는 실제 positive 또는 사용자 세션 통과를 의미하지 않습니다.

## 1. 모델과 DEBUG APK 확인

정해진 모델만 사용합니다.

| 항목 | 값 |
|---|---|
| modelId | `yolo26n_exp02_fp32` |
| 파일 | `airptcoach_yolo26n_exp02_fp32.tflite` |
| 크기 | `9,894,030 bytes` |
| SHA256 | `464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749` |
| input | FLOAT32 `[1,3,640,640]` |
| output | FLOAT32 `[1,6,8400]` |
| class | `0=SQUAT`, `1=SHOULDER_PRESS` |

로컬 보관 파일은 APK assets로 배포하지 않습니다. 기존 로컬 asset 폴더에 파일이 남아 있어도 Gradle APK exclusion과 `scripts/verify-public-apk.ps1` 검사가 별도로 필요합니다. release/debug 양쪽 정책을 유지합니다. 기기 최종 경로만 다음과 같습니다.

```text
<context.noBackupFilesDir>/yolo/airptcoach_yolo26n_exp02_fp32.tflite
# 표준 기기 예시: /data/user/0/com.example.arptapp/no_backup/yolo/...
```

DEBUG APK를 사용자 승인으로 설치하고 USB debugging을 허용합니다. release는 `run-as`를 허용하지 않아야 하며 이 절차는 release를 root로 우회하지 않습니다. 앱 업데이트에는 private 파일이 유지될 수 있지만 앱 삭제/데이터 초기화 시 없어집니다. 기기 백업 대상으로 사용하지 않습니다.

## 2. Windows에서 binary-safe 모델 주입

프로젝트 루트 PowerShell에서 실행합니다. Android SDK platform-tools의 `adb.exe`가 PATH에 없다면 `-AdbPath 'C:\...\platform-tools\adb.exe'`를 명시합니다. serial은 `adb devices`에서 확인한 **본인 검증 기기**를 선택합니다.

```powershell
adb devices
powershell -NoProfile -File scripts/provision-exp02-model.ps1 `
  -DeviceSerial YOUR_DEVICE_SERIAL `
  -ModelPath 'C:\local-models\airptcoach_yolo26n_exp02_fp32.tflite' `
  -WhatIf

# 위 확인 후 -WhatIf를 제거하면 실제 private 저장소에 주입합니다.
powershell -NoProfile -File scripts/provision-exp02-model.ps1 `
  -DeviceSerial YOUR_DEVICE_SERIAL `
  -ModelPath 'C:\local-models\airptcoach_yolo26n_exp02_fp32.tflite'
```

도구는 다음을 수행합니다.

운동·기록 저장이 진행 중이지 않을 때 실행하세요. 실제 주입은 선택한 DEBUG 앱을 종료하며, 앱을 자동 재시작하지 않습니다.

1. 로컬 filename/size/SHA256 검증 및 online 기기·DEBUG `run-as` 확인.
2. 실제 주입 승인 후 선택한 DEBUG 앱만 force-stop하여 활성 Interpreter와 파일 교체가 충돌하지 않게 합니다. 문자 파이프 대신 `ProcessStartInfo`/`FileStream`으로 raw `adb exec-in run-as ... dd`에 전달합니다. `iflag=count_bytes count=<검증한 크기> bs=65536 conv=fsync`로 정확한 길이만 읽습니다. 무제한 dd는 마지막142바이트 누락, Windows shell stdin은 Ctrl-Z 잘림이 실측되어 사용하지 않습니다.
3. 앱 private `no_backup/yolo/`의 임시 업로드 파일을 `chmod 600`으로 제한하고 size/SHA256을 재검증합니다. 각 ADB 프로세스는 binary copy를 포함한 전체 120초 deadline을 사용합니다.
4. 검증된 파일만 atomic rename, 최종 SHA256 재확인. 실패 시 도구가 만든 정확한 임시 이름만 정리합니다. 모델 원본은 삭제하지 않습니다.

`/sdcard`, 공유 Download, public staging, 앱 assets fallback은 사용하지 않습니다. 임의 Preferences/환경변수로 `VERIFIED`를 설정하는 옵션도 없습니다. `-WhatIf`는 remote 읽기 preflight는 할 수 있지만 파일을 쓰지 않습니다.

주입 후에도 contract 미검증이면 Dashboard는 `CONTRACT_NOT_VERIFIED` 또는 runtime 비호환 상태일 수 있습니다. **READY가 되도록 gate를 강제로 고치면 안 됩니다.**

## 3. 실제 이미지와 Python reference 준비

최소 **스쿼트 정면/측면, 숄더 프레스 정면/측면** 각각 한 장이 필요합니다. 직접 제작·동의 받은 기존 검증 이미지 또는 권리가 확인된 프로젝트 이미지로만 구성합니다. AIHub 학습 이력만으로 이미지/weight의 재배포 권리를 추정하지 않습니다. 얼굴 포함 자료와 권리 근거·개인정보는 로컬에만 보관합니다.

1. [manifest template](exp02-actual-golden.manifest.example.json)을 ignored `local_validation/exp02_golden/manifest.json`에 복사합니다. 기존 `local-yolo-validation/`도 Git 제외 대상이지만 새 사용자 입력은 `local_validation/`에 유지합니다.
2. 해당 폴더에 기존 이미지를 두고 `image`, `rightsCleared`, `rightsBasis`를 실제 근거로 작성합니다. template의 `rightsCleared=false` 상태로는 실행되지 않습니다.
3. 이미 구축된 Python export/test 환경을 사용합니다. 필요한 runtime과 NumPy/OpenCV/CPU Torch가 없으면 **차단 사실을 보고**하며 이 도구는 `pip install`, 패키지 변경, 모델/이미지 다운로드를 하지 않습니다.
4. 기존 `build/yolo-contract-reference/`의 exact Ultralytics **8.4.176 wheel + extracted source**를 사용합니다. wheel SHA 및 원문 파일 byte가 일치해야 합니다. 합성 도구의 original-AST loader를 재사용하며 Ultralytics 원문을 저장소에 vendoring하지 않습니다.

```powershell
& '.\local_validation\.venv\Scripts\python.exe' scripts/generate-exp02-actual-golden.py `
  --manifest local_validation/exp02_golden/manifest.json `
  --model app/src/main/assets/airptcoach_yolo26n_exp02_fp32.tflite `
  --output local_validation/exp02_golden `
  --reference-filename reference_results.json
```

기존 `build/yolo-contract-env`에는 runtime이 없지만 사용자 승인으로 별도 `local_validation/.venv`에 공식 LiteRT 2.2.0을 설치해 실제 generation을 완료했습니다. 새 환경의 재현 절차와 검증 한계는 [로컬 환경 문서](EXP02_LOCAL_GOLDEN_ENVIRONMENT.md)를 참고합니다.

실행 시 real native `ai_edge_litert.Interpreter`로 tensor dtype/shape를 확인하고 exact-source `BasePredictor.preprocess` → `LiteRTBackend.forward` → class-aware NMS → `scale_boxes`를 실행합니다. synthetic interpreter를 actual reference로 사용하지 않습니다.

출력은 기본 ignored `build/exp02-actual-golden/` 또는 명시한 `local_validation/exp02_golden/` 안에만 생성됩니다. 실제 Git ignored·untracked 여부와 symlink/junction 이탈을 검사합니다.

```text
reference.json    # model/wheel/source provenance, tensors, thresholds, final predictions
<sample-id>.rgb  # 같은 decoded RGB raster를 Python/Android 양쪽에 제공
<sample-id>.input.f32 # Python normalized FLOAT32 BCHW, little-endian, 전처리 오차 비교
```

원본 JPEG/EXIF decoder 차이를 resize 차이로 오판하지 않도록 양쪽은 동일 hash의 RGB raster를 사용합니다. Python과 Kotlin 입력 tensor hash 및 실제 전처리 오차를 별도로 기록하며 bitwise 동일성을 강제하지 않습니다. 네 가지 입력 조건은 필수지만, 모델 오분류·검출0개도 실제 결과로 보존합니다. 파일명 expected label은 semantic sanity에만 사용하고 runtime equivalence를 통과시키는 근거로 쓰지 않습니다. 런타임 provenance에는 로컬 Python 절대경로가 포함될 수 있으므로 결과 JSON도 Git 제외·로컬 전용입니다. 권리 근거 원문·사용자 계정·영상·landmark 배열은 출력하지 않습니다. 실제 raster·입력 tensor·bbox 결과는 private 검증 bundle에만 존재하며 **세션 진단 DB에는 저장하지 않습니다.**

45도는 기존 enum이 아니므로 이 manifest에 임의 `ANGLE_45`를 추가하지 마세요. 필요 시 별도의 연구 기록으로 보관합니다.

## 4. Android actual-model Golden 실행

```powershell
powershell -NoProfile -File scripts/provision-exp02-model.ps1 `
  -DeviceSerial YOUR_DEVICE_SERIAL `
  -ModelPath 'C:\local-models\airptcoach_yolo26n_exp02_fp32.tflite' `
  -GoldenBundleDirectory build/exp02-actual-golden

# 검증하려는 기기가 하나만 연결된 상태에서 선택한 serial을 명시합니다.
$env:ANDROID_SERIAL = 'YOUR_DEVICE_SERIAL'
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.example.arptapp.ui.camera.yolo.Exp02ActualModelGoldenTest'
```

`Exp02ActualModelGoldenTest`는 **androidTest 전용**입니다. production gate를 바꾸지 않고 검증 대상 components를 직접 호출하여 검증 전 native inference 증거를 수집합니다. 앱/배포 APK에는 이 instrumentation 클래스가 포함되지 않습니다. 모델/fixture 부족 또는 runtime 미지원은 PASS나 skip으로 처리하지 않고 실패합니다.

시험 결과는 앱의 `no_backup/yolo/actual-golden/android-result.json`에만 남습니다. 모델 load 및 canonical tensor 확인 → 동일 RGB로 Kotlin preprocessing → native interpreter → decoder/NMS → Python final detection과 비교합니다.

| 비교 | 초기 허용 기준 |
|---|---|
| 클래스 | 정확히 동일 |
| post-NMS detection 수 | 오차 **0** |
| confidence | 절댓값 차이 ≤ **0.001** |
| bbox | 원본 이미지 좌표의 각 x1/y1/x2/y2 차이 ≤ **2px** |
| 순서 | same-class 일대일 matching과 **출력 순서 비교**. 양쪽 모두 confidence가 정확히 동점인 구간만 재정렬 허용; 그 외 순서 차이 실패 |
| IoU | 일치 쌍의 실측 IoU를 증거로 출력. 넓은 IoU threshold로 좌표 기준을 우회하지 않음 |

이 값은 **PROVISIONAL_NOT_MEASURED**인 보수적인 초기 공학 기준이며 실측 분포를 이미 검증한 결과가 아닙니다. 불합격을 통과시키기 위해 자동 확대하지 않습니다. INTER_LINEAR와 독립 bilinear의 byte 차이가 실제 class/confidence/bbox/NMS에 미치는 영향을 이 결과로 판단합니다. 실측 maximum error·IoU·샘플별 판정을 검토한 뒤 근거를 남깁니다.

## 5. contract 승격 조건

다음 모두에 실제 증거가 있어야 별도 코드 리뷰로 `Exp02YoloContract.verification`을 변경할 수 있습니다.

- canonical 모델 identity, input/output tensor 확인
- RGB/BCHW/FLOAT32 `/255`, LetterBox, output decoding, confidence, class-aware NMS
- 네 가지 실제 이미지의 runtime-equivalence Golden 통과와 resize 최종 결과 동등성 검토. 모델 오분류·검출0개도 실제 reference로 허용하며 사진별 semantic sanity와 구분
- Android runtime 모델 load 및 native inference 1회 이상 성공

이 문서나 provisioning/테스트 결과 파일이 gate를 자동 승격하지 않습니다. 모델 hash만 맞거나 합성 Golden만 통과한 상태로는 `VERIFIED`가 아닙니다. 모델이 현 TF Lite runtime과 호환되지 않으면 `UNSUPPORTED_RUNTIME`으로 남겨 원인을 보고하고 별도 승인 없이 runtime을 업그레이드하지 않습니다.

## 6. 운동 중·종료 후 실기기 테스트

contract 승격 전에도 MODEL_MISSING/NOT_VERIFIED 세션 저장 경로를 먼저 검사할 수 있습니다. prediction이 없어야 정상인 상태에서 가짜 raw/stable 값을 만들지 않습니다.

1. DEBUG 앱 로그인 후 촬영 방향을 명시적으로 선택합니다.
2. 하단 **YOLO 진단 보기**를 펼칩니다. model/contract, raw confidence, stable, fallback, session, view, FPS, pre/infer/post/total, thermal을 확인합니다.
3. 다음 조건 각각 **최소 5회** 운동합니다: SQUAT FRONT, SQUAT SIDE, SHOULDER_PRESS FRONT, SHOULDER_PRESS SIDE. 현재 초기 threshold, stabilizer, 최소 약250ms/single-flight/no-backlog는 바꾸지 않습니다.
4. 기존 랜드마크가 Primary로 session을 lock하는지 확인합니다. YOLO가 다르게 판단해도 점수·운동 종류·횟수가 YOLO로 변경되어서는 안 됩니다.
5. 운동 종료 → 기록 저장 → 운동 기록 목록의 작은 **YOLO Shadow 기록 있음** 표시를 확인합니다.
6. 해당 상세 리포트 하단의 독립 **YOLO Shadow 진단 보기**를 펼칩니다. 자세 피드백 카드와 혼합되지 않아야 합니다. 기본 접힘이며 DEBUG에서만 노출되어야 합니다.
7. 저장된 상태·모델 hash·세션 운동·방향·frame counts·confidence/latency/FPS/thermal을 확인합니다. UNKNOWN session 시점에는 match/mismatch가 집계되지 않아야 합니다.
8. known-match의 분모는 `matching/(matching+mismatching)`입니다. UNKNOWN은 별도로 표시하고 강제 오답 처리하지 않습니다. detection 없는 frame은 confidence=0으로 평균내지 않습니다.
9. 앱 재시작 뒤 같은 기록을 다시 열어 누적 진단이 유지되는지 확인합니다. 이전 기록에 null diagnostics가 있어도 crash하면 안 됩니다.
10. 모델 미주입 상태의 별도 테스트 설치에서는 `MODEL_MISSING`, processed=0 기록도 확인합니다. 테스트를 위해 기존 모델 파일을 삭제하지 않습니다. release APK에서는 내부 진단 UI와 모델이 없어야 합니다.

## 7. 통과/실패 및 로그

실제 inference 활성 상태가 기대되는 것은 **모든 근거 확인 후 코드 gate가 VERIFIED가 된 경우만**입니다. 그 전에는 NOT_VERIFIED/prediction 비활성이 성공적인 안전 동작입니다.

```powershell
# 진단만 선택적으로 표시합니다. 전체 logcat에는 타 서비스 개인정보가 있을 수 있습니다.
adb -s YOUR_DEVICE_SERIAL logcat -d -s YoloShadow:D '*:S'

# private native Golden 결과를 콘솔에서 읽습니다. 모델/이미지 binary를 text redirect 하지 않습니다.
adb -s YOUR_DEVICE_SERIAL exec-out run-as com.example.arptapp cat no_backup/yolo/actual-golden/android-result.json
```

실제 로그/리포트/테스트 결과는 ignored `build/` 또는 로컬 검증 폴더에서만 관리합니다. 외부 공유 전 계정·경로·기기 식별자 등 개인정보를 검토합니다. 원본 카메라 영상/landmark 전체 배열/세션별 frame bbox 로그를 Room에 넣거나 서버로 전송하지 않습니다.

**실패 기준:** 모델/hash/tensor 불일치, runtime 미지원, 필수 샘플 누락, Golden tolerance 초과, Primary 변경, session score 오염, 데이터 삭제 migration, release 내부 진단 노출, APK 모델 포함. 하나라도 발생하면 prediction 비활성을 유지하고 구체적 실패 항목을 기록합니다.

## 자동 검증과 실기기 증거 구분

```powershell
& '.\build\yolo-contract-env\Scripts\python.exe' scripts/test-exp02-actual-golden.py
.\gradlew.bat assembleDebug testDebugUnitTest
# 기기 없이 instrumentation 코드 컴파일만 수행하는 선택 검증:
.\gradlew.bat assembleDebugAndroidTest
```

pure Python/JVM/Node, APK build, instrumentation **컴파일**은 실제 Golden 실행이나 실기기 correctness 증거를 대체하지 않습니다. 현재 실제 실기기 결과는 미측정 상태입니다.

## 8. 세션 진단 저장 구조와 정의

```text
CameraX → 기존 MediaPipe 제출 throttle → Shadow trySubmit
 → seen/admitted + admission token/primary lock/thermal 스냅샷
 → 단일 Shadow worker → timed result
 → YoloSessionDiagnosticsBridge → synchronized Collector
 → 운동 종료 시 immutable 집계 freeze
 → 별도 bounded JSON extra → WorkoutReportData → Result 기존 저장
 → Room exercise_records.yoloDiagnosticsJson → History → DEBUG Report 하단
```

- `totalFramesSeen`은 Shadow 입력 후보 수이며 원본 CameraX 전체 프레임 수가 아닙니다.
- `admittedFrames`는 gate 허용 수이고, `processedFrames`는 timing이 있는 성공 native 완료 수입니다.
  초기화·무효화·timeout·실패 알림은 처리 프레임으로 세지 않습니다.
- `averageProcessedFps = processedFrames / 활성 monotonic 세션 시간(초)`입니다.
  기존 MediaPipe FPS와 구분해 Dashboard에 `YOLO processedFPS`로 표시합니다.
- confidence 평균·최소·최대는 선택된 실제 detection 표본만 사용합니다. 무검출을 0으로 넣지 않습니다.
- comparison은 **admission 당시** primary session이 확정된 표본만 사용합니다. 나중에 잠긴 종목으로 소급 비교하지 않습니다.
- 이전 세션의 늦은 READY 결과는 버립니다. 공유 runner를 끄는 전역 timeout/오류는 현재 세션에도
  availability 변경으로만 전달하고 과거 프레임 count/confidence/매칭은 합산하지 않습니다.
- thermal은 관찰한 유효 상태의 최대값이며, 미지원·미측정은 null입니다.
- `modelSha256`는 설정된 canonical **기준 hash**입니다. MODEL_MISSING에서도 있는 값이며 실제 파일 로드의 증명이 아닙니다.
- codec은 16,384자 한도, 허용 필드·enum·모델 식별자·유한 수치·count 합계를 검증합니다.
- Room 5→6은 nullable TEXT column 하나를 추가합니다. 기존 3→4→5 경로는 유지하며
  destructive fallback은 제거했습니다. 알 수 없는 v1/v2 schema는 데이터를 지우는 대신 실패합니다.
- 기존 기록은 null 진단으로 정상 처리합니다. 과거 model ID/hash를 향후 변경할 때도
  기존 EXP02 기록을 읽을 수 있도록 codec의 승인된 identity 목록을 유지·확장해야 합니다.
- 기존 유효 운동 결과의 확인 → Result 저장 경로에서만 기록합니다. 회차 분석 자체가 부족해 기존 앱이
  결과 저장을 거부하는 세션이나 확인 창을 취소한 세션을 새 운동 기록으로 강제 생성하지 않습니다.
- 진단은 별도 로컬 DTO입니다. `StructuredWorkoutAnalysis`, 점수, 횟수, 칼로리, Supabase/LLM payload에 추가하지 않습니다.
- 실제 검증용 static RGB raster는 androidTest fixture 전용 private 경로입니다. 카메라 세션 프레임을 수집하는 기능이 아닙니다.

## 9. 이전 세션 진단 구현 단계의 로컬 검증 결과

| 검증 | 결과 |
|---|---|
| Gradle `assembleDebug testDebugUnitTest assembleDebugAndroidTest assembleRelease` | **BUILD SUCCESSFUL, 2분45초**; 136 tasks (35 executed / 101 up-to-date) |
| JVM | 53 suites / **494 tests** / 실패0 / 오류0 / 건너뜀0 |
| 기존 JVM 회귀 | 작업 전417개 testcase 이름 대조: 누락0, 신규77 |
| Node | **111/111** 통과 (기존92 + 신규19), 실패·건너뜀0 |
| Python actual-Golden 도구 guard | **10/10** 통과. 실제 모델 inference 테스트가 아님 |
| SQLite local migration SQL | **3/3** 통과. 현재 Kotlin의5→6 SQL을 메모리 SQLite에 실행해 기존 행·JSON·null보존 확인 |
| Android instrumentation | 테스트 APK 컴파일·생성 성공. Room migration5개 및 actual native Golden1개 **기기 실행 미실시** |
| PowerShell provisioning | AST syntax error0. 실제 전송·기기 파일 변경 미실시 |
| debug/release APK | 각각 public APK 모델 검사PASS, 미확인 weights0, 필수 MediaPipe task 유지 |
| instrumentation APK | ZIP 직접검사: 미확인 weights0. 앱 MediaPipe assets가 없는 별도 test APK인 것이 정상 |
| 보안·diff | 변경 공개 텍스트39개 scoped secret-pattern 통과, APK키 publishable분류 확인(값 미출력), diffcheckPASS |
| actual-model Golden / interpolation prediction 동등성 | **BLOCKED / NOT_EXECUTED** — 권리확인4종 이미지·Python LiteRT runtime·연결기기 없음 |
| contract / primary | **NOT_VERIFIED 유지 / Landmark Primary 유지** |

최초 통합 실행은 새 표시 테스트3개가 중복 Prediction row label 때문에 실패했습니다. 활성 상태와 무검출 표본 라벨을 분리해 해결했습니다. 추가 리뷰에서 이전 세션 timeout이 새 세션의 전역 모델 상태에 전파되지 않는 문제를 발견해 수정하고 회귀 테스트를 추가했습니다. 최종494개는 이 수정을 포함한 결과입니다. 기존 테스트를 삭제하거나 완화하지 않았습니다.

ADB 읽기 조회: online0 / offline0 / unauthorized0. 실제 모델이 PC에 존재한다는 사실은 기기 로드 성공이 아닙니다. 실제 입력 이미지·네이티브 결과를 만들어 내거나 합성 Golden을 실제 Golden으로 승격하지 않았습니다.

이전 script는 앱 전용 APK에서 MediaPipe 존재도 함께 검사하므로, 별도 instrumentation APK에 적용하면 MediaPipe 부재 오류가 납니다. 테스트 APK는 별도의 ZIP 검사로 모델 제외만 검증했습니다. 이것은 앱 APK 검사 실패가 아닙니다.

최종 실행 명령 (프로세스 한정 캐시 설정, 전역 변경 없음):

```powershell
$env:GRADLE_USER_HOME = 'C:\Users\dlgus\.gradle'
$env:ANDROID_USER_HOME = 'C:\Users\dlgus\.android'
.\gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest assembleRelease --no-daemon --no-problems-report -PverificationBuildDir=dashboard-ui-verification
node --experimental-strip-types --test scripts/*.test.mjs supabase/functions/generate-workout-report/*.test.ts supabase/functions/youtube-ranking/*.test.ts
& 'build/yolo-contract-env/Scripts/python.exe' scripts/test-exp02-actual-golden.py
& 'build/yolo-contract-env/Scripts/python.exe' scripts/test-yolo-local-migration.py
node scripts/verify-coaching-security.mjs
```

산출물 (release는 서명·배포하지 않은 패키징 검사본):

| 파일 (`build/dashboard-ui-verification/outputs/apk/` 기준) | bytes | SHA256 |
|---|---:|---|
| `debug/app-debug.apk` | 37,671,613 | `7DD2B490E11767B20557A9C12FC659342018554AA62E0A40D4B063F3D43377DD` |
| `release/app-release-unsigned.apk` | 32,377,067 | `172B65D2395BDC0A09D6D46DBB82AF827ED2700E3F0F716D227941FD95176DD3` |
| `androidTest/debug/app-debug-androidTest.apk` | 629,083 | `087AF5565FE3D45B29BA97507F056C255BB5F252BC30A32E4E3FDB247BED40BA` |

기존 Gradle deprecation·TF namespace·native symbol strip 안내는 유지됩니다. 의존성/SDK/provider/billing/원격 DB/Edge Function/양자화/GPU/Primary/임계값은 변경하지 않았습니다. 전체 저장소·Git 이력의 긴 라이선스 스캔, 모델 삭제, 자동 commit/push를 수행하지 않았습니다.

## 10. 이전 세션 진단 구현 단계의 변경 파일 (40개)

- `.gitignore`
- `app/src/androidTest/java/com/example/arptapp/data/YoloDiagnosticsMigrationTest.kt`
- `app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp02ActualModelGoldenTest.kt`
- `app/src/main/java/com/example/arptapp/DashboardActivity.kt`
- `app/src/main/java/com/example/arptapp/ExerciseAdapter.kt`
- `app/src/main/java/com/example/arptapp/data/AppDatabase.kt`
- `app/src/main/java/com/example/arptapp/data/ExerciseRecord.kt`
- `app/src/main/java/com/example/arptapp/data/YoloSessionDiagnosticsCodec.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/DetectorComparison.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/YoloActualGoldenComparison.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/YoloSessionDiagnostics.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/YoloSessionDiagnosticsBridge.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/YoloShadowRunner.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/ReportActivity.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/ReportNavigation.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/WorkoutReportData.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/YoloSessionDiagnosticsPresentation.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/YoloSessionDiagnosticsRenderer.kt`
- `app/src/main/java/com/example/arptapp/ui/camera/PoseCameraController.kt`
- `app/src/main/java/com/example/arptapp/ui/camera/yolo/TfliteYoloRuntime.kt`
- `app/src/main/res/layout/activity_report.xml`
- `app/src/main/res/layout/item_exercise_record.xml`
- `app/src/main/res/values/yolo_report.xml`
- `app/src/test/java/com/example/arptapp/data/YoloSessionDiagnosticsCodecTest.kt`
- `app/src/test/java/com/example/arptapp/domain/detection/YoloActualGoldenComparisonTest.kt`
- `app/src/test/java/com/example/arptapp/domain/detection/YoloSessionDiagnosticsBridgeTest.kt`
- `app/src/test/java/com/example/arptapp/domain/detection/YoloSessionDiagnosticsCollectorTest.kt`
- `app/src/test/java/com/example/arptapp/domain/detection/YoloShadowObserverIntegrationTest.kt`
- `app/src/test/java/com/example/arptapp/presentation/report/WorkoutReportDataTest.kt`
- `app/src/test/java/com/example/arptapp/presentation/report/YoloSessionDiagnosticsPresentationTest.kt`
- `docs/YOLO_DEVICE_VALIDATION.md`
- `docs/ml/EXP02_ANDROID_DEVICE_SHADOW_CHECKLIST.md`
- `docs/ml/exp02-actual-golden.manifest.example.json`
- `scripts/exp02-actual-golden.test.mjs`
- `scripts/generate-exp02-actual-golden.py`
- `scripts/provision-exp02-model.ps1`
- `scripts/test-exp02-actual-golden.py`
- `scripts/test-yolo-local-migration.py`
- `scripts/yolo-session-persistence.test.mjs`
- `scripts/yolo-session-wiring.test.mjs`

## 커밋 메시지

```text
feat: YOLO Shadow 세션 진단 저장과 EXP02 실기기 검증 도구 추가

- 세션 토큰 기반 집계와 Room 비파괴 마이그레이션 구현
- 운동 기록·상세 리포트에 DEBUG 전용 접이식 진단 추가
- noBackup 모델 주입 및 actual-model Golden 검증 도구 준비
- Landmark Primary와 NOT_VERIFIED 게이트 유지 및 회귀 테스트 추가
```

## 11. 실제 이미지 제공 후 검증 준비 단계 (전용 venv 설치 전 기록)

사용자가 직접 촬영했다고 확인한 정면/측면 4개 원본이 `local_validation/exp02_golden/`에 존재한다.
확장자가 `.jpg.png`인 실제 파일을 그대로 사용하며 수정·리사이즈·이름 변경·업로드하지 않았다.
이 경로 전체를 Git ignore에 추가했고 이미지·권리 manifest·metadata·현재 상태 문서는 untracked/ignored이다.
샘플별 파일 크기/해상도/원본 hash와 실행 차단 근거는 **private** `VALIDATION_STATUS.md`에만 기록했다.

### 실제 관찰과 실행 여부

- Canonical 모델의 파일명·크기·SHA256과 FlatBuffer 내 input/output shape/dtype를 직접 읽었다.
  기대값 FLOAT32 `[1,3,640,640]` / `[1,6,8400]`과 일치한다.
- `scripts/inspect-exp02-model.py`는 bounded stdlib parser이며 Interpreter allocation/invoke를 하지 않는다.
  출력 evidence는 `STATIC_FLATBUFFER_METADATA_ONLY`, `NOT_VERIFIED`이며 native 실행=false다.
- 기존 기본 Python/.venv/contract-env 3곳에 LiteRT/TensorFlow/tflite_runtime이 없다.
- 실제 reference 생성 명령을 시도했다. 기존 함수 내부 importlib 범위 충돌을 수정한 뒤,
  `ai_edge_litert` 미설치로 종료코드2를 반환했다. 자동 설치·다운로드는 하지 않았다.
- `reference_results.json`, generated RGB/tensor, actual Python/Android prediction은 **생성하지 않았다**.
- ADB online0/offline0/unauthorized0. 실제 native Golden·provisioning 미실행.
- 현재 결과는 **NOT_VERIFIED**다. `Golden verified / Android native pending` 단계에도 도달하지 않았다.

### 의미 정확도와 실행 동등성 분리

- 모델 오분류·검출0개도 실제 reference로 보존한다. expected label로 출력 생성을 막지 않는다.
- `expectedExercise`, `topPrediction`, `semanticMatch`, `hasExpectedDetection`은 사진별 sanity check다.
  평가 이미지 수가 작으므로 전체 모델 accuracy로 표현하지 않으며 equivalence의 통과 조건도 아니다.
- JSON은 실제 추론 성공 후에만 작성한다. 현재는 정답 개수·confidence·bbox를 추정하지 않는다.
- class/confidence/bbox/post-NMS count와 class ordering을 비교한다. 일대일 동등성 검사를 유지하고,
  양쪽 모두 정확한 confidence 동점인 구간 외 순서 변화는 실패한다.
- Python `<id>.input.f32`를 별도 private fixture로 보관하여 전체1,228,800개 정규화 float의
  max/mean 오차 및 RGB값 환산 오차를 측정한다. padding/native-size는 기존1e-6,
  resampled 영역은 기존1/255+1e-6를 유지한다. 새 tolerance를 넓혀 통과시키지 않았다.

실제 LiteRT가 설치된 **기존 로컬** interpreter에서 재실행하는 명령:

```powershell
& 'LITERT_PYTHON.exe' scripts/generate-exp02-actual-golden.py `
  --manifest local_validation/exp02_golden/manifest.json `
  --model app/src/main/assets/airptcoach_yolo26n_exp02_fp32.tflite `
  --output local_validation/exp02_golden `
  --reference-filename reference_results.json

# 실제 결과 파일이 생성된 후에만 선택한 DEBUG 기기로 private fixture를 전송한다.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/provision-exp02-model.ps1 `
  -DeviceSerial YOUR_DEVICE_SERIAL `
  -GoldenBundleDirectory local_validation/exp02_golden `
  -ReferenceFileName reference_results.json
```

주입 도구는 local filename을 검증하고 기기에서는 항상 private `reference.json`으로 전달한다.
새 raw FLOAT32 fixture도 크기·hash·little-endian 계약을 확인해 private 저장소로만 전달한다.
실제 전송 전에 운동/저장을 종료해야 한다. 모델 제공만으로 production gate는 열리지 않는다.

### 이번 단계 자동 검증 결과

| 항목 | 결과 |
|---|---|
| debug·release·Android test APK / JVM 빌드 | BUILD SUCCESSFUL, 6분28초, 136 tasks (25 executed / 111 up-to-date) |
| JVM | 53 suites / **507 tests**, 실패0·오류0·건너뜀0 |
| Comparator 테스트 | 기존15개에서28개로 확장. 오분류/무검출, 순서, 모호한 매칭, 전처리 경계 검증 |
| 기존 testcase 이름 대조 | 이전494개 중2개 이름/판정을 새 요구사항에 맞게 갱신, 나머지492개 유지. 순서 검사는 더 엄격해짐 |
| Node | **119/119** 통과 |
| Python 생성 도구 | **26/26** 통과 |
| Python 정적 metadata 도구 | **13/13** 통과 |
| PowerShell provisioning | AST syntax error0, 실제 기기 전송 미실행 |
| debug/release public APK 검사 | 미확인 weights0, 필수 MediaPipe task 유지 |
| debug/release/test APK private fixture 검사 | 원본 PNG·RGB·input.f32·reference_results·local_validation·weight entry 모두0 |
| 최신 APK code 확인 | debug/release DEX에 새 YoloActualGoldenTensorComparator 포함 |
| 원본 이미지 보존 | 작업 전후4장 SHA256 모두 동일 |
| scoped security/diff | 변경 공개 텍스트11개 secret-pattern 통과, diffcheckPASS |
| actual-model Golden·semantic accuracy·Android native | 미실행/미측정. 자동 테스트 통과로 대체하지 않음 |

산출물 (`build/dashboard-ui-verification/outputs/apk/`):

| 파일 | SHA256 |
|---|---|
| `debug/app-debug.apk` | `94CC87366A8EFC8129D5A671D8CAA42C8CCF676E04FC90D22333D499ECD15C7A` |
| `release/app-release-unsigned.apk` | `8AF4A1932F76267B66348901A6B0C8C86D456B7AF7E66C70A889E41E1FC4627A` |
| `androidTest/debug/app-debug-androidTest.apk` | `865CF4D17BC7667D25CE7C2DE58E4B36DF483117E82331806848E2FE1C219EBA` |

release는 패키징 검사본이며 서명·게시·배포하지 않았다. 세션 진단 저장·Room·Report·Primary·
점수·횟수·threshold·stabilizer·카메라 방향·SDK·provider·billing·원격 DB는 변경하지 않았다.
전체 저장소/이력의 긴 라이선스 스캔, 자동 commit/push, 원본 모델 삭제도 하지 않았다.

### 이번 공개 소스 변경 파일 (12개)

- `.gitignore`
- `app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp02ActualModelGoldenTest.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/YoloActualGoldenComparison.kt`
- `app/src/test/java/com/example/arptapp/domain/detection/YoloActualGoldenComparisonTest.kt`
- `docs/ml/EXP02_ANDROID_DEVICE_SHADOW_CHECKLIST.md`
- `scripts/exp02-actual-golden.test.mjs`
- `scripts/exp02-private-input.test.mjs`
- `scripts/generate-exp02-actual-golden.py`
- `scripts/inspect-exp02-model.py`
- `scripts/provision-exp02-model.ps1`
- `scripts/test-exp02-actual-golden.py`
- `scripts/test-exp02-model-inspection.py`

Private 생성 파일은 `local_validation/exp02_golden/manifest.json`, `model_static_metadata.json`,
`VALIDATION_STATUS.md`다. 이 이전 단계에서는 실제 prediction JSON이 없었다. 이후 실행은 다음 절을 기준으로 한다.

## 12. 실제 Android provisioning 및 negative-path 검증 (2026-10-17)

- authorized 기기, applicationId `com.example.arptapp`, DEBUG/run-as 접근과 실제 기존
  `no_backup` 경로를 확인했다. Context API를 사용하는 native test가 같은 경로에 결과를 남겼다.
- 설치 APK가 PC 검증본과 달라 DEBUG 앱을 `install -r`로 업데이트하고 instrumentation APK를
  설치했다. 앱 삭제/데이터 초기화/release 설치는 하지 않았다.
- Golden 파생 RGB4개/tensor4개/reference 전송은 **별도의 명시적 사용자 동의 후** 본인 기기
  private 저장소로만 진행했다. 원본 PNG/외부 서버/공용 저장소/Git/APK로 전송하지 않았다.
- 모델9,894,030 bytes 및 canonical SHA256, bundle의 모든 staging/final 크기·hash가 일치했다.
  실패한 임시 파일은 최종 rename 전에 차단·정리했고 guard를 완화하지 않았다.
- 실제 `Exp02ActualModelGoldenTest`: **OK (1 test),2.994초**. Interpreter 생성 및 FLOAT32
  `[1,3,640,640]` / `[1,6,8400]` 확인 후 native inference4회 성공.
- Python0개/Android0개가 네 조건 모두 일치했다. padding 오차0, resampled 최대 정규화 오차
  0.003921598196029663으로 기존1/255+1e-6 이내다. **Negative-path equivalence PASS**.
- positive class/confidence/bbox/NMS 실증은 미완료다. 전체 Golden/최종 VERIFIED로 승격하지 않는다.
  gate/threshold/stabilizer/Primary/rep/score/report 코드는 변경하지 않았다.
- 실제 JSON은 기기의 `no_backup/yolo/actual-golden/android-result.json`, 상세 PC 기록은 Git 제외
  `local_validation/exp02_golden/VALIDATION_STATUS.md`에서만 관리한다.
- 앱 재실행 후 사용자가 잠금을 풀고 진단을 열어 실제 UI를 확인했다:
  `YOLO SHADOW yolo26n_exp02_fp32 · CONTRACT_NOT_VERIFIED`,
  `exportContract=NOT_VERIFIED · prediction 비활성`. **MODEL_MISSING 제거 확인**.
  raw/stable/session/view=UNKNOWN과 timings/FPS 미측정은 현재 닫힌 gate에서 예상되는 상태다.
  운동 시작/가짜 기록 생성은 하지 않았으며 운동 후 Report persistence E2E는 아직 미검증이다.

전송 회귀 테스트는 다음 명령으로 실행한다. 모델/사진/ADB 없이 runner 함수만 AST로 읽어,
synthetic byte0..255 패턴의1/511/512/513/65535/65536/65537/9,894,030 bytes를 검사한다.
이는 **transport 검사**이며 actual-model Golden의 증거가 아니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-exp02-provision-transport.ps1 `
  -PythonPath 'C:\...\local_validation\.venv\Scripts\python.exe'
```

기본 ModelPath는 param 바인딩 후 script body에서 해석한다. Task<VoidTaskResult> 파이프라인
누출을 막고 raw stdin을 flush/close한다. 이 보완만으로 EOF 누락이 해결된 것은 아니며 정확한
byte-count가 핵심이다. [AOSP ADB 구현](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/client/commandline.cpp)도 참고한다.

`-WhatIf`에서도 해시 사전 검사가 실제 read-only로 동작하도록 SHA256을 별도 .NET stream 함수로
분리했다. 실제 `-WhatIf`는 ShouldProcess에서 끝났고 앱 종료/전송은 실행하지 않았다.
검증: Node75개, 로컬 binary/hash8종, 실제 Android test1개(native4회), 오프라인 Gradle build 성공.
