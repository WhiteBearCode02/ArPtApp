import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

const read = p => readFileSync(p, "utf8")
const root = "app/src/main/java/com/example/arptapp/"
const dashboard = read(root + "DashboardActivity.kt")
const camera = read(root + "ui/camera/PoseCameraController.kt")
const factory = read(root + "ui/camera/yolo/TfliteYoloRuntime.kt")

test("production factory is DEBUG-only and injects only unverified conversion ports", () => {
  assert.match(factory, /if \(!BuildConfig.DEBUG\) return null/)
  assert.match(factory, /UnverifiedYoloContract.components<Bitmap>\(\)/)
  assert.doesNotMatch(factory, /ExportContractVerification.VERIFIED|Yolo26Classifier\(/)
})

test("contract gate precedes any future runtime call, with no inferred conversion or NMS", () => {
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
  assert.match(dashboard, /tvYoloDiagnostics.visibility = if \(BuildConfig.DEBUG\) View.VISIBLE else View.GONE/)
  const render = dashboard.slice(dashboard.indexOf("private fun renderYoloShadowDiagnostic()"), dashboard.indexOf("private fun onCameraPipelineError("))
  assert.match(render, /if \(!BuildConfig.DEBUG\) return/)
  assert.match(render, /Log.d\("YoloShadow", text\)/)
  assert.doesNotMatch(render, /observeDetectedExercise|resetSession|processAngle|uploadSession|addCompletedRep|generateFinalReport/)
  for (const layout of ["layout", "layout-land"]) {
    assert.match(read(`app/src/main/res/${layout}/activity_dashboard.xml`), /id="@\+id\/tvYoloDiagnostics"/)
  }
})

test("only the original classifier adapter feeds workout type selection", () => {
  assert.match(dashboard, /updateCurrentExerciseType\(primaryExerciseDetector.detect\(LandmarkDetectionFrame/)
  assert.match(dashboard, /primaryExerciseDetector = LandmarkExerciseDetector\(fallbackExerciseClassifier\)/)
  assert.doesNotMatch(dashboard, /observeDetectedExercise\(yolo|updateCurrentExerciseType\(.*yolo/i)
})

test("unavailable shadow paths cannot copy source frames and use a dedicated lifecycle", () => {
  const runner = read(root + "domain/detection/YoloShadowRunner.kt")
  assert.ok(runner.indexOf("latest.result.status != DetectorStatus.READY") < runner.indexOf("copyFrame(frame)"))
  assert.match(runner, /MIN_FRAME_INTERVAL_MS = 250L/)
  assert.match(runner, /Executors.newSingleThreadExecutor\(\)/)
  assert.match(camera, /yoloShadow\?\.start\(\)/)
  assert.match(camera, /yoloShadow\?\.close\(\)/)
  assert.match(camera, /yoloShadow\?\.trySubmit\(normalizedBitmap, timestamp\)/)
})

test("YOLO modules add no network, credentials, persistence of observations or model staging", () => {
  const files = ["domain/detection/ExerciseDetector.kt", "domain/detection/YoloExportContract.kt",
    "domain/detection/YoloExerciseDetector.kt", "domain/detection/YoloExerciseStabilizer.kt",
    "domain/detection/YoloShadowRunner.kt", "domain/detection/DetectorComparison.kt", "ui/camera/yolo/TfliteYoloRuntime.kt"]
  for (const file of files) {
    assert.doesNotMatch(read(root + file), /https?:\/\/|HttpClient|OkHttp|Retrofit|SUPABASE|LLM_API_KEY|accessToken|FileOutputStream|writeBytes|copyTo\(/)
  }
  assert.match(factory, /context.noBackupFilesDir/)
  assert.match(read("app/build.gradle.kts"), /ignoreAssetsPattern = "[^"\n]*\*\.tflite/)
  assert.match(read(".gitignore"), /\/app\/src\/main\/assets\/airptcoach_yolo26n_exp02_fp32.tflite/)
})
