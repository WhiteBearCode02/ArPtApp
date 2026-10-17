package com.example.arptapp.ui.dashboard

import org.junit.Assert.*
import org.junit.Test

class DebugLocalSessionUploadPolicyTest {
    @Test fun releaseAlwaysKeepsExistingBehavior() {
        assertTrue(DebugLocalSessionUploadPolicy.shouldUpload(false, false))
        assertTrue(DebugLocalSessionUploadPolicy.shouldUpload(false, true))
    }
    @Test fun debugSkipsOnlyExplicitLocalValidationSession() {
        assertTrue(DebugLocalSessionUploadPolicy.shouldUpload(true, false))
        assertFalse(DebugLocalSessionUploadPolicy.shouldUpload(true, true))
    }
}
