# 사이드 벤드가 스쿼트 세션으로 남는 문제 수정

날짜: **2026-11-15**, 기존 `ai-v2` 브랜치.

**실사용 신고를 바탕으로 landmark 분류·0회 세션 초기 교정·화면 표시 경계를 수정했습니다.** 수정 후 JVM 870개, 관련 Node 165개, 기존 V2 계약 fixture 19개와 `assembleDebug`/`lintDebug`가 통과했습니다. 새 시연용 APK를 만들었고 기존 APK는 보존했습니다. 수정 앱의 **실제 휴대폰 운동 재검증은 NOT_RUN**입니다. 자동 commit/push/PR/merge는 하지 않았습니다.

## 1. 무엇이 문제였는가

증상은 실제 사이드 벤드 중 화면에 스쿼트 세션/안내가 남는 것입니다. 이것만으로 YOLO 학습 실패나 동일 프레임의 직접 스쿼트 판정을 확정할 수 없습니다. 이번 수정의 세션 결정 경로는 계속 **MediaPipe landmark → 기존 `ExerciseClassifier` → session lock**이며 YOLO는 Shadow입니다.

| 경계 | 기존 코드에서 확인한 문제 |
|---|---|
| 현재 프레임 후보 | 사이드 벤드는 양쪽 팔 모두 충분히 보이고 펴져 있어야 했습니다. 덤벨을 든 한 팔은 내려가고 반대 팔은 허리에 굽힌 자세에서 SIDE_BEND 증거가 탈락할 수 있었습니다. |
| 안정화 결과 | 분류기는 짧은 UNKNOWN 구간에서 이전 stable 결과를 의도적으로 유지합니다. 따라서 현재 후보가 UNKNOWN이어도 stable SQUAT가 잠시 남을 수 있습니다. 다른 팔 모양과 몸통 조건에 따라 curl 후보와의 혼동도 별도로 구분해야 합니다. |
| 세션 잠금 | 최초 supported 종목을 잠그면 0회 상태에서도 바꿀 수 없어 초기 잘못된 스쿼트 잠금이 유지될 수 있었습니다. 제목·횟수 단위·분석은 세션 종목을 따라갔습니다. |

**동일 프레임의 조건 충돌을 구분합니다.** 사이드 벤드의 곧은 무릎 조건 `≥145°`와 기존 스쿼트 후보의 무릎 조건 `≤135°`는 같은 scalar에서 동시에 성립하지 않습니다. 따라서 해당 사이드 벤드 재현 입력을 “그 프레임에서 스쿼트로 직접 분류됐다”고 설명하지 않습니다. 현재 후보·이전 stable·세션 잠금을 따로 검증했습니다.

이 원인은 코드/합성 scalar 회귀로 확인한 범위이며, 신고 당시 휴대폰 프레임·로그를 다시 수집해 전체 실제 사건을 재현한 결과는 아닙니다.

## 2. 수정된 분류 정책

`AdditionalExerciseClassifier`의 정면 SIDE_BEND 경로를 기존 수치 안에서 정리했습니다.

- 정면에서 양쪽 다리 visibility와 곧은 무릎 증거는 계속 필요합니다.
- 몸통 signed lateral inclination의 절댓값이 기존 `15°~45°` 범위에 있어야 합니다.
- 내려가는 팔 **하나 이상**은 충분히 보이고, 팔꿈치가 기존 `≥140°`, 손목이 어깨 아래라는 증거가 있어야 합니다.
- 반대 팔은 허리에 굽혀 있거나 arm visibility가 낮을 수 있습니다. 양쪽 다리와 몸통 증거를 생략하는 변경은 아닙니다.
- 명확한 측굴/다리 조건이 있으나 내려가는 팔 증거가 부족하면 **UNKNOWN으로 명시적 보류**합니다. 이 경로가 스쿼트 fallback으로 흘러가지 않도록 했습니다.
- 실제 스쿼트·숄더 프레스 회귀와 FRONT/SIDE/UNKNOWN 구분을 유지했습니다. 무릎·팔·측굴 임계값을 낮춰 테스트를 통과시키지 않았습니다.

실제 측굴 측정은 계속 양쪽 어깨 11/12와 골반 23/24의 visibility·좌표·양 축 폭/방향을 검증합니다. 측정불가를 `0°`로 만들지 않습니다. `measurePose`의 signed 값은 카운터와 회차 feature에 보존하며, 개인 기준 비교용 벡터 API의 절댓값과 구분합니다.

## 3. 0회 초기 세션 교정과 완료 경계

`SessionExerciseLockChange`의 `NONE / LOCKED / CORRECTED`를 통해 종목 변화와 후속 초기화를 명시적으로 연결했습니다.

