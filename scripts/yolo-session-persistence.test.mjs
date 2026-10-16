import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

const base = "app/src/main/java/com/example/arptapp/"
const read = path => readFileSync(path, "utf8")

test("YOLO persistence is an additive nullable v6 column without destructive fallback", () => {
  const database = read(base + "data/AppDatabase.kt")
  assert.match(database, /version = 6/)
  assert.match(database, /addMigrations\(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6\)/)
  assert.match(database, /Migration\(5, 6\)/)
  assert.match(database, /ALTER TABLE exercise_records ADD COLUMN yoloDiagnosticsJson TEXT"/)
  assert.doesNotMatch(database, /fallbackToDestructiveMigration/)
  assert.match(read(base + "data/ExerciseRecord.kt"), /val yoloDiagnosticsJson: String\? = null/)
})

test("local report data normalizes the separate bounded diagnostics envelope and preserves old analyses", () => {
  const report = read(base + "presentation/report/WorkoutReportData.kt")
  assert.match(report, /yoloDiagnosticsJson: String\? = null/)
  assert.match(report, /YoloSessionDiagnosticsCodec\.decode\(yoloDiagnosticsJson\)/)
  assert.match(report, /\?\.let\(YoloSessionDiagnosticsCodec::encode\)/)
  assert.match(report, /yoloDiagnosticsJson = record\.yoloDiagnosticsJson/)
  assert.match(report, /scoresJson = analysesJson/)
  const navigation = read(base + "presentation/report/ReportNavigation.kt")
  assert.match(navigation, /private const val YOLO_DIAGNOSTICS_JSON = "YOLO_SESSION_DIAGNOSTICS_JSON"/)
  assert.match(navigation, /putExtra\(REP_ANALYSES_JSON, report\.analysesJson\)/)
  assert.match(navigation, /report\.yoloDiagnosticsJson\?\.let \{ putExtra\(YOLO_DIAGNOSTICS_JSON, it\) \}/)
})

test("local diagnostics never enter existing upload or AI DTOs", () => {
  for (const path of ["data/remote/SupabaseRepository.kt", "data/remote/AiCoachingRepository.kt",
    "domain/report/StructuredAnalysis.kt"]) {
    const source = read(base + path)
    assert.doesNotMatch(source, /yoloDiagnosticsJson|YoloSessionDiagnostics|YOLO_SESSION_DIAGNOSTICS_JSON/)
  }
})

test("saved history badge is small, DEBUG gated, optional and reset for recycled rows", () => {
  const row = read("app/src/main/res/layout/item_exercise_record.xml")
  assert.match(row, /android:id="@\+id\/tvYoloDiagnosticsStatus"[\s\S]*?android:textSize="12sp"[\s\S]*?android:visibility="gone"/)
  const adapter = read(base + "ExerciseAdapter.kt")
  assert.match(adapter, /visibleDiagnostics\(BuildConfig\.DEBUG, record\.yoloDiagnosticsJson\)/)
  assert.match(adapter, /View\.VISIBLE else View\.GONE/)
})

test("report diagnostics are a separate bottom section initially hidden and collapsed", () => {
  const layout = read("app/src/main/res/layout/activity_report.xml")
  const position = id => layout.indexOf(`android:id="@+id/${id}"`)
  assert.ok(position("improvementSummarySection") < position("yoloShadowSection"))
  assert.ok(position("yoloShadowSection") < position("btnClose"))
  for (const id of ["yoloShadowSection", "yoloShadowDetails"]) {
    const tag = layout.slice(position(id), layout.indexOf(">", position(id)))
    assert.match(tag, /android:visibility="gone"/)
  }
  const renderer = read(base + "presentation/report/YoloSessionDiagnosticsRenderer.kt")
  assert.match(renderer, /visibleDiagnostics\(BuildConfig\.DEBUG, json\) \?: return/)
  assert.match(renderer, /val state = YoloDiagnosticsExpansionState\(\)/)
  assert.match(renderer, /yoloShadowDetails\.visibility = if \(state\.expanded\) View\.VISIBLE else View\.GONE/)
  assert.match(renderer, /setOnClickListener \{ state\.toggle\(\); display\(\) \}/)
})

test("diagnostics explain comparison denominators and never calculate user scores", () => {
  const mapper = read(base + "presentation/report/YoloSessionDiagnosticsPresentation.kt")
  assert.match(mapper, /일치 \/ \(일치 \+ 불일치\) · UNKNOWN 제외/)
  assert.match(mapper, /UNKNOWN \/ \(일치 \+ 불일치 \+ UNKNOWN\)/)
  assert.match(mapper, /실제 detection이 있는 프레임만 포함/)
  assert.match(mapper, /실제 prediction 표본 없음/)
  assert.match(mapper, /기준 SHA256 · 실제 로드 증명 아님/)
  assert.doesNotMatch(mapper, /RepScoreCalculator|RepAnalysisEngine|\.insert\(|HttpClient|Supabase|LLM_API_KEY/)
  assert.match(read("app/src/main/res/values/yolo_report.xml"), /현재 운동 점수와 자세 분석 판정에는 사용되지 않습니다/)
})

test("real Room migration fixtures preserve legacy rows and test unsupported version fail-closed", () => {
  const tests = read("app/src/androidTest/java/com/example/arptapp/data/YoloDiagnosticsMigrationTest.kt")
  assert.match(tests, /verifyUpgrade\(3\)/)
  assert.match(tests, /verifyUpgrade\(4\)/)
  assert.match(tests, /verifyUpgrade\(5\)/)
  assert.match(tests, /Room\.databaseBuilder/)
  assert.match(tests, /database\.openHelper\.writableDatabase/)
  assert.match(tests, /assertNull\(before\.yoloDiagnosticsJson\)/)
  assert.match(tests, /unsupportedVersionFailsWithoutDeletingTheWorkoutRow/)
})
