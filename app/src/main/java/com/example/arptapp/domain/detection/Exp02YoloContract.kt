package com.example.arptapp.domain.detection

/**
 * Exact-source-backed candidate implementation, deliberately NOT named "Verified".
 * Source evidence: docs/ml/EXP02_ANDROID_INFERENCE_CONTRACT.md.
 *
 * TODO(EXP02_EXPORT_CONTRACT): OpenCV INTER_LINEAR uint8 rounding is not bitwise reproduced by
 * the independent bilinear implementation (observed <= 1/255, not mere float error). Review that
 * difference with an actual-model golden before changing this CODE-LEVEL gate. No env/property,
 * remote flag, or model presence can promote it. Android native compatibility is separate.
 */
object Exp02YoloContract {
    const val SOURCE_VERSION = "8.4.176"
    val verification: ExportContractVerification get() = ExportContractVerification.NOT_VERIFIED

    fun <F> components(preprocessor: YoloPreprocessor<F>) = YoloContractComponents(
        verification, preprocessor, Exp02OutputDecoder(), Exp02PostProcessor(),
        YoloStabilizationPolicy(minimumConfidence = Exp02DetectionPolicy.APP_ENGINEERING_INITIAL_STABLE_CONFIDENCE))
}
