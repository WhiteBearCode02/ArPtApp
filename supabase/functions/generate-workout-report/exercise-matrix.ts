/** Scalar V2 allowlist. All thresholds below are ENGINEERING_INITIAL, not clinical normal ranges. */
export const EXERCISES = ["SQUAT", "SHOULDER_PRESS", "BICEP_CURL", "PUSH_UP", "BENT_OVER_ROW", "TRICEPS_EXTENSION", "SIDE_BEND"]
export const LEGACY_JOINTS = ["leftKneeMinAngle", "rightKneeMinAngle", "leftHipMinAngle", "rightHipMinAngle", "leftElbowMaxAngle", "rightElbowMaxAngle", "torsoLeanMaxDeg"]
export const V2_JOINTS = ["leftElbowMinAngle", "rightElbowMinAngle", "leftElbowRomDeg", "rightElbowRomDeg",
  "upperArmDriftLeftDeg", "upperArmDriftRightDeg", "torsoInclinationDeg", "torsoInclinationChangeDeg",
  "bodyLineDeviationDeg", "shoulderHeightDifferenceDeg", "peakLateralFlexionDeg", "returnToNeutralDeviationDeg"]
export const V2_ASYMMETRIES = ["contralateralRomDifferenceDeg", "lateralRangeDifferenceDeg"]
export const EXTRA_ISSUES = ["UPPER_ARM_POSITION_VARIATION", "TRUNK_POSITION_VARIATION", "BODY_LINE_VARIATION", "LATERAL_RANGE_ASYMMETRY", "RETURN_TO_NEUTRAL_VARIATION"]
const elbows = ["leftElbowMinAngle", "rightElbowMinAngle", "leftElbowMaxAngle", "rightElbowMaxAngle", "leftElbowRomDeg", "rightElbowRomDeg"]
const upper = ["upperArmDriftLeftDeg", "upperArmDriftRightDeg"]
export function allowedJoints(exercise: string, view: string): string[] {
  if (!["FRONT", "SIDE"].includes(view)) return []
  switch (exercise) {
    case "SQUAT": return ["leftKneeMinAngle", "rightKneeMinAngle", ...(view === "SIDE" ? ["leftHipMinAngle", "rightHipMinAngle", "torsoLeanMaxDeg"] : [])]
    case "SHOULDER_PRESS": return ["leftElbowMaxAngle", "rightElbowMaxAngle"]
    case "BICEP_CURL": case "TRICEPS_EXTENSION": return [...elbows, ...upper, ...(view === "SIDE" ? ["torsoInclinationChangeDeg"] : [])]
    case "PUSH_UP": return [...elbows, view === "SIDE" ? "bodyLineDeviationDeg" : "shoulderHeightDifferenceDeg"]
    case "BENT_OVER_ROW": return [...elbows, ...(view === "SIDE" ? ["torsoInclinationDeg", "torsoInclinationChangeDeg"] : [])]
    case "SIDE_BEND": return view === "FRONT" ? ["peakLateralFlexionDeg", "returnToNeutralDeviationDeg"] : []
    default: return []
  }
}
export function allowedAsymmetries(exercise: string, view: string): string[] {
  if (view !== "FRONT") return []
  if (exercise === "SQUAT") return ["kneeAngleDifferenceDeg"]
  if (exercise === "BICEP_CURL") return ["contralateralRomDifferenceDeg"]
  if (exercise === "SIDE_BEND") return ["lateralRangeDifferenceDeg"]
  return EXERCISES.includes(exercise) ? ["elbowAngleDifferenceDeg"] : []
}
export function allowedIssues(exercise: string, view: string): string[] {
  if (exercise === "SQUAT") return view === "FRONT" ? ["KNEE_VALGUS", "KNEE_ASYMMETRY"] : view === "SIDE" ? ["FORWARD_LEAN"] : []
  if (view === "UNKNOWN") return []
  if (exercise === "SIDE_BEND") return view === "FRONT" ? ["LATERAL_RANGE_ASYMMETRY", "RETURN_TO_NEUTRAL_VARIATION"] : []
  return [
    ...(view === "FRONT" && exercise !== "BICEP_CURL" ? ["ELBOW_ASYMMETRY"] : []),
    ...(["BICEP_CURL", "TRICEPS_EXTENSION"].includes(exercise) ? ["UPPER_ARM_POSITION_VARIATION"] : []),
    ...(view === "SIDE" && ["BICEP_CURL", "BENT_OVER_ROW", "TRICEPS_EXTENSION"].includes(exercise) ? ["TRUNK_POSITION_VARIATION"] : []),
    ...(view === "SIDE" && exercise === "PUSH_UP" ? ["BODY_LINE_VARIATION"] : []),
  ]
}
export const DEFAULT_COACHING: Record<string, string> = {
  BICEP_CURL: "팔꿈치 위치를 확인하며 천천히 움직여 보세요.",
  PUSH_UP: "몸통 정렬을 확인하며 천천히 움직여 보세요.",
  BENT_OVER_ROW: "몸통 기울기를 확인하며 팔꿈치를 당겨 보세요.",
  TRICEPS_EXTENSION: "상완 위치를 확인하며 팔꿈치를 펴 보세요.",
  SIDE_BEND: "몸통을 옆으로 기울였다가 중앙으로 천천히 돌아오세요.",
}
export const EXERCISE_PATTERNS: Record<string, RegExp> = {
  SQUAT: /스쿼트|squat/i, SHOULDER_PRESS: /숄더.?프레스|shoulder.?press/i,
  BICEP_CURL: /바이셉.?컬|이두.?컬|bicep[s]?.?curl/i, PUSH_UP: /푸시업|푸쉬업|팔굽혀펴기|push[ -]?up/i,
  BENT_OVER_ROW: /벤트.?오버|덤벨.?로우|bent[ -]?over|dumbbell.?row/i,
  TRICEPS_EXTENSION: /트라이셉스|삼두.?익스텐션|triceps.?extension/i, SIDE_BEND: /사이드.?벤드|side.?bend/i,
}
