# EXP02 두 운동·NMS 계약 승격 리뷰

현재 판정: **NOT_READY_FOR_VERIFIED**. 현재 production 상수는 `NOT_VERIFIED`이며 Landmark Primary / YOLO Shadow / 실시간 prediction 비활성 상태를 유지합니다. 이 문서의 최신 실제 결과 표를 충족하기 전까지 승격 가능으로 표시하지 않습니다.

## 판정 기준

`VERIFIED`는 정의된 Python reference와 Android inference contract의 동등성을 뜻합니다. 전체 모델 정확도, 모든 사용자·카메라 방향의 정확도, 운동 안전성 보장, Primary 전환 허가는 아닙니다.

승격 리뷰에는 아래 실제 근거를 함께 확인합니다.

| 근거 | SQUAT | SHOULDER_PRESS |
|---|---|---|
| canonical model/tensor | PASS(기존 실제 native) | PASS(새 실제 native2회) |
| preprocessing | PASS(기존 actual) | PASS(실제2장 BYTE_IDENTICAL) |
| class decode / confidence / bbox / ordering | PASS(실제 양성2) | PARTIAL(무검출 결과·순서 동등, 실제 class1 양성 없음) |
| negative actual | 기존4장 + 수동후보1 무검출 동등성 PASS | 원본 무검출 결과 보존 |
| NMS 다중 겹침 | PASS(실제 raw2종 및 controlled) | PASS(controlled class-aware), actual class1 overlap 미확보 |
| Android native | 기존 실제 모델 로드/추론 PASS | PASS(실제2회, positive gate는 미달) |
| semantic sanity | 사용자 정답5장 중4검출·1무검출 | 실제2장 모두 무검출 |

confidence threshold `.10`, IoU `.70`, max detections300 및 기존 confidence `.001`/bbox2px actual 비교 한계는 바꾸지 않습니다. 동일 frozen raw의 NMS 비교는 기존 synthetic 한계 confidence `1e-6`/bbox `1e-4px`로 더 엄격하게 검사합니다. 실패를 맞추기 위해 tolerance/order를 넓히지 않습니다.

## 근거 보존과 새 실행

- 기존 SQUAT reference/result/semantic sidecar/실제 사용자 세션 증거는 덮어쓰지 않습니다.
- 새 PC 실행은 `local_validation/exp02_positive/<unique_run>/` 아래에만 둡니다. generator는 비어 있지 않은 출력 폴더를 거부합니다.
- 기기 positive 실행은 `no_backup/yolo/positive-golden/runs/<run>/`, NMS는 `no_backup/yolo/nms-contract/runs/<run>/`으로 분리합니다. 기존 run에는 다시 쓰지 않습니다.
- `positiveEvidenceRun`, `nmsEvidenceRun`은 instrumentation의 검증 자료 경로일 뿐, 앱 contract/실시간 prediction 활성화 플래그가 아닙니다.
- `requiredPositiveClass=SHOULDER_PRESS`는 이미 나온 결과에서 실제 class1 동등성 증거가 있는지 검사합니다. 정답으로 prediction을 필터링·수정하지 않습니다. 둘 다 SQUAT이면 sample runtime PASS일 수 있어도 class1 검증 목표는 미달입니다.

## NMS evidence 종류

`ACTUAL_IMAGE_NMS`: 같은 실제 이미지의 reference native invoke 직후 `Interpreter.get_tensor()`에서 복사한 normalized FLOAT32 `[1,6,8400]`을 사용합니다. pinned LiteRTBackend.forward는 반환 복사본의 xywh를 pixel로 바꾸므로, 그 반환값을 역추정하여 native raw로 쓰지 않습니다. `get_tensor()`의 copy 계약은 설치된 공식 LiteRT2.2.0 코드/docstring으로 확인했습니다. 원본 이미지·raw·reference SHA를 연결합니다.

`CONTROLLED_MODEL_FORMAT_NMS`: 명시적으로 만든 normalized 모델 출력 형식의 입력입니다. 실제 이미지나 실제 모델 prediction으로 표현하지 않습니다. 정확한8.4.176 원본 backend forward/NMS 함수로 Python 정답을 만들고, Android 기존 decoder/postprocessor에 같은 raw를 입력합니다. 별도 전처리/decoder/NMS 알고리즘은 만들지 않습니다.

양쪽 모두 실제 NMS 판정은 기존 구현에 위임합니다. 같은 class의 높은 confidence 유지·낮은 overlap 제거, IoU 경계 양측, 다른 class overlap 유지, strict confidence 경계, score ordering을 검사합니다. 실제 raw가 있어도 suppression witness가 없으면 실제 다중 겹침 PASS로 올리지 않습니다.

## 승격과 실시간 활성화는 별도

코드 리뷰에서 중요한 결합을 확인했습니다. 현재 `YoloExerciseDetector.initialize()`는 contract가 VERIFIED이고 stabilizer 설정이 있으면 READY가 되며, runner는 READY에서 실시간 추론을 허용합니다. **추후 상수만 바꾸면 Dashboard 실시간 Shadow까지 켜질 수 있습니다.**

