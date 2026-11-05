# EXP03_FINAL FP32 Android 계약

2026-11-05 · **VERIFIED / Landmark Primary / release Shadow OFF / camera NOT_MEASURED**

Run 1은 **FAIL (1/4 PASS)**로 보존했습니다. 보간 수정 뒤 Run 2는 **PASS (4/4)**,
input tensor4/4 byte identical, confidence 최대3.5762787e-7 / bbox 최대9.1552734e-5px입니다.
전체 조건을 확인한 뒤에만 EXP03을 VERIFIED로 승격했습니다. [실제 결과·원인](EXP03_PREPROCESSING_PARITY.md).

## 실제 파일 확인

| 항목 | 직접 확인한 값 |
|---|---|
| modelId | `yolo26n_exp03_final_fp32` |
| filename | `airptcoach_yolo26n_exp03_final_fp32.tflite` |
| exact bytes | **9,894,032** |
| SHA256 | `3d2b6bc537d8aef5a19b671226d73c98b49411574a193be72e225086202d1167` |
| 제공 hash 대조 | PASS |
| static input | FLOAT32 `[1,3,640,640]`, index 0 |
| static output | FLOAT32 `[1,6,8400]`, index 658 |
| classes | 사용자 확정 `0=SQUAT`, `1=SHOULDER_PRESS` |

로컬 원본 모델을 다시 다운로드·교체·변환·수정하지 않았습니다. Git/APK에 넣지 않습니다.
사용자 동의 후 본인 휴대폰 DEBUG 앱의 private noBackup 저장소로만 provisioning했습니다.
기존 bounded FlatBuffer parser를 재사용한 새 `scripts/inspect-exp03-model.py`로 실제 tensor를 읽었습니다.
기존 Python 3.11.9 / ai-edge-litert 2.2.0 환경의 synthetic zero-input native invoke 1회도 성공했습니다.
**이는 PC 실행이며 Android TF Lite 2.14.0 호환성, image Golden 동등성 또는 실제 운동 정확도가 아닙니다.**
실제 상세 evidence는 ignored `build/exp03-validation/model-inspection-20261105.json`에만 있습니다.

## 모델별 provenance와 공유 알고리즘

`Exp03YoloContract.verification`은 EXP03 Run 2 실제 증거를 검토해 `VERIFIED`로 승격했습니다.
EXP02 VERIFIED는 EXP02에만 해당하며 상속하지 않습니다.
`YoloTensorRuntime.modelId`와 계약 modelId가 다르면 detector는 `CONTRACT_NOT_VERIFIED`로 닫고
effective verification도 NOT_VERIFIED로 표시합니다. 모델/flag/fixture 존재로 승격할 수 없습니다.

`YoloRaw640Algorithms`는 기존 전처리·decoder·NMS 알고리즘을 조립할 뿐 검증 근거를 제공하지 않습니다.
canonical `YoloBitmapPreprocessor`는 OpenCV uint8 parity 구현을 사용합니다.
historical `Exp02BitmapPreprocessor`는 기존 `Exp02RgbPreprocessor`를 명시적으로 주입하는 wrapper로
남겼습니다. 기존 EXP02/transform/decoder/postprocessor의 수학·임계값은 변경하지 않았습니다.
대규모 rename이나 알고리즘 복제를 하지 않았습니다.

| 항목 | EXP03에서의 상태 |
|---|---|
| filename / bytes / SHA | 직접 대조 PASS |
| tensor shape / dtype | static + PC Interpreter + 실제 Android Interpreter PASS |
| logical RGB / BCHW / FLOAT32 | 정확한8.4.202 source 실행 및 Android tensor 비교 확인 |
| /255 / centered letterbox114 / resize | geometry/padding 유지, Run 2 실제4장 전체 tensor SHA 동일. 64개 synthetic 전체 RGB/tensor SHA 동일 |
| normalized center xywh / class score 해석 | 실제 EXP03 frozen raw 대조 4/4 PASS |
| external class-aware NMS / confidence | frozen raw 및 실제 end-to-end4/4 PASS, 기존 tolerance 유지 |
| Android Interpreter load / native inference | 실제 authorized 휴대폰, 기존 TF Lite 2.14.0에서 Run 2 actual-image invoke8회 PASS (이전 smoke와 구분) |
| Android image Golden equivalence | Run 1 FAIL 보존 → Run 2 실제4장4/4 PASS → VERIFIED |
| 실제 CameraX·MediaPipe 병행 FPS/latency/thermal | NOT_MEASURED |

