import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const read = path => readFileSync(path, 'utf8')
const root = 'app/src/main/java/com/example/arptapp/'

test('A/B adds two debug-derived build types without flavoring or renaming production release', () => {
  const gradle = read('app/build.gradle.kts')
  for (const [variant, suffix, title] of [['exp02Debug', '.exp02', 'AIRPTCoach V1 - EXP02'], ['exp03Debug', '.exp03', 'AIRPTCoach V2 - EXP03']]) {
    const block = gradle.slice(gradle.indexOf(`create("${variant}")`), gradle.indexOf('matchingFallbacks', gradle.indexOf(`create("${variant}")`)))
    assert.match(block, /initWith\(getByName\("debug"\)\)/)
    assert.ok(block.includes(`applicationIdSuffix = "${suffix}"`))
    assert.ok(block.includes(title))
    assert.match(block, /"YOLO_AB_COMPARISON", "true"/)
  }
  assert.match(gradle, /applicationId = "com.example.arptapp"/)
  assert.match(gradle, /release \{\s*buildConfigField\("boolean", "YOLO_SHADOW_ENABLED", "false"\)/)
  assert.doesNotMatch(gradle, /productFlavors|flavorDimensions/)
  assert.match(gradle, /java.srcDir\("src\/debug\/java"\)/)
  assert.match(gradle, /testBuildType = selectedTestBuild/)
})

test('compiled identity is shared by factory, validated native source, collector, manual tool and live label', () => {
  const profile = read(root + 'ui/camera/yolo/YoloBuildProfile.kt')
  assert.match(profile, /YoloBuildSelection.identity\(BuildConfig.YOLO_MODEL_VARIANT/)
  assert.match(profile, /KnownYoloModels.EXP02 -> Exp02YoloContract.components\(Exp02BitmapPreprocessor\(\)\)/)
  assert.match(profile, /KnownYoloModels.EXP03 -> Exp03YoloContract.components\(YoloBitmapPreprocessor\(\)\)/)
  assert.doesNotMatch(profile, /Intent|SharedPreferences|getenv|getProperty/)
  assert.match(read(root + 'ui/camera/yolo/TfliteYoloRuntime.kt'), /TfliteYoloRuntime\(DebugYoloModelSource.open\(app, identity\), identity\)/)
  assert.match(read(root + 'DashboardActivity.kt'), /YoloSessionDiagnosticsCollector\(com.example.arptapp.ui.camera.yolo.YoloBuildProfile.identity\)/)
  assert.match(read(root + 'domain/detection/DetectorComparison.kt'), /YOLO SHADOW \$\{data.modelId\}/)
  const manual = read('app/src/debug/java/com/example/arptapp/ui/camera/yolo/validation/DebugPositiveNativeInference.kt')
  assert.match(manual, /"modelId" to YoloBuildProfile.identity.modelId/)
  assert.match(manual, /contract.preprocessor.preprocess\(bitmap\)/)
})

test('comparison Auth cannot intercept original deep links and retains real password login', () => {
  const manifest = read('app/src/abComparison/AndroidManifest.xml')
  assert.equal((manifest.match(/tools:node="removeAll"/g) ?? []).length, 2)
  assert.equal((manifest.match(/tools:replace="android:exported"/g) ?? []).length, 2)
  assert.doesNotMatch(manifest, /android:scheme|android:host/)
  const login = read(root + 'MainActivity.kt')
  assert.match(login, /loginViewModel.signIn\(email, password\)/)
  assert.match(login, /btnGoogleLogin.isEnabled = enabled && !BuildConfig.YOLO_AB_COMPARISON/)
  assert.match(login, /tvForgotPassword.isEnabled = enabled && !BuildConfig.YOLO_AB_COMPARISON/)
  assert.doesNotMatch(login, /AuthSessionStore.current\s*=/)
})

test('both comparison apps keep workouts local without changing canonical sync or schemas', () => {
  assert.match(read(root + 'data/history/WorkoutHistoryProvider.kt'), /enabled = \{ !BuildConfig.YOLO_AB_COMPARISON \}/)
  const controls = read('app/src/debug/java/com/example/arptapp/ui/dashboard/DebugPositiveValidationControls.kt')
  assert.match(controls, /isChecked = BuildConfig.YOLO_AB_COMPARISON/)
  assert.match(controls, /localOnly.isEnabled = !active && !BuildConfig.YOLO_AB_COMPARISON/)
  assert.match(read(root + 'DashboardActivity.kt'), /primaryExerciseDetector = LandmarkExerciseDetector\(fallbackExerciseClassifier\)/)
})

test('A/B provisioning uses one strict transport with a closed model-to-package mapping', () => {
  const wrapper = read('scripts/provision-yolo-ab-model.ps1')
  assert.match(wrapper, /ValidateSet\('EXP02', 'EXP03'\)/)
  assert.match(wrapper, /ComparisonApp = \$true/)
  assert.match(wrapper, /provision-exp03-model.ps1/)
  const transport = read('scripts/provision-exp03-model.ps1')
  for (const token of ['9894030L', '9894032L', '464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749', '3d2b6bc537d8aef5a19b671226d73c98b49411574a193be72e225086202d1167', '[Regex]::Escape($packageName)', 'iflag=count_bytes', 'Assert-Exp03PrivatePath', "'mv'", "'sha256sum'"]) assert.ok(transport.includes(token), token)
  assert.doesNotMatch(wrapper + transport, /\/sdcard|Invoke-WebRequest|DownloadFile|uninstall|pm clear|Copy-Item|Remove-Item/)
})

test('device verification uses actual compiled factory but synthetic input never becomes camera evidence', () => {
  const device = read('app/src/androidTest/java/com/example/arptapp/ui/camera/yolo/YoloAbRuntimeDeviceTest.kt')
  for (const token of ['expectedVariant', 'context.packageName', 'contract.preprocessor.javaClass', 'DebugYoloShadowFactory.create', 'runner.trySubmit', 'stats.processedFrames', 'SYNTHETIC_SOLID_COLOR_NOT_CAMERA_NOT_GOLDEN', 'userDatabaseReadOrWritten']) assert.ok(device.includes(token), token)
  assert.doesNotMatch(device, /AppDatabase|AuthSessionStore|openCamera|uploadSession/)
})
