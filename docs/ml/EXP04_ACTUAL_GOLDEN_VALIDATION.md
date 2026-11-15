# EXP04 actual Golden 실행 기록

날짜: **2026-11-15**, branch `ai-v2`, 시작 HEAD `a91725e`. 시작 시 working tree clean.

## 최신 후속 시도: Pillow 복구 성공 / pip check FAIL

사용자의 Pillow 한정 설치 승인 후 지정 venv에 **Pillow 13.0.0만 추가**했습니다. import PASS, 기존 dependency 변경/삭제 0개, 보호 대상 31개 파일 SHA256 동일입니다. 그러나 `pip check`가 기존 Ultralytics 8.4.176의 미설치 의존성 10개로 **exit 1**을 반환하여 후속 preflight·이미지 검사·reference·ADB·Android 검증은 재개하지 않았습니다. contract **NOT_VERIFIED**를 유지합니다.

[복구 명령·전후 환경·실제 오류 로그·단계별 결과](EXP04_ENVIRONMENT_REPAIR.md). 아래는 삭제·덮어쓰지 않은 **최초 시도의 역사 기록**이며, Pillow 부재가 현재도 계속된다는 뜻은 아닙니다.

## 최초 시도 결과: 사전 검사 환경 차단으로 중단

기존 `--check-inputs`는 PASS였지만, 요청된 추가 이미지 디코딩 검사에서
`ModuleNotFoundError: No module named 'PIL'`이 발생했습니다. 사용자 요청의
“사전 검사 FAIL 시 원인을 보고하고 중단” 원칙에 따라 실제 추론·ADB·설치·전송·빌드를 시작하지 않았습니다.
Pillow를 자동 설치하거나 다른 환경/디코더로 바꿔 검사를 우회하지 않았습니다.

| 검증 단계 | 실제 결과 |
|---|---|
| 현재 branch / 기존 도구·계약 존재 | PASS: ai-v2, EXP04 profile/contract/generator/transfer/provision/native/Golden 테스트·문서 존재 |
| manifest / 기본 입력 검사 | PASS: 7종, 실제 파일명·exercise·유효 cameraView, rightsCleared=true·rightsBasis, 파일 존재·encoded size |
| selection_provenance.json | 존재, 7종·TEST split·결정론적 선택·CC BY 4.0 선언. 원본 Colab split을 독립 재현한 증거는 아님 |
| 실제 이미지 디코딩·해상도·픽셀 bounds | NOT_RUN: Pillow import에서 차단 |
| 이미지 SHA256 ↔ provenance / 내용 중복 검사 | NOT_RUN: 해당 단계에 도달하지 못함 |
| Python/LiteRT actual reference / check-bundle | NOT_RUN, reference 생성 안 함 |
| USB 기기·APK·private 모델/Golden provisioning | NOT_RUN: 사전 검사에서 중단, 기존 앱/데이터 접근 안 함 |
| A. Android native contract | NOT_RUN |
| B. Android preprocessing exact parity | NOT_RUN |
| C. Android actual Golden | NOT_RUN |
| D. 7-image semantic sanity check | NOT_RUN: correct/incorrect/no-detection 개수를 아직 계산할 수 없음 |
| Camera / FPS / thermal / rep·TTS·report | NOT_RUN |
| EXP04 contract | **NOT_VERIFIED 유지**, 자동 승격 없음. Landmark Primary 유지 |
| 회귀 테스트·APK build·packaging | 이번 실행에서는 NOT_RUN. 2026-11-12~13의 통과 기록을 이번 실행 결과로 재사용하지 않음 |

## 입력 목록

사용자 제공 manifest와 실제 파일 메타데이터입니다. 이미지 내용을 열어 확인하거나 추론한 결과가 아닙니다.

| ID / 파일명 | Expected | 방향 | bytes | Predicted / confidence / bbox / semantic |
|---|---|---|---:|---|
| squat_front.jpg | SQUAT | FRONT | 136,021 | NOT_RUN |
| press_front.jpg | SHOULDER_PRESS | FRONT | 151,115 | NOT_RUN |
| curl_front.jpg | BICEP_CURL | FRONT | 158,185 | NOT_RUN |
| pushup_side.jpg | PUSH_UP | SIDE | 163,331 | NOT_RUN |
| row_side.jpg | BENT_OVER_ROW | SIDE | 147,896 | NOT_RUN |
| triceps_front.jpg | TRICEPS_EXTENSION | FRONT | 165,623 | NOT_RUN |
| sidebend_front.jpg | SIDE_BEND | FRONT | 130,126 | NOT_RUN |

