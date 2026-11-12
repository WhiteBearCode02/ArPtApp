import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
const read = p => readFileSync(p, 'utf8')
test('bundled model is explicit opt-in debug source, never default or comparison', () => {
  const gradle = read('app/build.gradle.kts')
  assert.match(gradle, /gradleProperty\("privateExp02Apk"\).orNull == "true"/)
  assert.match(gradle, /if \(privateExp02Apk\) getByName\("debug"\).assets.srcDir/)
  // Default false plus the three explicitly isolated comparison variants (including EXP04).
  assert.equal((gradle.match(/"YOLO_PRIVATE_BUNDLED", "false"/g) || []).length, 4)
  for (const variant of ['exp02Debug', 'exp03Debug', 'exp04Debug']) {
    assert.match(gradle.slice(gradle.indexOf(`create("${variant}")`)).split('\n        }')[0], /"YOLO_PRIVATE_BUNDLED", "false"/)
  }
  assert.match(gradle, /\*\.tflite:\*\.pt:\*\.pth:\*\.onnx/)
  assert.match(gradle, /464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749/)
})
test('private bundled runtime pins EXP02 and validates bytes before native inference', () => {
  const runtime = read('app/src/main/java/com/example/arptapp/ui/camera/yolo/TfliteYoloRuntime.kt')
  assert.match(runtime, /check\(BuildConfig.DEBUG\)/)
  assert.match(runtime, /!BuildConfig.YOLO_AB_COMPARISON && identity == KnownYoloModels.EXP02/)
  assert.match(runtime, /openFd\("yolo_private\/exp02.bin"\)/)
  assert.match(runtime, /asset.declaredLength != identity.bytes/)
  assert.match(runtime, /YoloModelIdentity.validate\(it, identity\)/)
  assert.match(read('docs/private-exp02-apk-notice.txt'), /71422/)
  assert.match(read('licenses/ULTRALYTICS_AGPL-3.0.txt'), /GNU AFFERO GENERAL PUBLIC LICENSE/)
})
