package com.example.arptapp.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyProfileInputValidatorTest {
    private val validInput = BodyProfileInput("사용자", "170", "65", "30", "18")

    @Test fun validInputReturnsParsedValues() {
        val result = BodyProfileInputValidator.validate(validInput) as BodyProfileValidation.Valid
        assertEquals(BodyProfileValues("사용자", 170f, 65f, 30f, 18f), result.values)
    }

    @Test fun surroundingWhitespaceAndCommaDecimalAreNormalized() {
        val result = BodyProfileInputValidator.validate(
            validInput.copy(nickname = "  사용자  ", heightCm = " 170,5 ", weightKg = "65,2")
        ) as BodyProfileValidation.Valid
        assertEquals("사용자", result.values.nickname)
        assertEquals(170.5f, result.values.heightCm, 0f)
        assertEquals(65.2f, result.values.weightKg, 0f)
    }

    @Test fun optionalBlankValuesRemainNullRatherThanZero() {
        val result = BodyProfileInputValidator.validate(
            validInput.copy(skeletalMuscleMassKg = "  ", bodyFatPercentage = null)
        ) as BodyProfileValidation.Valid
        assertNull(result.values.skeletalMuscleMassKg)
        assertNull(result.values.bodyFatPercentage)
    }

    @Test fun minimumAndMaximumBoundsAreInclusive() {
        val minimum = validInput.copy(
            nickname = "가나", heightCm = "80", weightKg = "20",
            skeletalMuscleMassKg = "0", bodyFatPercentage = "0"
        )
        val maximum = validInput.copy(
            nickname = "가".repeat(20), heightCm = "250", weightKg = "350",
            skeletalMuscleMassKg = "200", bodyFatPercentage = "75"
        )
        assertTrue(BodyProfileInputValidator.validate(minimum) is BodyProfileValidation.Valid)
        assertTrue(BodyProfileInputValidator.validate(maximum) is BodyProfileValidation.Valid)
    }

    @Test fun nicknameIsRequiredAndLengthLimited() {
        listOf(null, "", "  ", "가", "가".repeat(21)).forEach { value ->
            assertInvalid(validInput.copy(nickname = value), BodyProfileField.NICKNAME)
        }
    }

    @Test fun requiredNumbersRejectEmptyMalformedAndOutOfRange() {
        listOf(null, "", "문자", "79.9", "250.1", "NaN", "Infinity").forEach { value ->
            assertInvalid(validInput.copy(heightCm = value), BodyProfileField.HEIGHT)
        }
        listOf(null, "", "문자", "19.9", "350.1", "NaN", "-Infinity").forEach { value ->
            assertInvalid(validInput.copy(weightKg = value), BodyProfileField.WEIGHT)
        }
    }

    @Test fun suppliedOptionalNumbersMustBeFiniteAndInRange() {
        listOf("문자", "-0.1", "200.1", "NaN", "Infinity").forEach { value ->
            assertInvalid(validInput.copy(skeletalMuscleMassKg = value), BodyProfileField.MUSCLE_MASS)
        }
        listOf("문자", "-0.1", "75.1", "NaN", "Infinity").forEach { value ->
            assertInvalid(validInput.copy(bodyFatPercentage = value), BodyProfileField.BODY_FAT)
        }
    }

    @Test fun validationPreservesFirstErrorOrder() {
        assertInvalid(BodyProfileInput("", "", "", "잘못된 값", "잘못된 값"), BodyProfileField.NICKNAME)
        assertInvalid(validInput.copy(heightCm = "", weightKg = ""), BodyProfileField.HEIGHT)
        assertInvalid(validInput.copy(skeletalMuscleMassKg = "-1", bodyFatPercentage = "-1"), BodyProfileField.MUSCLE_MASS)
    }

    @Test fun formattingPreservesOptionalBlankAndDecimalValues() {
        assertEquals("", BodyProfileInputValidator.formatNumber(null))
        assertEquals("", BodyProfileInputValidator.formatNumber(Float.NaN))
        assertEquals("", BodyProfileInputValidator.formatNumber(Float.POSITIVE_INFINITY))
        assertEquals("0", BodyProfileInputValidator.formatNumber(0f))
        assertEquals("170", BodyProfileInputValidator.formatNumber(170f))
        assertEquals("170.5", BodyProfileInputValidator.formatNumber(170.5f))
    }

    private fun assertInvalid(input: BodyProfileInput, field: BodyProfileField) {
        val result = BodyProfileInputValidator.validate(input) as BodyProfileValidation.Invalid
        assertEquals(field, result.field)
        assertTrue(result.message.isNotBlank())
    }
}
