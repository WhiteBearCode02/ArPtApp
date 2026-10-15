# MediaPipe fallback · 세션 고정 · 회차 점수

기준일: 2026-10-15. YOLO 연결 전 실시간 파이프라인 안정화 작업입니다.
`ENGINEERING_INITIAL`은 공학적 초기 비교 정책이며 의료 판정·안전 보증·정답 자세 인증이 아닙니다.

## 1. 렌즈와 촬영 방향

- `CameraLensPolicy`: 후면 우선, 후면이 없으면 전면, 둘 다 없으면 연결 실패 안내. 카메라 전환은 두 렌즈가 있을 때만 활성화합니다.
- `PoseCameraController`: CameraX `hasCamera` 확인 후 바인딩 성공 시 실제 렌즈를 콜백합니다. 세로·가로 UI와 mirror는 실제 바인딩 결과를 사용합니다.
- 물리 렌즈 FRONT/BACK과 신체 촬영 방향 `CameraView.FRONT/SIDE/UNKNOWN`은 별개입니다. `isFrontFacing`을 신체 방향 분기에 쓰지 않습니다.
- 운동/개인 기준 기록 중 렌즈 전환 금지, 권한 거부·카메라 없음 안내, 프레임 세대별 늦은 콜백 폐기는 유지합니다.

## 2. 보수적인 fallback 분류

`ExerciseFeatureExtractor`는 측정 가능한 scalar feature만 `ExerciseClassifier`에 전달합니다.

- FRONT: 양측 다리 굴곡·고관절 가시성 또는 양팔 팔꿈치·어깨 위 손목을 함께 확인합니다. 기존 normalized depth evidence를 유지하되 임상적인 3D 관절각이라고 주장하지 않습니다.
- SIDE: 필요한 관절들의 최소 visibility가 높은 쪽을 선택합니다. 다리/팔은 각각 선택하며, 가려진 반대쪽 값을 추정·복제하지 않습니다. 비대칭으로 LUNGE를 판정하지 않습니다.
- UNKNOWN 방향·낮은 visibility·비정상 좌표는 판정을 유보합니다.
- 최초 지원 종목은 5연속 프레임, 다른 지원 종목으로 안정 감지 변경은 10연속 프레임이 필요합니다. UNKNOWN 15연속 프레임이면 안정 감지를 해제합니다.
- LUNGE는 미지원 후보로만 유지하며 안정 확정하지 않습니다. 순간 후보와 안정 감지는 다릅니다.

단일 프레임 자세 휴리스틱을 연속 적용하는 방식이며, 학습된 시간적 운동 분류기가 아닙니다. 측면 한쪽 굴곡만으로 실제 스쿼트/런지를 완전히 구분할 수 없고 정면 관절각의 투영 오차도 남습니다. 실기기 오탐 평가는 별도로 필요합니다.

## 3. 세션 상태 계약

`candidateExercise` → `stableDetectedExercise` → `SessionExerciseLock.sessionExerciseType`을 구분합니다.
운동 시작 시 세션을 초기화하고, 처음 안정 확정된 SQUAT 또는 SHOULDER_PRESS를 종료까지 고정합니다.

- 일시적인 LUNGE/UNKNOWN/다른 지원 종목 감지는 UI 감지 상태만 바꿉니다.
- 카운터·관절 측정·오류 분석·구조화 엔진·기준 동작 로드·리포트·기존 저장/업로드 종목은 고정된 세션 종목만 사용합니다.
- 가림은 **진행 중인 미완성 회차만** 폐기하며, 이미 완료된 횟수·기록·점수는 유지합니다.
- 종목 미확정/미지원 후보만 있음/완료 횟수 없음/측정 가능한 점수 없음이면 안내만 표시합니다. 숫자 0점 리포트 생성·이동·저장·업로드를 하지 않습니다.
- 일부 회차만 측정 불가이면 횟수는 유지하되 해당 점수를 평균에서 제외하고 상세에 `측정 불가`로 표시합니다. 차트의 실제 회차 번호도 유지합니다.

기존 종목 변경으로 진행 회차를 초기화하는 위험 경로를 제거했습니다. 고정 후 실제로 다른 운동으로 바꾸는 경우도 자동 종목 전환하지 않으므로 **종료 후 새 세션을 시작**해야 합니다.

## 4. 구조화 분석 후 로컬 점수

순서: 관절 측정 → 기존 카운터 → `RepAnalysisEngine.complete` → `MainViewModel.addCompletedRep` → `RepScoreCalculator` → 리포트/기존 저장 경로.
기존 90°/160° 카운터와 3프레임·최소 시간 정책은 변경하지 않았습니다.

정책은 `RepScorePolicy`에 상수로 모아 두었습니다.

