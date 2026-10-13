package com.example.arptapp.ui.camera

import org.junit.Assert.*
import org.junit.Test

class CameraFrameTrackerTest {
    @Test fun preservesSubmittedLensAndConsumesOnce() {
        val tracker = CameraFrameTracker()
        assertTrue(tracker.register(10, 0, true))
        assertEquals(CameraFrameToken(0, true), tracker.consume(10))
        assertNull(tracker.consume(10))
    }
    @Test fun switchingCameraDiscardsPendingAndRejectsOldAnalyzer() {
        val tracker = CameraFrameTracker()
        tracker.register(10, 0, true)
        tracker.advance()
        assertNull(tracker.consume(10))
        assertFalse(tracker.register(11, 0, true))
        assertTrue(tracker.register(12, 1, false))
        assertEquals(CameraFrameToken(1, false), tracker.consume(12))
    }
    @Test fun switchingAfterCallbackRejectsQueuedUiResult() {
        val tracker = CameraFrameTracker()
        tracker.register(10, 0, true)
        val token = tracker.consume(10)!!
        tracker.advance()
        assertFalse(tracker.isCurrent(token.generation))
    }
    @Test fun droppedFramesCannotGrowMetadataIndefinitely() {
        val tracker = CameraFrameTracker(2)
        repeat(3) { tracker.register(it.toLong(), 0, true) }
        assertNull(tracker.consume(0))
        assertNotNull(tracker.consume(1))
        assertNotNull(tracker.consume(2))
    }
}
