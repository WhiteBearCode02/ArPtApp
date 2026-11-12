import { object } from "./contract-values.ts"
import { eventKey } from "./event-claims.ts"

/** Conservative lexical measurement guard. It cannot prove natural-language semantic truth. */
export function metricClaims(texts: unknown[], reps: Record<string,unknown>[], references?: Set<string>): void {
  const fields = new Set<string>()
  const issues = new Set<string>()
  for(const rep of reps) {
    for(const group of ["jointMetrics","asymmetryMetrics"]) for(const [key,value] of Object.entries(object(rep[group]))) {
      if(typeof value === "number" && (!references || references.has(`${group}.${key}`))) fields.add(key)
    }
    for(const raw of rep.errorEvents as Record<string,unknown>[]) {
      if(!references || references.has(eventKey(raw)) || references.has(`issue:${raw.type}`)) issues.add(String(raw.type))
    }
  }
  const has=(pattern:RegExp)=>[...fields].some(k=>pattern.test(k))
  const checks:[RegExp,boolean][]=[
    [/무릎|\bknee\b/i,has(/Knee|knee/)||[...issues].some(k=>k.includes("KNEE"))],
    [/고관절|hip\s*angle/i,has(/Hip|hip/)||issues.has("HIP_ASYMMETRY")],
    [/팔꿈치|\belbow\b/i,has(/Elbow|elbow/)||issues.has("ELBOW_ASYMMETRY")],
    [/상완|upper\s*arm/i,has(/upperArm/)||issues.has("UPPER_ARM_POSITION_VARIATION")],
    [/몸통|상체|torso|trunk/i,has(/torso|bodyLine|Lateral|returnToNeutral/)||[...issues].some(k=>/LEAN|TRUNK|BODY_LINE|LATERAL|RETURN_TO/.test(k))],
    [/어깨\s*높이|shoulder\s*height/i,has(/shoulderHeight/)],
    [/측굴|lateral\s*flexion/i,has(/Lateral/)],
  ]
  for(const text of texts as string[]) for(const [pattern,measured] of checks) {
    if(pattern.test(text)&&!measured) throw Error("Unmeasured body metric claim")
  }
}
