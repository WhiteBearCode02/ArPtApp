package com.example.arptapp.ui.dashboard

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity

/** Release does not contain candidate storage, capture UI, or native validation implementation. */
object DebugPositiveValidationControls {
    fun attach(activity: AppCompatActivity, panel: YoloDiagnosticsPanel,
        requestFrame: ((Bitmap?) -> Unit) -> Boolean, cancelFrame: () -> Unit): DebugValidationControls? = null
}
