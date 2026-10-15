# EXP02 Android inference contract

기준일: 2026-10-16 · 모델: `yolo26n_exp02_fp32` · **Landmark Primary / YOLO DEBUG Shadow**

## 상태와 검증 경계

| 구분 | 이 문서 작성 시 상태 | 의미 |
|---|---|---|
| Exact-version source | **VERIFIED** | PyPI의 Ultralytics 8.4.176 wheel 소스를 직접 확인했습니다. 최신 버전 코드를 대입하지 않았습니다. |
| 로컬 모델 식별·export metadata | **VERIFIED, 정적 검사** | canonical 파일의 SHA256과 내장 metadata를 읽었습니다. Interpreter 실행 증거는 아닙니다. |
| Python ↔ Kotlin synthetic golden | **PASS, 명시된 허용오차 범위** | 전처리 6종·decoder·후처리 5종과 경계 검사를 실행했습니다. bitwise 동일성/actual-model inference 검증은 아닙니다. |
| Android native compatibility | **NOT_VERIFIED** | Android Interpreter 초기화·실행은 별도 검증 대상입니다. Python 실행 성공으로 대체할 수 없습니다. |
| 실기기 지연·정확도·발열 | **NOT_MEASURED** | 실측 데이터만 기록합니다. 과거 Colab 지연을 Android 수치로 쓰지 않습니다. |
| 과거 EXP02 test metric | **HISTORICAL** | 원본 실험에서 기록한 수치이며 이번 Android 구현의 성능 측정값이 아닙니다. |

**전체 contract 활성화는 아직 보류입니다.** Source evidence와 합성 Kotlin/Golden은 검증했지만, OpenCV 보간 차이의 actual-model 영향 및 Android runtime metadata/init/run이 미검증입니다. `Exp02YoloContract`는 `NOT_VERIFIED`이고 `CONTRACT_NOT_VERIFIED`로 닫습니다. 미확인 다른 export용 `UnverifiedYoloContract`도 유지합니다. 환경 변수나 UI 스위치만으로 승격하지 않습니다.

Canonical concise reference [AIRPTCoach_EXP02_Export_Verify.ipynb](AIRPTCoach_EXP02_Export_Verify.ipynb)가 이번 작업 중 사용자에 의해 제공되었고, 13개 cell 전체를 읽어 확인했습니다. 이 문서는 해당 concise reference를 export history의 기준으로 사용합니다. 큰 원본 `AIRPTCoach_AIHub_AutoPipeline.ipynb`는 사용자 파일로 보존하며, 복사·이름 변경·새로 tracking하지 않습니다. 모델 metadata가 확인하는 사실과 notebook이 기록한 과거 실행 정보를 구별합니다.

## 1. 재현 가능한 source provenance

