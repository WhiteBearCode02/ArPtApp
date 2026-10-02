package com.example.arptapp.domain.analyzer

import org.junit.Assert.*
import org.junit.Test

class PersonalMotionReferenceTest {
    @Test fun referenceRequiresRealMotionAndKnownView() {
        val frames = (0..29).map { listOf(80f + it * 3, 80f + it * 3, 100f, 100f) }
        val reference = PersonalMotionReference("SQUAT", CameraView.SIDE, frames)
        assertTrue(reference.isValid())
        assertFalse(reference.copy(cameraView = CameraView.UNKNOWN).isValid())
        assertFalse(reference.copy(frames = frames.take(5)).isValid())
        assertFalse(reference.copy(frames = List(30) { frames[0] }).isValid())
        assertFalse(reference.copy(exerciseType = "SHOULDER_PRESS").isValid())
    }
    @Test fun latencyAndSampleMemoryStayBounded() {
        val monitor = FramePerformanceMonitor()
        var result = monitor.record(0, 10)
        repeat(100) { result = monitor.record(it * 100L, it * 100L + 10) }
        assertEquals(60, result.samples)
        assertEquals(10.0, result.processedFps, 0.001)
        assertEquals(10.0, result.averageLatencyMs, 0.001)
    }
}
