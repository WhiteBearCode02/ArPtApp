# EXP03_FINAL FP32 Android 계약

2026-11-05 · **NOT_VERIFIED / Landmark Primary / release Shadow OFF**

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

`Exp03YoloContract.verification`은 고정 `NOT_VERIFIED`입니다. EXP02 VERIFIED는 EXP02에만 해당합니다.
`YoloTensorRuntime.modelId`와 계약 modelId가 다르면 detector는 `CONTRACT_NOT_VERIFIED`로 닫고
effective verification도 NOT_VERIFIED로 표시합니다. 모델/flag/fixture 존재로 승격할 수 없습니다.

`YoloRaw640Algorithms`는 기존 전처리·decoder·NMS 알고리즘을 조립할 뿐 검증 근거를 제공하지 않습니다.
`YoloBitmapPreprocessor` 구현에 과거 이름 `Exp02BitmapPreprocessor` typealias를 남겼으며,
기존 `Exp02RgbPreprocessor`/transform/decoder/postprocessor의 수학·임계값은 변경하지 않았습니다.
대규모 rename이나 알고리즘 복제를 하지 않았습니다.

| 항목 | EXP03에서의 상태 |
|---|---|
| filename / bytes / SHA | 직접 대조 PASS |
| tensor shape / dtype | static + PC Interpreter + 실제 Android Interpreter PASS |
| logical RGB / BCHW / FLOAT32 | 사용자 제공 계약, 공유 후보 구현. Android actual 비교 NOT_VERIFIED |
| /255 / centered letterbox114 / resize | EXP02에서 검증한 공유 후보. EXP03 actual Golden NOT_VERIFIED |
| normalized center xywh / class score 해석 | 공유 decoder 후보. EXP03 실제 raw/reference 대조 NOT_VERIFIED |
| external class-aware NMS / confidence | 공유 후보, EXP03 actual Golden NOT_VERIFIED |
| Android Interpreter load / native inference | 실제 authorized 휴대폰, 기존 TF Lite 2.14.0에서 load 및 합성 입력 invoke 1회 PASS |
| Android image Golden equivalence | NOT_RUN / NOT_VERIFIED |
| 실제 CameraX·MediaPipe 병행 FPS/latency/thermal | NOT_MEASURED |

Shape나 PC invoke만으로 좌표 channel 의미·정규화·NMS 계약을 역추정하지 않습니다.
EXP03 정확한 export/preprocess/reference 근거와 실제 Android 비교가 준비될 때 후보를 검증해야 합니다.
기존 EXP02 reference JSON·raw·이미지 결과를 EXP03 결과로 재라벨링하지 않습니다.
새 instrumentation은 합성 solid-color를 메모리에서만 사용하며 `/255` channel 위치, 실제 metadata,
native 실행과 finite 출력·공유 decoder 경로를 점검합니다. 이 smoke 통과도 Golden PASS가 아닙니다.
실기기 `Exp03NativeContractDeviceTest` 2/2 PASS이며 미검증 factory의 prediction=false,
프레임 제출 거부도 확인했습니다. 합성 입력 nativeMs=128.967395는 단일 invoke 관찰값이며
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
Android native smoke는 통과했지만 EXP03 actual-image Golden reference/동등성 검증이 없으므로
**NOT_VERIFIED 유지**가 정상입니다. 실시간 prediction은 계속 비활성입니다.
실패를 통과시키기 위한 tolerance/threshold 조정이나 dependency upgrade를 하지 않습니다.

배포/권리 확인은 별개입니다. 모델은 로컬 provisioning 원본이며 공개 binary가 아닙니다.
[실기기 절차](EXP03_ANDROID_DEVICE_SHADOW_CHECKLIST.md) · [실행 정책/검증 기록](EXP03_RUNTIME_SHADOW_ROLLOUT.md)
