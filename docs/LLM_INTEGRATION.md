# 회차 분석과 선택형 AI 코칭

기준일: 2026-10-15. **현재 실제 LLM 제공자는 연결되지 않았으며, Android와 서버 모두 기본 비활성입니다.**
키를 넣거나 `LLM_ENABLED=true`만 설정해도 이 버전에서는 LLM이 호출되지 않습니다.
유료 API, 무료 한도 초과 시 유료 전환, 카드 등록, billing 활성화, 원격 배포·SQL 적용을 수행하지 않았습니다.

## 1. 판단과 설명의 책임 분리

```text
MediaPipe → 관절 측정 → 기존 ExerciseCounter (횟수 판단, 변경 없음)
                       └→ RepAnalysisEngine (회차 관찰 요약)
                           ├→ 로컬 상세 리포트 / 우선 코칭 / TTS
                           └→ 사용자 요청 + 승인된 설정일 때만
                               Edge Function → LlmProvider → 검증된 AI 설명
```

LLM은 새 자세 오류를 판정하거나 의료적 진단을 하지 않습니다. 기존 `RepRecord`와
`RepReportAnalyzer` 점수 정책을 유지하고 `StructuredRepAnalysis`를 별도로 수집합니다.
새 관찰 이벤트는 기존 점수를 자동으로 바꾸지 않습니다. 점수·15° 차이·20° 기울기 등의
초기 규칙은 전문가 검증을 거치지 않은 `ENGINEERING_INITIAL` 기준입니다.

## 2. 분석 API와 측정 범위

| API / 파일 | 책임 |
|---|---|
| `domain/report/StructuredAnalysis.kt` | scalar frame, 회차·운동 요약, nullable 측정 계약 |
| `RepFrameFeatureExtractor.extract` | 일시적인 좌표 → 품질 검사를 거친 각도; 좌표 보관 없음 |
| `RepAnalysisEngine.observe/complete/reset` | 최대 300프레임·20초 버퍼, 회차 완료 시 집계 후 폐기 |
| `WorkoutAnalysisAggregator.aggregate/forNetwork` | 전체 요약 / 마지막 50회 상세 전송 경계 |
| `LocalRepFeedback` | 관찰 강점 문구와 단일 우선 코칭 |
| `data/StructuredReportCodec` | 기존 `scoresJson`에 추가 envelope, 구버전 배열 읽기 유지 |
| `AiCoachingRepository.generateWorkoutReport` | Edge 요청·응답 검증; 제공자·키는 모름 |
| `AiReportViewModel` | 중복 클릭 억제·화면 회전·상태·로컬 fallback |

### 정면과 측면

- 스쿼트 정면: 좌우 무릎 내각, 같은 프레임의 최대 좌우 차이, 기존 정면 무릎 정렬 관찰.
- 스쿼트 측면: 가시성이 높은 한쪽 무릎·고관절 내각, 영상 종횡비를 반영한 몸통 기울기.
  가려진 반대쪽과 좌우 대칭 값은 `null`입니다.
- 숄더프레스: 가시적인 팔꿈치 최대 내각. 정면에서만 동시에 측정된 좌우 차이를 사용합니다.
- visibility 0.65 미만, 누락·비유한 값·퇴화 벡터는 측정 불가입니다.
- 방향 미설정에서는 새 관절별 측정과 오류를 판단하지 않습니다. `null`은 정상이나 0°가 아닙니다.

각도는 영상 좌표의 **2D 내각**이며 임상적 굴곡각·검증된 3D 모션캡처가 아닙니다.
정면 내각 차이는 영상상 관찰 지표이지 실제 좌우 관절 굴곡 차이의 확정값이 아닙니다.

### 단계와 오류

- 기존 카운터가 완료를 알릴 때만 새 회차를 확정합니다. 별도 카운터를 만들지 않습니다.
- `RepPhaseSegmenter`가 운동별로 분리합니다. 3° 이상 움직임 시작 전 최대 3프레임 준비 구간만 남깁니다.
  스쿼트는 하강→연속된 최소 내각 +5° 하단 구간→상승, 숄더프레스는 하단 준비→각도 증가(올리기)→상단→실제로 관찰된 복귀만 분석합니다.
  상단 정지는 `BOTTOM`으로 잘못 표시하지 않고 현재 enum에서 지원하지 않는 구간으로 둡니다.
  기존 숄더프레스 카운터는 70° 이하 하단/160° 이상 상단의 안정 프레임으로 **상단에서 회차를 확정**하므로,
  실시간 기록의 복귀/DESCENT는 보통 `null`입니다. 이전 회차의 내려오기를 이번 회차의 하강으로 가져오지 않습니다.
  전체 왕복 테스트 fixture에서는 실제 복귀가 포함될 때만 DESCENT를 계산합니다. 실패 후 재시도처럼 여러 파형이 섞이면 단계 판단을 보류합니다.