- 기준 패키지: [PyPI ultralytics 8.4.176](https://pypi.org/project/ultralytics/8.4.176/), [exact-version release JSON](https://pypi.org/pypi/ultralytics/8.4.176/json).
- Artifact: `ultralytics-8.4.176-py3-none-any.whl`.
- SHA256: `15f4bbac5c69fd9a5a9395f5957a7ce561e3eb4620699a9c2ff11ed38ec7d34c`.
- 소스 내 `ultralytics/__init__.py:3`의 version도 `8.4.176`입니다.
- 로컬 참조는 Git 제외 build 폴더에만 내려받았습니다. 앱 dependency를 설치·업그레이드하지 않았고, wheel/제3자 소스를 이 저장소에 vendor하지 않습니다.
- 아래 경로와 line은 **이 wheel 내부의 `ultralytics/` 기준**입니다. `main`/최신 문서 또는 확인되지 않은 Git tag의 line을 인용한 것이 아닙니다.

### canonical concise notebook evidence

- 경로: `docs/ml/AIRPTCoach_EXP02_Export_Verify.ipynb`.
- SHA256: `a5c74b7fd93272fd2f46fa125f4e28dca0090db4de28f86627ae2edbff277999`.
- 다음 cell 번호는 JSON 배열 기준 **0-based**입니다. Notebook을 이번 작업에서 실행·수정하지 않았습니다.
- Cell 4는 과거 환경을 Ultralytics `8.4.176`, Python `3.13.15`, `litert_torch` `0.9.4`, `ai-edge-litert` `2.2.0`으로 기록합니다. 이는 역사적 환경 기록이며 현재 Python 환경을 설치·pin하는 코드가 아닙니다. Exact-version source 확인은 위 PyPI artifact로 별도 수행했습니다.
- Cell 5는 `format="litert", imgsz=640, batch=1, device="cpu"` export 명령을 보존합니다. `RUN_EXPORT=False`이므로 제공된 notebook을 읽는 것만으로 재-export가 발생하지 않습니다.
- Cell 7은 생성된 `.tflite`를 canonical 이름으로 복사하는 과거 절차를 기록합니다. `RUN_CANONICAL_COPY=False`이며 이번 작업에서는 모델을 복사·교체하지 않았습니다.
- Cells 8–9는 `YOLO(FINAL_TFLITE).val(data=EXP02_YAML, split="test", imgsz=640)` 경로와 과거 metric을 기록합니다. `RUN_LITERT_VALIDATION=False`; 이번 작업의 새 성능 측정이 아닙니다.
- Cells 10–11은 FLOAT32 BCHW input과 FLOAT32 BCN output의 과거 tensor 정보를 정리하고 runtime 검사 코드를 제공합니다. 이 코드의 존재만으로 이번 Android Interpreter 검사 완료를 주장하지 않습니다.
- Cells 0·12는 shape만으로 color/resize/channel/NMS를 추정하지 말아야 한다고 명시합니다. 이 문서의 21개 항목은 notebook의 미확인 목록을 임의 승격한 것이 아니라 **해당 exact-version source와 artifact metadata를 추가 대조한 결과**입니다.

### canonical 모델의 정적 식별

| 항목 | 확인값 |
|---|---|
| 파일명 | `airptcoach_yolo26n_exp02_fp32.tflite` |
| 바이트 수 | `9894030` |
| SHA256 | `464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749` |
| 내장 metadata version / task / head | `8.4.176` / `detect` / `Detect` |
| batch / imgsz / channels | `1` / `[640,640]` / `3` |
| end2end / quantize | `false` / `null` |
| names | `0=SQUAT`, `1=SHOULDER_PRESS` |
| tensor contract | input FLOAT32 `[1,3,640,640]`, output FLOAT32 `[1,6,8400]`; Android에서 runtime 재검증 필요 |

모델에 append된 `metadata.json`을 읽어 version·head·end2end를 확인했습니다. **shape만으로 channel 의미를 추정하지 않았습니다.** Metadata 전체를 로그/문서에 복사하지 않습니다. description·로컬 학습 경로 등은 진단에 불필요하므로 whitelist 밖입니다.

## 2. 21개 contract 항목과 Android 대응

Android의 비교 기준은 **고정 입력 LiteRT `predict` 경로**입니다. 아래의 source 확인을 Python/Kotlin golden 통과와 혼동하지 않습니다.

| # | 항목 / source로 확인한 값 | Source file · class/function · line | Android 대응 |
|---|---|---|---|
| 1 | Logical RGB. 일반 ndarray 입력은 BGR이므로 channel을 뒤집습니다. | `engine/predictor.py`, `BasePredictor.preprocess`, 183–205 | `Exp02RgbPreprocessor`: Bitmap의 논리 R/G/B를 명시적으로 추출. raw byte order를 RGB로 가정하지 않음 |
| 2 | Letterbox → batch/HWC→CHW → BGR→RGB → contiguous → float → `/255` | 같은 함수, 196–205 | Bitmap adapter와 pure RGB tensor 모듈 분리 |
| 3 | 종횡비 유지, `r=min(640/H,640/W)`, 각 resized 크기는 Python `round` | `data/augment.py`, `LetterBox.get_params`, 1784–1811 | `Exp02FrameTransform`: ties-to-even round와 실제 resized 크기 보존 |
| 4 | Static LiteRT predict: centered letterbox, `auto=false`, `scale_fill=false`; predict scaleup 허용 | `engine/predictor.py`, `BasePredictor.pre_transform`, 225–244; `data/augment.py`, `LetterBox.__init__`, 1701–1711 | 고정 640×640을 보장; stretch/stride-auto 미사용 |
| 5 | Target 640×640 | 모델 `metadata.json` imgsz; `engine/predictor.py`, `setup_source`, 300 | 입력 shape `[1,3,640,640]`과 일치 검사 |
| 6 | 중앙 padding: left/top은 half-padding−0.1의 round, right/bottom은 +0.1 | `data/augment.py`, `LetterBox.get_params`, 1797–1811 | 정수 left/top/right/bottom 보존; 홀수 padding 비대칭 처리 |
| 7 | Constant padding 114, 모든 color channel 동일 | `data/augment.py`, `LetterBox.__init__`, 1709; `apply_image`, 1847–1848 | padding tensor sample은 `114/255f` |
| 8 | OpenCV `INTER_LINEAR` | `data/augment.py`, `LetterBox.__init__`, 1710; `apply_image`, 1842 | Pure Kotlin half-pixel bilinear의 허용 편차를 아래 golden으로 검증. OpenCV bitwise 동일이라고 주장하지 않음 |
| 9 | BCHW/NCHW. LiteRT backend는 실제 tensor로 legacy NHWC도 구별 | `engine/predictor.py`, `preprocess`, 200; `nn/backends/litert.py`, `load_model`, 41–43, `forward`, 59–62 | Native-order direct FLOAT32 ByteBuffer; R plane → G plane → B plane |
| 10 | uint8 → float `/255`; 추가 mean/std 없음 | `engine/predictor.py`, `preprocess`, 205 | 논리 channel 값/255f |
| 11 | EXP02 boundary FLOAT32, 정수 graph I/O quantization 불필요 | 확인 tensor; `nn/backends/litert.py`, `forward`, 63–68 | dtype·개수·shape mismatch는 unavailable |
| 12 | Raw output BCN; NMS에서 candidate-major로 transpose | `nn/modules/head.py`, `Detect.forward`, 180–204; `utils/nms.py`, `non_max_suppression`, 82–94 | `Exp02OutputDecoder`: channel-major indexing, 8400 candidates |
| 13 | 4 decoded box channel + class probability channel. nc=2는 모델 names로 확인 | `nn/modules/head.py`, `Detect._inference`, 206–217; 모델 names | `0..3=box`, `4=SQUAT score`, `5=SHOULDER_PRESS score` |
| 14 | Non-end2end Detect는 center xywh | `nn/modules/head.py`, `Detect.decode_bboxes`, 243–247; `utils/tal.py`, `dist2bbox`, 457–476 | 모델 pixel center xywh → xyxy 변환 |
| 15 | LiteRT raw box 단위는 target 크기로 나눈 좌표. Python backend가 width/height를 곱해 복원 | `utils/export/litert.py`, `torch2litert`, 76–85; `utils/export/engine.py`, `_NormalizeCoords.forward`, 41–61; `nn/backends/litert.py`, `forward`, 84–89 | x/w×640, y/h×640. 정규화 단위라는 이유만으로 모든 값을 `[0,1]`에 clamp하지 않음 |
| 16 | Class score는 head에서 이미 sigmoid. Predict는 max class score; 별도 objectness 곱 없음 | `nn/modules/head.py`, `Detect._inference`, 217; `utils/nms.py`, `non_max_suppression`, 114–126 | score 재-sigmoid·softmax·objectness multiplication 금지 |
| 17 | Confidence는 strict `>`; predict best-class-only, val multi-label | `utils/nms.py`, `non_max_suppression`, 86, 117–126; `models/yolo/detect/val.py`, `postprocess`, 168–178 | `Exp02PostProcessor` threshold 정책 분리; 동률 class는 첫 index |
| 18 | EXP02 external class-aware NMS. Artifact end2end=false, default export nms=None | 모델 metadata; `cfg/default.yaml`, 61; `engine/exporter.py`, `Exporter.__call__`, 723–724; `utils/nms.py`, 146–160 | 중복 embedded NMS 없음. 같은 class 사이만 suppression |
| 19 | 실제 predictor/validator cfg IoU=0.7. NMS helper signature 0.45는 pipeline default 아님 | `cfg/default.yaml`, 56; `models/yolo/detect/predict.py`, `postprocess`, 55–64; `models/yolo/detect/val.py`, 168–178 | NMS IoU 0.7, suppression 조건 IoU>0.7 |
| 20 | max detections=300, pre-NMS cap=30000 | `cfg/default.yaml`, 57; `utils/nms.py`, signature 20–24, 139–160 | 8400 후보 bounded; confidence 순서로 최대 300개 |
| 21 | xywh→xyxy → NMS → padding 제거 → 실제 rounded X/Y gain 각각으로 나눔 → 원본 경계 clip | `utils/nms.py`, 104–106; `models/yolo/detect/predict.py`, `construct_result`, 131; `utils/ops.py`, `scale_boxes`, 126–170, `clip_boxes`, 188–212 | `Exp02PostProcessor` 이후 `Exp02FrameTransform` 역변환. NMS 이전 clip 금지 |

원본 frame 기준은 CameraX의 회전 정규화된 분석 frame입니다. Preview crop/mirroring이나 MediaPipe 특정 인물과의 association을 위 transform이 해결했다고 해석하지 않습니다. 다중 인물의 `MULTI_PERSON_UNASSOCIATED` 제한을 유지합니다.

## 3. `predict` reference와 과거 `val` 구분

| 항목 | Static LiteRT predict reference | Historical validation 경로 |
|---|---|---|
| resize | LetterBox 안에서 rounded resized 크기 | `data/base.py:249–304`의 long-side resize에서 ceil 사용 후 letterbox |
| scaleup | LetterBox 기본 true | `data/dataset.py:341`의 LetterBox false. 앞선 load_image resize가 별도로 존재 |
| confidence default | 0.25 (`engine/predictor.py:155–156`) | 0.001 (`engine/validator.py:144–145`) |
| class 선택 | best class | multi-label true (`models/yolo/detect/val.py:168–178`) |
| color / scale | RGB BCHW float/255 | `Format._format_img` RGB CHW (`data/augment.py:2494–2520`), val float/255 (`models/yolo/detect/val.py:109`) |
| IoU / max_det | 0.7 / 300 | 0.7 / 300, dataset 등에 따른 별도 override는 실제 실행 설정 확인 필요 |

따라서 신규 Android golden은 **predict reference**와 비교하며, 과거 `val(split="test", imgsz=640)`의 모든 입력 pixel을 bitwise 재현한다고 주장하지 않습니다. Precision 0.9339 / Recall 0.8913 / mAP50 0.9383 / mAP50–95 0.8652는 사용자 제공 과거 실행 기록이며 이번 Android 결과가 아닙니다.

## 4. Android component·정책 경계

- `Exp02FrameTransform`: 원본 width/height, 실제 resized width/height, padding, 별도 gainX/gainY 보존. 8.4.176 `scale_boxes`의 rounding을 따릅니다.
- `Exp02RgbPreprocessor`: Bitmap adapter와 독립 pure Kotlin RGB 전처리. Direct native-order FLOAT32 BCHW tensor 생성.
- `Exp02OutputDecoder`: normalized center xywh를 모델 pixel의 typed candidate로 변환. 이 단계에서는 원본 clip/NMS를 하지 않습니다.
- `Exp02PostProcessor`: confidence filter, best-class selection, class-aware NMS, max_det, 역변환/clip을 담당합니다.
- `Exp02YoloContract`: 실제 세 모듈을 조립하지만 **NOT_VERIFIED 코드 gate**를 유지합니다. 보간 차이의 실제 모델 영향 미검증 때문에 `Verified`라는 이름을 붙이지 않았습니다. 향후 evidence가 충족될 때만 명시적 코드 리뷰로 승격할 수 있으며 flag/property로 해제할 수 없습니다. `UnverifiedYoloContract`도 유지합니다.

| Threshold | 값 | 분류 |
|---|---|---|
| Reference predict confidence | 0.25 | exact-version default, 앱 운영 최적값 주장 아님 |
| Reference val confidence | 0.001 | 과거 평가 경로의 기본값, 운영 threshold 아님 |
| Shadow raw admission | 0.10 | `APP_ENGINEERING_INITIAL`, 실기기 tuning 전 잠정값 |
| Stable vote confidence | 0.60 | `APP_ENGINEERING_INITIAL`, 낮은 confidence를 stable로 승격하지 않음 |
| Stabilizer | 최근 5개 중 4개 합의 | `ENGINEERING_INITIAL`, 실측 후 조정 대상 |
| NMS IoU / max_det | 0.7 / 300 | source reference |

Raw confidence 관찰과 stable threshold는 별개입니다. YOLO 결과가 있어도 `sessionExerciseType`이나 landmark `updateCurrentExerciseType`에 전달하지 않습니다. 4 FPS admission·single-flight·backlog 없음·MediaPipe 비대기 구조를 유지합니다.

## 5. Golden 검증과 허용오차

**Kotlin 합성 대조 통과 (2026-10-16).** `Exp02ContractTest` 39개와 identity/runtime/worker 회귀를 포함해 JVM 351개 전체가 통과했습니다. Python fixture 재생성 `--check`도 통과했습니다. 아래 한정된 interpolation 허용오차는 실제 모델 결과 동등성을 입증하지 않으므로 overall `NOT_VERIFIED`와 실제 prediction 비활성을 유지합니다. [전체 명령·결과](../YOLO_DEVICE_VALIDATION.md#현재-계약-구현-단계-실행-결과-2026-10-16)

1. Exact-version Python source를 사용한 RGB/layout/normalization/letterbox fixture와 Kotlin 결과 비교.
2. Portrait/landscape/square, up/down resize, odd padding, nonconstant synthetic color를 포함합니다. 단색·resize 없는 사례만으로 interpolation을 통과 처리하지 않습니다.
3. Padding·layout·normalization sample: absolute tolerance `1e-6` 이하.
4. Resampled uint8 pixel은 pure Kotlin bilinear와 OpenCV INTER_LINEAR의 정수 rounding 차이를 **최대 1 channel byte**, normalized `1/255 + 1e-6`로 제한합니다. 이보다 큰 차이는 실패입니다. 이 한정된 편차를 허용하는 것이지 full tensor hash/bitwise 동등성을 주장하는 것이 아닙니다.
5. Decoder synthetic tensor: 클래스·confidence·bbox 비교. 잘못된 shape, NaN/Inf, invalid confidence/box, 후보 여러 개를 포함합니다.
6. NMS fixture: 같은/다른 class overlap, strict threshold 경계, IoU 경계, no detection, max_det. Confidence·bbox는 작은 명시적 float 허용오차를 사용하며 선택 결과는 일치해야 합니다.
7. 동률 confidence의 NMS 순서는 구현에 따라 달라질 수 있으므로 핵심 parity fixture는 모호한 tie를 피합니다. 별도 deterministic tie 정책은 anchor index 순서로 문서화·테스트했습니다. 정상 범위 IoU는 reference Float 산술을 유지하며 두 개의 개별 유한 면적 합이 overflow되는 극단값만 Double fallback으로 처리합니다.
8. 모델 fixture가 가능하면 권리 확인된 synthetic/generated 입력만 사용합니다. AI-Hub 원본 이미지를 새로 commit하지 않습니다.

Interpolation golden이 위 한정 허용오차를 통과한 경우 그 결과는 **명시한 수치 허용오차 내 source-conformance**입니다. **전체 contract**가 bitwise OpenCV 동일하거나 Android native end-to-end inference까지 검증됐다는 뜻은 아닙니다. 1 byte 차이가 실제 관찰되면 그 차이를 숨기지 않고 tolerance와 함께 기록하며, 현재 단계의 전체 `NOT_VERIFIED`/실제 prediction 비활성을 자동 해제하지 않습니다. 실제 golden 미실행·불일치, 모델 identity/tensor mismatch 또는 핵심 export history 불확실성이 남으면 `NOT_VERIFIED`를 유지합니다.

## 6. Runtime·보안·배포 제한

- Python reference backend는 `ai-edge-litert>=2.1.4`를 요구합니다 (`nn/backends/litert.py:31`). Export는 `litert-torch>=0.9.0` 및 `ai-edge-litert>=2.1.4`를 사용합니다 (`utils/export/litert.py:63–66`). 이는 기존 Android TensorFlow Lite 2.14와의 호환 보증이 아닙니다.
- 실제 Android Interpreter가 실패하면 unsupported op/schema/runtime compatibility와 preprocessing 오류를 구분합니다. 자동 dependency 업그레이드·재-export·convert·quantize는 하지 않습니다.
- 모델 누락·초기화 실패·tensor mismatch·변환/추론/decoder/postprocessor 오류는 YOLO만 격리합니다. Landmark Primary·세션 잠금·회차 분석은 유지합니다.
- canonical 모델의 Git 제외와 APK loose-weight 제외 정책을 유지합니다. 모델을 assets에 복제하거나 네트워크에서 다운로드하지 않습니다. 로컬 DEBUG provisioning은 [검증 안내](../YOLO_DEVICE_VALIDATION.md)를 따릅니다.
- 모델 identity는 크기와 SHA256으로 native 생성 전에 확인합니다. 잘못된 파일은 `MODEL_IDENTITY_MISMATCH`, 알려진 unsupported op/schema 오류는 `UNSUPPORTED_RUNTIME`으로 구분하고 원본 native 오류 메시지/모델 description을 로그에 노출하지 않습니다.
- 공개 소스의 존재가 AI-Hub 학습 데이터/weight의 재배포 권리를 확정하지 않습니다. 이 문서는 법률적 배포 허가서가 아닙니다. 제3자 source는 AGPL 표시를 포함하므로 임의 vendoring하지 않습니다.
- 새 API key·cloud inference·image/frame 업로드·유료 서비스·원격 배포·billing 변경은 없습니다. 원본 image/landmark/userId/email/token을 DEBUG 로그에도 기록하지 않습니다.
- LLM은 작업 범위 밖이며 `AiCoachingPolicy.ENABLED=false`, `UnavailableLlmProvider`를 유지합니다.

## 7. 확인 source SHA256

아래는 exact wheel 내부 파일의 hash입니다. source 파일 자체는 공개 저장소에 추가하지 않습니다.

```text
9b29a5322293167a705a6d6054ef653a62d27f6e4bff589b242946df89b5b62f  engine/predictor.py
802333ea359def55e88f61d6726e35c29d28a6491110840a5e912ec1fafd1de3  data/augment.py
28a12efa45a220344bd85c66c71035305d306c07de5bbe659eb9a898c6f56537  nn/backends/litert.py
dad14c5c229bed662cd9760245c9c499d3b424cedfde3fa6385bddaa81aee8aa  utils/export/litert.py
304fd32c2ed2bd8927dd52b2dcec7abc9461fd1a4d9e67dffca39dad3fc6a67e  utils/export/engine.py
b471bb8a39cc1e38672c20feaa8c28bdf43739adb8eafd14226807b34b31f3ee  nn/modules/head.py
386cd0e3e38fddef5d434fbcfd58264bedfa37295534e9a2b7174a3436d4db62  utils/nms.py
c66d0eff4c6da3ba53247bc235de84bbe6db6682829ecaecd333c136070441cb  utils/ops.py
b91e4622e293270595e6e58d5bc3f3cdcb4a1d4f8b28c9035ec883bb2835081f  cfg/default.yaml
1711eaacf6f577d5277e0933a5437881e2c3ba833d13358d3dc4244424b5edc2  models/yolo/detect/predict.py
c9f738b513ba3e59f19fbc7e97028e4e68b70b4846c6b96a1611e3f65b3322d7  models/yolo/detect/val.py
4fa6e43d3b090e50c563ab904e7af095348e5f8471374048adb60eee66452111  engine/validator.py
ce0f840911b282b1a96e15188af95b14721f18ae6d8363f543d4220476000838  data/base.py
0a27c3981c946dab03a79e35583b867d69007153e0f53d5208e799cf49077404  data/dataset.py
```
