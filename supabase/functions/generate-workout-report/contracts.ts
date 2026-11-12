// Pure, dependency-free contract. Keep parity with Android StructuredAnalysis/AiReportValidator.
import { directionalClaims, eventKey, localEventFeedback } from "./event-claims.ts"
import { exact, number, list, member, object } from "./contract-values.ts"
import type { ObjectValue } from "./contract-values.ts"
import { EXERCISES, EXTRA_ISSUES, EXERCISE_PATTERNS, V2_JOINTS, V2_ASYMMETRIES } from "./exercise-matrix.ts"
import { validateV2Input } from "./contracts-v2.ts"
import { metricClaims } from "./metric-claims.ts"
export { object } from "./contract-values.ts"
export { EXERCISES } from "./exercise-matrix.ts"
export const MAX_INPUT_BYTES = 131_072
export const MAX_OUTPUT_BYTES = 32_768
export const ISSUES = ["KNEE_VALGUS", "FORWARD_LEAN", "KNEE_ASYMMETRY", "HIP_ASYMMETRY", "ELBOW_ASYMMETRY", "SHALLOW_DEPTH", "INCOMPLETE_EXTENSION", ...EXTRA_ISSUES]
const STRENGTHS = ["KNEE_SYMMETRY_OBSERVED", "ELBOW_SYMMETRY_OBSERVED", "BOTH_PHASES_CAPTURED"]
const LOCAL_FEEDBACK = [
  "다음 회차에서는 무릎 방향을 발 방향에 맞춰 보세요.",
  "다음 회차에서는 양쪽 무릎의 움직임을 함께 확인해 보세요.",
  "다음 회차에서는 양팔이 함께 올라가도록 천천히 움직여 보세요.",
  "다음 회차에서는 몸통 기울기를 확인하며 편안하게 움직여 보세요.",
  "회차가 기록되었습니다. 편안한 범위에서 천천히 반복해 보세요.",
]
const JOINTS = ["leftKneeMinAngle", "rightKneeMinAngle", "leftHipMinAngle", "rightHipMinAngle", "leftElbowMaxAngle", "rightElbowMaxAngle", "torsoLeanMaxDeg"]
const ASYMMETRIES = ["kneeAngleDifferenceDeg", "hipAngleDifferenceDeg", "elbowAngleDifferenceDeg"]
const PHASES = ["descentMs", "bottomMs", "ascentMs"]
export type WorkoutInput = ObjectValue & { exerciseType: string, repAnalyses: ObjectValue[] }
export function validateInput(value: unknown): WorkoutInput {
  if (object(value).schemaVersion === 2) return validateV2Input(value) as WorkoutInput
  const input = exact(value, ["schemaVersion", "exerciseType", "totalReps", "averageScore", "bestRepNumber", "weakestRepNumber", "dominantIssues", "repAnalyses", "omittedReps"])
  if (input.schemaVersion !== 1) throw new Error("Unsupported schema")
  member(input.exerciseType, ["SQUAT", "SHOULDER_PRESS"])
  number(input.totalReps, 1, 10000, true)
  number(input.averageScore, 0, 100, false, true)
  number(input.bestRepNumber, 1, input.totalReps as number, true, true)
  number(input.weakestRepNumber, 1, input.totalReps as number, true, true)
  number(input.omittedReps, 0, input.totalReps as number, true)
  list(input.dominantIssues, 7).forEach(x => member(x, ISSUES.slice(0, 7)))
  const reps = list(input.repAnalyses, 50)
  if (reps.length === 0 || reps.length + (input.omittedReps as number) !== input.totalReps) throw new Error("Invalid rep count")
  const seen = new Set<number>()
  for (const value of reps) {
    const rep = exact(value, ["repNumber", "exerciseType", "cameraView", "totalDurationMs", "phaseDurations", "jointMetrics", "asymmetryMetrics", "errorEvents", "strengths", "improvementCandidates", "localPriorityFeedback", "score", "observedFrames", "partialCapture"], ["phaseModelVersion"])
    number(rep.phaseModelVersion ?? 1, 1, 2, true)
    number(rep.repNumber, 1, input.totalReps as number, true)
    if (seen.has(rep.repNumber as number) || rep.exerciseType !== input.exerciseType) throw new Error("Invalid repetition")
    seen.add(rep.repNumber as number)
    member(rep.cameraView, ["FRONT", "SIDE", "UNKNOWN"])
    number(rep.totalDurationMs, 0, 20000, true, true)
    number(rep.score, 0, 100, false, true)
    number(rep.observedFrames, 3, 300, true)
    if (typeof rep.partialCapture !== "boolean" || (rep.partialCapture && rep.totalDurationMs !== null)) throw new Error("Invalid capture")
    const phases = exact(rep.phaseDurations, PHASES)
    PHASES.forEach(k => number(phases[k], 0, 20000, true, true))
    if (rep.totalDurationMs !== null && PHASES.reduce((total, key) => total + Number(phases[key] ?? 0), 0) > (rep.totalDurationMs as number)) throw new Error("Inconsistent timing")
    const joints = exact(rep.jointMetrics, JOINTS)
    JOINTS.forEach(k => number(joints[k], 0, 180, false, true))
    const differences = exact(rep.asymmetryMetrics, ASYMMETRIES)
    ASYMMETRIES.forEach(k => number(differences[k], 0, 180, false, true))
    // Prevent unsupported-view metrics or unknown-view guesses from crossing this boundary.
    const squat = rep.exerciseType === "SQUAT"
    if (rep.cameraView !== "SIDE" && (joints.leftHipMinAngle !== null || joints.rightHipMinAngle !== null || joints.torsoLeanMaxDeg !== null)) throw new Error("Unsupported view metric")
    if (rep.cameraView !== "FRONT" && ASYMMETRIES.some(k => differences[k] !== null)) throw new Error("Unsupported asymmetry")
    if (differences.hipAngleDifferenceDeg !== null) throw new Error("Unevaluated hip symmetry")
    if (squat && (joints.leftElbowMaxAngle !== null || joints.rightElbowMaxAngle !== null || differences.elbowAngleDifferenceDeg !== null)) throw new Error("Wrong exercise metric")
    if (!squat && (JOINTS.filter(k => !k.includes("Elbow")).some(k => joints[k] !== null) || differences.kneeAngleDifferenceDeg !== null)) throw new Error("Wrong exercise metric")
    if (rep.cameraView === "UNKNOWN" && JOINTS.some(k => joints[k] !== null)) throw new Error("Unknown view")
    if (rep.cameraView === "SIDE" && ((joints.leftKneeMinAngle !== null && joints.rightKneeMinAngle !== null) ||
      (joints.leftElbowMaxAngle !== null && joints.rightElbowMaxAngle !== null))) throw new Error("Occluded bilateral metric")
    const events = list(rep.errorEvents, 12)
    const eventGroups = new Set<string>()
    events.forEach(value => {
      const e = exact(value, ["type", "phase", "side", "severity", "timestampMs", "occurrenceFrames"])
      member(e.type, ISSUES); member(e.phase, ["DESCENT", "BOTTOM", "ASCENT"], true)
      member(e.side, ["LEFT", "RIGHT"], true); number(e.severity, 1, 3, true)
      if (e.type !== "KNEE_VALGUS" && e.side !== null) throw new Error("Unmeasured dominant side")
      if (eventGroups.has(eventKey(e))) throw new Error("Duplicate phase event")
      eventGroups.add(eventKey(e))
      number(e.timestampMs, 0, 20000, true, true); number(e.occurrenceFrames, 3, rep.observedFrames as number, true)
      if (e.timestampMs !== null && rep.totalDurationMs !== null && Number(e.timestampMs) > Number(rep.totalDurationMs)) throw new Error("Non-relative event time")
      const allowed = squat ? (rep.cameraView === "FRONT" ? ["KNEE_VALGUS", "KNEE_ASYMMETRY"] : rep.cameraView === "SIDE" ? ["FORWARD_LEAN"] : []) : rep.cameraView === "FRONT" ? ["ELBOW_ASYMMETRY"] : []
      member(e.type, allowed)
      if (e.type === "KNEE_ASYMMETRY" && !(Number(differences.kneeAngleDifferenceDeg) > 15)) throw new Error("Unsupported knee issue")
      if (e.type === "ELBOW_ASYMMETRY" && !(Number(differences.elbowAngleDifferenceDeg) > 15)) throw new Error("Unsupported elbow issue")
      if (e.type === "FORWARD_LEAN" && !(Number(joints.torsoLeanMaxDeg) > 20)) throw new Error("Unsupported lean issue")
    })
    list(rep.strengths, 3).forEach(x => {
      member(x, STRENGTHS)
      if (x === "KNEE_SYMMETRY_OBSERVED" && (!squat || rep.cameraView !== "FRONT" ||
        joints.leftKneeMinAngle === null || joints.rightKneeMinAngle === null ||
        differences.kneeAngleDifferenceDeg === null || Number(differences.kneeAngleDifferenceDeg) > 15)) throw new Error("Unsupported knee strength")
      if (x === "ELBOW_SYMMETRY_OBSERVED" && (squat || rep.cameraView !== "FRONT" ||
        joints.leftElbowMaxAngle === null || joints.rightElbowMaxAngle === null ||
        differences.elbowAngleDifferenceDeg === null || Number(differences.elbowAngleDifferenceDeg) > 15)) throw new Error("Unsupported elbow strength")
      if (x === "BOTH_PHASES_CAPTURED" && (phases.descentMs === null || phases.ascentMs === null)) throw new Error("Unsupported timing strength")
    })
    const eventTypes = events.map(e => object(e).type)
    list(rep.improvementCandidates, 7).forEach(x => { if (!eventTypes.includes(x)) throw new Error("Unobserved issue") })
    if (!LOCAL_FEEDBACK.includes(rep.localPriorityFeedback as string) && !events.some(e => localEventFeedback(object(e)) === rep.localPriorityFeedback)) throw new Error("Invalid local feedback")
  }
  return input as WorkoutInput
}

