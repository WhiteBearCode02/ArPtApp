import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
const read = p => readFileSync(p, 'utf8')
const root = 'app/src/main/java/com/example/arptapp/'
const android = 'app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/'

test('EXP04 exact local model and all private validation materials stay ignored and untracked', () => {
  for (const file of ['app/src/main/assets/airptcoach_yolo26n_exp04_7class_fp32.tflite', 'local_validation/exp04_golden/manifest.json',
    'local_validation/exp04_golden/inputs/0_SQUAT/frame.jpg', 'local_validation/exp04_golden/runs/test/reference.json']) {
    execFileSync('git', ['check-ignore', '--quiet', '--no-index', '--', file])
    assert.equal(execFileSync('git', ['ls-files', '--', file], { encoding: 'utf8' }).trim(), '')
  }
})

test('profiles retain two-class defaults while EXP04 buffer size and seven classes are model-bound', () => {
  const profile = read(root + 'domain/detection/YoloModelProfile.kt')
  const detector = read(root + 'domain/detection/YoloExerciseDetector.kt')
  for (const token of ['Collections.unmodifiableMap', 'Collections.unmodifiableSet', '4 + classes.size', 'Math::multiplyExact',
    'ExerciseType.BICEP_CURL', 'ExerciseType.PUSH_UP', 'ExerciseType.BENT_OVER_ROW', 'ExerciseType.TRICEPS_EXTENSION', 'ExerciseType.SIDE_BEND']) assert.ok(profile.includes(token), token)
  for (const token of ['contract.profile.outputBytes', 'matchesExpected(contract.profile)', 'contract.profile.supportedExercises']) assert.ok(detector.includes(token), token)
  assert.doesNotMatch(detector, /4 \* 6 \* 8400|YoloModelPolicy\.outputShape/)
  assert.match(read(root + 'domain/detection/YoloExportContract.kt'), /val outputShape: List<Int> get\(\) = listOf\(1, 6, 8400\)/)
})

