package com.example.arptapp.domain.statistics

import com.example.arptapp.data.ExerciseRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class WorkoutSummary(val sessions: Int, val reps: Int, val durationSeconds: Long,
    val activeDays: Int, val averageScore: Double?, val dailyReps: List<Pair<LocalDate, Int>>)

/** Calendar-day windows include today; invalid/future dates and other users are excluded. */
object WorkoutStatistics {
    fun summarize(records: List<ExerciseRecord>, userId: String, days: Int, today: LocalDate = LocalDate.now()): WorkoutSummary {
        require(days in 1..366)
        val start = today.minusDays(days - 1L)
        val dated = if (userId.isBlank()) emptyList() else records.filter { it.userId == userId }.mapNotNull { record ->
            val date = runCatching { LocalDateTime.parse(record.date, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).toLocalDate() }.getOrNull()
            date?.takeIf { !it.isBefore(start) && !it.isAfter(today) }?.let { it to record }
        }
        val validScores = dated.map { it.second }.filter { it.totalCount > 0 && it.averageScore.isFinite() && it.averageScore in 0f..100f }
        val repWeight = validScores.sumOf { it.totalCount.toLong() }
        return WorkoutSummary(dated.size, dated.sumOf { it.second.totalCount.coerceAtLeast(0) },
            dated.sumOf { it.second.duration.coerceAtLeast(0) }, dated.map { it.first }.distinct().size,
            if (repWeight == 0L) null else validScores.sumOf { it.averageScore.toDouble() * it.totalCount } / repWeight,
            (0 until days).map { offset ->
                val date = start.plusDays(offset.toLong())
                date to dated.filter { it.first == date }.sumOf { it.second.totalCount.coerceAtLeast(0) }
            })
    }
}
