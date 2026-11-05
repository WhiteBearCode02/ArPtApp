# EXP03 Android private provisioning 및 실기기 검증

2026-11-06 · 실제 휴대폰 native / actual-image Golden **PASS (Run 2)**, contract **VERIFIED**, camera **MEASURED_LIMITED**.
스쿼트 정면·측면 2세션의 실시간 경로/저장만 확인했으며 stable 운동 인식은 미확보입니다.
[실제 Camera baseline](EXP03_CAMERA_SHADOW_BASELINE.md). 아래 이전 Golden 시점의 미측정 이력은 보존합니다.
모델 9,894,032 bytes / SHA256 `3d2b6bc537d8aef5a19b671226d73c98b49411574a193be72e225086202d1167`.

Run 1 FAIL(1/4)은 보존했습니다. pure Kotlin OpenCV parity 수정 후 **Run 2 전체 PASS(4/4)**,
tensor SHA4/4 동일 및 기존 confidence/bbox tolerance를 확인해 VERIFIED로 승격했습니다.
[실제 결과·원인](EXP03_PREPROCESSING_PARITY.md). 실제 Camera baseline은 이번에 측정하지 않았습니다.

## 설치·주입·테스트 순서 (기록 보존)

1. 휴대폰 USB 디버깅을 허용하고 잠금을 풉니다. `adb devices`에 authorized `device`가 **정확히 1대**인지 확인합니다. 운동/저장을 종료한 뒤 진행하세요.
2. DEBUG 앱과 테스트 APK를 **업데이트 설치**합니다. 삭제·앱 데이터 초기화·`pm clear`는 금지입니다.
3. 아래 `-ValidateLocalOnly`와 `-WhatIf` 확인 후 EXP03을 private noBackup으로 주입합니다. 실제 주입은 DEBUG 앱만 종료하며 데이터/기존 EXP02 모델은 지우지 않습니다.
4. `Exp03NativeContractDeviceTest`를 실행합니다. load/metadata/native smoke 성공과 실제 JSON을 확인합니다. 합성 입력이므로 카메라 성능·운동 정답률·Golden PASS가 아닙니다.
5. 후속 Camera baseline 단계에서 앱의 운동 준비→YOLO 진단에 EXP03 / VERIFIED / READY / prediction 활성 표시를 확인합니다. 실제 FPS/latency는 본인 운동으로 별도 측정해야 하며 이번 정지 이미지 결과로 대체하지 않습니다. Landmark Primary는 유지합니다.

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb devices
& $adb -s YOUR_SERIAL install -r build/dashboard-ui-verification/outputs/apk/debug/app-debug.apk
& $adb -s YOUR_SERIAL install -r build/dashboard-ui-verification/outputs/apk/androidTest/debug/app-debug-androidTest.apk

powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provision-exp03-model.ps1 -ValidateLocalOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provision-exp03-model.ps1 -AdbPath $adb -DeviceSerial YOUR_SERIAL -WhatIf
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provision-exp03-model.ps1 -AdbPath $adb -DeviceSerial YOUR_SERIAL

& $adb -s YOUR_SERIAL shell am instrument -w -r -e class com.example.arptapp.ui.camera.yolo.Exp03NativeContractDeviceTest com.example.arptapp.test/androidx.test.runner.AndroidJUnitRunner
```

PC 원본은 `app/src/main/assets/airptcoach_yolo26n_exp03_final_fp32.tflite`이지만 APK에서는 제외합니다.
기기 목적지는 `<context.noBackupFilesDir>/yolo/airptcoach_yolo26n_exp03_final_fp32.tflite`입니다.

## 보안과 실행 결과 해석

- EXP02 provisioning 스크립트는 보존합니다. EXP03 스크립트는 그 파일의 검토된 raw stream/hash 함수만 재사용하며 EXP02 실행 본문은 실행하지 않습니다.
- canonical local size/hash, authorized 1대, DEBUG run-as, private root, symlink/junction 및 경로를 검증합니다.
- raw exec-in/dd 정확한 byte count → private 임시 파일 0600 → size/hash → atomic mv → final size/hash 순서입니다.
- root, shared storage, 네트워크 다운로드, 모델 APK 우회 포함은 없습니다.
- 정리 대상은 해당 실행이 만든 정확한 임시 파일 하나뿐입니다. 모델/디렉터리/운동 DB 삭제는 없습니다.
- `-ValidateLocalOnly`는 ADB를 호출하지 않습니다. `-WhatIf`는 read-only 기기 preflight까지만 허용합니다.
- staging/final device size/SHA 검증 PASS, 기존 EXP02 private SHA 보존 확인. 로컬 binary transport 테스트와 별도로 실제 USB provisioning도 수행했습니다.

## 이번 실기기 실행 결과

사용자가 운동·저장 중이 아님을 확인한 뒤, authorized USB 1대에 DEBUG 앱/test APK를 `install -r`로
업데이트하고 EXP03을 주입했습니다. 앱 삭제·데이터 초기화·기존 모델 삭제는 하지 않았습니다.
`Exp03NativeContractDeviceTest` **2/2 PASS**: 실제 모델 load, FLOAT32 tensor shape/dtype,
합성 입력 native invoke 1회와 finite 출력, 미검증 factory prediction 차단을 확인했습니다.
위 내용은 이전 synthetic smoke 기록입니다. 후속 Run 1은 FAIL, 수정 후 Run 2는 PASS이며
DEBUG + VERIFIED + READY에서만 prediction을 허용합니다. 앱 화면의 육안 확인이나
카메라 FPS/발열/운동 테스트를 이번 instrumentation 결과로 대신 주장하지 않습니다.

native smoke evidence는 private `no_backup/yolo/exp03-native/runs/<UUID>/native-smoke.json`에 배타 생성합니다.
RGB/영상/raw tensor/계정 정보나 운동 DB 행은 저장하지 않습니다. Android 측 nativeMs는 **합성 입력 단일 invoke** 값이며
카메라 FPS가 아닙니다. 실패/모델 누락은 test FAIL이지 PASS나 skip이 아닙니다.

Golden 검증에서는 EXP03 전용 모델 ID/hash·export source·권리 확인 입력·Python 실제 reference가 필요합니다.
기존 EXP02 bundle/reference를 EXP03 디렉터리에 복사하거나 expected label로 결과를 필터링하지 마세요.
최종 VERIFIED 후 실제 카메라 측정은 별도 단계입니다. 그때에만 raw/stable/UNKNOWN, confidence,
pre/infer/post/total, processed FPS, cameraView, thermal을 수집하고 History→Report 저장/재시작을 확인합니다.

현재 세션 UI에서 MODEL_MISSING은 새 EXP03 private 파일이 아직 없다는 뜻일 수 있습니다.
기존 EXP02 private 파일이 있다고 자동 fallback/활성화하지 않습니다. 학습 모델 권리/공개 승인을 뜻하지 않습니다.
