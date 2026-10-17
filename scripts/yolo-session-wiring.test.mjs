import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

const base = "app/src/main/java/com/example/arptapp/"
const read = path => readFileSync(path, "utf8")

test("development model source exclusively uses private noBackup storage and preserves identity checks", () => {
  const source = read(base + "ui/camera/yolo/TfliteYoloRuntime.kt")
  assert.match(source, /check\(BuildConfig.DEBUG\)/)
  assert.match(source, /context.noBackupFilesDir/)
  assert.match(source, /canonicalFile/)
  assert.doesNotMatch(source, /assets\.open|openFd\(|\.download\(|HttpURLConnection|URL\(/)
  assert.ok(source.indexOf("YoloModelIdentity.validate(model)") < source.indexOf("Interpreter(model,"))
})

test("runner captures a scalar admission token and retains 250ms single-flight policy", () => {
  const source = read(base + "domain/detection/YoloShadowRunner.kt")
  assert.match(source, /MIN_FRAME_INTERVAL_MS = 250L/)
  assert.match(source, /val observation = try \{ observer.captureContext\(\)/)
  assert.match(source, /observer.onFrameSeen\(it\)/)
  assert.match(source, /observer.onAdmission\(it\)/)
  assert.match(source, /launch\(inferenceTimeoutMs, owned, generation, timestampMs, observation\)/)
  assert.match(source, /observer.onResult\(observation, latest, frame != null\)/)
  assert.match(source, /generation == epoch.get\(\)/)
})

test("session diagnostics freeze before report creation and never enter the upload call", () => {
  const source = read(base + "DashboardActivity.kt")
  const end = source.slice(source.indexOf("private fun endExercise()"), source.indexOf("private fun restorePreparationUi()"))
  assert.ok(end.indexOf("yoloSessionDiagnostics?.finish(") < end.indexOf("generateLockedSessionReport()"))
  const upload = end.slice(end.indexOf("supabaseRepository.uploadSession("), end.indexOf(".onFailure"))
  assert.doesNotMatch(upload, /yolo|diagnostic/i)
  assert.match(source, /yoloDiagnosticsJson = completedYoloDiagnosticsJson/)
  assert.match(source, /if \(BuildConfig.DEBUG\)\s*YoloSessionDiagnosticsBridge/)
  assert.match(source, /primaryExerciseDetector = LandmarkExerciseDetector\(fallbackExerciseClassifier\)/)
  assert.doesNotMatch(source, /updateCurrentExerciseType\([^\n]*yolo/i)
})

test("bridge cannot apply old-session results or retrofit a frame's expected exercise", () => {
  const source = read(base + "domain/detection/YoloSessionDiagnosticsBridge.kt")
  assert.match(source, /context\?\.token != current.token/)
  assert.match(source, /origin.expectedExercise/)
  assert.match(source, /active.getAndSet\(null\)/)
  assert.doesNotMatch(source, /observeDetectedExercise|RepScoreCalculator|RepAnalysisEngine|uploadSession/)
})

test("live YOLO FPS is separate from existing landmark performance", () => {
  const source = read(base + "domain/detection/DetectorComparison.kt")
  assert.match(source, /YOLO processedFPS=/)
  assert.match(source, /data.processedFps/)
  assert.match(read(base + "DashboardActivity.kt"), /관절 처리 %.1f FPS/)
  const collector = read(base + "domain/detection/YoloSessionDiagnostics.kt")
  assert.match(collector, /state.processed.toDouble\(\) \* 1000 \/ duration/)
})

test("local validation media are ignored and contract still cannot be promoted by a runtime flag", () => {
  assert.match(read(".gitignore"), /\/local-yolo-validation\//)
  const contract = read(base + "domain/detection/Exp02YoloContract.kt")
  assert.match(contract, /get\(\) = ExportContractVerification.VERIFIED/)
  assert.match(read("app/src/main/java/com/example/arptapp/domain/detection/YoloExerciseDetector.kt"), /runtimePolicy.predictionAllowed/)
  assert.doesNotMatch(contract, /getenv|getProperty|SharedPreferences|FORCE_VERIFIED/)
  assert.match(read("app/build.gradle.kts"), /ignoreAssetsPattern = "[^"\n]*\*\.tflite/)
})
