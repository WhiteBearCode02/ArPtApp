# 모듈 구조와 유지보수 가이드

현재 프로젝트는 **하나의 Gradle `:app` 모듈** 안에서 책임별 패키지와 공개 API를 분리합니다. 파일을 나누는 것뿐 아니라 화면·정책·저장·SDK의 변경 이유를 분리하는 것이 목적입니다. 기존 Supabase API에 더해 선택형 AI 코칭의 비활성 서버 소스를 준비했으며, 유료 서비스·실제 LLM 제공자는 추가하지 않았습니다.

## 책임과 진입 API

| 영역 | 모듈 / API | 담당 책임 |
|---|---|---|
| 운동 화면 | `DashboardActivity` | 권한, 세션 전환, 센서와 화면 연결 |
| 카메라 | `ui/camera/PoseCameraController` | CameraX 바인딩, 이미지 회전, MediaPipe 추론, 자원 해제 |
| 프레임 정책 | `FrameSubmissionPolicy`, `CameraFrameTracker` | 제출 간격, 렌즈/세대 대응, 전환 전 결과 폐기 |
| 대상 선택 | `MainPersonSelector` | 가장 큰 관절 영역 선택; 개인 신원 인증은 아님 |
| 운동 정책 | `domain/analyzer`, `domain/counter`, `domain/classifier` | 각도, 반복 구간, 초기 오류 규칙, 임시 종목 추정 |
| 리포트 계산 | `domain/report/RepReportAnalyzer.analyze/report` | 회차 점수·설명·세션 피드백; Android 없이 실행 |
| 구조화 회차 분석 | `RepFrameFeatureExtractor`, `RepAnalysisEngine`, `WorkoutAnalysisAggregator` | 품질별 scalar 측정·bounded 회차 집계; 기존 카운터와 별도 |
| 선택형 AI 설명 | `AiCoachingRepository`, `AiReportValidator`, `AiReportViewModel` | 기본 비활성, 수동 요청·근거 참조 검증·로컬 fallback |
| 서버 코칭 포트 | `supabase/functions/generate-workout-report` | 인증·입력 제한·durable quota RPC·provider abstraction; 실제 제공자 없음 |
| 운동 상태 | `viewmodel/MainViewModel` | 세션 기록 수집과 화면 상태; 계산은 분석 API에 위임 |
| 보고서 계약 | `presentation/report/WorkoutReportData`, `ReportNavigation` | 단위 정규화, Room 변환, 화면 간 전달, 구버전 JSON 호환 |
| 결과 저장 | `WorkoutResultViewModel`, `WorkoutResultSaveGate` | 저장 상태, 재시도, 화면 회전 중 중복 저장 억제 |
| 신체정보 폼 | `ui/profile/BodyProfileForm`, `BodyProfileInputValidator` | View 바인딩과 순수 입력 검증 분리 |
| 시간·캘린더 | `ReminderTime`, `ProfileCalendarNavigator` | 다음 알림 시각, 외부 캘린더 화면 연결 |
| 설정 동기화 | `data/repository/UserSettingsSyncRepository.synchronize` | 계정별 로컬/원격 설정 병합 |
| 교체 가능한 저장 포트 | `UserSettingsStore`, `BodyProfileSource`, `ReminderPreferencesSource` | 구현과 분리된 설정 읽기/쓰기 계약 |
| 로컬 저장 | `data/preferences/UserSettingsRepository`, Room DAO | 계정별 설정 키와 운동 기록 보관 |
| 인증·원격 저장 | `data/remote/*Repository` | 기존 Supabase/HTTP SDK 어댑터, 직렬화·쿼리 |
| 인증 상태 | `AuthSessionStore.current/session` | 저장소 인스턴스가 공유하는 메모리 내 단일 상태 |
| 취소 처리 | `core/coroutines/suspendResult` | 일반 실패는 Result로, 코루틴 취소·치명적 오류는 상위로 전달 |
| 주변 헬스장 | `ui/gym/*` | 사용자 요청 시 위치 확인 후 외부 지도 연결 |
| 데이터셋 | `ai_module` | 추출·정규화·특징·반복 분할·라벨·검증 단계 분리 |

## 중요한 계약

### 카메라와 좌표

- `PoseCameraController`가 executor와 MediaPipe 모델을 소유합니다. 화면 종료 시 `close()`를 호출합니다.
- 프레임은 `finally`에서 `ImageProxy.close()` 처리하여 추론 실패/스로틀 때문에 CameraX가 정체되지 않게 합니다.
- 결과 콜백은 메인 스레드에 전달됩니다. 영상 회전 후의 크기를 Overlay와 공유합니다.
- 비동기 입력 bitmap을 제출 직후 임의로 recycle하지 않습니다. native 메모리·장시간 촬영·종료 순서는 실제 기기에서 확인해야 합니다.
- 카메라 전환 시 제출 세대를 바꿉니다. 전환 전 프레임/이미 메인 큐에 올라온 결과는 폐기하고, 제출 당시 렌즈 방향만 사용합니다.
- 모델이 프레임을 건너뛰어도 메타데이터 대기 항목은 최대 32개입니다. 영상이나 관절 좌표는 이 추적기에 저장하지 않습니다.
- 가장 큰 관절 영역을 선택하는 정책은 안정적인 개인 식별/다중 사용자 추적을 보장하지 않습니다.

### 설정 동기화와 인증

`로컬 계정 활성화 → 로컬 읽기 → 원격 신체정보 → 원격 알림 → 병합 → 로컬 저장` 순서입니다. 알람 예약은 화면/기기 계층이 담당합니다.

