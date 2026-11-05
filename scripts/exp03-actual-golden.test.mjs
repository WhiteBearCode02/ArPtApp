import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const read = path => readFileSync(path, 'utf8')
const generator = read('scripts/generate-exp03-actual-golden.py')
const transfer = read('scripts/transfer-exp03-actual-golden.py')
const device = read('app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp03ActualModelGoldenTest.kt')

test('EXP03 actual reference is pinned to its OWN actual model and exact export source', () => {
  assert.match(generator, /VERSION = "8\.4\.202"/)
  assert.match(generator, /MODEL_BYTES = 9894032/)
  assert.match(generator, /ae71dc5bb1024010fbdef5bd9281984ddd8c70885bddcfdd6aff0801b7015fb4/)
  assert.match(generator, /reference\["forward"\]\(backend, tensor\)/)
  assert.match(generator, /intel=False/)
  assert.match(generator, /sourceExecution="EXACT_HASH_PINNED_WHEEL_AST_NOT_INSTALLED_PACKAGE"/)
  assert.doesNotMatch(generator, /from ultralytics|import ultralytics|pip install|requests\.|urlopen|SyntheticInterpreter\(/)
})

test('new generator retains rights, immutable private runs, hashes, geometry and unchanged tolerances', () => {
  for (const token of ['shared.read_manifest', 'sourceImageSha256', 'rgbSha256', 'inputTensorSha256', 'nativeOutputSha256',
    'preprocessing=dict', 'sourceSha256', 'referenceFunctions', 'shared.semantic_observation', 'exist_ok=False', 'open("xb")']) {
    assert.ok(generator.includes(token), token)
  }
  assert.match(generator, /TOLERANCES = dict\(confidence=\.001, bboxPixels=2\.0, detectionCount=0, classMatch="EXACT"\)/)
  assert.match(generator, /if output.exists\(\)/)
})

test('device runs explicit EXP03 native model and separates three comparison stages', () => {
  assert.match(device, /TfliteYoloRuntime\(DebugYoloModelSource.open\(context, KnownYoloModels.EXP03\), KnownYoloModels.EXP03\)/)
  for (const token of ['YoloBitmapPreprocessor()', 'YoloActualGoldenTensorComparator.compare', 'samePythonTensorNativeComparison',
    'frozenPythonRawDecodeNmsComparison', 'expectedReferenceHash', 'requireGeometry', 'pixelComparison.passed && comparison.passed && nativeOnly.passed && frozenRaw.passed']) {
    assert.ok(device.includes(token), token)
  }
  assert.match(device, /actual.firstOrNull\(\)\?\.classId/)
  assert.doesNotMatch(device, /assumeTrue|Assume\.|Supabase|ExerciseRepository|FileOutputStream.*Bitmap/)
})

test('bundle transfer is separate from models and has fixed private, exclusive, hash-checked paths', () => {
  for (const token of ['no_backup/yolo/exp03-actual-golden/runs', 'validate_bundle', 'readlink', 'Exactly one authorized',
    'adb.write_private(target, data)', 'reference published last', 'remote)  # exclusive']) assert.ok(transfer.includes(token), token)
  assert.doesNotMatch(transfer, /\/sdcard|\/storage\/|adb root|uninstall|pm clear|force-stop|SupabaseClient|upload.*https/)
  assert.match(transfer, /document.get\("referenceSha256"\) != reference_hash/)
})

test('actual Golden does not itself modify a production contract or claim camera performance', () => {
  assert.match(device, /"contractPromotionPerformed" to false/)
  assert.match(device, /"cameraPerformance" to "NOT_MEASURED"/)
  assert.match(device, /OFFLINE_STILL_IMAGE_TEST_NOT_CAMERA_PERFORMANCE/)
  assert.doesNotMatch(generator + transfer + device, /writeText\([^\n]*Exp03YoloContract|verification\s*=\s*ExportContractVerification.VERIFIED/)
})
