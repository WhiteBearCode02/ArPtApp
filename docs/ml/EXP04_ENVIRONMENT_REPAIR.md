# EXP04 검증 환경 — Pillow 한정 복구

2026-11-15 / `ai-v2`. **Pillow 복구 성공, 환경 검사 FAIL로 후속 검증 중단.**
EXP04 contract는 **NOT_VERIFIED**이며 Production/Primary 변경은 없습니다.

## 1. 이전 오류가 발생한 단계

- 공식 `scripts/generate-exp04-actual-golden.py --check-inputs`: 이전 시도 **PASS**. 이 경로는 PIL 없이 manifest·파일 존재 등을 검사합니다.
- 별도 이미지 decode audit: `local_validation/.venv/Scripts/python.exe local_validation/exp04_golden/preflight_20261115.py`의 **27행**, `from PIL import Image`에서 `ModuleNotFoundError: No module named 'PIL'`.
- 실제 Python/LiteRT reference 생성: **NOT_RUN**. reference 생성 오류로 혼동하지 않습니다.

기존 검증 코드·manifest·사진을 수정하거나 다른 디코더로 검사를 우회하지 않았습니다.

## 2. 허용된 환경 변경과 확인 결과

대상 executable: 프로젝트의 `local_validation/.venv/Scripts/python.exe`.
Python **3.11.9**, pip **24.0**. 설치 전 Pillow/PIL 없음.

실제 설치 명령(프로젝트 루트 기준):

```powershell
local_validation\.venv\Scripts\python.exe -I -m pip install --no-deps --no-cache-dir --only-binary=:all: --index-url https://pypi.org/simple Pillow
```

시스템/사용자 설치로 우회되지 않도록 executable·venv prefix를 확인하고, 해당 subprocess에서만 pip 설정 redirect를 차단했습니다. wheel만 허용했으며 설치 임시 파일도 같은 venv 내부에 두었습니다. 시스템 Python·다른 venv·pip 설정은 변경하지 않았습니다.

| 검사 | 실제 결과 |
|---|---|
| 설치 | PASS, exit 0, **Pillow 13.0.0** |
| `import PIL; from PIL import Image` | PASS, exit 0, `Pillow 13.0.0` 출력 |
| PIL import 위치 | 요청된 venv의 `Lib/site-packages/PIL/__init__.py` 내부 확인 |
| 전후 `pip freeze --all` | 둘 다 exit 0. **pillow==13.0.0 추가만 있음** |
| 기존 distribution 변경/삭제 | 각각 0개 |
| OpenCV / NumPy / Torch / LiteRT / 설치 Ultralytics | 각각 4.13.0.92(headless) / 2.4.6 / 2.9.1+cpu / 2.2.0 / 8.4.176 유지 |
| 모델 3개·입력 7장·manifest/provenance·관련 source/wheel·Gradle 설정 등 | 보호 대상 **31개 SHA256 전후 동일** |
| `python -I -m pip check` | **FAIL, exit 1**, 아래 기존 누락 10개 |

실제 `pip check` 로그:

```text
ultralytics 8.4.176 requires matplotlib, which is not installed.
ultralytics 8.4.176 requires nvidia-ml-py, which is not installed.
ultralytics 8.4.176 requires opencv-python, which is not installed.
ultralytics 8.4.176 requires polars, which is not installed.
ultralytics 8.4.176 requires psutil, which is not installed.
ultralytics 8.4.176 requires pyyaml, which is not installed.
ultralytics 8.4.176 requires requests, which is not installed.
ultralytics 8.4.176 requires torchvision, which is not installed.
ultralytics 8.4.176 requires ultralytics-platform, which is not installed.
ultralytics 8.4.176 requires ultralytics-thop, which is not installed.
```

이 10개 distribution은 **설치 전 inventory에도 없었습니다**. Pillow가 기존 패키지를 삭제하거나 버전을 바꿔 만든 문제는 아닙니다. `opencv-python-headless`는 그대로 있으나, `opencv-python`과 distribution 이름이 달라 선언된 요구를 충족하지 않습니다. OpenCV를 추가 설치·대체하지 않았습니다.

설치된 Ultralytics **8.4.176**의 metadata 검사가 실패한 것입니다. EXP04 reference는 별도로 hash-pinned **8.4.202 wheel의 검토된 원본 함수**를 사용하도록 설계되어 있습니다. 이 차이는 사용자가 요구한 `pip check` 실패를 무시하고 진행할 근거가 아니며, reference 실행 성공/실패도 아직 판정하지 않았습니다.

## 3. 단계별 최종 상태

