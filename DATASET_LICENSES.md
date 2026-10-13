# Dataset A / Dataset B 출처·라이선스

사용자 제공 실제 실험 이력과 공식 제공자 표시를 구분합니다. 이번에 학습/split을 재실행하거나 원본 데이터·외부 manifest를 내려받지 않았습니다. 원본 영상·이미지·전체 프레임·압축파일·학습 weight는 공개 저장소에 추가하지 않습니다.

## Dataset A — YOLO 운동 종류 탐지

| 항목 | 내용 |
|---|---|
| Dataset | `RickyRiccio/Real_Time_Exercise_Recognition_Dataset` |
| Provider | Hugging Face / RickyRiccio |
| 원본 URL | https://huggingface.co/datasets/RickyRiccio/Real_Time_Exercise_Recognition_Dataset |
| Provided license | CC BY-NC-SA 4.0, 제공자 dataset card 표시 확인 |
| Project usage | Dataset A / YOLO Exercise Detection / `similar_dataset` / SQUAT·SHOULDER_PRESS |
| EXP01 | SQUAT 영상 18개 + SHOULDER_PRESS 영상 18개, 총 36개; video-level split |
| EXP01 누수 | SQUAT GROUP_g02/g04/g06가 train + val에 중복 |
| EXP02 | 같은 Dataset A, group-aware train/val/test split 및 다른 운동/background negative 추가 |
| Git 공개 | 원본·전체 프레임·weight를 추가하지 않음; detector TFLite ignore 유지 |

사용 범위와 누수/개선은 사용자 제공 이력이며 이번에 원본 manifest로 독립 검증하지 않았습니다. EXP02의 구체적 영상/프레임/negative 수·split 비율은 미제공입니다. [실험 문서](docs/ML_EXPERIMENTS.md)에 제공값만 기록합니다.

### 라이선스·attribution·개별 원본

제공자 [dataset card](https://huggingface.co/datasets/RickyRiccio/Real_Time_Exercise_Recognition_Dataset)는 CC BY-NC-SA 4.0을 표시합니다. [라이선스 설명](https://creativecommons.org/licenses/by-nc-sa/4.0/)의 저작자 표시·비영리·동일조건변경허락 조건을 검토해야 합니다. 실제 사용 시 RickyRiccio/데이터셋명/원본 링크/라이선스 링크/변경 사항과 원본에 제공된 고지를 기록합니다.

제공자는 Kaggle Workout/Exercises, InfiniteRep, Pexels/Pixabay/Shutterstock 등 여러 외부 영상 소스를 언급합니다. card 표기만으로 개별 원본의 모든 권리·촬영/재배포 허가가 해결됐다고 판단하지 않습니다. 실제 `similar_dataset` 사용 파일의 제공처·권리·필수 고지와 negative의 출처/범위를 추가 확인해야 합니다.

**Trained-weight redistribution implications may require additional review.** 이 데이터로 학습한 weight가 CC BY-NC-SA 4.0의 Adapted Material 또는 ShareAlike 대상인지 법적 결론을 내리지 않습니다. Ultralytics AGPL 조건과 데이터 조건이 자동으로 동일/호환된다고 가정하지 않습니다. TFLite·PT·모델 포함 APK 공개는 보류합니다.

## Dataset B — AI-Hub 크로스핏 동작 데이터

| 항목 | 내용 |
|---|---|
| 제공처/데이터 | AI-Hub 크로스핏 동작 데이터, 사용자 제공 이력 |
| 목적 | Dataset B / pose sequence / pose/form / biomechanics 분석 |
| target 동작 | 에어스쿼트, 숄더프레스 |
| 작업 이력 | label 구조 검토, target label 추출, subject-like identifier 기반 split 설계, subject-disjoint manifest 생성 |
| YOLO 학습 사용 | **아니오. EXP01/EXP02 detector는 Dataset A 기반** |
| 정확한 ID/URL | 저장소에서 충분히 확인되지 않음 — 추가 확인 필요 |
| 라이선스/이용조건/attribution | 해당 데이터셋의 원문 조건·허가 증빙 확인 필요 |
| 공개 상태 | 원본 데이터 제외. 외부 manifest도 개인정보·경로·식별자 및 공개 조건 검토 전 자동 추가하지 않음 |

식별자 기반 subject-disjoint 설계는 실제 사람 신원/완전한 독립성을 자동 인증하지 않습니다. 외부 작업 이력과 로컬의 일반 `ai_module` 도구/빈 템플릿을 구분하며, 생성됐다고 보고된 외부 manifest의 파일 위치/내용은 이번에 검증하지 않았습니다.

## 저장소에 포함하는 범위

`ai_module/dataset/metadata/dataset_manifest.json`(samples 0)과 빈 sources 템플릿은 기존 도구 구조 설명용입니다. EXP01/EXP02 또는 AI-Hub 외부 실험의 원본 split manifest를 검증했다는 증거가 아닙니다. 신규 메타데이터도 검토 후 포함해야 하며 개인 계정·토큰·서명 URL·로컬 절대 경로를 넣지 않습니다.

원본 데이터를 직접 촬영한 경우에도 얼굴·신체·식별정보를 자동 공개하지 않습니다. Git 제외는 이미 추적된 파일/과거 이력을 지우지 않으므로 기존 metadata를 채울 때도 diff 검토가 필요합니다.
