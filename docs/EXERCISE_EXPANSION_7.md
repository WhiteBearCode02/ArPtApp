# 7종 운동 확장 · 구현 및 검증 경계

업데이트: 2026-11-12 · 기존 EXP02 모델/운동 기록 보존 · 새 운동 기준은 `ENGINEERING_INITIAL`

## 지원 범위

| 코드 | 표시 이름 | 반복 완료 조건 | 주 관찰값 | 권장 촬영 |
|---|---|---|---|---|
| SQUAT | 스쿼트 | 기존 하강→저점→복귀 | 무릎·몸통·좌우 차이, 기존 기준 유지 | 정면/측면 |
| SHOULDER_PRESS | 숄더 프레스 | 기존 아래→위 완료 | 팔꿈치·좌우 차이, 기존 기준 유지 | 정면/측면 |
| BICEP_CURL | 바이셉 컬 | 팔별 펴짐→굽힘→펴짐 | 팔별 팔꿈치 ROM·상완 이동·몸통 변화 | 정면/측면 |
| PUSH_UP | 푸시업 | 펴짐→굽힘→펴짐 | 팔꿈치 ROM·측면 어깨–엉덩이–발목 정렬 | 측면 권장 |
| BENT_OVER_ROW | 벤트오버 덤벨 로우 | 팔 펴짐→당김→펴짐 | 팔꿈치 ROM·몸통 기울기 변화 | 측면 권장 |
| TRICEPS_EXTENSION | 오버헤드 트라이셉스 익스텐션 | 굽힘을 확인한 뒤 펴진 지점에서 완료 | 팔꿈치 ROM·상완 이동·몸통 변화 | 정면/측면 |
| SIDE_BEND | 덤벨 사이드 벤드 | 중립→한쪽 기울기→중립 | 방향별 몸통 기울기·복귀 편차 | 정면만 |

`ExerciseType.supported`가 목록의 단일 기준이다. LUNGE는 과거 enum/코드 호환을 위해 남지만 지원 목록·세션 확정·기준 기록 선택에서 제외한다. 새 운동 자동 분류는 **2D 관절 기반 보수적 규칙**이며, 학습된 7-class YOLO가 아니다. 모호한 자세/가림/잘못된 촬영 방향은 UNKNOWN 또는 측정 불가로 유지한다. 정면 영상에서 로우/푸시업은 투영 때문에 확정되지 않을 수 있다.

## 변경 전 조사 → 현재 구조

변경 전에는 두 운동만 세션 확정·카운터·구조화 리포트·서버 V1 검증을 통과했다. TTS는 시작 버튼과 회차 완료 시점에만 호출됐다. Room의 운동 이름은 문자열, 상세 리포트는 JSON이므로 열 추가 없이 확장할 수 있었다.

```text
CameraX → MediaPipe → ExerciseFeatures
  → ExerciseClassifier (기존 안정화) → SessionExerciseLock (7종)
  → ExerciseMeasurementStrategy (운동/촬영 방향별 실제 관측)
  → ExerciseCounter → CompletedRep 목록 (팔별 동시 완료 보존)
  → RepAnalysisEngine → 회차 score/events/feedback
  → 기존 FeedbackManager/TTS · Report
  → StructuredReportCodec → Room / 기존 Supabase 동기화 → History 상세 재열람

YOLO EXP02 (2-class Shadow) → 별도 진단 집계; 새 5종은 비교 대상 아님
```

기존 두 운동의 카운터 분기·StandardPose 임계값·점수 공식은 유지한다. 새 운동은 `ExpandedExerciseCounter`, `ExpandedRepAnalysisEngine`, `ExpandedRepScoreCalculator`로 분리했다. 측정/분류/카운터의 신규 수치는 `ExerciseMotionPolicy`에서 관리한다. 외부 유료 API나 새 서비스를 추가하지 않았다.

## 카운터·좌우·안정화

| 신규 운동 | 전환 각도 | 복귀/완료 각도 | ROM 비교 기준 |
|---|---:|---:|---:|
| 바이셉 컬 | 팔꿈치 ≤70° | ≥150° | 80° |
| 푸시업 | ≤100° | ≥155° | 70° |
| 벤트오버 로우 | ≤90° | ≥150° | 70° |
| 트라이셉스 익스텐션 | ≤90° 재준비 | ≥155° 완료 | 70° |
| 사이드 벤드 | 중립 대비 ≥15° | 절대 기울기 ≤5° | 20° |

모두 **임상 정상/오류 경계가 아닌 초기 공학값**이다. 팔꿈치 각도는 어깨–팔꿈치–손목의 내각이며 임상적 굴곡각과 혼용하지 않는다. 몸통 기울기도 촬영면의 2D 값이다.

