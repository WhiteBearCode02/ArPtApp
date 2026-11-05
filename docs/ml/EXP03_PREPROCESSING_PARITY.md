# EXP03 preprocessing parity · 2026-11-05

**Run 1 FAIL 보존 → 보간 구현 수정 → Run 2 PASS → EXP03 VERIFIED**.
실제 Camera 성능은 **NOT_MEASURED**, Primary는 **LANDMARK**입니다.

## 원인: 일반 bilinear가 아닌 uint8 INTER_LINEAR

기존 `Exp02RgbPreprocessor`는 half-pixel Double 보간 후 한 번 `round()`했습니다. 기준 OpenCV는
`cv2 4.13.0` / `opencv-python-headless 4.13.0.92`이며 아래의 uint8 전용 경로를 사용합니다.

1. half-pixel 좌표를 **Float**로 저장하고 floor.
2. 두 가중치를 **11-bit (2048)** 계수로 ties-to-even 반올림.
3. 수평 보간 결과는 정수 합으로 유지.
4. uint8 전용 수직 보간은 수평 합 `>>4`, 계수 곱 `>>16`, 두 항의 합에2를 더한 뒤 `>>2`.
5. source X 경계의 weight는0으로 고정하지만 Y는 weight를 유지한 채 source row를 복제.

이는 `round → floor`로 바꾼 시행착오가 아닙니다. scalar와 SIMD 구현의 동일한 정수 연산을
pure Kotlin으로 재현했습니다. 원본의 조건 분기와 bounds를 확인했으며 모든 중간 곱은 Int 범위입니다.

