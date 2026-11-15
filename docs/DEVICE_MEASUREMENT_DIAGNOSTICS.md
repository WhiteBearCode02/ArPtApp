# 7종 실기기 횟수 측정 진단

## 이번 수정의 범위

수동 선택 → 세션 고정 → MediaPipe 측정 → counter → 분석/리포트 구조를 유지한다.
Landmark 자동 분류는 DEBUG 진단용이며 YOLO는 Shadow, EXP04는 `NOT_VERIFIED`다.
분류 임계값, 각도 임계값, visibility 0.65, SIDE_BEND 60° 한도는 변경하지 않았다.

### 확인한 공통 취약점

수정 전 `DashboardActivity.processLandmarks()`는 `!measurement.isEvaluable` 한 프레임에
`invalidatePendingAnalysis()`를 호출했다. 이 함수는 counter의 미완성 회차, 분석 엔진,
각도 버퍼를 모두 지웠다. 사람이 감지되지 않는 프레임도 같은 경로였다.
`ExpandedExerciseCounter` 역시 null/invalid 샘플 하나로 해당 팔/회차 상태를 지웠다.
SQUAT/SHOULDER_PRESS는 직접 counter API에서 null일 때 즉시 초기화했으며,
연속 프레임 누락 timeout은 신규 운동에만 있었다.

따라서 잠깐의 관절 가림이 반복되면 준비/전환/복귀를 끝까지 연결하지 못할 수 있었다.
이것은 **코드로 확인한 취약점**이며, 모든 실기기 count 실패의 원인이 이것 하나라는
의미는 아니다. 각도 자체가 기준에 도달하지 않는 문제는 실측으로 구분해야 한다.

## 일관된 누락 정책/API

`ExerciseCounter.processFrame()`이 `FrameResult`로 유효성, 누락 시간, 미완성 회차
초기화 여부, 완료 회차를 반환한다. 기존 `processMeasurement`/`processAngle`은
호환 adapter다. 화면은 이 동일한 결과로 분석 버퍼 초기화 여부를 결정한다.

- 최초 invalid부터 시간을 기록한다.
- 1,000 ms 미만: 해당 프레임만 제외한다. 완료 횟수와 armed/turned/stability 상태는 보존한다.
- 1,000 ms 이상: 미완성 counter/분석/각도 버퍼만 초기화한다. 완료 횟수는 보존한다.
- 마지막 콜백 이후 1,000 ms 이상 아무 프레임도 없었으면 복귀 시에도 초기화한다.
- 동일/역행/음수 timestamp는 카운트에 쓰지 않는다.
- 취소/방향 변경/세션 재시작 같은 명시적 초기화는 즉시 적용한다.
- BICEP_CURL은 좌우 각각 같은 정책의 타이머를 갖는다. 반대 팔이 유효하다고
  누락된 팔의 오래된 동작이 무기한 살아 있지는 않는다.

새 timeout을 만들지 않고 `ExerciseMotionPolicy.MAX_FRAME_GAP_MS`를 공유한다.
누락 중에는 baseline/목표 도달 frame을 생성하거나 count를 증가시키지 않는다.

## DEBUG 진단 보기

운동을 수동 선택하고 시작한 뒤 기존 **YOLO 진단 보기**를 열면 스크롤 영역 맨 위에
**횟수 측정 진단 · DEBUG**가 표시된다. 패널은 기존 144dp 스크롤 영역을 그대로 사용한다.
제목/카메라/횟수/시작·종료 버튼 위로 떠 있는 새 overlay는 없다.

공통: selected/session, 자동 감지(진단만), 촬영 방향, 측정 가능 YES/NO,
대표 각도, invalid 시간, 완료 횟수, phase, 사유, 관련 관절 최소 visibility.
자동 감지는 일반 운동 제목/종목 문구를 바꾸지 않는다.

| 운동 | 표시값 | 현재 기준(변경 없음) |
|---|---|---|
| SQUAT | 무릎 대표 각도 | 내려감 ≤90°, 올라옴 ≥160° |
| SHOULDER_PRESS | 팔꿈치 대표 각도 | 아래 ≤70°, 위 ≥160° |
| BICEP_CURL | 왼팔/오른팔 각도와 대표값 | 전환 ≤70°, 복귀 ≥150° |
| PUSH_UP | 팔꿈치 대표/좌우 각도 | 전환 ≤100°, 복귀 ≥155° |
| BENT_OVER_ROW | 팔꿈치 대표/좌우, 측정 가능 시 몸통 기울기 | 전환 ≤90°, 복귀 ≥150° |
| TRICEPS_EXTENSION | 팔꿈치 대표/좌우 | 굽힘 ≤90°, 폄 ≥155° |
| SIDE_BEND | signed/absolute 기울기 | 기울임 ≥15°, 복귀 ≤5° |

