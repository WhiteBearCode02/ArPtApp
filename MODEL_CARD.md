# AIRPTCoach YOLO26n Exercise Detection 모델 카드

## 현재 canonical Shadow 모델: EXP03_FINAL (2026-11-05)

`yolo26n_exp03_final_fp32` · `airptcoach_yolo26n_exp03_final_fp32.tflite` · FP32.
실제 파일 **9,894,032 bytes**, SHA256
`3d2b6bc537d8aef5a19b671226d73c98b49411574a193be72e225086202d1167`를 직접 확인했습니다.
입력 FLOAT32 `[1,3,640,640]`, 출력 FLOAT32 `[1,6,8400]`; 사용자 확정 class0 SQUAT/class1 SHOULDER_PRESS.

사용자 제공 Subject-held-out Test 2,692장 결과(이번에 재평가하지 않음):

| metric | PyTorch | FP32 TFLite |
|---|---:|---:|
| Precision | 0.9213 | 0.9144 |
| Recall | 0.9369 | 0.9296 |
| F1 | 0.9290 | 0.9219 |
| mAP50 | 0.9551 | 0.9486 |
| mAP50-95 | 0.8014 | 0.7936 |

제공된 conversion delta: F1 -0.007145 / mAP50-95 -0.007830.
대표12장 PT↔TFLite class12/12, confidence 평균 차이0.00853748, bbox 평균 IoU0.99399749도 사용자 제공 값입니다.
PC LiteRT2.2.0 합성 입력 invoke1회와 **실제 Android TF Lite2.14.0 load/metadata/native smoke PASS**를
확인했습니다. Android instrumentation 2/2 PASS, 단색 합성 입력 invoke1회이며 image Golden은 아닙니다.
**contract VERIFIED / actual device PASS (actual-image Golden) / camera MEASURED_LIMITED**입니다.
스쿼트 정면·측면 2세션의 실시간 추론·로컬 저장은 확인했지만, stable 판정은 모두 UNKNOWN입니다.
모델 전체 정확도나 실사용 품질 통과로 해석하지 않습니다. [Camera baseline](docs/ml/EXP03_CAMERA_SHADOW_BASELINE.md).
Run 1의 FAIL(1/4)은 보존했고, OpenCV4.13.0 uint8 보간을 pure Kotlin으로 재현한 Run 2는
전체4/4 tensor SHA 동일·실제 inference/decoder/NMS PASS였습니다. confidence 허용치를 넓히지
않았고 최대 차이3.5762787e-7 / bbox9.1552734e-5px를 확인한 뒤에만 승격했습니다.
DEBUG gate ON + READY일 때 prediction 허용, release OFF / Landmark Primary는 유지합니다.
[실제 검증 결과](docs/ml/EXP03_ACTUAL_GOLDEN_VALIDATION.md) · [보간 수정/Run 2](docs/ml/EXP03_PREPROCESSING_PARITY.md).
기존 EXP02의 VERIFIED를 상속하지 않습니다.
정확한 EXP03 학습·export 원문과 권리 확인을 기존 EXP02 기록에서 추정하지 않습니다.

Landmark Primary / release Shadow OFF / 모델 Git·APK 제외를 유지합니다.
현재 파일은 로컬 provisioning 원본이며 재배포 승인본이 아닙니다. 원본·기존 증거를 삭제/교체하지 않았습니다.
[EXP03 계약](docs/ml/EXP03_ANDROID_INFERENCE_CONTRACT.md) · [rollout](docs/ml/EXP03_RUNTIME_SHADOW_ROLLOUT.md) ·
[실기기 확인](docs/ml/EXP03_ANDROID_DEVICE_SHADOW_CHECKLIST.md).

---

아래 EXP01/EXP02 내용은 기존 작성·검증 단계의 역사 기록입니다. EXP03의 근거로 소급 변경하지 않습니다.

## 목적·실험 근거

졸업작품·졸업논문·취업 포트폴리오를 위한 비상업적 공개 연구 프로젝트의 운동 종류 **object detection** 모델입니다. Ultralytics YOLO26n을 사용자가 직접 학습한 EXP01/EXP02 이력에 따라 기록합니다. 수치는 사용자 제공 test 결과이며 이번 작업에서 학습·평가를 재실행하지 않았습니다. 상용/폐쇄형 배포에는 별도 검토가 필요합니다.

## 모델과 현재 자산

