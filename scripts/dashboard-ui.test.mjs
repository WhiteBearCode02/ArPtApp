import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

const javaRoot = "app/src/main/java/com/example/arptapp/"
const read = path => readFileSync(path, "utf8")
const dashboard = () => read(javaRoot + "DashboardActivity.kt")

// Resolve this UI's shared styles too: a reusable minHeight is just as real as an inline one.
const dashboardStyles = new Map([...read("app/src/main/res/values/dashboard_ui.xml")
  .matchAll(/<style\s+name="([^"]+)"(?:\s+parent="([^"]*)")?\s*>([\s\S]*?)<\/style>/g)]
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

test("dashboard dotted styles declare their parent explicitly, including no-parent text styles", () => {
  const source = read("app/src/main/res/values/dashboard_ui.xml")
  for (const match of source.matchAll(/<style\s+name="([^"]+)"([^>]*)>/g)) {
    if (match[1].includes(".")) assert.match(match[2], /\bparent="[^"]*"/,
      `${match[1]} must not infer a nonexistent dotted parent`)
  }
  assert.equal(dashboardStyles.get("Widget.AIRPTCoach.Dashboard.SummaryText")?.parent, "")
})

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
  test(`${configuration}: full-screen camera and fixed header are separate from scrolling controls`, () => {
    const ui = layout(path)
    for (const id of ["viewFinder", "overlayView"]) {
      const node = ui.byId(id)
      assert.equal(node.attrs["android:layout_width"], "match_parent")
      assert.equal(node.attrs["android:layout_height"], "match_parent")
    }
    const chrome = ui.byId("dashboardChrome")
    const headerNode = ui.byId("layoutCameraHeader")
    const header = ui.descendants(headerNode)
    for (const id of ["btnClose", "tvDashboardTitle", "tvCameraLens", "btnSwitchCamera"]) {
      assert.ok(header.includes(ui.byId(id)), `header owns ${id}`)
    }
    const scroll = ui.byId("scrollDashboardContent")
    const actions = ui.byId("layoutCameraActions")
    for (const node of [headerNode, scroll, actions]) {
      assert.ok(chrome.children.includes(node), "header, body and CTA are independent chrome regions")
    }
    assert.equal(headerNode.attrs["app:layout_constraintTop_toTopOf"], "parent")
    assert.equal(scroll.attrs["android:layout_height"], "0dp", "content scroll is constrained, not unbounded")
    assert.equal(scroll.attrs["app:layout_constraintTop_toBottomOf"], "@id/layoutCameraHeader")
    assert.equal(scroll.attrs["app:layout_constraintBottom_toTopOf"], "@id/layoutCameraActions")
    const content = ui.descendants(scroll)
    assert.ok(!content.includes(headerNode), "the header cannot scroll under another overlay")
    assert.ok(!content.includes(actions), "primary CTA never scrolls away behind diagnostics")
    const buttonsInHeader = header.filter(node => /(?:Button)$/.test(node.name))
    assert.deepEqual(buttonsInHeader, [ui.byId("btnClose"), ui.byId("btnSwitchCamera")],
      "only back and camera-switch controls are in the header")
    assert.ok(!header.includes(ui.byId("btnYoloDiagnosticsToggle")), "diagnostics cannot cover header controls")
    assert.equal(headerNode.attrs["android:orientation"], "horizontal")
    assert.equal(headerNode.children[0], ui.byId("btnClose"))
    assert.equal(headerNode.children.at(-1), ui.byId("btnSwitchCamera"))
    const titleColumn = headerNode.children[1]
    assert.equal(titleColumn.attrs["android:orientation"], "vertical")
    assert.deepEqual(titleColumn.children, [ui.byId("tvDashboardTitle"), ui.byId("tvCameraLens")],
      "the camera state is directly beneath the centered title")
    assert.equal(scroll.attrs["android:clipChildren"], "true")
    if (configuration === "layout-land") {
      assert.equal(scroll.attrs["app:layout_constraintStart_toStartOf"], "@id/dashboardPanelGuide")
      assert.ok(parseFloat(ui.byId("dashboardPanelGuide").attrs["app:layout_constraintGuide_percent"]) >= 0.5,
        "landscape leaves at least half of the preview outside the controls pane")
      assert.equal(actions.attrs["app:layout_constraintStart_toStartOf"],
        scroll.attrs["app:layout_constraintStart_toStartOf"], "fixed CTA belongs to the same right pane")
    }
    assert.equal(actions.attrs["app:layout_constraintBottom_toBottomOf"], "parent")
    const actionChildren = ui.descendants(actions)
    assert.ok(actionChildren.includes(ui.byId("btnStartExercise")))
    assert.ok(actionChildren.includes(ui.byId("btnEndExercise")))
    const setup = ui.descendants(ui.byId("layoutSetupOptions"))
    assert.ok(setup.indexOf(ui.byId("btnCameraView")) < setup.indexOf(ui.byId("btnPersonalReference")))
    assert.equal(ui.byId("layoutSetupOptions").name, "com.example.arptapp.ui.dashboard.CompactSetupLayout")
    assert.equal(ui.byId("layoutSetupOptions").attrs["android:orientation"], "horizontal")
    for (const id of ["btnCameraView", "btnPersonalReference"]) {
      const attrs = resolvedAttributes(ui.byId(id))
      assert.equal(attrs["android:layout_width"], "wrap_content", `${id} is not a full-width list row`)
      assert.equal(attrs["android:singleLine"], "false", `${id} can wrap large/long text`)
      assert.equal(attrs["android:textSize"], "12sp")
      assert.equal(attrs.icon, undefined, "secondary chips have no large navigation chevron")
    }
  })

  test(`${configuration}: compact setup directly precedes count in lower footer, not camera center`, () => {
    const ui = layout(path)
    const body = ui.byId("layoutDashboardContent")
    assert.equal(body.attrs["android:orientation"], "vertical")
    const ordered = ["layoutDetectedExercise", "layoutWorkoutSummary",
      "layoutCameraClearSpace", "yoloDiagnosticsPanel", "layoutWorkoutFooter"].map(id => body.children.indexOf(ui.byId(id)))
    assert.ok(ordered.every(index => index >= 0), "all sections are direct, stacked body children")
    assert.deepEqual([...ordered].sort((a, b) => a - b), ordered)
    assert.equal(ui.byId("layoutWorkoutSummary").attrs["android:visibility"], "gone")
    assert.equal(ui.byId("layoutCameraClearSpace").children.length, 0,
      "no text or debug overlay covers the middle camera spacer")
    assert.ok(parseFloat(ui.byId("layoutCameraClearSpace").attrs["android:layout_weight"]) > 0,
      "spare height is reserved for unobstructed camera preview")
    const footer = ui.byId("layoutWorkoutFooter")
    assert.equal(footer.attrs["android:orientation"], "vertical")
    const row = ui.byId("layoutCountAndDiagnostics")
    const setup = ui.byId("layoutSetupOptions")
    assert.deepEqual(footer.children.slice(0, 2), [setup, row], "no spacer/debug/card between settings and count")
    assert.ok(!body.children.includes(setup), "large upper setup card is removed")
    assert.equal(setup.attrs["android:background"], undefined, "no large enclosing card background")
    assert.ok(parseFloat(setup.attrs["android:layout_marginBottom"]) <= 8)
    assert.ok(parseFloat(setup.attrs["android:layout_marginTop"]) <= 8)
    assert.ok(footer.children.indexOf(row) < footer.children.indexOf(ui.byId("tvPoseFeedback")),
      "count comes before guidance; the fixed CTA is below this bounded footer")
    assert.ok(ui.descendants(row).includes(ui.byId("layoutCount")))
    assert.ok(ui.descendants(row).includes(ui.byId("btnYoloDiagnosticsToggle")))
    assert.equal(ui.byId("tvPoseFeedback").attrs["android:accessibilityLiveRegion"], "polite")
  })

  test(`${configuration}: primary controls are accessible and no diagnostic detail exists on main screen`, () => {
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
    assert.equal(ui.byId("btnYoloDiagnosticsToggle").attrs["android:visibility"], "gone")
    assert.equal(ui.byId("yoloDiagnosticsPanel").attrs["android:visibility"], "gone")
    assert.doesNotMatch(read(path), /@\+id\/(?:layoutYoloDiagnosticsDetails|tvYoloDiagnostics|tvPerformance)\b/,
      "raw/stable/FPS/latency detail belongs only to the opt-in diagnostic panel")
  })
}

