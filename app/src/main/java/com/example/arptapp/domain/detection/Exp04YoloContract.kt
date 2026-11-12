package com.example.arptapp.domain.detection

/**
 * Source-reviewed 8.4.202 candidate ports. Colab PT/LiteRT parity does NOT verify Android.
 * TODO(EXP04_ANDROID_ACTUAL_GOLDEN): require native load/invoke + exact preprocessing/final
 * detection comparison on rights-cleared real images before a SEPARATE reviewed promotion.
 * No file, preference, build flag, fixture or test result changes this constant.
 */
object Exp04YoloContract {
    const val SOURCE_VERSION = "8.4.202"
    val verification: ExportContractVerification get() = ExportContractVerification.NOT_VERIFIED
    fun <F> components(preprocessor: YoloPreprocessor<F>) = YoloRaw640Algorithms.components(
        KnownYoloModels.EXP04.modelId, verification, preprocessor)
}
