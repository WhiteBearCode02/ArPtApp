package com.example.arptapp.presentation.report

import com.example.arptapp.data.ExerciseRecord
import com.example.arptapp.data.YoloSessionDiagnosticsCodec
import com.example.arptapp.domain.detection.YoloSessionDiagnostics
import com.example.arptapp.model.RepAnalysis
import org.junit.Assert.*
import org.junit.Test

class WorkoutReportDataTest {
    @Test fun `report input uses nonnegative units and finite bounded scores`() {
        val report = WorkoutReportData.create(
            exerciseType = " ", totalCount = -3, averageScore = Float.NaN,
            durationSeconds = -10L, burnedCalories = Double.POSITIVE_INFINITY
        )
        assertEquals("운동 기록", report.exerciseType)
        assertEquals(0, report.totalCount)
        assertEquals(0f, report.averageScore, 0f)
        assertEquals(0L, report.durationSeconds)
        assertNull(report.burnedCalories)
        assertEquals("[]", report.analysesJson)
        assertEquals(100f, WorkoutReportData.create(averageScore = 120f).averageScore, 0f)
    }

    @Test fun `legacy scores keep repetition positions without invalid JSON numbers`() {
        val report = WorkoutReportData.create(
            legacyScores = listOf(80f, Float.NaN, 101f, Float.NEGATIVE_INFINITY, 90f)
        )
        assertEquals("[80.0, null, null, null, 90.0]", report.analysesJson)
    }

    @Test fun `current analysis JSON takes precedence over legacy scores`() {
        val json = "[{\"rep\":2,\"score\":85,\"detail\":\"관찰\"}]"
        assertEquals(json, WorkoutReportData.create(analysesJson = json, legacyScores = listOf(10f)).analysesJson)
    }

    @Test fun `completed workout estimates calories only when no value was provided`() {
        val completed = WorkoutReportData.create(totalCount = 12).completedAt("2026-10-13 20:00")
        assertEquals(6.0, completed.burnedCalories!!, 0.0)
        assertEquals("2026-10-13 20:00", completed.workoutDate)
        assertTrue(completed.feedbackMessage.isNotBlank())
        assertEquals(0.0, WorkoutReportData.create(totalCount = 12, burnedCalories = 0.0)
            .completedAt("date").burnedCalories!!, 0.0)
    }

    @Test fun `completion preserves original date and detailed feedback`() {
        val report = WorkoutReportData.create(workoutDate = "original", feedbackMessage = "기존 관찰")
            .completedAt("replacement")
        assertEquals("original", report.workoutDate)
        assertEquals("기존 관찰", report.feedbackMessage)
    }

    @Test fun `history model round trip preserves report details and account ownership`() {
        val original = ExerciseRecord(
            userId = "account-A", date = "2026-10-13 20:00", totalCount = 3, duration = 90,
            burnedCalories = 1.5, exerciseType = "스쿼트", averageScore = 82f,
            scoresJson = "[80,84]", feedbackMessage = "회차별 관찰"
        )
        val report = WorkoutReportData.fromRecord(original)
        assertEquals(original, report.toRecord("account-A"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank account cannot receive a local workout record`() {
        WorkoutReportData.create().toRecord(" ")
    }

    @Test fun `displayed score falls back to repetition average for legacy records`() {
        val analyses = listOf(RepAnalysis(1, 80f, ""), RepAnalysis(2, 90f, ""))
        val report = WorkoutReportData.create()
        assertEquals(85f, WorkoutReportPresentation.displayScore(report, analyses)!!, 0f)
        assertEquals(92f, WorkoutReportPresentation.displayScore(
            WorkoutReportData.create(averageScore = 92f), analyses)!!, 0f)
        assertNull(WorkoutReportPresentation.displayScore(report, emptyList()))
    }

    @Test fun `duration formatting keeps seconds units and handles negative input`() {
        assertEquals("01분 05초", WorkoutReportPresentation.formatDuration(65))
        assertEquals("00분 00초", WorkoutReportPresentation.formatDuration(-1))
    }

    @Test fun `score fallback feedback never guarantees correct posture`() {
        val feedback = WorkoutReportPresentation.feedback(100f)
        assertFalse(feedback.contains("완벽"))
        assertFalse(feedback.contains("올바른 자세"))
    }

    @Test fun `separate local diagnostics survives completion Room model and report round trip`() {
        val diagnostics = YoloSessionDiagnostics(modelStatus = "MODEL_MISSING", contractStatus = "NOT_VERIFIED",
            predictionEnabled = false, startedAt = 1000, endedAt = 2000)
        val raw = YoloSessionDiagnosticsCodec.encode(diagnostics)!!
        val report = WorkoutReportData.create(totalCount = 4, analysesJson = "[80,90]", yoloDiagnosticsJson = raw)
            .completedAt("2026-10-16 20:00")
        val record = report.toRecord("owner-A")
        assertEquals(raw, record.yoloDiagnosticsJson)
        assertEquals(raw, WorkoutReportData.fromRecord(record).yoloDiagnosticsJson)
        assertEquals("[80,90]", record.scoresJson)
        assertEquals("[80,90]", WorkoutReportData.fromRecord(record).analysesJson)
        assertEquals(4, record.totalCount)
        assertEquals(2.0, record.burnedCalories, 0.0)
    }

    @Test fun `old records retain null optional diagnostics`() {
        val record = WorkoutReportData.create().completedAt("date").toRecord("owner-A")
        assertNull(record.yoloDiagnosticsJson)
        assertNull(WorkoutReportData.fromRecord(record).yoloDiagnosticsJson)
    }

    @Test fun `malformed local diagnostics never blocks existing analyses`() {
        val report = WorkoutReportData.create(analysesJson = "[75]", yoloDiagnosticsJson = "{bad}")
        assertNull(report.yoloDiagnosticsJson)
        assertEquals("[75]", report.analysesJson)
        val record = report.toRecord("owner").copy(yoloDiagnosticsJson = "{}")
        assertNull(WorkoutReportData.fromRecord(record).yoloDiagnosticsJson)
        assertEquals("[75]", WorkoutReportData.fromRecord(record).analysesJson)
    }

    @Test fun `oversized diagnostics is discarded before intent or persistence`() {
        assertNull(WorkoutReportData.create(yoloDiagnosticsJson = "a".repeat(16_385)).yoloDiagnosticsJson)
    }
}
