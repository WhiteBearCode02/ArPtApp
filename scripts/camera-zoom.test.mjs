import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

const read = path => readFileSync(path, "utf8")
const camera = file => read(`app/src/main/java/com/example/arptapp/ui/camera/${file}.kt`)

test("zoom uses the current bound CameraX camera, real zoomState and lifecycle detach", () => {
  const source = camera("CameraXZoomController")
  for (const token of ["boundCamera.cameraControl.setZoomRatio(ratio)", "boundCamera.cameraInfo.zoomState",
    "it.minZoomRatio", "it.maxZoomRatio", "live.observe(owner, observer)", "removeObserver(it)",
    "Lifecycle.State.STARTED", "camera === boundCamera"])
    assert.ok(source.includes(token), token)
  assert.ok(source.indexOf("session.unbind()") < source.indexOf("removeObserver(it)"))
})

test("camera binding retains native camera and invalidates zoom before use-case unbind", () => {
  const source = camera("PoseCameraController")
  assert.match(source, /val boundCamera = provider\.bindToLifecycle[\s\S]*zoomController\.bind\(boundCamera\)/)
  assert.match(source, /zoomController\.unbind\(\)\s+provider\.unbindAll\(\)/)
  assert.match(source, /override fun close\(\)[\s\S]*zoomController\.close\(\)/)
})

test("zoom is device-bounded and UI remains camera-observed rather than optimistic", () => {
  const source = camera("CameraZoomPolicy")
  assert.match(source, /coerceIn\(state\.minZoomRatio, state\.maxZoomRatio\)/)
  assert.match(source, /binding != generation/)
  assert.match(source, /request == requestId/)
  const submit = source.slice(source.indexOf("override fun setZoomRatio"), source.indexOf("override fun scaleZoom"))
  assert.doesNotMatch(submit, /onStateChanged\(/)
})

test("pinch is exactly two-finger, controls excluded and child cancelled once on claim", () => {
  const source = camera("CameraZoomGestureLayout")
  assert.match(source, /pointerCount == 2/)
  assert.match(source, /allPointersOnCamera\(event\)/)
  assert.match(source, /getGlobalVisibleRect\(visibleBounds\)/)
  assert.match(source, /super\.dispatchTouchEvent\(cancel\)/)
  assert.match(source, /isQuickScaleEnabled = false/)
  assert.match(source, /isStylusScaleEnabled = false/)
  assert.match(source, /onDetachedFromWindow\(\)[\s\S]*cancelGesture\(\)/)
})

test("zoom UI is rounded, accessible, resettable and releases its callbacks", () => {
  const source = read("app/src/main/java/com/example/arptapp/ui/dashboard/CameraZoomUi.kt")
  assert.match(source, /control\.scaleZoom\(it\)/)
  assert.match(source, /control\.resetZoom\(\)/)
  assert.match(source, /rounded == displayedRatio/)
  assert.match(source, /Locale\.ROOT, "%.1f"/)
  assert.match(source, /contentDescription/)
  assert.match(source, /onScaleFactor = null/)
  assert.match(source, /protectedViews = emptyList\(\)/)
})

for (const file of ["CameraZoomPolicy", "CameraXZoomController", "CameraZoomGestureLayout"]) {
  test(`${file} has no zoom bitmap/view scaling, storage, network or debug-only gating`, () => {
    const source = camera(file).replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/[^\n]*/g, "")
    assert.doesNotMatch(source, /scale[XY]|Bitmap|postScale|URL\(|File\(|http|Supabase|BuildConfig|Yolo|ExerciseType/)
  })
}

for (const folder of ["layout", "layout-land"]) {
  test(`${folder} keeps zoom chip in ordered body, not header/footer/clear-space`, () => {
    const xml = read(`app/src/main/res/${folder}/activity_dashboard.xml`).replace(/<!--[\s\S]*?-->/g, "")
    assert.match(xml, /<com\.example\.arptapp\.ui\.camera\.CameraZoomGestureLayout/)
    const stack = [], ancestors = new Map()
    for (const match of xml.matchAll(/<([^>]+)>/g)) {
      const tag = match[1].trim()
      if (tag.startsWith("?")) continue
      if (tag.startsWith("/")) { stack.pop(); continue }
      const id = tag.match(/android:id="@\+id\/([^"]+)"/)?.[1]
      if (id) ancestors.set(id, [...stack])
      if (!tag.endsWith("/")) stack.push(id)
    }
    const path = ancestors.get("btnZoomReset")
    assert.ok(path.includes("layoutDashboardContent"))
    for (const id of ["layoutCameraHeader", "layoutWorkoutFooter", "layoutCameraActions", "layoutCameraClearSpace"])
      assert.ok(!path.includes(id), `${id} remains separate`)
    assert.ok(xml.indexOf('id="@+id/btnZoomReset"') < xml.indexOf('id="@+id/layoutCameraClearSpace"'))
    assert.match(xml, /style="@style\/Widget\.AIRPTCoach\.Dashboard\.ZoomChip"/)
  })
}

test("small zoom chip has 48dp target and debug-only verification host is not exported", () => {
  const xml = read("app/src/main/res/values/camera_zoom.xml")
  assert.match(xml, /name="android:minHeight">48dp/)
  assert.match(xml, /name="camera_zoom_reset_description"/)
  const debug = read("app/src/debug/AndroidManifest.xml")
  assert.match(debug, /CameraZoomVerificationActivity" android:exported="false"/)
  assert.doesNotMatch(debug, /intent-filter|uses-permission/)
  assert.doesNotMatch(read("app/src/main/AndroidManifest.xml"), /CameraZoomVerificationActivity/)
})

test("Dashboard protects every action area and disposes zoom without analysis routing", () => {
  const source = read("app/src/main/java/com/example/arptapp/DashboardActivity.kt")
  const setup = source.slice(source.indexOf("cameraZoomUi = CameraZoomUi"), source.indexOf("cameraController.initialize"))
  for (const id of ["layoutCameraHeader", "layoutSetupOptions", "layoutWorkoutSummary", "yoloDiagnosticsPanel",
    "layoutWorkoutFooter", "layoutCameraActions", "btnZoomReset"])
    assert.ok(setup.includes(`binding.${id}`), id)
  assert.match(source, /cameraZoomUi\?\.cancelGesture\(\)/)
  assert.match(source, /cameraZoomUi\?\.close\(\)/)
  assert.doesNotMatch(source, /cameraControl\.setZoomRatio|scale[XY]\s*=/)
})
