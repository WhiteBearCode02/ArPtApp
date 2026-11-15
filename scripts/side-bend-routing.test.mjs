import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const base = 'app/src/main/java/com/example/arptapp/'
const read = file => readFileSync(base + file, 'utf8')
const dashboard = read('DashboardActivity.kt')

test('manual live dashboard keeps landmark classification diagnostic-only', () => {
  const update = dashboard.slice(dashboard.indexOf('private fun updateCurrentExerciseType('),
    dashboard.indexOf('private fun updateDetectedExerciseUi('))
  assert.match(update, /detectedExerciseType = nextExerciseType/)
  assert.doesNotMatch(update, /observeStableDetectedExercise|observeDetectedExercise|beginSession|resetSession|referenceRepository|exerciseCounter|repAnalysisEngine/)
  const lock = read('domain/session/SessionExerciseLock.kt')
  assert.match(lock, /MANUAL_SELECTED\)\s*return SessionExerciseLockChange.NONE/)
  assert.match(lock, /SessionExerciseLockChange.CORRECTED/,
    'isolated legacy automatic-session correction remains covered by JVM regression')
})

test('new selected session clears old-type pending feedback and analysis exactly once', () => {
  assert.equal((dashboard.match(/resetSessionMeasurementState\(\)/g) ?? []).length, 2,
    'one declaration and one selected-session start call, never a classifier callback')
  const start = dashboard.slice(dashboard.indexOf('private fun startExercise('), dashboard.indexOf('private fun endExercise('))
  assert.match(start, /resetSessionMeasurementState\(\)/)
  const discard = dashboard.slice(dashboard.indexOf('private fun resetSessionMeasurementState('),
    dashboard.indexOf('private fun checkCameraPermission('))
  for (const fragment of ['exerciseCounter.resetSession()', 'repAnalysisEngine.reset()',
    'currentRepAngles.clear()', 'currentRepErrorTags.clear()', 'currentMaxSwayX = 0f',
    'lastAccelerationX = null', 'repFeedbackUntilMs = 0L', 'repPriorityFeedback = ""',
    'feedbackManager.resetSession()', 'tts?.stop()', 'binding.overlayView.setFeedback']) {
    assert.ok(discard.includes(fragment), fragment)
  }
  assert.doesNotMatch(discard, /mainViewModel.resetSession|fallbackExerciseClassifier.reset|startTime\s*=|upload|delete|save\(/i)
})

test('dashboard labels share one pure session-derived projection', () => {
  assert.match(dashboard, /WorkoutExerciseUiFormatter.formatManualSession\(isExercising, sessionExerciseType\)/)
  const formatter = read('ui/dashboard/WorkoutExerciseUiFormatter.kt')
  assert.match(formatter, /session.displayName/)
  assert.doesNotMatch(formatter, /resetSession|observeStable|Supabase|SharedPreferences|FileOutputStream|Yolo/)
})

test('side-bend fix keeps YOLO observational and EXP04 unverified', () => {
  assert.match(dashboard, /primaryExerciseDetector = LandmarkExerciseDetector\(fallbackExerciseClassifier\)/)
  assert.match(read('domain/detection/Exp04YoloContract.kt'), /get\(\) = ExportContractVerification.NOT_VERIFIED/)
  assert.doesNotMatch(dashboard, /observeStableDetectedExercise\([^)]*yolo/i)
})
