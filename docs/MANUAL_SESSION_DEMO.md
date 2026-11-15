# 7종 운동 수동 선택 시연 가이드

## 세션 라우팅 정책

운동 자동 분류가 흔들려도 다른 운동의 카운터·코칭으로 바뀌지 않도록 **사용자가 선택한 종목을 세션의 유일한 라우팅 기준**으로 사용한다.

```text
운동 시작 → 7종 선택 → 촬영 방향 확인 → beginSession(selectedExercise)
                                          ↓
                                  MANUAL_SELECTED 세션 잠금
                                          ↓
                   측정 → 카운터 → 회차 분석 → 코칭/TTS → 리포트/기록

Landmark 자동 분류 ──→ 진단만 (세션 변경 불가)
EXP04 YOLO Shadow ──→ 진단만 (NOT_VERIFIED 유지)
```

- 선택 목록의 source of truth는 `ExerciseType.supported`다. `LUNGE`, `UNKNOWN`, `IDLE`은 선택 항목이 아니다.
- 화면에는 각 종목의 `displayName`을 사용한다. 취소·바깥 영역·뒤로가기 등으로 선택을 취소하면 세션·카운터·TTS를 시작하지 않는다.
- `MainViewModel.beginSession(selectedExercise)` → `SessionExerciseLock.lockSelectedExercise(...)`의 명시적 API가 종목을 잠근다.
- 수동 세션의 `selectionSource`는 `MANUAL_SELECTED`다. 레거시 자동 경로의 `observe()` / `observeStable()`은 이를 바꿀 수 없다. 기존의 **0회 한 번 자동 교정도 수동 세션에는 적용되지 않는다.**
- 이미 잠긴 세션에서 다시 시작을 요청하면 거부한다. 운동 종료 후 명시적 `resetSession()`을 거쳐 다음 종목을 선택할 수 있다.
- 시작 직후 선택 종목으로 카운터·분석·미완료 상태·횟수 0·기준 기록·제목·단위·시작 음성을 준비한다. 자동 감지 결과를 기다리지 않는다.
- 사용자가 실제로 다른 운동을 하더라도 자동으로 교체하지 않는다. 잘못 선택했다면 종료 후 다시 선택한다. **수동 선택은 자세 측정 정확도나 운동 수행의 안전성을 보증하지 않는다.**
- 기존 진단 UI의 `Primary`/관절 기반 판정은 자세 측정 주체를 가리키며, 종목 선택 권한은 사용자에게 있다. 진단의 `expectedSessionExercise` 역시 선택 종목이지 검증된 사진/실운동 정답 라벨이 아니다. 기존 저장 DTO에는 선택 출처 필드가 없으므로 과거 기록을 수동/자동 선택 세션으로 소급 구분하지 않는다.

예: 덤벨 사이드 벤드 선택 후 자동 감지가 `SQUAT`여도 실제 세션은 `SIDE_BEND`다.

```text
제목: 덤벨 사이드 벤드 운동 중
횟수: 0회
운동 단위/선택 상태: 덤벨 사이드 벤드
DEBUG 자동 감지: SQUAT (진단이며 라우팅 권한 없음)
```

## 촬영 방향: 기존 코드에 근거한 안내

여기서 정면/측면은 **운동하는 몸을 바라보는 촬영 방향**이며 전면/후면 렌즈와 다르다. 새 생체역학 기준이나 각도 threshold를 만들지 않았다.

