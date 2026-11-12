package com.example.arptapp.domain.report

/** Shared Korean phrase API for TTS, local report and exact server allowlist. */
object LocalEventPresentation {
    fun phase(value: MovementPhase?): String = when (value) {
        MovementPhase.DESCENT -> "내려가는 구간"
        MovementPhase.BOTTOM -> "하단 구간"
        MovementPhase.ASCENT -> "올라가는 구간"
        null -> "구간 미확인"
    }
    fun side(value: BodySide?): String = when (value) { BodySide.LEFT -> "왼쪽"; BodySide.RIGHT -> "오른쪽"; null -> "" }
    fun feedback(event: FormErrorEvent, exercise: String? = null): String {
        if (event.type in ExerciseMetricPolicy.expandedEvents) {
            val prefix = event.phase?.let { "${ExerciseReportLanguage.phase(exercise.orEmpty(), it)}에서 " }.orEmpty()
            val side = event.side?.let { "${side(it)} " }.orEmpty()
            val advice = when (event.type) {
                FormErrorType.UPPER_ARM_POSITION_VARIATION -> "상완 위치를 확인하며 천천히 움직여 보세요."
                FormErrorType.TRUNK_POSITION_VARIATION -> "몸통 기울기 변화를 확인하며 천천히 움직여 보세요."
                FormErrorType.BODY_LINE_VARIATION -> "몸통 정렬을 확인하며 천천히 움직여 보세요."
                FormErrorType.LATERAL_RANGE_ASYMMETRY -> "앞서 기록한 반대 방향과 움직임 범위를 함께 확인해 보세요."
                else -> "시작 위치로 천천히 돌아오는지 확인해 보세요."
            }
            return prefix + side + advice
        }
        if (event.side == null && event.phase == null) return LocalRepFeedback.forIssue(event.type)
        val prefix = event.phase?.let { "${if (exercise != null && exercise !in listOf("SQUAT", "SHOULDER_PRESS")) ExerciseReportLanguage.phase(exercise, it) else phase(it)}에서 " }.orEmpty()
        return when (event.type) {
            FormErrorType.KNEE_VALGUS -> "${prefix}${side(event.side).let { if (it.isBlank()) "무릎" else "$it 무릎" }} 방향을 발 방향에 맞춰 보세요."
            FormErrorType.KNEE_ASYMMETRY -> "${prefix}양쪽 무릎이 함께 움직이는지 확인해 보세요."
            FormErrorType.ELBOW_ASYMMETRY -> "${prefix}양팔이 함께 움직이도록 천천히 진행해 보세요."
            FormErrorType.FORWARD_LEAN -> "${prefix}몸통 기울기를 확인하며 편안하게 움직여 보세요."
            else -> LocalRepFeedback.forIssue(event.type)
        }
    }
    fun observation(event: FormErrorEvent): String {
        val part = when (event.type) {
            FormErrorType.KNEE_VALGUS -> "${side(event.side).let { if (it.isBlank()) "무릎" else "$it 무릎" }}의 안쪽 이동 후보"
            FormErrorType.KNEE_ASYMMETRY -> "좌우 무릎 각도 차이 후보"
            FormErrorType.ELBOW_ASYMMETRY -> "좌우 팔꿈치 각도 차이 후보"
            FormErrorType.FORWARD_LEAN -> "몸통 기울기 후보"
            else -> "관찰 후보"
        }
        return "${phase(event.phase)} · $part · 초기 우선도 ${event.severity} · 유효 ${event.occurrenceFrames}프레임"
    }
}
