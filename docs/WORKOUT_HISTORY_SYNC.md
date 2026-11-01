# 계정 기반 운동 기록 동기화

기준일: 2026-11-01. 원격 스키마를 읽기 전용으로 확인했으며 SQL 적용/원격 저장 테스트는 하지 않았습니다.
이 문서는 코드 구현과 실제 운영 검증을 구분합니다.

## 1. 원인과 현재 설계

기존 Dashboard는 운동 종료 시 새 UUID로 서버에 먼저 저장하고, Result ViewModel은 별도로
Room에 저장했습니다. 두 저장소가 공통 ID를 갖지 않았고 History/Statistics는 Room만 읽었습니다.
따라서 다른 기기에 로그인해도 서버의 기록이 표시되지 않았고 재시도 시 중복 가능성이 있었습니다.

```text
운동 결과 확인
  → WorkoutResultViewModel (최초 UUID/완료 일시/소유자 보존)
  → WorkoutHistoryRepository
      → Room UNIQUE sessionId 저장 → 즉시 로컬 저장 성공 표시
      → Supabase save_workout_snapshot (세션 + 회차 원자적 저장)

로그인 후 Home / History / Statistics
  → 현재 Auth UUID + 로그인 epoch 확인
  → Room 즉시 조회
  → 서버 요약 페이지만 조회 → Room merge → 화면 갱신
  → 기록 선택 시에만 report_json + 해당 exercise_rep_records 조회 → 상세 캐시
```

Supabase는 승인된 불변 계정 기록의 원본 저장소, Room은 로컬/오프라인 캐시와 미전송 outbox입니다.
서버에서 삭제된 기록을 자동으로 로컬에서 지우는 양방향 삭제 동기화는 이번 범위가 아닙니다.
MainViewModel의 운동 분석·횟수·점수 계산, Landmark Primary, YOLO Shadow 설정은 변경하지 않습니다.

## 2. Room 6 → 7

기존 자동 증가 `id`와 모든 데이터는 유지합니다. 다음 필드만 추가합니다.

| 필드 | 의미 | 기존 행 기본값 |
|---|---|---|
| sessionId | 새 세션의 공통 UUID, UNIQUE/nullable | null |
| syncState | PENDING / SYNCED / FAILED / LOCAL_ONLY | LOCAL_ONLY |
| cloudEligible | 서버 전송 대상인지 영속적으로 표시 | false |
| detailsCached | 상세 리포트를 기기에서 바로 열 수 있는지 | true |
| createdAt | 서버 정렬용 최초 완료 UTC 시각 | 빈 문자열 |
| repRecordsJson | 원래 관측된 회차 각도·흔들림·단계 요약 | [] |

3→4→5→6→7의 명시적 migration을 연결합니다. destructive fallback은 없습니다.
지원하지 않는 오래된 버전은 데이터 삭제 대신 열기를 거부합니다.
INSERT IGNORE + owner-scoped lookup을 사용하며 REPLACE로 기존 YOLO 진단을 지우지 않습니다.
요약 merge는 이미 캐시된 상세 JSON과 local-only 진단을 보존합니다.

## 3. 서버 준비 — 아직 적용 안 함

기존 실제 DB에서 확인한 내용:

- `exercise_sessions.id`는 **text PRIMARY KEY**, `user_id`는 uuid, `average_score`는 integer입니다.
- `exercise_rep_records.session_id`는 text, `(session_id, rep_number)` UNIQUE가 존재합니다.
- 세션 소유자 INSERT와 본인/신뢰된 관리자 SELECT 정책이 존재합니다.
- 회차는 부모 세션 소유자만 INSERT, 소유자/신뢰된 관리자만 SELECT 가능합니다.
- 이번 확인에서는 사용자 행·토큰·이메일·리포트 내용을 조회하지 않았습니다.

추가 SQL: [supabase/workout-history-sync.sql](../supabase/workout-history-sync.sql)

- nullable `workout_date`, `exact_average_score`, `report_json` 추가. 기존 서버 행도 그대로 유지.
- 요약 조회에는 `report_json`을 포함하지 않습니다. 소수 점수는 기존 integer와 별도로 보존합니다.
- `save_workout_snapshot(jsonb,jsonb)`는 SECURITY INVOKER이고 search_path는 비워 둡니다.
- auth.uid 소유권·허용 필드·UUID·크기·회차 상한을 검사하고 한 트랜잭션으로 세션/회차를 저장합니다.
- 같은 UUID의 동일 스냅샷은 무시하고, 다른 내용이면 충돌로 거부합니다. UPDATE 권한 불필요.
- public/anon 권한 회수, authenticated SELECT/INSERT와 RPC EXECUTE만 허용합니다.
- 알 수 없는 기존 정책이 있으면 조용히 남기지 않고 스크립트가 실패하도록 했습니다.
- 기존 관리자 화면을 위해 신뢰된 app_metadata admin SELECT는 유지합니다.
  개인 기록 API는 관리자 계정이어도 반드시 현재 user_id만 필터링합니다.

