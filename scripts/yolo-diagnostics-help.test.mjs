import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const root = 'app/src/main/'
const java = root + 'java/com/example/arptapp/'
const read = file => readFileSync(file, 'utf8')

test('live diagnostics keep the bounded viewport and technical text initially hidden', () => {
  const xml = read(root + 'res/layout/view_yolo_diagnostics_panel.xml')
  assert.match(xml, /android:layout_height="144dp"/)
  assert.match(xml, /tvYoloDiagnostics[\s\S]*android:visibility="gone"/)
  for (const id of ['tvYoloStatus', 'tvYoloRecognition', 'tvYoloSpeed', 'btnYoloTechnical']) assert.ok(xml.includes(`@+id/${id}`))
  assert.doesNotMatch(xml, /importantForAccessibility="no"/)
  const panel = read(java + 'ui/dashboard/YoloDiagnosticsPanel.kt')
  assert.match(panel, /BuildConfig.DEBUG && expanded/)
  assert.match(panel, /visibility != View.VISIBLE/)
  assert.match(panel, /lastOverviewAt < 500L/)
})

test('live and saved screens share a locally collapsed help disclosure', () => {
  for (const file of ['view_yolo_diagnostics_panel.xml', 'activity_report.xml'])
    assert.match(read(root + 'res/layout/' + file), /@layout\/view_yolo_diagnostics_help/)
  assert.match(read(root + 'res/layout/view_yolo_diagnostics_help.xml'), /tvYoloGuide[\s\S]*visibility="gone"/)
  const binder = read(java + 'presentation/diagnostics/YoloDiagnosticsHelp.kt')
  assert.match(binder, /var expanded = false/)
  assert.match(binder, /ViewCompat.setStateDescription/)
})

test('help explains terms and separates confidence, frame counts and agreement from accuracy', () => {
  const source = read(root + 'res/values/yolo_diagnostics_help.xml')
  for (const label of ['raw', 'stable', 'fallback', 'session', 'confidence', 'frame', 'FPS', 'latency'])
    assert.ok(source.includes(`(${label})`), label)
  for (const claim of ['판단 보류', '자세 정확도가 90점이라는 뜻은 아닙니다', '운동 횟수가 아닙니다', '실제 정답과 비교한 정확도가 아닙니다'])
    assert.ok(source.includes(claim), claim)
  assert.match(source, /name="yolo_guide_text" formatted="false"/)
  assert.match(source, /name="android:minHeight">48dp/)
})

test('saved readable groups and lossless developer groups have separate disclosures', () => {
  assert.match(read(root + 'res/layout/activity_report.xml'), /yoloShadowTechnicalGroups[\s\S]*visibility="gone"/)
  const renderer = read(java + 'presentation/report/YoloSessionDiagnosticsRenderer.kt')
  assert.match(renderer, /var technicalExpanded = false/)
  assert.match(renderer, /technicalGroups\(diagnostics\)/)
  assert.match(renderer, /groups\(diagnostics\)/)
  assert.match(renderer, /visibleDiagnostics\(BuildConfig.DEBUG, json\)/)
})

test('explanations have no persistence, network, scoring or model gate writes', () => {
  for (const file of ['YoloDiagnosticGuide.kt', 'YoloLiveDiagnosticsPresentation.kt', 'YoloDiagnosticsHelp.kt'])
    assert.doesNotMatch(read(java + 'presentation/diagnostics/' + file), /HttpClient|Supabase|SharedPreferences|FileOutputStream|writeBytes|RepScoreCalculator|SessionExerciseLock/)
  assert.match(read(java + 'presentation/diagnostics/YoloLiveDiagnosticsPresentation.kt'), /result.predictionAllowed && result.timings != null/)
})

test('original developer log formatter stays independent from friendly presentation', () => {
  const source = read(java + 'DashboardActivity.kt')
  assert.match(source, /val text = DetectorComparisonFormatter.format\(comparison\)/)
  assert.match(source, /updateComparison\(comparison\)/)
  assert.match(source, /Log.d\("YoloShadow", text\)/)
})
