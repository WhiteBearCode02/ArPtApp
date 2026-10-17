import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

const read = p => readFileSync(p, "utf8")
const root = "app/src/main/java/com/example/arptapp/"
const dashboard = read(root + "DashboardActivity.kt")
const camera = read(root + "ui/camera/PoseCameraController.kt")
const factory = read(root + "ui/camera/yolo/TfliteYoloRuntime.kt")

test("production factory is DEBUG-only and approved contract is independent of runtime rollout", () => {
  assert.match(factory, /if \(!BuildConfig.DEBUG\) return null/)
  assert.match(factory, /Exp02YoloContract.components\(Exp02BitmapPreprocessor\(\)\)/)
  assert.doesNotMatch(factory, /ExportContractVerification.VERIFIED|Yolo26Classifier\(/)
  const contract = read(root + "domain/detection/Exp02YoloContract.kt")
  assert.match(contract, /get\(\) = ExportContractVerification.VERIFIED/)
  assert.doesNotMatch(contract, /getenv|getProperty|BuildConfig|SharedPreferences/)
  assert.match(factory, /YoloRuntimePolicy.forBuild\(BuildConfig.DEBUG, BuildConfig.YOLO_SHADOW_ENABLED\)/)
})

test("contract gate precedes runtime call and unknown exports retain three fail-closed ports", () => {
  const contract = read(root + "domain/detection/YoloExportContract.kt")
  assert.match(contract, /minimumConfidence = null/)
  assert.match(contract, /TODO\(EXP02_EXPORT_CONTRACT\)/)
  assert.equal((contract.match(/throw ExportContractNotVerified\(\)/g) ?? []).length, 3)
  assert.doesNotMatch(contract, /114|255|xywh|xyxy|sigmoid|nonMaxSuppression/)
  const detector = read(root + "domain/detection/YoloExerciseDetector.kt")
  assert.match(detector, /contract.verification != ExportContractVerification.VERIFIED/)
  assert.ok(detector.indexOf("contract.verification !=") < detector.indexOf("runtime?.run("))
})

test("shadow UI and logging are unreachable in non-debug activity paths", () => {
  assert.match(dashboard, /btnYoloDiagnosticsToggle.visibility = if \(BuildConfig.DEBUG\) View.VISIBLE else View.GONE/)
  assert.match(dashboard, /yoloDiagnosticsPanel\.setExpanded\(/)
  const panel = read(root + "ui/dashboard/YoloDiagnosticsPanel.kt")
  assert.match(panel, /BuildConfig\.DEBUG/)
  assert.match(panel, /visibility =.*View\.GONE/)
  const render = dashboard.slice(dashboard.indexOf("private fun renderYoloShadowDiagnostic()"), dashboard.indexOf("private fun onCameraPipelineError("))
  assert.match(render, /if \(!BuildConfig.DEBUG\) return/)
  assert.match(render, /Log.d\("YoloShadow", text\)/)
  assert.doesNotMatch(render, /observeDetectedExercise|resetSession|processAngle|uploadSession|addCompletedRep|generateFinalReport/)
  for (const layout of ["layout", "layout-land"]) {
    const source = read(`app/src/main/res/${layout}/activity_dashboard.xml`)
    assert.doesNotMatch(source, /id="@\+id\/(?:tvYoloDiagnostics|tvPerformance)"/)
    assert.match(source, /id="@\+id\/yoloDiagnosticsPanel"/)
  }
  assert.match(read("app/src/main/res/layout/view_yolo_diagnostics_panel.xml"), /id="@\+id\/tvYoloDiagnostics"/)
})

test("only the original classifier adapter feeds workout type selection", () => {
  assert.match(dashboard, /updateCurrentExerciseType\(primaryExerciseDetector.detect\(LandmarkDetectionFrame/)
  assert.match(dashboard, /primaryExerciseDetector = LandmarkExerciseDetector\(fallbackExerciseClassifier\)/)
  assert.doesNotMatch(dashboard, /observeDetectedExercise\(yolo|updateCurrentExerciseType\(.*yolo/i)
})

test("unavailable shadow paths cannot copy source frames and use a dedicated lifecycle", () => {
  const runner = read(root + "domain/detection/YoloShadowRunner.kt")
  assert.ok(runner.indexOf("!latest.result.predictionAllowed") >= 0)
  assert.ok(runner.indexOf("!latest.result.predictionAllowed") < runner.indexOf("copyFrame(frame)"))
  assert.match(runner, /MIN_FRAME_INTERVAL_MS = 250L/)
  assert.match(runner, /Executors.newSingleThreadExecutor\(\)/)
  assert.match(camera, /yoloShadow\?\.start\(\)/)
  assert.match(camera, /yoloShadow\?\.close\(\)/)
  assert.match(camera, /yoloShadow\?\.trySubmit\(normalizedBitmap, timestamp\)/)
})

test("YOLO modules add no network, credentials, persistence of observations or model staging", () => {
  const files = ["domain/detection/ExerciseDetector.kt", "domain/detection/YoloExportContract.kt",
    "domain/detection/YoloExerciseDetector.kt", "domain/detection/YoloExerciseStabilizer.kt",
    "domain/detection/YoloShadowRunner.kt", "domain/detection/DetectorComparison.kt", "ui/camera/yolo/TfliteYoloRuntime.kt",
    "domain/detection/Exp02Preprocessor.kt", "domain/detection/Exp02PostProcessor.kt", "domain/detection/Exp02YoloContract.kt",
    "domain/detection/YoloModelIdentity.kt", "ui/camera/yolo/Exp02BitmapPreprocessor.kt"]
  for (const file of files) {
    assert.doesNotMatch(read(root + file), /https?:\/\/|HttpClient|OkHttp|Retrofit|SUPABASE|LLM_API_KEY|accessToken|FileOutputStream|writeBytes|copyTo\(/)
  }
  assert.match(factory, /context.noBackupFilesDir/)
  assert.match(read("app/build.gradle.kts"), /ignoreAssetsPattern = "[^"\n]*\*\.tflite/)
  assert.match(read(".gitignore"), /\/app\/src\/main\/assets\/airptcoach_yolo26n_exp02_fp32.tflite/)
})

test("canonical model bytes are validated before native initialization", () => {
  assert.ok(factory.indexOf("YoloModelIdentity.validate(model)") < factory.indexOf("Interpreter(model,"))
  assert.match(read(root + "domain/detection/YoloModelIdentity.kt"), /CANONICAL_SHA256/)
  assert.match(read(root + "domain/detection/YoloModelIdentity.kt"), /CANONICAL_MODEL_BYTES/)
  assert.match(dashboard, /cameraController.invalidateShadowEvidence\(\)/)
  assert.match(camera, /yoloShadow\?\.isCurrent\(snapshot\)/)
})
