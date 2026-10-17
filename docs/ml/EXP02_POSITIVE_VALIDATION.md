# EXP02 양성 경로 · 로컬 세션 검증

이 문서는 DEBUG 전용 수동 검증 절차입니다. **실시간 prediction은 비활성이고 production contract는 `NOT_VERIFIED`**입니다. 후보가 검출되거나 테스트가 통과해도 상수를 자동 승격하지 않습니다. Landmark Primary, 운동 잠금·횟수·점수·자세 분석, confidence `0.10`, IoU `0.70`, max detections `300`은 유지합니다.

## 검증 경계

- 기존 실제 Golden 4장은 Python/Android 모두 무검출: negative-path 증거이며 positive bbox/confidence/NMS 증거는 아닙니다.
- 양성 PASS에는 동일한 실제 입력에서 최소 한 쌍의 검출, class/count/order 동등성, confidence 차이 ≤ `0.001`, bbox 최대 오차 ≤ `2px`가 필요합니다. 기존 잠정 허용 오차를 확대하지 않습니다.
- 전처리는 기존 tensor comparator로 별도 확인합니다. 보간 영역 최대 `1/255 + 1e-6`, padding은 기존 엄격한 기준을 적용합니다.
- 실제 운동 정답과 runtime 동등성은 별개입니다. 사용자가 명시한 정답만 semantic sanity check에 쓰며 무검출·오분류를 삭제하지 않습니다. 소수 후보를 전체 모델 accuracy라고 부르지 않습니다.
- Python reference는 기존 로컬 venv의 LiteRT `2.2.0`, Ultralytics `8.4.176` 원본 함수와 실제 모델을 사용합니다. 패키지 설치·서버 호출·클라우드 업로드는 하지 않습니다.

## 휴대폰: 명시적 한 장 촬영

1. 새 DEBUG APK를 기존 앱 위에 설치합니다(`adb install -r`). 삭제 후 재설치하지 마세요. private 모델과 운동 기록을 유지합니다.
2. 운동 준비 → **YOLO 진단 보기** → 패널 내부를 스크롤합니다. 검증 시 다른 사람·민감한 배경이 없는 곳에서 본인 자세만 촬영하세요.
3. **3초 후 검증 프레임 저장**을 직접 누릅니다. 카운트다운 후 허용된 카메라 프레임 **한 장만** 복사합니다. 카메라 전환·화면 이탈·시간 초과 시 취소합니다. 자동 촬영/영상 기록/프레임 반복 저장은 없습니다.
4. worker에서 저장한 PNG를 다시 읽어 기존 production preprocessor/decoder/postprocessor로 별도 native 추론합니다. 결과는 운동 판정·Room·LLM에 전달되지 않습니다.
5. `detections > 0`인 후보가 있으면 파일명과 실제 결과를 확인합니다. 없으면 자세/구도를 달리해 수동으로 다시 촬영할 수 있으나 threshold는 바꾸지 않습니다.

저장 위치: `context.noBackupFilesDir/exp02_positive/`의 PNG와 동일 basename `.android.json`입니다. 최대 8장, 한 장 최대 8MiB/4,194,304 pixels/한 변 4096입니다. UI는 경로·크기·SHA와 scalar 결과만 표시하며 사진 미리보기/외부 전송 기능이 없습니다. **검증 프레임 모두 삭제**는 확인 후 해당 모듈 소유 후보/결과만 삭제하며 모델·운동 기록을 건드리지 않습니다. PC로 내보낸 자료는 별도로 로컬 정리해야 합니다.

## PC: 선택한 후보만 private 전송

아래 `<serial>`과 `<candidate.png>`는 연결한 본인 기기와 UI에 표시된 정확한 파일명으로 바꿉니다. 예시 숫자/이미지는 실제 증거가 아닙니다.

```powershell
$python = '.\local_validation\.venv\Scripts\python.exe'
$adb = 'C:\Users\dlgus\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $python scripts/transfer-exp02-positive.py --adb $adb --serial <serial> export-candidate --candidate <candidate.png>
```

