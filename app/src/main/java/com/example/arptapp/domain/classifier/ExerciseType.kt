package com.example.arptapp.domain.classifier

enum class ExerciseType(val displayName: String) {
    IDLE("대기 중"),
    UNKNOWN("운동 감지 중"),
    SQUAT("스쿼트"),
    SHOULDER_PRESS("숄더 프레스"),
    LUNGE("런지"),
    BICEP_CURL("바이셉 컬"),
    PUSH_UP("푸시업"),
    BENT_OVER_ROW("벤트오버 덤벨 로우"),
    TRICEPS_EXTENSION("오버헤드 트라이셉스 익스텐션"),
    SIDE_BEND("덤벨 사이드 벤드");

    companion object {
        /** Explicit production capability registry. LUNGE stays readable but is not advertised. */
        val supported = linkedSetOf(SQUAT, SHOULDER_PRESS, BICEP_CURL, PUSH_UP,
            BENT_OVER_ROW, TRICEPS_EXTENSION, SIDE_BEND)
        val supportedNames get() = supported.map { it.name }.toSet()
        fun fromName(value: String) = entries.firstOrNull { it.name == value.uppercase(java.util.Locale.ROOT) }
        fun display(value: String) = fromName(value)?.displayName ?: value
    }
}
