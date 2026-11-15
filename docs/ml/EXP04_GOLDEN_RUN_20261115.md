# EXP04 실제 입력 Golden — PC reference 실행 기록

날짜: **2026-11-15**, 브랜치 `ai-v2`, run **`exp04_actual_01`**.

**실제 Python/LiteRT 이미지 추론 7회와 private bundle 검사는 PASS했습니다.** 최초 기기 목록 조회는 0대였지만 사용자 재연결 후 SM-S908N에서 Android native·7장 전처리 동등성·actual Golden도 PASS했습니다. 각 Android 근거는 [별도 실기기 보고서](EXP04_ANDROID_GOLDEN_RUN_20261115.md)에 있습니다. `Exp04YoloContract`는 **NOT_VERIFIED**, Landmark Primary 유지이며 실제 camera 검증은 **NOT_RUN**입니다.

이 문서는 [최초 환경 차단 기록](EXP04_ACTUAL_GOLDEN_VALIDATION.md)과 [Pillow 한정 복구 기록](EXP04_ENVIRONMENT_REPAIR.md)의 후속 실행입니다. 이전 실패·부분 기록은 삭제하거나 성공 결과로 덮어쓰지 않았습니다.

## 1. 환경 경고와 사용자가 승인한 재개 범위

지정 `local_validation/.venv/Scripts/python.exe`에서 Python **3.11.9**를 사용했습니다. Pillow 13.0.0만 복구한 이후의 실제 core import 확인은 다음과 같습니다.

| 항목 | 실제 확인 |
|---|---|
| NumPy | 2.4.6 import PASS |
| OpenCV | `cv2` 4.13.0 import PASS; distribution `opencv-python-headless` 4.13.0.92 |
| Torch | 2.9.1+cpu import PASS |
| Pillow | 13.0.0, `PIL.Image` import PASS |
| LiteRT | `ai-edge-litert` 2.2.0 / `ai_edge_litert.interpreter.Interpreter` import PASS; 아래 실제 모델 invoke 수행 |

이전 `pip check`의 **exit 1과 누락 의존성 10개 경고는 해소되지 않았으며 이번 재조회에서도 exit 1**입니다. 사용자가 그 검사에서 exit 0이어야 한다는 실행 게이트만 명시적으로 해제하고, 추가 패키지 설치 없이 core runtime·exact source·실제 입력을 단계별 검증하도록 승인했습니다. 환경 전체가 정상화됐거나 `pip check`가 PASS했다고 기록하지 않습니다. 실제 현재 경고는 ignored `validation_resume_20261115_01/pip-check-current.log`에도 남겼습니다.

설치된 Ultralytics distribution 8.4.176은 이번 reference 코드로 import하지 않았습니다. 기존 환경의 metadata 불완전성과, 아래의 exact 8.4.202 source 실행 근거는 서로 다른 항목입니다. 실패 경고를 숨기거나 검증 정책·threshold를 수정해 통과시킨 것이 아닙니다.

## 2. 실제 입력 사전 검사

공식 `--check-inputs`를 **이번 시도에서 재실행하여 exit 0**을 확인한 뒤, 기존 local-only `preflight_20261115.py`의 추가 검사를 실행해 **7장 모두 PASS**했습니다.

| Expected | 방향 | 해상도 | 원본 encoded bytes |
|---|---|---:|---:|
| SQUAT | FRONT | 810 × 1440 | 136,021 |
| SHOULDER_PRESS | FRONT | 810 × 1440 | 151,115 |
| BICEP_CURL | FRONT | 810 × 1440 | 158,185 |
| PUSH_UP | SIDE | 1440 × 810 | 163,331 |
| BENT_OVER_ROW | SIDE | 810 × 1440 | 147,896 |
| TRICEPS_EXTENSION | FRONT | 810 × 1440 | 165,623 |
| SIDE_BEND | FRONT | 810 × 1440 | 130,126 |

추가 검사 범위: Pillow header/verify와 OpenCV 실제 decode, 두 decoder의 해상도 일치, encoded size·dimension/pixel bounds, 경로/내용 hash 중복 없음, 7종 각각 1장, 실제 SHA256과 `selection_provenance.json` 일치, exercise·class ID·view·TEST 선언·프레임 식별 정보의 manifest/provenance 일치입니다. 이 검사는 모델 prediction을 생성하거나 입력을 수정하지 않았습니다.

