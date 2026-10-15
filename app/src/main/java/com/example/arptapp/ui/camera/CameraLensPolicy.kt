package com.example.arptapp.ui.camera

/** Physical lens only. Never use this enum as the person's FRONT/SIDE pose view. */
enum class CameraLens { BACK, FRONT }

object CameraLensPolicy {
    val defaultLens = CameraLens.BACK
    fun select(requested: CameraLens = defaultLens, hasBack: Boolean, hasFront: Boolean): CameraLens? = when {
        requested == CameraLens.BACK && hasBack -> CameraLens.BACK
        requested == CameraLens.FRONT && hasFront -> CameraLens.FRONT
        hasBack -> CameraLens.BACK
        hasFront -> CameraLens.FRONT
        else -> null
    }
    fun switch(current: CameraLens, hasBack: Boolean, hasFront: Boolean): CameraLens? =
        select(if (current == CameraLens.BACK) CameraLens.FRONT else CameraLens.BACK, hasBack, hasFront)
}
