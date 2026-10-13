package com.example.arptapp.presentation.report

import org.junit.Assert.*
import org.junit.Test

class WorkoutResultSaveGateTest {
    @Test fun `reentry during a pending save cannot start another insert`() {
        val gate = WorkoutResultSaveGate()
        assertTrue(gate.begin())
        assertFalse(gate.begin())
        assertEquals(WorkoutResultSaveGate.State.SAVING, gate.state)
    }

    @Test fun `completed save stays completed during cleanup and reentry`() {
        val gate = WorkoutResultSaveGate()
        gate.begin()
        gate.complete()
        gate.failed()
        assertEquals(WorkoutResultSaveGate.State.COMPLETED, gate.state)
        assertFalse(gate.begin())
    }

    @Test fun `restored completed flag prevents another insert`() {
        val restored = WorkoutResultSaveGate(completed = true)
        assertFalse(restored.begin())
        assertEquals(WorkoutResultSaveGate.State.COMPLETED, restored.state)
    }

    @Test fun `failed save allows explicit retry`() {
        val gate = WorkoutResultSaveGate()
        gate.begin()
        gate.failed()
        assertEquals(WorkoutResultSaveGate.State.READY, gate.state)
        assertTrue(gate.begin())
    }

    @Test(expected = IllegalStateException::class)
    fun `completion is valid only after save begins`() {
        WorkoutResultSaveGate().complete()
    }
}
