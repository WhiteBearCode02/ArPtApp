package com.example.arptapp.domain.report

/** Same seven-name confusion guard as the Edge contract. Not a general semantic LLM verifier. */
object ExerciseNameClaims {
    private val patterns = mapOf(
        "SQUAT" to "스쿼트|squat", "SHOULDER_PRESS" to "숄더.?프레스|shoulder.?press",
        "BICEP_CURL" to "바이셉.?컬|이두.?컬|bicep[s]?.?curl",
        "PUSH_UP" to "푸시업|푸쉬업|팔굽혀펴기|push[ -]?up",
        "BENT_OVER_ROW" to "벤트.?오버|덤벨.?로우|bent[ -]?over|dumbbell.?row",
        "TRICEPS_EXTENSION" to "트라이셉스|삼두.?익스텐션|triceps.?extension",
        "SIDE_BEND" to "사이드.?벤드|side.?bend")
        .mapValues { Regex(it.value, RegexOption.IGNORE_CASE) }
    fun mentionsOther(text: String, exercise: String) = patterns.any { (type, pattern) -> type != exercise && pattern.containsMatchIn(text.replace(Regex("\\s+"), "")) }
}
