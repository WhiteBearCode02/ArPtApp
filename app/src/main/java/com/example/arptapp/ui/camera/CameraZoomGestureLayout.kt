package com.example.arptapp.ui.camera

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout

/**
 * Routes only camera-origin two-finger streams, including the empty space inside the existing
 * NestedScrollView. A PreviewView listener alone cannot see those streams under dashboard chrome.
 * One-finger scrolling/taps and ALL streams starting on protected controls keep normal dispatch.
 * Once claimed, send child CANCEL once and consume through UP, never turn a leftover finger into a tap.
 */
class CameraZoomGestureLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    var zoomAvailable = false
    var onScaleFactor: ((Float) -> Unit)? = null
    var protectedViews: List<View> = emptyList()
    private val visibleBounds = Rect()
    private val rootLocation = IntArray(2)
    private var candidate = false
    private var claimed = false
    private var pointerCount = 0
    private val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = claimed && zoomAvailable && pointerCount == 2
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (claimed && zoomAvailable && pointerCount == 2) onScaleFactor?.invoke(detector.scaleFactor)
            return true
        }
    }).apply { isQuickScaleEnabled = false; isStylusScaleEnabled = false }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        pointerCount = event.pointerCount
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            claimed = false
            candidate = zoomAvailable && allPointersOnCamera(event)
        }
        if (claimed) {
            detector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) finishStream()
            return true
        }
        if (candidate && event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            if (zoomAvailable && pointerCount == 2 && allPointersOnCamera(event)) {
                claimed = true
                // Cancel an ongoing ScrollView/Preview stream once, not once per scale frame.
                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                try { super.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
                parent?.requestDisallowInterceptTouchEvent(true)
                detector.onTouchEvent(event)
                return true
            }
            cancelDetector()
            candidate = false
        }
        if (candidate) detector.onTouchEvent(event)
        val handled = super.dispatchTouchEvent(event)
        val keepCameraStream = candidate // Keep DOWN in empty preview space eligible for a second finger.
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) finishStream()
        return handled || keepCameraStream
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!candidate && !claimed) return super.onTouchEvent(event)
        if (!claimed && event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun allPointersOnCamera(event: MotionEvent): Boolean {
        getLocationOnScreen(rootLocation)
        for (index in 0 until event.pointerCount) {
            val x = event.getX(index); val y = event.getY(index)
            if (x < 0 || y < 0 || x >= width || y >= height) return false
            for (view in protectedViews) {
                if (view.isShown && view.getGlobalVisibleRect(visibleBounds) &&
                    visibleBounds.contains((x + rootLocation[0]).toInt(), (y + rootLocation[1]).toInt())) return false
            }
        }
        return true
    }

    private fun finishStream() {
        candidate = false; claimed = false; pointerCount = 0
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun cancelDetector() {
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        try { detector.onTouchEvent(cancel) } finally { cancel.recycle() }
    }

    fun cancelGesture() { cancelDetector(); finishStream() }

    override fun onDetachedFromWindow() {
        cancelGesture()
        super.onDetachedFromWindow()
    }
}