`adb exec-out/run-as`로 선택한 PNG/JSON만 `local_validation/exp02_positive/`에 저장합니다. 바이너리 stdin/out, 정확한 byte count, 전후 SHA를 확인하며 public/external storage, `adb root`, 서버를 사용하지 않습니다. 기존 로컬 증거는 덮어쓰지 않습니다.

`local_validation/exp02_positive/manifest.json`을 준비합니다. `rightsCleared`는 실제 촬영/사용 권한이 확인된 경우에만 true로 하세요. 정답을 모르면 `exercise`/`cameraView`는 생략하거나 null로 둡니다. `androidResult`로 해당 native JSON을 연결하면 PNG SHA/해상도/decoded RGB SHA도 확인합니다.

```json
{
  "schemaVersion": 1,
  "samples": [{
    "id": "candidate_01",
    "image": "<실제로 내보낸 파일명>.png",
    "androidResult": "<동일 basename>.android.json",
    "rightsCleared": true,
    "rightsBasis": "본인이 직접 수동 촬영한 로컬 검증 사진",
    "exercise": null,
    "cameraView": null
  }]
}
```

```powershell
& $python scripts/generate-exp02-positive-reference.py --manifest local_validation/exp02_positive/manifest.json --output local_validation/exp02_positive/bundle
# 다음 명령은 검증 앱을 force-stop하므로 운동 중 실행하지 않습니다.
& $python scripts/transfer-exp02-positive.py --adb $adb --serial <serial> provision-reference --bundle local_validation/exp02_positive/bundle
& $adb -s <serial> shell am instrument -w -r -e class com.example.arptapp.ui.camera.yolo.Exp02PositiveModelGoldenTest#actualCandidateMatchesPythonReference com.example.arptapp.test/androidx.test.runner.AndroidJUnitRunner
& $python scripts/transfer-exp02-positive.py --adb $adb --serial <serial> export-comparison
& $python scripts/summarize-exp02-positive-comparison.py --reference local_validation/exp02_positive/bundle/reference.json --android local_validation/exp02_positive/bundle/android-positive-result.json --output local_validation/exp02_positive/comparison_summary.json
```

Android 입력/결과는 private `no_backup/yolo/positive-golden/`에만 둡니다. harness는 Python과 동일 RGB raster를 복원해 production 컴포넌트를 사용합니다. 저장 시 native 결과와 같은 PNG의 decode hash를 함께 확인하여 입력 차이를 숨기지 않습니다. 모든 결과가 무검출이면 positive 테스트는 실패합니다. 실패나 미실행을 PASS로 표시하지 않습니다.

## 실제 운동 세션 저장/재시작 검증

일반 운동 종료에는 기존 Supabase 업로드가 있습니다. 외부 전송 없는 검증을 위해 시작 **전에** DEBUG 패널의 **로컬 검증 세션 · 업로드 안 함**을 직접 선택합니다. 기본값은 false이며 시작 시 동결합니다. 운동 중 선택을 바꿀 수 없습니다. 이 세션의 종료 업로드만 생략하며 일반/release 동작은 유지합니다. Result의 기존 Room 저장은 그대로입니다. Home의 기존 설정 동기화는 별개이므로 엄격한 오프라인 검증 시 로그인 후 사용자가 네트워크를 끄고 진행하세요.

1. 개발자가 고유 `sessionEvidenceRun` 인자로 `YoloSessionPersistenceDeviceTest#readBaseline`을 실행합니다(MAX(id)만 읽음).
2. 사용자가 촬영 방향 선택 → 로컬 검증 체크 → 시작 → 실제 몇 회 운동 → 종료 → 결과 저장을 완료합니다. 테스트가 운동 기록을 만들지 않습니다.
3. `#captureNewlySavedRecord`로 baseline 이후 정확히 한 개의 실제 저장 행만 선택합니다. 새 행이 없거나 둘 이상이면 중단합니다.
4. `adb shell am force-stop com.example.arptapp` 후 같은 run 인자로 `#verifySameRecordAfterRestart`를 실행합니다. 새 PID에서 같은 record/횟수/시간/정규화된 diagnostics SHA를 비교합니다.
5. `#openSelectedReport`로 동일 History mapper/navigation을 사용하는 실제 Report의 기본 접힘/펼침/저장된 metric 표시를 검사합니다. **이 방법은 실제 History 행 탭 증거가 아닙니다**. 사용자 로그인 복원 후 직접 같은 History 행을 여는 확인은 따로 기록합니다.

