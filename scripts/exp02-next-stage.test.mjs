import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const read = path => readFileSync(path, 'utf8')

test('next-stage review cannot change production verification or primary policy', () => {
  assert.match(read('app/src/main/java/com/example/arptapp/domain/detection/Exp02YoloContract.kt'), /get\(\) = ExportContractVerification\.NOT_VERIFIED/)
  const dashboard = read('app/src/main/java/com/example/arptapp/DashboardActivity.kt')
  assert.match(dashboard, /primaryExerciseDetector = LandmarkExerciseDetector/)
  assert.match(read('docs/ml/EXP02_VERIFIED_REVIEW.md'), /상수만 바꾸면 Dashboard 실시간 Shadow까지 켜질 수/)
})
test('positive native class coverage is an evidence assertion, not a prediction rewrite', () => {
  const source = read('app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp02PositiveModelGoldenTest.kt')
  assert.match(source, /requiredPositiveClass/)
  assert.match(source, /requiredClassPairs > 0/)
  assert.match(source, /REQUIRED_CLASS_POSITIVE_NOT_OBSERVED/)
  assert.doesNotMatch(source, /\.filter\s*\{[^\n]*requiredClass/)
  assert.match(source, /target\.createNewFile\(\)/)
})
test('raw NMS export is opt-in copied normalized output from the same native invoke', () => {
  const source = read('scripts/generate-exp02-positive-reference.py')
  assert.match(source, /interpreter\.get_tensor\(outputs\[0\]\["index"\]\)/)
  assert.match(source, /getattr\(args, "export_nms_raw", False\)/)
  assert.match(source, /ACTUAL_NATIVE_NORMALIZED_GET_TENSOR_COPY_AFTER_REFERENCE_FORWARD/)
  assert.match(source, /require_empty_output\(output\)/)
  assert.doesNotMatch(source, /snapshot.*\/\s*640|requests\.|urllib\./)
})
test('private runs reject traversal and previous evidence rather than deleting it', () => {
  const source = read('scripts/transfer-exp02-positive.py')
  assert.match(source, /Private run already contains evidence/)
  assert.match(source, /readlink", "-f"/)
  assert.match(source, /REMOTE_NMS = "no_backup\/yolo\/nms-contract"/)
  assert.match(source, /normalizedOutput/)
  assert.doesNotMatch(source, /rm", "-r|\/sdcard|\/storage\/emulated|shell=True/)
})
test('controlled and actual NMS retain source-backed implementation and frozen thresholds', () => {
  const source = read('scripts/generate-exp02-nms-reference.py')
  for (const name of ['ACTUAL_IMAGE_NMS', 'CONTROLLED_MODEL_FORMAT_NMS', 'non_max_suppression']) assert.ok(source.includes(name))
  assert.match(source, /CONFIDENCE, IOU, MAX_DETECTIONS = \.10, \.70, 300/)
  const native = read('app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp02NmsContractTest.kt')
  assert.match(native, /Exp02OutputDecoder\(\)\.decode/)
  assert.match(native, /Exp02PostProcessor\(\)\.process/)
  assert.doesNotMatch(native, /Interpreter\(|runtime\.run\(|Supabase|AppDatabase|ExerciseCounter/)
})
test('persistence regression reads a frozen source run without creating or changing its directory', () => {
  const source = read('app/src/androidTest/java/com/example/arptapp/data/YoloSessionPersistenceDeviceTest.kt')
  assert.match(source, /getString\("sessionSourceEvidenceRun"\)/)
  assert.match(source, /readEvidence\("selected_record.json", sourceRun\)/)
  assert.match(source, /evidenceDirectory\(run, create = false\)/)
  assert.match(source, /SQLiteDatabase\.OPEN_READONLY/)
  assert.match(source, /historyRowTapPerformed", false/)
  assert.doesNotMatch(source, /INSERT INTO|UPDATE exercise_records|DELETE FROM|AuthSessionStore\.current\s*=/)
})
