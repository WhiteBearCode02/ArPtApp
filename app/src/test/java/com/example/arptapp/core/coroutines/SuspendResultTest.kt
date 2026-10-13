package com.example.arptapp.core.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class SuspendResultTest {
    @Test fun returnsValue() = runBlocking {
        assertEquals(42, suspendResult { 42 }.getOrThrow())
    }

    @Test fun returnsRecoverableFailure() = runBlocking {
        val failure = IllegalStateException("test failure")
        assertSame(failure, suspendResult<Unit> { throw failure }.exceptionOrNull())
    }

    @Test fun propagatesCancellationInsteadOfResult() = runBlocking {
        val cancellation = CancellationException("test cancellation")
        try {
            suspendResult<Unit> { throw cancellation }
            fail("Cancellation must escape")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test fun cancelledJobDoesNotExecuteBlock() = runBlocking {
        var executed = false
        val job = Job().apply { cancel() }
        try {
            withContext(job) { suspendResult { executed = true } }
            fail("Cancelled job must escape")
        } catch (_: CancellationException) {
            assertFalse(executed)
        }
    }

    @Test fun cancellationAfterIoFailureTakesPrecedence() = runBlocking {
        val job = Job()
        try {
            withContext(job) {
                suspendResult<Unit> {
                    // withContext가 만든 자식뿐 아니라 검증하는 동일한 부모 Job을 취소한다.
                    job.cancel()
                    throw IllegalStateException("simulated IO failure")
                }
            }
            fail("Cancellation must not become a recoverable failure")
        } catch (_: CancellationException) {
            assertTrue(job.isCancelled)
        }
    }

    @Test fun fatalErrorsAreNotSilenced() = runBlocking {
        val failure = AssertionError("test fatal error")
        try {
            suspendResult<Unit> { throw failure }
            fail("Fatal errors must escape")
        } catch (actual: AssertionError) {
            assertSame(failure, actual)
        }
    }
}
