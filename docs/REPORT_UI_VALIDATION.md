# 운동 결과 / 상세 리포트 UI 검증

작업일: 2026-10-16 (Asia/Seoul). 대상은 XML / ViewBinding 기반 표시 계층이다.

## Overall Report

```text
ResultActivity
 ├ 완료 안내 + 총 운동 횟수 카드
 ├ 상세 리포트 보기
 └ 홈으로 돌아가기

ReportActivity (NestedScrollView, 세로/가로 공용)
 ├ 운동 결과 / 운동 종류 / 저장된 날짜
 ├ 전체 요약 카드: 총 횟수 · 세션 평균 · 운동 시간 · 예상 칼로리
 │  └ 저장된 피드백 + 초기 공학 기준 / 열량 예상값 안내
 ├ AI 설명: 측정 역할 구분 + 비활성 준비 상태
 ├ 회차별 점수 흐름: 기존 MPAndroidChart 유지, 높이 200dp
 ├ 로컬 자세 분석: 회차별 독립 카드 또는 빈 기록 안내
 ├ 전체 개선 요약: 저장된 세션 관찰 및 우선 팁이 있을 때만 표시
 └ 닫기
```

- 점수와 그래프는 빨강/초록 등급색 대신 중립 텍스트와 동일 cyan 계열을 사용한다.
- 운동 횟수만으로 완벽한 자세라고 평가하거나 다음 운동량 증가를 요구하지 않는다.
- 평균의 기존 표시 규칙은 `WorkoutReportPresentation.displayScore`를 그대로 사용한다.
  구조화된 기록만 있는 경우에는 저장된 평균을 표시하며, 새로운 점수식을 실행하지 않는다.
- 알 수 없는 열량은 0 kcal로 만들어 내지 않고 기록 없음으로 표시한다.
- 날짜는 저장된 값을 그대로 사용한다. 과거 기록을 오늘 날짜로 바꾸지 않는다.

## Rep Card

각 회차는 20dp 모서리와 얇은 경계선의 독립 카드이며 다음을 분리해 표시한다.

1. 회차 / 저장된 점수 / 참고용 상태 문구
2. 요약: 우선 오류·개선 후보 → 실제 저장 강점 → 중립 안내
3. 잘한 점: 알려진 저장 강점 키가 있을 때만 표시
4. 개선할 점: 저장된 오류 또는 개선 후보가 있을 때만 표시
5. 다음 회차 팁: 저장된 `localPriorityFeedback` 하나를 보라 계열로 강조
6. 촬영 범위·부분 기록·이전 단계 모델 등 해석 제한
7. 상세 측정값 보기 버튼과 접이식 측정 그룹

강점이 없다는 이유로 강점을 만들지 않으며, 오류가 없다는 이유로 정상/안전 판정을 하지 않는다.
측정 근거가 없는 경우 그 한계를 표시한다. 좌우/단계는 한국어로 풀어 쓰며 내부 severity,
occurrenceFrames 및 미확인 machine key는 사용자 설명에 사용하지 않는다.

## Expandable Metrics

- 기본값은 접힘이다. 핵심 코칭은 접지 않는다.
- 버튼을 누르면 해당 회차의 측정값만 펼쳐지고, 다시 누르면 접힌다.
- `RepMetricExpansionState`는 회차 번호별 상태를 화면 메모리에만 보관한다.
- 상세 View는 최초 펼침 때 생성한다. 재접기/재펼침 시 중복 생성하지 않는다.
- 화면 회전 시 펼침 상태 복원은 이번 범위에 포함하지 않았다.
- null/NaN/Infinity/범위 밖 각도는 행을 숨기고, 빈 그룹도 숨긴다.
- 재사용 가능한 `MetricGroup` / `MetricRow` binding으로 label-value를 표시한다.
  작은 폭과 큰 글꼴을 위해 label-value는 세로로 배치하고 자동 줄바꿈한다.

| 운동 | 실제 저장값이 있을 때 표시하는 그룹 |
|---|---|
| 스쿼트 | 무릎 / 고관절 / 몸통 / 동작 시간 |
| 숄더 프레스 | 팔꿈치 / 좌우 균형 / 동작 시간 |
| 공통 선택 항목 | 저장된 점수 구성 / 저장된 내 기준 유사도 |

숄더 프레스의 bottom 값은 전환 구간으로 표기한다. 아직 관찰하지 않은 복귀 구간은 만들지 않는다.
점수 구성은 `localScore.components`에 저장된 값만 보여 준다. 측정 제외 항목을 0점으로 표시하거나,
이전 기록의 breakdown을 재구성하거나, 이미 반영된 오류 감점을 다시 차감하지 않는다.
내 기준 유사도는 총점과 분리하고 개인 기준이 자세 인증이 아니라는 안내를 유지한다.