| 단계 | 이번 복구 시도 결과 |
|---|---|
| 환경 repair gate | **FAIL**: Pillow 설치/import는 PASS, `pip check` FAIL |
| 공식 입력 preflight 재실행 | **NOT_RUN**: 선행 환경 검사에서 중단. 이전 PASS를 이번 PASS로 재사용하지 않음 |
| 7장 decode·dimensions·duplicate·SHA256/provenance match | **NOT_RUN**: 이미지 내용 검증 완료를 주장하지 않음 |
| rights·7개 class coverage | 이전 manifest 검사 PASS/사용자 제공 권리 선언. 이번 재검증 NOT_RUN |
| 실제 Python/LiteRT reference / bundle verification | **NOT_RUN**, `exp04_actual_01` 미생성 |
| expected/predicted/confidence/semanticMatch | 아래 7종 모두 **prediction/confidence/semanticMatch NOT_RUN** |
| USB device / model·Golden private provisioning | **NOT_RUN**, ADB 미실행·기기 변경 없음 |
| Android Native Contract / Preprocessing Parity / Actual Golden | 모두 **NOT_RUN** |
| contract | **NOT_VERIFIED 유지**, 승격/실시간 활성화 없음 |

| Expected | Predicted | Confidence | Semantic match |
|---|---|---|---|
| SQUAT | NOT_RUN | NOT_RUN | NOT_RUN |
| SHOULDER_PRESS | NOT_RUN | NOT_RUN | NOT_RUN |
| BICEP_CURL | NOT_RUN | NOT_RUN | NOT_RUN |
| PUSH_UP | NOT_RUN | NOT_RUN | NOT_RUN |
| BENT_OVER_ROW | NOT_RUN | NOT_RUN | NOT_RUN |
| TRICEPS_EXTENSION | NOT_RUN | NOT_RUN | NOT_RUN |
| SIDE_BEND | NOT_RUN | NOT_RUN | NOT_RUN |

사진의 SHA256이 설치 전후 동일하다는 사실은 provenance hash 일치나 정상 decode의 증거가 아닙니다.

## 4. 로컬 증거와 보호

증거는 ignored `local_validation/exp04_golden/environment_repair_20261115_01/`에만 있습니다.

- `before_freeze.log` / `after_freeze.log`: 설치 전후 실제 freeze.
- `before_inventory.json` / `after_inventory.json`: distribution 이름·version.
- `before_protected_hashes.json` / `after_protected_hashes.json`: 보호 대상 31개 파일의 SHA256.
- `pillow_install.log` / `pillow_import.log` / `pip_check.log`: 실제 실행 출력.
- `repair_result_recovered.json`: 로그와 inventory로 확인한 사실 기반 요약.

첫 요약 저장 시 `pip check`가 이미 exit 1로 종료된 뒤, 보조 기록 스크립트가 `WindowsPath`를 JSON으로 변환하지 못했습니다. 원래의 불완전한 `repair_result.json`과 전체 로그는 삭제·덮어쓰기 없이 보존하고 별도 recovered JSON으로 기록했습니다. 기록 스크립트의 Path→문자열 변환만 수정했으며 설치·검증을 재실행하지 않았습니다. 기존 Golden 검증 코드를 변경한 것이 아닙니다.

이미지·manifest·모델·venv·private 증거를 Git/APK에 추가하지 않았습니다. 검증 입력 외부 업로드, 유료 API·DB/Edge Function·billing·LLM·Android/Gradle dependency 변경은 없습니다. Pillow 다운로드를 위해 PyPI에 접속했을 뿐입니다. 기존 EXP02/EXP03 증거·모델·사용자 기록을 변경하지 않았습니다.

마무리 읽기 전용 확인: recovered 기록과 inventory/log 일치 PASS, 보호 대상 31개 현재 hash 일치 PASS, private 경로 21개 ignore 확인 PASS, local_validation/모델 3개 tracked 0개, 변경 공개 문서 7개의 주요 secret 패턴 검사 PASS, `git diff --check` PASS. 이는 범위를 한정한 공개 문서/Git 안전 검사이며 전체 보안·라이선스 감사나 Golden/실기기 검증이 아닙니다. 자동 commit/push/PR/merge 없음.

## 5. 재개 조건

현재 허용 범위는 Pillow뿐입니다. 다른 의존성은 추가·삭제·교체하지 않고 여기서 중단합니다. 다음에는 기존 환경의 부족한 항목을 어떤 범위로 복구할지, 또는 기존 venv를 보존한 별도 환경을 준비할지 사용자 판단이 필요합니다.

승인된 환경에서 검사가 통과한 뒤의 순서:

1. 공식 `--check-inputs` → 기존 이미지 decode/bounds/duplicate/hash/provenance 검사.
2. 이전 run을 보존하고 fresh run에 Python/LiteRT reference → bundle verification → 7종 semantic sanity check.
3. 단일 허가된 Android device → `.exp04` 전용 private provisioning.
4. Native Contract → preprocessing exact parity → actual Golden. 중간 FAIL 시 중단.
5. 이후 별도 실기기 Camera Shadow에서 7종·방향·조명·거리·줌·FPS/latency/thermal과 기존 관절/rep/TTS/report 회귀를 검증. 이번 offline/환경 작업을 Camera PASS로 취급하지 않음.

Android actual Golden이 전부 통과해도 이번 작업에서 `VERIFIED` / Production으로 자동 승격하지 않습니다.