따라서 후속 승격 변경에서는 먼저 code-level contract 검증과 Dashboard activation의 단계/게이트를 분리해 별도 검증해야 합니다. 이번 작업에서는 상수·실시간 설정·Primary·안정화/FPS interval을 변경하지 않습니다. READY_FOR_VERIFIED_REVIEW 판정도 실시간 활성화 승인을 뜻하지 않습니다.

## 실행 순서

1. 기존 DEBUG 수동 버튼으로 본인 숄더 프레스 정면·측면을 촬영합니다. 전체 최대8장을 유지하며 자동 촬영/기존 자료 자동 삭제는 없습니다.
2. 선택한 새 후보만 private ADB 경로로 새 PC run에 옮깁니다. 사용자가 명시한 운동/방향만 manifest에 기록합니다.
3. Python reference를 `--export-nms-raw`로 실행합니다. 기존 RGB/BCHW/FLOAT32/255/letterbox/decoder/NMS를 재사용합니다.
4. `provision-reference --run-id <run>` 후 native test에 `-e positiveEvidenceRun <run> -e requiredPositiveClass SHOULDER_PRESS`를 전달합니다. 모든 결과를 보존하고 class1 양성 최소1을 검사합니다.
5. NMS reference 생성 후 `provision-nms --run-id <nmsrun>` 및 `Exp02NmsContractTest#sameRawOutputMatchesPinnedPythonNms`에 `-e nmsEvidenceRun <nmsrun>`을 전달합니다.

세션 회귀는 기존 실제 사용자 저장 행을 읽기 전용으로 선택하여 force-stop 이후 새 PID의 동일 진단과 Report 렌더링을 확인합니다. 새 실제 운동을 했다고 주장하거나 fixture를 사용자 DB에 넣지 않습니다. in-memory save/legacy 테스트와 실제 기존 행 재조회는 구분합니다.

## 사용자 History → Report 확인

운동 기록 → History 열기 → 저장한 운동 선택 → 상세 Report → YOLO Shadow 진단 펼치기 → 저장된 model/status/latency/count 확인 순서입니다. 자동 Report 직접 진입과 실제 History 행 탭은 구분하여 기록합니다. prediction 비활성에서는 processedFrames0 및 latency 미측정이 정상일 수 있습니다.

## 개인정보·배포·비용

후보/원본 Golden/모델/raw/reference/venv는 Git 미추적·local-only입니다. private noBackup과 ignored PC 폴더 외 저장소, Supabase/cloud/public storage 업로드는 하지 않습니다. Supabase 보안 스킬은 이 경계를 확인하는 용도로만 적용하며 원격 DB·Auth·RLS·배포·billing을 변경하지 않습니다. 새 유료 API/provider/의존성을 추가하지 않습니다.

기존 DEBUG 수동 UI·8장 제한·소유 파일 삭제·release null factory·APK 모델 exclusion을 유지합니다. DEBUG/release/test APK의 private 자료 미포함과 본 앱 MediaPipe task 유지도 검사합니다. 기존 tracked pose 모델은 수정/삭제하지 않습니다.

## 최신 실행 결과

새 기기에 연결해 추가 스쿼트2장과, 사용자가 별도로 실제 숄더 프레스라고 확인한 새2장을 확보했습니다. 앞의 추가2장은 사용자가 스쿼트라고 명시적으로 정정했습니다. 최초 manifest에서 촬영 완료 답변을 숄더 프레스 정답으로 해석한 것은 잘못이므로 원본을 덮어쓰지 않고 private 라벨 정정 sidecar를 기록했습니다. 폴더/샘플 ID에 shoulder라는 이름이 있어도 ground truth로 쓰지 않습니다.

- SQUAT: 기존 실제 양성2 + 무검출1, 사용자 정정한 추가2장도 Python/Android 저장 시 native 모두 SQUAT 검출. 전체 accuracy 아님.
- SHOULDER_PRESS: 실제 candidate2 / class1 positive0 / no detection2 / wrongclass0. Python·Android 모두 무검출이며 두 입력의 전처리 tensor는 BYTE_IDENTICAL, 각 sample runtime equivalence PASS. 하지만 양성 최소1이 없어 positive gate는 FAIL(`NO_POSITIVE_EQUIVALENCE_EVIDENCE`). 이 실패를 구현 회귀나 PASS로 바꾸지 않습니다.
- NMS: 정확한 reference로 controlled6종 + 기존 실제 이미지 raw3종을 실행하고 Android 기존 decoder/NMS로9종 모두 PASS. 실제 raw2종에서 threshold 초과10개 → 최종1개, 각9개 same-class suppression witness 확인. 9종의 confidence/bbox 오차 모두0, class/order exact. 실제 무검출 raw1종은 overlap 증거로 세지 않습니다.
- Persistence regression: 기존 실제 사용자 저장 행을 force-stop 후 새 PID에서 재조회하고 동일 집계/SHA 및 Report 접힘·펼침·저장 metric 표시 PASS. in-memory save/legacy 및 private storage8개도 이번 기기에서 새로 PASS. 새 사용자 운동을 생성/삽입한 것은 아닙니다.
- VERIFIED Review: NOT_READY_FOR_VERIFIED.

