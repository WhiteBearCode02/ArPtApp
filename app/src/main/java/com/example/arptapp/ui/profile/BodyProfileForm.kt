package com.example.arptapp.ui.profile

import com.example.arptapp.data.preferences.UserSettings
import com.example.arptapp.databinding.ActivityProfileBinding

/**
 * 신체정보 폼의 View 읽기/쓰기와 필드 오류 표시만 담당한다.
 * 저장 대상 계정 선택 및 로컬/클라우드 저장은 호출자가 담당하여 UI 검증에 인증 의존성을 두지 않는다.
 */
class BodyProfileForm(private val binding: ActivityProfileBinding) {
    fun bind(settings: UserSettings) {
        binding.etProfileNickname.setText(settings.nickname)
        binding.etProfileHeight.setText(BodyProfileInputValidator.formatNumber(settings.heightCm))
        binding.etProfileWeight.setText(BodyProfileInputValidator.formatNumber(settings.weightKg))
        binding.etMuscleMass.setText(BodyProfileInputValidator.formatNumber(settings.skeletalMuscleMassKg))
        binding.etBodyFat.setText(BodyProfileInputValidator.formatNumber(settings.bodyFatPercentage))
    }

    /** 오류를 필드에 표시하며 유효한 입력일 때만 저장 가능한 값을 반환한다. */
    fun validatedValues(): BodyProfileValues? {
        clearErrors()
        val result = BodyProfileInputValidator.validate(
            BodyProfileInput(
                nickname = binding.etProfileNickname.text?.toString(),
                heightCm = binding.etProfileHeight.text?.toString(),
                weightKg = binding.etProfileWeight.text?.toString(),
                skeletalMuscleMassKg = binding.etMuscleMass.text?.toString(),
                bodyFatPercentage = binding.etBodyFat.text?.toString()
            )
        )
        return when (result) {
            is BodyProfileValidation.Valid -> result.values
            is BodyProfileValidation.Invalid -> {
                when (result.field) {
                    BodyProfileField.NICKNAME -> binding.tilNickname
                    BodyProfileField.HEIGHT -> binding.tilHeight
                    BodyProfileField.WEIGHT -> binding.tilWeight
                    BodyProfileField.MUSCLE_MASS -> binding.tilMuscleMass
                    BodyProfileField.BODY_FAT -> binding.tilBodyFat
                }.error = result.message
                null
            }
        }
    }

    private fun clearErrors() {
        binding.tilNickname.error = null
        binding.tilHeight.error = null
        binding.tilWeight.error = null
        binding.tilMuscleMass.error = null
        binding.tilBodyFat.error = null
    }
}
