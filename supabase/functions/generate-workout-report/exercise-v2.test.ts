import test from "node:test"
import assert from "node:assert/strict"
import { validateInput, validateReport, evidenceKeys } from "./contracts.ts"
import { EXERCISES, EXERCISE_PATTERNS } from "./exercise-matrix.ts"
import { localEventFeedback } from "./event-claims.ts"
import { v2Fixture } from "./exercise-v2-fixtures.ts"
import { buildPrompt } from "./prompt.ts"

function output(exercise: string) {
  return {exerciseType:exercise,summary:"1회가 관찰되었습니다.",strengths:[],improvements:[],priority:"측정 내용을 확인해 보세요.",
    nextWorkoutGoal:"편안한 범위에서 천천히 반복해 보세요.",repFeedback:[{repNumber:1,summary:"관찰된 움직임 범위가 기록되었습니다.",
      positive:"측정 가능한 구간을 기록했습니다.",improvement:"같은 촬영 조건을 확인해 보세요.",evidenceKeys:[exercise==="SQUAT"?"jointMetrics.leftKneeMinAngle":
        exercise==="SHOULDER_PRESS"?"jointMetrics.leftElbowMaxAngle":exercise==="SIDE_BEND"?"jointMetrics.peakLateralFlexionDeg":"jointMetrics.leftElbowRomDeg"]}]}
}
const names:Record<string,string> = {SQUAT:"스쿼트",SHOULDER_PRESS:"숄더 프레스",BICEP_CURL:"바이셉 컬",PUSH_UP:"푸시업",
  BENT_OVER_ROW:"벤트오버 덤벨 로우",TRICEPS_EXTENSION:"오버헤드 트라이셉스 익스텐션",SIDE_BEND:"덤벨 사이드 벤드"}
for(const exercise of EXERCISES) {
  test(`${exercise} exact missing-capture placeholder remains unscored, not a normal posture`,()=>{
    const input=v2Fixture(exercise),r=input.repAnalyses[0]
    r.partialCapture=true;r.observedFrames=0;r.score=null;r.totalDurationMs=null;r.activeSide=null
    for(const key of Object.keys(r.jointMetrics))r.jointMetrics[key]=null
    for(const key of Object.keys(r.asymmetryMetrics))r.asymmetryMetrics[key]=null
    r.phaseDurations={descentMs:null,bottomMs:null,ascentMs:null}
    r.localPriorityFeedback="이 회차의 측정 근거가 부족합니다."
    input.averageScore=null as unknown as number;input.bestRepNumber=null as unknown as number;input.weakestRepNumber=null as unknown as number
    assert.doesNotThrow(()=>validateInput(input))
    r.partialCapture=false;assert.throws(()=>validateInput(input));r.partialCapture=true
    r.activeSide="LEFT";assert.throws(()=>validateInput(input));r.activeSide=null
    r.observedFrames=1;assert.throws(()=>validateInput(input))
  })
  test(`${exercise} V2 accepts exactly its measured metrics and grounded explanation`,()=>{
    const input=validateInput(v2Fixture(exercise));assert.equal(input.schemaVersion,2)
    assert.doesNotThrow(()=>validateReport(output(exercise),input))
    assert.match(buildPrompt(input),/Schema 2 supports exactly/)
    assert.ok(!evidenceKeys(input.repAnalyses[0]).has("jointMetrics.notMeasured"))
  })
  test(`${exercise} refuses wrong exercise metrics, unknown fields and unsupported view`,()=>{
    const wrong=v2Fixture(exercise)
    wrong.repAnalyses[0].jointMetrics[exercise==="SQUAT"?"leftElbowRomDeg":"leftKneeMinAngle"]=80
    assert.throws(()=>validateInput(wrong))
    const unknown=v2Fixture(exercise);unknown.repAnalyses[0].jointMetrics.rawLandmarks=1
    assert.throws(()=>validateInput(unknown))
    const view=v2Fixture(exercise);view.repAnalyses[0].cameraView="UNKNOWN"
    assert.throws(()=>validateInput(view))
  })
  test(`${exercise} partial/NOT_EVALUATED stays nullable and cannot manufacture a score`,()=>{
    const partial=v2Fixture(exercise),r=partial.repAnalyses[0]
    r.cameraView="UNKNOWN";r.partialCapture=true;r.totalDurationMs=null;r.score=null;r.activeSide=null
    for(const k of Object.keys(r.jointMetrics)) r.jointMetrics[k]=null
    for(const k of Object.keys(r.asymmetryMetrics)) r.asymmetryMetrics[k]=null
    r.phaseDurations={descentMs:null,bottomMs:null,ascentMs:null}
    assert.doesNotThrow(()=>validateInput(partial));r.score=0
    assert.throws(()=>validateInput(partial))
  })
  for(const other of EXERCISES.filter(v=>v!==exercise)) test(`${exercise} explanation cannot rename exercise ${other}`,()=>{
    const input=validateInput(v2Fixture(exercise)),r=output(exercise);r.summary=`${names[other]} 동작을 확인해 보세요.`
    assert.ok(EXERCISE_PATTERNS[other].test(r.summary));assert.throws(()=>validateReport(r,input))
  })
  test(`${exercise} invented numbers, evidence, side, phase and diagnosis are rejected`,()=>{
    const input=validateInput(v2Fixture(exercise))
    for(const sentence of ["팔꿈치 각도가 179.9도입니다.","부상 위험이 높습니다.","정상 자세입니다.","오른쪽 상완의 변화가 관찰되었습니다."]){
      const r=output(exercise);r.repFeedback[0].summary=sentence;assert.throws(()=>validateReport(r,input),sentence)
    }
    const r=output(exercise);r.repFeedback[0].evidenceKeys=["jointMetrics.notMeasured"]
    assert.throws(()=>validateReport(r,input))
  })
}
for(const exercise of ["SQUAT","SHOULDER_PRESS"]) test(`${exercise} V1 remains valid; V2 fields cannot enter V1`,()=>{
  const legacy=v2Fixture(exercise);legacy.schemaVersion=1
  delete (legacy.repAnalyses[0] as Record<string,unknown>).activeSide
  assert.doesNotThrow(()=>validateInput(legacy));legacy.repAnalyses[0].jointMetrics.leftElbowRomDeg=80
  assert.throws(()=>validateInput(legacy))
})
for(const exercise of EXERCISES.slice(2)) test(`${exercise} cannot masquerade as V1`,()=>{
  const input=v2Fixture(exercise);input.schemaVersion=1;assert.throws(()=>validateInput(input))
})
test("curl active side authorizes only an exact identity statement, not an issue or a different side",()=>{
  const input=validateInput(v2Fixture("BICEP_CURL")),r=output("BICEP_CURL")
  r.repFeedback[0].summary="사용 팔: 왼쪽";r.repFeedback[0].evidenceKeys.push("activeSide")
  assert.doesNotThrow(()=>validateReport(r,input))
  r.repFeedback[0].summary="사용 팔: 오른쪽";assert.throws(()=>validateReport(r,input))
  r.repFeedback[0].summary="팔을 굽히는 구간에서 상완 변화가 관찰되었습니다.";assert.throws(()=>validateReport(r,input))
})

