# ArPt Dataset Pipeline

## 목적

기존 `data_preprocessor.py`의 프레임 CSV는 운동 분류 실험에는 유용하지만 원본, 사람, 구간, repetition 및 자세 오류를 추적할 수 없었습니다. v0.1.0 파이프라인은 모든 산출물을 `sample_id`로 연결하고, 자세 분석 학습 단위를 프레임이 아닌 repetition sequence로 저장합니다.

## Dataset A와 Dataset B

- 실제 Dataset A — YOLO Exercise Detection: RickyRiccio/Real_Time_Exercise_Recognition_Dataset의 similar_dataset으로 EXP01/EXP02를 학습했습니다. detector class는 SQUAT/SHOULDER_PRESS 두 개이며 READY/UNKNOWN은 학습 class가 아닙니다.
- 실제 Dataset B — AI-Hub 크로스핏 동작 데이터: 에어스쿼트/숄더프레스 label 검토·target 추출·subject-like ID split 설계·subject-disjoint manifest 생성 이력이 있습니다. pose/form/biomechanics 목적이며 YOLO EXP02 학습에 사용하지 않았습니다.
- 이 문서의 로컬 도구는 일반 `(T, 33, 4)` raw/normalized pose, 관절 feature, repetition 경계와 자세 annotation을 저장하는 v0.1.0 파이프라인입니다. 외부 detector 실험/AI-Hub manifest를 재검증하거나 재현한 것으로 취급하지 않습니다. [실험 이력](ML_EXPERIMENTS.md)을 참조합니다.

레거시 도구의 이미지 산출물 경로는 `dataset/classification/<class>/`, 자세 sequence 경로는 `dataset/pose/<exercise>/`입니다. 폴더명이 classification이어도 현재 YOLO 실험은 object detection입니다. 원본·대용량 이미지·NPZ는 Git 제외하며 metadata/annotation도 공개 검토 후에만 포함합니다. 현재 .gitignore는 검토한 빈 metadata 템플릿만 명시적으로 허용합니다.

운동명, 오류명, 촬영 방향, 심각도 및 annotation 출처는 `pipeline/schema.py`의 enum으로 관리합니다. pseudo label의 `AUTO`는 검수된 `HUMAN` 또는 `AUTO_VERIFIED`와 구분됩니다.

## 데이터 흐름과 저장 구조

```text
source metadata -> streamed frames -> MediaPipe (33, 4)
 -> hip-centered normalization -> joint features -> exercise strategy segmenter
 -> compressed repetition NPZ + feature JSON -> pseudo label -> sample metadata -> manifest
```

`metadata/samples/<sample_id>.json`은 source, optional `subject_id`, source segment, exercise, view, annotation 및 repetition 파일을 연결합니다. 각 NPZ는 frame index와 timestamp를 포함하며 설정에 따라 `raw_pose`, `normalized_pose`를 함께 저장합니다. 전체 영상을 메모리에 올리지 않고 제한된 현재 repetition 버퍼만 사용합니다.

## 생성 방법

프로젝트 루트에서 의존성을 설치한 뒤 실행합니다.

Python 3.10 이상이 필요합니다.

```bash
python -m pip install -r ai_module/requirements.txt
python -m ai_module.pipeline.dataset_builder --video ./data/test_squat.mp4 --exercise SQUAT --source-type LOCAL --view SIDE --subject-id person_001
```

레거시 프레임 CSV:

```bash
python -m ai_module.data_preprocessor --data ./data --output raw_data.csv
```

레거시 CLI의 폴더 관례는 `data/READY`, `data/SQUAT`, `data/SHOULDER_PRESS`입니다. 이 호환 schema는 YOLO26n detector class 계약과 별개이며 이번 문서 정리에서 코드를 변경하지 않았습니다. 이전의 `LUNGE` 입력은 해당 초기 schema에 포함되지 않아 자동 처리하지 않습니다.

## Annotation과 pseudo labeling

스쿼트는 `KNEE_VALGUS`, `SHALLOW_DEPTH`, `FORWARD_LEAN`, `HEEL_LIFT`, 숄더프레스는 `INCOMPLETE_ROM`, `ASYMMETRIC_PRESS`, `EXCESSIVE_BACK_EXTENSION`, `ELBOW_ALIGNMENT_ERROR`를 허용합니다. v0.1.0 heuristic은 일부 후보만 생성하며 임상적 또는 전문가 판정을 대신하지 않습니다. 임계값은 `pipeline/config.py`에 모여 있습니다.

## 검증과 split

```bash
python -m ai_module.pipeline.dataset_validator --dataset-root ai_module/dataset
python -m ai_module.training.dataset_split --dataset-root ai_module/dataset --seed 42
python -m unittest discover -s ai_module/tests -v
```

validator는 ID·label·시간·annotation·pose 파일·shape·NaN·visibility·참조 불일치를 검사합니다. split은 `subject_id` 단위로 사람을 격리하며, ID가 없으면 경고 후 sample 단위 fallback을 사용합니다.

### 영상 의존성 없이 테스트하기

프로젝트 루트의 전용 가상환경을 사용하면 전역 Python 패키지를 변경하지 않고 재실행할 수 있습니다. 단위·통합 테스트에는 NumPy만 필요합니다.

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r ai_module/requirements-test.txt
.\.venv\Scripts\python.exe -m unittest discover -s ai_module/tests -v
.\.venv\Scripts\python.exe -m ai_module.pipeline.dataset_validator --dataset-root ai_module/dataset
```

2026-10-02 Python 3.11.9 / NumPy 2.4.6에서 10개 테스트가 모두 통과했습니다. 통합 테스트는 임시 합성 샘플 12개를 이용하여 NPZ/metadata 검증, manifest 생성, 실제 split CLI, 사람별 격리와 동일 seed 재현성을 확인합니다. 잘못된 pose shape 및 운동에 맞지 않는 오류 라벨도 거부하는지 검사합니다.

현재 저장소에는 실제 sample metadata가 없어 validator가 `dataset contains no sample metadata` 경고를 출력합니다. 이를 실제 데이터 품질 검증 완료로 해석하지 않습니다. 영상 추출에는 기존 `requirements.txt`의 MediaPipe/OpenCV와 사용 권한이 있는 원본 영상이 별도로 필요합니다.

## YouTube source 정책

YouTube metadata는 `video_id`, URL, timestamp 등의 reference 정보만 저장합니다. YouTube 영상 파일을 자동 다운로드하거나 우회 다운로드하는 기능은 본 파이프라인에 포함하지 않습니다. 실제 학습 원본은 사용 권한이 명확한 영상, 직접 촬영 영상 또는 사용 가능한 공개 데이터셋을 전제로 합니다.