| 구성 | 기본 가중치 | 측정 근거/비율 |
|---|---:|---|
| 동작 범위 | 30 | 스쿼트: 측정된 최소 무릎 내각 평균 `(180-angle)/90`; 프레스: 최대 팔꿈치 내각 평균 `(angle-70)/100` |
| 좌우 균형 | 25 | FRONT의 동시 좌우 무릎/팔꿈치 각도 차이, `1-difference/40` |
| 몸통 안정성 | 20 | SQUAT SIDE의 최대 몸통 기울기, `1-max(lean-10,0)/40`; 현재 프레스/정면은 미평가 |
| 관찰된 단계 | 15 | phaseModelVersion≥2·비부분 캡처·양의 상승 시간, 스쿼트는 하강 시간도 필요 |
| 관찰 오류 | 10 | 심각도·관찰 프레임 비율 기반 제한 감점 |

비율은 0..1로 제한합니다. 미측정 항목은 `earned=null`로 제외하고 `획득점수/측정가능가중치*100`으로 재정규화합니다. 최소 3프레임과 동작 범위 근거가 없으면 최종 점수도 null입니다. 관절 흔들림 대신 **휴대폰 가속도(swayX)로 자세를 감점하지 않습니다**.

예: 측면 스쿼트에서 ROM 30/30, 좌우 균형 미평가, 몸통 20/20, 단계 15/15, 오류 10/10이면 `75/75×100=100`입니다. 100은 현재 관찰 가능한 초기 항목의 비율일 뿐 완벽한 자세라는 뜻이 아닙니다. 촬영 방향·가시성마다 평가 항목이 달라 점수를 직접 비교하는 데 한계가 있습니다.

이벤트 감점(재정규화 전 가중치 점수 기준):

- severity 1..3, 관찰 ≥3프레임인 이벤트만 사용합니다.
- 같은 issue/side/phase 중복은 최대 프레임 수만 사용하고, issue별 합산 프레임은 해당 회차 관찰 프레임을 넘지 않습니다.
- issue 감점 = `severity/3 × min(1, 2×occurrenceFrames/observedFrames) × 3`.
- issue별 최대 3점, 오류 구성 전체 최대 10점입니다. 여러 side/phase·중복 이벤트로 무제한 차감하지 않습니다.
- 전체 오류 예산 초과 시 enum 순서의 결정적 배분을 쓰며 상세 내역 합계와 실제 차감액은 같습니다.

숄더프레스는 개인 기준이 없어도 관절·단계 근거로 점수를 계산합니다. 상단에서 카운트하므로 이후 복귀 시간이 null이어도 단계 감점하지 않습니다.
DTW는 별도 `referenceSimilarity`입니다. 새 종합 점수에는 **전혀 반영하지 않습니다**. 측면의 가려진 반대쪽을 채워 DTW를 강제하지 않으므로 유사도는 없을 수 있습니다.

## 5. 저장·전송 호환성과 개인정보

- Room entity/schema/version, DB migration은 변경하지 않았습니다.
- 기존 `scoresJson` envelope에 optional `localScore`/`referenceSimilarity`만 추가합니다. 과거 flat 배열·기존 상세 JSON·기존 점수는 계속 읽으며 재계산하지 않습니다.
- 새 live 기록은 localScore가 있으나 점수가 null이면 구버전 DTW/각도 fallback으로 점수를 만들지 않습니다. 구버전 API/기록의 계산 방식은 유지합니다.
- 기존 Supabase 업로드 필드/SQL은 유지합니다. 평균·회차 점수에는 새 로컬 점수를 쓰되, 기존 `pose_score` 보조 필드는 기준 유사도의 역할만 유지합니다.
- 현재 비활성인 LLM 경계에는 `forNetwork`에서 새 로컬 메타데이터를 제거합니다. default null은 `EncodeDefault.NEVER`로 직렬화하지 않아 기존 strict schema를 확장하지 않습니다.
- 새 DEBUG 진단은 종목 enum, 촬영 방향, 회차, 각도·시간·점수 구성·오류 enum 등의 scalar만 허용합니다. 사용자 식별자/이메일/토큰/키/원본 좌표·영상은 추가하지 않습니다. 실제 호출은 `BuildConfig.DEBUG` 안에 있습니다.
- LLM false/Unavailable, 기존 권리 미확인 YOLO 패키징 차단, 백업 금지 등 기존 보안 경계는 유지합니다. 신규 네트워크 서비스·유료 SDK·provider·자동 배포는 추가하지 않았습니다.

## 6. 검증