- 내려오기/올라가기 각도 변화가 10° 미만이면 해당 시간은 `null`입니다.
  하단에서 시작한 첫 회차는 이전 내려오기 시간을 만들어 넣지 않습니다.
- 대기·가림·1초 넘는 프레임 간격·운동/시점 전환은 미완성 버퍼를 초기화합니다.
  300프레임 또는 20초 초과로 앞부분이 버려지면 `partialCapture=true`, 전체 시간과 내려오기 시간은 `null`입니다.
- 단계는 각도 기반 초기 구간 분할입니다. 진짜 최저점·완전한 생체역학적 단계 분할로 보장하지 않습니다.
- 정면 무릎 정렬은 기존 `FormErrorAnalyzer`의 projected inward-offset/leg-width >0.08 규칙을 재사용합니다.
  `observeSquatKnees/observeKneeValgus`가 LEFT/RIGHT ratio를 별도로 반환하고 기존 `assessSquat()` 결과에는 상세 관찰을 추가했습니다.
  신뢰할 수 없는 한쪽은 `null`; 반대쪽의 유효 관찰은 유지합니다. 좌우는 MediaPipe 인덱스의 해부학적 좌우이며 화면 좌우가 아닙니다.
  ratio는 일시적인 scalar 버퍼에서만 severity 계산에 사용하고 새 wire/Room JSON 필드로 저장·전송하지 않습니다.
- 좌우 무릎/팔꿈치 차이 >15°, 측면 몸통 기울기 >20° 탐지 기준은 유지합니다.
  `FormEventAggregator`는 (오류 종류, side, phase)별로 **3프레임 이상 연속된 구간**만 채택합니다.
  phase가 바뀌면 연속 구간을 끊습니다. `true true true false true true`에서 마지막 2프레임은 제외합니다.
  한 phase에 채택된 여러 구간이 있으면 이벤트 하나로 집계합니다. `occurrenceFrames`는 그 phase의 채택된 구간 프레임 수 합계입니다.
  `timestampMs`는 첫 채택 구간의 시작 프레임을 회차 시작 기준으로 환산한 상대 시간입니다. raw camera timestamp가 아닙니다.
  내부 시간은 지연된 UI 콜백 시각 대신 프레임 제출 timestamp를 사용합니다. 로그·JSON에는 상대 시간만 남깁니다.
  같은 운동/시점에서 1초 초과 프레임 간격 등으로 앞부분을 잃으면 partialCapture를 유지해 전체/누락 하강 시간을 만들어 넣지 않습니다.
  프레임 수를 지속 시간의 실측이나 서로 독립된 오류 횟수로 표현하지 않습니다. phase에 근거가 없으면 이벤트를 추측하지 않습니다.
- `HIP_ASYMMETRY`, `SHALLOW_DEPTH`, `INCOMPLETE_EXTENSION`은 현재 **NOT_EVALUATED**입니다.
  개인별 목표와 신뢰할 측정 근거가 없으므로 오류를 만들어 넣지 않습니다.
- 새 실시간 정면 무릎 정렬 경로는 LEFT/RIGHT 이벤트를 각각 생성합니다. 양쪽이면 두 이벤트입니다.
  구버전 단일 boolean 호출자는 호환을 위해 side=null로 남기며 좌우를 소급 추측하지 않습니다.
  절댓값 비대칭과 전체 몸통 기울기는 우세 측을 알 수 없어 side=null입니다.
- 우선 코칭은 severity → 관찰 프레임 수 → 고정 오류 순서로 하나만 선택합니다.
  UI에 2.5초 유지하고 횟수와 함께 TTS로 안내합니다. LLM 응답을 기다리지 않습니다.
- 강점은 실제 양쪽 측정값이나 두 단계의 측정 성공에서만 생성합니다. 오류 없음 ≠ 완벽/안전입니다.