test("an existing numeric angle cannot be relabelled as an unmeasured joint",()=>{
  const input=validateInput(v2Fixture("BICEP_CURL")),r=output("BICEP_CURL")
  r.repFeedback[0].summary="팔꿈치 각도가 60도입니다."
  r.repFeedback[0].evidenceKeys=["jointMetrics.leftElbowMinAngle"]
  assert.doesNotThrow(()=>validateReport(r,input))
  r.repFeedback[0].summary="무릎 각도가 60도입니다."
  assert.throws(()=>validateReport(r,input))
  r.repFeedback[0].summary="몸통 각도가 60도입니다."
  assert.throws(()=>validateReport(r,input))
})
test("new events need matching exercise/view/metric/side/observed phase and exact local coaching",()=>{
  for(const exercise of EXERCISES.slice(2)) {
    const input=v2Fixture(exercise),r=input.repAnalyses[0]
    let type="UPPER_ARM_POSITION_VARIATION",side:string|null="LEFT",phase="ASCENT"
    if(exercise==="BICEP_CURL"||exercise==="TRICEPS_EXTENSION") r.jointMetrics.upperArmDriftLeftDeg=21
    if(exercise==="SIDE_BEND") {type="RETURN_TO_NEUTRAL_VARIATION";side="RIGHT";r.jointMetrics.returnToNeutralDeviationDeg=4}
    if(exercise==="PUSH_UP"||exercise==="BENT_OVER_ROW") {
      r.cameraView="SIDE";side=null
      for(const key of Object.keys(r.jointMetrics)) if(key.startsWith("right")||key.includes("Right")||key==="shoulderHeightDifferenceDeg")r.jointMetrics[key]=null
      for(const key of Object.keys(r.asymmetryMetrics))r.asymmetryMetrics[key]=null
      type=exercise==="PUSH_UP"?"BODY_LINE_VARIATION":"TRUNK_POSITION_VARIATION"
      r.jointMetrics[exercise==="PUSH_UP"?"bodyLineDeviationDeg":"torsoInclinationChangeDeg"]=21
    }
    const e={type,side,phase,severity:1,timestampMs:100,occurrenceFrames:3}
    r.errorEvents=[e];r.improvementCandidates=[type];input.dominantIssues=[type];r.localPriorityFeedback=localEventFeedback(e,exercise)
    assert.doesNotThrow(()=>validateInput(input),exercise)
    r.phaseDurations.ascentMs=null;assert.throws(()=>validateInput(input))
  }
})