도우미 class: `com.example.arptapp.data.YoloSessionPersistenceDeviceTest`, runner: `com.example.arptapp.test/androidx.test.runner.AndroidJUnitRunner`. 각 호출에 동일 `-e sessionEvidenceRun <고유_안전한_run_id>`를 전달합니다. 증거는 private `no_backup/yolo_session_validation/<run>/`에 저장합니다. 사용자 DB는 읽기 전용이며 계정/토큰/신체정보/분석 원문은 내보내지 않습니다. null legacy 호환 테스트는 별도 **in-memory Room**에서만 수행합니다.

`NOT_VERIFIED` 상태에서 0 processed frames/미측정 latency는 정상일 수 있습니다. 값을 채워 넣지 않습니다. Report 진단은 DEBUG 전용·기본 접힘이며 운동 점수·자세 분석에 사용되지 않는다는 설명을 유지합니다.

## 공개/배포 안전

- `local_validation/` 전체가 Git ignore 대상입니다. 후보, reference, RGB/tensor, venv, 모델은 commit하지 않습니다.
- EXP02 및 loose YOLO 모델의 기존 APK exclusion은 유지합니다. 기존 tracked `yolo11n-pose_int8.tflite`는 수정/삭제하지 않습니다.
- 촬영 UI·저장/native 구현은 `src/debug`에만 있으며 `src/release` factory는 null입니다. release에서 접근할 수 없습니다.
- 새 API key, 유료 API, LLM provider, Supabase migration/deploy/billing 변경은 없습니다.
- 공개 코드가 모델/데이터 재배포 권리를 의미하지 않습니다. 이번 흐름은 권리 미확인 모델의 공개 APK 포함을 허용하지 않습니다.

## 증거 기록 원칙

실행 전에는 `NOT_RUN`, 입력 미확보는 `USER_MANUAL_CAPTURE_PENDING`, 실제 운동 미완료는 `USER_WORKOUT_PENDING`으로 기록합니다. 양성 동등성·native·실제 세션 저장 조건이 모두 확인되어도 먼저 **VERIFIED 승격 가능** 근거를 보고하고 코드 상수 변경은 별도 승인 후 진행합니다.

### 2026-10-17 구현 후 검증

| 항목 | 실제 확인 결과 |
|---|---|
| Gradle | `assembleDebug testDebugUnitTest assembleDebugAndroidTest` 성공, 별도 `assembleRelease` 성공 |
| JVM | 522개, failure/error/skip 0(최종 빌드 기준) |
| Node | 기존 75개 + 신규 10개 통과 |
| Python 신규 도구 | 19 + 17 + 10 = 46개 통과(실제 후보 inference 증거 아님) |
| 실기기 저장/Room | 본인 SM-S908N의 storage 6개 + in-memory Room 2개 통과(사용자 운동 증거 아님) |
| 실제 세션 기준점 | 읽기 전용 `readBaseline` 통과 |
| 모델 유지 | 앱 업데이트 후 private 모델 SHA256이 canonical 값과 동일 |
| APK | debug/release 모두 loose YOLO weights 없음, MediaPipe task 유지 |
| 실제 positive | 사용자 재시도에서 실제 3장 확보. 2 positive + 1 empty를 모두 유지하여 Python 3회 / Android native 3회 실행, 동등성 PASS |
| 실제 오차 | 3장 preprocessing byte-identical. class/count/order/NMS 후 결과 일치. confidence 최대 `2.3841858e-7`, bbox 최대 `3.0517578e-5px` |
| 실제 사용자 세션 저장 | 첫 시도는 새 행 없음으로 실패하여 중단. 재시도에서 baseline 이후 정확히 한 신규 행을 읽어 diagnostics 존재 확인 PASS |
| 실제 사용자 세션 재시작 | 명시적 force-stop 후 새 PID에서 동일 record/집계/SHA 재조회 PASS |
| 실제 저장 Report 렌더링 | 동일 History mapper/navigation으로 실제 Report 진입, 기본 접힘/펼침/저장 metric 표시 PASS. 앱 재실행 후 실제 History 행 → Report → 진단 표시도 사용자가 직접 확인 |
| semantic sanity check | 사용자가 세 장 모두 SQUAT으로 명시. 2장 일치 + 1장 무검출, 전체 모델 accuracy 아님. 원본 runtime 결과를 수정하지 않고 private sidecar로 기록 |
| 최종 contract | `NOT_VERIFIED` 유지, 자동 승격 없음 |