| 운동 (`ExerciseType`) | 시연 방향 | 기존 근거와 제한 |
|---|---|---|
| 스쿼트 (`SQUAT`) | 정면 또는 측면 | `PoseAngleExtractor.measurePose()`가 양쪽 무릎/측면 가시 관절을 측정한다. 측면과 정면의 분석 지표는 다르다. |
| 숄더 프레스 (`SHOULDER_PRESS`) | 정면 또는 측면 | 기존 팔꿈치 측정과 가시 관절 선택을 유지한다. |
| 바이셉 컬 (`BICEP_CURL`) | 정면 권장 | `ExerciseMeasurements.elbows()`가 정면 양팔/측면 가시 한팔을 구분한다. 양팔 비교는 정면에서만 가능하다. |
| 푸시업 (`PUSH_UP`) | **측면 권장** | `PushUpMeasurementStrategy`의 팔꿈치 각도는 정면에서도 측정할 수 있으나 몸통 정렬(`bodyLineDeviation`)은 측면 전용이다. 정면은 안내 확인 후 허용한다. |
| 벤트오버 덤벨 로우 (`BENT_OVER_ROW`) | **측면 권장** | `BentOverRowMeasurementStrategy`의 팔꿈치 각도는 정면도 가능하나 몸통 기울기는 측면에서만 얻는다. 정면은 안내 확인 후 허용한다. |
| 오버헤드 트라이셉스 익스텐션 (`TRICEPS_EXTENSION`) | 정면 또는 측면 | 기존 팔꿈치/상완 가시성 측정을 유지한다. |
| 덤벨 사이드 벤드 (`SIDE_BEND`) | **정면 필요** | `SideBendMeasurementStrategy`는 `FRONT`에서만 signed lateral 값을 반환한다. 측면/미지정으로는 시작하지 않도록 안내한다. |

근거 파일: `domain/analyzer/PoseAngleExtractor.kt`, `ExerciseMeasurementStrategy.kt`, `RepFrameFeatureExtractor.kt`. 안내·시작 가능 여부는 별도 `domain/session/ExerciseCameraViewPolicy.kt`에서 관리한다. 측정이 가능하다는 것과 해당 촬영 방향에서 모든 분석 항목을 계산할 수 있다는 것은 다르다. 가려진 관절의 값은 계속 `null`/측정 불가로 남긴다.

## 카운터·분석·리포트의 일관성

1. `DashboardActivity.processLandmarks()`는 `sessionExerciseType.name`으로 측정과 `RepFrameFeatureExtractor`를 호출한다.
2. 동일 종목이 `ExerciseCounter.processMeasurement()`와 `RepAnalysisEngine`에 들어간다. 자동 감지 종목은 DEBUG 진단에만 사용한다.
3. `FormErrorAnalyzer.assessSquat()`는 실제 세션이 `SQUAT`이고 측정 가능할 때만 실행한다. SIDE_BEND를 포함한 다른 선택 운동에는 스쿼트 무릎 코칭을 주입하지 않는다.
4. `MainViewModel.addCompletedRep()`는 다른 종목의 구조화 결과를 거부한다. `generateLockedSessionReport()` / `structuredReport()`는 잠긴 종목을 보존한다.
5. `WorkoutReportData`의 표시 종목은 선택 종목의 한국어 이름이고, 구조화 분석 JSON은 같은 종목의 enum 이름을 보존한다. 기존 저장/조회 계약을 변경하지 않는다.
6. 시작 TTS는 `ExerciseSpeechFormatter.sessionStart(selectedExercise)`를 사용하고, 완료 TTS는 선택 종목으로 생성된 회차 분석의 피드백을 사용한다.

기존 SIDE_BEND 비대칭 팔 분류 수정, 좌/우 signed 측정·카운팅과 **15°/5°**, 3프레임 체류 및 최소 동작 시간 정책은 그대로 보존한다. 이번 작업에서는 classifier/counter/analysis threshold를 조정하지 않는다.

## 검증 상태

2026-11-15 현재 작업 트리로 실행한 결과다. 합성·로컬 검증과 실제 기기 검증을 구분한다.