CLI가 현재 환경에 없어 임의 timestamp migration을 생성하지 않았습니다. 승인된 배포 환경에서
`supabase migration new workout_history_sync`로 생성한 파일에 SQL을 넣고 검토/적용하세요.
**앱 설치만으로 이 SQL은 적용되지 않습니다. 미적용 상태에서는 원격 동기화가 실패하고 로컬은 유지됩니다.**
원격 적용/권한 검증 전 공개 서비스의 완성된 서버 동기화로 간주하지 마세요.

## 4. 저장·복원·중복 방지·오프라인

1. 운동 시작 시 계정 소유자를 고정하고, 결과 전달 시 UUID를 한 번 생성합니다.
2. SavedStateHandle로 UUID와 완료 시각을 유지합니다. 복원/재시도에도 같은 키를 씁니다.
3. 현재 인증 계정과 기록 소유자가 같은지 확인하고 Room에 먼저 저장합니다.
4. Room 성공을 즉시 표시한 뒤 서버에 시도합니다. 실패/취소해도 로컬 기록을 삭제하지 않습니다.
5. 실패는 FAILED, 아직 시도/완료되지 않은 경우 PENDING, 성공은 SYNCED입니다.
6. 다음 Home/기록 화면 진입 또는 ‘다시 동기화’로 대기 기록을 재전송합니다.
7. 다른 기기는 같은 계정 로그인 → 요약 sync → Room merge → 기록 선택 시 상세 다운로드합니다.
8. 한 번 받은 상세는 오프라인에서도 열립니다. 아직 받지 않은 상세는 네트워크 필요 안내를 표시합니다.

### 기존 기록의 중요한 제한

예전 Room ID와 서버의 무작위 UUID 사이에는 검증 가능한 연결 정보가 없습니다.
날짜·횟수·점수만 보고 서로 다른 운동을 합치지 않습니다. 기존 Room 행은 LOCAL_ONLY로 보존하고
자동 업로드하지 않습니다. 따라서 **기존 로컬 기록의 재업로드로 새 중복을 만들지 않습니다.**
단, 이미 서버에 별도 업로드된 과거 기록을 내려받으면 이전 로컬 행과 함께 보일 수 있습니다.
과거 이중 저장 자료의 자동 식별/정리는 보장하지 않습니다. 이는 검증된 매핑이나 사용자의
별도 정리 결정이 필요하며 이번에 기존 행을 삭제하거나 임의 연결하지 않았습니다.
이전 서버 기록에 존재하지 않는 구조화 분석은 새로 만들어내지 않습니다. 저장된 회차 점수/설명만 복원합니다.

## 5. 제한된 호출과 비용

사용자는 이번 작업에서 기존 Supabase 저장·조회 동기화를 허용했습니다.
새 요금제·LLM·유료 SDK·Edge Function·실시간 구독·서버 배포를 연결하지 않았습니다.

- 자동 진입 sync: 같은 로그인 epoch 기준 최소 60초 간격. 자동 반복 타이머 없음.
- 한 sync: 업로드 최대 20세션, 요약 최대 5페이지 × 100행, 전체 45초 제한.
- 개별 업로드/상세 다운로드 10초 제한. 초과/네트워크 실패 시 대기 기록 유지.
- 부분 완료 시 사용자 재시도로 나머지 요약 페이지/대기 전송을 이어갑니다.
- 목록에서는 모든 회차를 미리 받지 않습니다. 저장소 인스턴스를 공유해 중복 동시 sync를 직렬화합니다.

이는 **0원 보장이나 서버 측 전체 quota가 아닙니다.** 기존 Supabase storage/egress 사용량과
설정된 요금제에 따라 비용이 생길 수 있습니다. billing 변경/요금제 확인은 하지 않았습니다.
많은 계정·수동 연속 요청·앱 재시작까지 전역으로 제한하는 서버 quota는 별도 과제입니다.

## 6. 보안·데이터 범위