최종 로컬 실행 결과는 아래에 기록합니다. 실기기 정확도·카메라 UI 검증과 구분합니다.

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user-home'
$env:ANDROID_USER_HOME = Join-Path $env:GRADLE_USER_HOME 'android'
.\gradlew.bat assembleDebug testDebugUnitTest --offline --no-daemon --no-problems-report -PverificationBuildDir=pose-stability-verification --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8'
node --experimental-strip-types --test scripts/*.test.mjs supabase/functions/generate-workout-report/*.test.ts supabase/functions/youtube-ranking/*.test.ts
node scripts/verify-coaching-security.mjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-public-apk.ps1 -ApkPath build/pose-stability-verification/outputs/apk/debug/app-debug.apk
git diff --check
```

새 JVM 테스트는 렌즈 정책, FRONT/SIDE 분류·가림, 실제 MediaPipe 컨테이너 어댑터, 양측 visible side 선택, 종목 오인식 시 카운터/엔진/리포트 고정, 미평가 리포트 차단, 점수 차이·상한·개인 기준 없음·이전 JSON·strict wire를 검증합니다. 입력은 합성 관절/스칼라이며 카메라 실측은 아닙니다.

최종 실행 결과:

- `assembleDebug testDebugUnitTest`: **BUILD SUCCESSFUL**, 8분 6초. 진행 출력이 이어졌으며 3분 이상 무응답인 명령은 없었습니다.
- Android JVM: **39 suites / 265 tests / 실패·오류·skip 0**. 기존 213개에 새 52개를 추가했습니다. 기존 분류 테스트 1개는 미지원 LUNGE 안정 확정 금지 계약에 맞게 수정했습니다.
- Node 서버·보안·배선 회귀: **56 tests / 실패 0**. 기존 50개 및 이번 static wiring 검사 6개이며, 실제 외부 서비스 요청/배포 검증이 아닙니다.
- 최초 빌드에서는 가로 레이아웃의 `tvCameraLens` 누락으로 nullable binding 컴파일 오류가 발생했습니다. 가로 레이아웃도 맞춘 후 전체 빌드·테스트를 통과했습니다.
- APK 검사: loose YOLO/권리 미확인 weight 없음, 필수 `pose_landmarker_lite.task` 유지. 생성 APK는 Git ignore 적용됨을 확인했습니다.
- 변경된 공개 텍스트 32개 한정 비밀정보 패턴 검사 및 `git diff --check` 통과. 전체 Git history/장시간 라이선스 스캔은 하지 않았습니다.
- Windows 실행 정책으로 `.ps1` 직접 실행이 차단되어 위 명령처럼 **해당 읽기 전용 검사 프로세스에만** 실행 정책을 지정했습니다. 시스템 실행 정책은 변경하지 않았습니다.
- Room entity/schema, 의존성, Supabase/Edge Function 소스·원격 상태는 변경하지 않았습니다. 실기기 설치·촬영 정확도·UI E2E는 **미실행**입니다.
- APK SHA-256: `81C6FF8572203CB5FA61AC0E0CFE9C258465BE218EA60DF54C6369BF88A4D0D5`.

TensorFlow 의존성의 중복 namespace, native symbol strip 불가, Gradle 10 대비 deprecation 경고는 남아 있습니다. 이번 빌드의 실패 원인은 아니며 의존성 업그레이드는 범위에 넣지 않았습니다.

실기기 절차는 [DEVICE_VALIDATION.md](DEVICE_VALIDATION.md)를 따릅니다. 이번 변경의 APK 경로는 `build/pose-stability-verification/outputs/apk/debug/app-debug.apk`입니다. 다른 검증 폴더의 APK는 자동 갱신되지 않습니다.

### 변경 파일 범주

- Camera: `CameraLensPolicy.kt`, `PoseCameraController.kt`, `AndroidManifest.xml`, `activity_dashboard.xml`(세로·가로), `strings_pose_stability.xml`.
- Classifier/angle: `ExerciseClassifier.kt`, `ExerciseFeatures.kt`, `PoseAngleExtractor.kt`, `FormErrorAnalyzer.kt`, `RepFrameFeatureExtractor.kt`.
- Session: `SessionExerciseLock.kt`, `DashboardActivity.kt`, `MainViewModel.kt`.
- Score/report: `RepScoreCalculator.kt`, `StructuredAnalysis.kt`, `RepAnalysisEngine.kt`, `RepReportAnalyzer.kt`, `RepDiagnosticFormatter.kt`, `DTWCaluclator.kt`, `StructuredReportPresentation.kt`, `ReportActivity.kt`.
- Tests: 기존 `ExerciseClassifierTest.kt` 수정 및 `CameraLensPolicyTest.kt`, `CameraViewClassifierTest.kt`, `VisibleSideAnalysisTest.kt`, `LockedSessionTest.kt`, `RepScoreCalculatorTest.kt`, `scripts/pose-stability.test.mjs` 추가.
- Docs: `README.md`, `docs/DEVICE_VALIDATION.md`, 이 문서.

### 남은 일

전면/후면 기기 바인딩·mirror·가로 화면 실측, 실제 SIDE 오탐·가림 복구·첫 회차 누락 여부, 촬영 방향별 점수 분포·전문가 평가가 남습니다. 새 점수는 검증된 biomechanics scoring 모델이 아닙니다. YOLO 통합·학습 권리 검토·LLM 제공자 연결은 이번 범위가 아닙니다.