### Severity 우선도: ENGINEERING_INITIAL

`EngineeringFormPolicy`의 탐지 임계값 초과 정도를 사용합니다. 아래 값은 임상적 위험도·부상 등급이 아닙니다.
각 이벤트의 채택된 연속 구간에서 가장 큰 우선도를 사용하며, 1~2프레임 노이즈 구간의 값은 반영하지 않습니다.

| 관찰 값 | 우선도 1 | 우선도 2 | 우선도 3 |
|---|---|---|---|
| 무릎 projected inward ratio | >0.08, ≤0.16 | >0.16, ≤0.24 | >0.24 |
| 무릎/팔꿈치 각도 차이 | >15°, ≤25° | >25°, ≤35° | >35° |
| 몸통 기울기 | >20°, ≤30° | >30°, ≤40° | >40° |

검증된 정면 무릎 지표가 아니라 기존 2D 초기 heuristic입니다. 실기기 false positive와 전문가 기준 검증은 남아 있습니다.
phaseModelVersion=2를 새 회차에만 추가하고 이전 기록은 기본값 1로 읽습니다. schemaVersion/Room 버전/envelope는 유지합니다.
이전 숄더프레스 단계는 현재 전략으로 소급 재해석하지 않습니다.

## 3. 기록 호환성과 화면

`scoresJson`에 `{reportSchemaVersion:1, analyses:[기존 회차 분석], structured:{...}}`를 저장합니다.
Room 테이블/버전은 바꾸지 않았고 destructive migration을 새로 실행하지 않습니다.
기존 점수 배열과 회차 객체 배열은 계속 읽습니다. 과거에 없던 구조화 값은 소급해서 만들지 않습니다.
현재 신체정보·세션 원격 업로드 흐름은 변경하지 않았습니다. 새 구조화 요약은 우선 기기 기록에 저장합니다.
새 요약은 원격 회차 테이블에 자동 업로드하지 않습니다.

상세 리포트에서 로컬 관절 측정·단계 시간·관찰 강점·다음 회차 확인점·우선 코칭을 볼 수 있습니다.
AI 설명 영역은 별도이며 기본 버튼은 비활성입니다. 활성화한 승인 버전에서는 전송 안내 확인 후 수동 요청만 합니다.
화면 회전 중 요청/성공 결과는 ViewModel이 유지합니다. AI 결과의 영구 저장·서버 캐시·계정 간 공유는 하지 않습니다.
화면을 닫고 다시 열면 로컬 기록만 유지됩니다.

## 4. 공개 소스와 비밀 정보

공개 가능: Android 코드, `supabase/functions/generate-workout-report/` 소스와 프롬프트,
내용이 빈 `.env.example`, 이 문서, 검토용 quota SQL.

공개 금지: 실제 `.env`, LLM 키, Supabase service-role/secret, signing key, credentials 파일.
이 기능은 **service-role 키가 필요 없습니다.** 서버의 사용자 bearer JWT로 Auth 검증과 제한된 RPC를 실행합니다.
APK에는 LLM 키를 넣지 않습니다. `BuildConfig`, `strings.xml`, manifest, assets,
`local.properties`→APK로 LLM secret을 전달하지 마세요. Supabase 앱 설정에는 공개 publishable/anon 키만 허용합니다.

`.gitignore`는 env·개인키·자격증명·서명키를 제외하며 `.env.example`만 예외로 둡니다.
이미 추적된 비밀은 ignore로 제거되지 않습니다. 유출됐으면 먼저 폐기/교체하고 이력 처리는 별도 승인을 받으세요.
자동 commit/push/history rewrite는 수행하지 않습니다.

## 5. 요청·응답과 개인정보 경계

- JWT는 Android→Supabase `Authorization` 헤더에서만 사용합니다. LLM 입력 JSON/프롬프트에 넣지 않습니다.
- 서버는 `/auth/v1/user`로 토큰을 검증합니다. JWT를 decode한 userId만 신뢰하지 않습니다.
- 요청은 exact allowlist 스키마: 운동·회차·각도·위상 시간·관찰 이벤트·고정 문구·점수만 허용합니다.
  이메일/이름/userId/위치/비밀번호/생년월일/전화번호/영상/bitmap/원본 landmark가 추가되면 거절합니다.
