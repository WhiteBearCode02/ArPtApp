# EXP03 actual-image Golden · 2026-11-05

**최신 Run 2 PASS / Exp03YoloContract VERIFIED / 실제 camera NOT_MEASURED**.
기존 resampled 보간만 수정한 뒤 input tensor4/4 byte identical, 실제 Python4회/Android8회,
confidence 최대3.5762787e-7 / bbox 최대9.1552734e-5px로 통과했습니다. tolerance는 확대하지 않았습니다.
[정확한 원인·수정·64개 unit vector·동일4장 상세 결과](EXP03_PREPROCESSING_PARITY.md).

## Run 1 — FAIL 원문 및 근거 보존

**당시 전체 FAIL / 당시 Exp03YoloContract NOT_VERIFIED / 실제 camera NOT_MEASURED**

EXP03 전용 Python reference를 새로 생성하고 실제 휴대폰에서 비교했습니다. 이전 EXP02 출력은
재사용하지 않았습니다. 실패한 입력/결과도 보존하며 threshold·tolerance·production 전처리를
바꾸거나 EXP03을 VERIFIED로 승격하지 않았습니다.

## 입력과 권리 경계

사용자가 직접 촬영한 로컬 검증 이미지라고 명시했고, 기존 권리 manifest와 이번 요청에서
source image의 로컬 재사용을 허용한 4장만 사용했습니다. 권리 근거는 사용자 확인이며,
독립적인 법률 검토나 배포 권리 보증은 아닙니다. 인터넷 이미지/새 촬영은 사용하지 않았습니다.

| sample | 운동 / 방향 | 원본 크기 | 원본 bytes | Python / Android NMS 후 수 |
|---|---|---:|---:|---:|
| squat_front | SQUAT / FRONT | 350×410 | 99,610 | 2 / 2 |
| squat_side | SQUAT / SIDE | 466×438 | 112,922 | 2 / 2 |
| shoulder_press_front | SHOULDER_PRESS / FRONT | 314×446 | 221,392 | 1 / 1 |
| shoulder_press_side | SHOULDER_PRESS / SIDE | 314×454 | 173,549 | 1 / 1 |

원본은 `local_validation/exp02_golden/`에서 읽기만 했습니다. 새 manifest는
`local_validation/exp03_golden/manifest.json`, 새 결과는
`local_validation/exp03_golden/runs/actual_20261105_01/`입니다. 두 경로 모두 Git ignored입니다.
권리는 source image에만 재사용했고 EXP02 reference/prediction을 EXP03으로 재라벨링하지 않았습니다.

## 실제 Python reference