| 항목 | 내용 |
|---|---|
| Model/task | Ultralytics YOLO26n object detection / YOLO Exercise Detection |
| Classes | `0 = SQUAT`, `1 = SHOULDER_PRESS` |
| READY / UNKNOWN | READY는 YOLO class 아님. UNKNOWN은 탐지 없음·confidence 미달·실패 시 application logic 상태이며 별도 학습 class 아님 |
| 원본 형식 | PyTorch, 사용자 제공 체크포인트명 `AIRPTCoach_EXP02_YOLO26n_best.pt`; 원본 미검사 |
| export 형식 | LiteRT / TFLite FP32 |
| 로컬 파일 | `app/src/main/assets/airptcoach_yolo26n_exp02_fp32.tflite` |
| 파일 크기 | 9,894,030 bytes, 약 9.44 MiB |
| SHA-256 | `464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749` |
| 입력 | FLOAT32 `[1, 3, 640, 640]`, tensor index 0 |
| 출력 | FLOAT32 `[1, 6, 8400]`, tensor index 658 |
| FlatBuffer 검사 | TFL3 schema version 3, subgraph 1개, operator 414개; 이전 실제 파일 검사 이력, 이번 존재/해시 재확인 |
| Git 상태 | 파일 존재, 미추적·ignore 유지. 강제 stage/commit/push하지 않음 |
| Android 상태 | 🚧 exact-source 기반 전처리·decoder·NMS 후보 구현. 전체 contract `NOT_VERIFIED`, 실제 Shadow prediction 비활성. 기존 landmark primary 유지 |