- Auth UUID + epoch로 A→B→A 전환도 구분. 대기 요청 후 재검증하여 이전 결과를 화면에 반영하지 않음.
- Room 조회/수정에도 userId 조건 적용. 로그아웃 시 전체 DB를 지우지 않고 화면/작업만 분리.
- 기존 사용자 편집 불가능 app_metadata 관리자 정책 유지. user_metadata를 권한 근거로 쓰지 않음.
- Android에 공개 publishable 키만 사용. 서비스 키/비밀번호/토큰/서버 오류 본문 로깅 추가 없음.
- Remote DTO는 명시적 allowlist. Entity나 WorkoutReportData를 통째로 직렬화하지 않음.
- report JSON은 기존 typed codec으로 재구성. 원본 영상/관절 좌표는 업로드하지 않음.
- `yoloDiagnosticsJson`은 Room에만 남고 Remote DTO/LLM DTO/score JSON에 포함하지 않음.
- DEBUG ‘로컬 검증 세션’은 cloudEligible=false/LOCAL_ONLY로 저장. 후속 sync도 전송하지 않음.
- Android backup/device extraction 전체 제외 정책 유지. 새로운 권한·외부 서버 없음.

운동 분석 요약도 개인 데이터입니다. 기존 개인정보처리방침, 보유기간, 계정 삭제, 국외 이전/동의
완료 여부까지 이번 수정으로 보증하지 않습니다. 공개 배포 전 별도 검토가 필요합니다.

## 7. 검증 상태

최종 검증: 2026-11-01. 로컬 테스트는 실제 원격 RLS/기기간 동기화 성공 증명이 아닙니다.

| 항목 | 현재 상태 |
|---|---|
| Android assembleDebug | 성공. 최종 고정 소스 검증 1분 47초 |
| testDebugUnitTest | 61 suites / 582개 통과(신규 저장소 계약 18개 포함) |
| assembleDebugAndroidTest | 성공. 실제 기기에서 실행한 것은 아님 |
| Node 회귀 검사 | 120개 통과 |
| 실제 SQLite migration SQL 검사 | 7개 통과. Android Room validator와는 별개 |
| Android instrumented migration | 연결 기기 없음, 미실행 |
| APK / 비밀키 패턴 검사 | 모델 제외·MediaPipe 유지 / 변경 공개 텍스트 37개 통과. 이력/전체 dependency 감사 아님 |
| 원격 schema/RLS 확인 | 메타데이터 읽기 전용 확인 |
| 새 SQL/RPC 실행, 원격 저장/복원/RLS 동시성 | 미적용/미검증 |

새 JVM 검사는 계정 분리, 중복 ID, 양방향 mapper, 네트워크 실패/재시도, 원격 merge,
legacy/DEBUG local-only 전송 금지, epoch 전환, bounded paging/upload, 취소, YOLO DTO 제외를 다룹니다.
기존 WorkoutStatistics 회귀 테스트도 유지합니다. 실제 SQLite 테스트와 Android Room 생성 스키마
검증 테스트는 별개입니다. Android 테스트는 고유 fixture/in-memory DB만 쓰며 실제 사용자 DB를 건드리지 않습니다.

최종 실행 명령(기존 Gradle/SDK 캐시 접근 권한으로 실행):

```powershell
$env:GRADLE_USER_HOME = 'C:\Users\dlgus\.gradle'
$env:ANDROID_USER_HOME = 'C:\Users\dlgus\.android'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --offline --no-daemon --no-problems-report --no-build-cache --max-workers=2 '-PverificationBuildDir=dashboard-ui-verification'
node --test scripts/*.test.mjs
python scripts/test-yolo-local-migration.py
node scripts/verify-coaching-security.mjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-public-apk.ps1 -ApkPath build/dashboard-ui-verification/outputs/apk/debug/app-debug.apk
```

