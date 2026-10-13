package com.example.arptapp.ui.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameSubmissionPolicyTest {
    @Test
    fun acceptsFirstFrameAndExactExistingInterval() {
        val policy = FrameSubmissionPolicy()
        assertTrue(policy.tryAccept(1_000L))
        assertFalse(policy.tryAccept(1_065L))
        assertTrue(policy.tryAccept(1_066L))
    }

    @Test
    fun skippedFramesDoNotExtendTheWaitingInterval() {
        val policy = FrameSubmissionPolicy()
        assertTrue(policy.tryAccept(1_000L))
        assertFalse(policy.tryAccept(1_030L))
        assertFalse(policy.tryAccept(1_060L))
        assertTrue(policy.tryAccept(1_066L))
    }

    @Test
    fun rejectsInvalidDuplicateAndBackwardsTimestampsWithoutChangingState() {
        val policy = FrameSubmissionPolicy()
        assertFalse(policy.tryAccept(-1L))
        assertTrue(policy.tryAccept(0L))
        assertFalse(policy.tryAccept(0L))
        assertTrue(policy.tryAccept(100L))
        assertFalse(policy.tryAccept(90L))
        assertTrue(policy.tryAccept(166L))
    }

    @Test
    fun supportsAConfigurableIntervalWithoutAndroidClock() {
        val policy = FrameSubmissionPolicy(minimumIntervalMs = 100L)
        assertTrue(policy.tryAccept(1_000L))
        assertFalse(policy.tryAccept(1_099L))
        assertTrue(policy.tryAccept(1_100L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroInterval() {
        FrameSubmissionPolicy(minimumIntervalMs = 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeInterval() {
        FrameSubmissionPolicy(minimumIntervalMs = -1L)
    }
}
