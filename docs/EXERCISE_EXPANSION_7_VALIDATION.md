# 7종 운동 확장 · 변경 파일과 검증 기록

2026-11-12 · branch `ai-v2` · 자동 commit/push 없음

## 실행 결과

최종 자동 검증 결과는 아래 표에 기록한다. 실기기/원격 Supabase/실제 사람의 인식 정확도는 포함하지 않는다.

| 검사 | 결과 |
|---|---|
| Android JVM | 최종 개인 Debug: 76 suites / 823 통과 / 실패·오류·skip 0. 기본 Debug도 직전 816개 통과 후 측정 누락 fixture 7개를 추가함 |
| Node 화면/서버 검사 | 282 통과 / 실패 0 / skip 0 |
| Android serializer → 서버 V2 | 기본 7 + 이벤트 5 + 측정 누락 7, 총 19개 PASS |
| assembleDebug / lintDebug / assembleDebugAndroidTest | 모두 PASS (오프라인, 기본 모델 제외 빌드); Lint 오류·fatal 0 / 경고 300 |
| 일반 APK 모델 제외 / 개인 APK 원본 hash·고지 | 일반 APK 제외·v2 서명 PASS; 개인 APK 정확한 EXP02 bytes·라이선스 고지·v2 서명 PASS; 개인 APK의 공개 배포 차단도 PASS |
| 변경 텍스트 비밀 패턴 검사 | 변경 공개 텍스트 73개 PASS, 값 출력 없이 publishable key 분류; Git history 전체 스캔 아님 |

추가된 원격 mapper 테스트의 첫 실행은 테스트가 sessionId 확정 전에 toRecord를 호출하여 7개 실패했다. 실제 `forSaving → toRecord → upload` 순서로 fixture를 수정했다. 앱의 cloudEligible/owner 조건을 완화하지 않았다.

추가 메모리 DB 기기 테스트의 필수 repAnalyses 생성자 인수 누락을 수정했다. 메모리 여유 약 284MiB인 상태에서 동시 Gradle 빌드가 느려져, 3분 이상 무출력인 이 작업의 개인용 launcher만 식별·중단했다. 기존 IDE 프로세스나 사용자 파일은 종료/삭제하지 않았다. 이후 worker 1개·Gradle heap 1GiB·in-process Kotlin으로 순차 재검증한다.

일반 Debug 검증 시점 APK: `build/exercise-seven-verification/outputs/apk/debug/app-debug.apk` · 37,937,726 bytes · SHA256 `c7fb273b0bb93a315772d0d33b21bc11e6db3c81233a044d394c0655e85ca9a3`. 이후 buildDirectory 전환 시 Gradle의 이전 task-output 정리로 이 경로의 APK는 남아 있지 않다. 이를 현재 설치 파일로 안내하지 않는다.
패키지 `com.example.arptapp`, minSdk 26, targetSdk 35, ABI arm64-v8a/armeabi-v7a/x86_64. APK는 실제 기기 실행 검증과 별개다.

**최종 개인 설치 파일:** `build/deliverables/AIRPTCoach-7Exercises-EXP02-Personal-debug.apk` · 46,679,645 bytes · SHA256 `999898e557e232ac369b720ae2db16df9cbbdbb23849e9e99dff8e562e9537cc`.
기존 opt-in `privateExp02Apk=true`로 빌드했고 원본/전달 복사본 해시가 같다. EXP02 SHA256은 `464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749`로 유지된다. 모델 포함 개인용 APK이므로 Git commit·공개 배포 대상이 아니다. APK/fixture는 기존 build ignore 규칙으로 제외된다. 개인용 `assembleDebug`도 PASS했다. 2026-11-09의 이전 이름 APK와 혼동하지 말고 위 **7Exercises** 파일을 사용한다.

## 수정 파일 (44)

- `README.md`
- `app/src/main/java/com/example/arptapp/DashboardActivity.kt`
- `app/src/main/java/com/example/arptapp/data/StructuredReportCodec.kt`
- `app/src/main/java/com/example/arptapp/data/YoloSessionDiagnosticsCodec.kt`
- `app/src/main/java/com/example/arptapp/domain/analyzer/AnalyzerFactory.kt`
- `app/src/main/java/com/example/arptapp/domain/analyzer/PersonalMotionReference.kt`
- `app/src/main/java/com/example/arptapp/domain/analyzer/PoseAngleExtractor.kt`
- `app/src/main/java/com/example/arptapp/domain/classifier/ExerciseClassifier.kt`
- `app/src/main/java/com/example/arptapp/domain/classifier/ExerciseFeatures.kt`
- `app/src/main/java/com/example/arptapp/domain/classifier/ExerciseType.kt`
- `app/src/main/java/com/example/arptapp/domain/counter/ExerciseCounter.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/DetectorComparison.kt`
- `app/src/main/java/com/example/arptapp/domain/detection/YoloSessionDiagnostics.kt`
- `app/src/main/java/com/example/arptapp/domain/report/AiWorkoutReport.kt`
- `app/src/main/java/com/example/arptapp/domain/report/DirectionalClaimValidator.kt`
- `app/src/main/java/com/example/arptapp/domain/report/LocalEventPresentation.kt`
- `app/src/main/java/com/example/arptapp/domain/report/RepAnalysisEngine.kt`
- `app/src/main/java/com/example/arptapp/domain/report/RepDiagnosticFormatter.kt`
- `app/src/main/java/com/example/arptapp/domain/report/RepFrameFeatureExtractor.kt`
- `app/src/main/java/com/example/arptapp/domain/report/RepPhaseSegmenter.kt`
- `app/src/main/java/com/example/arptapp/domain/report/RepReportAnalyzer.kt`
- `app/src/main/java/com/example/arptapp/domain/report/RepScoreCalculator.kt`
- `app/src/main/java/com/example/arptapp/domain/report/StructuredAnalysis.kt`
- `app/src/main/java/com/example/arptapp/domain/session/SessionExerciseLock.kt`
- `app/src/main/java/com/example/arptapp/presentation/diagnostics/YoloDiagnosticGuide.kt`
- `app/src/main/java/com/example/arptapp/presentation/diagnostics/YoloLiveDiagnosticsPresentation.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/RepReportUiModel.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/ReportActivity.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/StructuredReportPresentation.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/YoloSessionDiagnosticsPresentation.kt`
- `app/src/main/java/com/example/arptapp/ui/dashboard/DashboardUiPolicy.kt`
- `app/src/main/java/com/example/arptapp/ui/dashboard/PersonalReferenceDialog.kt`
- `app/src/main/java/com/example/arptapp/ui/feedback/FeedbackManager.kt`
- `app/src/main/java/com/example/arptapp/viewmodel/MainViewModel.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/example/arptapp/ui/dashboard/DashboardUiPolicyTest.kt`
- `docs/BIOMECHANICS_REFERENCES.md`
- `docs/LLM_INTEGRATION.md`
- `docs/POSE_STABILITY.md`
- `scripts/dashboard-ui.test.mjs`
- `scripts/pose-stability.test.mjs`
- `supabase/functions/generate-workout-report/contracts.ts`
- `supabase/functions/generate-workout-report/event-claims.ts`
- `supabase/functions/generate-workout-report/prompt.ts`

