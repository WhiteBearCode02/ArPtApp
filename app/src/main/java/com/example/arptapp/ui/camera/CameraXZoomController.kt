package com.example.arptapp.ui.camera

import androidx.camera.core.Camera
import androidx.camera.core.ZoomState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import java.util.concurrent.Executor

/**
 * Owns only the Camera returned by the current bind and its zoom observer. All calls, including
 * future completions, are on the main executor. Expected superseded/closed futures stay silent.
 * No model, image, analytics, persistence, network or BuildConfig dependency: works in release too.
 */
internal class CameraXZoomController(
    private val owner: LifecycleOwner,
    private val mainExecutor: Executor,
    onStateChanged: (CameraZoomState) -> Unit
) : CameraZoomControl, AutoCloseable {
    private var closed = false
    private var camera: Camera? = null
    private var zoomLiveData: LiveData<ZoomState>? = null
    private var zoomObserver: Observer<ZoomState>? = null
    private val session = CameraZoomSession(onStateChanged) {
        !closed && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
    override val zoomState: CameraZoomState get() = session.zoomState

    fun bind(boundCamera: Camera) {
        unbind()
        if (closed) return
        camera = boundCamera
        val live = boundCamera.cameraInfo.zoomState
        zoomLiveData = live
        var binding = -1L
        binding = session.bind(CameraZoomCommand { ratio, completed ->
            val future = boundCamera.cameraControl.setZoomRatio(ratio)
            future.addListener({
                val success = try { future.get(); true } catch (_: Exception) { false }
                completed(success)
                if (!closed && camera === boundCamera) publish(binding, live.value)
            }, mainExecutor)
        })
        val observer = Observer<ZoomState> { value ->
            if (!closed && camera === boundCamera) publish(binding, value)
        }
        zoomObserver = observer
        publish(binding, live.value)
        live.observe(owner, observer)
    }

    private fun publish(binding: Long, value: ZoomState?) {
        session.update(binding, value?.let {
            CameraZoomPolicy.fromCamera(it.zoomRatio, it.minZoomRatio, it.maxZoomRatio)
        } ?: CameraZoomState())
    }

    fun unbind() {
        // Invalidate before removing the observer/unbinding use cases, including queued futures.
        camera = null
        session.unbind()
        zoomObserver?.let { zoomLiveData?.removeObserver(it) }
        zoomObserver = null; zoomLiveData = null
    }

    override fun setZoomRatio(ratio: Float): Boolean = session.setZoomRatio(ratio)
    override fun scaleZoom(factor: Float): Boolean = session.scaleZoom(factor)
    override fun resetZoom(): Boolean = session.resetZoom()
    override fun close() {
        if (closed) return
        closed = true
        unbind()
        session.close()
    }
}
