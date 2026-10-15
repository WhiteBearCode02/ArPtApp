# YOLO Exercise Detection 실험 이력

## 근거와 범위

이 문서는 사용자가 제공한 실제 실험 이력을 정리합니다. 이번 문서 정리에서 학습·평가를 재실행하거나 외부 실험 로그/manifest를 독립적으로 재검증하지 않았습니다. 로컬 파일 존재·자산 해시·코드 호출 경로 확인은 실험 수치의 재현 검증과 구분합니다.

## 1. Dataset A — 운동 종류 탐지

- 데이터: `RickyRiccio/Real_Time_Exercise_Recognition_Dataset`, 실제 사용 부분 `similar_dataset`.
- 목적: Ultralytics YOLO26n **object detection**, `0 = SQUAT`, `1 = SHOULDER_PRESS`.
- `READY`는 detector class가 아닙니다. `UNKNOWN`도 학습 class가 아니라 탐지 없음·임계값 미달·실패 시 application logic의 상태입니다.
- 원본 영상·이미지·전체 프레임·학습 weight는 공개 저장소에 추가하지 않습니다.
- 제공자 표시 라이선스와 재배포 검토는 [DATASET_LICENSES.md](../DATASET_LICENSES.md)를 참조합니다.

## 2. EXP01 — 초기 baseline

`similar_dataset`의 SQUAT 영상 18개, SHOULDER_PRESS 영상 18개, 총 36개 영상을 사용했습니다. 초기 분할은 video-level split입니다. 이후 다음 group leakage가 발견됐습니다.

| 발견된 그룹 | 중복 분할 |
|---|---|
| SQUAT GROUP_g02 | train + val |
| SQUAT GROUP_g04 | train + val |
| SQUAT GROUP_g06 | train + val |

서로 다른 영상으로 나눴더라도 관련 그룹이 동시에 포함돼 평가/모델 선택이 낙관적일 수 있습니다. 확인된 누수는 위 train/val 중복이며, 별도 증거 없이 추가 누수 그룹/분할을 만들지 않습니다. EXP01은 baseline 이력으로 보존하고 최종 generalization 성능으로 과도하게 해석하지 않습니다.

## 3. EXP02 — 더 엄격한 평가 조건

같은 Dataset A를 사용하되 group-aware split으로 train/val/test 간 group leakage를 제거하고 다른 운동·background negative sample을 추가했습니다. 이 제거 이력은 사용자 제공 정보이며 split manifest 재검사는 이번 작업에 포함되지 않습니다.

EXP01보다 낮은 지표를 실패로 단정하지 않습니다. 분할 독립성과 negative 조건이 강화되어 평가가 어려워졌으며, 데이터 규모/구성·라벨 노이즈도 함께 고려해야 합니다. EXP02의 영상 수·프레임 수·group 수·split 비율·negative 수는 미제공이므로 EXP01의 36개 수치를 그대로 적용하지 않습니다.

group-disjoint 분할을 실제 사람의 완전한 subject-disjoint 또는 외부 분포 검증과 동일하게 취급하지 않습니다.

## 4. 사용자 제공 test 결과

| 지표 | EXP01 test | EXP02 PT test | EXP02 LiteRT FP32 test |
|---|---:|---:|---:|
| Precision | 0.9929 | 0.9557 | 0.9339 |
| Recall | 0.9996 | 0.9193 | 0.8913 |
| F1 | 0.9962 | 0.9372 | 미제공 |
| mAP50 | 0.9950 | 0.9440 | 0.9383 |
| mAP50-95 | 0.9562 | 0.8482 | 0.8652 |
| SQUAT mAP50-95 | 0.9678 | 0.8262 | 0.829 |
| SHOULDER_PRESS mAP50-95 | 0.9446 | 0.8703 | 0.902 |