- 입력 최대 128KiB, 회차 상세 최대 50개, 관찰 프레임 최대 300, 이벤트 최대 12개입니다.
  정면 무릎 LEFT/RIGHT × 3 phases와 비대칭 × 3 phases를 수용하고 중복 (type, side, phase)은 거절합니다.
  50회 초과 시 마지막 50회와 전체 평균·주요 관찰 요약·생략 회차 수만 보냅니다.
  더 작은 제한에서도 입력 크기를 넘으면 요청하지 않고 로컬 분석을 유지합니다.
- provider에는 검증된 구조화 입력과 공개 프롬프트만 전달할 수 있습니다.
  Auth 응답/사용자 객체/헤더/환경 변수는 provider API 인자가 아닙니다.
- 응답 최대 32KiB. exerciseType, summary, strengths, improvements, priority,
  nextWorkoutGoal, repFeedback와 회차별 evidenceKeys를 엄격하게 검증합니다.
  다른 종목, 새 회차, 누락 필드, 추가 필드, 측정되지 않은 근거 참조, 일부 의료·완벽성 문구는 거절합니다.
  숫자 문구도 해당 회차의 참조 근거와 단위에 맞춰 검사합니다. 시간의 초 변환과 정수/소수 첫째 자리 반올림만 허용합니다.
  이벤트를 참조할 때는 실제 occurrenceFrames/초기 우선도/상대 시간도 근거로 사용할 수 있습니다. 없는 프레임 수는 거절합니다.
  정확한 이벤트 참조는 `event:KNEE_VALGUS:RIGHT:DESCENT` 형식이며 null은 UNKNOWN입니다. 기존 `issue:TYPE`도 읽습니다.
  방향·phase 문구는 실제 참조 이벤트에 같은 조합으로 존재해야 합니다. RIGHT/DESCENT + LEFT/ASCENT가 RIGHT/ASCENT를 승인하지 않습니다.
  `N회차`를 명시한 문장은 해당 회차의 이벤트만 근거로 사용하며 다른 회차의 측을 빌려오지 않습니다.
  null side는 왼쪽/오른쪽 문구를 승인하지 않습니다. 관절 값이나 duration만으로 특정 side의 오류/phase를 말하지 않습니다.
  검증은 지원하는 한국어·영어 어휘의 보수적 lexical 검사입니다. 문장당 한 조합을 권장하며 복잡한 문장·동의어의 의미는 보장하지 않습니다.
  이는 숫자의 존재·단위 검사이지 자연어의 모든 의미/인과관계 검증은 아닙니다.
- 임의 자연어의 의미적 환각을 완전히 증명해서 차단할 수는 없습니다. 근거 참조 검증은 필요조건이며
  완전한 사실성 검증이 아닙니다. 로컬 수치가 우선이고 AI는 참고용입니다. 제공자 연결 전 추가 평가가 필요합니다.
- 서버/앱은 요청 본문·토큰·provider 원문 오류를 로그에 남기지 않습니다.
  회차 완료 시 `BuildConfig.DEBUG`에서만 `RepAnalysis` Logcat tag로 허용한 관절 요약·phase 시간·side·우선도·유효 프레임 수·상대 시간을 출력합니다.
  좌표/영상/사진/이메일/userId/JWT/키는 진단 API 입력이 아니며 출력하지 않습니다. release 화면/로그에 새 진단을 노출하지 않습니다.

## 6. 비용·요청 한도

현재 Android `AiCoachingPolicy.ENABLED=false`; 서버 `LLM_ENABLED=false`가 기본입니다.
provider factory는 `UnavailableLlmProvider`만 반환합니다. 현재 코드로는 외부 LLM 호출이 불가능합니다.
AI SDK/Gateway/새 유료 의존성을 설치하지 않았습니다. 기존 Supabase 계정의 요금제·청구 상태까지
로컬 코드 검사로 보장할 수는 없지만, 이 변경은 클라우드 설정이나 요금제를 변경하지 않았습니다.

향후 provider를 승인해 연결할 때 서버는 사용자별 UTC 일일 기본 5회, 최대 5회로 제한합니다.
`DAILY_LIMIT`은 1..5로 낮출 수 있지만 상한을 올릴 수 없습니다. 실패한 provider 호출도 이미 예약한 한도를 사용합니다.
잘못된 한도 설정(0·음수·비정수 등)은 기능을 unavailable로 처리하며 무제한/자동 확대하지 않습니다.
원격 RPC가 없거나 실패하면 **fail closed**로 provider를 호출하지 않습니다.
기기 메모리·Edge isolate 메모리로 한도를 흉내 내지 않습니다.
단, 사용자별 quota만으로 다계정 악용이나 프로젝트 전체 비용을 보장하지 못합니다.
제공자에 무료 초과 호출을 유료 전환하지 않는 하드 한도와 프로젝트 전체 제한이 없으면 연결하지 마세요.

