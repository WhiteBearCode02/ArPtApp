package com.example.arptapp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.data.history.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated in-memory DB only: never clear, rewrite or reassign the user's actual workout DB. */
@RunWith(AndroidJUnit4::class)
class WorkoutHistoryCacheDeviceTest {
    @Test fun uniqueSessionIsolationMergeAndLazyDetailPreserveLocalDiagnostics() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val local = RoomWorkoutDataSource(db.exerciseDao())
            val original = ExerciseRecord(userId = "a", date = "2026-11-01 20:00", totalCount = 10,
                duration = 60, burnedCalories = 5.0, sessionId = "session-a", syncState = "PENDING",
                cloudEligible = true, scoresJson = "[80,90]", yoloDiagnosticsJson = "device-only")
            local.save(original); local.save(original)
            assertEquals(1, local.records("a").size)
            assertTrue(local.records("b").isEmpty())
            assertTrue(runCatching { local.save(original.copy(userId = "b")) }.isFailure)
            local.state("a", "session-a", WorkoutSyncState.SYNCED)
            local.merge(original.copy(syncState = "SYNCED", scoresJson = "[]", detailsCached = false, yoloDiagnosticsJson = null))
            val existing = local.find("a", "session-a")!!
            assertTrue(existing.detailsCached); assertEquals("[80,90]", existing.scoresJson)
            assertEquals("device-only", existing.yoloDiagnosticsJson)
            local.merge(original.copy(sessionId = "remote-b", userId = "b", syncState = "SYNCED",
                detailsCached = false, yoloDiagnosticsJson = null))
            local.details("b", "remote-b", WorkoutDetails("[95]", "[]"))
            assertEquals("[95]", local.find("b", "remote-b")!!.scoresJson)
            assertEquals("[80,90]", local.find("a", "session-a")!!.scoresJson)
            assertEquals(1, local.records("a").size); assertEquals(1, local.records("b").size)
        } finally { db.close() }
    }
}
