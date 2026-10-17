import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { execFileSync } from "node:child_process"

const read = path => readFileSync(path, "utf8")

test("local validation images and result envelopes are ignored and never tracked", () => {
  assert.match(read(".gitignore"), /^\/local_validation\/$/m)
  assert.match(read(".gitignore"), /^\/local_validation\/\.venv\/$/m)
  execFileSync("git", ["check-ignore", "--quiet", "--no-index", "--", "local_validation/.venv/pyvenv.cfg"])
  execFileSync("git", ["check-ignore", "--quiet", "--no-index", "--", "local_validation/exp02_golden/reference_results.json"])
  assert.equal(execFileSync("git", ["ls-files", "--", "local_validation"], { encoding: "utf8" }).trim(), "")
})

test("dedicated environment instructions do not claim Python inference proves Android equivalence", () => {
  const source = read("docs/ml/EXP02_LOCAL_GOLDEN_ENVIRONMENT.md")
  assert.match(source, /ai-edge-litert==2\.2\.0/)
  assert.match(source, /torch==2\.9\.1\+cpu/)
  assert.match(source, /--no-deps build\/yolo-contract-reference\/ultralytics-8\.4\.176/)
  assert.match(source, /native inference4회 성공/)
  assert.match(source, /positive bbox\/confidence\/NMS는 미검증/)
  assert.match(source, /positive class\/confidence\/bbox\/NMS/)
})

test("provisioning maps a validated local reference filename to fixed private device reference", () => {
  const source = read("scripts/provision-exp02-model.ps1")
  assert.match(source, /\[string\]\$ReferenceFileName = 'reference\.json'/)
  assert.match(source, /Join-Path \$bundle \$ReferenceFileName/)
  assert.match(source, /Target = 'no_backup\/yolo\/actual-golden\/reference\.json'/)
  assert.match(source, /Unsafe\/missing input tensor fixture/)
  assert.match(source, /\$inputTensorBytes = 4L \* 3 \* 640 \* 640/)
  assert.match(source, /ExpectedHash = \$sample\.inputTensorSha256/)
})

test("static model inspection explicitly cannot imply real inference or verification", () => {
  const source = read("scripts/inspect-exp02-model.py")
  assert.match(source, /STATIC_FLATBUFFER_METADATA_ONLY/)
  assert.match(source, /actualModelGoldenExecuted=False, androidNativeInferenceExecuted=False/)
  assert.match(source, /contractStatus="NOT_VERIFIED"/)
  assert.doesNotMatch(source, /import tensorflow|from ai_edge_litert|Interpreter\(|\.invoke\(/)
})

test("native fixture validation accepts mismatches and empty predictions without semantic gating", () => {
  const source = read("app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp02ActualModelGoldenTest.kt")
  assert.match(source, /sample\.detections\.size in 0\.\.300/)
  assert.doesNotMatch(source, /require\(sample\.detections\.any|sample\.detections\.size in 1\.\.300/)
  assert.match(source, /comparison\.passed && preprocessing\.passed/)
  assert.match(source, /topPredictionMatchesExpected/)
  assert.match(source, /referenceClassOrdering/)
})

test("pixel comparison retains existing strict padding and single-byte interpolation bounds", () => {
  const source = read("app/src/main/java/com/example/arptapp/domain/detection/YoloActualGoldenComparison.kt")
  assert.match(source, /maximumExact <= 1e-6/)
  assert.match(source, /maximumResampled <= 1\.0 \/ 255 \+ 1e-6/)
  assert.match(source, /maximumNormalizedError/)
  assert.match(source, /meanNormalizedError/)
  assert.match(source, /maximumRgbValueEquivalentError/)
})

test("contract activation and model exclusion are not controlled by private fixtures", () => {
  const contract = read("app/src/main/java/com/example/arptapp/domain/detection/Exp02YoloContract.kt")
  assert.match(contract, /get\(\) = ExportContractVerification\.VERIFIED/)
  assert.match(read("app/src/main/java/com/example/arptapp/domain/detection/YoloRuntimePolicy.kt"), /debug && requested/)
  assert.doesNotMatch(contract, /reference_results|local_validation|System\.getenv|SharedPreferences/)
  assert.match(read("app/build.gradle.kts"), /ignoreAssetsPattern = "[^"\n]*\*\.tflite/)
})