1. landmark stable 결과와 **현재 candidate가 같은 supported 종목**일 때만 새 증거로 인정합니다.
2. 최초 잘못된 잠금의 교정은 **완료 0회 상태에서 세션당 한 번**만 허용합니다.
3. 다른 종목이 classifier 자체 안정화를 통과한 뒤에도, 동일한 `stable == candidate` 증거를 **추가 5프레임 연속** 확인해야 교정합니다. UNKNOWN·unsupported·기존 종목 복귀·candidate 불일치는 교정 증거를 끊습니다.
4. activity/counter의 완료 횟수와 ViewModel의 저장된 회차 수 중 하나라도 1 이상이면 세션 종목 변경을 막습니다. 완료 경계를 관찰한 사실은 외부 counter가 나중에 0을 반환해도 reset 전까지 유지됩니다.
5. 기존 단일 stable 관찰 호환 API는 최초 잠금만 가능하며 교정을 승인하지 않습니다.

5프레임은 기존 classifier의 공통 안정화 상수를 재사용한 공학적 잡음 정책이며, 새로운 의학적/운동역학적 기준은 아닙니다. YOLO 결과·외부 flag·계정 정보로 잠금을 교정하지 않습니다.

## 4. 카운터·이전 안내·화면 일관성

**카운터 production 코드와 SIDE_BEND `15°/5°` hysteresis는 변경하지 않았습니다.** signed 방향, 기본 3프레임 안정화, 최소 600ms 회차 조건을 그대로 둡니다.

사용자 제시 각도 `0,5,10,16,20,25,20,10,4,0`과 그 음수 순서를 각각 **샘플당 3프레임 × 150ms** 유지한 회귀에서 오른쪽/왼쪽 각각 1회와 peak 25°를 확인했습니다. 원시 샘플을 한 프레임씩만 넣으면 처음 `0°,5°`와 마지막 `4°,0°`가 각각 2프레임뿐이어서 기존 3프레임 dwell을 만족하지 않습니다. 이 시간 조건을 숨기거나 임계값을 완화하지 않았습니다.

Dashboard는 **CORRECTED가 승인됐을 때만, 해당 프레임을 새 종목으로 측정하기 전에** 다음 미완성 상태를 폐기합니다.

- counter/회차 분석 engine의 미완성 상태와 각도 sequence·이전 error tag.
- 진행 중 sway·가속도 기준과 일시적인 이전 우선 안내문.
- 반복 안내의 중복 방지 상태와 activity 시작 TTS, 화면/overlay의 이전 코칭.

경과 시간·classifier 증거·기존 저장 기록을 지우지 않으며 ViewModel 전체 세션 reset을 호출하지 않습니다. 이미 완료한 회차를 삭제해서 0회 교정 조건을 만드는 방식도 아닙니다.

`WorkoutExerciseUiFormatter`는 세션 종목을 기준으로 감지 칩·제목·횟수 단위의 문자열을 만드는 **순수 표시 모듈**입니다. 예를 들어 세션이 교정되면 제목과 단위도 “덤벨 사이드 벤드”로 함께 바뀝니다. 현재 감지와 잠긴 세션이 다르면 둘을 따로 표시합니다. formatter는 종목 잠금·점수·카운터·파일·서버를 변경하지 않습니다.

측굴의 좌우·최대 기울기·“옆으로 기울이는 구간 / 중앙으로 돌아오는 구간”·기본 측굴 코칭을 리포트/로컬 기록 왕복에서도 검증했습니다. 스쿼트 무릎 metric이나 안내를 측굴 회차에 붙이지 않습니다.

## 5. 실제 실행한 자동 검증

기존 상태의 RED 재현에서는 **8개 중 4개 failure**를 먼저 확인했습니다. 아래 GREEN은 수정 후 실행 결과이며 RED 실패 기록을 무시하거나 기준을 바꾼 PASS가 아닙니다.

