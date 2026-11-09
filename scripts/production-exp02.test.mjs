import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const read = path => readFileSync(path, 'utf8')
const root = 'app/src/main/java/com/example/arptapp/'

test('normal app is compile-time pinned to EXP02 while explicit experimental comparison remains', () => {
  const gradle = read('app/build.gradle.kts')
  const defaults = gradle.slice(gradle.indexOf('defaultConfig {'), gradle.indexOf('buildTypes {'))
  assert.ok(defaults.includes('"YOLO_MODEL_VARIANT", "\\"EXP02\\""'))
  const policy = read(root + 'domain/detection/YoloExportContract.kt')
  assert.match(policy, /const val MODEL_VERSION = "EXP02"/)
  assert.match(policy, /const val MODEL_ID = "yolo26n_exp02_fp32"/)
  assert.match(policy, /val identity get\(\) = KnownYoloModels.EXP02/)
  assert.match(read(root + 'domain/detection/YoloBuildSelection.kt'), /variant == YoloModelPolicy.MODEL_VERSION/)
  assert.match(gradle, /create\("exp03Debug"\)/)
  assert.match(gradle, /release \{\s*buildConfigField\("boolean", "YOLO_SHADOW_ENABLED", "false"\)/)
})

test('known EXP03 evidence is explicitly model-bound after final selection changes', () => {
  const smoke = read('app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/Exp03NativeContractDeviceTest.kt')
  assert.match(smoke, /TfliteYoloRuntime\(DebugYoloModelSource.open\(context, KnownYoloModels.EXP03\), KnownYoloModels.EXP03\)/)
  assert.match(smoke, /"sha256", KnownYoloModels.EXP03.sha256/)
  assert.doesNotMatch(smoke, /YoloModelPolicy.CANONICAL_(SHA256|MODEL_BYTES)/)
})

test('debug-only loaded log is based on observed native metadata and selected file', () => {
  const native = read(root + 'ui/camera/yolo/TfliteYoloRuntime.kt')
  assert.match(native, /BuildConfig.DEBUG && !metadataLogged && observed.matchesExpected\(\)/)
  assert.match(native, /AIRPTCoach model loaded/)
  assert.match(native, /file=\$\{identity.fileName\}/)
  assert.match(native, /classes=\$\{YoloModelPolicy.classMapping\}/)
  const dashboard = read(root + 'DashboardActivity.kt')
  assert.match(dashboard, /Production Model/)
  assert.match(dashboard, /Model File: \$\{modelProfile.fileName\}/)
  assert.match(dashboard, /primaryExerciseDetector = LandmarkExerciseDetector\(fallbackExerciseClassifier\)/)
})

test('final-app provisioning cannot select EXP03 and reuses exact private transport', () => {
  const wrapper = read('scripts/provision-production-exp02.ps1')
  assert.match(wrapper, /ModelVariant = 'EXP02'; ProductionApp = \$true/)
  assert.match(wrapper, /provision-exp03-model.ps1/)
  assert.doesNotMatch(wrapper, /\[string\]\$PackageName|DownloadFile|Invoke-WebRequest|Copy-Item|uninstall|pm clear/)
  const common = read('scripts/provision-exp03-model.ps1')
  assert.match(common, /\$ComparisonApp -and \$ProductionApp/)
  assert.match(common, /\$ProductionApp -and \$ModelVariant -cne 'EXP02'/)
  for (const token of ['[Regex]::Escape($packageName)', 'iflag=count_bytes', 'conv=fsync', 'Assert-Exp03PrivatePath', "'sha256sum'", "'mv'"]) assert.ok(common.includes(token), token)
})