이 표의 대기 항목은 사용자 수동 촬영/실제 운동을 받은 뒤 실제 증거로만 갱신합니다.

요청한 최소 A(실제 양성 확보)·B(동일 입력 runtime 동등성)·C(실제 세션 영속성/Report/History 확인)가 충족되어 **VERIFIED 승격 코드 리뷰가 가능한 상태**입니다. 사용자의 별도 승인 없이 상수는 변경하지 않았습니다.

현재 실제 양성 검출은 class 0(SQUAT) 범위입니다. class 1 실제 양성, 다중 overlapping bbox suppression 및 다른 구도/해상도 전체를 이번 소수 입력으로 보장하지 않습니다. 사진 정답은 별도로 사용자가 명시해야 하며, 미지정 상태에서 운동 종류를 추측하거나 전체 accuracy를 산출하지 않습니다. 후보 SHA/이미지별 bbox/confidence, 실제 운동 record 식별자·횟수·시간 및 세션 증거는 ignored 로컬 자료/기기 private 파일에만 남깁니다.

## 변경 파일 목록

공개 가능한 source/test/docs 28개입니다. `local_validation/exp02_positive/VALIDATION_STATUS.md`는 별도 ignored 로컬 상태 기록입니다.

```text
app/src/main/java/com/example/arptapp/DashboardActivity.kt
app/src/main/java/com/example/arptapp/ui/camera/PoseCameraController.kt
app/src/main/java/com/example/arptapp/ui/camera/ManualValidationFrameGate.kt
app/src/main/java/com/example/arptapp/ui/camera/yolo/validation/Exp02PositiveValidation.kt
app/src/main/java/com/example/arptapp/ui/dashboard/YoloDiagnosticsPanel.kt
app/src/main/java/com/example/arptapp/ui/dashboard/DebugValidationControls.kt
app/src/debug/java/com/example/arptapp/ui/camera/yolo/validation/DebugPositiveCandidateStore.kt
app/src/debug/java/com/example/arptapp/ui/camera/yolo/validation/DebugPositiveNativeInference.kt
app/src/debug/java/com/example/arptapp/ui/camera/yolo/validation/DebugPositiveValidationFactory.kt
app/src/debug/java/com/example/arptapp/ui/dashboard/DebugPositiveValidationControls.kt
app/src/release/java/com/example/arptapp/ui/camera/yolo/validation/DebugPositiveValidationFactory.kt
app/src/release/java/com/example/arptapp/ui/dashboard/DebugPositiveValidationControls.kt
app/src/test/java/com/example/arptapp/ui/camera/ManualValidationFrameGateTest.kt
app/src/test/java/com/example/arptapp/ui/dashboard/DebugLocalSessionUploadPolicyTest.kt
app/src/testDebug/java/com/example/arptapp/ui/camera/yolo/validation/DebugPositiveNativeFailureTest.kt
app/src/androidTest/java/com/example/arptapp/data/YoloSessionLocalRoundTripTest.kt
app/src/androidTest/java/com/example/arptapp/data/YoloSessionPersistenceDeviceTest.kt
app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp02PositiveModelGoldenTest.kt
app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/validation/DebugPositiveCandidateStoreTest.kt
scripts/generate-exp02-positive-reference.py
scripts/transfer-exp02-positive.py
scripts/summarize-exp02-positive-comparison.py
scripts/test-exp02-positive-reference.py
scripts/test-exp02-positive-transfer.py
scripts/test-exp02-positive-comparison.py
scripts/exp02-positive.test.mjs
docs/ml/EXP02_ANDROID_DEVICE_SHADOW_CHECKLIST.md
docs/ml/EXP02_POSITIVE_VALIDATION.md
```
