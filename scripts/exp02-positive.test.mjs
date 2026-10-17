import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { execFileSync } from "node:child_process"

const read = path => readFileSync(path, "utf8")
const base = "app/src/"
const feature = "java/com/example/arptapp/ui/camera/yolo/validation/"

test("positive candidate images, result files and environment remain ignored and untracked", () => {
  assert.match(read(".gitignore"), /^\/local_validation\/$/m)
  for (const path of ["local_validation/exp02_positive/candidate.png", "local_validation/exp02_positive/android-positive-result.json", "local_validation/.venv/pyvenv.cfg"]) {
    execFileSync("git", ["check-ignore", "--quiet", "--no-index", "--", path])
  }
  assert.equal(execFileSync("git", ["ls-files", "--", "local_validation/exp02_positive"], { encoding: "utf8" }).trim(), "")
})

test("release factories cannot construct a native validator or render capture controls", () => {
  const factory = read(base + "release/" + feature + "DebugPositiveValidationFactory.kt")
  const controls = read(base + "release/java/com/example/arptapp/ui/dashboard/DebugPositiveValidationControls.kt")
  assert.match(factory, /Exp02PositiveValidation\? = null/)
  assert.match(controls, /DebugValidationControls\? = null/)
  assert.doesNotMatch(factory + controls, /TfliteYoloRuntime\(|DebugPositiveCandidateStore\(|setOnClickListener|BitmapFactory/)
})

test("capture has explicit one-shot admission and never subscribes to camera frames", () => {
  const source = read(base + "debug/java/com/example/arptapp/ui/dashboard/DebugPositiveValidationControls.kt")
  assert.match(source, /capture\.setOnClickListener/)
  assert.match(source, /CountDownTimer\(3_000, 1_000\)/)
  assert.match(source, /requestFrame \{ frame ->/)
  assert.match(source, /awaitingFrame = false/)
  assert.match(source, /validation\.saveAndAnalyze\(frame\)/)
  assert.doesNotMatch(source, /while\s*\(true\)|addObserver|launchWhenStarted|repeatOnLifecycle|FileOutputStream/)
})

test("candidate store bounds storage and only deletes allowlisted files without recursion", () => {
  const source = read(base + "debug/" + feature + "DebugPositiveCandidateStore.kt")
  assert.match(source, /MAX_CANDIDATES = 8/)
  assert.match(source, /MAX_PIXELS = 4_194_304/)
  assert.match(source, /MAX_PNG_BYTES = 8L \* 1024 \* 1024/)
  assert.match(source, /filter \{ OWNED\.matches\(it\) \}/)
  assert.match(source, /OsConstants\.S_ISLNK/)
  assert.match(source, /Os\.chmod\(temporary\.absolutePath, 384\)/)
  assert.doesNotMatch(source, /deleteRecursively|getExternal|MediaStore|Supabase|HttpClient|java\.net/)
})

test("native candidate tool uses the existing preprocessor decoder and NMS without a session bridge", () => {
  const source = read(base + "debug/" + feature + "DebugPositiveNativeInference.kt")
  for (const component of ["Exp02BitmapPreprocessor()", "Exp02OutputDecoder()", "Exp02PostProcessor()", "TfliteYoloRuntime(DebugYoloModelSource.open(context))"]) {
    assert.ok(source.includes(component))
  }
  assert.match(source, /"predictionUsedForSession" to false/)
  assert.match(source, /"contractPromotionPerformed" to false/)
  assert.match(source, /decodedRgbSha256/)
  assert.doesNotMatch(source, /sessionExerciseType\s*=|RepAnalysisEngine\(|ExerciseClassifier\(|forceVerified/i)
})

test("positive native harness cannot pass solely on empty detection or matching expected labels", () => {
  const source = read(base + "androidTest/java/com/example/arptapp/ui/camera/yolo/Exp02PositiveModelGoldenTest.kt")
  assert.match(source, /assertTrue\([^\n]+BuildConfig\.DEBUG\)/)
  assert.match(source, /expected\.isNotEmpty\(\) && actual\.isNotEmpty\(\)/)
  assert.match(source, /positivePairs > 0/)
  assert.match(source, /NO_POSITIVE_EQUIVALENCE_EVIDENCE/)
  assert.match(source, /preprocessing\.passed && comparison\.passed/)
  assert.match(source, /sample\.exercise == null/)
  assert.doesNotMatch(source, /require\([^\n]*topPredictionMatchesExpected|ExportContractVerification\.VERIFIED/)
})

test("candidate reference invokes exact pinned functions and never rewrites a semantic result", () => {
  const source = read("scripts/generate-exp02-positive-reference.py")
  for (const key of ["preprocess", "forward", "non_max_suppression", "scale_boxes"]) assert.ok(source.includes(`reference["${key}"]`))
  assert.match(source, /conf_thres=\.10, iou_thres=\.70/)
  assert.match(source, /actual\.post_nms_detections/)
  assert.match(source, /semanticMatch=None/)
  assert.match(source, /decodedRgbSha256/)
  assert.doesNotMatch(source, /cv2\.resize\(|from ultralytics import|pip install|requests\.|urllib\./)
})

test("transfer only offers fixed private paths with binary size and SHA validation", () => {
  const source = read("scripts/transfer-exp02-positive.py")
  assert.match(source, /REMOTE_CANDIDATES = "no_backup\/exp02_positive"/)
  assert.match(source, /REMOTE_BUNDLE = "no_backup\/yolo\/positive-golden"/)
  assert.match(source, /"iflag=count_bytes"/)
  assert.match(source, /"conv=fsync"/)
  assert.match(source, /TIMEOUT_SECONDS = 30/)
  assert.match(source, /refusing to overwrite private evidence/)
  assert.doesNotMatch(source, /"\/sdcard|"\/storage|"pull"|"push"|"root"|shell=True/)
})

test("summary distinguishes positive evidence from negative equivalence and never promotes contract", () => {
  const source = read("scripts/summarize-exp02-positive-comparison.py")
  assert.match(source, /positive_pairs > 0/)
  assert.match(source, /positiveEquivalenceStatus="PASS" if positive_pass else "NOT_VERIFIED"/)
  assert.match(source, /exportContractVerified=False, productionContract="NOT_VERIFIED"/)
  assert.match(source, /semanticCorrectness/)
  assert.match(source, /Exact representation of the unchanged Kotlin \.001f/)
})

test("production policy stays NOT_VERIFIED with unchanged confidence NMS and APK exclusion", () => {
  assert.match(read(base + "main/java/com/example/arptapp/domain/detection/Exp02YoloContract.kt"), /get\(\) = ExportContractVerification\.NOT_VERIFIED/)
  const policy = read(base + "main/java/com/example/arptapp/domain/detection/Exp02PostProcessor.kt")
  assert.match(policy, /APP_ENGINEERING_INITIAL_RAW_CONFIDENCE = \.10f/)
  assert.match(policy, /IOU_THRESHOLD = \.70f/)
  assert.match(read("app/build.gradle.kts"), /ignoreAssetsPattern = "[^"\n]*\*\.tflite/)
})
