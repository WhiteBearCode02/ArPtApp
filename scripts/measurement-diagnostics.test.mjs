import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
const root = 'app/src/main/java/com/example/arptapp/'
const read = p => readFileSync(root + p, 'utf8')
const dashboard = read('DashboardActivity.kt')

test('camera dropout uses the counter verdict for analysis expiry, never immediate reset on invalid', () => {
  const process = dashboard.slice(dashboard.indexOf('private fun processLandmarks('), dashboard.indexOf('private fun renderMeasurementDiagnostic('))
  assert.match(process, /if \(counterFrame.pendingReset\) clearPendingAnalysisFrames\(\)/)
  assert.match(process, /if \(!counterFrame.evaluable\) \{\s*return/)
  assert.doesNotMatch(process, /invalidatePendingAnalysis\(\)/)
  assert.match(process, /counterFrame.completions.forEach/)
  assert.ok(process.indexOf('if (!counterFrame.evaluable)') < process.indexOf('repAnalysisEngine.observe'))
  assert.match(dashboard, /processFrame\(sessionExerciseType.name, null, frame.timestampMs\)/)
  assert.match(read('domain/counter/MeasurementContinuity.kt'), /ExerciseMotionPolicy.MAX_FRAME_GAP_MS/)
})

test('measurement diagnostics are DEBUG-only, bounded to 2 Hz and contain no raw coordinates', () => {
  const render = dashboard.slice(dashboard.indexOf('private fun renderMeasurementDiagnostic('), dashboard.indexOf('private fun List<NormalizedLandmark>.toPoseData('))
  assert.ok(render.indexOf('if (!BuildConfig.DEBUG) return') < render.indexOf('MeasurementDiagnosticSnapshot('))
  assert.match(render, /now - lastMeasurementDiagnosticAt < 500L/)
  assert.match(render, /Log.d\("RepTrace", text\)/)
  assert.doesNotMatch(render, /Log\.[diwe]\([^\n]*(points|landmarks)/)
  const panel = read('ui/dashboard/YoloDiagnosticsPanel.kt')
  assert.match(panel, /fun updateMeasurement\(text: String\) \{\s*if \(!BuildConfig.DEBUG\) return/)
  for (const path of ['domain/counter/MeasurementContinuity.kt', 'presentation/diagnostics/MeasurementDiagnosticFormatter.kt']) {
    assert.doesNotMatch(read(path), /HttpClient|Retrofit|https?:\/\/|API_KEY|FileOutputStream|writeBytes|Supabase/)
  }
})
