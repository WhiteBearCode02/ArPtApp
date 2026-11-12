package com.example.arptapp.domain.report

import com.example.arptapp.domain.classifier.ExerciseType

/** Central Korean engineering coaching/phase vocabulary. Not medical advice or paper cutoffs. */
object ExerciseReportLanguage {
    fun strength(exercise: String, key: String): String =
        if (key == "BOTH_PHASES_CAPTURED" && exercise in setOf("BICEP_CURL", "BENT_OVER_ROW", "SIDE_BEND")) {
            when (exercise) {
                "BICEP_CURL" -> "팔을 굽히고 펴는 두 구간이 기록되었습니다."
                "BENT_OVER_ROW" -> "당기고 돌아가는 두 구간이 기록되었습니다."
                else -> "옆으로 기울이고 중앙으로 돌아오는 두 구간이 기록되었습니다."
            }
        } else LocalRepFeedback.strength(key)
    fun phase(exercise: String, phase: MovementPhase?): String {
        val labels = when (exercise) {
            "BICEP_CURL" -> listOf("팔을 굽히는 구간", "최대 굽힘 구간", "팔을 펴는 구간")
            "PUSH_UP" -> listOf("내려가는 구간", "하단 구간", "밀어올리는 구간")
            "BENT_OVER_ROW" -> listOf("당기는 구간", "최대 당김 구간", "돌아가는 구간")
            "TRICEPS_EXTENSION" -> listOf("팔꿈치를 굽히는 구간", "전환 구간", "팔꿈치를 펴는 구간")
            "SIDE_BEND" -> listOf("옆으로 기울이는 구간", "최대 기울임 구간", "중앙으로 돌아오는 구간")
            else -> listOf("내려가는 구간", if (exercise == "SHOULDER_PRESS") "전환 구간" else "하단 구간", "올라가는 구간")
        }
        return phase?.let { labels[it.ordinal] } ?: "구간 미확인"
    }
    fun defaultFeedback(exercise: String): String = when (exercise) {
        "BICEP_CURL" -> "팔꿈치 위치를 확인하며 천천히 움직여 보세요."
        "PUSH_UP" -> "몸통 정렬을 확인하며 천천히 움직여 보세요."
        "BENT_OVER_ROW" -> "몸통 기울기를 확인하며 팔꿈치를 당겨 보세요."
        "TRICEPS_EXTENSION" -> "상완 위치를 확인하며 팔꿈치를 펴 보세요."
        "SIDE_BEND" -> "몸통을 옆으로 기울였다가 중앙으로 천천히 돌아오세요."
        else -> "회차가 기록되었습니다. 편안한 범위에서 천천히 반복해 보세요."
    }
    fun observation(type: FormErrorType): String = when (type) {
        FormErrorType.UPPER_ARM_POSITION_VARIATION -> "상완 위치의 변화가 관찰되었습니다."
        FormErrorType.TRUNK_POSITION_VARIATION -> "몸통 기울기 변화가 관찰되었습니다."
        FormErrorType.BODY_LINE_VARIATION -> "몸통 정렬의 변화가 관찰되었습니다."
        FormErrorType.LATERAL_RANGE_ASYMMETRY -> "앞서 기록한 반대 방향과 움직임 범위 차이가 관찰되었습니다."
        FormErrorType.RETURN_TO_NEUTRAL_VARIATION -> "중앙 복귀 위치와 시작 위치의 차이가 관찰되었습니다."
        else -> "측정 가능한 범위에서 관찰 후보가 기록되었습니다."
    }
}

/** Pure speech API; event timing remains start-button / completed-rep, never per-frame. */
object ExerciseSpeechFormatter {
    fun sessionStart(type: ExerciseType?): String = if (type in ExerciseType.supported)
        "${type!!.displayName} 운동을 시작합니다." else "운동을 시작합니다"
    fun repCompleted(number: Int, feedback: String?): String = "${number}회 완료. ${feedback.orEmpty()}"
}
