# EXP04 구현·검증 보고

작업: 2026-11-12~13 / 기존 `ai-v2` 브랜치 / 자동 commit·push·PR·merge 없음.

## 결론

7-class 모델별 profile·DEBUG Shadow 연결 기반·private provisioning·실제 Golden 도구를 구현했습니다. **실제 EXP04 실시간 prediction 활성화 아님: contract NOT_VERIFIED, actual Golden / Android native / camera NOT_RUN**입니다. 사용자에게 실제 7종 TEST 이미지가 없어 임의 이미지 다운로드·생성·외부 업로드·reference 결과 생성은 하지 않았습니다.

기본 앱 EXP02, EXP03 비교 빌드·기존 모델/Golden 증거, Landmark Primary와 기존 7종 관절 분석·카운트·TTS·리포트·V2 저장 구조를 유지했습니다. 새 외부 API·유료 서비스·LLM/provider·SDK/의존성 설치·Supabase migration/배포·billing 변경 없음. 기존 서비스의 일반 사용 비용/원격 보안은 이번 로컬 ML 변경 검사로 보장하지 않습니다.

## 모델·offline 근거

- 로컬 파일: `app/src/main/assets/airptcoach_yolo26n_exp04_7class_fp32.tflite` (원본 보존, Git ignored, APK 제외).
- 실제 9,898,949 bytes, SHA256 `3e613e94fc1ef3aebbeba309d1b31d7ba6fc71a19274c9b2946793fe493eb84d`.
- PC LiteRT load/allocate로 관찰: FLOAT32 `[1,3,640,640]` → FLOAT32 `[1,11,8400]`. 이번 EXP04 이미지 inference 없음.
- ID 0~6: SQUAT / SHOULDER_PRESS / BICEP_CURL / PUSH_UP / BENT_OVER_ROW / TRICEPS_EXTENSION / SIDE_BEND.
- 사용자 제공 Colab: holdout 9,031장/labels, 35/35 PT↔LiteRT parity Top1=1 / IoU=1 / confidence delta=0 / missing=0. PT mAP50=.8865/mAP50-95=.7464, LiteRT=.8857/.7460. P/R/F1 및 출처는 [모델 카드](../../MODEL_CARD.md). 이번에 재현한 성능 수치가 아니며 모바일 성능 증거 아님.

## 테스트 결과

| 항목 | 결과 / 범위 |
|---|---|
| JVM | 834 tests / 77 suites, failures·errors·skipped 0. EXP02/03 회귀 + EXP04 synthetic 정책 테스트 포함 |
| Node | 294 PASS, 0 fail, 0 skipped |
| Python 도구 정책 | EXP04 15 + EXP03 14 + EXP02 26 = 55 PASS. 실제 모델 Golden 아님 |
| Android serializer → strict V2 contract | 19 fixture PASS. 원격 Supabase 호출/DB 적용 아님 |
| 기본 APK / Android test APK | `assembleDebug` / `assembleDebugAndroidTest` PASS |
| 기본 Lint | errors/fatal 0, warnings 300. 전체 경고 해소를 주장하지 않음 |
| EXP04 APK / Android test APK / Lint | `assembleExp04Debug` / `assembleExp04DebugAndroidTest` / `lintExp04Debug` PASS(9m49s). errors/fatal 0, warnings 300 |
| 공개 APK gate (기본·EXP04) | 둘 다 PASS: loose YOLO weight·검증 RGB/tensor/reference 각각 0개, MediaPipe task·정확한 OpenCV NOTICE 보존 |
| EXP04 private provisioning | `-ValidateLocalOnly` PASS: exact filename/size/hash. ADB 전송 미실행 |
| Secret 검사 | 변경된 공개 텍스트 범위 패턴 검사 PASS, 공개 Supabase key 분류는 값 숨김. Git history·원격 설정 검사 아님 |
| Git safety | EXP02/03/04 모델·전체 local_validation 미추적/ignored. 세 모델 exact SHA256 재확인, 기존 EXP02/03 Golden 문서 변경 없음, 원본 모델 삭제·이동 없음 |
| 실제 Android native / actual Golden / camera | NOT_RUN. 빌드·합성 테스트·offline 제공값으로 대체하지 않음 |

초기 빌드에서는 3분 이상 무출력이 반복되어 해당 빌드만 중단했습니다. 사용자 앱 정리 후 하나의 Gradle·worker1·1GB 상한·in-process Kotlin·별도 출력 폴더로 기본 빌드 전체 PASS(7m35s)를 확인했습니다. 사용자 IDE/Java/Kotlin 언어 서버를 강제 종료하거나 기존 output을 수동 삭제하지 않았습니다. TF Lite 중복 namespace/native strip/Gradle deprecation 경고와 Lint 경고는 남아 있습니다.

산출물 루트: `build/exp04-integration-verification/`. 기본 APK `outputs/apk/debug/app-debug.apk` (36,775,938 bytes), EXP04 후보 APK `outputs/apk/exp04Debug/app-exp04Debug.apk` (36,775,846 bytes). 둘 다 모델이 없는 검증용 APK이며 설치만으로 EXP04 추론이 켜지지 않습니다. EXP04 생성 BuildConfig도 `.exp04` / EXP04 / DEBUG=true / comparison=true / privateBundled=false임을 확인했습니다.