공식 근거: [OpenCV 4.13.0 resize.cpp](https://github.com/opencv/opencv/blob/4.13.0/modules/imgproc/src/resize.cpp),
`INTER_RESIZE_COEF_BITS`, `HResizeLinear`, uint8 `VResizeLinear` specialization, coefficient setup.
설치된 build 문자열은 `4.13.0-1-gb4c5ec4042`입니다. [실제 build commit](https://github.com/opencv/opencv/blob/b4c5ec4042/modules/imgproc/src/resize.cpp)과
4.13.0 tag의 해당 파일 SHA256이 모두
`89db2e4de92e1d03890f3e7fe517f895a36514f355da097b2c8e4c55fc1847e4`로 같음을 확인했습니다.
원본은 ignored build 경로에만 보관했습니다.

IPP enabled=true, NotExact=false, optimized=true 환경입니다. 원본 코드의 ipp8u/linear NotExact
제외 조건도 확인했습니다. 합성64개에서 기본 경로와 optimized/IPP OFF 경로의 결과가 모두 동일했습니다.
reference를 다른 보간 방식으로 바꾸지 않았고 `INTER_LINEAR_EXACT`로 교체하지 않았습니다.
이 근거는 현재 버전/uint8 계약에 해당하며 임의 OpenCV 버전/모든 backend의 보장을 뜻하지 않습니다.

## 변경 경계

- 새 pure Kotlin `YoloOpenCvLinearRgbPreprocessor`: resampled pixel 연산만 변경.
- canonical `YoloBitmapPreprocessor`: 새 구현을 사용, logical Bitmap ARGB 읽기 방식 유지.
- historical `Exp02BitmapPreprocessor`: 기존 `Exp02RgbPreprocessor`를 명시적으로 주입해 보존.
- `Exp02Preprocessor.kt`의 geometry 및 기존 연산은 **변경하지 않음**.
- 640×640, aspect ratio, centered letterbox, pad114, RGB, BCHW, FLOAT32 `/255` 유지.
- bbox transform/decoder/NMS/confidence/stabilizer/rep/score/calorie/coaching/routing 변경 없음.
- OpenCV Android SDK, Maven repository, GPU/NNAPI delegate, runtime upgrade 추가 없음.
- 원본 BSD-3-Clause 조건/면책을 `res/raw/opencv_resize_notice.txt`에 보존하고 APK에도 포함.
  `.gitignore`는 이 고지 하나만 예외로 허용하며 나머지 raw model/media는 계속 제외합니다.

## 실제 환경과 synthetic unit vectors

Python3.11.9 / OpenCV4.13.0.92 / NumPy2.4.6 / LiteRT2.2.0 / Ultralytics **8.4.202 exact wheel source**.
설치된 ultralytics distribution8.4.176은 reference 함수로 쓰지 않았습니다.
새 reference에는 package version, optimization/IPP 상태, CPU features, build-information hash도 기록했습니다.

16개 크기 ×4패턴 = **64개**입니다. 1×1, 2×2, 3×2, 2×3, 5×7, 7×5, 17×13, 13×17,
320×240, 640×480, 1280×720, 720×1280, 640×640, 1280×1280, 1×640, 640×1.
gradient/checkerboard/edge/fixed-seed counter-random의 정수식을 Python과 Kotlin에서 동일하게 생성합니다.
전체 resized RGB 및 전체 padded FLOAT32 tensor SHA와 작은 probe를 비교하여 **64/64 exact PASS**했습니다.
native-size, border, upscale, downscale/2× area-equivalent 경로도 포함합니다.
이 합성 검사는 모델 추론/사진/운동 정확도 검사가 아닙니다.

공개 fixture는 약71KB의 hash·작은 probe·geometry뿐입니다. 대형 RGB/tensor/사진은 Git에 넣지 않습니다.
실제 기본 OpenCV 경로를 expected로 유지하며, 최적화 OFF는 진단 비교 후 상태를 복구합니다.

## 동일한4장: 기존 차이를 재수치화

원본 source/RGB/Python tensor는 Run 1/2에서 각각 SHA가 동일합니다. 새 촬영은 없었습니다.
각 tensor는 **1,228,800 elements**입니다.

| sample | 기존 mismatch | R / G / B | padding | resized 영역 | 수정 후 mismatch |
|---|---:|---|---:|---:|---:|
| squat_front | 62,014 | 20,494 / 20,070 / 21,450 | 0 | 62,014 | **0** |
| squat_side | 59,361 | 19,851 / 19,709 / 19,801 | 0 | 59,361 | **0** |
| shoulder_press_front | 98,029 | 32,742 / 32,571 / 32,716 | 0 | 98,029 | **0** |
| shoulder_press_side | 78,211 | 26,741 / 25,846 / 25,624 | 0 | 78,211 | **0** |

기존 max float 차이0.003921598196029663, max uint8-equivalent 차이1.000007539987564.
후자의 미세한1 초과는 Float 차이에255를 곱한 표현이며 실제 차이는1 uint8 level입니다.
기존 모든 mismatch가 resampled 영역에만 있었고 padding은0입니다. 네 사진은 모두 resize되므로
native-size 영역은 해당 없음(실제 native-size equality는 별도640×640 synthetic에서 확인).

실제 컴파일된 기존/수정 Kotlin 클래스에 동일 RGB를 넣어 측정했습니다. 최초20개의 channel/x/y,
region, expected/actual float/uint8은 private
`local_validation/exp03_golden/audits/interpolation_20261105_01.json`에 보존했습니다.
그 실제 pixel 자료는 공개 문서/Git에 복사하지 않습니다. `scripts/Exp03PreprocessingAudit.java`는
모델 추론 없이 이 비교만 수행하고 새 ignored JSON 외에는 쓰지 않습니다.

수정 후 host Kotlin **4/4 tensor SHA identical / max error0**, 이어서 실제 Android의
`preprocessingOnlyMatchesPythonBitForBit`도 **4/4 SHA identical / max error0**으로 통과했습니다.
이 preflight가 성공한 뒤에만 전체 실제 Golden을 실행했습니다.

## Actual Golden Run 2

새 PC/device run ID: **actual_20261105_02**. 기존 Run 1은 수정·삭제하지 않았습니다.
8.4.202 원본 소스와 actual EXP03 모델로 새 Python native4회, Android native8회(A/B 각4회)를 실행했습니다.
고유 private run에13개 fixture를 size/SHA 검증 후 전송하고 reference를 마지막에 공개했습니다.

| sample | input SHA | count/order (양쪽) | confidence 최대 차이 | bbox 최대 차이 | 판정 |
|---|---|---|---:|---:|---|
| SQUAT FRONT | identical | 2 / [0,1] | 1.1920929e-7 | 0.000061035156px | PASS |
| SQUAT SIDE | identical | 2 / [0,1] | 3.427267e-7 | 0.00005340576px | PASS |
| SHOULDER_PRESS FRONT | identical | 1 / [1] | 1.1920929e-7 | 0.000030517578px | PASS |
| SHOULDER_PRESS SIDE | identical | 1 / [1] | 3.5762787e-7 | 0.000091552734px | PASS |

- A end-to-end, B identical Python input/native, C frozen actual raw/decoder/NMS 모두 **4/4 PASS**.
- C의 confidence/bbox 차이0. class/count/order는 exact. semantic labels로 결과를 필터링하지 않음.
- confidence tolerance **0.001**, bbox **2px**, NMS/confidence threshold는 변경하지 않음.
- preprocessing은 오히려 **SHA bit equality**로 강화함(기존1byte 허용에 기대지 않음).
- 최대 confidence 차이는3.5762787e-7, 최대 bbox 차이는9.1552734e-5px.
- `allSamplesPassed=true`, `failure=NONE`, actualImageCount4, nativeInferenceCount8.

reference SHA256: `a9fc32327edfcfaab56b6c7eef27f2eb2c5dc68840f2c7954f4ce97997335e18`.
Android result SHA256: `86608442575a0a8492852ed549f3f8cbc7a63aad2d6a46efb4ffb8a34966af6d`.
Run 1 Android result SHA도 `3b9c532237985538f30771074b479609617d20502e281b3e103837d2193ce09a` 그대로 보존했습니다.

Run 2 JSON의 productionContract=NOT_VERIFIED는 **검증 시점 상태**입니다. JSON/테스트가 상수를 바꾼
것이 아니며, 실제 증거 검토 후에만 별도의 코드 수정으로 VERIFIED로 승격했습니다.
두 SQUAT 입력의 부가 SHOULDER_PRESS 검출도 그대로 있습니다. top-label4/4 sanity는 전체 모델
accuracy/모든 사용자의 운동 안전성 보장이 아니며 별도 모델 성능 평가를 대체하지 않습니다.

승격된 최종 APK에서도 같은 reference를 **새 device run `actual_20261105_02_reviewed`**에 주입해
preprocessing-only → actual Golden을 순서대로 재실행했습니다. 두 검사 PASS, 추가 actual native8회,
4장 전체 PASS, productionContract=VERIFIED, cameraPerformance=NOT_MEASURED였습니다.
새 device result SHA256은 `e92ffb05e4672ee450a7b0f93b07be49f73da4ad5605489cdf4df858879739d5`입니다.
따라서 이 단계의 실제 Android image invoke는 최초 Run 2의8회 + 승격 후 재실행8회 = **16회**이며,
별도 synthetic native smoke1회는 이 숫자에 포함하지 않습니다. Python actual invoke는4회입니다.

## 승격 / Runtime / 카메라 경계

canonical identity + 실제 Android load/metadata/native + exact preprocessing + class/count/order +
confidence/bbox + decoder/NMS가 모두 통과하여 **NOT_VERIFIED → VERIFIED**로 코드 리뷰 승격했습니다.
EXP02의 VERIFIED를 상속한 것이 아닙니다. build flag/fixture/model 존재로 승격하는 API는 없습니다.

DEBUG gate ON + 실제 EXP03 READY일 때 predictionAllowed=true, release는 항상 false입니다.
최종 기기 factory/native smoke instrumentation2/2 PASS, 실제 frame 제출 없이 READY/gate를 확인했습니다.
NOT_VERIFIED/MODEL_MISSING/identity mismatch/gate OFF는 계속 차단됩니다.
Primary LANDMARK와 기존 분석 경로는 그대로입니다. 이번에는 실제 카메라를 열거나 운동하지
않았습니다. **Actual Camera = NOT_MEASURED**. 정지 이미지 nativeMs로 camera FPS/latency를 만들지 않습니다.

## 보안·비용·재현

모델/사진/RGB/input/output/reference/result/venv는 ignored PC 및 앱 private noBackup에만 유지했습니다.
공용 저장소/root/클라우드/Supabase 업로드나 사용자 DB 변경은 없습니다. 앱 업데이트는 install -r만
사용했고 clear/uninstall은 하지 않았습니다. 기존 EXP02 증거·모델·사용자 운동 기록은 보존했습니다.
새 API/SDK/유료 서비스/billing 변경은 없습니다. 공개 배포 권리와 앱 전체 무과금 보증은 별개입니다.

```powershell
& local_validation/.venv/Scripts/python.exe scripts/generate-exp03-interpolation-vectors.py --output app/src/test/resources/exp03-opencv-4.13-linear.json --check
.\gradlew.bat :app:testDebugUnitTest --tests '*YoloOpenCvLinear*' --offline --no-daemon '-PverificationBuildDir=dashboard-ui-verification'
```

실제 재현은 [Golden 문서](EXP03_ACTUAL_GOLDEN_VALIDATION.md)의 생성/전송/실행 절차에서 **새 run ID**를
사용합니다. 먼저 `#preprocessingOnlyMatchesPythonBitForBit`, 그 뒤
`#actualExp03MatchesExactVersionPythonReference`를 명시적으로 각각 실행하세요.
기존 run의 전처리/result 파일을 덮어쓰지 않습니다.
최종 전체 build/회귀와 수정 파일 목록은 [rollout](EXP03_RUNTIME_SHADOW_ROLLOUT.md)에 기록합니다.
