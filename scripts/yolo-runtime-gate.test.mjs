import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const base = 'app/src/main/java/com/example/arptapp/'
const read = path => readFileSync(path, 'utf8')

test('contract verification and build variant rollout have independent code owners', () => {
  const gradle = read('app/build.gradle.kts')
  assert.match(gradle, /debug\s*\{[^}]*buildConfigField\("boolean", "YOLO_SHADOW_ENABLED", "true"\)/s)
  assert.match(gradle, /release\s*\{[^}]*buildConfigField\("boolean", "YOLO_SHADOW_ENABLED", "false"\)/s)
  const policy = read(base + 'domain/detection/YoloRuntimePolicy.kt')
  assert.match(policy, /class YoloRuntimePolicy private constructor/)
  assert.match(policy, /YoloRuntimePolicy\(debug && requested\)/)
  assert.doesNotMatch(policy, /data class|SharedPreferences|Intent\(|getenv|getProperty|Supabase|URL\(/)
})

test('both frame copying and native preprocessing have fail-closed gates', () => {
  const runner = read(base + 'domain/detection/YoloShadowRunner.kt')
  const detector = read(base + 'domain/detection/YoloExerciseDetector.kt')
  assert.ok(runner.indexOf('!latest.result.predictionAllowed') < runner.indexOf('copyFrame(frame)'))
  assert.ok(detector.indexOf('!runtimePolicy.predictionAllowed') < detector.indexOf('contract.preprocessor.preprocess(frame)'))
  assert.match(detector, /YoloRuntimePolicy.DISABLED/)
})

test('session gate state is local additive metadata and historical absence stays unknown', () => {
  const diagnostics = read(base + 'domain/detection/YoloSessionDiagnostics.kt')
  assert.match(diagnostics, /val shadowRuntimeEnabled: Boolean\? = null/)
  assert.match(diagnostics, /predictionEnabled = state.shadowEnabled && state.status/)
  assert.match(diagnostics, /!snapshot.result.predictionAllowed/)
  const presentation = read(base + 'presentation/report/YoloSessionDiagnosticsPresentation.kt')
  assert.match(presentation, /기록 없음 · 이전 버전/)
  assert.match(presentation, /LANDMARK · YOLO는 판정에 사용하지 않음/)
  const remote = read(base + 'data/remote/SupabaseRepository.kt')
  assert.doesNotMatch(remote, /YoloSessionDiagnostics|yoloDiagnostics|shadowRuntimeEnabled/)
})

test('rollout does not relax baseline thresholds or session isolation', () => {
  const policy = read(base + 'domain/detection/Exp02PostProcessor.kt')
  assert.match(policy, /APP_ENGINEERING_INITIAL_RAW_CONFIDENCE = \.10f/)
  assert.match(policy, /APP_ENGINEERING_INITIAL_STABLE_CONFIDENCE = \.60f/)
  assert.match(policy, /IOU_THRESHOLD = \.70f/)
  assert.match(read(base + 'domain/detection/YoloShadowRunner.kt'), /MIN_FRAME_INTERVAL_MS = 250L/)
  const native = read(base + 'ui/camera/yolo/TfliteYoloRuntime.kt')
  assert.doesNotMatch(native, /SessionExerciseLock|MainViewModel|ExerciseCounter|uploadSession|HttpURLConnection/)
})
