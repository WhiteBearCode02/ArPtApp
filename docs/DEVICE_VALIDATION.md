# 회차별 좌우·단계 분석 실기기 검증

기준일: 2026-10-15. 아래는 **사람이 직접 수행할 체크리스트**입니다. 완료 표시가 없는 항목은 미검증입니다.
JVM/Node 테스트 통과를 카메라 정확도·실기기 성능 검증으로 간주하지 않습니다.
초기 2D 관찰 기준은 의료 판정이 아닙니다. 오류를 만들려고 무릎을 과하게 안쪽으로 넣거나 무리한 가동 범위를 수행하지 마세요.

## 준비

1. 보안 보강 검증 APK인 `build/security-hardening-verification/outputs/apk/debug/app-debug.apk`를 설치합니다.
   이전 `location-manager-verification` APK는 자동 갱신되지 않으며 YOLO 모델이 포함된 과거 파일일 수 있습니다.
   [배포 준비·검증 상태](../RELEASE_READINESS.md)를 먼저 확인하세요. 이 debug 파일은 공개 배포 승인본이 아닙니다.
2. 카메라 권한을 허용하고 촬영 방향을 정면/측면으로 명시합니다. 가능하면 화면에 한 사람만 보이게 합니다.
3. Android Studio Logcat에서 앱 process를 선택하고 tag `RepAnalysis`로 필터링합니다.
4. 휴대폰/Android 버전, 렌즈, 조명, 촬영 방향, 분석 기준 버전, 수행 횟수, 앱 횟수, 표시/TTS/보고서 일치를 메모합니다.
   계정 이름/이메일/토큰/식별되는 사진·영상은 검증 기록에 넣지 않습니다.
5. LLM은 활성화하지 않습니다. `AiCoachingPolicy.ENABLED=false`, `.env.example`의 `LLM_ENABLED=false`, `UnavailableLlmProvider`를 유지합니다.

기기별 matrix는 전면/후면 × 밝은 조명/일반 실내 × 정면/측면입니다. 별도로 부분 가림·촬영 방향 미선택을 확인합니다.
전면 미리보기의 화면 오른쪽이 사람의 해부학적 오른쪽이라고 단정하지 마세요.
MediaPipe 23/25/27은 LEFT, 24/26/28은 RIGHT이며, 화면 mirror와 구분해서 확인해야 합니다.

## SQUAT · FRONT

- [ ] 편안한 범위에서 5~10회 수행하고 직접 센 횟수와 앱 횟수를 대조한다.
- [ ] 자연스러운 움직임에서 좌/우 관찰이 발생했다면 실제 측과 event.side가 맞는지 확인한다. 없으면 미관찰로 기록한다.
- [ ] 양쪽 이벤트가 관찰됐을 때 LEFT/RIGHT가 각각 표시되고 null로 합쳐지지 않는지 확인한다.
- [ ] 한 오류가 두 단계에 관찰될 때 상세 리포트에서 단계가 분리되는지 확인한다.
- [ ] 회차 완료 직후 TTS와 화면의 우선 코칭이 같은 side/phase를 안내하는지 확인한다.
- [ ] 좌우 무릎 차이는 동시 측정값으로 계산되고 측면 고관절/몸통 값이 정면에서 만들어지지 않는지 확인한다.
- [ ] false positive를 기록하되 의료적 이상 유무로 해석하지 않는다.

## SQUAT · SIDE

- [ ] 5~10회 직접 횟수와 비교한다. 몸통·어깨·고관절·무릎·발목이 보이게 촬영한다.
- [ ] 하강→하단→상승 순서와 시간 값이 실제 관찰 구간과 일관되는지 확인한다.
- [ ] 신뢰할 수 있는 한쪽 관절만 기록되고 반대쪽 및 knee bilateral metric은 null인지 확인한다.
- [ ] 정면 무릎 정렬(KNEE_VALGUS)이 생성되지 않는지 확인한다.
- [ ] 몸통 기울기 후보가 있다면 phase와 우선도가 입력 관찰과 맞는지 확인한다.

## SHOULDER_PRESS · FRONT

- [ ] 5~10회 직접 횟수와 비교한다. 기존 하단 70°/상단 160° 정책은 그대로임을 확인한다.
- [ ] 하단 준비→올라가는 구간→상단의 순서이며 이전 복귀를 현재 DESCENT로 잘못 포함하지 않는지 확인한다.
- [ ] **상단에서 회차를 확정하므로 이후 복귀 시간은 보통 null**인지 확인한다. null은 버그나 0ms가 아니다.
- [ ] 상단에서 머문 시간/오류를 하단 구간으로 표시하지 않는지 확인한다.
- [ ] elbow symmetry와 error.side=null 표시를 확인한다. 절댓값 차이만으로 어느 팔이 잘못됐는지 추측하지 않는다.
- [ ] 올라가는 구간의 양팔 코칭이 표시/TTS/리포트에서 일치하는지 확인한다.
- [ ] 실패 후 재시도 등 복잡한 파형은 단계 판단을 보류하며 없는 phase 오류를 만들지 않는지 확인한다.