상태: `WAIT_BASELINE`은 시작 자세를 기다림, `ARMED`는 시작 자세 관찰 완료,
`MOVING`은 이동 중, `TURNED`는 목표 도달이다. 기존 squat/press의 `WAIT_TURN`은
목표 각도 진입 대기다. 뒤의 숫자는 안정 프레임 수이며 임계값은 기본 3프레임이다.
TRICEPS_EXTENSION은 기존 정책대로 안정된 폄에서 완료하고 다음 굽힘을 기다린다.

SIDE_BEND의 사유는 실제 계산과 같은 코드에서 나온다:
`LOW_VISIBILITY`, `SHOULDER_AXIS_TOO_NARROW`, `HIP_AXIS_TOO_NARROW`,
`AXIS_SIGN_MISMATCH`, `TORSO_VERTICAL_DEGENERATE`, `OUTSIDE_60_DEG_RANGE`,
`MISSING_LANDMARK`, `NON_FINITE_OR_ASPECT`. `NO_PERSON`은 대상 유실이다.
`UNSUPPORTED_CAMERA_VIEW`는 아직 방향을 선택하지 않았거나 SIDE_BEND에 측면을 선택한 경우다.

Logcat의 `RepTrace`도 같은 요약을 최대 초당 2회 출력한다. 좌표 배열, 이미지, 계정,
토큰은 출력하지 않는다. release에서는 진단 표시/로그를 호출하지 않는다.
진단 snapshot은 메모리 전용이며 Room/API/Report 저장 schema에 추가하지 않는다.

## 팔꿈치 측정 routing (기존 정책 보존)

- FRONT 컬: 유효한 팔꿈치 중 작은 각도가 대표값이지만 실제 카운트는 좌우 별도 값으로 실행한다.
- FRONT 푸시업/로우/트라이셉스: 유효한 쪽 중 관절 최소 visibility가 높은 팔이 대표값이다.
- SIDE: 필요한 관절의 visibility가 높은 한쪽 팔만 사용한다. 가려진 팔은 null이며 0°로 대체하지 않는다.
- SQUAT/SHOULDER_PRESS의 기존 대표 각도 선택도 유지한다.

## 검증 상태

검증 결과:

- 전체 JVM: 91 suites / 979 tests / failures 0 / errors 0 / skipped 0.
- Node: 173 PASS.
- Android serializer → 저장/전송 계약 fixture: 19 PASS (로컬 검증, 원격 호출 없음).
- 새 회귀 테스트: 7종 짧은 누락·긴 누락·callback gap·전환/복귀 jitter·분석 연속성,
  7종 실제 geometry → low visibility → 측정 → counter → 분석,
  curl 좌우 독립 dropout, 측정값 routing, SIDE_BEND invalid 사유 및 진단 formatter.
- 최초 실행의 신규 triceps fixture 2개는 동작 시간이 기존 최소 600 ms보다 짧아 실패했다.
  fixture의 sample 간격만 100→150 ms로 바로잡아 재실행했다. 제품 threshold/timing은 그대로다.
  최초 실패 log/XML은 로컬 ignored 경로에 보존했다.
- assembleDebug: PASS (`build/device-diagnostic-gradle-02.log`, 정상 종료 4m 38s).
- packaging: PASS. assets는 MediaPipe task와 `standard_poses/squat.json`뿐이다.
  private YOLO 모델, Golden image/tensor, local_validation, keystore/env/credentials 경로는 없다.
  기존 OpenCV NOTICE 내용도 보존됐다.
- APK 서명: PASS. 기존 휴대폰 앱과 일치하는 이 PC의 원래 자동 debug 인증서로 빌드했다.
- 이번 소스에 대한 lintDebug: `NOT_RUN` (별도 검증 대상). 이전 APK의 lint PASS를
  이번 변경분 PASS로 재사용하지 않는다.

Synthetic fixture는 실기기 PASS로 간주하지 않는다. 새 진단 APK의 설치/운동 검증은
사용자 결과를 받기 전까지 `NOT_RUN`이다. 이전 APK/모델/Golden/환경은 삭제하지 않는다.

### 진단 APK

정확한 파일:

```text
C:\Users\dlgus\AndroidStudioProjects\ArPtApp\build\deliverables\AIRPTCoach-7Exercises-DeviceDiagnostic-debug.apk
```

- 36,805,555 bytes
- SHA256: `629947cb1b02b20d9e354d3a3fb01ced563201080b5f55f13284638a00becb1c`
- source: `build/device-diagnostic-verification/outputs/apk/debug/app-debug.apk`
- 공개 인증서 SHA256: `c0f23e8d0eb73152cba4cfa5bbb84279d93b2f8d31e2fc8cc98e535aedbd34db`
- 기존 ManualSelection 원본/동일 키 서명본/SideBendFix APK의 SHA256 불변 확인.
- 이번 작업에서는 새 진단 APK를 실기기에 설치하거나 앱을 강제 종료하지 않았다.
- 서명이 같은 기존 앱에는 삭제 없이 업데이트 설치한다. signature 오류가 생기면
  제거/데이터 초기화를 하지 말고 중단한다. 성공적인 설치 자체도 운동 카운트 PASS 증거는 아니다.