- 기존 안정 프레임/최소 회차 시간 설정을 재사용한다. 한 프레임 임계값 통과, 너무 빠른 동작, 불완전 왕복은 완료하지 않는다.
- 컬은 해부학적 LEFT/RIGHT 별 독립 상태·버퍼를 사용한다. 동시 완료 시 두 회차를 보존한다. 반대쪽 팔이 보이지 않는다고 다른 팔의 진행을 삭제하지 않는다.
- 사이드 벤드는 어깨·골반의 해부학적 축으로 좌우 부호를 결정한다. 프리뷰 수평 반전만으로 해부학적 좌우가 바뀌지 않는다. 양쪽 방향은 각각 한 회차다. 중립 복귀 없이 반대 방향으로 건너뛰면 취소한다.
- 중립으로 되돌아온 미완료 동작은 재준비한다. 완료 정보가 실제 동작 시작 시각을 분석 엔진에 전달해 이전 시도/대기 시간이 다음 회차에 섞이지 않게 한다.
- 관절 신뢰도 미달·측정 누락·시간 역행·1초 초과 프레임 공백은 진행 중 상태를 무효화한다. 이미 완료한 횟수는 유지한다. 회차 버퍼는 최대 300프레임/20초다.
- 횟수와 자세 품질을 분리한다. 자세 변화 이벤트가 발생해도 유효한 왕복의 횟수를 임의로 삭제하지 않는다.

## 관찰·회차 리포트

신규 관찰 후보는 상완 위치 변화, 몸통 기울기 변화, 측면 몸통 선 정렬 편차, 좌우 운동 범위 차이, 중립 복귀 편차다. 시점·촬영 방향·실제 관측값과 3프레임 이상의 근거가 있을 때만 이벤트를 만든다. 컬의 반대쪽 비교는 **직전에 완료된 반대 팔 회차 ROM**과 비교하며, 현재 쉬는 팔 각도를 오류 근거로 사용하지 않는다.

회차 카드에는 해당 운동의 실제 관절값·단계 시간·이벤트·개선 문구를 표시한다. 기존 저장 phase 코드 `DESCENT/BOTTOM/ASCENT`는 유지하되 신규 운동의 사용자 문구는 굽히기/펴기, 당기기/돌아가기, 기울이기/중립 복귀로 해석한다. 익스텐션은 위에서 완료되므로 그 이후 아직 수행하지 않은 복귀 구간을 생성하지 않는다.

측면에서는 보이지 않는 반대쪽 관절/대칭값을 만들지 않는다. UNKNOWN·가림·부분 포착은 null/측정 불가이며 0점·정상으로 치환하지 않는다. 표시 불가능한 지표 행은 숨긴다. 점수는 측정 가능한 요소만 분모에 포함하고, ROM 근거가 없으면 점수도 null이다. 새 운동에 기존 스쿼트 90°/160° 등으로 점수를 대입하는 fallback은 금지한다.

초기 신규 점수 가중치: ROM 30 / 대칭 25 / 안정성 20 / 단계 15 / 관찰 이벤트 10. 점수는 공학적 운동 보조 지표이며 전문가 인증·의료적 안전 판정이 아니다.

## 음성

`ExerciseSpeechFormatter`가 7종 이름과 회차 문구를 공통 생성한다. 시작 버튼을 누를 때 현재 준비 종목의 시작 안내, 회차 완료 시 `N회 완료`와 근거 있는 우선 피드백을 한 번 호출한다. 프레임마다 말하지 않는다. 기존 QUEUE_FLUSH 정책을 유지했으므로 동시 팔 완료/빠른 연속 회차에서 실제 들리는 순서는 실기기 확인이 필요하다.

## 저장·API 계약·호환성

- Room 버전 7/테이블은 변경하지 않았다. 운동 이름 문자열과 상세 JSON으로 7종 저장이 가능하다. 기존 계정별 owner/session 분리·로컬 전용 진단 보존 정책도 유지한다.
- 신규 실시간 구조화 분석은 **schemaVersion=2**. 회차 activeSide와 새 관절/변화 지표는 nullable·선택적 필드다.
- V1은 기존 두 운동/기존 필드만 엄격하게 허용한다. 오래된 배열 기록/V1 리포트 읽기와 기존 상세 재열람을 유지한다. V1을 새 운동으로 가장해 통과시키지 않는다. V2의 관측 0프레임은 `partialCapture=true`, phaseModelVersion=2, 점수·좌우·시간·모든 지표 없음, 이벤트·강점·개선 후보 없음인 정확한 “측정 근거 부족” placeholder만 허용한다. 1–2프레임이나 임의의 수치/정상 문구는 허용하지 않는다.
- Android `ExerciseMetricPolicy`와 서버 `contracts-v2.ts`/`exercise-matrix.ts`가 운동·촬영 방향·지표·이벤트를 검증한다. 실제 Android serializer가 생성한 7종 기본 + 5종 이벤트 + 7종 측정 누락 payload를 서버에 **네트워크 없이 함수 호출**로 검증한다.
- 기존 Supabase history DTO의 문자열/JSON 경로를 사용한다. 저장소 SQL에는 2종 enum 제한이 없으므로 원격 migration은 만들거나 적용하지 않았다. 실제 원격 DB의 적용 상태/RLS·다른 기기 동기화는 이번에 확인하지 않았다. 기존 요금제 저장·트래픽 한도에 따른 비용 가능성은 남아 있다.
- LLM provider는 계속 비활성이다. 프롬프트/V2 검증만 확장했으며 실제 모델 호출·배포·키 추가는 없다. 다른 운동명·측정 없는 숫자/신체 부위·근거 없는 방향/의학적 단정은 거부한다. 텍스트 검증이 모든 자연어 오해를 보장해 막는 것은 아니다.

