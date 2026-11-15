import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const base = 'app/src/main/java/com/example/arptapp/'
const read = file => readFileSync(base + file, 'utf8')
const dashboard = read('DashboardActivity.kt')

test('live admission uses landmark stable/candidate and both completed-count owners', () => {
  const update = dashboard.slice(dashboard.indexOf('private fun updateCurrentExerciseType('),
    dashboard.indexOf('private fun updateDetectedExerciseUi('))
  assert.match(update, /observeStableDetectedExercise\(\s*nextExerciseType, fallbackExerciseClassifier.candidateExercise,/)
  assert.match(update, /maxOf\(repetitionCount, exerciseCounter.getRepCount\(\)\)/)
  assert.match(update, /if \(change == SessionExerciseLockChange.CORRECTED\) discardUnconfirmedExerciseState\(\)/)
  assert.doesNotMatch(update, /yoloShadowSnapshot|\.resetSession\(|\.clear\(/)
})

test('only admitted zero-rep correction discards old-type pending feedback and analysis', () => {
  assert.equal((dashboard.match(/discardUnconfirmedExerciseState\(\)/g) ?? []).length, 2,
    'one declaration and one CORRECTED-only call')
  const discard = dashboard.slice(dashboard.indexOf('private fun discardUnconfirmedExerciseState('),
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
  assert.match(dashboard, /WorkoutExerciseUiFormatter.format\(isExercising, sessionExerciseType, exerciseType\)/)
  const formatter = read('ui/dashboard/WorkoutExerciseUiFormatter.kt')
  assert.match(formatter, /session.displayName/)
  assert.doesNotMatch(formatter, /resetSession|observeStable|Supabase|SharedPreferences|FileOutputStream|Yolo/)
})

test('side-bend fix keeps YOLO observational and EXP04 unverified', () => {
  assert.match(dashboard, /primaryExerciseDetector = LandmarkExerciseDetector\(fallbackExerciseClassifier\)/)
  assert.match(read('domain/detection/Exp04YoloContract.kt'), /get\(\) = ExportContractVerification.NOT_VERIFIED/)
  assert.doesNotMatch(dashboard, /observeStableDetectedExercise\([^)]*yolo/i)
})
