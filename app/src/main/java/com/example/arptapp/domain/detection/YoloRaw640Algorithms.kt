package com.example.arptapp.domain.detection

/**
 * Shared candidate algorithms, NOT transferable verification/provenance. Historical implementation
 * names remain to preserve EXP02 tests and references; no threshold or arithmetic changes here.
 * Each export independently supplies its model ID and evidence-backed verification state.
 */
object YoloRaw640Algorithms {
    fun decoder(profile: YoloModelProfile = YoloModelProfiles.EXP02): YoloOutputDecoder = YoloRawOutputDecoder(profile)
    fun postProcessor(profile: YoloModelProfile = YoloModelProfiles.EXP02): YoloPostProcessor = Exp02PostProcessor(profile = profile)
    fun <F> components(modelId: String, verification: ExportContractVerification, preprocessor: YoloPreprocessor<F>) =
        YoloContractComponents(verification, preprocessor, decoder(YoloModelProfiles.requireModel(modelId)), postProcessor(YoloModelProfiles.requireModel(modelId)),
            YoloStabilizationPolicy(minimumConfidence = Exp02DetectionPolicy.APP_ENGINEERING_INITIAL_STABLE_CONFIDENCE), modelId)
}