**LLM provider의 무료 사용 조건은 변경될 수 있으며, 배포 전 반드시 현재 provider의 quota와 billing 정책을 다시 확인해야 합니다.**
무료라고 표시된 서비스도 카드·자동 과금·유료 플랜이 요구된다면 사용자 동의 없이 활성화하지 않습니다.

`supabase/workout-coaching-rate-limit.sql`은 **적용하지 않은 검토 템플릿**입니다.
private schema + RLS + 테이블 직접 접근 차단 + 고정 search_path definer + public invoker RPC를 사용합니다.
계정은 `auth.uid()`에서 결정하고 (account, UTC date) PK와 conditional upsert로 동시에 예약해도 상한을 넘지 않게 설계했습니다.
실제 DB에서 동시성·RLS·권한 테스트는 아직 하지 않았습니다. 계정 UUID/일자/횟수만 저장하며
워크아웃·프로필·영상은 저장하지 않습니다. 운영자가 오래된 quota 행을 수동 정리할 수 있으며 Cron은 추가하지 않았습니다.

## 7. fallback과 실패 처리

| 상황 | 처리 |
|---|---|
| 비활성/제공자 미설정 | 서버 503 / 앱 비활성 안내; Auth·DB·LLM outbound 호출 없음 |
| 오프라인·통신 실패 | 오류 안내; 이미 표시한 로컬 분석 유지 |
| 인증 만료/계정 변경 | 401; 로컬 분석 유지; 다른 계정 응답 재사용 금지 |
| quota 소진 | 429; 다음날 또는 로컬 분석 이용 |
| 잘못된 JSON/새 회차/누락 필드 | AI 결과 폐기; 로컬 분석 유지 |
| timeout/provider 오류 | 15초 서버 deadline, 18초 앱 coroutine deadline; 자동 재시도 없음 |

Android HTTP는 연결 4초·읽기 12초 제한, redirect 금지, 응답 바이트 제한을 사용합니다.
blocking I/O의 실제 취소 완료 시간은 네트워크·OS에 따라 deadline을 약간 넘을 수 있습니다.
향후 provider adapter는 AbortSignal 취소를 지키고 자체 최대 응답 크기/출력 토큰 제한도 반드시 구현해야 합니다.
실시간 프레임/횟수마다 LLM을 호출하지 않습니다. 운동 종료 후 리포트의 수동 요청만 지원합니다.

## 8. 나중에 실제 무료 제공자를 연결하는 순서

1. 지금은 설정하지 않아도 운동·상세 리포트를 사용할 수 있습니다. 기본 비활성을 유지하세요.
2. 원하는 제공자의 **현재 공식** 무료 quota, 카드 필요 여부, 무료 초과 시 처리, 상업/공개 앱 이용 약관,
   운동 데이터 보관/학습/국외 이전 정책을 확인합니다. 특정 서비스가 영구 무료라고 가정하지 마세요.
3. 자동 청구가 불가능한 조건과 이용자 개인정보 안내를 확인한 후 사용자에게 승인을 요청합니다.
   조건을 충족하지 않으면 연결하지 않습니다. 실제 앱 공개 전 이용약관·개인정보 고지는 별도 검토가 필요합니다.
4. 승인한 제공자의 버전별 공식 API를 확인하여 **서버에만** `LlmProvider` 구현을 추가하고 factory에 등록합니다.
   현재는 adapter가 없으므로 키만 입력해서는 동작하지 않습니다. HTTPS/허용 호스트/redirect 금지,
   AbortSignal/시간·출력 토큰·바이트 제한, 무료 quota 초과 시 중단을 구현합니다. 다른 유료 제공자로 fallback하지 않습니다.
