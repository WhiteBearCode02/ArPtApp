# ArPt Dataset Pipeline

## 목적

기존 `data_preprocessor.py`의 프레임 CSV는 운동 분류 실험에는 유용하지만 원본, 사람, 구간, repetition 및 자세 오류를 추적할 수 없었습니다. v0.1.0 파이프라인은 모든 산출물을 `sample_id`로 연결하고, 자세 분석 학습 단위를 프레임이 아닌 repetition sequence로 저장합니다.

## Dataset A와 Dataset B

- Dataset A — Exercise Classification: `READY`, `SQUAT`, `SHOULDER_PRESS` 이미지/프레임 분류용입니다. 기존 `data_preprocessor` CLI가 source·frame·timestamp가 포함된 CSV를 스트리밍 방식으로 생성합니다.
- Dataset B — Exercise Form: `SQUAT`, `SHOULDER_PRESS`의 `(T, 33, 4)` raw/normalized pose, 관절 feature, repetition 경계와 자세 annotation을 저장합니다.

분류용 이미지 산출물은 `dataset/classification/<class>/`, 자세 sequence는 `dataset/pose/<exercise>/`로 물리적으로도 분리합니다. 생성된 대용량 이미지·NPZ 파일은 Git에서 제외되고 metadata와 annotation만 버전 관리됩니다.

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

기존 폴더 관례는 `data/READY`, `data/SQUAT`, `data/SHOULDER_PRESS`입니다. 이전의 `LUNGE` 입력은 현재 명시된 초기 schema에 포함되지 않아 더 이상 자동 처리하지 않습니다.

## Annotation과 pseudo labeling

스쿼트는 `KNEE_VALGUS`, `SHALLOW_DEPTH`, `FORWARD_LEAN`, `HEEL_LIFT`, 숄더프레스는 `INCOMPLETE_ROM`, `ASYMMETRIC_PRESS`, `EXCESSIVE_BACK_EXTENSION`, `ELBOW_ALIGNMENT_ERROR`를 허용합니다. v0.1.0 heuristic은 일부 후보만 생성하며 임상적 또는 전문가 판정을 대신하지 않습니다. 임계값은 `pipeline/config.py`에 모여 있습니다.

## 검증과 split

```bash
python -m ai_module.pipeline.dataset_validator --dataset-root ai_module/dataset
python -m ai_module.training.dataset_split --dataset-root ai_module/dataset --seed 42
python -m unittest discover -s ai_module/tests -v
```

validator는 ID·label·시간·annotation·pose 파일·shape·NaN·visibility·참조 불일치를 검사합니다. split은 `subject_id` 단위로 사람을 격리하며, ID가 없으면 경고 후 sample 단위 fallback을 사용합니다.

## YouTube source 정책

YouTube metadata는 `video_id`, URL, timestamp 등의 reference 정보만 저장합니다. YouTube 영상 파일을 자동 다운로드하거나 우회 다운로드하는 기능은 본 파이프라인에 포함하지 않습니다. 실제 학습 원본은 사용 권한이 명확한 영상, 직접 촬영 영상 또는 사용 가능한 공개 데이터셋을 전제로 합니다.
