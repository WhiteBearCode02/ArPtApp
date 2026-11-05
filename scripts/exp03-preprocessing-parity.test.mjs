import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const read = path => readFileSync(path, 'utf8')
const base = 'app/src/main/java/com/example/arptapp/'

test('EXP03 uses source-backed bounded integer interpolation, without changing geometry or native deps', () => {
  const source = read(base + 'domain/detection/YoloOpenCvLinearRgbPreprocessor.kt')
  for (const token of ['Exp02FrameTransform.create', 'round(weight * 2048f)', 'coordinate - source', 'h0 shr 4', 'shr 16', 'shr 2', '114f / 255f', 'value / 255f']) assert.ok(source.includes(token), token)
  assert.doesNotMatch(source, /import org.opencv|tensorflow|tflite|Supabase|https|FileOutputStream|SharedPreferences/)
  assert.ok(read('app/src/main/res/raw/opencv_resize_notice.txt').includes('Intel Corporation'))
})

test('EXP02 keeps its original delegate while canonical EXP03 switches only its resize implementation', () => {
  const adapter = read(base + 'ui/camera/yolo/Exp02BitmapPreprocessor.kt')
  assert.match(adapter, /delegate: YoloPreprocessor<Exp02RgbFrame> = YoloOpenCvLinearRgbPreprocessor\(\)/)
  assert.match(adapter, /class Exp02BitmapPreprocessor : YoloPreprocessor<Bitmap> by YoloBitmapPreprocessor\(Exp02RgbPreprocessor\(\)\)/)
})

test('synthetic vectors cover 64 complete outputs without embedding tensors or actual media', () => {
  const fixture = JSON.parse(read('app/src/test/resources/exp03-opencv-4.13-linear.json'))
  assert.equal(fixture.cases.length, 64)
  assert.equal(fixture.environment.opencvVersion, '4.13.0')
  for (const vector of fixture.cases) {
    assert.match(vector.resizedRgbSha256, /^[a-f0-9]{64}$/)
    assert.match(vector.inputTensorSha256, /^[a-f0-9]{64}$/)
    assert.equal(vector.defaultVsUnoptimizedMismatches, 0)
    assert.ok(vector.probes.length <= 11)
  }
})

test('actual Golden preprocessing is strict bit equality and has a separate no-inference preflight', () => {
  const source = read('app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp03ActualModelGoldenTest.kt')
  assert.match(source, /fun preprocessingOnlyMatchesPythonBitForBit/)
  assert.match(source, /samplePassed = inputByteIdentical && pixelComparison.passed/)
  assert.match(source, /preprocessingAcceptance == "EXACT_TENSOR_BYTES_SHA256"/)
  assert.match(read('scripts/generate-exp03-actual-golden.py'), /preprocessingTolerances=dict\(exact=0, resampled=0/)
})

test('APK verification retains exact source-license bytes even when release paths are shortened', () => {
  const scanner = read('scripts/verify-public-apk.ps1')
  assert.match(scanner, /expectedNoticeHash/)
  assert.match(scanner, /ComputeHash\(\$stream\)/)
  assert.match(scanner, /if \(-not \$noticeFound\)/)
  assert.match(read('.gitignore'), /!\/app\/src\/main\/res\/raw\/opencv_resize_notice\.txt/)
})
