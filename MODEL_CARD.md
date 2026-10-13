# AIRPTCoach EXP02 운동 탐지 모델

## 목적과 출처

사용자가 직접 학습했다고 제공한 Ultralytics YOLO26n 기반 모델입니다. 졸업작품·졸업논문·취업 포트폴리오의 운동 종류 탐지를 목적으로 합니다. 비상업적 공개 연구 프로젝트이며, 상용화/폐쇄형 배포는 별도 검토 대상입니다.

| 항목 | 내용 |
|---|---|
| 원본 체크포인트 | `AIRPTCoach_EXP02_YOLO26n_best.pt` — 이름은 사용자 제공, 저장소에서 원본 미검사 |
| 로컬 모델 | `app/src/main/assets/airptcoach_yolo26n_exp02_fp32.tflite` |
| 형식 | LiteRT / TFLite FP32, TFL3 schema version 3 |
| 파일 크기 | 9,894,030 bytes, 약 9.44 MiB |
| SHA-256 | `464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749` |
| 클래스 | 사용자 제공: `0 = SQUAT`, `1 = SHOULDER_PRESS` |
| 입력 | 실제 FlatBuffer 검사: tensor index 0, FLOAT32 `[1, 3, 640, 640]` |
| 출력 | 실제 FlatBuffer 검사: tensor index 658, FLOAT32 `[1, 6, 8400]` |
| 그래프 | subgraph 1개, operator 414개 |
| Git 추적 | 제외 상태 유지. 승인 없이 모델·체크포인트를 stage/commit/push하지 않음 |
| Android 통합 | 라이선스/학습 출처 검토 때문에 보류. 현재 모델을 실행한다고 주장하지 않음 |

검사는 TensorFlow의 [공식 FlatBuffer schema](https://github.com/tensorflow/tensorflow/blob/v2.14.0/tensorflow/lite/schema/schema.fbs)에 따라 실제 파일을 읽었습니다. Interpreter 초기화/추론 검증은 아직 수행하지 않았습니다. 입력은 채널 우선 형태로 관찰되지만 RGB 순서·정규화·출력 필드 의미·좌표 단위·NMS 포함 여부를 shape만으로 확정하지 않습니다. export 설정과 실제 추론 결과를 추가 확인해야 합니다.

## 사용자 제공 EXP02 LiteRT 평가

| 지표 | 값 |
|---|---:|
| Precision | 0.9339 |
| Recall | 0.8913 |
| mAP50 | 0.9383 |
| mAP50-95 | 0.8652 |
| SQUAT mAP50-95 | 0.829 |
| SHOULDER_PRESS mAP50-95 | 0.902 |

특정 보유 test dataset 기준의 사용자 제공 수치이며 이번 작업에서 재평가하지 않았습니다. 실제 스마트폰·사용자·조명·촬영 방향·가림 환경의 최종 정확도가 아닙니다. 모델 유형/학습 버전도 체크포인트와 export provenance를 추가 확인해야 합니다.

## 학습 데이터와 라이선스

- 사용자가 positive 및 다른 운동/background negative를 포함했다고 설명했습니다.
- 사용자는 AI-Hub 데이터로 학습했다고 확인했습니다. 정확한 데이터셋명/ID/URL, 출처별 사용량·split·원본 권리·attribution 목록은 미제공입니다.
- `RickyRiccio/Real_Time_Exercise_Recognition_Dataset`은 기존 검토 후보였으며 실제 사용 데이터로 확정하지 않습니다. AI-Hub 사용 설명과 구분하여 기록합니다. 상세는 [DATASET_LICENSES.md](DATASET_LICENSES.md)를 참조합니다.
- Ultralytics 공식 안내에 따라 직접 학습한 모델도 AGPL-3.0 영향을 받는 것으로 보수적으로 관리합니다. [공식 안내](https://www.ultralytics.com/license)
- 데이터셋 조건이 학습 weight 재배포에 미치는 영향은 추가 검토가 필요합니다. 소스 공개 의사만으로 모델 재배포 권리가 확정되지는 않습니다.
- 모델은 Git에서 제외하지만 로컬 `assets`의 파일은 APK에 포함될 수 있습니다. `.gitignore`는 APK 배포 허가나 라이선스 예외가 아닙니다.

## 공개 전 확인 목록

1. 실제 학습 데이터 출처/사용 범위/라이선스/필수 고지 확정.
2. 학습 weight·APK 재배포 가능성 확인.
3. 기존 앱 dependency와 AGPL 조건의 관계 검토.
4. export 버전/설정 및 Interpreter 실제 텐서·추론 검증.
5. 모델 metadata의 개인정보·로컬 경로·계정·키 포함 여부 검사.
6. 공개 승인 후 source/모델/notice 제공 방식 결정. 자동 commit/push 금지.