최신 앱: `build/dashboard-ui-verification/outputs/apk/debug/app-debug.apk`.
테스트 APK: `build/dashboard-ui-verification/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
검증 도중 수정된 mapper 소스보다 오래된 클래스가 사용되어 종목 변환 검사 1건이 실패한 적이 있습니다.
소스 고정 후 빌드 캐시 없이 다시 실행해 새 클래스 갱신과 전체 582개 통과를 확인했습니다.
기대값 완화/테스트 제외/소스 초기화는 하지 않았습니다. 기존 Gradle 10 관련 deprecation 경고는 남아 있습니다.

## 8. 운영자/실기기 확인 순서

1. 별도 승인된 환경에서 새 SQL 검토 → 적용 → authenticated A/B/anon RLS와 RPC 재호출/롤백 검증.
2. 최신 DEBUG APK를 기기 A에 설치하고 로그인 → 일반 운동 → 결과 확인 → 운동 기록의 동기화 확인.
3. 같은 계정으로 기기 B 로그인 → 운동 기록에서 같은 날짜/종목/횟수/점수/시간/열량 확인.
4. B에서 기록 선택 → 회차 분석/상세 리포트 확인. YOLO 진단이 B로 이동하지 않았는지 확인.
5. 네트워크를 끄고 운동 저장 → 로컬 조회 → 네트워크 복구 후 ‘다시 동기화’ → UUID 1행 확인.
6. A 로그아웃 → B 계정 로그인 → A 기록 미노출 확인. 다시 A 로그인 → A 캐시 유지 확인.
7. DEBUG 로컬 검증 운동은 여러 번 재시도해도 서버에 저장되지 않는지 확인.
8. 기존 기록이 있는 기기에서는 앱 데이터 삭제/재설치 대신 업데이트 설치하여 6→7 migration 확인.

## 9. 변경 파일 전체 목록

### 기존 파일 수정 (24개)

```text
PRIVACY_DATA_MAP.md
README.md
RELEASE_READINESS.md
SECURITY_REVIEW.md
app/src/androidTest/java/com/example/arptapp/data/YoloDiagnosticsMigrationTest.kt
app/src/main/java/com/example/arptapp/DashboardActivity.kt
app/src/main/java/com/example/arptapp/HistoryActivity.kt
app/src/main/java/com/example/arptapp/HomeActivity.kt
app/src/main/java/com/example/arptapp/ResultActivity.kt
app/src/main/java/com/example/arptapp/WorkoutStatisticsActivity.kt
app/src/main/java/com/example/arptapp/data/AppDatabase.kt
app/src/main/java/com/example/arptapp/data/ExerciseDao.kt
app/src/main/java/com/example/arptapp/data/ExerciseRecord.kt
app/src/main/java/com/example/arptapp/data/remote/SupabaseAuthRepository.kt
app/src/main/java/com/example/arptapp/data/remote/SupabaseRepository.kt
app/src/main/java/com/example/arptapp/presentation/report/ReportActivity.kt
app/src/main/java/com/example/arptapp/presentation/report/ReportNavigation.kt
app/src/main/java/com/example/arptapp/presentation/report/WorkoutReportData.kt
app/src/main/java/com/example/arptapp/presentation/report/WorkoutResultViewModel.kt
app/src/main/res/layout/activity_history.xml
scripts/pose-stability.test.mjs
scripts/test-yolo-local-migration.py
scripts/yolo-session-persistence.test.mjs
scripts/yolo-session-wiring.test.mjs
```

### 새 파일 (13개)

```text
app/src/androidTest/java/com/example/arptapp/data/WorkoutHistoryCacheDeviceTest.kt
app/src/main/java/com/example/arptapp/data/history/RoomWorkoutDataSource.kt
app/src/main/java/com/example/arptapp/data/history/WorkoutHistoryProvider.kt
app/src/main/java/com/example/arptapp/data/history/WorkoutHistoryRepository.kt
app/src/main/java/com/example/arptapp/data/history/WorkoutPayloadCodec.kt
app/src/main/java/com/example/arptapp/data/history/WorkoutSyncContract.kt
app/src/main/java/com/example/arptapp/data/remote/RemoteExerciseSession.kt
app/src/main/java/com/example/arptapp/presentation/history/WorkoutHistoryViewModel.kt
app/src/main/java/com/example/arptapp/presentation/report/WorkoutReportAccess.kt
app/src/test/java/com/example/arptapp/data/history/WorkoutHistoryRepositoryTest.kt
docs/WORKOUT_HISTORY_SYNC.md
scripts/workout-history-sync.test.mjs
supabase/workout-history-sync.sql
```

## 공식 API 근거

- [supabase-kt 3.0.0 RPC API](https://github.com/supabase-community/supabase-kt/blob/3.0.0/Postgrest/src/commonMain/kotlin/io/github/jan/supabase/postgrest/Postgrest.kt)
- [동일 버전 order/range API](https://github.com/supabase-community/supabase-kt/blob/3.0.0/Postgrest/src/commonMain/kotlin/io/github/jan/supabase/postgrest/query/PostgrestRequestBuilder.kt)
- [Supabase RLS](https://supabase.com/docs/guides/database/postgres/row-level-security)
- [Supabase SQL migrations](https://supabase.com/docs/guides/deployment/database-migrations)