사용자가 제공한 출처는 Mendeley Data의 “A Multi-View Raw Video Dataset of Seven Fitness Exercises with Good/Bad Form Labels”, dataset `kgbb3yn47p`, **CC BY 4.0**입니다. 입력별 `rightsCleared=true`와 TEST split·예측을 보지 않은 결정론적 선택 선언을 확인했습니다. 이는 **사용자 제공 선언과 파일 hash를 대조한 범위**이며, 원본 Colab split을 재현하거나 개별 권리를 독립적인 법률 판단으로 확정한 것은 아닙니다. 원본 영상 경로·subject 식별자·사진 자체는 이 공개 문서에 복사하지 않았습니다.

사전 검사 결과는 ignored `local_validation/exp04_golden/preflight_20261115_01.json`에 보존했습니다.

## 3. 모델·source·계약

| 항목 | 이번 실제 실행 근거 |
|---|---|
| 모델 | `airptcoach_yolo26n_exp04_7class_fp32.tflite`, **9,898,949 bytes** |
| 모델 SHA256 | `3e613e94fc1ef3aebbeba309d1b31d7ba6fc71a19274c9b2946793fe493eb84d` |
| 실제 PC tensor | FLOAT32 input `[1,3,640,640]`, output `[1,11,8400]`; allocate/metadata 검사를 거쳐 실제 invoke 7회 |
| Exact source | Ultralytics **8.4.202 wheel**, SHA256 `ae71dc5bb1024010fbdef5bd9281984ddd8c70885bddcfdd6aff0801b7015fb4` 일치 |
| Source 실행 방식 | hash-pinned wheel에서 검토된 원본 AST만 선택 실행; 전체 Ultralytics import·자동 설치·업데이트 없음 |
| Source provenance | predictor·augment/LetterBox·LiteRT backend·ops·NMS의 SHA256 5개와 함수별 원본 line 범위를 reference에 기록 |
| Reference SHA256 | `86a58da0c4d0881892377847a6dd43bfdf079b6c1ebd76fb71df35509836170b` |

전처리는 logical RGB / FLOAT32 / BCHW / `/255`, centered 640 × 640 letterbox, padding 114, auto=false입니다. 이 입력들은 종횡비에 따라 360 × 640 + 좌우 140 padding 또는 640 × 360 + 상하 140 padding으로 처리됐습니다. **이 PC 전처리 기록만으로 Android tensor exact parity를 주장하지 않습니다.**

기존 raw confidence `> .10`, class-aware NMS IoU `.70`, max detections 300을 그대로 사용했습니다. expected label로 결과를 걸러내지 않았습니다. Android 비교 기준 역시 변경하지 않았습니다: input tensor SHA256 exact equality; frozen Python raw decoder/NMS의 confidence `1e-6`·bbox `1e-4px`; 실제 native 최종 결과의 confidence `≤ .001`·bbox 좌표 `≤ 2px`·count/class 및 기존 ordering 규칙입니다.

## 4. 실제 Python/LiteRT 결과

아래 bbox는 **원본 해상도의 `[x1, y1, x2, y2]` pixel 좌표**, confidence는 모델 score입니다. 가독성을 위해 confidence는 소수점 6자리, bbox는 3자리로 반올림했으며 원래 full precision 값은 local-only reference에 보존했습니다.

| Expected | Top prediction / class ID | Confidence | NMS 후 count | Top bbox | Top semantic match |
|---|---|---:|---:|---|---|
| SQUAT | SQUAT / 0 | 0.834635 | 1 | `[164.807, 294.307, 674.040, 1352.987]` | 일치 |
| SHOULDER_PRESS | SHOULDER_PRESS / 1 | 0.823681 | 1 | `[94.929, 184.851, 734.832, 1431.831]` | 일치 |
| BICEP_CURL | BICEP_CURL / 2 | 0.812042 | 1 | `[170.465, 137.225, 724.027, 1402.275]` | 일치 |
| PUSH_UP | PUSH_UP / 3 | 0.791306 | 1 | `[285.089, 395.170, 1208.176, 796.896]` | 일치 |
| BENT_OVER_ROW | BENT_OVER_ROW / 4 | 0.799132 | 1 | `[214.582, 575.038, 641.793, 1310.607]` | 일치 |
| TRICEPS_EXTENSION | TRICEPS_EXTENSION / 5 | 0.887411 | 1 | `[229.564, 12.402, 602.191, 1375.462]` | 일치 |
| SIDE_BEND | SIDE_BEND / 6 | 0.795550 | **2** | `[202.904, 250.092, 659.694, 1337.669]` | 일치 |