## SHOULDER_PRESS · SIDE

- [ ] 가시적인 팔 한쪽의 내각만 기록되고 반대쪽 값은 null인지 확인한다.
- [ ] elbow bilateral metric과 비대칭 오류가 생성되지 않는지 확인한다.
- [ ] 카운트·단계·TTS가 실제 측정과 일관되는지 확인한다.

## 가림 / 방향 미선택 / 수명주기

- [ ] 한쪽 가림은 해당 측의 평가를 중단하고 반대쪽의 신뢰할 측정만 유지한다.
- [ ] 필요한 대표 관절을 잃으면 미완성 회차를 중단한다. 가림 프레임을 오류 연속 구간에 연결하지 않는다.
- [ ] UNKNOWN에서는 view-dependent 관절·좌우 차이·몸통 기울기·정렬 이벤트를 생성하지 않는다.
- [ ] 300프레임/20초 초과는 partialCapture=true. 버린 phase를 소급해서 채우지 않는다.
- [ ] 운동 완료/화면 회전/과거 기록 재열기에서 최신 데이터와 구버전 기록 모두 열리는지 확인한다.
- [ ] 화면 종료/카메라 전환 후 종료된 화면의 새 이벤트가 생기지 않는지 확인한다.

## Event·severity 수동 통과 기준

- key는 (type, side, phase). 각 phase에서 true true true는 채택, 1~2프레임 또는 간헐적 3개는 미채택이다.
- phase 경계를 넘는 2+2프레임은 합쳐서 4프레임 이벤트를 만들지 않는다.
- occurrenceFrames는 해당 phase 안에서 채택한 연속 구간들의 프레임 수 합계이다. 나중의 2프레임 노이즈는 더하지 않는다.
- timestampMs는 첫 채택 구간의 시작 프레임을 회차 시작 기준으로 환산한다. 3번째 확정 프레임 시간이나 absolute clock이 아니다.
- 내부 단계 시간은 프레임 제출 시각을 사용한다. UI의 코칭 2.5초 유지 시간과 분석 timestamp는 분리한다.
- severity는 채택된 구간의 최대 초기 우선도이다. ratio/각도 차이/기울기 band는 [LLM_INTEGRATION](LLM_INTEGRATION.md)의 표를 따른다.
- 보고서의 수치·이벤트·우선 코칭이 `RepAnalysis` 진단과 동일해야 한다. 오류 없음은 정상/완벽/안전 보증이 아니다.
- 실제 카메라 노이즈/가림/조명 false positive는 별도로 기록한다. 합성 fixture 통과만으로 정확도를 인증하지 않는다.

## DEBUG 진단과 개인정보

진단은 회차 완료 때 한 번이며 `BuildConfig.DEBUG`에서만 출력합니다. release 사용자 UI에 새 진단을 추가하지 않습니다.
허용 필드는 rep/exercise/view/phaseModel/partial/frames/phase durations/관절 요약/좌우 차이/
event type·side·phase·severity·occurrenceFrames·상대 시간입니다.

출력 금지: 원본 landmark, 사진/영상, 이름/이메일/userId, JWT/access token, API key.
이 진단 API는 사용자 세션이나 원본 프레임 객체를 받지 않습니다. 보낸 Logcat 전체에는 다른 시스템 로그가 섞일 수 있으므로
공유 전 필요한 tag만 선별하고 식별정보를 제거하세요. `.log` 등 기기 로그는 기존 ignore 규칙으로 Git에서 제외합니다.

## 테스트 fixture 기반 예시 — 실기기 측정값 아님

`RepScenarioFixtures`, `PhaseSideAnalysisTest`, `ShoulderPhaseStrategyTest`의 합성 scalar 입력으로 검증한 일부 출력입니다.
78/82점은 fixture에서 주입한 기존 점수이며 새 엔진의 임상적 판정 점수가 아닙니다.

### SQUAT FRONT

```text
3회차 · 78점
로컬 측정 · 정면
좌우 무릎 최대 차이 12.4°
내려가는 구간: 500ms / 하단 구간: 300ms / 올라가는 구간: 500ms
관찰된 강점: 관찰 구간의 좌우 무릎 각도 차이가 초기 기준 이내였습니다.
개선 후보:
내려가는 구간 · 오른쪽 무릎의 안쪽 이동 후보 · 초기 우선도 2 · 유효 5프레임
하단 구간 · 오른쪽 무릎의 안쪽 이동 후보 · 초기 우선도 2 · 유효 4프레임
우선 코칭: 내려가는 구간에서 오른쪽 무릎 방향을 발 방향에 맞춰 보세요.
```

