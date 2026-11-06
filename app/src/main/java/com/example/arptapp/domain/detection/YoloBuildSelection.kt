package com.example.arptapp.domain.detection

/** Pure compile-time configuration validator. Never reads an Intent, preference, server or model list. */
object YoloBuildSelection {
    fun identity(variant: String, debug: Boolean, comparison: Boolean, applicationId: String): YoloModelDescriptor {
        if (!comparison) {
            require(variant == "EXP03" && applicationId == "com.example.arptapp")
            return KnownYoloModels.EXP03
        }
        require(debug) { "A/B comparison is DEBUG-only" }
        return when (variant) {
            "EXP02" -> KnownYoloModels.EXP02.also { require(applicationId == "com.example.arptapp.exp02") }
            "EXP03" -> KnownYoloModels.EXP03.also { require(applicationId == "com.example.arptapp.exp03") }
            else -> error("Unknown compiled YOLO variant")
        }
    }
}