| 검사 | 결과 |
|---|---|
| 전체 JVM | **PASS: 87 suites / 922 tests, failures/errors/skipped 모두 0** |
| 7종 수동 선택 → measurement/counter/analysis/report/history DTO | **PASS: 7종 × 4개 = 28개**, 각 종목의 5회 합성 cycle 포함 |
| 7종 기존 synthetic counter / SIDE_BEND 좌·우 회귀 | **PASS**, 기존 비대칭 팔 classifier fixture·signed 좌/우·자동 세션 교정 회귀 보존 |
| Node 정적·정책 검사 | **PASS: 171개**, 실패/skip 0 |
| Android serializer → 로컬 server contract fixture | **PASS: 19개**, 이번 JVM 실행에서 생성한 데이터로 검사. 원격 DB 검증 아님 |
| `assembleDebug` | **PASS**, 수동 선택 코드가 포함된 DEBUG APK 조립 완료 |
| `lintDebug` | **미완료: `lintAnalyzeDebug` 180초 무출력으로 2회 중단**, 보고서 없음. 오류 0/PASS로 해석하지 않음 |
| APK packaging 검사 | **PASS**, 조립된 APK에서 private 모델/Golden/tensor 제외 및 MediaPipe task·OpenCV NOTICE 유지 확인 |
| 실기기 화면·5회 운동·음성·저장/History | **NOT_RUN — 사용자 체크 필요** |

`ManualSelectedExercisePipelineTest`는 `ExerciseType.supported`에서 7종을 파라미터화한다. 생성한 수학적 관절 좌표를 실제 `PoseAngleExtractor`와 scalar extractor에 넣고, 기존 체류/시간 정책으로 5회 synthetic cycle을 수행한다. 프레임마다 다른 자동 감지 결과를 넣어도 선택 종목이 분석·시작/완료 음성 문구·SessionReport·WorkoutReportData·기록 DTO 재열기까지 유지되는지 검증한다. 원격 저장소/실제 Room DB/TTS 엔진/카메라 정확도를 검증하는 테스트는 아니다.

## 시연 APK

목표 산출물: `build/deliverables/AIRPTCoach-7Exercises-ManualSelection-demo-debug.apk`

**최종 시연용 이름으로의 복사는 보류했다.** 사용자 요청의 "최종 PASS 후 생성" 조건에 따라 lint 미완료 상태에서 위 산출물을 만들지 않았다. 기존 `AIRPTCoach-7Exercises-SideBendFix-debug.apk`는 삭제하거나 덮어쓰지 않았다. 7종 수동 선택과 Landmark 기반 측정은 YOLO 모델과 독립적이다.

조립된 검증 중 APK는 `build/manual-selection-verification/outputs/apk/debug/app-debug.apk`다. 36,793,835 bytes이며 수동 선택 UI/라우팅 코드가 포함돼 있다. 최종 시연본이 아니며 실제 기기 화면·음성·저장은 미검증이다.

- SHA256: `6f62c08f7d3be9708190df9e7ee82a1cd2c8da0ef6fd6ee83b15b8842ecf7f33`.
- `com.example.arptapp` DEBUG / 기본 EXP02 Shadow / `YOLO_PRIVATE_BUNDLED=false`. EXP04로 전환하거나 승격하지 않았다.
- `lockSelectedExercise`, `beginSession`, `WorkoutSelectionDialog`, `formatManualSession`, `MANUAL_SELECTED`가 실제 APK DEX에 포함돼 있다.
- 기존 보호 대상 31개 파일 및 SideBendFix APK의 SHA256이 유지됐다. private 입력의 Git tracked 파일은 0개이며, Golden 이미지의 encoded SHA256과 APK 항목도 대조해 일치 항목이 없음을 확인했다.
- 이번 변경 공개 파일은 18개다. 새 비밀정보·외부 통신·유료 SDK·권한 경로를 발견하지 못한 범위 제한 검토이며, 전체 저장소/원격 서비스의 보안을 보증하지 않는다.
- 현재 미완료를 포함한 로컬 근거: `local_validation/manual_selection/partial_verification_01.json`. `requestedVerificationComplete=false`, `demoApkCreated=false`, `lint=INTERRUPTED_NO_REPORT`로 기록했다.

로컬 검증 근거는 ignored `local_validation/manual_selection/`에 보존한다. `build-01`은 기존 Gradle 캐시 lock 접근 권한 오류였고, 허용된 캐시 접근으로 `build-02`에서 JVM/조립을 마쳤다. 다만 `build-02`와 상세 로그 `build-03` 모두 `lintAnalyzeDebug` 180초 무출력에 따라 **이 작업의 명령 트리만 중단**했다. IDE·사용자 프로세스 종료/clean/기존 APK 삭제/의존성 변경은 하지 않았다. 기존 캐시를 오프라인으로 재사용하고 기존 산출물과 구분되는 프로젝트 내 출력 폴더를 사용했다.

