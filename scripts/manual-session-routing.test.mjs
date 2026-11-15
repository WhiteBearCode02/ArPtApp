import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const root = 'app/src/main/java/com/example/arptapp/'
const read = path => readFileSync(root + path, 'utf8')
const dashboard = read('DashboardActivity.kt')
const section = (start, end) => dashboard.slice(dashboard.indexOf(start), dashboard.indexOf(end))

test('start CTA only opens picker; cancelling cannot start or reset a session', () => {
  const buttons = section('// 운동 시작 버튼', '// 운동 종료 버튼')
  assert.match(buttons, /showExerciseSelection\(\)/)
  assert.doesNotMatch(buttons, /startExercise\(|beginSession|resetSession/)
  const picker = section('private fun showExerciseSelection(', '/** The user\'s supported selection')
  assert.match(picker, /exerciseChoiceDialog\?\.isShowing == true/)
  assert.match(picker, /WorkoutSelectionDialog.show\(this, selectedView\)/)
  assert.match(picker, /cameraView != selectedView/)
  assert.match(picker, /startExercise\(selectedExercise\)/)
  assert.doesNotMatch(picker, /\.resetSession\(|isExercising = true/)
})

test('selection registry and compatibility are validated without guessed classifier thresholds', () => {
  const picker = read('ui/dashboard/WorkoutSelectionDialog.kt')
  assert.match(picker, /ExerciseType.supported/)
  assert.match(picker, /displayName/)
  assert.doesNotMatch(picker, /ExerciseType.entries|ExerciseType.LUNGE|HttpClient|https?:\/\//)
  const start = section('private fun startExercise(', 'private fun endExercise(')
  assert.match(start, /ExerciseCameraViewPolicy.canStart\(selectedExercise, cameraView\)/)
  assert.ok(start.indexOf('ExerciseCameraViewPolicy.canStart') < start.indexOf('mainViewModel.resetSession()'))
  assert.match(start, /if \(!mainViewModel.beginSession\(selectedExercise\)\) return/)
  assert.doesNotMatch(start, /observeDetectedExercise|observeStableDetectedExercise/)
})

test('selected exercise owns startup speech, reference and immediate zero-count labels', () => {
  const start = section('private fun startExercise(', 'private fun endExercise(')
  for (const code of ['ExerciseSpeechFormatter.sessionStart(selectedExercise)',
    'referenceRepository.load(sessionOwnerId, selectedExercise.name, cameraView)',
    'repetitionCount = 0', 'binding.tvCount.text = "0"', 'resetSessionMeasurementState()']) assert.ok(start.includes(code), code)
  assert.doesNotMatch(start, /sessionStart\(detectedExerciseType\)|tvDashboardTitle.text = "운동 중"/)
  const projection = read('ui/dashboard/WorkoutExerciseUiFormatter.kt')
  const manual = projection.slice(projection.indexOf('fun formatManualSession('), projection.indexOf('/** Legacy'))
  assert.match(manual, /selected.displayName/)
  assert.doesNotMatch(manual, /detectedExerciseType|detected\.displayName|SQUAT|SIDE_BEND/)
})

test('measurement, squat-only assessment, counter and analyzer use the selected lock', () => {
  const process = section('private fun processLandmarks(', 'private fun List<NormalizedLandmark>.toPoseData(')
  assert.match(process, /measurePose\(sessionExerciseType.name,/)
  assert.match(process, /sessionExerciseType == ExerciseType.SQUAT && measurement.isEvaluable/)
  assert.match(process, /val exerciseType = sessionExerciseType.name/)
  assert.match(process, /exerciseCounter.processMeasurement\(exerciseType,/)
  assert.match(process, /RepFrameFeatureExtractor.extract\(\s*poseData.landmarks, exerciseType,/)
  assert.match(process, /mainViewModel.addCompletedRep/)
  assert.doesNotMatch(process, /measurePose\(detected|processMeasurement\(detected|val exerciseType = detected|yoloShadowSnapshot/)
  assert.match(dashboard, /exerciseType = ExerciseType.display\(report.exerciseType\)/)
})

test('neither detector has live lock admission and model evidence remains unverified', () => {
  assert.doesNotMatch(dashboard, /mainViewModel.observe(?:Stable)?DetectedExercise\(/)
  assert.match(dashboard, /LandmarkExerciseDetector\(fallbackExerciseClassifier\)/)
  assert.match(read('domain/detection/Exp04YoloContract.kt'), /get\(\) = ExportContractVerification.NOT_VERIFIED/)
  const lock = read('domain/session/SessionExerciseLock.kt')
  assert.match(lock, /MANUAL_SELECTED\) return false/)
  assert.match(lock, /MANUAL_SELECTED\)\s*return SessionExerciseLockChange.NONE/)
})

test('new local routing/selection modules add no secrets, network, uploads or paid dependencies', () => {
  for (const path of ['domain/session/ExerciseCameraViewPolicy.kt', 'ui/dashboard/WorkoutSelectionDialog.kt',
    'ui/dashboard/WorkoutExerciseUiFormatter.kt']) {
    assert.doesNotMatch(read(path), /https?:\/\/|HttpClient|Retrofit|SUPABASE|LLM_API_KEY|accessToken|FileOutputStream|writeBytes/)
  }
  assert.match(read('ui/feedback/FeedbackManager.kt'), /fun resetSession\(\)\s*\{[\s\S]*?textToSpeech.stop\(\)/)
})