test('EXP04 candidate contract is permanently NOT_VERIFIED until separate reviewed code change', () => {
  const contract = read(root + 'domain/detection/Exp04YoloContract.kt')
  assert.match(contract, /get\(\) = ExportContractVerification.NOT_VERIFIED/)
  assert.match(contract, /TODO\(EXP04_ANDROID_ACTUAL_GOLDEN\)/)
  assert.doesNotMatch(contract, /BuildConfig|SharedPreferences|Intent|File\(|ExportContractVerification\.VERIFIED\b/)
  assert.match(read(root + 'domain/detection/YoloExerciseDetector.kt'), /contract.verification != ExportContractVerification.VERIFIED/)
})

test('EXP04 build is a separate explicit DEBUG sandbox, release stays off and assets stay excluded', () => {
  const gradle = read('app/build.gradle.kts')
  const block = gradle.slice(gradle.indexOf('create("exp04Debug")')).split('\n        }')[0]
  for (const token of ['applicationIdSuffix = ".exp04"', '"YOLO_PRIVATE_BUNDLED", "false"', '\\"EXP04\\"', '"YOLO_AB_COMPARISON", "true"']) assert.ok(block.includes(token), token)
  assert.match(gradle, /release \{\s*buildConfigField\("boolean", "YOLO_SHADOW_ENABLED", "false"\)/)
  assert.match(gradle, /\*\.tflite:\*\.pt:\*\.pth:\*\.onnx/)
  assert.match(read(root + 'domain/detection/YoloBuildSelection.kt'), /"EXP04" -> KnownYoloModels.EXP04.also \{ require\(applicationId == "com.example.arptapp.exp04"\)/)
  assert.match(read(root + 'ui/camera/yolo/TfliteYoloRuntime.kt'), /identity == KnownYoloModels.EXP02/)
})

test('EXP04 decoder takes an argmax over profile classes with unchanged score, NMS and no second sigmoid', () => {
  const source = read(root + 'domain/detection/Exp02PostProcessor.kt')
  for (const token of ['profile.classMapping', 'profile.outputBytes', 'profile.anchors', 'probability.isFinite()', 'probability !in 0f..1f']) assert.ok(source.includes(token), token)
  assert.doesNotMatch(source, /fun sigmoid|kotlin.math.exp\(|\*\s*objectness\b/)
  const tests = read('app/src/test/java/com/example/arptapp/domain/detection/Exp04ContractTest.kt')
  for (const token of ['twoClassGenericRegression', 'stabilizerSupportsSeven', 'realContractNeverAdmitsCameraInference', '369600']) assert.ok(tests.includes(token), token)
})

test('diagnostic persistence is model-bound and does not reinterpret EXP02/03 as seven-class evidence', () => {
  assert.match(read(root + 'data/YoloSessionDiagnosticsCodec.kt'), /YoloModelProfiles.requireModel\(modelId\).countKeys/)
  assert.match(read(root + 'domain/detection/YoloSessionDiagnostics.kt'), /profile.countKeys/)
  const ui = read(root + 'presentation/report/YoloSessionDiagnosticsPresentation.kt')
  assert.match(ui, /YoloModelProfiles/)
  assert.doesNotMatch(read(root + 'domain/detection/Exp04YoloContract.kt'), /sessionExerciseType\s*=|RepAnalysisEngine\(|Supabase|TextToSpeech/)
})

test('EXP04 provisioning uses exact identity, one explicit device, private binary stream and atomic replacement', () => {
  const ps = read('scripts/provision-exp04-model.ps1')
  for (const token of ['9898949', '3e613e94fc1ef3aebbeba309d1b31d7ba6fc71a19274c9b2946793fe493eb84d',
    'com.example.arptapp.exp04', 'ValidateLocalOnly', 'ShouldProcess', 'iflag=count_bytes', 'conv=fsync', "'600'", "'mv'", 'readlink']) assert.ok(ps.includes(token), token)
  assert.doesNotMatch(ps, /Invoke-WebRequest|DownloadFile|\/sdcard|\/data\/local\/tmp|adb root|uninstall|pm clear/)
})

test('EXP04 Python reference is actual-only, pinned to 8.4.202, private and not expected-label filtered', () => {
  const source = read('scripts/generate-exp04-actual-golden.py')
  for (const token of ['VERSION = "8.4.202"', 'old.load_reference(args.wheel)', 'reference["forward"](backend, tensor)', 'nc=7',
    'post_nms_detections(result.tolist(), w, h)', 'rightsCleared', 'exist_ok=False', 'sourceImageSha256', 'allow_nan=False',
    'androidNativeInferenceExecuted=False', 'exportContractVerified=False', 'contractPromotionPerformed=False']) assert.ok(source.includes(token), token)
  assert.doesNotMatch(source, /from ultralytics|import ultralytics|pip install|urlopen|requests\.|download\(/)
})

test('EXP04 fixtures are seven-class empty templates, never rights or Golden evidence', () => {
  const document = JSON.parse(read('docs/ml/exp04-actual-golden.manifest.example.json'))
  assert.equal(document.samples.length, 7)
  assert.equal(new Set(document.samples.map(s => s.exercise)).size, 7)
  for (const [index, sample] of document.samples.entries()) {
    assert.equal(sample.rightsCleared, false)
    assert.equal(sample.rightsBasis, '')
    assert.ok(sample.image.startsWith(`inputs/${index}_${sample.exercise}/`))
  }
})

test('EXP04 fixture transfer is explicit isolated package with exclusive hash-bound private runs', () => {
  const source = read('scripts/transfer-exp04-actual-golden.py')
  for (const token of ['com.example.arptapp.exp04', 'package=PACKAGE', 'no_backup/yolo/exp04-actual-golden/runs',
    'validate_bundle', 'readlink', 'Exactly one authorized', 'adb.write_private(target, data)', 'reference published last', 'referenceSha256']) assert.ok(source.includes(token), token)
  assert.doesNotMatch(source, /\/sdcard|\/storage\/|uninstall|pm clear|force-stop|https:\/\/|Supabase/)
})

test('Android actual Golden separates exact pixels, frozen NMS, native parity and semantics without promotion', () => {
  const test = read(android + 'Exp04ActualModelGoldenTest.kt')
  const fixtures = read(android + 'Exp04GoldenFixtures.kt')
  for (const token of ['preprocessingOnlyMatchesPythonBitForBit', 'PREPROCESSING_NOT_BYTE_IDENTICAL', 'pixelComparison.passed && comparison.passed && frozenRaw.passed',
    'semanticMatch', 'pythonPositiveClassCoverage', '"contractPromotionPerformed" to false', '"cameraPerformance" to "NOT_MEASURED"']) assert.ok(test.includes(token), token)
  for (const token of ['exp04ReferenceSha256', 'sourceSha256 == mapOf', 'LITTLE_ENDIAN', 'EXACT_TENSOR_BYTES_SHA256', 'requireSandbox()', 'file.createNewFile()']) assert.ok(fixtures.includes(token), token)
  assert.doesNotMatch(test + fixtures, /ExportContractVerification.VERIFIED|Supabase|RepAnalysisEngine|HttpClient|sessionExerciseType\s*=/)
})

test('native smoke is bounded and cross-model rejection remains separate from actual Golden', () => {
  const smoke = read(android + 'Exp04NativeContractDeviceTest.kt')
  for (const token of ['profile.inputBytes', 'profile.outputBytes', 'output.float.isFinite()', 'YoloModelIdentityMismatch::class.java',
    'KnownYoloModels.EXP03', 'KnownYoloModels.EXP04', 'ExportContractVerification.NOT_VERIFIED', 'NOT_RUN: separate EXP03']) assert.ok(smoke.includes(token), token)
})
