package com.example.arptapp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.report.StructuredWorkoutAnalysis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic records in an isolated IN-MEMORY DB only: never opens or alters the user's DB. */
@RunWith(AndroidJUnit4::class)
class SevenExerciseHistoryDeviceTest {
    @Test fun sevenNamesAndV2DetailsSurviveOwnerScopedRoomReads() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.exerciseDao()
            for (type in ExerciseType.supported) {
                val report = StructuredWorkoutAnalysis(schemaVersion = 2, exerciseType = type.name, totalReps = 0,
                    repAnalyses = emptyList())
                val json = StructuredReportCodec.encode(emptyList(), report)
                dao.saveSession(ExerciseRecord(userId = "synthetic-owner", sessionId = "synthetic-${type.name}",
                    date = "2026-11-12 12:00", totalCount = 0, duration = 0, burnedCalories = 0.0,
                    exerciseType = type.displayName, scoresJson = json))
                val read = requireNotNull(dao.findSession("synthetic-owner", "synthetic-${type.name}"))
                assertEquals(type.displayName, read.exerciseType)
                assertEquals(report, StructuredReportCodec.decode(read.scoresJson))
                assertNull(dao.findSession("different-owner", "synthetic-${type.name}"))
            }
            assertEquals(7, dao.getAllRecords("synthetic-owner").size)
            assertTrue(dao.getAllRecords("different-owner").isEmpty())
        } finally { database.close() }
    }
}
