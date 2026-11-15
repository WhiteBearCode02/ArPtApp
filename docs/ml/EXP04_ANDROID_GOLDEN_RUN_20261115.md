# EXP04 Android native / actual Golden 실행 기록

날짜: **2026-11-15**, `ai-v2`, reference run **`exp04_actual_01`**.

**실제 Android native contract·전처리 exact parity·7장 actual Golden을 각각 실행하여 PASS했습니다.** 계약 코드는 **NOT_VERIFIED**를 유지하며, 실시간 Camera Shadow·FPS·발열·운동 횟수/TTS/report 회귀는 **NOT_RUN**입니다. 이번 결과를 자동 VERIFIED/Production 승격이나 전체 운동 인식 정확도로 해석하지 않습니다.

[PC reference 실행 기록](EXP04_GOLDEN_RUN_20261115.md)의 기기 0대 기록 이후, 사용자가 USB 연결과 디버깅 승인을 완료하여 승인된 기기 한 대를 다시 확인한 후 진행한 별도 단계입니다. 초기 연결 실패·환경 경고·기록 helper 오류는 삭제하거나 성공 결과로 덮지 않았습니다.

## 1. 기기·APK·모델 identity

실제 기기: **Samsung SM-S908N**, Android **SDK 36**, **arm64**. 기기 serial·계정·SDK 전체 경로·앱 private 절대 경로는 공개 문서에 기록하지 않습니다.

사용한 것은 **2026-11-12에 생성된 기존 APK 두 개**입니다. 이번에 새 Gradle 빌드나 전체 회귀 suite를 실행한 결과로 표현하지 않습니다. 설치 전에 실제 aapt 결과의 package/debuggable/runner와 APK hash, public APK의 private weight/fixture 제외 게이트를 확인했습니다.

| 산출물 | 실제 package / 확인 | SHA256 |
|---|---|---|
| 앱 APK | `com.example.arptapp.exp04`, isolated DEBUG | `9c31dcb2efb56179dadcc9923d8ed2a2b775d4b4843a13ecb2231296e294cfd1` |
| Android test APK | `com.example.arptapp.exp04.test`, runner `androidx.test.runner.AndroidJUnitRunner` | `7434c1f4fe995cb3936cae8ca5a3b6e0d93ad9e9be49bdc4a9509a557f6a6cb8` |

사용한 기존 빌드의 상대 경로:

- `build/exp04-integration-verification/outputs/apk/exp04Debug/app-exp04Debug.apk`
- `build/exp04-integration-verification/outputs/apk/androidTest/exp04Debug/app-exp04Debug-androidTest.apk`

두 APK 설치는 exit 0으로 완료했습니다. 기존 기본 앱을 uninstall/clear하거나 EXP02/EXP03 모델·기록을 옮기는 작업은 하지 않았습니다. 이번 검증은 `.exp04` sandbox에 한정되며 기본 앱·기존 사용자 기록의 기능 동작을 다시 검증한 것은 아닙니다.

| 항목 | 확인값 |
|---|---|
| 모델 | `airptcoach_yolo26n_exp04_7class_fp32.tflite`, **9,898,949 bytes** |
| 모델 SHA256 | `3e613e94fc1ef3aebbeba309d1b31d7ba6fc71a19274c9b2946793fe493eb84d` |
| 실제 reference SHA256 | `86a58da0c4d0881892377847a6dd43bfdf079b6c1ebd76fb71df35509836170b` |
| PC source | hash-pinned Ultralytics **8.4.202 wheel**, SHA256 `ae71dc5bb1024010fbdef5bd9281984ddd8c70885bddcfdd6aff0801b7015fb4` |
| 클래스 | 0 SQUAT / 1 SHOULDER_PRESS / 2 BICEP_CURL / 3 PUSH_UP / 4 BENT_OVER_ROW / 5 TRICEPS_EXTENSION / 6 SIDE_BEND |

## 2. 실제 provisioning과 instrumentation 결과