## YOLO·프라이버시·비용 경계

EXP02 모델 파일·class mapping(0 SQUAT, 1 SHOULDER_PRESS)·threshold·pre/postprocessing·Shadow 정책은 바꾸지 않았다. 새 5종 세션은 `UNSUPPORTED_EXERCISE / NOT_APPLICABLE`이며 일치율/불일치율 계산에 넣지 않는다. primary/session 명칭은 7종을 보존하되 YOLO raw/stable class 집계는 기존 2종+UNKNOWN만 허용한다. `NOT_APPLICABLE`은 모델 고장이나 사용자 자세 오류를 뜻하지 않는다.

원본 영상·사진·프레임별 관절 좌표를 서버/LLM payload에 추가하지 않는다. 합성 테스트 fixture만 build 경로에 생성한다. 모델/개인 검증 자료는 기존 ignore 및 APK 제외 정책을 유지한다. 개인 EXP02 포함 빌드는 기존 명시적 opt-in/권리 고지 경계를 바꾸지 않으며 일반 공개 빌드와 구분한다. 데이터셋 다운로드·재학습·모델 재배포 권리의 신규 판정은 수행하지 않았다.

## 근거·데이터셋

[추가 논문 매핑](BIOMECHANICS_REFERENCES_7.md)을 참조한다. 논문 실험의 사실은 `PAPER_DIRECT`, 그 사실을 근거로 앱에서 관찰할 지표를 선택한 것은 `PAPER_SUPPORTIVE`, 앱의 모든 신규 숫자 임계값/점수는 `ENGINEERING_INITIAL`이다. MediaPipe 좌표로 EMG, 근활성, 척추 하중, 3D 견갑골 움직임 또는 부상 위험을 측정한다고 표현하지 않는다.

현재 연구 참고 데이터셋은 [Mendeley V3](https://data.mendeley.com/datasets/kgbb3yn47p/3), DOI `10.17632/kgbb3yn47p.3`, **A Multi-View Raw Video Dataset of Seven Fitness Exercises with Good/Bad Form Labels** (2026-08-10, CC BY 4.0)다. Duddela Sai Prashanth 외 기여자의 26명·7운동·정면/측면/대각선 영상이다. 세 폴더 구성은 같은 영상의 다른 정리 방식이므로 독립 샘플로 중복 계산하면 안 된다. 향후 학습은 사람 단위 분할이 필요하다. 이 문서는 **EXP02가 이 데이터로 학습되었다는 주장이 아니며**, 기존 AI-Hub 실험 출처를 대체하지 않는다. good/bad 라벨도 개인에 대한 의학적 정답이 아니다.

## 재현·기기 확인

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --offline --no-daemon --no-problems-report --no-build-cache --max-workers=2 '-PverificationBuildDir=exercise-seven-verification'
node --experimental-strip-types --test scripts/*.test.mjs supabase/functions/generate-workout-report/*.test.ts supabase/functions/youtube-ranking/*.test.ts
node --experimental-strip-types scripts/verify-seven-exercise-contracts.mjs
node scripts/verify-coaching-security.mjs
.\scripts\verify-public-apk.ps1 -ApkPath build/exercise-seven-verification/outputs/apk/debug/app-debug.apk
```

자동 테스트: 기존 회귀, 7종 1회/10회/노이즈/최소 시간/가림/초기화/전환, 컬 양팔/교대·측면 누락, 사이드 벤드 좌우·반전, 익스텐션 top jitter, 측정·부분 포착·점수·V1/V2 저장 codec·서버 엄격 검증·YOLO 비교 범위를 검증한다. 합성 좌표/시간 테스트 통과는 실제 사용자의 인식 정확도가 아니다. 최종 실행 수치와 APK는 [검증 기록](EXERCISE_EXPANSION_7_VALIDATION.md)을 기준으로 한다.

메모리가 부족한 Windows 환경에서는 동시에 여러 Gradle 빌드를 실행하지 않는다. 필요 시 위 명령의 worker를 1개로 줄이고 `'-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'`를 명령행에만 추가할 수 있다. 사용자 IDE 종료나 프로젝트의 전역 JVM 설정 변경 없이 검증할 수 있다.

**남은 실기기 확인:** 사람별 7종 분류/세션 lock, 각 10회·휴식·중도 복귀, 양팔 동시/교대, 전후면 미러·줌·가림, FRONT/SIDE 전환, 실제 TTS 중복/누락, 종료/재실행/계정 전환 후 Room·History 동일 상세, 허가된 Supabase 두 기기 동기화. 이번 작업에서는 기기 설치·운동 수행·원격 배포/SQL·모델 실행 검증을 하지 않았다.