### 변경 파일

공통 경로는 `app/src/main/java/com/example/arptapp/`이다.

- `DashboardActivity.kt`: count/분석에 같은 continuity 결과 적용, DEBUG bounded 진단 호출.
- `domain/counter/MeasurementContinuity.kt`: 신규 공통 dropout 정책.
- `domain/counter/ExerciseCounter.kt`: 7종 frame admission/result API, legacy adapter.
- `domain/counter/ExpandedExerciseCounter.kt`: 좌우 독립 continuity, read-only phase.
- `domain/analyzer/ExerciseMeasurementStrategy.kt`: SIDE_BEND geometry 사유 공유, 유실된 elbow quality 보존.
- `presentation/diagnostics/MeasurementDiagnosticFormatter.kt`: 메모리 전용 snapshot/문구.
- `ui/dashboard/YoloDiagnosticsPanel.kt`: DEBUG 측정 요약 표시 API.
- `app/src/main/res/layout/view_yolo_diagnostics_panel.xml`: 기존 패널 위쪽 진단 텍스트.
- `app/src/test/java/com/example/arptapp/domain/counter/SevenExerciseCounterTest.kt`: 즉시 초기화 가정만 prolonged 정책으로 교체, 기존 signed trajectory 유지.
- `app/src/test/java/com/example/arptapp/domain/counter/MeasurementDropoutTest.kt`: 신규 continuity/dropout/jitter/arm isolation 테스트.
- `app/src/test/java/com/example/arptapp/domain/analyzer/MeasurementRoutingDiagnosticTest.kt`: 실제 입력값 및 사유 테스트.
- `app/src/test/java/com/example/arptapp/domain/report/ManualSelectedExercisePipelineTest.kt`: 7종 low-visibility pipeline 회귀 추가.
- `app/src/test/java/com/example/arptapp/presentation/diagnostics/MeasurementDiagnosticFormatterTest.kt`: 7종 기준 및 null 표시 테스트.
- `scripts/manual-session-routing.test.mjs`, `scripts/pose-stability.test.mjs`: 변경된 frame API에 맞게 기존 selected routing 정적 검증 유지.
- `scripts/measurement-diagnostics.test.mjs`: DEBUG 경계/2Hz/로컬 전용/분석 expiry 검증.
- `docs/DEVICE_MEASUREMENT_DIAGNOSTICS.md`: 본 문서와 실기기 기록표.

새로운 외부 API/네트워크/SDK/결제 연동/권한/저장 schema는 추가하지 않았다.
원격 배포·migration·모델 승격·자동 commit/push/PR/merge는 수행하지 않는다.

## 실제 기기 기록표

APK 설치 후 운동 시작 → 종목 선택 → 진단 펼치기 → 무리 없는 범위에서 5회 수행한다.
SIDE_BEND는 FRONT에서 오른쪽 5회 + 왼쪽 5회, 컬은 우선 한 팔 5회로 구분한다.
양팔 컬 동시 완료는 기존 정책상 좌우 각각 1회로 기록될 수 있다.
분류 결과가 달라도 선택 운동이 고정돼 있으면 **측정/카운터**부터 조사한다.

| 운동 | 촬영 방향 | 최저/최고 실제 각도 | NO 사유·최장 누락 ms | phase 변화 | 수행/실제 count | TTS·피드백 | Report·History |
|---|---|---|---|---|---|---|---|
| 스쿼트 | FRONT 또는 SIDE | 미검증 | 미검증 | 미검증 | 5 / 미검증 | 미검증 | 미검증 |
| 숄더 프레스 | FRONT 또는 SIDE | 미검증 | 미검증 | 미검증 | 5 / 미검증 | 미검증 | 미검증 |
| 바이셉 컬 | FRONT 또는 SIDE | 좌/우 별도 | 미검증 | 미검증 | 한 팔 5 / 미검증 | 미검증 | 미검증 |
| 푸시업 | SIDE 권장 | 미검증 | 미검증 | 미검증 | 5 / 미검증 | 미검증 | 미검증 |
| 벤트오버 로우 | SIDE 권장 | 미검증 | 미검증 | 미검증 | 5 / 미검증 | 미검증 | 미검증 |
| 트라이셉스 익스텐션 | FRONT 또는 SIDE | 미검증 | 미검증 | 미검증 | 5 / 미검증 | 미검증 | 미검증 |
| 덤벨 사이드 벤드 | FRONT | signed 좌/우 별도 | 미검증 | 미검증 | 우5+좌5 / 미검증 | 무릎 안내 금지 | 미검증 |

`YES`인데 목표 각도/안정 프레임에 도달하지 않는지, `NO`가 반복되는지,
`TURNED` 이후 복귀를 못 보는지를 구분해 기록한다. PASS를 위해 임계값을 바꾸지 않는다.
사진/전체 landmark/로그인 정보는 공유할 필요 없다. 운동 이름과 위 요약 수치만 전달한다.