모델은 local identity 검사를 먼저 통과한 뒤, `.exp04` private `noBackupFilesDir/yolo/`로만 전송했습니다. staging/final bytes·SHA256 검사가 PASS했습니다. Golden은 별도 도구로 private `no_backup/yolo/exp04-actual-golden/runs/exp04_actual_01/`에 **22파일의 size/hash**를 확인하며 전송했고 reference를 마지막에 게시했습니다. shared storage·APK assets runtime fallback·외부 서버를 사용하지 않았습니다.

각 instrumentation은 runner의 실제 **`OK (1 test)`**와 단계별 exit 0을 확인했습니다. ADB 명령의 exit code만으로 native PASS를 간주하지 않았습니다.

| 순서 | 실제 테스트 method / 단계 | 결과 | JUnit 표시 시간 |
|---|---|---|---:|
| 1 | `Exp04NativeContractDeviceTest#privateIdentityMetadataAndBoundedSyntheticInvoke` | **PASS**, 1 test | 0.354 s |
| 2 | `Exp04ActualModelGoldenTest#preprocessingOnlyMatchesPythonBitForBit` | **PASS**, 1 test | 5.239 s |
| 3 | `Exp04ActualModelGoldenTest#actualExp04MatchesExactVersionPythonReference` | **PASS**, 1 test | 6.603 s |
| 4 | `transfer-exp04-actual-golden.py export-result` | **PASS**, reference-bound scalar JSON 회수 | 해당 없음 |

### A. Native contract — PASS

실제 모델 파일 identity와 Interpreter load, **FLOAT32 input `[1,3,640,640]` / output `[1,11,8400]`**, 제한된 synthetic 입력의 native invoke·finite output, EXP04 모델을 EXP03 identity/shape로 허용하지 않는 검사를 통과했습니다. 이것 자체는 이미지 semantic test나 카메라 성능 검사가 아닙니다.

별도 `separatelyProvisionedExp03CannotPassExp04Identity` method는 **이번에 실행하지 않았습니다**. EXP03 negative fixture를 이 검증을 위해 복사하지 않았고 그 역방향 optional 기기 검사를 PASS로 계산하지 않습니다.

### B. Preprocessing exact parity — PASS

동일 private RGB에서 Android 전처리가 만든 **7개 input tensor 모두 Python의 SHA256과 exact 일치**했습니다. 각 입력의 1,228,800 float 비교도 `BYTE_IDENTICAL`이며, normalized/RGB-value-equivalent·exact/resampled 영역의 최대/평균 오차가 모두 **0**입니다. geometry와 logical RGB/BCHW/FLOAT32·`/255`·padding 조건을 확인했습니다.

기존 tensor comparator에 있는 pixel tolerance로 승격한 것이 아니라, 별도의 **exact input byte hash gate**를 통과한 결과입니다. 이 gate가 실패하면 실제 Golden의 native invoke를 차단하는 코드는 유지됩니다.

### C. Actual Android Golden — PASS

회수된 `android-result.json`의 실제 최종 필드:

```text
nativeInferenceCount = 7
actualImageCount = 7
allSamplesPassed = true
failure = NONE
productionContract = NOT_VERIFIED
contractPromotionPerformed = false
cameraPerformance = NOT_MEASURED
```

## 3. 입력별 실제 동등성 결과

각 행의 confidence는 첫 번째 검출 기준입니다. delta는 **그 입력의 모든 매칭 검출 중 최대값**입니다. confidence·bbox 원본 full precision 값과 one-to-one match는 ignored JSON에 보존합니다. Python/Android JSON의 소수점 표기 길이는 다를 수 있으며, delta는 표시 문자열을 빼서 만든 값이 아니라 Android comparator가 reference를 FLOAT32로 읽어 계산한 실제 기록값입니다.

