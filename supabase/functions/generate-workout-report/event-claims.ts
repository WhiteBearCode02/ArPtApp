type Event = Record<string, unknown>
type Rep = Record<string, unknown>
export function eventKey(e: Event): string { return `event:${e.type}:${e.side ?? "UNKNOWN"}:${e.phase ?? "UNKNOWN"}` }
const sides = [ ["LEFT", /왼쪽|좌측|왼\s*(?:무릎|팔)|\bleft\b/i], ["RIGHT", /오른쪽|우측|오른\s*(?:무릎|팔)|\bright\b/i] ] as const
const phases = [ ["DESCENT", /내려가|내려오|내려갈|내려가기|하강|복귀|\bdescent\b/i],
  ["BOTTOM", /하단|최저점|\bbottom\b/i], ["ASCENT", /올라가|올라오|올라갈|올라가기|밀어\s*올|상승|\bascent\b/i] ] as const

export function phaseLabels(exercise: string): string[] {
  switch (exercise) {
    case "BICEP_CURL": return ["팔을 굽히는 구간", "최대 굽힘 구간", "팔을 펴는 구간"]
    case "PUSH_UP": return ["내려가는 구간", "하단 구간", "밀어올리는 구간"]
    case "BENT_OVER_ROW": return ["당기는 구간", "최대 당김 구간", "돌아가는 구간"]
    case "TRICEPS_EXTENSION": return ["팔꿈치를 굽히는 구간", "전환 구간", "팔꿈치를 펴는 구간"]
    case "SIDE_BEND": return ["옆으로 기울이는 구간", "최대 기울임 구간", "중앙으로 돌아오는 구간"]
    default: return ["내려가는 구간", "하단 구간", "올라가는 구간"]
  }
}
function phasePatterns(exercise: string): readonly (readonly [string, RegExp])[] {
  const canonical = ["DESCENT", "BOTTOM", "ASCENT"]
  const aliases: Record<string, string[]> = {
    BICEP_CURL: ["팔을 굽히는|flexing", "최대 굽힘|peak flexion", "팔을 펴는|복귀|extending"],
    BENT_OVER_ROW: ["당기는 구간|pulling", "최대 당김|peak pull", "돌아가는|복귀|returning"],
    TRICEPS_EXTENSION: ["팔꿈치를 굽히는|복귀|flexing", "전환|bottom", "팔꿈치를 펴는|extending"],
    SIDE_BEND: ["옆으로 기울이는|bending", "최대 기울임|peak", "중앙으로 돌아오는|복귀|returning"],
  }
  return aliases[exercise]?.map((pattern, i) => [canonical[i], new RegExp(pattern + `|\\b${canonical[i]}\\b`, "i")] as const) ?? phases
}

/** Requires an actual referenced event, not just a left/right angle or unrelated duration. */
export function directionalClaims(texts: unknown[], reps: Rep[], references?: Set<string>): void {
  const exercise = String(reps[0]?.exerciseType ?? "")
  const observations = reps.flatMap(r => (r.errorEvents as Event[]).map(e => ({ rep: r.repNumber, event: e })))
    .filter(({ event: e }) => !references || references.has(eventKey(e)) || references.has(`issue:${e.type}`))
  for (const text of texts as string[]) for (const sentence of text.split(/[;\n!?。]|(?<!\d)\.(?!\d)/)) {
    const requestedSides = sides.filter(([, re]) => re.test(sentence)).map(([v]) => v)
    const requestedPhases = phasePatterns(exercise).filter(([, re]) => re.test(sentence)).map(([v]) => v)
    if (!requestedSides.length && !requestedPhases.length) continue
    // Explicit V2 identity statement only; an active arm never authorizes an issue/phase claim.
    if (!requestedPhases.length && requestedSides.length === 1 && ["BICEP_CURL", "SIDE_BEND"].includes(exercise) &&
      /^(?:사용 팔|움직인 방향)\s*[:：]\s*(?:왼쪽|오른쪽)$/.test(sentence.trim()) &&
      reps.some(r => r.activeSide === requestedSides[0]) && (!references || references.has("activeSide"))) continue
    const types = /무릎|knee/i.test(sentence) ? ["KNEE_VALGUS", "KNEE_ASYMMETRY"] : /몸통|상체|torso/i.test(sentence)
      ? ["FORWARD_LEAN", "TRUNK_POSITION_VARIATION", "BODY_LINE_VARIATION", "LATERAL_RANGE_ASYMMETRY", "RETURN_TO_NEUTRAL_VARIATION"] : /팔|상완|elbow/i.test(sentence) ? ["ELBOW_ASYMMETRY", "UPPER_ARM_POSITION_VARIATION"] : null
    const mentionedReps = [...sentence.matchAll(/(\d+)\s*(?:회차|번째)/g)].map(m => Number(m[1]))
    const matching = observations.filter(({ rep, event: e }) => (!types || types.includes(e.type as string)) &&
      (!mentionedReps.length || mentionedReps.includes(rep as number))).map(o => o.event)
    for (const side of requestedSides.length ? requestedSides : [null]) for (const phase of requestedPhases.length ? requestedPhases : [null]) {
      if (!matching.some(e => (side === null || e.side === side) && (phase === null || e.phase === phase))) throw new Error("Unobserved side/phase claim")
    }
  }
}

export function localEventFeedback(e: Event, exercise = ""): string {
  const labels = phaseLabels(exercise)
  const phase = e.phase === "DESCENT" ? labels[0] : e.phase === "BOTTOM" ? labels[1] : e.phase === "ASCENT" ? labels[2] : ""
  const prefix = phase ? `${phase}에서 ` : ""
  const side = e.side === "LEFT" ? "왼쪽 " : e.side === "RIGHT" ? "오른쪽 " : ""
  switch (e.type) {
    case "KNEE_VALGUS": return `${prefix}${side}무릎 방향을 발 방향에 맞춰 보세요.`
    case "KNEE_ASYMMETRY": return `${prefix}양쪽 무릎이 함께 움직이는지 확인해 보세요.`
    case "ELBOW_ASYMMETRY": return `${prefix}양팔이 함께 움직이도록 천천히 진행해 보세요.`
    case "FORWARD_LEAN": return `${prefix}몸통 기울기를 확인하며 편안하게 움직여 보세요.`
    case "UPPER_ARM_POSITION_VARIATION": return `${prefix}${side}상완 위치를 확인하며 천천히 움직여 보세요.`
    case "TRUNK_POSITION_VARIATION": return `${prefix}${side}몸통 기울기 변화를 확인하며 천천히 움직여 보세요.`
    case "BODY_LINE_VARIATION": return `${prefix}${side}몸통 정렬을 확인하며 천천히 움직여 보세요.`
    case "LATERAL_RANGE_ASYMMETRY": return `${prefix}${side}앞서 기록한 반대 방향과 움직임 범위를 함께 확인해 보세요.`
    case "RETURN_TO_NEUTRAL_VARIATION": return `${prefix}${side}시작 위치로 천천히 돌아오는지 확인해 보세요.`
    default: throw new Error("Unsupported feedback")
  }
}
