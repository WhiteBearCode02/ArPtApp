package com.example.arptapp.ui.camera.yolo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.domain.detection.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Synthetic native smoke, NOT an actual-image Golden, camera benchmark or semantic test. */
@RunWith(AndroidJUnit4::class)
class Exp04NativeContractDeviceTest {
    @Test fun privateIdentityMetadataAndBoundedSyntheticInvoke() {
        Exp04GoldenFixtures.requireSandbox()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val profile = YoloModelProfiles.EXP04
        val model = DebugYoloModelSource.open(context, profile.descriptor)
        YoloModelIdentity.validate(model, profile.descriptor)
        assertThrows(YoloModelIdentityMismatch::class.java) { YoloModelIdentity.validate(model, KnownYoloModels.EXP03) }
        TfliteYoloRuntime(model, profile.descriptor).use { runtime ->
            assertTrue(runtime.metadata().matchesExpected(profile))
            assertFalse(runtime.metadata().matchesExpected(YoloModelProfiles.EXP03))
            val input = ByteBuffer.allocateDirect(profile.inputBytes).order(ByteOrder.nativeOrder())
            while (input.hasRemaining()) input.putFloat(0f)
            input.rewind()
            val output = ByteBuffer.allocateDirect(profile.outputBytes).order(ByteOrder.nativeOrder())
            runtime.run(input, output); output.rewind()
            while (output.hasRemaining()) assertTrue(output.float.isFinite())
        }
        assertEquals(ExportContractVerification.NOT_VERIFIED, Exp04YoloContract.verification)
    }

    @Test fun separatelyProvisionedExp03CannotPassExp04Identity() {
        Exp04GoldenFixtures.requireSandbox()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Optional negative fixture: never copy/overwrite EXP03 or call a missing fixture a PASS.
        assumeTrue("NOT_RUN: separate EXP03 negative fixture is absent", File(context.noBackupFilesDir, "yolo/${KnownYoloModels.EXP03.fileName}").isFile)
        val old = DebugYoloModelSource.open(context, KnownYoloModels.EXP03)
        YoloModelIdentity.validate(old, KnownYoloModels.EXP03)
        assertThrows(YoloModelIdentityMismatch::class.java) { YoloModelIdentity.validate(old, KnownYoloModels.EXP04) }
    }
}
