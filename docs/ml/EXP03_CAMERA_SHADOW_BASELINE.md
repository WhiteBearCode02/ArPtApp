# EXP03 실제 Camera Shadow · 첫 2세션 baseline

검토일: 2026-11-06. 사용자가 직접 수행·저장한 스쿼트 정면/측면 세션을 읽기 전용으로 확인했습니다.
**Camera = MEASURED_LIMITED / 안정화 인식 품질 미달 / Primary = LANDMARK**입니다.
Golden PASS와 실제 카메라 인식 품질은 별개이며, 이 결과로 일반 정확도·배포 준비를 주장하지 않습니다.

## 확인 방법과 범위

- 사용자가 두 결과의 저장 완료와 검증 중 앱 종료 가능을 확인했습니다.
- authorized 휴대폰 한 대의 DEBUG 앱에서 기존 Room DB를 `OPEN_READONLY`로 열었습니다.
- 최신 두 ID만 조회했습니다. 두 행의 소유자 일치를 메모리에서 검사했으며, 더 좋은 결과를 찾으려 과거 행을 검색하지 않았습니다.
- 촬영 방향은 저장된 `cameraView=FRONT/SIDE`, 기준 운동은 `expectedSessionExercise=SQUAT`입니다.
  FRONT/SIDE는 **몸의 촬영 방향**이지 전면/후면 렌즈가 아닙니다. 실제 운동 종류는 사용자의 확인에 근거합니다.
- 두 행 모두 strict codec으로 유효한 진단을 읽었습니다. 새 프로세스에서 정확히 같은 ID의
  횟수·시간·진단 SHA와 JSON이 동일했고 History가 사용하는 Report mapper/Intent의 진단 전달도 통과했습니다.
  **History 목록을 실제로 탭하거나 화면을 육안 확인한 검증은 아닙니다.**
- 원본 ID·계정·세션 시각·운동 분석·private JSON은 이 문서에 싣지 않습니다.
  사진/영상/랜드마크/전체 DB를 읽거나 복사하지 않았으며 이 검증은 inference를 추가 실행하지 않았습니다.

## 모델·실행 상태

두 세션 모두 `yolo26n_exp03_final_fp32`, 기준 SHA
`3d2b6bc537d8aef5a19b671226d73c98b49411574a193be72e225086202d1167`,
`modelStatus=READY`, `contractStatus=VERIFIED`, `shadowRuntimeEnabled=true`, `predictionEnabled=true`입니다.
진단의 SHA는 기록된 identity이지 이 읽기 전용 테스트가 모델 파일을 다시 해시했다는 뜻은 아닙니다.

| 저장된 집계 | 스쿼트 정면 | 스쿼트 측면 |
|---|---:|---:|
| activeDurationMs | 57,370 | 39,098 |
| 관찰 / 입장 / 완료 프레임 | 699 / 123 / 122 | 482 / 81 / 80 |
| YOLO 완료 FPS | 2.1265 | 2.0461 |
| 전처리 평균 ms | 259.60 | 269.39 |
| native inference 평균 ms | 142.30 | 147.69 |
| 후처리 평균 ms | 17.72 | 19.25 |
| 전체 평균 ms | 419.61 | 436.33 |
| raw SQUAT / SHOULDER_PRESS / UNKNOWN | 0 / 25 / 97 | 30 / 36 / 14 |
| stable SQUAT / SHOULDER_PRESS / UNKNOWN | 0 / 0 / 122 | 0 / 0 / 80 |
| confidence 표본 수 | 25 | 66 |
| confidence 평균 / 최저 / 최고 | .174196 / .101911 / .328710 | .386594 / .102031 / .856653 |
| lock 전 미비교 / lock 후 UNKNOWN | 20 / 102 | 1 / 79 |
| lock 후 stable 일치 / 불일치 | 0 / 0 | 0 / 0 |
| maxThermalStatus | 0 (NORMAL) | 0 (NORMAL) |

완료 FPS는 `processedFrames / activeDuration`이며 CameraX/MediaPipe FPS가 아닙니다.
confidence는 detection이 선택된 프레임만의 값으로, 인식 정확도나 자세 점수가 아닙니다.
입장과 완료의 차이는 각각 1이며, 이 집계만으로 종료 시 진행 중 작업인지 오류인지 확정하지 않습니다.
thermal 0은 두 짧은 세션에서 기록된 상태일 뿐 장시간 발열 안전성 증명이 아닙니다.

## 판정: 실행 성공과 인식 품질을 분리

1. **실시간 경로 동작 확인:** 두 세션에서 실제 native 완료·시간·진단 저장이 관찰됐습니다.
2. **안정화된 운동 인식 미확보:** 202개 완료 프레임 모두 stable UNKNOWN입니다.
   lock 이후 UNKNOWN 비율은 각각 102/102, 79/79 = 100%입니다.
   일치율 `일치/(일치+불일치)`는 분모가 0이므로 **N/A**이지 0%나 100%가 아닙니다.
