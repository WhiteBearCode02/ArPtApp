package com.example.arptapp.ui.camera.yolo

import android.graphics.Bitmap
import com.example.arptapp.BuildConfig
import com.example.arptapp.domain.detection.*

/** Single composition root for native identity, contract, preprocessor, live label and session collector.
 * Final app uses EXP02's reviewed arithmetic. EXP03 stays isolated in its explicit comparison build.
 */
internal object YoloBuildProfile {
    val identity: YoloModelDescriptor = YoloBuildSelection.identity(BuildConfig.YOLO_MODEL_VARIANT,
        BuildConfig.DEBUG, BuildConfig.YOLO_AB_COMPARISON, BuildConfig.APPLICATION_ID)

    fun components(): YoloContractComponents<Bitmap> = when (identity) {
        KnownYoloModels.EXP02 -> Exp02YoloContract.components(Exp02BitmapPreprocessor())
        KnownYoloModels.EXP03 -> Exp03YoloContract.components(YoloBitmapPreprocessor())
        // Candidate OpenCV/RGB ports only: NOT_VERIFIED prevents camera frame admission.
        KnownYoloModels.EXP04 -> Exp04YoloContract.components(YoloBitmapPreprocessor())
        else -> error("No reviewed preprocessing contract")
    }
}