export function evidenceKeys(rep: ObjectValue): Set<string> {
  const keys = new Set<string>()
  for (const k of ["score", "totalDurationMs"]) if (rep[k] !== null) keys.add(k)
  for (const [name, members] of [["jointMetrics", [...JOINTS, ...V2_JOINTS]], ["asymmetryMetrics", [...ASYMMETRIES, ...V2_ASYMMETRIES]], ["phaseDurations", PHASES]] as const) {
    const values = object(rep[name])
    for (const k of members) if (typeof values[k] === "number" && Number.isFinite(values[k])) keys.add(`${name}.${k}`)
  }
  if (rep.activeSide === "LEFT" || rep.activeSide === "RIGHT") keys.add("activeSide")
  for (const x of rep.strengths as string[]) keys.add(`strength:${x}`)
  for (const x of rep.errorEvents as ObjectValue[]) { keys.add(`issue:${x.type}`); keys.add(eventKey(x)) }
  return keys
}
function measuredClaims(texts: unknown[], reps: ObjectValue[], references?: Set<string>, totals: number[] = [], average?: unknown): void {
  const values = new Map<string, number[]>()
  const add = (unit: string, value: number) => values.set(unit, [...(values.get(unit) ?? []), value])
  totals.forEach(value => add("회", value))
  if (typeof average === "number") add("점", average)
  for (const rep of reps) {
    add("회", rep.repNumber as number)
    const keys = references ?? evidenceKeys(rep)
    for (const e of rep.errorEvents as ObjectValue[]) {
      if (keys.has(eventKey(e)) || keys.has(`issue:${e.type}`)) {
        add("프레임", e.occurrenceFrames as number); add("우선도", e.severity as number)
        if (typeof e.timestampMs === "number") { add("ms", e.timestampMs); add("초", e.timestampMs / 1000) }
      }
    }
    for (const key of keys) {
      const path = key.split(".")
      const value = path.length === 2 ? object(rep[path[0]])[path[1]] : rep[key]
      if (typeof value !== "number") continue
      if (key.startsWith("jointMetrics.") || key.startsWith("asymmetryMetrics.")) add("°", value)
      else if (key.startsWith("phaseDurations.") || key === "totalDurationMs") { add("ms", value); add("초", value / 1000) }
      else if (key === "score") add("점", value)
    }
  }
  for (const text of texts as string[]) {
    for (const match of text.matchAll(/(-?\d+(?:\.\d+)?)\s*(°|도|점|ms|밀리초|초|회|프레임|frames?)?/gi)) {
      const number = Number(match[1])
      const unit = match[2]?.toLowerCase() === "도" ? "°" : match[2] === "밀리초" ? "ms" : /^frames?$/i.test(match[2] ?? "") ? "프레임" : match[2]?.toLowerCase()
      const allowed = unit ? values.get(unit) ?? [] : [...values.values()].flat()
      if (!allowed.some(v => Math.abs(number - v) < 0.00001 || Math.abs(number - Math.round(v)) < 0.00001 || Math.abs(number - Math.round(v * 10) / 10) < 0.00001)) throw new Error("Unobserved numeric claim")
    }
  }
}
export function validateReport(value: unknown, input: WorkoutInput): ObjectValue {
  const report = exact(value, ["exerciseType", "summary", "strengths", "improvements", "priority", "nextWorkoutGoal", "repFeedback"])
  if (report.exerciseType !== input.exerciseType) throw new Error("Wrong exercise")
  const wrongExercise = (text: string) => Object.entries(EXERCISE_PATTERNS).some(([type, pattern]) => type !== input.exerciseType && pattern.test(text.replace(/\s+/g, "")))
  function text(v: unknown): void {
    if (typeof v !== "string" || !v.trim() || v.length > 500 || /진단|치료|질환|부상|완벽|정상|안전하|안전한|안전합|전문가.*자세|근활성|근육 활성|척추 부하|scapular|spinal load|muscle activation|\bsafe\b|\bnormal\b|diagnos|cure|injur|perfect/i.test(v) || wrongExercise(v) || /[\x00-\x09\x0b-\x1f\x7f]/.test(v)) throw new Error("Invalid text")
  }
  text(report.summary); text(report.priority); text(report.nextWorkoutGoal)
  list(report.strengths, 5).forEach(text); list(report.improvements, 5).forEach(text)
  metricClaims([report.summary, report.priority, report.nextWorkoutGoal, ...report.strengths as unknown[], ...report.improvements as unknown[]],input.repAnalyses)
  directionalClaims([report.summary, report.priority, report.nextWorkoutGoal, ...report.strengths as unknown[], ...report.improvements as unknown[]], input.repAnalyses)
  measuredClaims([report.summary, report.priority, report.nextWorkoutGoal, ...report.strengths as unknown[], ...report.improvements as unknown[]],
    input.repAnalyses, undefined, [input.totalReps, input.omittedReps, input.bestRepNumber, input.weakestRepNumber].filter(v => typeof v === "number") as number[], input.averageScore)
  const feedbacks = list(report.repFeedback, 50)
  if (feedbacks.length !== input.repAnalyses.length) throw new Error("Missing rep feedback")
  const seen = new Set()
  for (const v of feedbacks) {
    const feedback = exact(v, ["repNumber", "summary", "positive", "improvement", "evidenceKeys"])
    const rep = input.repAnalyses.find(r => r.repNumber === feedback.repNumber)
    if (!rep || seen.has(feedback.repNumber)) throw new Error("Invented rep")
    seen.add(feedback.repNumber)
    text(feedback.summary); text(feedback.positive); text(feedback.improvement)
    const keys = list(feedback.evidenceKeys, 12)
    const allowed = evidenceKeys(rep)
    if (!keys.length || new Set(keys).size !== keys.length || keys.some(k => typeof k !== "string" || !allowed.has(k))) throw new Error("Unobserved evidence")
    metricClaims([feedback.summary,feedback.positive,feedback.improvement],[rep],new Set(keys as string[]))
    directionalClaims([feedback.summary, feedback.positive, feedback.improvement], [rep], new Set(keys as string[]))
    measuredClaims([feedback.summary, feedback.positive, feedback.improvement], [rep], new Set(keys as string[]))
  }
  return report
}
