package com.example.arptapp.ui.camera

import org.junit.Assert.*
import org.junit.Test

class CameraLensPolicyTest {
    @Test fun backIsDefaultWhenBothAvailable() {
        assertEquals(CameraLens.BACK, CameraLensPolicy.defaultLens)
        assertEquals(CameraLens.BACK, CameraLensPolicy.select(hasBack = true, hasFront = true))
    }
    @Test fun frontOnlyDeviceFallsBack() {
        assertEquals(CameraLens.FRONT, CameraLensPolicy.select(hasBack = false, hasFront = true))
    }
    @Test fun missingFrontFallsBackToBack() {
        assertEquals(CameraLens.BACK, CameraLensPolicy.select(CameraLens.FRONT, true, false))
    }
    @Test fun missingBothReturnsNull() {
        assertNull(CameraLensPolicy.select(hasBack = false, hasFront = false))
    }
    @Test fun switchingBothWaysUsesAvailableLens() {
        assertEquals(CameraLens.FRONT, CameraLensPolicy.switch(CameraLens.BACK, true, true))
        assertEquals(CameraLens.BACK, CameraLensPolicy.switch(CameraLens.FRONT, true, true))
    }
    @Test fun switchingSingleLensDoesNotInventCamera() {
        assertEquals(CameraLens.BACK, CameraLensPolicy.switch(CameraLens.BACK, true, false))
        assertEquals(CameraLens.FRONT, CameraLensPolicy.switch(CameraLens.FRONT, false, true))
    }
}