가용 RAM 조회는 약 0.8GB였으나, 이것만으로 lint 지연의 원인을 확정하지 않는다. 프로그램을 저장·종료해 자원을 확보한 뒤 같은 출력 폴더에서 lint를 재개하고, 완료된 결과를 확인한 뒤 최종 시연 APK를 복사해야 한다. 새 선택창의 `announceForAccessibility` 호출에는 컴파일 사용 중단 예정 경고가 있었으며, 완성된 lint 보고서가 없어 전체 경고 수는 아직 확정할 수 없다.

## 실기기 사용 순서

1. 새 APK를 설치하고 로그인한다. 기존 앱을 삭제하면 로컬 기록을 잃을 수 있으므로 단순 업데이트를 먼저 시도한다. 서명 충돌이 생기면 삭제하지 말고 확인한다.
2. 운동 준비에서 몸 전체가 보이는 구도와 촬영 방향을 설정한다. 전면/후면 렌즈를 바꿔도 몸의 정면/측면 선택과는 별개다.
3. **운동 시작 → 종목 선택 → 촬영 방향 확인**을 마친다. 먼저 취소도 눌러 횟수·세션·음성이 시작되지 않는지 확인한다.
4. 제목·단위가 선택 종목으로 즉시 표시되고 횟수가 `0`인지 확인한다. 디버그 자동 감지 결과가 달라도 선택 제목이 유지되어야 한다.
5. 관절이 충분히 보이는 상태에서 편안하게 5회를 수행한다. SIDE_BEND는 중립에서 시작하고 좌/우를 나눠 별도로 기록한다. 바이셉 컬은 한팔씩 수행하면 관찰한 동작 수와 앱 총 완료 수를 비교하기 쉽다(양팔 동시 완료는 좌·우 각각 카운트한다).
6. 완료 횟수·완료 음성·종목에 맞는 피드백을 확인한다. SIDE_BEND 등 비스쿼트에서 스쿼트 무릎 안내가 나오면 실패로 기록한다.
7. 종료 후 결과를 저장하고 운동 기록에서 같은 항목을 다시 열어 종목·횟수·회차 분석을 확인한다. 네트워크/계정 상태에 따른 기존 동기화 결과는 별도 기록한다.
8. 다음 세션에서 다른 종목을 선택하고 이전 운동의 count/분석/제목이 남지 않는지 확인한다.

### 직접 기록하는 7종 체크표

`□`에는 확인 결과를 적고 실제 count는 숫자로 기록한다. 칸이 비어 있으면 미검증이다.

| 운동 | 운동 선택 | 촬영 방향 | 화면 제목 | 5회 수행 | 실제 count | TTS | feedback | Report | History |
|---|---|---|---|---|---|---|---|---|---|
| 스쿼트 | □ | 정면/측면: ___ | □ 스쿼트 운동 중 | □ | ___ | □ | □ | □ | □ |
| 숄더 프레스 | □ | 정면/측면: ___ | □ 숄더 프레스 운동 중 | □ | ___ | □ | □ | □ | □ |
| 바이셉 컬 | □ | 정면 권장: ___ | □ 바이셉 컬 운동 중 | □ | ___ | □ | □ | □ | □ |
| 푸시업 | □ | 측면 권장: ___ | □ 푸시업 운동 중 | □ | ___ | □ | □ | □ | □ |
| 벤트오버 덤벨 로우 | □ | 측면 권장: ___ | □ 벤트오버 덤벨 로우 운동 중 | □ | ___ | □ | □ | □ | □ |
| 오버헤드 트라이셉스 익스텐션 | □ | 정면/측면: ___ | □ 선택 종목 + 운동 중 | □ | ___ | □ | □ | □ | □ |
| 덤벨 사이드 벤드 | □ | **정면**: ___ | □ 덤벨 사이드 벤드 운동 중 | □ 좌/우 별도 | 우 ___ / 좌 ___ | □ | □ 무릎 안내 없음 | □ | □ |

