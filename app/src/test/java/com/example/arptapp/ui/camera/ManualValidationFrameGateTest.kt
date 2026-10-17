package com.example.arptapp.ui.camera

import org.junit.Assert.*
import org.junit.Test

class ManualValidationFrameGateTest {
    @Test fun noRequestDoesNotConsumeOrRetainFrame() {
        val gate = ManualValidationFrameGate<String>()
        gate.offer("unused", 0, 0)
        var result: String? = null
        assertTrue(gate.request(0, 1) { result = it })
        gate.offer("explicit", 0, 2)
        gate.offer("later", 0, 3)
        assertEquals("explicit", result)
    }
    @Test fun duplicateRequestsAreRejected() {
        val gate = ManualValidationFrameGate<String>()
        assertTrue(gate.request(0, 0) {})
        assertFalse(gate.request(0, 0) {})
    }
    @Test fun expiredOrDifferentLensDoesNotSave() {
        val gate = ManualValidationFrameGate<String>(10)
        val results = mutableListOf<String?>()
        gate.request(0, 0) { results += it }
        gate.offer("expired", 0, 11)
        gate.request(0, 20) { results += it }
        gate.offer("new lens", 1, 21)
        assertEquals(listOf(null, null), results)
    }
    @Test fun cancelConsumesExactlyOnce() {
        val gate = ManualValidationFrameGate<String>()
        var calls = 0
        gate.request(0, 0) { assertNull(it); calls++ }
        gate.cancel(); gate.cancel(); gate.offer("unused", 0, 1)
        assertEquals(1, calls)
    }
    @Test fun debugExceptionDoesNotBreakCameraAndNewRequestStillWorks() {
        val gate = ManualValidationFrameGate<String>()
        gate.request(0, 0) { throw IllegalStateException("isolated") }
        gate.offer("frame", 0, 1)
        assertTrue(gate.request(0, 2) {})
    }
    @Test fun nativeLinkageFailureIsIsolatedButFatalVmFailureIsNot() {
        val gate = ManualValidationFrameGate<String>()
        gate.request(0, 0) { throw UnsatisfiedLinkError("synthetic") }
        gate.offer("frame", 0, 1)
        val fatal = OutOfMemoryError("synthetic only")
        gate.request(0, 2) { throw fatal }
        try { gate.offer("frame", 0, 3); fail("Fatal VM failure must escape") }
        catch (actual: OutOfMemoryError) { assertSame(fatal, actual) }
    }
}