5. Supabase CLI가 설치된 개발 환경에서 `supabase init`이 필요한지 현재 설정을 먼저 확인하고,
   `supabase migration new workout_coaching_rate_limit`로 새 migration을 생성합니다.
   검토한 SQL 템플릿을 그 생성 파일에 옮기고 로컬 테스트 프로젝트에서 권한·동시성 테스트를 진행합니다.
   기존 사용자 테이블/기록을 삭제하거나 reset하지 마세요. 운영 DB 적용·함수 배포는 별도 승인 후 수동으로 수행합니다.
6. Supabase Dashboard → Project → Edge Functions → Secrets에 provider 설정을 **직접** 입력합니다.
   `LLM_API_KEY`는 서버 secret으로만 관리합니다. `.env.example`에는 값을 쓰지 마세요.
   이 함수에는 `SUPABASE_SERVICE_ROLE_KEY`를 입력할 필요가 없습니다.
7. 무료 조건, quota RPC, 인증 테스트가 끝날 때까지 `LLM_ENABLED=false`를 유지합니다.
   승인 후 `supabase functions deploy generate-workout-report`를 수동 실행하되 JWT 검증은 유지합니다.
   `--no-verify-jwt`로 인증 오류를 우회하지 않습니다. 실제 토큰과 프로젝트 설정을 확인하세요.
8. 승인된 Android 배포 버전에서 공개 feature flag `AiCoachingPolicy.ENABLED`를 true로 바꾸고 APK를 다시 빌드합니다.
   키를 APK에 넣는 단계는 없습니다. 사용자 동의 후 1회 소량 요청부터, 실패·한도·오프라인 동작을 검증합니다.

