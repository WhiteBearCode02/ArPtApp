package com.example.arptapp.ui.profile

/** 편집 화면의 원문 입력. 저장이나 네트워크 호출 없이 [BodyProfileInputValidator]에서만 해석한다. */
data class BodyProfileInput(
    val nickname: String?,
    val heightCm: String?,
    val weightKg: String?,
    val skeletalMuscleMassKg: String?,
    val bodyFatPercentage: String?
)

data class BodyProfileValues(
    val nickname: String,
    val heightCm: Float,
    val weightKg: Float,
    val skeletalMuscleMassKg: Float?,
    val bodyFatPercentage: Float?
)

enum class BodyProfileField { NICKNAME, HEIGHT, WEIGHT, MUSCLE_MASS, BODY_FAT }

sealed interface BodyProfileValidation {
    data class Valid(val values: BodyProfileValues) : BodyProfileValidation
    data class Invalid(val field: BodyProfileField, val message: String) : BodyProfileValidation
}

/**
 * 개인설정 신체정보의 입력 규칙을 Android View와 분리한 순수 검증 API.
 *
 * 키/몸무게는 필수, 골격근량/체지방률은 미입력 시 null이다. 첫 오류만 반환하는 기존
 * 화면 흐름을 유지하며 쉼표 소수 입력을 허용한다. NaN/Infinity는 저장 값으로 허용하지 않는다.
 * 이 범위는 입력 오류 방지용이며 의료적 정상 범위를 의미하지 않는다.
 */
object BodyProfileInputValidator {
    fun validate(input: BodyProfileInput): BodyProfileValidation {
        val nickname = input.nickname?.trim().orEmpty()
        if (nickname.length !in 2..20) {
            return invalid(BodyProfileField.NICKNAME, "닉네임은 2~20자로 입력해 주세요.")
        }

        val height = number(input.heightCm)
        if (height == null || height !in 80f..250f) {
            return invalid(BodyProfileField.HEIGHT, "키는 80~250cm 범위로 입력해 주세요.")
        }
        val weight = number(input.weightKg)
        if (weight == null || weight !in 20f..350f) {
            return invalid(BodyProfileField.WEIGHT, "몸무게는 20~350kg 범위로 입력해 주세요.")
        }
        val muscleMass = number(input.skeletalMuscleMassKg)
        if (!input.skeletalMuscleMassKg.isNullOrBlank() &&
            (muscleMass == null || muscleMass !in 0f..200f)
        ) {
            return invalid(BodyProfileField.MUSCLE_MASS, "골격근량은 0~200kg 범위로 입력해 주세요.")
        }
        val bodyFat = number(input.bodyFatPercentage)
        if (!input.bodyFatPercentage.isNullOrBlank() && (bodyFat == null || bodyFat !in 0f..75f)) {
            return invalid(BodyProfileField.BODY_FAT, "체지방률은 0~75% 범위로 입력해 주세요.")
        }

        return BodyProfileValidation.Valid(
            BodyProfileValues(nickname, height, weight, muscleMass, bodyFat)
        )
    }

    /** 편집용 표시: 선택값 미입력은 빈 문자열, 정수값은 불필요한 .0 없이 표시한다. */
    fun formatNumber(value: Float?): String = when {
        value == null || !value.isFinite() -> ""
        value % 1f == 0f -> value.toInt().toString()
        else -> value.toString()
    }

    private fun number(value: String?): Float? =
        value?.trim()?.replace(',', '.')?.toFloatOrNull()?.takeIf { it.isFinite() }

    private fun invalid(field: BodyProfileField, message: String) =
        BodyProfileValidation.Invalid(field, message)
}
