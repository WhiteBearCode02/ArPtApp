package com.example.arptapp.domain.analyzer

/** Joint indices are MediaPipe indices and remain anatomical after screen mirroring. */
data class CoachingFeedback(val evaluable: Boolean, val jointIndices: Set<Int>, val message: String, val tags: List<String>)

object CoachingFeedbackFactory {
    fun create(measurement: AngleMeasurement, errors: List<String>): CoachingFeedback {
        if (!measurement.isEvaluable) return CoachingFeedback(false, emptySet(),
            "측정 대기 · 관절이 가려지지 않도록 전신을 화면에 맞춰 주세요", emptyList())
        val joints = mutableSetOf<Int>()
        val messages = mutableListOf<String>()
        if (FormErrorAnalyzer.ERROR_KNEE_VALGUS in errors) {
            joints += listOf(25, 26)
            messages += "무릎 방향을 발끝과 나란히 맞춰 보세요"
        }
        if (FormErrorAnalyzer.ERROR_FORWARD_LEAN in errors) {
            joints += listOf(11, 12, 23, 24)
            messages += "몸통 기울기를 확인하며 천천히 움직여 보세요"
        }
        return CoachingFeedback(true, joints, messages.joinToString(" · ").ifBlank {
            "관절 추적 중 · 호흡과 동작 속도를 일정하게 유지해 주세요"
        }, errors)
    }
}