| Expected / 방향 | Python top confidence | Android top confidence | 최대 confidence delta | 최대 bbox 좌표 오차(px) | NMS 후 count / ordering (양쪽 동일) | Exact input / frozen raw / native 결과 |
|---|---:|---:|---:|---:|---|---|
| SQUAT / FRONT | 0.8346345425 | 0.8346346 | `5.9604645e-8` | `0.00012207031` | 1 / `[0]` | PASS / PASS / PASS |
| SHOULDER_PRESS / FRONT | 0.8236812353 | 0.8236809 | `3.5762787e-7` | `0.00010681152` | 1 / `[1]` | PASS / PASS / PASS |
| BICEP_CURL / FRONT | 0.8120424151 | 0.8120424 | `0` | `0` | 1 / `[2]` | PASS / PASS / PASS |
| PUSH_UP / SIDE | 0.7913057804 | 0.7913058 | `0` | `0` | 1 / `[3]` | PASS / PASS / PASS |
| BENT_OVER_ROW / SIDE | 0.7991321087 | 0.79913217 | `5.9604645e-8` | `0.00012207031` | 1 / `[4]` | PASS / PASS / PASS |
| TRICEPS_EXTENSION / FRONT | 0.8874109983 | 0.887411 | `0` | `0.0001373291` | 1 / `[5]` | PASS / PASS / PASS |
| SIDE_BEND / FRONT | 0.7955502272 | 0.7955502 | `2.3841858e-7` | `0.00010681152` | **2 / `[6,2]`** | PASS / PASS / PASS |

전체 검출의 최대 confidence delta는 **`3.5762787e-7`**, 최대 bbox 좌표 오차는 **`0.0001373291 px`**였습니다. 기존 native confidence 허용값 `.001` / bbox 좌표 `2px`를 변경하지 않았습니다. count·class·ordering은 모든 7개 입력에서 일치하며 ordering 판정은 모두 **`ORDER_MATCH`**입니다.

Frozen Python raw output을 Android decoder/NMS에 넣은 별도 비교는 **전체 8개 검출의 confidence/box 차이 0, class/count/order 일치**로 PASS했습니다. 이는 frozen raw tensor의 decode/NMS 비교와 동일 input에서 실제 Android native 최종 검출 비교를 각각 수행한 것입니다. Android와 Python의 native raw output 전체 요소가 bitwise 동일하다고 주장하는 검사는 아닙니다.

### 실제 Android top bbox

아래는 원본 이미지 좌표 `[x1,y1,x2,y2]`, pixel 단위입니다.

| Expected | Android top bbox |
|---|---|
| SQUAT | `[164.80714, 294.30737, 674.0395, 1352.9866]` |
| SHOULDER_PRESS | `[94.92874, 184.85121, 734.83167, 1431.8306]` |
| BICEP_CURL | `[170.46524, 137.22528, 724.0273, 1402.2754]` |
| PUSH_UP | `[285.08856, 395.17038, 1208.1764, 796.8965]` |
| BENT_OVER_ROW | `[214.58205, 575.03796, 641.7934, 1310.6073]` |
| TRICEPS_EXTENSION | `[229.56378, 12.401367, 602.19104, 1375.4622]` |
| SIDE_BEND | `[202.90402, 250.09167, 659.6943, 1337.669]` |

### SIDE_BEND 추가 검출 보존

Python에서 남았던 두 번째 **BICEP_CURL / class 2**가 Android에서도 남았습니다.

- Python confidence **0.5576190948** → Android **0.55761886**, 차이 **`2.3841858e-7`**.
- Android bbox **`[201.72546,258.75687,662.81354,1345.9551]`**, 최대 좌표 차이 **`0.00010681152 px`**.
- 최종 count **2**, class ordering **`[6,2]`**. 추가 검출을 삭제하거나 expected label로 결과를 변경하지 않았습니다.

## 4. Semantic 결과와 미검증 범위

7개 입력의 top class는 Python/Android 모두 expected와 일치했습니다. **correct 7 / incorrect 0 / no detection 0**, 최종 검출 총수는 **8**입니다. 이는 선정한 7장의 semantic sanity check이며, 전체 데이터셋 accuracy·새로운 사람/구도에 대한 일반화·연속 영상 품질을 의미하지 않습니다. SIDE_BEND에서의 추가 BICEP_CURL은 class 혼동 후보로 남기며, runtime 동등성 PASS와 모델 품질을 분리해서 다룹니다.

Android result의 각 `inferenceAndPostprocessMs`는 **145.589~188.109 ms** 범위였지만, 이는 offline still image의 inference+postprocess 관측값입니다. warm-up/연속 카메라/thermal/장시간 검증이 아니며 camera FPS나 inference-only latency로 대체하지 않습니다.

