package com.example.arptapp.ui.dashboard

import android.view.View
import android.widget.Button
import com.example.arptapp.R
import com.example.arptapp.ui.camera.CameraZoomControl
import com.example.arptapp.ui.camera.CameraZoomGestureLayout
import com.example.arptapp.ui.camera.CameraZoomState
import java.util.Locale
import kotlin.math.round

/** Presentation only: no CameraX/pose/YOLO/session dependency. Text updates only when tenths change. */
class CameraZoomUi(
    private val gestures: CameraZoomGestureLayout,
    private val resetButton: Button,
    control: CameraZoomControl,
    protectedViews: List<View>
) : AutoCloseable {
    private var closed = false
    private var displayedRatio: Double? = null
    private var displayedAvailable: Boolean? = null
    private var displayedCanZoom: Boolean? = null

    init {
        gestures.protectedViews = protectedViews.toList()
        gestures.onScaleFactor = { control.scaleZoom(it) }
        resetButton.setOnClickListener { control.resetZoom() }
        render(control.zoomState)
    }

    fun render(state: CameraZoomState) {
        if (closed) return
        gestures.zoomAvailable = state.canZoom
        val rounded = if (state.available) round(state.currentZoomRatio.toDouble() * 10.0) / 10.0 else null
        if (rounded == displayedRatio && state.available == displayedAvailable && state.canZoom == displayedCanZoom) return
        displayedRatio = rounded; displayedAvailable = state.available; displayedCanZoom = state.canZoom
        val context = resetButton.context
        if (rounded == null) {
            resetButton.setText(R.string.camera_zoom_connecting)
            resetButton.contentDescription = context.getString(R.string.camera_zoom_connecting_description)
        } else {
            val ratio = String.format(Locale.ROOT, "%.1f", rounded)
            resetButton.text = context.getString(R.string.camera_zoom_ratio, ratio)
            resetButton.contentDescription = context.getString(if (state.canZoom)
                R.string.camera_zoom_reset_description else R.string.camera_zoom_fixed_description, ratio)
        }
        resetButton.isEnabled = state.canZoom
        resetButton.alpha = if (state.canZoom) 1f else .6f
    }

    fun cancelGesture() { gestures.cancelGesture() }

    override fun close() {
        if (closed) return
        closed = true
        gestures.cancelGesture()
        gestures.zoomAvailable = false
        gestures.onScaleFactor = null
        gestures.protectedViews = emptyList()
        resetButton.setOnClickListener(null)
    }
}
