package com.example.arptapp.domain.classifier

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.session.SessionExerciseLock
import com.example.arptapp.domain.report.ExerciseSpeechFormatter
import org.junit.Assert.*
import org.junit.Test

class SevenExerciseClassifierTest {
    private fun features(type: ExerciseType): ExerciseFeatures {
        val side = ExerciseSideFeatures(knee=170.0,hip=170.0,elbow=90.0,wristAboveShoulder=false,
            legVisibility=1f,armVisibility=1f,torsoInclination=0.0,upperArmDownInclination=0.0,upperArmUpInclination=180.0)
        val f = when(type) {
            ExerciseType.SQUAT -> side.copy(knee=90.0,hip=120.0,torsoInclination=30.0)
            ExerciseType.SHOULDER_PRESS -> side.copy(wristAboveShoulder=true,upperArmUpInclination=90.0)
            ExerciseType.PUSH_UP -> side.copy(torsoInclination=90.0,upperArmDownInclination=90.0)
            ExerciseType.BENT_OVER_ROW -> side.copy(hip=100.0,torsoInclination=50.0,upperArmDownInclination=45.0)
            ExerciseType.TRICEPS_EXTENSION -> side.copy(wristAboveShoulder=true,upperArmUpInclination=0.0,upperArmDownInclination=180.0)
            ExerciseType.SIDE_BEND -> side.copy(elbow=170.0,torsoInclination=25.0)
            else -> side
        }
        return ExerciseFeatures(f,f,if(type==ExerciseType.SIDE_BEND)25.0 else null)
    }
    @Test fun allSevenRequireStableDetectionBeforeTheSameSessionLock() {
        for (type in ExerciseType.supported) {
            val c=ExerciseClassifier();val lock=SessionExerciseLock()
            repeat(4) { assertEquals(ExerciseType.UNKNOWN,c.detectFeatures(features(type),CameraView.FRONT));assertFalse(lock.observe(c.stableDetectedExercise)) }
            assertEquals(type,c.detectFeatures(features(type),CameraView.FRONT));assertTrue(lock.observe(c.stableDetectedExercise))
            assertFalse(lock.observe(ExerciseType.SQUAT));assertEquals(type,lock.sessionExerciseType)
        }
    }
    @Test fun uncertainMissingFeaturesAbstainAndLungeIsNotAProductionChoice() {
        val c=ExerciseClassifier()
        repeat(10){ assertEquals(ExerciseType.UNKNOWN,c.detectFeatures(ExerciseFeatures(ExerciseSideFeatures(),ExerciseSideFeatures()),CameraView.FRONT)) }
        assertFalse(SessionExerciseLock().observe(ExerciseType.LUNGE));assertFalse(ExerciseType.LUNGE in ExerciseType.supported)
    }
    @Test fun sideBendIsNotInferredFromSideOrUnknownView() {
        for(view in listOf(CameraView.SIDE,CameraView.UNKNOWN)) {
            val c=ExerciseClassifier();repeat(10){ assertEquals(ExerciseType.UNKNOWN,c.detectFeatures(features(ExerciseType.SIDE_BEND),view)) }
        }
    }
    @Test fun allSevenSpeechNamesUseSameStartAndRepFormatter() {
        for(type in ExerciseType.supported) {
            assertEquals("${type.displayName} 운동을 시작합니다.",ExerciseSpeechFormatter.sessionStart(type))
            assertEquals("1회 완료. 관찰 안내",ExerciseSpeechFormatter.repCompleted(1,"관찰 안내"))
        }
        assertEquals("운동을 시작합니다",ExerciseSpeechFormatter.sessionStart(ExerciseType.UNKNOWN))
    }
}
