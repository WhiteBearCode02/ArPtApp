type Event = Record<string, unknown>
type Rep = Record<string, unknown>
export function eventKey(e: Event): string { return `event:${e.type}:${e.side ?? "UNKNOWN"}:${e.phase ?? "UNKNOWN"}` }
const sides = [ ["LEFT", /왼쪽|좌측|왼\s*(?:무릎|팔)|\bleft\b/i], ["RIGHT", /오른쪽|우측|오른\s*(?:무릎|팔)|\bright\b/i] ] as const
const phases = [ ["DESCENT", /내려가|내려오|내려갈|내려가기|하강|복귀|\bdescent\b/i],
  ["BOTTOM", /하단|최저점|\bbottom\b/i], ["ASCENT", /올라가|올라오|올라갈|올라가기|밀어\s*올|상승|\bascent\b/i] ] as const

/** Requires an actual referenced event, not just a left/right angle or unrelated duration. */
export function directionalClaims(texts: unknown[], reps: Rep[], references?: Set<string>): void {
  const observations = reps.flatMap(r => (r.errorEvents as Event[]).map(e => ({ rep: r.repNumber, event: e })))
    .filter(({ event: e }) => !references || references.has(eventKey(e)) || references.has(`issue:${e.type}`))
  for (const text of texts as string[]) for (const sentence of text.split(/[;\n!?。]|(?<!\d)\.(?!\d)/)) {
    const requestedSides = sides.filter(([, re]) => re.test(sentence)).map(([v]) => v)
    const requestedPhases = phases.filter(([, re]) => re.test(sentence)).map(([v]) => v)
    if (!requestedSides.length && !requestedPhases.length) continue
    const types = /무릎|knee/i.test(sentence) ? ["KNEE_VALGUS", "KNEE_ASYMMETRY"] : /몸통|상체|torso/i.test(sentence)
      ? ["FORWARD_LEAN"] : /팔|elbow/i.test(sentence) ? ["ELBOW_ASYMMETRY"] : null
    const mentionedReps = [...sentence.matchAll(/(\d+)\s*(?:회차|번째)/g)].map(m => Number(m[1]))
    const matching = observations.filter(({ rep, event: e }) => (!types || types.includes(e.type as string)) &&
      (!mentionedReps.length || mentionedReps.includes(rep as number))).map(o => o.event)
    for (const side of requestedSides.length ? requestedSides : [null]) for (const phase of requestedPhases.length ? requestedPhases : [null]) {
      if (!matching.some(e => (side === null || e.side === side) && (phase === null || e.phase === phase))) throw new Error("Unobserved side/phase claim")
    }
  }
}

export function localEventFeedback(e: Event): string {
  const phase = e.phase === "DESCENT" ? "내려가는 구간" : e.phase === "BOTTOM" ? "하단 구간" : e.phase === "ASCENT" ? "올라가는 구간" : ""
  const prefix = phase ? `${phase}에서 ` : ""
  const side = e.side === "LEFT" ? "왼쪽 " : e.side === "RIGHT" ? "오른쪽 " : ""
  switch (e.type) {
    case "KNEE_VALGUS": return `${prefix}${side}무릎 방향을 발 방향에 맞춰 보세요.`
    case "KNEE_ASYMMETRY": return `${prefix}양쪽 무릎이 함께 움직이는지 확인해 보세요.`
    case "ELBOW_ASYMMETRY": return `${prefix}양팔이 함께 움직이도록 천천히 진행해 보세요.`
    case "FORWARD_LEAN": return `${prefix}몸통 기울기를 확인하며 편안하게 움직여 보세요.`
    default: throw new Error("Unsupported feedback")
  }
}