Tensor 정보는 [공식 FlatBuffer schema](https://github.com/tensorflow/tensorflow/blob/v2.14.0/tensorflow/lite/schema/schema.fbs)에 따른 파일 검사 결과이며 Android Interpreter 실행 증거가 아닙니다. **shape만으로 RGB/BGR, normalization, bbox format, xywh/xyxy, class score 의미, NMS 포함 여부를 확정하지 않습니다.** 이번에는 아래의 notebook·exact source·artifact metadata를 추가 대조했습니다. 실제 모델 golden·Android native inference·카메라 integration 검증은 아직 별개로 남아 있습니다.

## EXP02 inference contract 진행 상태

- Canonical reference: [AIRPTCoach_EXP02_Export_Verify.ipynb](docs/ml/AIRPTCoach_EXP02_Export_Verify.ipynb), 13개 cell 전체 확인. 이번 작업에서 export/copy/validation을 실행하지 않았습니다.
- Exact source: **Ultralytics 8.4.176** PyPI wheel을 별도로 확인했습니다. 앱 dependency를 올리거나 다른 버전 source로 대체하지 않았습니다.
- 모델 내 whitelist metadata: version `8.4.176`, task/head `detect/Detect`, batch `1`, imgsz `[640,640]`, names `SQUAT/SHOULDER_PRESS`, `end2end=false`, `quantize=null`.
- Source-backed 계약: logical RGB · float BCHW `/255` · centered letterbox/114 padding · normalized center xywh · 이미 sigmoid된 class score · external class-aware NMS. 21개 항목의 함수/line/hash는 [inference contract 문서](docs/ml/EXP02_ANDROID_INFERENCE_CONTRACT.md)에 기록했습니다.
- 구현 준비: `Exp02RgbPreprocessor`·`Exp02FrameTransform`·`Exp02OutputDecoder`·`Exp02PostProcessor`와 synthetic Python golden fixture. Decoder는 모델 pixel 후보를 만들며 NMS 후 별도 X/Y gain으로 역변환·clip합니다.
- Measured local validation: JVM 351개(합성 contract/Golden 39개 포함), Node 63개와 debug APK build/패키징 검사가 통과했습니다. Python reference 재생성도 통과했습니다. 이는 모델 inference/실기기 accuracy 측정이 아닙니다. [실행 기록](docs/YOLO_DEVICE_VALIDATION.md#현재-계약-구현-단계-실행-결과-2026-10-16)
- **전체 검증은 `NOT_VERIFIED`**: 독립 Kotlin bilinear와 OpenCV uint8 resize의 최대 1-byte 차이는 단순 float 오차가 아닙니다. 실제 모델 비교와 Android native/device 검증 없이 `Exp02YoloContract`를 승격하지 않습니다. Fixture 생성은 Kotlin 테스트 통과나 실제 prediction 성공과 동의어가 아닙니다.

실시간 앱 비교 기준은 **고정 입력 LiteRT predict 경로**입니다. 과거 `val`은 resize rounding·multi-label·confidence 기본값이 다릅니다. Reference predict confidence 0.25/val 0.001과 별개로 Shadow raw 0.10/stable 0.60은 `APP_ENGINEERING_INITIAL` 잠정값입니다. Threshold를 실기기 최적값으로 주장하지 않습니다.

## Dataset A / Dataset B

- **Dataset A**: `RickyRiccio/Real_Time_Exercise_Recognition_Dataset`의 `similar_dataset`, YOLO 운동 탐지용 SQUAT/SHOULDER_PRESS. EXP01/EXP02의 실제 학습 데이터입니다.
- **Dataset B**: AI-Hub 크로스핏 동작 데이터, 에어스쿼트/숄더프레스 label 및 pose/form/biomechanics 분석용입니다. label 검토·target label 추출·subject-like ID 분할 설계·subject-disjoint manifest 생성 이력이 있으며 **EXP02 detector 학습에 사용하지 않았습니다.**
- Dataset B의 정확한 ID/URL/이용조건, Dataset A의 EXP02 규모·negative 수와 일부 원본 권리 증빙은 추가 확인이 필요합니다.
- [데이터 출처/라이선스](DATASET_LICENSES.md), [실험 상세](docs/ML_EXPERIMENTS.md)를 참조합니다.

## EXP01 → EXP02

EXP01은 `similar_dataset` SQUAT 18개 + SHOULDER_PRESS 18개, 총 36개 영상의 video-level split baseline입니다. 이후 **SQUAT GROUP_g02 / GROUP_g04 / GROUP_g06의 train + val 중복**이 발견돼 평가가 낙관적일 수 있습니다. baseline으로 보존하되 최종 generalization 성능으로 과도하게 해석하지 않습니다.

EXP02는 같은 Dataset A의 group-aware split으로 train/val/test 간 group leakage를 제거하고 다른 운동/background negative를 추가했습니다. 더 엄격하고 어려운 평가 조건의 수치 하락을 실패로 단정하지 않습니다. 제거 이력은 사용자 제공 정보이며 이번에 split manifest를 재검증한 것은 아닙니다.

| test 지표 | EXP01 baseline | EXP02 PT | EXP02 LiteRT FP32 |
|---|---:|---:|---:|
| Precision | 0.9929 | 0.9557 | 0.9339 |
| Recall | 0.9996 | 0.9193 | 0.8913 |
| F1 | 0.9962 | 0.9372 | 미제공 |
| mAP50 | 0.9950 | 0.9440 | 0.9383 |
| mAP50-95 | 0.9562 | 0.8482 | 0.8652 |
| SQUAT mAP50-95 | 0.9678 | 0.8262 | 0.829 |
| SHOULDER_PRESS mAP50-95 | 0.9446 | 0.8703 | 0.902 |

특정 test dataset 기준의 제공값으로 실제 모바일 사용자 정확도가 아닙니다. PT/LiteRT 차이의 원인도 수치만으로 결론 내리지 않습니다.

## 제한과 개선 방향

데이터 규모·출처 다양성·target 2개 제한, pseudo bbox label, video label의 frame 전파, transition/neutral label noise 가능성이 있습니다. 실세계 스마트폰 검증·mobile FPS 측정·external subject-disjoint test는 미완료이고 background false positive 추가 평가가 필요합니다. 외부 독립 사람 데이터, bbox/전환 구간 라벨 검수, 기기별 추론 시간·발열·FPS 측정으로 개선합니다. 실측하지 않은 수치를 생성하지 않습니다.

## 라이선스와 공개 상태

Ultralytics [공식 안내](https://www.ultralytics.com/license)에 따라 직접 학습한 모델도 AGPL-3.0 영향 대상으로 관리합니다. Dataset A 제공자 표기는 CC BY-NC-SA 4.0이며, **Trained-weight redistribution implications may require additional review.** weight가 Adapted Material/ShareAlike 대상인지 법적 결론을 내리지 않습니다.

모델의 Git 공개·새 APK 포함/배포는 보류합니다. `.gitignore` 자체는 APK에서 모델을 제외하거나 재배포 허가를 주지 않습니다. 2026-10-15 보강된 Gradle 정책은 debug/release 모두 loose weight assets를 제외하며 이번 작업에서도 유지했습니다. 2026-10-16에는 DEBUG Shadow 경계와 exact-source 기반 전처리·decoder·NMS 후보를 준비했습니다. 모델 파일은 복사·삭제·이동·재-export하지 않았으며 전체 contract `NOT_VERIFIED`로 실제 prediction은 비활성입니다. [개발 검증 상태](docs/YOLO_DEVICE_VALIDATION.md)

공개 전 Dataset A 원본별 권리·attribution·weight 재배포 조건, export pipeline, 모델 metadata의 민감정보, 기존 자산의 고지와 프로젝트 LICENSE를 검토하고 사용자 승인을 받아야 합니다.
