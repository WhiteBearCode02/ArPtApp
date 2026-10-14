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
    fun feedback(event: FormErrorEvent): String {
        if (event.side == null && event.phase == null) return LocalRepFeedback.forIssue(event.type)
        val prefix = event.phase?.let { "${phase(it)}에서 " }.orEmpty()
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