## Compatibility

- 기존 `ReportNavigation`, `WorkoutReportData`, `RepAnalysisCodec`, `StructuredReportCodec` 그대로 사용.
- structured + legacy 회차 번호의 합집합을 정렬해 카드로 표시한다.
- structured의 점수가 null이면 구버전 점수로 대체하지 않는다. 그래프에서 빠져도 카드는 남는다.
- structured가 없는 회차는 저장된 `RepAnalysis.detail`을 별도 섹션에 표시한다.
- 회차 식별 필드만 있는 빈 structured 회차는 대응하는 구버전 설명을 유지하되 구버전 점수로 되돌리지 않는다.
- 빈/손상된 기록은 빈 상태로 표시한다. 누락 데이터를 다시 분석하거나 추정하지 않는다.
- Room entity / schema / version / migration 변경 없음.
- 엔진, 점수 계산, phase, threshold, YOLO detector/shadow mode 변경 없음.
- 기존 결과 저장 ViewModel의 계정 소유권·중복 저장 방지·재시도·화면 이동을 변경하지 않았다.

## UI modules and rendering decision

```text
저장된 report / structured / legacy
 → RepReportUiMapper (순수 표시 모델)
 → RepAnalysisCardRenderer (카드와 지연 생성 측정 View)
 → ReportActivity (화면 이벤트와 상태 연결)

저장된 점수 → ReportChartRenderer
시스템 창 여백 → ReportWindowInsets (결과/리포트 공용)
AI 공개 정책 + 데이터 + 요청 상태 → ReportAiUiPolicy
```

이번에는 기존 단일 스크롤/회차 컨테이너를 유지하고 재사용 카드 renderer로 분리했다.
외부 의존성이나 중첩 RecyclerView를 추가하지 않았다. 상세 영역은 지연 생성한다.
아주 긴 기록에서 모든 핵심 카드를 한 번에 생성하는 기존 구조의 비용은 남아 있으며,
실측상 필요하다면 추후 전체 화면을 단일 가상화 목록으로 전환하는 별도 작업으로 다룬다.

## AI UI and security / cost boundary

- 버튼 위치: 전체 요약 바로 아래, 그래프/회차 카드 위.
- `AiCoachingPolicy.ENABLED = false` 유지. 버튼은 비활성 / 준비 중으로 표시.
- 명시적인 사용자 클릭 및 확인 동작 양쪽에서 공개 정책·구조화 데이터·중복 요청을 재확인한다.
- 화면 진입, lifecycle 재구독, 카드 펼침 또는 그래프 조작은 generate를 실행하지 않는다.
- 저장소의 독립적인 disabled-before-transport gate를 유지한다.
- Supabase 스킬의 비밀키/클라이언트 노출·사용자 데이터 경계 점검을 참고했으며,
  이번 변경은 로컬 UI 게이트와 표시만 대상으로 했다. 원격 쿼리나 배포 검증은 하지 않았다.
- API key / provider / 유료 API / dependency / 권한 / 서버 전송 / billing 추가 없음.
- DB migration / Edge Function 배포 / 모델 삭제 / Git commit·push 수행 없음.
- 변경된 공개 텍스트만 비밀키 패턴 검사한다. 전체 저장소/이력 라이선스 재스캔은 하지 않는다.

## Test

### 자동 검증 결과

| 검증 | 결과 |
|---|---|
| `assembleDebug testDebugUnitTest` | BUILD SUCCESSFUL, 3분 56초, 49 tasks (19 executed / 30 up-to-date) |
| JVM | 47 suites / 417 tests / 실패 0 / 오류 0 / 건너뜀 0 |
| 기존 JVM 회귀 | 작업 전 369개 testcase 이름과 대조: 누락 0, 신규 48 |
| 신규 JVM | 표시 mapper 42 / 독립 펼침 상태 4 / AI UI 정책 2 |
| Node 전체 | 92개 통과, 실패·건너뜀 0 (기존 76 + 신규 UI 회귀 16) |
| 변경 공개 텍스트 비밀키 패턴 검사 | 21개 파일 통과, 기존 APK 키는 publishable 분류 확인 (값 미출력) |
| `git diff --check` | 통과 |
| 공개 APK 모델 검사 | 통과: 미확인 YOLO/loose weights 없음, 필수 MediaPipe task 유지 |
| APK 최신 UI 포함 확인 | DEX에 `RepAnalysisCardRenderer` 포함 |

