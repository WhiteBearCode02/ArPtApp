package com.example.arptapp.domain.detection

/**
 * Independently reviewed EXP03 export, not inherited EXP02 verification.
 * 2026-11-05 Run 2: actual Python LiteRT4 / Android native8 invokes, four exercise/view inputs,
 * exact preprocessing tensor SHA4/4, class/count/order exact, confidence <= .001 / bbox <=2px,
 * frozen actual raw decoder/NMS4/4. Source: Ultralytics8.4.202 + OpenCV4.13.0 uint8 arithmetic.
 * Reference SHA: a9fc32327edfcfaab56b6c7eef27f2eb2c5dc68840f2c7954f4ce97997335e18.
 * Evidence: docs/ml/EXP03_ACTUAL_GOLDEN_VALIDATION.md (Run 1 FAIL retained; Run 2 PASS).
 * This verifies a bounded inference contract, not model accuracy/camera performance or Primary use.
 * Code-reviewed state only: no preference, fixture, model or remote flag can promote it.
 */
object Exp03YoloContract {
    val verification: ExportContractVerification get() = ExportContractVerification.VERIFIED
    fun <F> components(preprocessor: YoloPreprocessor<F>) = YoloRaw640Algorithms.components(
        KnownYoloModels.EXP03.modelId, verification, preprocessor)
}
