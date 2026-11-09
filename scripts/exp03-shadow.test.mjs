import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
const root = 'app/src/main/java/com/example/arptapp/'
const read = path => readFileSync(path, 'utf8')
test('EXP03 identity stays ignored and cannot be packaged or tracked', () => {
  const source = read(root + 'domain/detection/YoloExportContract.kt')
  assert.match(source, /val EXP03 = YoloModelDescriptor\("yolo26n_exp03_final_fp32", "airptcoach_yolo26n_exp03_final_fp32.tflite", 9894032L/)
  assert.match(source, /3d2b6bc537d8aef5a19b671226d73c98b49411574a193be72e225086202d1167/)
  assert.match(read('.gitignore'), /^\/app\/src\/main\/assets\/airptcoach_yolo26n_exp03_final_fp32\.tflite$/m)
  assert.equal(execFileSync('git', ['ls-files', '--', 'app/src/main/assets/airptcoach_yolo26n_exp03_final_fp32.tflite'], { encoding: 'utf8' }).trim(), '')
  assert.match(read('app/build.gradle.kts'), /ignoreAssetsPattern = "[^"\n]*\*\.tflite:\*\.pt:\*\.pth:\*\.onnx/)
})
test('EXP03 is independently reviewed after its own actual Golden and bound to current factory identity', () => {
  const contract = read(root + 'domain/detection/Exp03YoloContract.kt')
  assert.match(contract, /get\(\) = ExportContractVerification.VERIFIED/)
  assert.match(contract, /a9fc32327edfcfaab56b6c7eef27f2eb2c5dc68840f2c7954f4ce97997335e18/)
  assert.doesNotMatch(contract, /Exp02YoloContract\.verification|SharedPreferences|System.getenv|BuildConfig/)
  const factory = read(root + 'ui/camera/yolo/TfliteYoloRuntime.kt')
  assert.match(factory, /YoloBuildProfile.components\(\)/)
  assert.match(read(root + 'ui/camera/yolo/YoloBuildProfile.kt'), /KnownYoloModels.EXP03 -> Exp03YoloContract.components\(YoloBitmapPreprocessor\(\)\)/)
  assert.match(factory, /if \(!BuildConfig.DEBUG\) return null/)
  assert.match(read(root + 'domain/detection/YoloExerciseDetector.kt'), /runtime\?\.modelId != contract.modelId/)
})
test('historical codec requires exact known identity pairs, not current defaults', () => {
  const codec = read(root + 'data/YoloSessionDiagnosticsCodec.kt')
  assert.match(codec, /KnownYoloModels.matches\(modelId, modelSha256\)/)
  assert.match(codec, /fields.keys.containsAll\(listOf\("modelId", "modelSha256"\)\)/)
  assert.doesNotMatch(codec, /modelId != YoloModelPolicy.MODEL_ID/)
})
test('legacy Golden harnesses keep EXP02 identity and native model, not EXP03', () => {
  for (const name of ['Exp02ActualModelGoldenTest', 'Exp02PositiveModelGoldenTest']) {
    const source = read(`app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/${name}.kt`)
    assert.match(source, /TfliteYoloRuntime\(DebugYoloModelSource.open\(context, KnownYoloModels.EXP02\), KnownYoloModels.EXP02\)/)
    assert.match(source, /KnownYoloModels.EXP02.sha256/)
  }
})
test('EXP03 provisioning reuses binary transport and confines writes to private noBackup', () => {
  const ps = read('scripts/provision-exp03-model.ps1')
  for (const token of ['Invoke-PrivateAdb', 'iflag=count_bytes', 'count=$canonicalBytes', 'conv=fsync', "'run-as'", "'sha256sum'", "'readlink'", "'mv'", "'chmod', '600'", 'ValidateLocalOnly']) assert.ok(ps.includes(token), token)
  assert.match(ps, /\$online.Count -ne 1/)
  assert.doesNotMatch(ps, /\/sdcard|\/storage\/|Invoke-WebRequest|DownloadFile|uninstall|pm clear|adb root|Remove-Item|Copy-Item|ToBase64String/)
})
test('native smoke cannot masquerade as Golden and candidates report current reviewed state', () => {
  const smoke = read('app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp03NativeContractDeviceTest.kt')
  assert.match(smoke, /SYNTHETIC_SOLID_COLOR_NOT_CAMERA_NOT_GOLDEN/)
  assert.match(smoke, /contractPromotionPerformed", false/)
  assert.match(smoke, /DetectorStatus.READY/)
  assert.match(smoke, /submits NO frames/)
  const candidate = read('app/src/debug/java/com/example/arptapp/ui/camera/yolo/validation/DebugPositiveNativeInference.kt')
  assert.match(candidate, /"productionContract" to YoloBuildProfile.components\(\).verification.name/)
})