- EXP03: `airptcoach_yolo26n_exp03_final_fp32.tflite`, **9,894,032 bytes**.
- 모델 SHA256: `3d2b6bc537d8aef5a19b671226d73c98b49411574a193be72e225086202d1167`.
- Python 3.11.9 / ai-edge-litert 2.2.0 / NumPy 2.4.6 / OpenCV 4.13.0 / CPU Torch 2.9.1+cpu.
- reference source: **Ultralytics 8.4.202** 원본 wheel에서 AST-selected 함수를 변경 없이 실행.
- wheel SHA256: `ae71dc5bb1024010fbdef5bd9281984ddd8c70885bddcfdd6aff0801b7015fb4`.
- 공식 [버전별 PyPI metadata](https://pypi.org/pypi/ultralytics/8.4.202/json)의 hash와 다운로드 파일을 대조했습니다.
- 기존 venv의 설치된 ultralytics distribution은 8.4.176이지만 **그 패키지의 함수를 실행하지 않았습니다**.
  새 의존성 설치/버전 변경 없이 8.4.202 wheel source만 ignored build 경로에서 사용했습니다.
- 실제 모델 invoke **4회**, FLOAT32 input `[1,3,640,640]` / output `[1,6,8400]` 확인.
- 매 sample마다 source image/RGB/input tensor/native raw output SHA, 전처리 geometry, 실제 결과를 기록했습니다.
- confidence .10 / IoU .70 / maxDet300 / class-aware best-class NMS 유지.

| 원본 함수 | 8.4.202 wheel 내 위치 |
|---|---|
| BasePredictor.preprocess / pre_transform | engine/predictor.py:183–206 / 224–243 |
| LetterBox | data/augment.py:1678–1937 |
| LiteRTBackend.forward | nn/backends/litert.py:70–144 |
| xywh2xyxy / scale_boxes / clip_boxes | utils/ops.py:264–284 / 126–170 / 188–213 |
| non_max_suppression / TorchNMS | utils/nms.py:13–169 / 172–301 |

각 source file hash도 reference와 Android harness에 고정했습니다. 8.4.202의 CPU 경로는
`intel=False`, NCHW, FLOAT32 graph I/O를 사용합니다. 전처리는 RGB/BCHW `/255`, centered
114 letterbox, OpenCV INTER_LINEAR입니다. 원본 backend가 normalized xywh를 모델 pixel로
변환하고 NMS 후 별도 X/Y gain으로 source 좌표를 복원합니다. shape만으로 추정한 것이 아닙니다.
이 버전의 극단적으로 얇은 입력에 대한 `max(round(...),1)` 처리까지 기존 Android 구현과 동일하다고
주장하지 않습니다. 현재 4장의 geometry는 일치하지만 전체 contract 승격은 보류합니다.

reference SHA256: `760a8a394325d54113ffcac367f2456bde2edfce00a44b9f66a7d05bf1406b14`.

## Android: 세 단계를 분리한 실제 측정

정확히 1대의 authorized 본인 휴대폰에서 기존 TF Lite **2.14.0**을 사용했습니다. 앱 프로세스가
실행 중이지 않은 것을 확인하고 test APK만 `install -r`로 갱신했습니다. 앱/사용자 DB는 지우지
않았고 모델 재주입도 필요하지 않았습니다.

`no_backup/yolo/exp03-actual-golden/runs/actual_20261105_01/`에만 13개 fixture를 전송했습니다.
파일별 staging/final size/SHA 확인, reference-last publication, exclusive run 경로를 사용했습니다.
Android는 별도 인자로 전달한 reference SHA도 대조했습니다.

| 단계 | 실제 경로 | 결과 |
|---|---|---|
| A. end-to-end | 동일 RGB → 기존 Android 전처리 → 실제 native → decoder/NMS | **1/4 PASS, 전체 FAIL** |
| B. native 분리 | Python과 byte-identical tensor → 실제 Android native → decoder/NMS | **4/4 PASS** |
| C. decoder/NMS 분리 | Python의 실제 모델 raw → Android decoder/NMS | **4/4 PASS**, confidence/bbox 오차0 |

Android 실제 모델 load/shape/dtype PASS. 네 이미지마다 A/B 각각 한 번, native invoke **8회**입니다.
C는 frozen raw 비교이며 native inference 횟수에 포함하지 않았습니다. synthetic inference가 아닙니다.

### A 경로의 최종 결과

| sample | class order (양쪽 동일) | 최대 confidence 차이 | bbox 최대 오차 | 판정 |
|---|---|---:|---:|---|
| squat_front | [0,1] | 0.00283441 | 0.179553px | FAIL: confidence |
| squat_side | [0,1] | 0.00257927 | 0.314414px | FAIL: confidence |
| shoulder_press_front | [1] | 0.00060154 | 0.060002px | PASS |
| shoulder_press_side | [1] | 0.00118887 | 0.249932px | FAIL: confidence |

허용 오차는 기존 그대로 confidence ≤ **0.001**, bbox ≤ **2px**, class/count exact입니다.
ordering comparator도 기존 정책(양쪽의 exact-score tie 이외 재배열 금지)을 유지했습니다.
모든 실제 class sequence는 재배열 없이 exact입니다.

### 실패 원인과 한계

- geometry/채널/정규화/padding 일치. 전처리의 padding 오차0.
- Android의 half-pixel bilinear rounding과 OpenCV INTER_LINEAR의 uint8 보간/반올림 차이로
  4장 모두 tensor는 byte-identical이 아닙니다. 최대 normalized 차이는 **0.0039215982**,
  기존 resampled 허용치 `1/255 + 1e-6` 이내입니다. 평균 RGB-value-equivalent 차이는 약0.0483–0.0798입니다.
- 이 작은 입력 차이로 최종 confidence 차이가 일부 0.001을 초과했습니다.
- B에서 최대 confidence 오차 **3.5762787e-7**, bbox 오차 **9.1552734e-5px**로 모두 통과했습니다.
  native raw 최대 절대 차이는 **4.1723251e-6**이며 진단값이지 새 tolerance가 아닙니다.
- C에서는 confidence/bbox 오차0, 최종 count/order 동일입니다.

따라서 관찰된 주요 차이는 **전처리 보간 값 → 모델 confidence** 경로로 분리됩니다. 모델 재학습,
threshold/stabilizer 조정, SDK upgrade로 우회하지 않았습니다. OpenCV와 일치하는 EXP03 전용
전처리를 원본 구현/경계 fixture 근거로 별도 구현·검증한 뒤, 동일 원본 입력으로 **새 run**을
생성해 다시 비교해야 합니다. 기존 EXP02 수학/역사 증거를 조용히 바꾸지 않습니다.

Android result SHA256: `3b9c532237985538f30771074b479609617d20502e281b3e103837d2193ce09a`.
원본 result JSON은 private 로컬/기기에 보존하며 공개 저장소에 넣지 않습니다.

## 의미적 정답은 별도

네 장 모두 top prediction은 사용자 제공 운동명과 일치했습니다(**4/4 top-label sanity**).
다만 SQUAT 두 장에는 SHOULDER_PRESS의 부가 검출도 남아 있습니다. 이 결과를 숨기거나
정답 label에 맞춰 제거하지 않았습니다. 이 소수 입력 결과는 모델 전체 accuracy가 아니며,
runtime Golden이 실패한 사실을 상쇄하지 않습니다.

## Contract / Safety

- 이전/현재: **NOT_VERIFIED → NOT_VERIFIED**, 승격 없음.
- actual-image Golden: **RUN / FAIL**. 단순 NOT_RUN이나 PASS로 쓰지 않습니다.
- DEBUG gate는 기존 ON이지만 contract 미검증으로 **prediction=false**.
- release gate OFF / Primary LANDMARK / rep·score·coaching·routing 변경 없음.
- cameraPerformance **NOT_MEASURED**. 정지 이미지 nativeMs를 카메라 FPS/latency로 재사용하지 않습니다.
- 모델/원본 사진/RGB/input/output tensor/reference/venv/wheel 모두 Git/APK 제외.
- 원격 DB/API/LLM/업로드/배포/billing/유료 의존성 추가 없음. 전체 앱 무과금/법적 승인을 보증하지 않습니다.
- EXP02 generator/test/docs/model/evidence, 사용자 운동 기록을 수정·삭제하지 않았습니다.

## 재현 (기존 run을 덮어쓰지 말 것)

```powershell
$python = '.\local_validation\.venv\Scripts\python.exe'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$run = 'YOUR_NEW_SAFE_RUN_ID'
$bundle = "local_validation/exp03_golden/runs/$run"
& $python scripts/generate-exp03-actual-golden.py --manifest local_validation/exp03_golden/manifest.json --output $bundle
& $python scripts/transfer-exp03-actual-golden.py --adb $adb --serial YOUR_SERIAL --run-id $run --bundle $bundle provision
$referenceHash = (Get-FileHash "$bundle/reference.json" -Algorithm SHA256).Hash.ToLowerInvariant()
& $adb -s YOUR_SERIAL shell am instrument -w -r -e exp03GoldenRun $run -e exp03ReferenceSha256 $referenceHash -e class com.example.arptapp.ui.camera.yolo.Exp03ActualModelGoldenTest com.example.arptapp.test/androidx.test.runner.AndroidJUnitRunner
& $python scripts/transfer-exp03-actual-golden.py --adb $adb --serial YOUR_SERIAL --run-id $run --bundle $bundle export-result
```

instrumentation의 셸 종료 코드만 보지 말고 `FAILURES`/`allSamplesPassed`를 확인해야 합니다.
누락 모델·fixture·권리·version·hash는 실패이며 skip/PASS로 처리하지 않습니다. generator와
test가 production 검증 상수를 자동으로 수정하는 API는 없습니다.

## Run 1 변경 파일 / 검증 (이력)

| 파일 | 이유 |
|---|---|
| scripts/generate-exp03-actual-golden.py | 정확한8.4.202 원본 source + 실제EXP03 reference, 새 private run/hash/권리/geometry |
| scripts/transfer-exp03-actual-golden.py | 모델과 별개인 private fixture 전송, 고유 run·경로·hash·한 기기 제한 |
| scripts/test-exp03-actual-golden.py | 권리/경로/과거 모델/버전/덮어쓰기/USB 선택 실패 회귀 |
| scripts/exp03-actual-golden.test.mjs | source pin·native 경로·3단계 분리·실시간 비우회 구조 검사 |
| app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp03ActualModelGoldenTest.kt | 실제 이미지 4장, A/B/C 분리 비교와 실패 evidence 보존 |
| docs/ml/EXP03_ANDROID_INFERENCE_CONTRACT.md | source8.4.202 및 실제 Golden 실패 원인/미승격 |
| docs/ml/EXP03_ANDROID_DEVICE_SHADOW_CHECKLIST.md | Golden 재현/결과 해석/카메라 보류 절차 |
| docs/ml/EXP03_RUNTIME_SHADOW_ROLLOUT.md | 이번 실제 결과와 기존 smoke 기록 구분 |
| MODEL_CARD.md | Golden NOT_RUN에서 RUN/FAIL로 정정, 정확도와 별개 표시 |
| 이 문서 | 입력·버전·실제 측정·실패 원인·보안·재현 근거 |

최종 build/회귀/보안 결과는 [rollout](EXP03_RUNTIME_SHADOW_ROLLOUT.md)의 이번 actual-image 항목에 기록합니다.
자동 commit/push/PR/merge는 하지 않았습니다.