특정 test dataset 기준의 제공값입니다. 미제공 LiteRT F1을 계산하여 실험 결과처럼 추가하지 않습니다. PT/LiteRT 차이는 전처리·출력 해석·평가 설정을 추가 비교해야 하며, 수치만으로 export가 성능을 향상시켰다고 인과적으로 결론 내리지 않습니다. 어떤 값도 실제 모바일 사용자 정확도와 동일하지 않습니다.

## 5. LiteRT FP32 export와 Android 상태

- 제공 모델: `airptcoach_yolo26n_exp02_fp32.tflite`.
- 입력: FLOAT32 `[1, 3, 640, 640]`.
- 출력: FLOAT32 `[1, 6, 8400]`.
- export 완료 이력과 로컬 FlatBuffer 구조 검사 완료를 **Android integration 완료**와 구분합니다.
- RGB/BGR, normalization, bbox format, xywh/xyxy, class score 의미, NMS 포함 여부는 shape만으로 확정하지 않습니다.
- 실제 export pipeline 및 Interpreter/inference verification은 필요합니다. 2026-10-16에는 Android Shadow 기반 구조·tensor 검사·실패 격리를 준비했으나, 원본 export/test 계약 미확인으로 실제 prediction은 비활성입니다.
- 모델은 Git 제외 상태를 유지합니다. 현재 Gradle은 debug/release 모두 loose weight assets를 제외합니다. 모델 없는 debug APK 검증과 모델 포함/공개 재배포 승인은 구분합니다.
- 실제 파일 크기·해시·공개 조건은 [MODEL_CARD.md](../MODEL_CARD.md)에 기록합니다.

## 6. Dataset B — 자세/pose/biomechanics

AI-Hub **크로스핏 동작 데이터**는 Dataset B입니다. 검토 대상 동작은 에어스쿼트·숄더프레스입니다. label 구조 검토, target label 추출, subject-like identifier 기반 split 설계 및 subject-disjoint manifest 생성 이력이 있습니다.

이 이력은 사용자가 제공한 외부 실험 내용이며 로컬의 일반 `ai_module` 도구/빈 manifest 템플릿만으로 실험 파일 검증 완료를 주장하지 않습니다. 식별자 기준 분할은 실제 사람 신원/완전한 독립성을 자동으로 인증하지 않습니다.

**Dataset B는 YOLO EXP01/EXP02 detector 학습 데이터가 아닙니다.** 정확한 AI-Hub ID/URL/이용조건과 manifest 공개 가능성은 추가 확인이 필요합니다.

## 7. 한계와 향후 평가

- 데이터 규모가 작고 target exercise가 SQUAT/SHOULDER_PRESS 두 개로 제한됩니다.
- pseudo bbox label 사용 및 video label의 frame 전파가 있어, transition/neutral frame의 label noise 가능성이 있습니다.
- Dataset A의 출처 다양성이 제한됩니다.
- real-world smartphone validation과 mobile FPS 측정은 미완료입니다.
- background false positive의 별도 정량 평가가 필요합니다.
- external subject-disjoint test는 미완료입니다.
- export 전후 전처리/후처리 일치, target별 오탐·미탐, 카메라 방향·조명·가림·기기별 추론 시간/발열을 후속 평가합니다.
- 실제 측정 전에는 모바일 FPS·추론 시간·실제 사용자 정확도를 임의로 추가하지 않습니다.

## 8. 다음 단계와 공개 gate

문서 정리는 모델 재배포 허가가 아닙니다. Dataset A 원본별 권리/attribution과 trained-weight redistribution, export 설정/추론 검증, 자산 고지 및 전체 프로젝트 LICENSE 검토가 남아 있습니다. 사용자 후속 승인 범위의 DEBUG Shadow 기반 구조는 [YOLO_DEVICE_VALIDATION.md](YOLO_DEVICE_VALIDATION.md)에 별도 기록합니다. 실제 preprocessing/postprocessing·NMS·prediction·primary 전환은 하지 않았고 모델 자산은 유지했습니다.
