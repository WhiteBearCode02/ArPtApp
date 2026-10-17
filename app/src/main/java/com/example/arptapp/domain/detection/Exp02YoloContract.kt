package com.example.arptapp.domain.detection

/**
 * VERIFIED means Python/Android runtime contract equivalence, NOT model accuracy or safety.
 * Evidence: docs/ml/EXP02_ANDROID_INFERENCE_CONTRACT.md and EXP02_VERIFIED_REVIEW.md.
 *
 * Reviewed actual negative / both-class positive Golden and Android native results support this
 * code-level promotion. General interpolation is not claimed bitwise identical: the existing
 * <=1/255 comparison bound is retained. No environment/property/model can promote a new export.
 * Realtime execution additionally requires YoloRuntimePolicy AND a ready canonical model.
 */
object Exp02YoloContract {
    const val SOURCE_VERSION = "8.4.176"
    val verification: ExportContractVerification get() = ExportContractVerification.VERIFIED

    fun <F> components(preprocessor: YoloPreprocessor<F>) = YoloContractComponents(
        verification, preprocessor, Exp02OutputDecoder(), Exp02PostProcessor(),
        YoloStabilizationPolicy(minimumConfidence = Exp02DetectionPolicy.APP_ENGINEERING_INITIAL_STABLE_CONFIDENCE))
}
