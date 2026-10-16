import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

const javaRoot = "app/src/main/java/com/example/arptapp/"
const read = path => readFileSync(path, "utf8")
const dashboard = () => read(javaRoot + "DashboardActivity.kt")

// Resolve this UI's shared styles too: a reusable minHeight is just as real as an inline one.
const dashboardStyles = new Map([...read("app/src/main/res/values/dashboard_ui.xml")
  .matchAll(/<style\s+name="([^"]+)"(?:\s+parent="([^"]+)")?\s*>([\s\S]*?)<\/style>/g)]
  .map(match => [match[1], {
    parent: match[2],
    attrs: Object.fromEntries([...match[3].matchAll(/<item\s+name="([^"]+)">([^<]*)<\/item>/g)]
      .map(item => [item[1], item[2].trim()]))
  }]))

function resolvedAttributes(node) {
  const seen = new Set()
  const inherited = name => {
    if (!name || !dashboardStyles.has(name)) return {}
    assert.ok(!seen.has(name), `no cycle in shared dashboard styles: ${name}`)
    seen.add(name)
    const style = dashboardStyles.get(name)
    return { ...inherited(style.parent), ...style.attrs }
  }
  return { ...inherited(node.attrs.style?.replace(/^@style\//, "")), ...node.attrs }
}

// Small structural parser for repository-owned Android layouts: no DOM package or network needed.
// Unlike source-order-only checks, ancestry catches accidentally overlaying independent controls.
function layout(path) {
  const document = { name: "document", attrs: {}, children: [] }
  const stack = [document]
  const source = read(path).replace(/<!--[\s\S]*?-->/g, "")
  for (const match of source.matchAll(/<([^>]+)>/g)) {
    const token = match[1].trim()
    if (token.startsWith("?") || token.startsWith("!")) continue
    if (token.startsWith("/")) {
      assert.equal(stack.pop()?.name, token.slice(1).trim(), `${path}: balanced XML nodes`)
      continue
    }
    const name = token.match(/^[^\s/]+/)?.[0]
    const attrs = Object.fromEntries([...token.matchAll(/([\w:.-]+)="([^"]*)"/g)].map(m => [m[1], m[2]]))
    const node = { name, attrs, children: [] }
    stack.at(-1).children.push(node)
    if (!token.endsWith("/")) stack.push(node)
  }
  assert.equal(stack.length, 1, `${path}: all layout elements close`)
  const flatten = node => [node, ...node.children.flatMap(flatten)]
  const all = flatten(document)
  const byId = id => {
    const found = all.filter(node => node.attrs["android:id"] === `@+id/${id}`)
    assert.equal(found.length, 1, `${path}: unique ${id}`)
    return found[0]
  }
  return { byId, descendants: node => flatten(node), all }
}

function assertTouchTarget(node, label) {
  const attrs = resolvedAttributes(node)
  const dimensions = [attrs["android:layout_height"], attrs["android:minHeight"]]
  assert.ok(dimensions.some(value => /^\d+(?:\.\d+)?dp$/.test(value ?? "") && parseFloat(value) >= 48),
    `${label} has an explicit touch target of at least 48dp`)
}

for (const configuration of ["layout", "layout-land"]) {
  const path = `app/src/main/res/${configuration}/activity_dashboard.xml`
  test(`${configuration}: full-screen camera and separate, non-overlapping dashboard regions`, () => {
    const ui = layout(path)
    for (const id of ["viewFinder", "overlayView"]) {
      const node = ui.byId(id)
      assert.equal(node.attrs["android:layout_width"], "match_parent")
      assert.equal(node.attrs["android:layout_height"], "match_parent")
    }
    const header = ui.descendants(ui.byId("layoutCameraHeader"))
    for (const id of ["btnClose", "tvDashboardTitle", "tvCameraLens", "btnSwitchCamera"]) {
      assert.ok(header.includes(ui.byId(id)), `header owns ${id}`)
    }
    const scroll = ui.byId("scrollDashboardContent")
    assert.equal(scroll.attrs["android:layout_height"], "0dp", "content scroll is constrained, not unbounded")
    const content = ui.descendants(scroll)
    const orderedRegions = configuration === "layout"
      ? ["layoutSetupOptions", "layoutPoseGuide", "layoutCount", "layoutYoloDiagnostics"]
      : ["layoutSetupOptions", "layoutCount", "layoutYoloDiagnostics"]
    const order = orderedRegions
      .map(id => content.indexOf(ui.byId(id)))
    assert.ok(order.every(index => index >= 0), "setup, count and debug are in bounded scrolling content")
    assert.deepEqual([...order].sort((a, b) => a - b), order, "logical region order is preserved")
    if (configuration === "layout-land") {
      assert.ok(!content.includes(ui.byId("layoutPoseGuide")), "landscape preserves a separate camera/guide pane")
      const guideScroll = ui.byId("scrollDashboardGuide")
      assert.ok(ui.descendants(guideScroll).includes(ui.byId("layoutPoseGuide")))
      assert.equal(guideScroll.attrs["android:layout_height"], "0dp", "left guide also scrolls in short windows")
      assert.equal(guideScroll.attrs["app:layout_constraintBottom_toBottomOf"], "parent")
      assert.equal(guideScroll.attrs["app:layout_constraintTop_toBottomOf"], "@id/layoutCameraHeader")
      assert.equal(scroll.attrs["app:layout_constraintStart_toStartOf"],
        guideScroll.attrs["app:layout_constraintEnd_toStartOf"], "left guide and right controls meet at one split guide")
      assert.equal(ui.byId("layoutCameraActions").attrs["app:layout_constraintStart_toStartOf"],
        scroll.attrs["app:layout_constraintStart_toStartOf"], "fixed CTA belongs to the same right pane")
    } else {
      assert.equal(ui.byId("layoutSetupOptions").attrs["app:layout_constraintTop_toBottomOf"], "@id/layoutDetectedExercise")
      assert.equal(ui.byId("layoutPoseGuide").attrs["app:layout_constraintTop_toBottomOf"], "@id/layoutSetupOptions")
      assert.equal(ui.byId("layoutPoseGuide").attrs["app:layout_constraintBottom_toTopOf"], "@id/layoutCount")
      assert.equal(ui.byId("layoutCount").attrs["app:layout_constraintBottom_toTopOf"], "@id/layoutYoloDiagnostics")
      assert.equal(ui.byId("layoutYoloDiagnostics").attrs["app:layout_constraintTop_toBottomOf"], "@id/layoutCount")
    }
    assert.ok(!content.includes(ui.byId("layoutCameraActions")), "CTA is outside scrolling diagnostic content")
    assert.equal(scroll.attrs["app:layout_constraintBottom_toTopOf"], "@id/layoutCameraActions")
    const actions = ui.byId("layoutCameraActions")
    assert.equal(actions.attrs["app:layout_constraintBottom_toBottomOf"], "parent")
    const actionChildren = ui.descendants(actions)
    assert.ok(actionChildren.includes(ui.byId("btnStartExercise")))
    assert.ok(actionChildren.includes(ui.byId("btnEndExercise")))
    const setup = ui.descendants(ui.byId("layoutSetupOptions"))
    assert.ok(setup.indexOf(ui.byId("btnCameraView")) < setup.indexOf(ui.byId("btnPersonalReference")))
    assert.equal(ui.byId("layoutSetupOptions").attrs["android:orientation"], "vertical")
  })

  test(`${configuration}: primary controls are accessible and debug details start hidden`, () => {
    const ui = layout(path)
    for (const id of ["btnClose", "btnSwitchCamera", "btnCameraView", "btnPersonalReference",
      "btnStartExercise", "btnEndExercise", "btnYoloDiagnosticsToggle"]) {
      assertTouchTarget(ui.byId(id), `${configuration}/${id}`)
    }
    for (const id of ["btnClose", "btnSwitchCamera"]) {
      assert.ok(ui.byId(id).attrs["android:contentDescription"], `${id} has a spoken description`)
      assert.ok(parseFloat(resolvedAttributes(ui.byId(id))["android:layout_width"]) >= 48,
        `${id} also has a 48dp-wide icon hit target`)
    }
    assert.equal(ui.byId("layoutYoloDiagnostics").attrs["android:visibility"], "gone")
    assert.equal(ui.byId("layoutYoloDiagnosticsDetails").attrs["android:visibility"], "gone")
    const details = ui.descendants(ui.byId("layoutYoloDiagnosticsDetails"))
    assert.ok(details.includes(ui.byId("tvYoloDiagnostics")))
    assert.ok(details.includes(ui.byId("tvPerformance")))
  })
}

test("personal reference is an ordered two-row selection with one separate bottom cancel", () => {
  const ui = layout("app/src/main/res/layout/dialog_personal_reference.xml")
  const squat = ui.byId("btnReferenceSquat")
  const shoulder = ui.byId("btnReferenceShoulderPress")
  const cancel = ui.byId("btnCancelReference")
  assert.ok(ui.all.indexOf(squat) < ui.all.indexOf(shoulder))
  assert.ok(ui.all.indexOf(shoulder) < ui.all.indexOf(cancel))
  for (const [label, node] of [["squat row", squat], ["shoulder press row", shoulder], ["cancel", cancel]]) {
    assertTouchTarget(node, label)
  }
  const dialog = read(javaRoot + "ui/dashboard/PersonalReferenceDialog.kt")
  assert.match(dialog, /DashboardUiPolicy\.referenceExerciseOptions\[0\]/)
  assert.match(dialog, /DashboardUiPolicy\.referenceExerciseOptions\[1\]/)
  assert.match(dialog, /btnReferenceSquat\.setOnClickListener/)
  assert.match(dialog, /btnReferenceShoulderPress\.setOnClickListener/)
  assert.match(dialog, /btnCancelReference\.setOnClickListener/)
  assert.doesNotMatch(dialog, /setPositiveButton|setNeutralButton|setNegativeButton/)
  assert.match(dialog, /cameraView/)
})

test("dashboard calls the existing recording entry only after presentation admission checks", () => {
  const source = dashboard()
  assert.match(source, /PersonalReferenceDialog\.show\(/)
  assert.match(source, /beginReference\(/)
  assert.match(source, /DashboardUiPolicy\.canBeginReference\(/)
  assert.match(source, /DashboardUiPolicy\.buttonState\(/)
  for (const field of ["cameraViewEnabled", "personalReferenceEnabled", "startExerciseEnabled"]) {
    assert.match(source, new RegExp(`\\.${field}`))
  }
  const begin = source.slice(source.indexOf("private fun beginReference("), source.indexOf("private fun captureReferenceFrame("))
  assert.ok(begin.indexOf("DashboardUiPolicy.canBeginReference(") < begin.indexOf("calibrationExercise = type"),
    "stale dialog selections are checked before changing capture state")
  assert.match(source, /btnPersonalReference\.setOnLongClickListener/)
  assert.match(source, /referenceRepository\.remove\(/)
})

test("collapsible diagnostic stays DEBUG-only without feeding the primary exercise classifier", () => {
  const source = dashboard()
  assert.match(source, /tvYoloDiagnostics\.visibility = if \(BuildConfig\.DEBUG\) View\.VISIBLE else View\.GONE/)
  assert.match(source, /layoutYoloDiagnostics\.visibility = if \(BuildConfig\.DEBUG\) View\.VISIBLE else View\.GONE/)
  assert.match(source, /btnYoloDiagnosticsToggle\.setOnClickListener/)
  const expand = source.slice(source.indexOf("private fun setYoloDiagnosticsExpanded("))
  assert.match(expand, /BuildConfig\.DEBUG/)
  assert.match(expand, /layoutYoloDiagnosticsDetails\.visibility/)
  assert.match(source, /primaryExerciseDetector = LandmarkExerciseDetector\(fallbackExerciseClassifier\)/)
  assert.match(source, /updateCurrentExerciseType\(primaryExerciseDetector\.detect\(LandmarkDetectionFrame/)
  assert.doesNotMatch(source, /observeDetectedExercise\(yolo|updateCurrentExerciseType\(.*yolo/i)
})

test("new dashboard presentation modules add no API calls, credentials or billable providers", () => {
  for (const file of ["ui/dashboard/DashboardUiPolicy.kt", "ui/dashboard/PersonalReferenceDialog.kt"]) {
    assert.doesNotMatch(read(javaRoot + file),
      /https?:\/\/|HttpClient|OkHttp|Retrofit|SUPABASE|LLM_API_KEY|accessToken|FileOutputStream|writeBytes/)
  }
})
