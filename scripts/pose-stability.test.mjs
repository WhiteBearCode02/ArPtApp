import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

// Wiring checks complement pure JVM policy tests; these do not bind a real Android camera.
const root = "app/src/main/"
const source = path => readFileSync(root + path, "utf8")
const dashboard = source("java/com/example/arptapp/DashboardActivity.kt")

test("actual lens is published only after binding and both layouts contain its UI", () => {
  const camera = source("java/com/example/arptapp/ui/camera/PoseCameraController.kt")
  assert.match(camera, /lensFacing = CameraSelector.LENS_FACING_BACK/)
  assert.match(camera, /hasCamera\(CameraSelector.DEFAULT_BACK_CAMERA\)/)
  assert.ok(camera.indexOf("onLensChanged(front, hasBack && hasFront)") > camera.indexOf("provider.bindToLifecycle("))
  for (const layout of ["layout", "layout-land"]) {
    assert.match(source(`res/${layout}/activity_dashboard.xml`), /id="@\+id\/tvCameraLens"/)
  }
})

test("physical lens never selects body view in the analysis path", () => {
  const analysis = dashboard.slice(dashboard.indexOf("private fun processLandmarks("), dashboard.indexOf("private fun List<NormalizedLandmark>.toPoseData"))
  assert.doesNotMatch(analysis, /isFrontFacing|LENS_FACING/)
  assert.match(dashboard, /LandmarkDetectionFrame\(mainPersonLandmarks, cameraView,/)
  assert.match(source("java/com/example/arptapp/ui/camera/LandmarkExerciseDetector.kt"), /classifier.detectExercise\(frame.landmarks, frame.view, frame.aspectRatio\)/)
})

test("counting and structural analysis use only the locked session type", () => {
  assert.match(dashboard, /val exerciseType = sessionExerciseType.name/)
  assert.match(dashboard, /exerciseCounter.processAngle\(exerciseType, currentAngle, timestamp\)/)
  assert.doesNotMatch(dashboard, /processAngle\(detected|currentExerciseType/)
  const update = dashboard.slice(dashboard.indexOf("private fun updateCurrentExerciseType("), dashboard.indexOf("private fun updateDetectedExerciseUi("))
  assert.doesNotMatch(update, /reset|clear\(|repetitionCount = 0/)
})

test("unreportable sessions return before upload and result navigation", () => {
  const end = dashboard.slice(dashboard.indexOf("private fun endExercise()"), dashboard.indexOf("private fun showSessionReport("))
  assert.match(end, /generateLockedSessionReport\(\)/)
  const guard = end.slice(end.indexOf("if (report == null)"), end.indexOf("val elapsedTime"))
  assert.match(guard, /return/)
  assert.doesNotMatch(guard, /uploadSession|openResultActivity|WorkoutReportData/)
  assert.ok(end.indexOf("uploadSession(") > end.indexOf("val elapsedTime"))
})

test("live scoring follows structured completion, and diagnostics remain debug guarded", () => {
  assert.ok(dashboard.indexOf("repAnalysisEngine.complete(repetitionCount)") < dashboard.indexOf("mainViewModel.addCompletedRep("))
  assert.doesNotMatch(dashboard, /mainViewModel.addRepRecord\(|mainViewModel.addStructuredRep\(/)
  assert.match(dashboard, /if \(BuildConfig.DEBUG\) Log.d\("RepAnalysis",/)
})

test("this change does not activate YOLO or an LLM provider", () => {
  assert.doesNotMatch(dashboard, /YOLOAnalyzer\(|YoloAnalyzer\(|YOLOPoseAnalyzer\(/)
  assert.match(source("java/com/example/arptapp/data/remote/AiCoachingRepository.kt"), /const val ENABLED = false/)
  const edge = readFileSync("supabase/functions/generate-workout-report/index.ts", "utf8")
  assert.match(edge, /provider: createProvider\(\)/)
  assert.match(readFileSync("supabase/functions/generate-workout-report/provider.ts", "utf8"), /return new UnavailableLlmProvider\(\)/)
})