test("personal reference uses expandable vertical rows with one separate bottom cancel", () => {
  const ui = layout("app/src/main/res/layout/dialog_personal_reference.xml")
  const options = ui.byId("referenceOptions")
  const cancel = ui.byId("btnCancelReference")
  assert.equal(options.attrs["android:orientation"], "vertical")
  assert.ok(ui.all.indexOf(options) < ui.all.indexOf(cancel))
  assertTouchTarget(cancel, "cancel")
  const row = layout("app/src/main/res/layout/item_personal_reference_option.xml")
  assertTouchTarget(row.byId("referenceOptionRow"), "reusable exercise row")
  assert.equal(row.byId("referenceOptionRow").attrs["android:focusable"], "true")
  row.byId("tvReferenceOptionTitle")
  row.byId("tvReferenceOptionSubtitle")
  const dialog = read(javaRoot + "ui/dashboard/PersonalReferenceDialog.kt")
  assert.match(dialog, /DashboardUiPolicy\.referenceExerciseOptions\.forEach/)
  assert.match(dialog, /ItemPersonalReferenceOptionBinding\.inflate/)
  assert.match(dialog, /referenceOptions\.addView\(row\.root\)/)
  assert.match(dialog, /row\.root\.setOnClickListener\s*\{\s*select\(type\)/)
  assert.match(dialog, /btnCancelReference\.setOnClickListener/)
  assert.doesNotMatch(dialog, /setPositiveButton|setNeutralButton|setNegativeButton/)
  assert.doesNotMatch(dialog, /referenceExerciseOptions\[[01]\]|btnReferenceSquat|btnReferenceShoulderPress/)
  assert.match(dialog, /if \(selected \|\| !dialog\.isShowing\) return/)
  const policy = read(javaRoot + "ui/dashboard/DashboardUiPolicy.kt")
  assert.match(policy, /listOf\(ExerciseType\.SQUAT, ExerciseType\.SHOULDER_PRESS\)/)
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

test("presentation state swaps setup for summary without mutating a session", () => {
  const source = dashboard()
  assert.match(source, /DashboardUiPolicy\.displayState\(/)
  assert.match(source, /layoutSetupOptions\.visibility\s*=\s*if\s*\([^)]*\.showSetup\)/)
  assert.match(source, /layoutWorkoutSummary\.visibility\s*=\s*if\s*\([^)]*\.showWorkoutSummary\)/)
  const policy = read(javaRoot + "ui/dashboard/DashboardUiPolicy.kt")
  assert.match(policy, /isExercising -> DashboardDisplayState\(showSetup = false, showWorkoutSummary = true\)/)
  assert.doesNotMatch(policy, /viewModel|resetSession|observeDetectedExercise|startExercise\(|save\(|remove\(/)
  assert.match(source, /WindowInsetsCompat\.Type\.systemBars\(\) or WindowInsetsCompat\.Type\.displayCutout\(\)/,
    "bottom CTA respects system bars and cutout insets")
})

test("report dismissal cannot leave an idle screen stranded on the End action", () => {
  const source = dashboard()
  const end = source.slice(source.indexOf("private fun endExercise()"), source.indexOf("private fun restorePreparationUi()"))
  assert.equal((end.match(/restorePreparationUi\(\)/g) ?? []).length, 2,
    "both insufficient-report and completed-report paths restore presentation state")
  assert.ok(end.lastIndexOf("restorePreparationUi()") < end.indexOf("showSessionReport(report)"))
  const restore = source.slice(source.indexOf("private fun restorePreparationUi()"), source.indexOf("private fun showSessionReport("))
  assert.match(restore, /btnStartExercise\.visibility = View.VISIBLE/)
  assert.match(restore, /btnEndExercise\.visibility = View.GONE/)
  assert.match(restore, /applyDashboardControlState\(\)/)
  assert.doesNotMatch(restore, /resetSession|reset\(|remove\(|uploadSession|generateLockedSessionReport/)
})

test("opt-in allocated diagnostic panel stays DEBUG-only without feeding the primary exercise classifier", () => {
  const source = dashboard()
  assert.match(source, /btnYoloDiagnosticsToggle\.visibility = if \(BuildConfig\.DEBUG\) View\.VISIBLE else View\.GONE/)
  assert.match(source, /btnYoloDiagnosticsToggle\.setOnClickListener/)
  assert.match(source, /yoloDiagnosticsPanel\.setExpanded\(/)
  const panel = read(javaRoot + "ui/dashboard/YoloDiagnosticsPanel.kt")
  assert.match(panel, /FrameLayout/)
  assert.match(panel, /BuildConfig\.DEBUG/)
  assert.doesNotMatch(panel, /BottomSheetDialog|PopupWindow|AlertDialog/,
    "diagnostics use allocated layout height, never cover count, guidance or fixed CTA")
  const details = layout("app/src/main/res/layout/view_yolo_diagnostics_panel.xml")
  details.byId("tvYoloDiagnostics")
  details.byId("tvPerformance")
  const viewport = details.all.find(node => node.name === "androidx.core.widget.NestedScrollView")
  assert.ok(viewport, "long diagnostics have their own bounded scroll viewport")
  assert.ok(parseFloat(viewport.attrs["android:layout_height"]) <= 160)
  assert.equal(viewport.attrs["android:clipChildren"], "true")
  assert.match(panel, /fun setExpanded\(/)
  assert.match(panel, /fun updateDiagnostics\(/)
  assert.match(panel, /fun updatePerformance\(/)
  assert.match(source, /primaryExerciseDetector = LandmarkExerciseDetector\(fallbackExerciseClassifier\)/)
  assert.match(source, /updateCurrentExerciseType\(primaryExerciseDetector\.detect\(LandmarkDetectionFrame/)
  assert.doesNotMatch(source, /observeDetectedExercise\(yolo|updateCurrentExerciseType\(.*yolo/i)
})

test("new dashboard presentation modules add no API calls, credentials or billable providers", () => {
  for (const file of ["ui/dashboard/DashboardUiPolicy.kt", "ui/dashboard/PersonalReferenceDialog.kt",
    "ui/dashboard/YoloDiagnosticsPanel.kt", "ui/dashboard/CompactSetupLayout.kt"]) {
    assert.doesNotMatch(read(javaRoot + file),
      /https?:\/\/|HttpClient|OkHttp|Retrofit|SUPABASE|LLM_API_KEY|accessToken|FileOutputStream|writeBytes/)
  }
})

test("compact settings choose a row or content-height column without changing domain actions", () => {
  const source = read(javaRoot + "ui/dashboard/CompactSetupLayout.kt")
  assert.match(source, /measureChildWithMargins/)
  assert.match(source, /rowWidth > available/)
  assert.match(source, /VERTICAL else HORIZONTAL/)
  assert.match(source, /child.visibility == View.GONE/)
  assert.doesNotMatch(source, /setOnClickListener|CameraView|ExerciseType|CameraControl|save\(|reset\(/)
  const activity = dashboard()
  assert.match(activity, /dashboard_camera_view_compact_value/)
  assert.match(activity, /btnCameraView\.setOnClickListener/)
  assert.match(activity, /btnPersonalReference\.setOnClickListener \{ togglePersonalReference\(\) \}/)
  const dialog = read(javaRoot + "ui/dashboard/PersonalReferenceDialog.kt")
  assert.match(dialog, /R.string.personal_reference_camera_view_value/,
    "compact label does not shorten the full context inside reference dialog")
})
