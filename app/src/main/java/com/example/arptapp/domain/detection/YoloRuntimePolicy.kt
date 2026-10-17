package com.example.arptapp.domain.detection

/**
 * Immutable process policy, separate from export-contract evidence and from the primary classifier.
 * No preference, Intent, remote configuration or model file can enable a release build.
 */
class YoloRuntimePolicy private constructor(val shadowRuntimeEnabled: Boolean) {
    fun predictionAllowed(contract: ExportContractVerification, modelStatus: DetectorStatus): Boolean =
        contract == ExportContractVerification.VERIFIED && shadowRuntimeEnabled && modelStatus == DetectorStatus.READY

    companion object {
        val DISABLED = YoloRuntimePolicy(false)
        fun forBuild(debug: Boolean, requested: Boolean): YoloRuntimePolicy = YoloRuntimePolicy(debug && requested)
    }
}
