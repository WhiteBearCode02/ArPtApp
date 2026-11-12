# EXP04 DEBUG Shadow rollout

기준: 2026-11-12. **준비된 구조, NOT_VERIFIED. 실제 EXP04 camera prediction 비활성.**

## 상태와 모델 선택

| 빌드 | 모델 / 패키지 | 정책 |
|---|---|---|
| debug (기본) | EXP02 / com.example.arptapp | 기존 2종 Shadow·전처리·사용자 기록 유지 |
| exp02Debug / exp03Debug | 각 2종 / .exp02·.exp03 | 기존 비교 빌드·검증 근거 보존 |
| exp04Debug (명시 선택) | EXP04 7종 / .exp04 | 전용 private 파일·profile, NOT_VERIFIED로 frame admission 차단 |
| release | 기존 기본 identity | YOLO Shadow OFF, EXP04 선택 불가 |

`YoloBuildSelection`은 compile-time variant/DEBUG/정확한 applicationId 조합만 허용합니다. 모델 파일 존재·Intent·remote flag·preferences로 켤 수 없습니다. `YoloModelProfiles`의 shape·class·supported set·buffer bytes를 detector/stabilizer/diagnostics/codec 모두 사용하고, 전역 EXP02 정책은 legacy 기본값으로 보존합니다.

```text
CameraX → MediaPipe → Landmark Primary → SessionExerciseLock → counter / TTS / report / V2 history
       └→ DEBUG Shadow gate → exact model identity/metadata → contract gate (EXP04: CLOSED)
                                                       └→ 향후 raw/stable/진단 집계만
```

YOLO는 어떤 운동 결과·반복 횟수·점수·TTS·Supabase exercise도 결정하지 않습니다. 기존 진단은 Room/local 전용이며 Supabase/LLM DTO에 추가하지 않았습니다. EXP04 count map만 7종+UNKNOWN이고 EXP02/03 기록은 당시 2종+UNKNOWN으로 해석합니다.

## 빌드 (기기 설치 아님)

기존 캐시만 사용하며 새 패키지 설치/네트워크 호출은 필요하지 않습니다. 아래 출력 폴더는 verificationBuildDir를 지정한 경우입니다. Windows 기존 IDE 잠금이 있는 기본 build/app을 지우지 않습니다.

```powershell
.\gradlew.bat :app:assembleExp04Debug :app:assembleExp04DebugAndroidTest :app:lintExp04Debug -PabTestBuildType=exp04Debug -PverificationBuildDir=exp04-integration-verification --offline --no-daemon --no-build-cache --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'
# 공개 가능 여부의 패키징 gate. 생성 경로를 확인한 뒤 실행.
.\scripts\verify-public-apk.ps1 -ApkPath build/exp04-integration-verification/outputs/apk/exp04Debug/app-exp04Debug.apk
```

EXP04 빌드에 `-PprivateExp02Apk=true`를 사용하지 마세요. 해당 기존 옵션은 EXP02 소유자 전용 별도 산출물 정책이며 EXP04 재배포 허가가 아닙니다. 일반 APK에는 loose YOLO weight·RGB·tensor·검증 사진이 포함되지 않습니다.

## 이후 기기 단계 (이번에 미실행)

운동/기록 저장 중이 아닐 때에만 본인 한 대를 연결하고 USB 허용 후 진행합니다. 설치·instrumentation은 앱을 재시작할 수 있습니다. 기존 기본 앱은 삭제/초기화하지 않습니다.

```powershell
$adb = '본인 Android SDK platform-tools의 실제 adb.exe 전체 경로'
$serial = 'adb devices에서 확인한 본인 기기 serial'
& $adb devices
& $adb -s $serial install -r build/exp04-integration-verification/outputs/apk/exp04Debug/app-exp04Debug.apk
& $adb -s $serial install -r build/exp04-integration-verification/outputs/apk/androidTest/exp04Debug/app-exp04Debug-androidTest.apk
.\scripts\provision-exp04-model.ps1 -ModelPath app/src/main/assets/airptcoach_yolo26n_exp04_7class_fp32.tflite -ValidateLocalOnly
# 아래는 .exp04 앱을 일시 종료할 수 있음. 저장 중에 실행 금지.
.\scripts\provision-exp04-model.ps1 -AdbPath $adb -DeviceSerial $serial -ModelPath app/src/main/assets/airptcoach_yolo26n_exp04_7class_fp32.tflite
```

실제 모델 목적지는 앱 private `noBackupFilesDir/yolo/airptcoach_yolo26n_exp04_7class_fp32.tflite`입니다. exact 이름·크기·hash, 각 parent의 canonical path를 확인하고 private temporary file→chmod600→size/hash→atomic mv→final hash 순서입니다. 공유 저장소/외부 서버/원본 삭제/다른 모델 덮어쓰기 없음.

다음은 [실제 Golden 절차](EXP04_ANDROID_INFERENCE_CONTRACT.md)입니다. **7종 TEST 이미지 미제공으로 현재 NOT_RUN**. native smoke만으로 VERIFIED/운동 인식 성공을 주장하지 않습니다.

## 진단 UI / 실패 처리

운동 준비 → YOLO 진단 보기에서 선택 모델 ID/상태, Landmark Primary, raw/stable, confidence, processed frames, UNKNOWN/HOLD, inference/total latency, FPS, thermal을 읽습니다. NOT_VERIFIED이면 processed=0·실시간 prediction 비활성이 정상이며 측정하지 않은 latency/FPS는 `미측정` 또는 `세션 측정 전`입니다. UNKNOWN/HOLD count는 안정화된 종목을 결정하지 못한 처리 프레임 수(처리 전은 0)이며 정확도 비율이 아닙니다. 모델 로드 성공과 계약 확인을 구분합니다. 사진 경로/기기 private path를 UI·원격 로그로 보내지 않습니다.

기존 DEBUG 수동 후보 촬영 기능은 사용자가 직접 누른 한 장만 별도 검증 포트로 처리하는 도구이며 실시간 Shadow gate/Primary 승격이 아닙니다. 이번 작업에서는 촬영하지 않았습니다. EXP04 actual Golden 증거로는 위 수동 촬영만으로 대체하지 않고 7종 TEST fixture 절차를 사용합니다.

Windows 실행 정책 때문에 `.ps1` 실행이 막히면, 확인한 로컬 도구에 한해 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/provision-exp04-model.ps1 -ValidateLocalOnly`처럼 **해당 프로세스에만** 적용할 수 있습니다. PC 전체 정책/레지스트리는 변경하지 않았습니다. APK 검사 도구도 같은 방식으로 실행할 수 있습니다.

모델 없음·hash 불일치·shape/dtype 불일치·지원되지 않는 native op·inference 오류는 fail closed이며 Landmark Primary가 계속 동작합니다. single-flight·throttle·backpressure·thermal 정책은 기존 것을 유지합니다. 새 delegate/runtime·서버·유료 API/LLM·Supabase migration/배포/billing 변경을 추가하지 않았습니다.

BICEP_CURL/SIDE_BEND를 별도 관찰하되 UI에 ‘저정확도’ 단정 문구는 넣지 않습니다. [기기 체크리스트](EXP04_ANDROID_DEVICE_SHADOW_CHECKLIST.md)의 실제 관찰 뒤에만 품질을 판단합니다.
