package com.example.arptapp.domain.detection

/**
 * Separate export: user-reported Python PT/TFLite validation does NOT verify Android native parity.
 * Candidate RGB/BCHW/FLOAT32 /255, letterbox and normalized xywh/NMS algorithms are shared, but
 * EXP02 evidence cannot promote EXP03. TODO(EXP03_ANDROID_EXPORT_CONTRACT): actual Golden + native.
 * This state is code-reviewed only: no preference, fixture, model or remote flag can promote it.
 */
object Exp03YoloContract {
    val verification: ExportContractVerification get() = ExportContractVerification.NOT_VERIFIED
    fun <F> components(preprocessor: YoloPreprocessor<F>) = YoloRaw640Algorithms.components(
        KnownYoloModels.EXP03.modelId, verification, preprocessor)
}
