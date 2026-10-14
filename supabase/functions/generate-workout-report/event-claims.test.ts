import test from "node:test"
import assert from "node:assert/strict"
import { validateInput, validateReport, evidenceKeys } from "./contracts.ts"
import { eventKey, localEventFeedback, directionalClaims } from "./event-claims.ts"

const copy = <T>(x: T): T => JSON.parse(JSON.stringify(x))
function input() { return {
  schemaVersion: 1, exerciseType: "SQUAT", totalReps: 1, averageScore: 78, bestRepNumber: 1, weakestRepNumber: 1, dominantIssues: ["KNEE_VALGUS"], omittedReps: 0,
  repAnalyses: [{ repNumber: 1, exerciseType: "SQUAT", cameraView: "FRONT", totalDurationMs: 1300,
    phaseDurations: { descentMs: 500, bottomMs: 300, ascentMs: 500 },
    jointMetrics: { leftKneeMinAngle: 80, rightKneeMinAngle: 80, leftHipMinAngle: null, rightHipMinAngle: null,
      leftElbowMaxAngle: null, rightElbowMaxAngle: null, torsoLeanMaxDeg: null },
    asymmetryMetrics: { kneeAngleDifferenceDeg: 0, hipAngleDifferenceDeg: null, elbowAngleDifferenceDeg: null },
    errorEvents: [{ type: "KNEE_VALGUS", phase: "DESCENT", side: "RIGHT", severity: 2, timestampMs: 0, occurrenceFrames: 5 }],
    strengths: ["KNEE_SYMMETRY_OBSERVED"], improvementCandidates: ["KNEE_VALGUS"],
    localPriorityFeedback: "내려가는 구간에서 오른쪽 무릎 방향을 발 방향에 맞춰 보세요.", score: 78,
    observedFrames: 14, partialCapture: false, phaseModelVersion: 2 }],
} }
function report() { return {
  exerciseType: "SQUAT", summary: "내려가는 구간에서 오른쪽 무릎의 안쪽 이동 후보가 관찰되었습니다.",
  strengths: ["좌우 각도 차이가 초기 기준 이내였습니다."], improvements: ["무릎 방향을 확인해 보세요."],
  priority: "오른쪽 무릎 방향을 먼저 확인해 보세요.", nextWorkoutGoal: "편안한 범위에서 움직여 보세요.",
  repFeedback: [{ repNumber: 1, summary: "내려가는 구간에서 오른쪽 무릎의 안쪽 이동 후보가 관찰되었습니다.",
    positive: "측정 가능한 정보가 기록되었습니다.", improvement: "오른쪽 무릎 방향을 확인해 보세요.",
    evidenceKeys: ["event:KNEE_VALGUS:RIGHT:DESCENT"] }],
} }
test("right/down event and exact local feedback accepted", () => {
  const data = validateInput(input())
  assert.deepEqual(validateReport(report(), data), report())
  assert.ok(evidenceKeys(data.repAnalyses[0]).has("event:KNEE_VALGUS:RIGHT:DESCENT"))
})
test("left claim rejected when only right event observed", () => {
  const value = report(); value.repFeedback[0].summary = "내려가는 구간에서 왼쪽 무릎이 관찰되었습니다."
  assert.throws(() => validateReport(value, validateInput(input())))
})
test("wrong phase rejected even with non-null corresponding duration", () => {
  const value = report(); value.repFeedback[0].summary = "올라가는 구간에서 오른쪽 무릎이 관찰되었습니다."
  assert.throws(() => validateReport(value, validateInput(input())))
})
test("global summary and next goal cannot invent side or phase", () => {
  const left = report(); left.summary = "왼쪽 무릎이 관찰되었습니다."
  assert.throws(() => validateReport(left, validateInput(input())))
  const phase = report(); phase.nextWorkoutGoal = "하단 구간에서 오른쪽 무릎을 확인해 보세요."
  assert.throws(() => validateReport(phase, validateInput(input())))
})
test("null side never authorizes explicit anatomical side", () => {
  const data = input(); data.repAnalyses[0].errorEvents[0].side = null as never
  data.repAnalyses[0].localPriorityFeedback = "다음 회차에서는 무릎 방향을 발 방향에 맞춰 보세요."
  const value = report(); value.repFeedback[0].evidenceKeys = ["issue:KNEE_VALGUS"]
  assert.throws(() => validateReport(value, validateInput(data)))
})
test("side and phase must occur on the same event", () => {
  const data = input(); data.repAnalyses[0].errorEvents.push({ ...data.repAnalyses[0].errorEvents[0], side: "LEFT", phase: "ASCENT", timestampMs: 900 })
  const value = report(); value.repFeedback[0].evidenceKeys = ["issue:KNEE_VALGUS"]
  value.repFeedback[0].summary = "올라가는 구간에서 오른쪽 무릎이 관찰되었습니다."
  assert.throws(() => validateReport(value, validateInput(data)))
})
test("unreferenced phase event cannot ground per-rep claim", () => {
  const data = input(); data.repAnalyses[0].errorEvents.push({ ...data.repAnalyses[0].errorEvents[0], phase: "BOTTOM", timestampMs: 500, occurrenceFrames: 4 })
  const value = report(); value.repFeedback[0].summary = "하단 구간에서 오른쪽 무릎이 관찰되었습니다."
  assert.throws(() => validateReport(value, validateInput(data)))
})
test("joint evidence alone cannot authorize side issue prose", () => {
  const value = report(); value.repFeedback[0].evidenceKeys = ["jointMetrics.rightKneeMinAngle"]
  assert.throws(() => validateReport(value, validateInput(input())))
})
test("both knees and all three phases fit bounded event contract", () => {
  const data = input()
  data.repAnalyses[0].errorEvents = ["LEFT", "RIGHT"].flatMap(side => ["DESCENT", "BOTTOM", "ASCENT"].map((phase, i) => ({
    type: "KNEE_VALGUS", phase, side, severity: 2, timestampMs: [0, 500, 900][i], occurrenceFrames: [5, 4, 5][i],
  })))
  data.repAnalyses[0].asymmetryMetrics.kneeAngleDifferenceDeg = 30
  data.repAnalyses[0].strengths = []
  data.repAnalyses[0].errorEvents.push(...["DESCENT", "BOTTOM", "ASCENT"].map((phase, i) => ({
    type: "KNEE_ASYMMETRY", phase, side: null as never, severity: 2, timestampMs: [0, 500, 900][i], occurrenceFrames: [5, 4, 5][i],
  })))
  assert.equal(validateInput(data).repAnalyses[0].errorEvents instanceof Array, true)
})
test("duplicate group, excess count and absolute timestamp rejected", () => {
  const duplicate = input(); duplicate.repAnalyses[0].errorEvents.push(copy(duplicate.repAnalyses[0].errorEvents[0]))
  assert.throws(() => validateInput(duplicate))
  const absolute = input(); absolute.repAnalyses[0].errorEvents[0].timestampMs = 123456789
  assert.throws(() => validateInput(absolute))
  const excess = input(); excess.repAnalyses[0].errorEvents[0].occurrenceFrames = 15
  assert.throws(() => validateInput(excess))
})
test("side cannot be invented for unsigned bilateral difference", () => {
  const data = input(); data.repAnalyses[0].errorEvents[0].type = "KNEE_ASYMMETRY"
  data.repAnalyses[0].asymmetryMetrics.kneeAngleDifferenceDeg = 30; data.repAnalyses[0].strengths = []
  assert.throws(() => validateInput(data))
})
test("unknown view and missing phase cannot authorize issue phrases", () => {
  const data = input(); data.repAnalyses[0].cameraView = "UNKNOWN"
  assert.throws(() => validateInput(data))
  const missing = input(); missing.repAnalyses[0].errorEvents[0].phase = null as never
  missing.repAnalyses[0].localPriorityFeedback = "임의 문구" // invalid fixed text is also rejected
  assert.throws(() => validateInput(missing))
  missing.repAnalyses[0].localPriorityFeedback = "다음 회차에서는 무릎 방향을 발 방향에 맞춰 보세요."
  const value = report(); value.repFeedback[0].evidenceKeys = ["issue:KNEE_VALGUS"]
  assert.throws(() => validateReport(value, validateInput(missing)))
})
test("legacy phase version and coarse issue reference remain accepted", () => {
  const data = input(); delete (data.repAnalyses[0] as Record<string, unknown>).phaseModelVersion
  const value = report(); value.repFeedback[0].evidenceKeys = ["issue:KNEE_VALGUS"]
  assert.deepEqual(validateReport(value, validateInput(data)), value)
})
test("local canonical phrase mapping preserves both side and phase", () => {
  for (const side of ["LEFT", "RIGHT"]) for (const phase of ["DESCENT", "BOTTOM", "ASCENT"]) {
    const event = { type: "KNEE_VALGUS", side, phase }
    const text = localEventFeedback(event)
    assert.ok(text.includes(side === "LEFT" ? "왼쪽" : "오른쪽")); assert.ok(eventKey(event).endsWith(`${side}:${phase}`))
  }
})
test("transient ratios, frame timestamp and unknown phase version never cross input boundary", () => {
  const data = input()
  assert.throws(() => validateInput({ ...data, repAnalyses: [{ ...data.repAnalyses[0], leftKneeValgusRatio: 0.20 }] }))
  data.repAnalyses[0].phaseModelVersion = 3; assert.throws(() => validateInput(data))
})
test("actual occurrence frames and initial priority supported; invented count rejected", () => {
  const real = report(); real.repFeedback[0].summary = "내려가는 구간에서 오른쪽 무릎 후보가 5프레임 관찰되었습니다. 초기 우선도 2입니다."
  assert.deepEqual(validateReport(real, validateInput(input())), real)
  const fake = copy(real); fake.repFeedback[0].summary = "오른쪽 무릎 후보가 6프레임 관찰되었습니다."
  assert.throws(() => validateReport(fake, validateInput(input())))
})
test("numbered summary cannot borrow another repetitions side", () => {
  const a = input().repAnalyses[0]
  const b = { ...copy(a), repNumber: 2, errorEvents: a.errorEvents.map(e => ({ ...e, side: "LEFT" })) }
  assert.throws(() => directionalClaims(["1회차에서 왼쪽 무릎이 관찰되었습니다."], [a, b]))
  directionalClaims(["2회차에서 왼쪽 무릎이 관찰되었습니다."], [a, b])
})
