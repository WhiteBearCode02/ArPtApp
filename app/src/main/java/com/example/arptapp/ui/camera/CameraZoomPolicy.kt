package com.example.arptapp.ui.camera

/** Camera-owned ratios only; never a Preview/bitmap transform or an exercise-analysis setting. */
data class CameraZoomState(
    val currentZoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val available: Boolean = false
) {
    val canZoom: Boolean get() = available && maxZoomRatio > minZoomRatio
}

object CameraZoomPolicy {
    fun fromCamera(current: Float, min: Float, max: Float): CameraZoomState {
        if (!min.isFinite() || !max.isFinite() || min <= 0f || max < min || !current.isFinite() || current <= 0f)
            return CameraZoomState()
        return CameraZoomState(current.coerceIn(min, max), min, max, available = true)
    }

    /** CameraX defaults to 1x, including cameras supporting ultra-wide <1x. */
    fun defaultRatio(state: CameraZoomState): Float? = if (!state.available) null
        else if (1f in state.minZoomRatio..state.maxZoomRatio) 1f else state.minZoomRatio

    fun targetRatio(state: CameraZoomState, requested: Float): Float? =
        if (!state.available || !requested.isFinite() || requested <= 0f) null
        else requested.coerceIn(state.minZoomRatio, state.maxZoomRatio)

    fun scaledRatio(state: CameraZoomState, factor: Float): Float? {
        if (!state.available || !factor.isFinite() || factor <= 0f) return null
        // Double multiplication saturates to the device limit instead of overflowing Float first.
        return (state.currentZoomRatio.toDouble() * factor.toDouble())
            .coerceIn(state.minZoomRatio.toDouble(), state.maxZoomRatio.toDouble()).toFloat()
    }
}

/** Small UI-facing API; callers cannot access CameraX, the image pipeline or workout state. */
interface CameraZoomControl {
    val zoomState: CameraZoomState
    fun setZoomRatio(ratio: Float): Boolean
    fun scaleZoom(factor: Float): Boolean
    fun resetZoom(): Boolean
}

fun interface CameraZoomCommand {
    fun submit(ratio: Float, completed: (success: Boolean) -> Unit)
}

/**
 * Main-thread session with a generation per physical binding. A late observer/completion from an
 * old lens cannot change the new lens. Rapid pinch accumulates its pending target, while UI state
 * comes ONLY from the camera observer (never an optimistic label). Pure Kotlin for lifecycle tests.
 */
class CameraZoomSession(
    private val onStateChanged: (CameraZoomState) -> Unit,
    private val canSubmit: () -> Boolean = { true }
) : CameraZoomControl, AutoCloseable {
    override var zoomState = CameraZoomState()
        private set
    private var generation = 0L
    private var requestId = 0L
    private var command: CameraZoomCommand? = null
    private var desiredRatio: Float? = null
    private var needsDefault = false
    private var closed = false

    fun bind(command: CameraZoomCommand): Long {
        unbind()
        if (!closed) { this.command = command; needsDefault = true }
        return generation
    }

    fun update(binding: Long, state: CameraZoomState) {
        if (closed || binding != generation || command == null) return
        val safe = if (state.available) CameraZoomPolicy.fromCamera(state.currentZoomRatio, state.minZoomRatio, state.maxZoomRatio)
            else CameraZoomState()
        if (desiredRatio == safe.currentZoomRatio) desiredRatio = null
        if (zoomState != safe) { zoomState = safe; onStateChanged(safe) }
        if (needsDefault && safe.available && canSubmit()) {
            needsDefault = false
            resetZoom()
        }
    }

    override fun setZoomRatio(ratio: Float): Boolean {
        if (closed || !canSubmit()) return false
        val target = CameraZoomPolicy.targetRatio(zoomState, ratio) ?: return false
        val port = command ?: return false
        if (target == (desiredRatio ?: zoomState.currentZoomRatio)) return false
        val binding = generation
        val request = ++requestId
        desiredRatio = target
        return try {
            port.submit(target) { success ->
                if (!closed && binding == generation && request == requestId)
                    desiredRatio = if (success && zoomState.currentZoomRatio != target) target else null
            }
            true
        } catch (_: Exception) {
            if (binding == generation && request == requestId) desiredRatio = null
            false // Camera closing/rebinding must never become an Activity crash.
        }
    }

    override fun scaleZoom(factor: Float): Boolean {
        val base = desiredRatio?.let { zoomState.copy(currentZoomRatio = it) } ?: zoomState
        return CameraZoomPolicy.scaledRatio(base, factor)?.let(::setZoomRatio) ?: false
    }

    override fun resetZoom(): Boolean = CameraZoomPolicy.defaultRatio(zoomState)?.let(::setZoomRatio) ?: false

    fun unbind() {
        generation++; requestId++; command = null; desiredRatio = null; needsDefault = false
        if (zoomState.available) { zoomState = CameraZoomState(); onStateChanged(zoomState) }
    }

    override fun close() { if (!closed) { unbind(); closed = true } }
}