## 신규 파일 (29)

- `app/src/androidTest/java/com/example/arptapp/data/SevenExerciseHistoryDeviceTest.kt`
- `app/src/main/java/com/example/arptapp/domain/analyzer/ExerciseMeasurementStrategy.kt`
- `app/src/main/java/com/example/arptapp/domain/analyzer/ExerciseMotionPolicy.kt`
- `app/src/main/java/com/example/arptapp/domain/classifier/AdditionalExerciseClassifier.kt`
- `app/src/main/java/com/example/arptapp/domain/counter/ExpandedExerciseCounter.kt`
- `app/src/main/java/com/example/arptapp/domain/report/ExerciseMetricPolicy.kt`
- `app/src/main/java/com/example/arptapp/domain/report/ExerciseNameClaims.kt`
- `app/src/main/java/com/example/arptapp/domain/report/ExerciseReportLanguage.kt`
- `app/src/main/java/com/example/arptapp/domain/report/ExpandedRepAnalysisEngine.kt`
- `app/src/main/java/com/example/arptapp/domain/report/ExpandedRepScoreCalculator.kt`
- `app/src/main/java/com/example/arptapp/domain/report/MetricClaimValidator.kt`
- `app/src/main/java/com/example/arptapp/presentation/report/ExpandedRepMetricGroups.kt`
- `app/src/test/java/com/example/arptapp/domain/analyzer/ExpandedMeasurementTest.kt`
- `app/src/test/java/com/example/arptapp/domain/classifier/SevenExerciseClassifierTest.kt`
- `app/src/test/java/com/example/arptapp/domain/counter/SevenExerciseCounterTest.kt`
- `app/src/test/java/com/example/arptapp/domain/detection/ExpandedYoloApplicabilityTest.kt`
- `app/src/test/java/com/example/arptapp/domain/report/ExpandedAiReportValidatorTest.kt`
- `app/src/test/java/com/example/arptapp/domain/report/ExpandedObservationTest.kt`
- `app/src/test/java/com/example/arptapp/domain/report/SevenExercisePipelineTest.kt`
- `docs/BIOMECHANICS_REFERENCES_7.md`
- `docs/EXERCISE_EXPANSION_7.md`
- `scripts/verify-seven-exercise-contracts.mjs`
- `supabase/functions/generate-workout-report/contract-values.ts`
- `supabase/functions/generate-workout-report/contracts-v2.ts`
- `supabase/functions/generate-workout-report/exercise-matrix.ts`
- `supabase/functions/generate-workout-report/exercise-v2-fixtures.ts`
- `supabase/functions/generate-workout-report/exercise-v2.test.ts`
- `supabase/functions/generate-workout-report/metric-claims.ts`
- `docs/EXERCISE_EXPANSION_7_VALIDATION.md`

## 미수행·안전 경계

- 기기 설치/앱 종료/모델 provisioning/native inference/사용자 운동 테스트를 수행하지 않음.
- `SevenExerciseHistoryDeviceTest`는 사용자 DB가 아닌 격리된 메모리 DB 전용이다. 테스트 APK 빌드와 실제 기기 테스트 실행은 구분한다.
- 원격 SQL 적용/Edge Function 배포/LLM 연결/모델 재학습/데이터셋 다운로드 없음.
- 기존 EXP02 hash와 2-class 매핑, 공개 APK 제외/개인 opt-in 정책 유지.
- 신규 threshold/점수는 ENGINEERING_INITIAL이며 임상/전문가 검증 결과가 아님.
- 새 유료 API는 추가하지 않음. 기존 Supabase 동기화의 요금제/할당량 비용 가능성은 남아 있음.
- Git history 전체 비밀 스캔/법률 의견/라이선스의 법적 적합성 보증은 이번 검증 범위가 아님.

## 권장 커밋 내용

```text
feat: 7종 운동의 관절 기반 회차 분석·리포트 파이프라인 확장

- 신규 5종 측정 전략과 좌우 독립 카운터·회차 관찰·음성/리포트 연결
- V2 분석 계약 및 V1 기록 호환, 기존 Room/Supabase 저장 경로 검증
- EXP02 2-class 유지 및 신규 운동의 YOLO 비교 제외
- 논문 근거·공학 기준 구분과 자동/실기기 검증 경계 문서화
```