- 신체정보의 원격 null/빈 닉네임은 기존 로컬 값을 보존합니다.
- 알림 **조회 성공 + 행 없음**일 때만 초기 설정을 한 번 업로드합니다. 조회 실패를 행 없음으로 취급하여 덮어쓰지 않습니다.
- 같은 호출에 같은 `userId`를 사용합니다. DataStore 키와 Room 조회/저장 대상은 계정별로 분리합니다.
- 동기화 취소는 오류 UI나 후속 저장으로 전환하지 않습니다. 로그아웃 전에 진행 중 동기화를 취소·대기하여 이전 계정 알람 복원을 방지합니다.
- Auth 상태의 StateFlow는 메모리 상태이며 인증 토큰의 영속 저장을 대신하지 않습니다. 인증 복원/로그아웃은 기존 Supabase Auth SDK가 수행합니다.
- 관리자 역할은 서버 관리 `appMetadata`에서 읽습니다. 화면의 관리자 표시 여부는 권한 검증을 대신하지 않으며 최종 접근은 서버 RLS가 제한합니다.
- 실제 Supabase 프로젝트의 테이블·RLS·메일 설정은 로컬 JVM 테스트만으로 검증할 수 없습니다.

### 운동 기록과 점수

새 회차 분석·AI 설명의 지원 범위와 비활성 정책은 [LLM_INTEGRATION.md](LLM_INTEGRATION.md)에 정의합니다.
MediaPipe 측정과 로컬 판단을 LLM 설명과 혼합하지 않습니다. LLM은 선택 기능이며 운동 화면에서 호출하지 않습니다.

- 각도는 도, 시간은 초 또는 이름에 표시된 `Ms`, 열량은 kcal, 자세 점수는 0~100입니다.
- `swayX`는 휴대폰 가속도이지 사람의 관절 안정성이 아닙니다. 자세 점수 감점에 쓰지 않습니다.
- 90°/160° 임계값, 오류별 감점, 0.5 kcal/회는 기존 초기 공학적 정책을 보존했습니다. 의료적 판정·실측 열량·올바른 자세 인증으로 표현하지 않습니다.
- `ReportNavigation`의 extra 키/단위를 바꾸기 전에 구버전 읽기 호환을 먼저 추가합니다. `RepAnalysisCodec`은 이전 점수 배열과 회차 객체 배열을 지원합니다.
- 결과 저장은 ViewModel과 완료 상태가 관리하여 화면 회전으로 재삽입하지 않습니다. **DB commit 직후 프로세스 종료까지 정확히 한 번 저장을 보장하지는 않습니다.** 향후 영속 세션 ID/UNIQUE 제약을 설계할 때 DB 마이그레이션과 동기화 계약을 함께 검토해야 합니다.
- 운동 세션과 회차의 원격 업로드는 현재 두 단계이며 DB 트랜잭션이 아닙니다. 자동 재시도는 중복을 만들 수 있어 추가하지 않았습니다.

## 기술 주석 작성 기준

단순히 “버튼 클릭”, “변수 선언”을 반복하지 않고 다음 사항을 KDoc/주석으로 남깁니다.

1. 공개 API의 책임과 책임이 **아닌** 것.
2. 값의 단위·범위·null의 의미와 구버전 호환 계약.
3. 스레드, 수명, 취소 전파, 이미지/native 자원의 소유권.
4. 임계값의 출처/초기 가정과 측정 한계.
5. 계정 경계, 민감정보 로그 금지, 재시도 시 부작용.

동작/단위를 바꾸면 코드·주석·테스트를 같은 변경에서 수정합니다. 비밀번호·키·토큰·실제 사용자 값은 예시 주석에도 쓰지 않습니다.

## 검증 방법과 남은 경계

```powershell
# Android Studio의 출력 파일 잠금과 분리된 검증 경로
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user-home'
$env:ANDROID_USER_HOME = Join-Path $env:GRADLE_USER_HOME 'android'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline --no-daemon --no-problems-report -PverificationBuildDir=modularity-verification --max-workers=2
```

오프라인 명령은 의존성이 이미 캐시에 있을 때 사용합니다. APK는 `build/modularity-verification/outputs/apk/debug/app-debug.apk`에 생성됩니다.

순수 검증·프레임 정책·설정 병합·취소 전파·보고서 계약·저장 게이트를 JVM에서 테스트합니다. 빌드/JVM 성공은 실기기 카메라·알림·외부 캘린더·인증/원격 DB 성공을 뜻하지 않습니다.

2026-10-13 검증: `assembleDebug` 및 Android JVM 테스트 111개, 기존 Python 테스트 10개 통과. 연결된 기기가 없어 실기기 검증은 수행하지 않았으며 실제 Supabase 프로젝트에 쿼리/마이그레이션을 실행하지 않았습니다. 추적 대상 텍스트의 주요 비밀키 패턴과 민감 설정 파일 경로 검사에서는 발견 사항이 없었지만, 이는 Git 전체 이력이나 원격 서비스 보안을 보증하는 감사는 아닙니다.

아직 하나의 Gradle 모듈이며 일부 domain 분석기가 MediaPipe landmark 타입을 사용합니다. `DashboardActivity`의 세션/개인 기준 연결과 일부 화면 이벤트도 남아 있습니다. 전면 멀티모듈 전환이나 랜드마크 DTO 교체는 호출부·모델 검증 범위가 커 별도 단계로 진행합니다.

이번 구조 개선은 의존성·권한·SQL 스키마·유료 API·서버 작업을 추가하지 않습니다. 다만 기존 연결 서비스의 계정 요금제/사용량까지 코드 리팩터링으로 무료를 보장하는 것은 아닙니다.
