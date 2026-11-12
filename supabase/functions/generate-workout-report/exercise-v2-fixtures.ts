import { DEFAULT_COACHING } from "./exercise-matrix.ts"

/** Synthetic scalar fixtures only, never real camera frames, model accuracy or a medical benchmark. */
export function v2Fixture(exercise: string) {
  const joints: Record<string, number | null> = {leftKneeMinAngle:null,rightKneeMinAngle:null,leftHipMinAngle:null,rightHipMinAngle:null,
    leftElbowMaxAngle:null,rightElbowMaxAngle:null,torsoLeanMaxDeg:null}
  const asym: Record<string, number | null> = {kneeAngleDifferenceDeg:null,hipAngleDifferenceDeg:null,elbowAngleDifferenceDeg:null}
  let activeSide: string | null = null
  if(exercise === "SQUAT") {joints.leftKneeMinAngle=90;joints.rightKneeMinAngle=92;asym.kneeAngleDifferenceDeg=2}
  else if(exercise === "SHOULDER_PRESS") {joints.leftElbowMaxAngle=165;joints.rightElbowMaxAngle=163;asym.elbowAngleDifferenceDeg=2}
  else if(exercise === "SIDE_BEND") {joints.peakLateralFlexionDeg=25;joints.returnToNeutralDeviationDeg=1;activeSide="RIGHT"}
  else {
    joints.leftElbowMinAngle=60;joints.leftElbowMaxAngle=170;joints.leftElbowRomDeg=110
    if(exercise === "BICEP_CURL") {activeSide="LEFT";joints.upperArmDriftLeftDeg=2}
    else {joints.rightElbowMinAngle=62;joints.rightElbowMaxAngle=170;joints.rightElbowRomDeg=108;asym.elbowAngleDifferenceDeg=2}
    if(exercise === "TRICEPS_EXTENSION") {joints.upperArmDriftLeftDeg=2;joints.upperArmDriftRightDeg=2}
    if(exercise === "PUSH_UP") joints.shoulderHeightDifferenceDeg=1
  }
  return {schemaVersion:2,exerciseType:exercise,totalReps:1,averageScore:80,bestRepNumber:1,weakestRepNumber:1,
    dominantIssues:[] as string[],omittedReps:0,repAnalyses:[{repNumber:1,exerciseType:exercise,cameraView:"FRONT",totalDurationMs:1200 as number|null,
      phaseDurations:{descentMs:500 as number|null,bottomMs:200 as number|null,ascentMs:500 as number|null},
      jointMetrics:joints,asymmetryMetrics:asym,errorEvents:[] as Record<string,unknown>[],strengths:[] as string[],improvementCandidates:[] as string[],
      localPriorityFeedback:DEFAULT_COACHING[exercise]??"회차가 기록되었습니다. 편안한 범위에서 천천히 반복해 보세요.",
      score:80 as number|null,observedFrames:12,partialCapture:false,phaseModelVersion:2,activeSide}]}
}