| 검증 | 결과 / 범위 |
|---|---|
| 전체 JVM | **82 suites / 870 tests, failures 0 / errors 0 / skipped 0** |
| 기존 7종 classifier / 기존 classifier | 4 / 2 tests PASS |
| SIDE_BEND classifier 회귀 | 9 tests PASS: 한쪽 팔·반대 팔 굽힘/가림·양 다리·실제 squat/press·비정상 값·촬영 방향·기존 threshold |
| 기존 7종 counter | 56 parameterized tests PASS |
| signed SIDE_BEND counter 추가 | 6 tests PASS: 좌/우 exact sequence·raw dwell·방향 stitching 차단·측정불가 후 복구 |
| 기존 7종 pipeline / signed SIDE_BEND pipeline | 42 / 3 tests PASS |
| 세션 잠금 / ViewModel 잠금 / 라우팅·UI | 10 / 12 / 4 tests PASS |
| 관련 Node | **165 tests PASS**. source wiring/기존 경계 검사이며 Android 화면을 렌더링한 검사는 아님 |
| 기존 V2 계약 fixture | **19개 PASS**. 원격 DB 적용이나 실계정 검증은 아님 |
| `assembleDebug` / `lintDebug` | **PASS**, 전체 JVM과 같은 오프라인 실행에서 BUILD SUCCESSFUL, 5m55s |
| Lint 세부 | **Error/Fatal 0, Warning 299**. 기존 경고 전체를 해결한 변경은 아님 |
| APK packaging | **PASS**, private 모델/Golden 제외, MediaPipe task·정확한 OpenCV NOTICE 유지 |
| 휴대폰 실제 좌/우 운동·음성·화면·저장 | **NOT_RUN** |

JVM 결과는 `build/sidebend-fix-verification/test-results/testDebugUnitTest/`의 실제 XML로 확인한 범위입니다. 이전 EXP04 Golden PASS는 이번 UI/실사용 회귀의 PASS로 재사용하지 않았습니다.

별도 실행 근거는 ignored `local_validation/side_bend_fix/`에 보존했습니다.

- `red-classifier-01.log` / `.json` / `.xml`: 수정 전 실제 8개/4 failure 원본 기록.
- `node-guards-01.log` / `.json`: 165개/0 failure, exit 0.
- `serializer-contracts-01.log` / `.json`: 이번 JVM이 생성한 fixture 19개 검증, exit 0.
- `green-build-lint-01.log` / `.json`: 전체 JVM·assemble·lint exit 0, 무출력 3분 중단 조건 발생 없음.

원본 XML에는 개발 PC 환경 메타데이터가 있으므로 전체 원문을 공개 문서로 복사하지 않았습니다.

Windows Gradle 복구 가이드에 따라 기존 출력의 잠금/덮어쓰기를 피하려고 지원되는 `verificationBuildDir`의 별도 폴더 `build/sidebend-fix-verification`을 사용했습니다. 이미 있는 Gradle 캐시를 오프라인으로 재사용해 D:에 중복 설치/다운로드하지 않았고, 기존 출력 clean이나 IDE 강제 종료도 하지 않았습니다.

### 최신 시연용 APK

```text
build/deliverables/AIRPTCoach-7Exercises-SideBendFix-debug.apk
```

- 원본: `build/sidebend-fix-verification/outputs/apk/debug/app-debug.apk`
- 위 경로는 프로젝트 루트 기준입니다. 공개 문서에 개발 PC의 사용자 이름/절대 경로를 추가하지 않습니다.
- applicationId: `com.example.arptapp`, DEBUG, versionCode 1 / versionName 1.0.
- 크기: **36,779,058 bytes**.
- SHA256: `1d7429617cb618f5bd1f099aa59746b9ab543573a516455891ae4abfa877405f`.
- 새 이름으로 복사하고 원본/복사본 해시 일치를 확인했습니다. 기존 deliverable APK 1개도 전후 해시 동일합니다.
- `YOLO_PRIVATE_BUNDLED=false`, 기본 EXP02 Shadow 선택 유지. 7종 landmark 분류와 이번 수정은 모델 APK 포함/YOLO Primary 전환 없이 동작하는 경로입니다.

## 6. 보안·비용·모델 경계

- 기본 Landmark Primary, 기존 YOLO Shadow 분리, EXP04 contract **NOT_VERIFIED**를 유지합니다. YOLO를 Primary로 전환하거나 model prediction으로 `sessionExerciseType`을 정하지 않습니다.
- 모델 파일·기존 EXP02/EXP03/EXP04 Golden 증거와 기존 별도 산출물을 삭제하거나 옮기는 작업은 하지 않았습니다.
- 새로운 SDK/의존성·서버/API·LLM·과금 기능·권한·원격 migration/배포/billing 변경을 추가하지 않았습니다. 이번 수정으로 새 호출 비용 경로를 만들지 않았다는 범위이며, 기존 서비스 요금제 전체가 무료라는 보장은 아닙니다.
- 실제 사용자 사진·영상·관절 좌표·계정·토큰을 추가 저장/전송하지 않습니다. 회귀 fixture는 합성 scalar/landmark이며 실제 사람 데이터나 기기 증거가 아닙니다.
- 최종 재검사에서 기존 보호 대상 31개 파일의 SHA256이 모두 일치했습니다. 모델/Golden 입력/venv/검증 로그/APK의 ignore 상태를 확인했으며, 검사 대상 private 입력의 Git tracked 파일은 0개입니다.
- 변경된 공개 파일 14개 범위의 보안 검토에서 새 비밀정보·네트워크·과금·권한 경로는 발견하지 못했습니다. 전체 저장소나 원격 서비스의 보안을 보증하는 결과는 아닙니다.
- 전달용 APK도 public packaging guard를 통과했고 원본 빌드와 SHA256이 일치합니다. 새 formatter/세션 교정 메서드가 실제 APK DEX에 포함된 것까지 확인했습니다. 로컬 근거는 `local_validation/side_bend_fix/final_verification_01.json`에 보존합니다.
- 자동 commit/push/PR/merge는 하지 않았습니다. 기존 사용자 변경은 유지합니다.