Shape나 PC invoke만으로 좌표 channel 의미·정규화·NMS 계약을 역추정하지 않습니다.
후속 요청에서 export version을8.4.202로 확정했고, 공식 PyPI wheel SHA를 고정해 해당 버전 원본 함수를
실제로 실행했습니다. 기존 설치된8.4.176 distribution이나 EXP02 source loader를 대신 실행하지 않았습니다.
Python native4회, 실제 Android native8회(A/B 각4회), frozen raw decoder/NMS4건을 분리했습니다.
Run 1의 미검증 상태는 보존했으며, Run 2 재생성 reference/실제 Android 비교가 모두 통과한 뒤
코드 상수만 별도로 승격했습니다. reference/test 자체가 verification을 바꾸는 API는 없습니다.
기존 EXP02 reference JSON·raw·이미지 결과를 EXP03 결과로 재라벨링하지 않습니다.
새 instrumentation은 합성 solid-color를 메모리에서만 사용하며 `/255` channel 위치, 실제 metadata,
native 실행과 finite 출력·공유 decoder 경로를 점검합니다. 이 smoke 통과도 Golden PASS가 아닙니다.
이전 실기기 `Exp03NativeContractDeviceTest` 2/2 PASS는 당시 미검증 factory의 prediction=false와
프레임 제출 거부를 확인한 이력입니다. 승격 후에는 ready/gate를 별도 재검사합니다.
합성 입력 nativeMs=128.967395는 이전 단일 invoke 관찰값이며
실제 카메라 속도가 아닙니다. 이미지 Golden/좌표·confidence·NMS 동등성 증거를 대체하지 않습니다.

## 사용자 제공 Colab 결과 — 이번에 재평가하지 않음

Subject-held-out Test **2,692장** 기준입니다. Android 정확도/성능으로 표현하지 않습니다.

| metric | EXP03_FINAL PyTorch | FP32 TFLite |
|---|---:|---:|
| Precision | 0.9213 | 0.9144 |
| Recall | 0.9369 | 0.9296 |
| F1 | 0.9290 | 0.9219 |
| mAP50 | 0.9551 | 0.9486 |
| mAP50-95 | 0.8014 | 0.7936 |

제공된 변환 delta: F1 **-0.007145**, mAP50-95 **-0.007830** (제공된 원래 정밀도 값, 표에서 재계산하지 않음).
대표 Test 12장의 PT↔TFLite class match **12/12**, confidence 평균 차이 **0.00853748**,
bbox 평균 IoU **0.99399749**도 사용자 제공 값입니다. 원본 Colab 결과를 이번에 재실행한 것이 아닙니다.
Intel Xeon의 141.8ms를 Android 성능으로 가져오지 않습니다.

## 승격 조건

canonical identity + Android load/metadata/native invoke + EXP03-specific actual-image
preprocess/decoder/confidence/bbox/classes/NMS 동등성 근거를 모두 확인하고 코드 리뷰해야 합니다.
Run 2의 canonical identity / load / native / exact tensor / class / count·order / confidence·bbox /
decoder·NMS가 모두 통과해 **VERIFIED**입니다. DEBUG gate ON + READY일 때만 prediction을 허용합니다.
release는 OFF, Primary는 LANDMARK이며 실제 camera baseline은 다음 단계입니다.
실패를 통과시키기 위한 tolerance/threshold 조정이나 dependency upgrade를 하지 않습니다.

배포/권리 확인은 별개입니다. 모델은 로컬 provisioning 원본이며 공개 binary가 아닙니다.
[실기기 절차](EXP03_ANDROID_DEVICE_SHADOW_CHECKLIST.md) · [실행 정책/검증 기록](EXP03_RUNTIME_SHADOW_ROLLOUT.md)
