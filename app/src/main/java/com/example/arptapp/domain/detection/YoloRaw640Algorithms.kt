package com.example.arptapp.domain.detection

/**
 * Shared candidate algorithms, NOT transferable verification/provenance. Historical implementation
 * names remain to preserve EXP02 tests and references; no threshold or arithmetic changes here.
 * Each export independently supplies its model ID and evidence-backed verification state.
 */
object YoloRaw640Algorithms {
    fun decoder(): YoloOutputDecoder = Exp02OutputDecoder()
    fun postProcessor(): YoloPostProcessor = Exp02PostProcessor()
    fun <F> components(modelId: String, verification: ExportContractVerification, preprocessor: YoloPreprocessor<F>) =
        YoloContractComponents(verification, preprocessor, decoder(), postProcessor(),
            YoloStabilizationPolicy(minimumConfidence = Exp02DetectionPolicy.APP_ENGINEERING_INITIAL_STABLE_CONFIDENCE), modelId)
}
