# EXP04 Android inference contract

기준: 2026-11-12. **NOT_VERIFIED / Android actual Golden NOT_RUN**.

## 1. 확인한 것과 아직 확인하지 않은 것

| 단계 | 상태 / 근거 |
|---|---|
| 사용자 Colab holdout·PT/LiteRT parity | 제공값 PASS, 이번에 재실행하지 않음. [모델 카드](../../MODEL_CARD.md) |
| 실제 PC 파일 / LiteRT load·allocate | 9,898,949 bytes·아래 SHA256 일치. 실제 tensor shape/dtype 확인. 이미지 inference 미실행 |
| Android native smoke | NOT_RUN; 테스트 구현/컴파일과 실행은 별개 |
| Android actual-image Golden | NOT_RUN; 실제 7종 입력 미제공 |
| Android camera accuracy/FPS/thermal | NOT_RUN; offline 수치로 대신하지 않음 |

ID `yolo26n_exp04_7class_fp32`, 파일 `airptcoach_yolo26n_exp04_7class_fp32.tflite`.
SHA256 `3e613e94fc1ef3aebbeba309d1b31d7ba6fc71a19274c9b2946793fe493eb84d`.
입력 FLOAT32 `[1,3,640,640]` = 4,915,200 bytes. 출력 FLOAT32 `[1,11,8400]` = 369,600 bytes.
ID 0~6: SQUAT, SHOULDER_PRESS, BICEP_CURL, PUSH_UP, BENT_OVER_ROW, TRICEPS_EXTENSION, SIDE_BEND. LUNGE/UNKNOWN/IDLE은 모델 class가 아닙니다.

## 2. 후보 계약의 근거와 경계

Tensor shape만으로 계약을 역추론하지 않았습니다. 기존 EXP03에서 확인한 exact Ultralytics **8.4.202 wheel**(SHA256 `ae71dc5bb1024010fbdef5bd9281984ddd8c70885bddcfdd6aff0801b7015fb4`)의 실제 predictor·LetterBox·LiteRT backend·NMS를 기준으로 후보 포트를 재사용했습니다. EXP02의 8.4.176 및 기존 전처리는 그대로 유지합니다.