## 7. 변경 파일

Production:

- `app/src/main/java/com/example/arptapp/domain/classifier/AdditionalExerciseClassifier.kt`
- `app/src/main/java/com/example/arptapp/domain/classifier/ExerciseClassifier.kt`
- `app/src/main/java/com/example/arptapp/domain/session/SessionExerciseLock.kt`
- `app/src/main/java/com/example/arptapp/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/example/arptapp/ui/dashboard/WorkoutExerciseUiFormatter.kt` — 신규 순수 표시 API.
- `app/src/main/java/com/example/arptapp/DashboardActivity.kt`

검증·문서:

- `app/src/test/java/com/example/arptapp/domain/classifier/SideBendClassifierRegressionTest.kt`
- `app/src/test/java/com/example/arptapp/domain/session/SessionExerciseLockTest.kt`
- `app/src/test/java/com/example/arptapp/domain/counter/SevenExerciseCounterTest.kt`
- `app/src/test/java/com/example/arptapp/domain/report/SevenExercisePipelineTest.kt`
- `app/src/test/java/com/example/arptapp/viewmodel/LockedSessionTest.kt`
- `app/src/test/java/com/example/arptapp/ui/dashboard/SideBendSessionRoutingTest.kt`
- `scripts/side-bend-routing.test.mjs`
- `docs/SIDE_BEND_DEVICE_BUGFIX.md` — 이 문서.

## 8. 다음 실제 휴대폰 확인

빌드/산출물 검증이 끝난 수정 APK를 명시적으로 확인한 뒤, 운동·기록 저장 중이 아닐 때 별도 승인된 설치/검증으로 진행합니다. 기존 앱 데이터 삭제로 문제를 우회하지 않습니다.

1. 새 APK 설치 후 운동 준비 화면에서 **촬영 방향 FRONT(정면)**을 선택합니다. 물리적 전면/후면 렌즈 선택과 촬영 방향 선택은 다릅니다.
2. 양쪽 다리·골반·어깨와 내려가는 팔이 보이도록 중립 자세를 충분히 유지합니다. 종목이 아직 미확정이면 무리하지 않는 측굴로 인식을 확인하고, SIDE_BEND가 확인된 뒤 **중립으로 다시 돌아와** 카운터를 준비합니다. 인식 전/교정 중의 미완성 회차가 소급 합산된다고 가정하지 않습니다.
3. 오른쪽 side bend **5회**를 천천히 수행합니다. 옆으로 기울였다 중앙으로 복귀해야 1회이며, 화면을 보며 급하게 기준 각도를 맞추려 하지 않습니다.
4. 왼쪽 side bend **5회**를 같은 방식으로 수행합니다. 반대 방향 이동이나 가림을 한 회로 이어 붙이지 않는지도 확인합니다.
5. `세션: 덤벨 사이드 벤드 · 감지: 덤벨 사이드 벤드`, 제목·횟수 단위가 안정적으로 일치하는지 확인합니다. 초기 교정이 발생했다면 중립부터 다시 시작합니다.
6. 좌/우 **count**를 확인합니다. 0회 초기 교정은 세션당 1회만 가능하며, 완료 1회 이후에는 다른 감지가 나와도 잠긴 종목/기록이 바뀌지 않아야 합니다.
7. **TTS/안내문**을 확인합니다. 스쿼트 무릎 코칭이 남지 않고 측굴 측정/횟수 기반 안내가 나와야 합니다.
8. 종료·저장 후 **Report/History**에서 종목·좌우·측굴 metric·횟수·안내를 확인합니다.

이 절차를 수행하기 전에는 “실기기 수정 확인 완료”로 보고하지 않습니다. 별도 EXP04 카메라 7종 Shadow 검증도 이번 범위의 자동 테스트로 대체하지 않습니다.

커밋 제안: `fix: 사이드 벤드 분류와 0회 세션 교정 및 화면 일관성 개선`