**추가 검출도 그대로 보존:** SIDE_BEND 입력에는 두 번째로 **BICEP_CURL / class 2**, confidence **0.557619**, bbox **`[201.726, 258.757, 662.814, 1345.955]`**가 남았습니다. 해당 입력의 NMS 후 ordering은 **`[6, 2]`**입니다. 서로 다른 class를 유지하는 class-aware NMS 결과를 삭제하거나 하나의 검출인 것처럼 기록하지 않았습니다. 이것은 class 혼동 후보이며 향후 별도 품질 관찰 대상입니다.

Top prediction 기준 sanity check는 **correct 7 / incorrect 0 / no detection 0**, 총 최종 검출 수는 **8**입니다. 이 **7장 제한 결과를 전체 모델 accuracy로 해석하지 않습니다.** 또한 정답 일치와 Python↔Android runtime 동등성은 다르며, 후자는 아직 측정하지 않았습니다.

## 5. Bundle 검사와 Android 단계

실제 generator는 각 입력을 모델에 넣은 뒤에만 `.rgb`, `.input.f32`, `.output.f32`를 작성하고 `reference.json`을 마지막에 생성했습니다. 실행 log에는 실제 1/7~7/7 inference 진행과 reference SHA256이 있습니다. `modelInferenceExecuted=true`, `nativeInferenceCount=7`은 **PC LiteRT invoke** 횟수입니다. `androidNativeInferenceExecuted=false`, `exportContractVerified=false`, `contractPromotionPerformed=false`는 그대로입니다.

`check-bundle`은 **exit 0 / `BUNDLE_PREFLIGHT_PASS: 22 files; Android NOT_RUN`**을 반환했습니다. 7 RGB + 7 input tensor + 7 raw output + 1 reference의 private path·모델 identity·class/threshold/verification 경계·payload size/hash 검사입니다. PC bundle PASS를 Android source/geometry/tensor/native 비교 PASS로 대체하지 않습니다.

최초 기기 목록 조회 시점에는 **deviceCount=0 / NO_SINGLE_AUTHORIZED_DEVICE**여서 중단했습니다. 사용자 재연결 후 승인된 SM-S908N 한 대를 확인하고 `.exp04` 전용 설치/private provisioning·instrumentation을 수행했습니다. 원래 0대 기록은 그대로 보존했습니다. 개인 기기 serial이나 SDK/private 전체 경로는 공개하지 않습니다.

| 판정 | 상태 |
|---|---|
| 실제 Python/LiteRT reference / bundle | **PASS** |
| A. Android native contract | **PASS**, 단일 native 테스트 OK |
| B. Android preprocessing exact parity | **PASS**, 7/7 tensor SHA256 byte-identical |
| C. Actual Android Golden / runtime equivalence | **PASS**, 7/7·추론7회·class/count/order 일치 |
| D. 7-image top semantic sanity check | **7/7**, 추가 class 검출 1개 보존 |
| Camera / FPS / thermal / rep·TTS·report 회귀 | **NOT_RUN** |
| EXP04 contract | **NOT_VERIFIED**, 자동 승격 없음 |

승인된 기기 한 대를 확인한 뒤 [Android 절차](EXP04_ANDROID_INFERENCE_CONTRACT.md)의 `.exp04` isolated DEBUG 설치·private 모델/Golden provisioning → native → 전처리 exact parity → actual Golden 순서로 완료했습니다. 각 단계 PASS를 확인하고 다음 단계를 실행했으며 scalar 결과 두 파일을 회수했습니다. 모든 테스트가 PASS했어도 contract를 VERIFIED/Production으로 자동 변경하지 않았습니다.

## 6. 보존·기록 도구 오류·공개 경계

Ignored local-only 근거:

- `local_validation/exp04_golden/preflight_20261115_01.json`: 실제 입력 검사 결과.
- `local_validation/exp04_golden/runs/exp04_actual_01/`: 실제 RGB·tensor·raw output·reference. **Git/APK/서버 업로드 금지**.
- `local_validation/exp04_golden/validation_resume_20261115_01/`: 단계별 명령·exit code·log 및 기기 목록 조회 결과.
- `local_validation/exp04_golden/environment_repair_20261115_01/`: 이전 설치·pip check 실패·복구 기록. 기존 최초 실패 결과도 보존.

