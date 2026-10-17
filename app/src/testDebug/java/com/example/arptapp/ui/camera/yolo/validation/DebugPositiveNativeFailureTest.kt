package com.example.arptapp.ui.camera.yolo.validation

import com.example.arptapp.domain.detection.InvalidYoloOutput
import com.example.arptapp.domain.detection.MissingYoloModel
import com.example.arptapp.domain.detection.UnsupportedYoloRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

/** DEBUG-only pure classification checks; does not load a model, JNI, raster, or Android device. */
class DebugPositiveNativeFailureTest {
    @Test fun missingNativeLibraryIsUnavailableNotAnUncaughtWorkerFailure() {
        assertEquals("UNSUPPORTED_RUNTIME", DebugPositiveNativeInference.failureCode(UnsatisfiedLinkError("synthetic")))
    }

    @Test fun missingRuntimeClassIsUnavailable() {
        assertEquals("UNSUPPORTED_RUNTIME", DebugPositiveNativeInference.failureCode(NoClassDefFoundError("synthetic")))
    }

    @Test fun runtimeInitializationFailureIsUnavailable() {
        assertEquals("UNSUPPORTED_RUNTIME", DebugPositiveNativeInference.failureCode(ExceptionInInitializerError("synthetic")))
    }

    @Test fun ordinaryFailureClassificationIsPreserved() {
        assertEquals("MODEL_MISSING", DebugPositiveNativeInference.failureCode(MissingYoloModel()))
        assertEquals("UNSUPPORTED_RUNTIME", DebugPositiveNativeInference.failureCode(UnsupportedYoloRuntime()))
        assertEquals("INVALID_MODEL_OUTPUT", DebugPositiveNativeInference.failureCode(InvalidYoloOutput()))
        assertEquals("TENSOR_MISMATCH", DebugPositiveNativeInference.failureCode(IllegalArgumentException("TENSOR_MISMATCH")))
        assertEquals("PRIVATE_VALIDATION_FAILED", DebugPositiveNativeInference.failureCode(IllegalStateException("synthetic")))
    }

    @Test fun outOfMemoryErrorIsRethrownUnchanged() = assertFatalErrorEscapes(OutOfMemoryError("synthetic only; no allocation"))

    @Test fun threadDeathIsRethrownUnchanged() = assertFatalErrorEscapes(ThreadDeath())

    @Test fun assertionAndOtherErrorsAreNotDowngraded() = assertFatalErrorEscapes(AssertionError("synthetic"))

    private fun assertFatalErrorEscapes(error: Error) {
        try {
            DebugPositiveNativeInference.failureCode(error)
            fail("Fatal/unspecified Error must not become a benign validation state")
        } catch (actual: Error) {
            assertSame(error, actual)
        }
    }
}