3. **원인 분리의 한계:** 정면은 선택 confidence 최고치도 .60 미만이므로 현재 안정화 정책에서
   유효 표가 되지 않습니다. 측면은 최고치가 .60 이상이지만 집계에는 프레임별 순서가 없어
   최근 5개 중 4개 합의·2초 evidence age 충족 여부나 reset 여부를 역산할 수 없습니다.
   낮은 confidence의 원인을 모델 성능/구도/실시간 입력 처리 중 하나로 단정하지 않습니다.
4. **지연 baseline:** 전처리가 전체 평균 시간의 약 62%입니다. 최적화 후보이지만
   이 작업에서는 전처리·runtime·delegate·임계값을 바꾸지 않았습니다.

raw confidence .10 / stable .60 / window 5·합의 4 / evidence age 2000ms /
NMS IoU .70 / maxDet 300 / 최소 interval 250ms를 그대로 유지했습니다.
Landmark Primary와 exercise lock/rep/score/calorie/coaching/routing도 변경하지 않았습니다.
`VERIFIED`는 export contract 동등성이지 실제 운동 인식 품질 보증이 아닙니다. YOLO는 계속 DEBUG Shadow입니다.

## 증거·검증·안전

앱 private noBackup의 immutable run `exp03_camera_pair_20261106_01`과
`exp03_camera_pair_20261106_01_restart`에 scalar evidence만 저장했습니다.
capture 파일 SHA256은 `a09d9bbc9ad766064b6c8d6afeaa10a3b5a130f1546eaad4bf26dbc9f0f97cc6`입니다.
이 두 파일은 Git/APK에 복사하지 않았고 기존 Golden FAIL/PASS 증거도 변경하지 않았습니다.

- `assembleDebugAndroidTest --offline --no-build-cache`: BUILD SUCCESSFUL (2분 54초).
- Node: 137/137 PASS. 기존 136 + owner-confirmed pair guard 1.
- 실제 Android instrumentation: capture 1/1, fresh-process readback 1/1 PASS.
- scoped secret-pattern 검사 9개 public text PASS, `git diff --check` PASS.
- 새 test APK와 기존 debug/release APK에서 loose model/private fixture 제외 PASS.
  기존 debug/release에는 MediaPipe task와 exact BSD notice도 유지돼 있습니다. 두 앱 APK는 재빌드하지 않았습니다.
- `local_validation/`와 EXP03 원본 모델은 ignored/untracked이며 evidence는 기기 private 저장소에만 남았습니다.
- 앱 main/release 소스·dependency·DB schema 변경 없음. JVM/전체 APK 빌드는 이번에 반복하지 않았습니다.
- 설치는 test APK의 `install -r`만 사용했습니다. 앱 삭제/초기화·DB 쓰기·모델 교체 없음.
- 사진/프레임 저장, 외부 전송, 원격 SQL, 배포, LLM/유료 API 호출, billing 변경 없음.
  기존 앱 전체 기능의 무과금·법적 적합성을 새로 보증하는 검토는 아닙니다.

## 다음 검증

지금의 낮은 confidence를 threshold 완화나 Primary 전환으로 숨기지 않습니다.
향후 원인 분리에는 사용자가 별도로 동의해 직접 저장한 소수의 같은 Camera 입력을
Python/Android에 대조하는 검증이 필요합니다. 이번 두 세션은 영상을 저장하지 않았으므로
사후에 해당 프레임을 재구성하거나 Python 동등성을 확인했다고 주장할 수 없습니다.
이전 Golden contract PASS는 유지하되, **실시간 운동 인식 품질은 추가 검증 필요**로 남깁니다.

## 변경 파일

- `app/src/androidTest/java/com/example/arptapp/data/YoloSessionPersistenceDeviceTest.kt`: 기존 helper를 재사용한 bounded 2건 capture와 새 프로세스 재조회. 기존 baseline/단일 기록 테스트 보존.
- `scripts/yolo-session-persistence.test.mjs`: owner 확인·2건 제한·소유자 일치·읽기 전용·결과 선별 금지·재시작 검증 guard.
- `docs/ml/EXP03_CAMERA_SHADOW_BASELINE.md`: 실제 Camera 관측값·한계·안전·검증 기록.
- `docs/ml/EXP03_RUNTIME_SHADOW_ROLLOUT.md`: 현재 제한적 Camera 관측과 과거 NOT_MEASURED 이력 분리.
- `docs/ml/EXP03_ANDROID_INFERENCE_CONTRACT.md`: 계약 동등성/Camera 품질 구분.
- `docs/ml/EXP03_ANDROID_DEVICE_SHADOW_CHECKLIST.md`: 현재 관측 범위 연결.
- `docs/ml/EXP03_PREPROCESSING_PARITY.md`: Golden 당시 미측정 이력을 보존하면서 후속 Camera 결과 연결.
- `README.md`: 현재 Camera 검증 상태/로드맵 갱신.
- `MODEL_CARD.md`: 제한적 Camera baseline과 인식 품질 미달 명시.

자동 commit/push/PR/merge는 수행하지 않았습니다.
