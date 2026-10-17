package com.example.arptapp.ui.camera.yolo.validation

import android.graphics.Bitmap
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.BuildConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Synthetic privacy/storage regression only. Does not execute or verify EXP02 model predictions. */
@RunWith(AndroidJUnit4::class)
class DebugPositiveCandidateStoreTest {
    private fun withStore(block: (File, DebugPositiveCandidateStore) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parent = File(context.noBackupFilesDir, "exp02-store-test-${UUID.randomUUID()}")
        check(parent.mkdir())
        val store = DebugPositiveCandidateStore(parent)
        try { block(parent, store) } finally {
            // Test-owned exact directory only; no recursive deletion of app data.
            runCatching { store.deleteOwned() }
            File(parent, DebugPositiveCandidateStore.DIRECTORY_NAME).delete()
            parent.delete()
        }
    }

    @Test fun explicitSaveUsesBoundedNoBackupPngAndPreservesRgb() = withStore { parent, store ->
        assertTrue(BuildConfig.DEBUG)
        assertTrue(store.inventory().isEmpty()) // no creation/automatic frame dump
        val source = Bitmap.createBitmap(intArrayOf(0xff123456.toInt(), 0xffaabbcc.toInt()), 2, 1, Bitmap.Config.ARGB_8888)
        try {
            val candidate = store.save(source)
            assertEquals(File(parent, "exp02_positive").canonicalFile, File(candidate.privatePath).canonicalFile.parentFile)
            assertEquals(2, candidate.width)
            assertEquals(1, candidate.height)
            assertTrue(candidate.bytes in 1..DebugPositiveCandidateStore.MAX_PNG_BYTES)
            assertTrue(candidate.sha256.matches(Regex("[a-f0-9]{64}")))
            val loaded = store.decode(candidate)
            try { assertEquals(DebugPositiveNativeInference.logicalRgbSha256(source), DebugPositiveNativeInference.logicalRgbSha256(loaded)) }
            finally { loaded.recycle() }
            assertEquals(listOf(candidate), store.inventory())
        } finally { source.recycle() }
    }

    @Test fun deleteOnlyKnownCandidatesAndAssociatedResults() = withStore { parent, store ->
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            val candidate = store.save(source)
            store.writeResult(candidate, "{\"status\":\"SYNTHETIC_STORAGE_TEST_ONLY\"}")
            val unknown = File(parent, "exp02_positive/unrelated.txt").apply { writeText("keep") }
            try {
                assertEquals(2, store.deleteOwned())
                assertTrue(unknown.isFile)
                assertTrue(store.inventory().isEmpty())
            } finally { unknown.delete() }
        } finally { source.recycle() }
    }

    @Test fun ninthManualCandidateIsRejectedWithoutEviction() = withStore { _, store ->
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            repeat(8) { store.save(source) }
            var rejected = false
            try { store.save(source) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(8, store.inventory().size)
        } finally { source.recycle() }
    }

    @Test fun symlinkDirectoryIsRejectedAndExternalTargetPreserved() = withStore { parent, store ->
        val outside = File(parent, "owned-target").apply { mkdir() }
        val sentinel = File(outside, "sentinel.txt").apply { writeText("keep") }
        val link = File(parent, "exp02_positive")
        Os.symlink(outside.absolutePath, link.absolutePath)
        try {
            var rejected = false
            try { store.inventory() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertTrue(sentinel.isFile)
        } finally {
            link.delete()
            sentinel.delete()
            outside.delete()
        }
    }

    @Test fun symlinkCandidateFailsPreflightWithoutDeletingOtherFiles() = withStore { parent, store ->
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val outside = File(parent, "sentinel.txt").apply { writeText("keep") }
        var link: File? = null
        try {
            val saved = store.save(source)
            link = File(parent, "exp02_positive/positive_candidate_20000101_000000_000_00000000.png")
            Os.symlink(outside.absolutePath, link.absolutePath)
            var rejected = false
            try { store.deleteOwned() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertTrue(File(saved.privatePath).isFile)
            assertTrue(outside.isFile)
        } finally { link?.delete(); outside.delete(); source.recycle() }
    }

    @Test fun dimensionsAreBoundedBeforeAllocatingOrEncoding() {
        listOf(0 to 1, 1 to 0, 4097 to 1, 4096 to 4096).forEach { (width, height) ->
            var rejected = false
            try { DebugPositiveCandidateStore.requireDimensions(width, height) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
    }
}
