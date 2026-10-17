package com.example.arptapp.ui.dashboard

/** Optional debug presentation port; session consent is frozen at Start, not read at Finish. */
interface DebugValidationControls : AutoCloseable {
    fun freezeLocalOnlyForSession(): Boolean
    fun setSessionActive(active: Boolean)
    fun onHostPaused()
}

object DebugLocalSessionUploadPolicy {
    fun shouldUpload(debug: Boolean, explicitlyLocal: Boolean): Boolean = !debug || !explicitlyLocal
}
