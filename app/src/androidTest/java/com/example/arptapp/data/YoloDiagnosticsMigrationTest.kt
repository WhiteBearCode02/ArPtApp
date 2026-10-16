package com.example.arptapp.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.domain.detection.YoloSessionDiagnostics
import com.example.arptapp.presentation.report.ReportNavigation
import com.example.arptapp.presentation.report.WorkoutReportData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real Android SQLite -> Room schema validator, not a mocked SQL/string assertion. */
@RunWith(AndroidJUnit4::class)
class YoloDiagnosticsMigrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtureNames = mutableListOf<String>()

    @After fun removeOnlyOwnFixtures() {
        fixtureNames.forEach { context.deleteDatabase(it) }
    }

    @Test fun version5PreservesExistingRowAndSupportsDiagnosticsRoundTrip() = verifyUpgrade(5)
    @Test fun version4RetainsItsExistingUpgradePath() = verifyUpgrade(4)
    @Test fun version3RetainsItsExistingUpgradePath() = verifyUpgrade(3)

    private fun verifyUpgrade(version: Int) = runBlocking {
        val name = createFixture(version)
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            .build()
        try {
                // Opening triggers the actual Room upgrade and complete generated TableInfo validation.
                database.openHelper.writableDatabase
                val owner = if (version >= 5) "migration-owner" else ""
                val before = database.exerciseDao().getAllRecords(owner).single()
                assertEquals(7, before.id)
                assertEquals("2026-10-15 20:00", before.date)
                assertEquals(4, before.totalCount)
                assertEquals(80L, before.duration)
                assertEquals(2.0, before.burnedCalories, 0.0)
                assertNull(before.yoloDiagnosticsJson)
                assertEquals(if (version >= 4) "[80,90]" else "[]", before.scoresJson)

                val diagnostics = YoloSessionDiagnosticsCodec.encode(YoloSessionDiagnostics(
                    modelStatus = "MODEL_MISSING", contractStatus = "NOT_VERIFIED", predictionEnabled = false,
                    startedAt = 1000, endedAt = 2000))!!
                val inserted = WorkoutReportData.create(totalCount = 2, analysesJson = "[88,90]",
                    yoloDiagnosticsJson = diagnostics).completedAt("2026-10-16 20:00").toRecord("new-owner")
                database.exerciseDao().insertRecord(inserted)
                val loaded = database.exerciseDao().getAllRecords("new-owner").single()
                assertEquals(diagnostics, loaded.yoloDiagnosticsJson)
                assertEquals(diagnostics, WorkoutReportData.fromRecord(loaded).yoloDiagnosticsJson)
                assertEquals("[88,90]", loaded.scoresJson)
                assertEquals(1, database.exerciseDao().getAllRecords(owner).size)
        } finally {
            database.close()
        }
    }

    @Test fun unsupportedVersionFailsWithoutDeletingTheWorkoutRow() {
        val name = createFixture(2)
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6).build()
        try {
            assertTrue(runCatching { database.openHelper.writableDatabase }.isFailure)
        } finally {
            database.close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT id, date, totalCount FROM exercise_records", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(7, cursor.getInt(0))
                assertEquals("2026-10-15 20:00", cursor.getString(1))
                assertEquals(4, cursor.getInt(2))
            }
        }
    }

    @Test fun separateNavigationExtraSurvivesResultToDetailedReport() {
        val raw = YoloSessionDiagnosticsCodec.encode(YoloSessionDiagnostics(
            modelStatus = "MODEL_MISSING", contractStatus = "NOT_VERIFIED", predictionEnabled = false,
            startedAt = 1000, endedAt = 2000))!!
        val source = WorkoutReportData.create(analysesJson = "[80]", yoloDiagnosticsJson = raw)
        val result = ReportNavigation.read(ReportNavigation.resultIntent(context, source))
        val details = ReportNavigation.read(ReportNavigation.intent(context, result))
        assertEquals(source, result)
        assertEquals(source, details)
        assertEquals("[80]", details.analysesJson)
    }

    private fun createFixture(version: Int): String {
        val name = "yolo-diagnostics-migration-$version-${System.nanoTime()}.db"
        fixtureNames.add(name)
        val columns = mutableListOf(
            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "date TEXT NOT NULL", "totalCount INTEGER NOT NULL",
            "duration INTEGER NOT NULL", "burnedCalories REAL NOT NULL")
        if (version >= 4) columns += listOf("exerciseType TEXT NOT NULL", "averageScore REAL NOT NULL",
            "scoresJson TEXT NOT NULL", "feedbackMessage TEXT NOT NULL")
        if (version >= 5) columns += "userId TEXT NOT NULL"
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("CREATE TABLE exercise_records (${columns.joinToString()})")
            val row = ContentValues().apply {
                put("id", 7); put("date", "2026-10-15 20:00"); put("totalCount", 4)
                put("duration", 80L); put("burnedCalories", 2.0)
                if (version >= 4) {
                    put("exerciseType", "스쿼트"); put("averageScore", 85f)
                    put("scoresJson", "[80,90]"); put("feedbackMessage", "기존 기록 보존")
                }
                if (version >= 5) put("userId", "migration-owner")
            }
            assertEquals(7L, db.insertOrThrow("exercise_records", null, row))
            db.version = version
        }
        return name
    }
}
