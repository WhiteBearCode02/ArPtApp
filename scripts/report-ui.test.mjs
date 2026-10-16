import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

const root = "app/src/main/java/com/example/arptapp/"
const reportRoot = root + "presentation/report/"
const read = path => readFileSync(path, "utf8")

// Repository-owned XML only; no browser, Android runtime, dependency or network is needed.
function layout(path) {
  const document = { name: "document", attrs: {}, children: [] }
  const stack = [document]
  const source = read(path).replace(/<!--[\s\S]*?-->/g, "")
  for (const match of source.matchAll(/<([^>]+)>/g)) {
    const token = match[1].trim()
    if (token.startsWith("?") || token.startsWith("!")) continue
    if (token.startsWith("/")) {
      assert.equal(stack.pop()?.name, token.slice(1).trim(), `${path}: balanced nodes`)
      continue
    }
    const node = {
      name: token.match(/^[^\s/]+/)?.[0],
      attrs: Object.fromEntries([...token.matchAll(/([\w:.-]+)="([^"]*)"/g)].map(m => [m[1], m[2]])),
      children: []
    }
    stack.at(-1).children.push(node)
    if (!token.endsWith("/")) stack.push(node)
  }
  assert.equal(stack.length, 1, `${path}: every node closes`)
  const flatten = node => [node, ...node.children.flatMap(flatten)]
  const all = flatten(document)
  return {
    all,
    descendants: flatten,
    byId: id => {
      const found = all.filter(n => n.attrs["android:id"] === `@+id/${id}`)
      assert.equal(found.length, 1, `${path}: unique ${id}`)
      return found[0]
    }
  }
}

function attributes(node) {
  const styles = new Map([...read("app/src/main/res/values/report_ui.xml")
    .matchAll(/<style\s+name="([^"]+)"(?:\s+parent="([^"]*)")?\s*>([\s\S]*?)<\/style>/g)]
    .map(m => [m[1], { parent: m[2], attrs: Object.fromEntries([...m[3]
      .matchAll(/<item\s+name="([^"]+)">([^<]*)<\/item>/g)].map(i => [i[1], i[2].trim()])) }]))
  const seen = new Set()
  const inherit = name => {
    if (!name || !styles.has(name)) return {}
    assert.ok(!seen.has(name), `no report style inheritance cycle: ${name}`)
    seen.add(name)
    const style = styles.get(name)
    return { ...inherit(style.parent), ...style.attrs }
  }
  return { ...inherit(node.attrs.style?.replace(/^@style\//, "")), ...node.attrs }
}

function touchTarget(node) {
  const attrs = attributes(node)
  assert.ok([attrs["android:layout_height"], attrs["android:minHeight"]]
    .some(value => /^\d+(?:\.\d+)?dp$/.test(value ?? "") && parseFloat(value) >= 48),
  `${node.attrs["android:id"]}: touch target is at least 48dp`)
}

test("report sections remain in one scroll flow: summary, AI, chart, repetitions, improvement summary", () => {
  const ui = layout("app/src/main/res/layout/activity_report.xml")
  const content = ui.byId("reportContent")
  assert.equal(content.attrs["android:orientation"], "vertical")
  assert.ok(ui.descendants(ui.byId("reportScroll")).includes(content))
  const summary = content.children.find(node => ui.descendants(node).includes(ui.byId("tvTotalCount")))
  assert.match(summary.name, /MaterialCardView$/)
  for (const id of ["tvTotalCount", "tvAvgScore", "tvDuration", "tvCalories", "tvFeedback"]) {
    assert.ok(ui.descendants(summary).includes(ui.byId(id)), `summary card owns ${id}`)
  }
  const sections = [summary, ...["aiExplanationSection", "chart", "repAnalysisContainer", "improvementSummarySection"]
    .map(id => ui.byId(id))]
  for (const node of sections) assert.ok(content.children.includes(node), "no floating report sections")
  const positions = sections.map(node => content.children.indexOf(node))
  assert.deepEqual(positions, [...positions].sort((a, b) => a - b))
  const height = attributes(ui.byId("chart"))["android:layout_height"]
  assert.ok(/^\d+dp$/.test(height) && parseFloat(height) >= 180 && parseFloat(height) <= 220)
  assert.match(ui.byId("chart").name, /com\.github\.mikephil\.charting\.charts\.BarChart$/)
  touchTarget(ui.byId("btnGenerateAi"))
  touchTarget(ui.byId("btnClose"))
  assert.equal(ui.byId("btnGenerateAi").attrs["android:enabled"], "false", "fail closed before binding")
})

test("only metrics collapse: repetition identity, status and summary stay available", () => {
  const ui = layout("app/src/main/res/layout/item_rep_analysis.xml")
  const metrics = ui.byId("metricGroupsContainer")
  assert.equal(metrics.attrs["android:visibility"], "gone")
  for (const id of ["tvRepNumber", "tvRepScore", "tvRepStatus", "tvRepSummary", "strengthsSection",
    "improvementsSection", "prioritySection", "legacySection", "tvRepNotes", "btnToggleMetrics"]) {
    assert.ok(!ui.descendants(metrics).includes(ui.byId(id)), `${id}: never hidden inside metrics`)
  }
  for (const id of ["tvRepNumber", "tvRepScore", "tvRepStatus", "tvRepSummary"]) {
    assert.notEqual(ui.byId(id).attrs["android:visibility"], "gone", `${id}: core content visible by default`)
  }
  touchTarget(ui.byId("btnToggleMetrics"))
  const renderer = read(reportRoot + "RepAnalysisCardRenderer.kt")
  assert.match(renderer, /displayExpanded\(expansion\.isExpanded\(model\.repNumber\)\)/)
  assert.match(renderer, /setOnClickListener \{ displayExpanded\(expansion\.toggle\(model\.repNumber\)\) \}/)
  assert.match(renderer, /metricGroupsContainer\.visibility = visible\(expanded\)/)
  assert.match(renderer, /btnToggleMetrics\.contentDescription/)
  assert.match(renderer, /ViewCompat\.setStateDescription\(card\.btnToggleMetrics/)
  assert.match(renderer, /ViewCompat\.setAccessibilityHeading\(card\.tvRepNumber, true\)/)
})

test("empty optional coaching sections and metric groups are hidden, not fabricated", () => {
  const renderer = read(reportRoot + "RepAnalysisCardRenderer.kt")
  for (const [section, condition] of [
    ["strengthsSection", "model.strengths.isNotEmpty()"],
    ["improvementsSection", "model.improvements.isNotEmpty()"],
    ["prioritySection", "!model.priorityTip.isNullOrBlank()"],
    ["legacySection", "!model.legacyDetail.isNullOrBlank()"],
    ["btnToggleMetrics", "model.metricGroups.isNotEmpty()"]
  ]) assert.ok(renderer.includes(`card.${section}.visibility = visible(${condition})`), section)
  const mapper = read(reportRoot + "RepReportUiModel.kt")
  assert.match(mapper, /rep\.strengths\.filter \{ it in strengthKeys \}/,
    "strengths come only from actual stored supported facts")
  assert.match(mapper, /summary = improvements\.firstOrNull\(\) \?: strengths\.firstOrNull\(\) \?:/)
  assert.ok(mapper.includes("else NO_IMPROVEMENT"), "neutral summary is used only after stored observations")
})

test("long report and metric text wraps without ellipsis or forced single-line labels", () => {
  const paths = ["activity_report.xml", "item_rep_analysis.xml", "item_report_metric_group.xml", "item_report_metric_row.xml"]
  for (const path of paths) {
    const ui = layout("app/src/main/res/layout/" + path)
    for (const node of ui.all.filter(node => /(?:TextView|Button)$/.test(node.name))) {
      const attrs = attributes(node)
      assert.equal(attrs["android:ellipsize"], undefined, `${path}: no ellipsis`)
      assert.notEqual(attrs["android:singleLine"], "true", `${path}: text may wrap`)
      assert.equal(attrs["android:maxLines"], undefined, `${path}: no arbitrary line limit`)
      assert.equal(attrs["android:layout_height"], "wrap_content", `${path}: text height grows with font size`)
    }
  }
  const styleSource = read("app/src/main/res/values/report_ui.xml")
  assert.match(styleSource, /name="android:lineSpacingExtra">[3-9](?:\.\d+)?dp</)
})

test("report and result share stable system-bar and cutout padding without accumulation", () => {
  const insets = read(reportRoot + "ReportWindowInsets.kt")
  assert.match(insets, /WindowCompat\.setDecorFitsSystemWindows\(activity\.window, false\)/)
  assert.match(insets, /WindowInsetsCompat\.Type\.systemBars\(\) or WindowInsetsCompat\.Type\.displayCutout\(\)/)
  assert.match(insets, /setPadding\(left \+ safe\.left, top \+ safe\.top, right \+ safe\.right, bottom \+ safe\.bottom\)/)
  assert.ok(insets.indexOf("val bottom = root.paddingBottom") < insets.indexOf("setOnApplyWindowInsetsListener"))
  assert.match(read(reportRoot + "ReportActivity.kt"), /ReportWindowInsets\.apply\(this, binding\.root\)/)
  assert.match(read(root + "ResultActivity.kt"), /ReportWindowInsets\.apply\(this, binding\.root\)/)
  assert.doesNotMatch(read("app/src/main/res/layout/activity_report.xml"), /fitsSystemWindows="true"/)
})

test("new dotted report styles declare their parent explicitly, including no-parent bases", () => {
  for (const match of read("app/src/main/res/values/report_ui.xml").matchAll(/<style\s+name="([^"]+)"([^>]*)>/g)) {
    assert.match(match[2], /\bparent="[^"]*"/, `${match[1]}: do not infer a nonexistent dotted parent`)
  }
})

test("AI report remains disabled and repository rejects before any transport request", () => {
  const source = read(root + "data/remote/AiCoachingRepository.kt")
  assert.match(source, /object AiCoachingPolicy\s*\{\s*const val ENABLED = false\s*\}/)
  const generate = source.slice(source.indexOf("suspend fun generateWorkoutReport"), source.indexOf("private class SupabaseAiReportTransport"))
  assert.match(generate, /if \(!enabled\) throw AiUnavailableException\(\)/)
  assert.ok(generate.indexOf("if (!enabled)") < generate.indexOf("transport.request("),
    "disabled local policy must reject before the network boundary")
})

test("AI generation is user-confirmed only and both click boundaries recheck the disabled policy", () => {
  const activity = read(reportRoot + "ReportActivity.kt")
  const entry = activity.slice(activity.indexOf("override fun onCreate"), activity.indexOf("private fun setupSummary"))
  assert.doesNotMatch(entry, /\.generate\(/, "opening a report may only bind local data")
  assert.equal((activity.match(/aiViewModel\.generate\(/g) ?? []).length, 1, "one explicit request boundary")
  const listener = activity.slice(activity.indexOf("binding.btnGenerateAi.setOnClickListener"), activity.indexOf("lifecycleScope.launch"))
  assert.match(listener, /if \(!canGenerateAi\(structured\) \|\| structured == null\) return@setOnClickListener/)
  assert.match(listener, /\.setNegativeButton\("취소", null\)/)
  assert.match(listener, /\.setPositiveButton\("생성"\)[\s\S]*if \(canGenerateAi\(structured\)\) aiViewModel\.generate\(structured\)/)
  const gate = activity.slice(activity.indexOf("private fun canGenerateAi"), activity.indexOf("private fun visible"))
  assert.match(gate, /ReportAiUiPolicy\.canRequest\(AiCoachingPolicy\.ENABLED/)
  assert.match(gate, /structured\?\.repAnalyses\?\.isNotEmpty\(\) == true/)
  assert.match(gate, /loading = state is AiReportState\.Loading, completed = state is AiReportState\.Success/)
  assert.match(read(reportRoot + "ReportUiPolicy.kt"), /enabled && hasMeasuredReps && !loading && !completed/)
})

test("unscored structured repetitions render independently of available chart scores", () => {
  const source = read(reportRoot + "ReportActivity.kt")
  const setup = source.slice(source.indexOf("private fun setupRepDetails"), source.indexOf("private fun setupImprovementSummary"))
  assert.match(setup, /val cards = RepReportUiMapper\.map\(analyses, structured\?\.repAnalyses\.orEmpty\(\)\)/)
  assert.match(setup, /RepAnalysisCardRenderer\(layoutInflater\)\.render\(binding\.repAnalysisContainer, cards\)/)
  assert.match(setup, /val chartScores = cards\.mapNotNull \{ card -> card\.score\?\.let/)
  assert.ok(setup.indexOf(".render(binding.repAnalysisContainer, cards)") < setup.indexOf("val chartScores"),
    "missing scores cannot suppress measured cards")
  assert.match(setup, /tvRepEmpty\.visibility = visible\(cards\.isEmpty\(\)\)/)
})

test("chart colors identify a single score series, not medical red-green grades", () => {
  const chart = read(reportRoot + "ReportChartRenderer.kt")
  assert.match(chart, /BarDataSet\(analyses\.map \{ BarEntry\(it\.repNumber\.toFloat\(\), it\.score\) \}/)
  assert.match(chart, /color = ContextCompat\.getColor\(context, R\.color\.aurora_mint\)/)
  assert.doesNotMatch(chart, /#4CAF50|#F44336|Color\.(?:RED|GREEN)|color_success|score\s*[<>]=?|RepScoreCalculator/)
  assert.doesNotMatch(read(reportRoot + "ReportActivity.kt"), /#4CAF50|#F44336|Color\.(?:RED|GREEN)|color_success/)
})

test("report UI does not add LLM provider dependencies or perform network/persistence operations", () => {
  const dependencies = read("app/build.gradle.kts")
  assert.doesNotMatch(dependencies, /(?:implementation|api)\([^\n]*(?:openai|anthropic|generativeai|genai|firebase-ai)/i)
  for (const path of ["ReportActivity.kt", "RepAnalysisCardRenderer.kt", "RepReportUiModel.kt"]) {
    const source = read(reportRoot + path)
    assert.doesNotMatch(source,
      /HttpURLConnection|HttpClient|OkHttp|Retrofit|URL\(|FileOutputStream|SharedPreferences|SupabaseClientProvider|LLM_API_KEY|accessToken|\.insert\(|\.upsert\(/,
      `${path}: display code must not gain a network, credential or persistence boundary`)
  }
})

test("presentation supports a sorted union of structured-only and older repetition numbers", () => {
  const source = read(reportRoot + "RepReportUiModel.kt")
  assert.match(source, /legacyByNumber\.keys \+ measuredByNumber\.keys/)
  assert.match(source, /\)\.sorted\(\)\.map \{ number/)
  assert.match(source, /measuredByNumber\[number\]\?\.let \{ structuredCard\(it, legacyByNumber\[number\]\) \}\s*\?: legacyCard/)
  assert.match(source, /legacyDetail = savedText\(rep\.detail\)/,
    "older recorded descriptions remain available, without reanalysis")
  assert.match(source, /score = validScore\(rep\.score\)|val score = validScore\(rep\.score\)/)
  assert.doesNotMatch(source, /RepScoreCalculator\.|RepReportAnalyzer\.|RepAnalysisEngine\(/)
  assert.match(source, /if \(!hasObservation && score == null && priority == null\) legacy\?\.detail\?\.let\(::savedText\)/,
    "a sparse structured identity may retain saved legacy prose without borrowing its score")
})

test("detailed metrics omit unavailable rows and never reconstruct missing breakdown scores", () => {
  const source = read(reportRoot + "RepReportUiModel.kt")
  assert.match(source, /rows\.filterNotNull\(\)\.takeIf \{ it\.isNotEmpty\(\) \}/)
  assert.match(source, /value\s*\n?\s*\?\.takeIf \{ it\.isFinite\(\) && it in 0\.0\.\.180\.0 \}/)
  assert.match(source, /rep\.localScore\?\.components/,
    "optional breakdown uses only stored components")
  assert.doesNotMatch(source, /(?:calculate|score)\(rep\)|RepScorePolicy\./)
  assert.doesNotMatch(source, /"N\/A"|"측정 불가"/,
    "unavailable individual metric rows are hidden rather than replaced with placeholders")
})

test("error display uses Korean side and phase wording without raw severity or frame counts", () => {
  const source = read(reportRoot + "RepReportUiModel.kt")
  const display = source.slice(source.indexOf("private fun issueObservation"), source.indexOf("private fun savedText"))
  for (const text of ["왼쪽", "오른쪽", "내려가는 구간", "하단 구간", "전환 구간", "올라가는 구간"]) {
    assert.ok(display.includes(text), `Korean observation context: ${text}`)
  }
  assert.doesNotMatch(display, /severity|occurrenceFrames|\.name\b|\.ordinal\b/)
  assert.doesNotMatch(source, /"(?:완벽한 자세|정상 자세|비정상 자세|안전함|부상 위험 없음|문제 없음)"/)
})

test("metric expansion is per repetition, initially empty, and does not persist private data", () => {
  const source = read(reportRoot + "RepReportUiModel.kt")
  const state = source.slice(source.indexOf("class RepMetricExpansionState"), source.indexOf("object RepReportUiMapper"))
  assert.match(state, /private val expandedRepNumbers = mutableSetOf<Int>\(\)/)
  assert.match(state, /fun isExpanded\(repNumber: Int\): Boolean = repNumber in expandedRepNumbers/)
  assert.match(state, /expandedRepNumbers\.add\(repNumber\)/)
  assert.match(state, /expandedRepNumbers\.remove\(repNumber\)/)
  assert.doesNotMatch(state, /SharedPreferences|SavedStateHandle|Database|File\(/)
})

test("activity and card renderer never recalculate motion, errors or scores", () => {
  for (const path of ["ReportActivity.kt", "RepAnalysisCardRenderer.kt"]) {
    const source = read(reportRoot + path)
    assert.doesNotMatch(source,
      /import .*\.(?:RepAnalysisEngine|RepReportAnalyzer|RepScoreCalculator|RepPhaseSegmenter|FormEventAggregator|EngineeringFormPolicy)\b/,
      `${path}: presentation consumes recorded facts, not analysis engines`)
    assert.doesNotMatch(source, /RepScoreCalculator\.(?:calculate|score)\(|RepAnalysisEngine\(/)
  }
})