실제 secret을 터미널 명령줄·스크린샷·GitHub issue에 붙이지 마세요.
공식 안내: [함수 인증](https://supabase.com/docs/guides/functions/auth),
[서버 Secrets](https://supabase.com/docs/guides/functions/secrets),
[RLS](https://supabase.com/docs/guides/database/postgres/row-level-security).

## 9. 로컬 검증

```powershell
node --test supabase/functions/generate-workout-report/handler.test.ts supabase/functions/generate-workout-report/security.test.ts supabase/functions/generate-workout-report/entrypoint.test.ts supabase/functions/generate-workout-report/event-claims.test.ts
node scripts/verify-coaching-security.mjs

$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user-home'
$env:ANDROID_USER_HOME = Join-Path $env:GRADLE_USER_HOME 'android'
.\gradlew.bat assembleDebug testDebugUnitTest --offline --no-daemon --no-problems-report -PverificationBuildDir=location-manager-verification --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8'
```

Node 24의 native TypeScript 실행으로 순수 핸들러와 injected fake Auth/quota/provider를 테스트합니다.
실제 provider·Auth·DB·클라우드 서비스를 테스트하지 않으며 과금 호출도 없습니다.
Android JVM 테스트는 단계·측정 불가·양측 차이·버퍼·집계·구버전 기록·AI 응답 검증·비활성 요청 차단을 포함합니다.
Deno가 없는 이 개발 환경에서 Supabase Deno 배포/typecheck는 별도 검증이 필요합니다.
실기기에서 전/후면 카메라, 촬영 방향, 회차 완료 TTS/UI, 오래된 기록 열기, 회전, 화면 종료를 확인하세요.

### 2026-10-15 최초 회차/LLM 기반 구현 검증 이력 (고도화 이전)

- 최종 `assembleDebug testDebugUnitTest`: **BUILD SUCCESSFUL**, 4분 3초. 출력이 이어졌으며 3분 이상 무응답인 명령은 없었습니다.
- Android JVM: **140개 테스트 / 28개 suite, 실패·오류 0** (기존 121개 + 새 19개).
- Node 서버/보안/entrypoint shim: **19개 테스트 통과**. 실제 Deno 클라우드 실행은 아님.
- 변경된 공개 텍스트 33개 scoped secret-pattern 검사 통과; private 파일 ignore와 빈 예제 공개 규칙 확인.
- APK의 기존 Supabase 키는 공개 publishable 키로 분류됨. 키 값·claim 원문은 출력하지 않음.
- `git diff --check` 통과. CRLF→LF 안내 및 Gradle 10 관련 기존 deprecation 경고는 남아 있음.
- APK: `build/location-manager-verification/outputs/apk/debug/app-debug.apk`.
- 실기기 실행, 운영 Auth/DB, SQL 권한/동시성, 실제 LLM 응답은 **미검증**. 배포·과금 호출·원격 변경 없음.

전체 저장소 라이선스 검사나 Git 이력 스캔은 반복하지 않았습니다. 위 검증은 법적 적합성의 보증이 아닙니다.

좌우/phase 개선의 기기별 검증 기준과 **합성 테스트 fixture 기반 리포트 예시**는 [DEVICE_VALIDATION.md](DEVICE_VALIDATION.md)에 기록합니다.

### 2026-10-15 좌우·phase 고도화 검증 결과

- 최종 `assembleDebug testDebugUnitTest`: **BUILD SUCCESSFUL**, 1분 10초.
- Android JVM **195개 / 32개 suite, 실패·오류 0**. 기존 140개 유지 + 새 55개. 기존 단일 이벤트 기대값은 phase별 연속 프레임 검증으로 강화했습니다.
- Node 서버/보안/entrypoint shim **37개 통과**. 기존 19개 유지 + 새 18개. 실제 Deno 클라우드 배포 검증은 아님.
- 공개 변경 텍스트 30개의 scoped secret-pattern 검사 및 `git diff --check` 통과. `.gitignore`는 변경하지 않았습니다.
- 첫 새 카운터 통합 테스트의 fixture가 기존 하단 70° 기준보다 큰 80°에서 시작해 실패한 것을 바로잡았습니다. 카운터 임계값은 수정하지 않았습니다.
- 출력이 이어졌으며 3분 이상 무응답인 명령은 없었습니다. 기존 Gradle 10 deprecation / 줄바꿈 안내는 남아 있습니다.
- 연결된 adb 기기 0대: 실기기 카메라·좌우·TTS·성능·release Logcat은 미검증입니다.
- provider/키/billing/원격 SQL/배포/AI 리포트 영구 저장은 변경하지 않았습니다.

## 10. 이번에 하지 않은 작업

실제 LLM provider 연결·AI 리포트 영구 저장·운영 DB migration/배포·기기 카메라 검증·전문가 오류 기준 검증은 남아 있습니다.
YOLO Android 통합, 모델 재배포·데이터셋 라이선스 승인 상태는 변경하지 않았습니다.

## 변경 파일 묶음

Android 경로의 기준은 `app/src/main/java/com/example/arptapp/`입니다.

- 기존 연결부: `DashboardActivity.kt`, `viewmodel/MainViewModel.kt`, `ui/feedback/FeedbackManager.kt`.
- 분석 계약·엔진: `domain/report/StructuredAnalysis.kt`, `RepFrameFeatureExtractor.kt`, `RepAnalysisEngine.kt`.
- AI 계약·검증: `domain/report/AiWorkoutReport.kt`, `MeasuredClaimValidator.kt`.
- 저장 호환: `data/RepAnalysisCodec.kt`, `StructuredReportCodec.kt`.
- 요청 어댑터: `data/remote/AiCoachingRepository.kt`.
- 리포트 화면: `presentation/report/ReportActivity.kt`, `AiReportViewModel.kt`, `StructuredReportPresentation.kt`;
  레이아웃 `app/src/main/res/layout/activity_report.xml`.
- Android 테스트: `app/src/test/java/com/example/arptapp/domain/report/`의
  `RepAnalysisEngineTest.kt`, `AiReportValidatorTest.kt`, `StructuredReportCodecTest.kt`.
- 서버: `supabase/functions/generate-workout-report/`의 `index.ts`, `handler.ts`, `contracts.ts`,
  `provider.ts`, `prompt.ts`, `.env.example`, `handler.test.ts`, `security.test.ts`, `entrypoint.test.ts`.
- 서버 설정·검토 SQL: `supabase/config.toml`, `supabase/workout-coaching-rate-limit.sql`.
- 공개/문서/검사: `.gitignore`, `README.md`, `docs/ARCHITECTURE.md`, 이 문서,
  `scripts/verify-coaching-security.mjs`.

의존성 추가/삭제, Room 스키마 변경, 모델 파일 변경은 없습니다.

추가 고도화 파일: `EngineeringFormPolicy`, `RepPhaseSegmenter`, `FormEventAggregator`,
`LocalEventPresentation`, `RepDiagnosticFormatter`, `DirectionalClaimValidator`; 기존 `FormErrorAnalyzer`와 각도 extractor를 연결했습니다.
서버 추가 API는 `event-claims.ts`, 테스트는 `event-claims.test.ts`입니다. 원격 SQL과 실제 provider는 수정/적용하지 않았습니다.