빌드 명령 (기존 사용자 캐시와 디버그 키 사용, 결과는 IDE와 분리):

```powershell
$env:GRADLE_USER_HOME = 'C:\Users\dlgus\.gradle'
$env:ANDROID_USER_HOME = 'C:\Users\dlgus\.android'
.\gradlew.bat assembleDebug testDebugUnitTest --no-daemon --no-problems-report -PverificationBuildDir=dashboard-ui-verification
```

최초 제한 환경에서는 `C:\.gradle` lock 경로, 이후 `C:\.android` 접근 문제로 소스 컴파일 전에
중단되었다. 전역 설정/빌드 파일을 바꾸지 않고 위 프로세스의 기존 사용자 캐시 경로를 명시해 해결했다.
기존 Gradle deprecation 안내는 남아 있으며 의존성 업그레이드는 하지 않았다.

```powershell
node --experimental-strip-types --test scripts/*.test.mjs supabase/functions/generate-workout-report/*.test.ts supabase/functions/youtube-ranking/*.test.ts
node scripts/verify-coaching-security.mjs
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-public-apk.ps1 -ApkPath build/dashboard-ui-verification/outputs/apk/debug/app-debug.apk
git -c core.safecrlf=false diff --check
```

APK 검사 스크립트는 로컬 실행 정책 때문에 별도 PowerShell 프로세스에서 실행했다.
전역 실행 정책은 변경하지 않았다. 기존 Android/server/security 테스트 파일은 수정하지 않았다.

설치 파일:

`C:\Users\dlgus\AndroidStudioProjects\ArPtApp\build\dashboard-ui-verification\outputs\apk\debug\app-debug.apk`

- 크기: 36,515,401 bytes
- SHA-256: `3584F6E4144FDBF993D29A977F8740C02B8BF1BAA7B5BED1F41940A265F35575`

실기기 검증은 별도다. ADB 조회 결과 연결 기기는 online 0 / offline 0 / unauthorized 0이었다.
정적 검사·JVM·빌드는 실제 화면 렌더링, TalkBack 또는 터치 E2E를 대신하지 않는다.

### 실기기 수동 확인 항목

- [ ] 새 운동 결과 → 저장 완료 → 상세 리포트 → 홈 이동
- [ ] 과거 기록의 상세 리포트 및 structured 없는 기록
- [ ] 점수가 없는 structured 회차와 빈 기록
- [ ] 1회차만 펼침 → 다른 회차 접힘 유지 → 다시 접기
- [ ] 세로/가로 전환, 작은 화면, 큰 시스템 글꼴, 긴 운동 설명
- [ ] TalkBack 회차 헤더·섹션·측정 label-value·펼침 상태 읽기
- [ ] 그래프 수평 이동과 세로 페이지 스크롤
- [ ] AI 비활성 버튼과 오프라인 로컬 분석

## Changed Files

### Kotlin

- `app/src/main/java/com/example/arptapp/ResultActivity.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/ReportActivity.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/RepReportUiModel.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/RepAnalysisCardRenderer.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/ReportChartRenderer.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/ReportUiPolicy.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/ReportWindowInsets.kt`

### Resources

- `app/src/main/res/layout/activity_report.xml`
- `app/src/main/res/layout/activity_result.xml`
- `app/src/main/res/layout/item_rep_analysis.xml`
- `app/src/main/res/layout/item_report_metric_group.xml`
- `app/src/main/res/layout/item_report_metric_row.xml`
- `app/src/main/res/values/report_ui.xml`
- `app/src/main/res/values/report_behavior.xml`
- `app/src/main/res/drawable/bg_report_note.xml`
- `app/src/main/res/drawable/bg_report_priority.xml`

### Tests / documentation

- `app/src/test/java/com/example/arptapp/presentation/report/RepReportUiMapperTest.kt`
- `app/src/test/java/com/example/arptapp/presentation/report/RepMetricExpansionStateTest.kt`
- `app/src/test/java/com/example/arptapp/presentation/report/ReportAiUiPolicyTest.kt`
- `scripts/report-ui.test.mjs`
- `docs/REPORT_UI_VALIDATION.md`

## Suggested Commit

```text
feat: 운동 결과 리포트를 회차별 코칭 카드 UI로 개선

- 전체 요약·AI 준비 상태·점수 흐름·회차 분석 영역 분리
- 근거 기반 회차 요약과 독립적인 상세 측정값 펼침 구현
- 구버전 기록 호환 및 AI 비활성·자동 호출 차단 유지
- 표시 모델·카드·그래프·안전 여백 모듈과 회귀 테스트 추가
```