## 변경·신규 공개 파일 전체

### 모델/Shadow API·진단·빌드 (21)

- `.gitignore`
- `app/build.gradle.kts`
- `app/src/main/java/com/example/arptapp/domain/detection/YoloModelProfile.kt` (신규)
- `app/src/main/java/com/example/arptapp/domain/detection/Exp04YoloContract.kt` (신규)
- `app/src/main/java/com/example/arptapp/domain/detection/YoloExportContract.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/Exp02PostProcessor.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/YoloRaw640Algorithms.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/YoloActualGoldenComparison.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/YoloBuildSelection.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/YoloExerciseDetector.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/YoloExerciseStabilizer.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/ExerciseDetector.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/DetectorComparison.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/YoloSessionDiagnostics.kt`
- `app/src/main/java/com/example/arptapp/data/YoloSessionDiagnosticsCodec.kt`
- `app/src/main/java/com/example/arptapp/ui/camera/yolo/TfliteYoloRuntime.kt`
- `app/src/main/java/com/example/arptapp/ui/camera/yolo/YoloBuildProfile.kt`
- `app/src/main/java/com/example/arptapp/presentation/diagnostics/YoloLiveDiagnosticsPresentation.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/YoloSessionDiagnosticsPresentation.kt`
- `app/src/main/java/com/example/arptapp/DashboardActivity.kt` (진단 집계 전달만)
- `app/src/debug/java/com/example/arptapp/ui/camera/yolo/validation/DebugPositiveNativeInference.kt` (선택 profile의 buffer/decoder 사용)

### 검증 코드·도구 (13)

- `app/src/test/java/com/example/arptapp/domain/detection/Exp04ContractTest.kt` (신규)
- `app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp04GoldenFixtures.kt` (신규, private/hash-bound fixture API)
- `app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp04ActualModelGoldenTest.kt` (신규)
- `app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp04NativeContractDeviceTest.kt` (신규)
- `scripts/generate-exp04-actual-golden.py` (신규)
- `scripts/transfer-exp04-actual-golden.py` (신규)
- `scripts/provision-exp04-model.ps1` (신규)
- `scripts/test-exp04-actual-golden.py` (신규)
- `scripts/exp04-shadow.test.mjs` (신규)
- `scripts/transfer-exp02-positive.py` (고정 allowlist의 package 인자만 추가, 기존 기본값/전송 동작 유지)
- `scripts/exp02-positive.test.mjs` (profile별 포트 검사)
- `scripts/private-exp02-apk.test.mjs` (새 비교 빌드도 private bundle false 확인)
- `scripts/production-exp02.test.mjs` (모델별 native metadata/클래스 로그 검사)

### 문서·공개 템플릿 (9)

- `README.md`
- `MODEL_CARD.md`
- `docs/ML_EXPERIMENTS.md`
- `docs/YOLO_DEVICE_VALIDATION.md`
- `docs/ml/EXP04_ANDROID_INFERENCE_CONTRACT.md` (신규)
- `docs/ml/EXP04_RUNTIME_SHADOW_ROLLOUT.md` (신규)
- `docs/ml/EXP04_ANDROID_DEVICE_SHADOW_CHECKLIST.md` (신규)
- `docs/ml/exp04-actual-golden.manifest.example.json` (신규, rightsCleared=false)
- `docs/ml/EXP04_IMPLEMENTATION_REVIEW.md` (본 문서, 신규)

총 43개 공개 텍스트 파일. 별도 local-only 준비물: `local_validation/exp04_golden/manifest.json`, README, 7개 inputs 종목 폴더와 runs 폴더의 빈 placeholder. 이미지·prediction은 없으며 이 준비물은 Git 목록에 포함하지 않습니다. 실제 모델 binary는 수정하지 않았습니다.

## 다음 작업 / commit 제안

1. 사용자가 Mendeley 원본 **TEST 대상 영상**의 대표 프레임을 7종 inputs 폴더에 제공합니다.
2. manifest의 파일명·실제 종목·방향·출처·권리 확인 후 [reference→private 전송→Android 비교 절차](EXP04_ANDROID_INFERENCE_CONTRACT.md)를 진행합니다. 기기 작업은 그때 별도 동의를 받습니다.
3. Android actual Golden/native 증거를 검토한 다음 별도 코드 변경으로만 contract 승격을 결정합니다. 이후 [종목별 최소10초·실시간 Shadow 체크리스트](EXP04_ANDROID_DEVICE_SHADOW_CHECKLIST.md)를 실행합니다. 지금 threshold/stabilizer/NMS를 튜닝하지 않습니다.

제안 제목: `feat: EXP04 7종 YOLO Shadow 검증 기반과 모델별 계약 분리`

제안 내용: 모델별 immutable profile·7종 decoder/stabilizer/진단 연결 / DEBUG private provisioning 및 actual Golden 도구·템플릿 추가 / NOT_VERIFIED와 Landmark Primary·기존 EXP02/03·공개 APK 모델 제외 정책 유지.