다음은 별도 리뷰로 contract 승격 여부를 판단하고, 필요한 변경/재검증 후 7종 각각 10초 이상의 실제 Camera Shadow 관찰과 Landmark Primary·rep count·TTS·report 회귀를 진행하는 단계입니다. 이번 테스트로 이 항목들을 PASS 처리하지 않았습니다.

## 5. 증거·보안 경계

아래 ignored local-only 증거를 근거로 한 기록입니다.

- `local_validation/exp04_golden/runs/exp04_actual_01/reference.json`: 실제 PC reference.
- 같은 run의 `android-result.json`: 기존 export-result 도구로 회수한 reference-bound scalar 결과.
- 같은 run의 `android-preprocessing.json`: bounded private JSON만 별도 회수, 7행 exact tensor 일치 확인. 모델/사진을 회수한 것이 아닙니다.
- `local_validation/exp04_golden/validation_resume_20261115_01/android-*.json` / `android-*.log`: APK preflight·install·private provisioning·3개 instrumentation·result export의 실제 exit code/log.

JSON/log의 초기 실패·부분 기록, EXP02/EXP03 모델·Golden 증거를 삭제하거나 덮어쓰지 않았습니다. `local_validation/` 전체와 모델은 Git ignored 그대로입니다. 모델/이미지/RGB/tensor/reference를 Git, APK, shared storage, 외부 서버에 추가하지 않았습니다. APK packaging gate는 private weight/fixture **0**을 확인했습니다. 입력 권리/TEST 출처는 사용자 제공 manifest/provenance 선언과 파일 hash를 대조한 범위이며, 개별 권리나 Colab split을 독립 재현했다는 뜻은 아닙니다.

이번 Android 검증을 위해 새 dependency·delegate·유료 API·LLM 연결을 추가하지 않았고, 원격 DB/Edge Function/billing 변경도 하지 않았습니다. 기존 `pip check` 경고와 사용자가 exit 0 필수 게이트만 해제한 경위는 [환경 기록](EXP04_ENVIRONMENT_REPAIR.md) 및 [PC 실행 기록](EXP04_GOLDEN_RUN_20261115.md)에 보존했습니다. 자동 commit/push/PR/merge는 하지 않았습니다.

**최종 상태: Android actual Golden / native 실측 증거 확보, contract는 NOT_VERIFIED 유지. Camera는 NOT_RUN.**

## 6. 마무리 확인과 변경 파일

실제 결과·단계별 exit/log·reference SHA binding PASS, 세 instrumentation의 실제 `OK (1 test)` 확인, 패키지 버전 변경 0개, 보호 대상 31개 기존 파일 SHA256 동일을 확인했습니다. private 경로 100개 ignore 확인 PASS와 local_validation/모델 tracked 0개, 공개 문서 9개 주요 secret/기기 serial/로컬 링크 검사 PASS, 보조 helper 4개 문법 검사 PASS, `git diff --check` PASS입니다. 전체 보안 감사·새 Gradle 빌드·Camera 회귀를 실행했다는 의미는 아닙니다.

이번까지 공개 변경 파일 전체:

- `MODEL_CARD.md`
- `docs/ml/EXP04_ACTUAL_GOLDEN_VALIDATION.md`
- `docs/ml/EXP04_ANDROID_INFERENCE_CONTRACT.md`
- `docs/ml/EXP04_RUNTIME_SHADOW_ROLLOUT.md`
- `docs/ml/EXP04_ANDROID_DEVICE_SHADOW_CHECKLIST.md`
- `docs/ml/EXP04_IMPLEMENTATION_REVIEW.md`
- `docs/ml/EXP04_ENVIRONMENT_REPAIR.md`
- `docs/ml/EXP04_GOLDEN_RUN_20261115.md` — 신규 PC 실제 실행 보고서.
- `docs/ml/EXP04_ANDROID_GOLDEN_RUN_20261115.md` — 이 신규 실기기 보고서.

추가 로컬 증거/기록 helper는 ignored 경로에만 있습니다. Android/Kotlin/Gradle·모델·입력·manifest·기존 Golden 검증 소스와 판정 기준을 변경하지 않았습니다.
