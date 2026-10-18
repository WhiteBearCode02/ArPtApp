package com.example.arptapp.ui.camera

import org.junit.Assert.*
import org.junit.Test

class CameraZoomPolicyTest {
    private fun state(current: Float = 1f, min: Float = .5f, max: Float = 8f) = CameraZoomPolicy.fromCamera(current, min, max)

    @Test fun pinchIncreasesAndDecreasesActualRatio() {
        assertEquals(1.5f, CameraZoomPolicy.scaledRatio(state(), 1.5f)!!, 0f)
        assertEquals(1f, CameraZoomPolicy.scaledRatio(state(2f), .5f)!!, 0f)
    }
    @Test fun limitsComeFromTheDeviceRatherThanFixedMaximum() {
        assertEquals(.7f, CameraZoomPolicy.scaledRatio(state(min = .7f, max = 1.8f), .01f)!!, 0f)
        assertEquals(1.8f, CameraZoomPolicy.scaledRatio(state(min = .7f, max = 1.8f), 100f)!!, 0f)
        assertEquals(37f, CameraZoomPolicy.targetRatio(state(max = 37f), 100f)!!, 0f)
    }
    @Test fun defaultIsOneWhenValidOtherwiseTheDeviceMinimum() {
        assertEquals(1f, CameraZoomPolicy.defaultRatio(state())!!, 0f)
        assertEquals(2f, CameraZoomPolicy.defaultRatio(state(2f, 2f, 5f))!!, 0f)
        assertEquals(.5f, CameraZoomPolicy.defaultRatio(state(.5f, .5f, .8f))!!, 0f)
    }
    @Test fun invalidOrMissingNativeStateIsUnavailable() {
        for (values in listOf(listOf(1f, 0f, 4f), listOf(1f, 2f, 1f), listOf(Float.NaN, 1f, 4f),
            listOf(1f, Float.NaN, 4f), listOf(1f, 1f, Float.POSITIVE_INFINITY), listOf(-1f, 1f, 4f))) {
            val state = state(values[0], values[1], values[2])
            assertFalse(state.available); assertFalse(state.canZoom)
            assertNull(CameraZoomPolicy.scaledRatio(state, 2f)); assertNull(CameraZoomPolicy.defaultRatio(state))
        }
    }
    @Test fun invalidGestureOrRequestedValueIsIgnored() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -1f)) {
            assertNull(CameraZoomPolicy.scaledRatio(state(), value))
            assertNull(CameraZoomPolicy.targetRatio(state(), value))
        }
    }
    @Test fun floatMultiplicationOverflowSaturatesWithoutRejectingValidGesture() {
        assertEquals(8f, CameraZoomPolicy.scaledRatio(state(8f), Float.MAX_VALUE)!!, 0f)
        assertEquals(.5f, CameraZoomPolicy.scaledRatio(state(), Float.MIN_VALUE)!!, 0f)
    }
    @Test fun fixedZoomCameraIsAvailableButNotZoomable() {
        val state = state(1f, 1f, 1f)
        assertTrue(state.available); assertFalse(state.canZoom)
        assertEquals(1f, CameraZoomPolicy.scaledRatio(state, 20f)!!, 0f)
    }
    @Test fun nativeCurrentValueIsClampedToReportedRange() {
        assertEquals(4f, state(8f, 1f, 4f).currentZoomRatio, 0f)
        assertEquals(1f, state(.5f, 1f, 4f).currentZoomRatio, 0f)
    }
}