DEBUG/release/test APK 빌드는 성공했습니다. JVM522 결과는 main/test 소스 변경이 없어 Gradle UP-TO-DATE로 유지됐으며, 새로 추가한 검증은 Python/Node 및 별도 Android instrumentation으로 구분해 기록합니다.

Fresh Node91 / Python80(기존46 + next-stage11 + NMS23) 통과. 기기에서는 storage/Room8개 + NMS1개(9종) + 기존 실제 행 restart/Report2개가 통과했습니다. 실제 숄더 프레스 positive gate1개는 양성 부재로 실패했으며 전체 device 검사가 모두 PASS라고 표시하지 않습니다. APK3개 private packaging/release 경계 및 변경 public text12개 보안 패턴 검사도 통과했습니다.

| Contract Evidence | 최종 판정 | 범위 |
|---|---|---|
| Model identity | PASS | filename/9,894,030bytes/canonical SHA256 유지 |
| Tensor | PASS | input FLOAT32[1,3,640,640], output FLOAT32[1,6,8400] |
| Preprocess | PASS | pinned RGB/BCHW/FLOAT32/255/center letterbox114, 실제 비교 |
| Decode | PARTIAL | class0 actual 및 class1 controlled 통과, class1 actual positive 없음 |
| BBox / Confidence | PARTIAL | class0 actual 및 frozen raw 동등, class1 실제 양성값 없음 |
| NMS | PASS | ACTUAL_IMAGE_NMS2 overlap + CONTROLLED_MODEL_FORMAT_NMS6 |
| Synthetic Golden | PASS | 기존 JVM 결과 + 이번 fresh controlled Python/Android |
| Negative Actual | PASS | 기존4장 보존, 이번 숄더 프레스2장 무검출 동등 |
| SQUAT Positive | PASS | 기존 실제 양성 근거 보존 |
| SHOULDER_PRESS Positive | FAIL | 실제2장 모두 무검출, 목표class1 양성 미확보 |
| Android Native | PASS | 새 실제 모델 로드/metadata/추론2회 성공 |
| Persistence | PASS | in-memory save/legacy + 기존 실제 행 restart/Report 회귀 |

현재 관측은 동일 이미지에서 양측 무검출이므로 Android만의 prediction 불일치 증거는 아닙니다. 그렇다고 class1 양성 계약 검증이나 전체 모델 성능을 통과한 것은 아닙니다. 새로운 class1 실제 양성 자료 확보 또는 모델/입력 도메인의 별도 성능 검토가 필요하며, 재학습·threshold 변경은 이번 범위에 포함하지 않았습니다.

## NMS 결과 요약

| 구분 / case | threshold 초과 후보 | Python/Android 최종 수 | 순서 | 판정 |
|---|---:|---:|---|---|
| controlled same class overlap | 3 | 1 / 1 | [0] | PASS |
| controlled IoU .6999 | 2 | 2 / 2 | [0,0] | PASS |
| controlled IoU .7001 | 2 | 1 / 1 | [0] | PASS |
| controlled different class overlap | 2 | 2 / 2 | [0,1] | PASS |
| controlled descending score | 4 | 4 / 4 | [1,0,1,0] | PASS |
| controlled strict confidence .10 | 1 | 1 / 1 | [0] | PASS |
| actual image raw 무검출 | 0 | 0 / 0 | [] | 동등성 PASS, overlap 아님 |
| actual image raw 양성2종(각각) | 10 | 1 / 1 | [0] | overlap PASS, suppression witness 각9개 |

NMS harness 자체는 모델을 로드하거나 native inference하지 않습니다. 실제 raw는 별도 actual Python invoke에서 얻었고, Android NMS 검사는 같은 frozen raw를 사용합니다. 기존 실제 Android native 모델 검증과 혼동하지 않습니다.

## 변경 파일(이번 단계 전체12개)

```text
app/src/androidTest/java/com/example/arptapp/data/YoloSessionPersistenceDeviceTest.kt
app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp02PositiveModelGoldenTest.kt
app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp02NmsContractTest.kt
scripts/generate-exp02-positive-reference.py
scripts/summarize-exp02-positive-comparison.py
scripts/transfer-exp02-positive.py
scripts/generate-exp02-nms-reference.py
scripts/test-exp02-next-stage.py
scripts/test-exp02-nms-reference.py
scripts/exp02-next-stage.test.mjs
docs/ml/EXP02_POSITIVE_VALIDATION.md
docs/ml/EXP02_VERIFIED_REVIEW.md
```

main/release 앱 코드·리소스·의존성은 변경하지 않았습니다. private 보고서·새 candidate/reference/raw/bundle·라벨 정정은 ignored 로컬 자료이며 공개 source 목록에 넣지 않습니다. 자동 commit/push하지 않았습니다.
