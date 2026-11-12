import { exact, list, member, number, object } from "./contract-values.ts"
import { allowedAsymmetries, allowedIssues, allowedJoints, DEFAULT_COACHING, EXERCISES, LEGACY_JOINTS, V2_ASYMMETRIES, V2_JOINTS } from "./exercise-matrix.ts"
import { eventKey, localEventFeedback } from "./event-claims.ts"

const ASYMMETRIES = ["kneeAngleDifferenceDeg", "hipAngleDifferenceDeg", "elbowAngleDifferenceDeg"]
const PHASES = ["descentMs", "bottomMs", "ascentMs"]
const LEGACY_FEEDBACK = ["다음 회차에서는 무릎 방향을 발 방향에 맞춰 보세요.", "다음 회차에서는 양쪽 무릎의 움직임을 함께 확인해 보세요.",
  "다음 회차에서는 양팔이 함께 올라가도록 천천히 움직여 보세요.", "다음 회차에서는 몸통 기울기를 확인하며 편안하게 움직여 보세요.",
  "회차가 기록되었습니다. 편안한 범위에서 천천히 반복해 보세요."]
const measured = (v: unknown): v is number => typeof v === "number" && Number.isFinite(v)

/** V1 validator remains separate and strict. V2 admits only exercise/view-specific measured scalars. */
export function validateV2Input(value: unknown) {
  const input = exact(value, ["schemaVersion", "exerciseType", "totalReps", "averageScore", "bestRepNumber", "weakestRepNumber", "dominantIssues", "repAnalyses", "omittedReps"])
  if (input.schemaVersion !== 2) throw new Error("Unsupported schema")
  member(input.exerciseType, EXERCISES)
  const exercise = input.exerciseType as string
  number(input.totalReps, 1, 10000, true); number(input.averageScore, 0, 100, false, true)
  for (const key of ["bestRepNumber", "weakestRepNumber"]) number(input[key], 1, Number(input.totalReps), true, true)
  number(input.omittedReps, 0, Number(input.totalReps), true)
  const dominant = list(input.dominantIssues, 12)
  dominant.forEach(x => member(x, [...allowedIssues(exercise, "FRONT"), ...allowedIssues(exercise, "SIDE")]))
  const reps = list(input.repAnalyses, 50)
  if (!reps.length || reps.length + Number(input.omittedReps) !== input.totalReps) throw new Error("Invalid rep count")
  const seen = new Set<number>()
  const observedIssues = new Set<string>()
  for (const raw of reps) {
    const rep = exact(raw, ["repNumber", "exerciseType", "cameraView", "totalDurationMs", "phaseDurations", "jointMetrics", "asymmetryMetrics", "errorEvents", "strengths", "improvementCandidates", "localPriorityFeedback", "score", "observedFrames", "partialCapture"], ["phaseModelVersion", "activeSide"])
    number(rep.repNumber, 1, Number(input.totalReps), true)
    if (seen.has(Number(rep.repNumber)) || rep.exerciseType !== exercise) throw new Error("Invalid repetition")
    seen.add(Number(rep.repNumber))
    member(rep.cameraView, ["FRONT", "SIDE", "UNKNOWN"])
    const view = rep.cameraView as string
    number(rep.phaseModelVersion ?? 1, 1, 2, true)
    number(rep.totalDurationMs, 0, 20000, true, true); number(rep.score, 0, 100, false, true)
    number(rep.observedFrames, 0, 300, true)
    if (typeof rep.partialCapture !== "boolean" || rep.partialCapture && rep.totalDurationMs !== null) throw new Error("Invalid capture")
    const phases = exact(rep.phaseDurations, PHASES)
    PHASES.forEach(k => number(phases[k], 0, 20000, true, true))
    if (rep.totalDurationMs !== null && PHASES.reduce((n, k) => n + Number(phases[k] ?? 0), 0) > Number(rep.totalDurationMs)) throw new Error("Inconsistent timing")
    const joints = exact(rep.jointMetrics, LEGACY_JOINTS, V2_JOINTS)
    const asym = exact(rep.asymmetryMetrics, ASYMMETRIES, V2_ASYMMETRIES)
    for (const [fields, allowed] of [[joints, allowedJoints(exercise, view)], [asym, allowedAsymmetries(exercise, view)]] as const) {
      for (const [key, v] of Object.entries(fields)) {
        number(v, 0, 180, false, true)
        if (v !== null && !allowed.includes(key)) throw new Error("Wrong exercise/view metric")
      }
    }
    const sideKeys = Object.keys(joints).filter(k => measured(joints[k]))
    const isLeft = (k: string) => k.startsWith("left") || k.includes("Left")
    const isRight = (k: string) => k.startsWith("right") || k.includes("Right")
    if (view === "SIDE" && sideKeys.some(isLeft) && sideKeys.some(isRight)) throw new Error("Occluded bilateral metric")
    member(rep.activeSide ?? null, ["LEFT", "RIGHT"], true)
    if (rep.activeSide != null && !["BICEP_CURL", "SIDE_BEND"].includes(exercise)) throw new Error("Wrong exercise side")
    if (exercise === "BICEP_CURL" && sideKeys.length) {
      member(rep.activeSide, ["LEFT", "RIGHT"])
      if (sideKeys.some(rep.activeSide === "LEFT" ? isRight : isLeft)) throw new Error("Inactive arm measurement")
    }
    if (exercise === "SIDE_BEND" && measured(joints.peakLateralFlexionDeg)) member(rep.activeSide, ["LEFT", "RIGHT"])
    for (const side of ["left", "right"]) {
      const min = joints[`${side}ElbowMinAngle`], max = joints[`${side}ElbowMaxAngle`], rom = joints[`${side}ElbowRomDeg`]
      if (measured(min) && measured(max) && min > max) throw new Error("Invalid extrema")
      if (measured(rom) && (!measured(min) || !measured(max) || Math.abs(rom - (max - min)) > 1e-6)) throw new Error("Unobserved ROM")
    }
    if (!sideKeys.length && rep.score !== null) throw new Error("Unmeasured score")
    const events = list(rep.errorEvents, 12).map(object)
    const eventKeys = new Set<string>()
    for (const e of events) {
      exact(e, ["type", "phase", "side", "severity", "timestampMs", "occurrenceFrames"])
      member(e.type, allowedIssues(exercise, view)); member(e.side, ["LEFT", "RIGHT"], true)
      member(e.phase, ["DESCENT", "BOTTOM", "ASCENT"], true)
      number(e.severity, 1, 3, true); number(e.occurrenceFrames, 3, Number(rep.observedFrames), true)
      number(e.timestampMs, 0, 20000, true, true)
      if (e.timestampMs !== null && rep.totalDurationMs !== null && Number(e.timestampMs) > Number(rep.totalDurationMs)) throw new Error("Non-relative event time")
      if (eventKeys.has(eventKey(e))) throw new Error("Duplicate phase event")
      eventKeys.add(eventKey(e)); observedIssues.add(String(e.type))
      const sideMetric = e.side === "LEFT" ? "upperArmDriftLeftDeg" : e.side === "RIGHT" ? "upperArmDriftRightDeg" : null
      switch (e.type) {
        case "KNEE_VALGUS": break // legacy 2D ratio remains transient; no invented numeric angle
        case "KNEE_ASYMMETRY": if (!(Number(asym.kneeAngleDifferenceDeg) > 15)) throw new Error("Unsupported knee issue"); break
        case "ELBOW_ASYMMETRY": if (!(Number(asym.elbowAngleDifferenceDeg) > 15)) throw new Error("Unsupported elbow issue"); break
        case "FORWARD_LEAN": if (!(Number(joints.torsoLeanMaxDeg) > 20)) throw new Error("Unsupported lean issue"); break
        case "UPPER_ARM_POSITION_VARIATION": if (!sideMetric || !(Number(joints[sideMetric]) > 15) || exercise === "BICEP_CURL" && rep.activeSide !== e.side) throw new Error("Unobserved arm drift"); break
        case "TRUNK_POSITION_VARIATION": if (!(Number(joints.torsoInclinationChangeDeg) > 10)) throw new Error("Unobserved trunk change"); break
        case "BODY_LINE_VARIATION": if (!(Number(joints.bodyLineDeviationDeg) > 10)) throw new Error("Unobserved alignment change"); break
        case "LATERAL_RANGE_ASYMMETRY": if (!(Number(asym.lateralRangeDifferenceDeg) > 15) || e.side !== rep.activeSide) throw new Error("Unobserved lateral comparison"); break
        case "RETURN_TO_NEUTRAL_VARIATION": if (!(Number(joints.returnToNeutralDeviationDeg) > 3) || e.side !== rep.activeSide) throw new Error("Unobserved return"); break
      }
      if (!["KNEE_VALGUS", "UPPER_ARM_POSITION_VARIATION", "LATERAL_RANGE_ASYMMETRY", "RETURN_TO_NEUTRAL_VARIATION"].includes(String(e.type)) && e.side !== null) throw new Error("Unmeasured dominant side")
      if (!(["SQUAT", "SHOULDER_PRESS"].includes(exercise)) && e.phase != null &&
        phases[e.phase === "DESCENT" ? "descentMs" : e.phase === "BOTTOM" ? "bottomMs" : "ascentMs"] === null) throw new Error("Unobserved phase")
    }
    list(rep.strengths, 3).forEach(key => {
      member(key, ["KNEE_SYMMETRY_OBSERVED", "ELBOW_SYMMETRY_OBSERVED", "BOTH_PHASES_CAPTURED"])
      if (key === "BOTH_PHASES_CAPTURED" && (phases.descentMs === null || phases.ascentMs === null)) throw new Error("Unsupported timing strength")
      if (key === "KNEE_SYMMETRY_OBSERVED" && !(exercise === "SQUAT" && view === "FRONT" && measured(joints.leftKneeMinAngle) && measured(joints.rightKneeMinAngle) && measured(asym.kneeAngleDifferenceDeg) && asym.kneeAngleDifferenceDeg <= 15)) throw new Error("Unsupported knee strength")
      if (key === "ELBOW_SYMMETRY_OBSERVED" && !(view === "FRONT" && !["SQUAT", "SIDE_BEND", "BICEP_CURL"].includes(exercise) && measured(joints.leftElbowMaxAngle) && measured(joints.rightElbowMaxAngle) && measured(asym.elbowAngleDifferenceDeg) && asym.elbowAngleDifferenceDeg <= 15)) throw new Error("Unsupported elbow strength")
    })
    list(rep.improvementCandidates, 12).forEach(x => { if (!events.some(e => e.type === x)) throw new Error("Unobserved issue") })
    const feedback = rep.localPriorityFeedback
    const legacyDefault = ["SQUAT", "SHOULDER_PRESS"].includes(exercise) && LEGACY_FEEDBACK.includes(String(feedback))
    // MainViewModel's missing-capture placeholder is not an observed/normal posture claim.
    // Accept this exact wording only with a completely absent, unscored measurement envelope.
    const missingCapture = feedback === "이 회차의 측정 근거가 부족합니다." && rep.partialCapture === true &&
      rep.observedFrames === 0 && rep.phaseModelVersion === 2 && rep.activeSide == null &&
      rep.score === null && rep.totalDurationMs === null && events.length === 0 &&
      Object.values(joints).every(v => v === null) && Object.values(asym).every(v => v === null) &&
      Object.values(phases).every(v => v === null) && list(rep.strengths, 3).length === 0 && list(rep.improvementCandidates, 12).length === 0
    if (Number(rep.observedFrames) < 3 && !missingCapture) throw new Error("Insufficient observed frames")
    if (!legacyDefault && !missingCapture && feedback !== DEFAULT_COACHING[exercise] && !events.some(e => localEventFeedback(e, exercise) === feedback)) throw new Error("Invalid local feedback")
  }
  if (input.omittedReps === 0 && dominant.some(x => !observedIssues.has(String(x)))) throw new Error("Unobserved dominant issue")
  return input
}
