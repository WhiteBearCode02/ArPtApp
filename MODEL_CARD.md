# AIRPTCoach YOLO26n Exercise Detection 모델 카드

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
| Android 상태 | 🚧 통합 전 / pending license review. 현재 앱은 MediaPipe landmark fallback 사용 |

Tensor 정보는 [공식 FlatBuffer schema](https://github.com/tensorflow/tensorflow/blob/v2.14.0/tensorflow/lite/schema/schema.fbs)에 따른 파일 검사 결과입니다. Interpreter 실행·전처리/후처리·카메라 integration 검증은 아직 하지 않았습니다. **shape만으로 RGB/BGR, normalization, bbox format, xywh/xyxy, class score 의미, NMS 포함 여부를 확정하지 않습니다.** 실제 export pipeline과 inference verification이 필요합니다.

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

모델의 Git 공개·새 APK 포함/배포는 보류합니다. `.gitignore`는 APK에서 모델을 제외하거나 재배포 허가를 주지 않습니다. 현재 파일이 `assets`에 남아 있어 향후 기본 빌드는 이를 포함할 수 있지만, 이번에는 앱/Gradle/자산을 변경하거나 APK를 빌드하지 않습니다.

공개 전 Dataset A 원본별 권리·attribution·weight 재배포 조건, export pipeline, 모델 metadata의 민감정보, 기존 자산의 고지와 프로젝트 LICENSE를 검토하고 사용자 승인을 받아야 합니다.