기기 조회 helper는 `device-check.json`에 deviceCount=0을 먼저 정상 기록했지만, 이를 감싼 명령 기록 helper도 **동일한 파일명**으로 요약을 새로 저장하려 하여 파일 충돌 오류가 발생했습니다. 원래 `device-check.json`과 `device-check.log`·기기 목록 log를 보존했습니다. 이는 기기 조회에서 없던 기기를 만들거나 Python inference/bundle PASS를 무효화한 오류가 아니며, 해당 요약을 정상 저장했다고 주장하지 않습니다. 실제 조회 결과는 위 원본 JSON/log로 판정합니다.

별도의 `device-check-command-recovered.json`에 원본 로그 기반 상태를 기록했습니다. 이후 helper의 기기 metadata 파일명과 명령 기록 파일명을 분리했습니다. 해당 최초 조회의 요약 복구 당시에는 기기 조회를 재실행하거나 기존 파일을 덮어쓰지 않았습니다.

사용자 재연결 이후 첫 재조회에서는 기존 raw 기기 목록 log 이름도 충돌해 새 `device-check-02` 실패 기록을 남겼습니다. raw log도 회차별 파일명으로 분리한 후 새 `device-check-03`에서 승인된 한 대를 확인했습니다. 두 차례 기록 도구 오류는 원본 로그를 보존하고 수정했으며, Golden 알고리즘·기준을 변경하거나 실패한 검증을 PASS로 덮어쓰지 않았습니다.

모델·입력·manifest/provenance·기존 EXP02/EXP03 증거와 모델을 삭제하거나 덮어쓰지 않았습니다. Golden 실행을 위해 threshold/NMS/tolerance/preprocessing/Primary 정책을 변경하거나 새로운 dependency를 설치하지 않았습니다. 입력 사진이나 모델을 외부로 업로드하지 않았으며 유료 API·LLM/provider·DB migration·Edge Function 배포·billing 변경·자동 commit/push/PR/merge는 하지 않았습니다. 이 공개 문서에는 scalar 검증 결과와 상대 경로만 정리했습니다.

마무리 확인: reference·단계별 실제 exit/log 일치 PASS, Pillow 복구 후 package 변화 0개, 보호 대상 31개 파일 hash 동일, private 경로 42개 ignore 확인 PASS, local_validation/모델 tracked 0개, 변경 문서 8개 주요 secret 패턴/로컬 링크 검사 PASS, `git diff --check` PASS. 전체 보안/법률 감사나 새로운 Android 빌드/회귀 테스트 결과는 아닙니다.

## 7. 이번 변경 파일

공개 문서만 변경했습니다. Android/Gradle·기존 Golden 검증 소스는 수정하지 않았습니다.

- `MODEL_CARD.md`
- `docs/ml/EXP04_ACTUAL_GOLDEN_VALIDATION.md`
- `docs/ml/EXP04_ANDROID_INFERENCE_CONTRACT.md`
- `docs/ml/EXP04_RUNTIME_SHADOW_ROLLOUT.md`
- `docs/ml/EXP04_ANDROID_DEVICE_SHADOW_CHECKLIST.md`
- `docs/ml/EXP04_IMPLEMENTATION_REVIEW.md`
- `docs/ml/EXP04_ENVIRONMENT_REPAIR.md` — 이전 실패를 역사 기록으로 보존하고 최신 결과 연결.
- `docs/ml/EXP04_GOLDEN_RUN_20261115.md` — 이 새 실제 실행 보고서.

후속 공개 파일 `docs/ml/EXP04_ANDROID_GOLDEN_RUN_20261115.md`에는 실제 Android 결과와 비교 오차를 기록했습니다. 따라서 후속 단계까지 포함한 변경 공개 문서는 총 9개입니다.

추가 ignored 로컬 항목: 입력 preflight 결과 JSON, 실제 run의 RGB/tensor/reference 22개 파일과 Android scalar 결과 2개, 환경/단계별 증거 디렉터리와 기록용 helper 4개(`run_stage_20261115.py`, `inspect_device_20261115.py`, `record_state_20261115.py`, `device_actions_20261115.py`). 이 파일들은 commit 대상이 아닙니다.