### SHOULDER_PRESS FRONT

```text
1회차 · 82점
로컬 측정 · 정면
왼쪽 팔꿈치 최대 내각 170.0° / 오른쪽 팔꿈치 최대 내각 140.0°
좌우 팔꿈치 최대 차이 30.0°
내려가는 구간: 측정 불가 / 하단 구간: 200ms / 올라가는 구간: 500ms
관찰된 강점: 강점을 판단할 측정 근거가 부족합니다.
하단 구간 · 좌우 팔꿈치 각도 차이 후보 · 초기 우선도 2 · 유효 3프레임
올라가는 구간 · 좌우 팔꿈치 각도 차이 후보 · 초기 우선도 2 · 유효 5프레임
우선 코칭: 올라가는 구간에서 양팔이 함께 움직이도록 천천히 진행해 보세요.
```

## LLM 검증 (제공자 호출 없는 fake 테스트)

RIGHT/DESCENT만 주어진 입력에서 LEFT나 BOTTOM/ASCENT를 말하면 거절합니다.
RIGHT/DESCENT와 LEFT/ASCENT가 있어도 RIGHT/ASCENT로 바꾸지 못합니다.
그 회차의 정확한 event evidence를 참조해야 하며 null side/phase는 구체적 문구의 근거가 아닙니다.
숫자/유효 프레임 수/우선도도 참조된 값에만 근거합니다. 검사는 제한된 어휘를 쓰는 lexical guard이며 자연어 전체의 사실성 보장은 아닙니다.
진짜 LLM 제공자는 미연결이며 자동 요청·배포·SQL 실행·AI 리포트 영구 저장은 하지 않았습니다.

## 결과 기록 양식

| 항목 | 기록 |
|---|---|
| APK 빌드 일시 / phase model | |
| 기기 / Android / 렌즈 / 조명 / view | |
| 직접 횟수 / 앱 횟수 | |
| side/phase 이벤트와 TTS/리포트 일치 | |
| 가림·미평가·부분 캡처 | |
| 처리 FPS / 발열 / UI 지연 | |
| false positive / false negative / 재현 순서 | |
| 통과 / 보류 / 미실행 | |

관련 API: [MediaPipe NormalizedLandmark](https://developers.google.com/edge/api/mediapipe/java/com/google/mediapipe/tasks/components/containers/NormalizedLandmark).
이 문서와 테스트는 법적/의학적 적합성이나 운동 분석 정확도의 보증이 아닙니다.

## 이번 수정 파일

기준 경로는 `app/src/main/java/com/example/arptapp/`입니다. 총 30개 파일(기존 16개 수정 + 새 14개)입니다.

- 화면 연결: `DashboardActivity.kt`.
- 측정: `domain/analyzer/FormErrorAnalyzer.kt`, `domain/report/RepFrameFeatureExtractor.kt`.
- 분석/계약: `domain/report/RepAnalysisEngine.kt`, `StructuredAnalysis.kt`, 새 `EngineeringFormPolicy.kt`, `RepPhaseSegmenter.kt`, `FormEventAggregator.kt`.
- 코칭/진단: 새 `domain/report/LocalEventPresentation.kt`, `RepDiagnosticFormatter.kt`; `presentation/report/StructuredReportPresentation.kt`.
- AI 검증: `domain/report/AiWorkoutReport.kt`, `MeasuredClaimValidator.kt`, 새 `DirectionalClaimValidator.kt`.
- Android 테스트: 기존 `RepAnalysisEngineTest.kt`, `StructuredReportCodecTest.kt`; 새 `KneeValgusObservationTest.kt`,
  `PhaseSideAnalysisTest.kt`, `ShoulderPhaseStrategyTest.kt`, `DirectionalClaimValidatorTest.kt`, `RepScenarioFixtures.kt`.
- 서버: 기존 `contracts.ts`, `prompt.ts`, `security.test.ts`; 새 `event-claims.ts`, `event-claims.test.ts`.
- 문서: `README.md`, `docs/ARCHITECTURE.md`, `docs/LLM_INTEGRATION.md`, 새 이 체크리스트.

`.gitignore`, 기존 counter, Room schema, Gradle dependencies, 실제 provider, SQL, 인증/업로드 정책, 모델 파일은 수정하지 않았습니다.
`MainViewModel`, `FeedbackManager`, `ReportActivity`, `AiCoachingRepository`는 기존 공개 API를 그대로 사용합니다.