기기/OS: ___ / 날짜: ___ / 앱 버전·APK: ___ / 실패 증상: ___

## 보안·비용·검증 경계

- 새 provider/API/SDK/권한/서버 배포/DB migration/billing 변경을 추가하지 않는다. 이 기능은 로컬 세션 라우팅 변경이다.
- 수동 선택 자체의 외부 호출·추가 비용은 없다. 기존 계정 동기화의 Supabase 사용량·요금제는 이 변경으로 제거되거나 무료가 보장되는 것은 아니다.
- private YOLO 모델, Golden 원본/이미지/tensor, `local_validation`, `.env`, credentials는 Git이나 APK에 새로 포함하지 않는다.
- 기존 EXP02/EXP03 모델, EXP04 Golden/Pillow/venv/environment 및 evidence를 삭제·교체하지 않는다. EXP04는 **NOT_VERIFIED** 그대로다.
- 모델 재배포 권리·법률 판단을 이번 UI/라우팅 변경으로 승인하거나 대체하지 않는다.
- 자동 commit/push/PR/merge, 기기 설치, 실운동 데이터 수집을 자동 수행하지 않는다. synthetic 통과를 실기기/모델 정확도로 표현하지 않는다.

## 변경 파일

주요 모듈과 테스트(최종 diff 기준으로 검토):

- `app/src/main/java/com/example/arptapp/DashboardActivity.kt`: 선택 확인 후 시작, 종목별 reference/measurement/counter/TTS/report 라우팅.
- `app/src/main/java/com/example/arptapp/domain/session/SessionExerciseLock.kt`: MANUAL_SELECTED와 레거시 자동 잠금 경로 분리.
- `app/src/main/java/com/example/arptapp/viewmodel/MainViewModel.kt`: `beginSession` 명시적 시작 API.
- `app/src/main/java/com/example/arptapp/domain/session/ExerciseCameraViewPolicy.kt`: 기존 측정 지원에 근거한 시작/권장 방향 안내.
- `app/src/main/java/com/example/arptapp/ui/dashboard/WorkoutSelectionDialog.kt`: 지원 목록 기반 세로 선택 모듈.
- `app/src/main/java/com/example/arptapp/ui/dashboard/WorkoutExerciseUiFormatter.kt`: 선택 종목의 사용자 표시.
- `app/src/main/java/com/example/arptapp/ui/feedback/FeedbackManager.kt`: 세션 경계의 음성 상태 정리.
- `app/src/main/res/layout/dialog_workout_selection.xml`, `app/src/main/res/values/strings_workout_selection.xml`: 선택 UI/한국어 리소스.
- `app/src/test/java/com/example/arptapp/domain/session/ManualSessionExerciseLockTest.kt`
- `app/src/test/java/com/example/arptapp/domain/session/ExerciseCameraViewPolicyTest.kt`
- `app/src/test/java/com/example/arptapp/viewmodel/ManualSelectedSessionTest.kt`
- `app/src/test/java/com/example/arptapp/domain/report/ManualSelectedExercisePipelineTest.kt`
- `app/src/test/java/com/example/arptapp/ui/dashboard/ManualWorkoutUiTest.kt`
- `scripts/manual-session-routing.test.mjs`: 선택·취소·세션 라우팅·보안 경계 검사.
- `scripts/side-bend-routing.test.mjs`: 기존 SIDE_BEND 경계와 새 수동 시작 연결 검사.
- `scripts/yolo-shadow.test.mjs`: 수동 선택과 진단 전용 detector의 분리 검사.
- `docs/MANUAL_SESSION_DEMO.md`: 본 문서.

기존 SIDE_BEND 버그 수정 근거는 [`SIDE_BEND_DEVICE_BUGFIX.md`](SIDE_BEND_DEVICE_BUGFIX.md)에 보존한다. 해당 문서의 이전 자동 시작 설명이 이번 UI의 동작 지침이 되는 것은 아니다.