권리·TEST 출처는 사용자 제공 `manifest.json` / `selection_provenance.json`의 선언 범위입니다.
provenance의 기존 pseudo-label confidence는 이번 EXP04 prediction/confidence가 아닙니다.
잘못된 prediction이나 무탐지를 숨긴 것이 아니라, 아직 실제 inference를 실행하지 않은 상태입니다.

## 환경 진단

검증 대상은 기존 `local_validation/.venv/Scripts/python.exe`, Python **3.11.9**입니다.

| 항목 | 읽기 전용 metadata 확인 |
|---|---|
| Pillow / PIL | **없음** — 추가 이미지 사전 검사에서 실제 import 실패 |
| ai-edge-litert | 2.2.0, module 존재. 이번 모델 load/invoke 미실행 |
| NumPy | 2.4.6, module 존재 |
| OpenCV | `opencv-python-headless` 4.13.0.92, cv2 module 존재 |
| Torch | 2.9.1+cpu, module 존재 |
| 설치된 Ultralytics | 8.4.176. EXP04 reference로 사용하지 않았음 |
| EXP04 reference source | 기존 exact 8.4.202 wheel 사용 설계. 이번에는 source loader/inference 실행 전 중단 |

초기 환경 metadata 조회에서 `opencv-python` distribution을 찾지 못했지만,
후속 읽기 전용 조회로 `opencv-python-headless`가 있음을 확인했습니다. OpenCV가 없다는 뜻이 아닙니다.
현재 직접 확인된 차단 사유는 **Pillow 미설치**입니다. 남은 환경 호환성은 아직 실행 검증하지 않았습니다.

## 보존 및 Git/비용 경계

- 입력 이미지·모델·manifest·provenance는 기존 ignored local-only 경로에 그대로 두었습니다.
- 실제 모델 inference, 이미지 생성/다운로드/외부 업로드, ADB serial 수집·기기 변경 없음.
- threshold/NMS/tolerance/preprocessing/contract code 변경 없음.
- 새 package 설치·유료 서비스 연결·remote DB/배포/billing 변경 없음.
- EXP02/EXP03 모델·문서·실제 Golden 증거·테스트 결과를 삭제하거나 덮어쓰지 않았습니다.
- 자동 commit/push/PR/merge 없음.

추가 local-only 자료(모두 Git/APK 제외):

- `local_validation/exp04_golden/preflight_20261115.py`: 추가 입력 검사 도구. 이미지 기록·업로드·추론 없음.
- `local_validation/exp04_golden/preflight_20261115_failure_01.json`: 실제 import 오류·확인 범위·NOT_RUN 기록.
- `local_validation/exp04_golden/manifest.json` / `selection_provenance.json`: 사용자가 제공한 원본, 수정 안 함.

`runs/exp04_actual_01`은 생성하지 않았습니다. 기존 EXP02/EXP03 및 이전 run은 수정하지 않았습니다.

## 재개 조건

검증 전용 venv에서 Pillow 의존성 복구에 대한 사용자 방향을 받은 뒤,
**입력 사전 검사부터 다시 시작**해야 합니다. 사용자가 제공한 이미지나 manifest를 PASS용으로 고치지 않습니다.
디코딩·bounds·중복·hash/provenance 검사가 모두 통과한 뒤에만 새 unique run의 실제 reference를 생성합니다.
그 다음 정확히 한 대의 승인된 USB device 확인 → `.exp04` 전용 APK/모델/Golden private provisioning →
native smoke → exact preprocessing → actual Golden 순서입니다. 자동 contract 승격은 하지 않습니다.

이후 camera 7종 Shadow 측정은 Android Golden이 검토된 다음 별도 단계입니다.

## 변경 문서 전체

- `docs/ml/EXP04_ACTUAL_GOLDEN_VALIDATION.md` (본 실제 실행/차단 기록, 신규)
- `docs/ml/EXP04_ANDROID_INFERENCE_CONTRACT.md`
- `docs/ml/EXP04_RUNTIME_SHADOW_ROLLOUT.md`
- `docs/ml/EXP04_ANDROID_DEVICE_SHADOW_CHECKLIST.md`
- `docs/ml/EXP04_IMPLEMENTATION_REVIEW.md`
- `MODEL_CARD.md`

변경 공개 텍스트 6개에 대한 secret 패턴 검사와 `git diff --check`는 PASS입니다.
이는 모델/Android/회귀 테스트 PASS를 의미하지 않습니다. source code·manifest·validation policy는 변경하지 않았습니다.