| 후보 항목 | 근거 / 구현 |
|---|---|
| logical RGB, BCHW float32, `/255` | wheel `engine/predictor.py` preprocess. Python은 원본 함수 AST만 선택 실행; 전체 Ultralytics import/자동 설치·업데이트 없음 |
| 640×640 centered letterbox, 114 padding, auto=false, OpenCV linear resize | wheel `data/augment.py`; Android 기존 `YoloOpenCvLinearRgbPreprocessor` 후보. **EXP04 실제 이미지의 bit parity는 아직 미검증** |
| normalized center xywh + nc class scores | wheel `nn/backends/litert.py` raw-output branch 및 [Detect head](https://github.com/ultralytics/ultralytics/blob/v8.4.202/ultralytics/nn/modules/head.py). backend가 xywh를 model pixel로 변환; 두 번째 sigmoid/objectness 곱셈 없음 |
| per-anchor argmax, external class-aware NMS | [exact NMS source](https://github.com/ultralytics/ultralytics/blob/v8.4.202/ultralytics/utils/nms.py), multi_label=false, agnostic=false, nc=7. clip/rescale은 NMS 후 적용 |
| source coordinates | [exact ops source](https://github.com/ultralytics/ultralytics/blob/v8.4.202/ultralytics/utils/ops.py), 실제 resize width/height 별 gain으로 역변환 |

공식 [LetterBox source](https://github.com/ultralytics/ultralytics/blob/v8.4.202/ultralytics/data/augment.py). `litert.py`는 검증된 로컬 wheel에서 직접 읽었으며 버전이 다른 설치 패키지로 대체하지 않습니다. 생성 reference에는 wheel·각 source SHA256·원본 함수 목록을 남기고 Android가 exact hash를 검증합니다. EXP03 Android PASS를 EXP04 PASS로 재사용하지 않습니다.

threshold는 기존 초기 공학 기준을 유지합니다: raw confidence `> .10`, NMS IoU `.70`, maxDetections 300, stable confidence `.60` 및 기존 시간 안정화. holdout 최적값이라는 뜻이 아니며 FAIL 후 자동 조정하지 않습니다.

## 3. 실제 입력 제공 전: fixture 템플릿

현재 아래 폴더에는 이미지가 없고 `manifest.json`의 rightsCleared는 모두 false입니다. 전체 `local_validation/`은 Git ignored이며 APK source가 아닙니다.

```text
local_validation/exp04_golden/
├─ manifest.json
├─ inputs/
│  ├─ 0_SQUAT/
│  ├─ 1_SHOULDER_PRESS/
│  ├─ 2_BICEP_CURL/
│  ├─ 3_PUSH_UP/
│  ├─ 4_BENT_OVER_ROW/
│  ├─ 5_TRICEPS_EXTENSION/
│  └─ 6_SIDE_BEND/
└─ runs/<unique-id>/       # 아직 실제 reference 없음
```

공개 원본은 [manifest example](exp04-actual-golden.manifest.example.json)입니다. 다른 PC에서는 이 예시를 위 ignored 경로로 복사하고 해당 inputs 폴더를 생성하세요. 기존 manifest/검증 결과를 덮어쓰지 마세요.

사용자가 직접 제공할 자료: 종목별 TEST 원본 대표 프레임 최소 1장, 실제 운동/촬영 방향, 데이터셋 출처·버전·TEST split·원본 영상/프레임 식별자를 rightsBasis에 기록하고 권리 확인 후에만 rightsCleared=true. 사용자 제공 출처는 Mendeley의 “A Multi-View Raw Video Dataset of Seven Fitness Exercises with Good/Bad Form Labels” / CC BY 4.0이며 이번 작업에서 개별 자료의 권리·영상 분할을 독립적으로 확인하지 않았습니다. 무작위 인터넷 이미지·생성 이미지를 대체 입력으로 사용하지 않습니다.

## 4. 이미지 제공 후 PC reference (이번에는 실행하지 않음)

기존 로컬 venv/공식 `ai_edge_litert.interpreter.Interpreter`, NumPy, Torch, Pillow, OpenCV **4.13.0**을 사용합니다. 새 runtime/패키지는 설치하지 않았습니다. exact source wheel이 없으면 중단하며 자동 다운로드하지 않습니다.

```powershell
local_validation\.venv\Scripts\python.exe scripts/generate-exp04-actual-golden.py --manifest local_validation/exp04_golden/manifest.json --output local_validation/exp04_golden/runs/exp04_actual_01 --check-inputs
local_validation\.venv\Scripts\python.exe scripts/generate-exp04-actual-golden.py --manifest local_validation/exp04_golden/manifest.json --output local_validation/exp04_golden/runs/exp04_actual_01
local_validation\.venv\Scripts\python.exe scripts/transfer-exp04-actual-golden.py check-bundle --bundle local_validation/exp04_golden/runs/exp04_actual_01
```

`--check-inputs`는 모델을 실행하지 않습니다. 현재 템플릿은 rights 미확인으로 의도적으로 실패합니다. 본 실행은 실제 모델 identity→tensor metadata→실제 inference 후에만 RGB/input.f32/output.f32/reference.json을 새 run에 기록합니다. reference.json은 마지막에 생성합니다. 오류가 발생한 부분 run은 성공 증거가 아니며 새 ID로 다시 실행해야 합니다. 오래된 결과를 삭제/덮어쓰지 않습니다.

## 5. Android 실행 준비 (기기 작업은 별도 동의 후)

[rollout](EXP04_RUNTIME_SHADOW_ROLLOUT.md)의 `.exp04` debug 앱/Android test APK를 설치하고 모델을 private provisioning합니다. 기존 앱의 기록을 지우거나 옮기지 않습니다. 모델과 fixture는 서로 다른 도구로 전송합니다. 아래 `$adb`, `$serial`은 본인 SDK의 adb.exe와 정확한 한 대의 USB serial을 직접 지정하세요. 공개 저장소에 serial을 기록하지 마세요.

```powershell
# App private에만 원본 RGB/tensor/reference 전송. 서버/공유 저장소/Git/APK 전송 아님.
local_validation\.venv\Scripts\python.exe scripts/transfer-exp04-actual-golden.py provision --adb "$adb" --serial "$serial" --run-id exp04_actual_01 --bundle local_validation/exp04_golden/runs/exp04_actual_01
$refHash = (Get-FileHash local_validation/exp04_golden/runs/exp04_actual_01/reference.json -Algorithm SHA256).Hash.ToLowerInvariant()
& $adb -s $serial shell am instrument -w -e class 'com.example.arptapp.ui.camera.yolo.Exp04NativeContractDeviceTest#privateIdentityMetadataAndBoundedSyntheticInvoke' com.example.arptapp.exp04.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s $serial shell am instrument -w -e exp04GoldenRun exp04_actual_01 -e exp04ReferenceSha256 $refHash -e class 'com.example.arptapp.ui.camera.yolo.Exp04ActualModelGoldenTest#preprocessingOnlyMatchesPythonBitForBit' com.example.arptapp.exp04.test/androidx.test.runner.AndroidJUnitRunner
# 전처리 PASS가 확인된 경우에만 다음 실행. 본 테스트도 내부에서 동일 조건을 재검사.
& $adb -s $serial shell am instrument -w -e exp04GoldenRun exp04_actual_01 -e exp04ReferenceSha256 $refHash -e class 'com.example.arptapp.ui.camera.yolo.Exp04ActualModelGoldenTest#actualExp04MatchesExactVersionPythonReference' com.example.arptapp.exp04.test/androidx.test.runner.AndroidJUnitRunner
local_validation\.venv\Scripts\python.exe scripts/transfer-exp04-actual-golden.py export-result --adb "$adb" --serial "$serial" --run-id exp04_actual_01 --bundle local_validation/exp04_golden/runs/exp04_actual_01
```

별도 EXP03 negative 파일이 `.exp04` sandbox에 없으면 해당 역방향 native negative test는 SKIPPED/NOT_RUN이지 PASS가 아닙니다. 이 검증만을 위해 이전 파일을 자동 복사하지 않습니다. 두 모델의 bytes/hash/shape 양방향 거부는 JVM에서도 검사합니다.

## 6. 판정과 보존

1. RGB SHA256과 geometry → **input tensor SHA256 exact 일치**. 한 픽셀 오차 허용으로 승격하지 않습니다. 기존 pixel error 비교는 진단값만 병기합니다. 불일치 시 native Golden invoke 차단.
2. 실제 Python raw output을 동결해 Android decoder/NMS 비교: confidence `1e-6`, bbox `1e-4px`.
3. 동일 tensor의 실제 Android native output 비교: confidence `≤ .001`, bbox 각 좌표 `≤ 2px`, count·class 동일. ordering 차이는 기존 comparator의 완전히 동일한 confidence tie 처리만 허용하며 원본 순서와 mapping을 기록합니다. tolerance 확대 금지.
4. runtime equivalence와 semanticMatch를 별도로 보고합니다. 오분류/무탐지도 숨기거나 expected label로 바꾸지 않습니다. 7장 sanity check는 전체 모델 accuracy가 아닙니다. positive class coverage도 별도로 남깁니다.
5. scalar-only 결과를 새 `android-result.json`에 기록하며 사진/계정/DB를 로그·서버에 보내지 않습니다. PASS/FAIL/NOT_RUN 모두 보존합니다.
6. **어떤 결과도 자동 VERIFIED 승격하지 않습니다.** Android 실제 Golden·native 근거 검토 후 별도 코드 변경/재검증이 필요하며 camera 품질은 그 이후 독립 항목입니다.
