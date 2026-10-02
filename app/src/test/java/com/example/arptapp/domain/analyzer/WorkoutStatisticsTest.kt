package com.example.arptapp.domain.analyzer

import com.example.arptapp.data.ExerciseRecord
import com.example.arptapp.domain.statistics.WorkoutStatistics
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorkoutStatisticsTest {
    @Test fun windowsAreInclusiveAndIsolatedByUser() {
        val today = LocalDate.of(2026, 10, 2)
        fun record(user: String, date: String, reps: Int, score: Float = 80f) = ExerciseRecord(userId = user, date = date, totalCount = reps, duration = 60, burnedCalories = 0.0, averageScore = score)
        val records = listOf(record("a", "2026-10-02 10:00", 10, 100f), record("a", "2026-09-26 12:00", 30),
            record("a", "2026-09-25 12:00", 100), record("b", "2026-10-02 10:00", 100), record("a", "invalid", 100), record("a", "2026-10-03 10:00", 100))
        val result = WorkoutStatistics.summarize(records, "a", 7, today)
        assertEquals(2, result.sessions)
        assertEquals(40, result.reps)
        assertEquals(85.0, result.averageScore!!, 0.01)
        assertEquals(7, result.dailyReps.size)
        assertEquals(0, WorkoutStatistics.summarize(records, "", 7, today).sessions)
    }
}
