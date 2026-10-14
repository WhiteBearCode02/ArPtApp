package com.example.arptapp.domain.report

import com.example.arptapp.data.RepAnalysisCodec
import com.example.arptapp.data.StructuredReportCodec
import com.example.arptapp.model.RepAnalysis
import org.junit.Assert.*
import org.junit.Test

class StructuredReportCodecTest {
    @Test fun existingScoresAndAnalysesRemainReadable() {
        val legacy = RepAnalysisCodec.decode("[90,null,80]")
        assertEquals(listOf(1, 3), legacy.map { it.repNumber })
        val analyses = listOf(RepAnalysis(1, 80f, "기존 관찰"))
        assertEquals(analyses, RepAnalysisCodec.decode(RepAnalysisCodec.encode(analyses)))
        assertNull(StructuredReportCodec.decode("[90]"))
        assertTrue(RepAnalysisCodec.decode("invalid").isEmpty())
    }
    @Test fun structuredEnvelopeRetainsLegacyScoresAndFacts() {
        val rep = StructuredRepAnalysis(1, "SQUAT", "SIDE", 1000, PhaseDurations(ascentMs = 1000),
            JointMetrics(leftKneeMinAngle = 90.0), AsymmetryMetrics(), emptyList(), emptyList(), emptyList(),
            LocalRepFeedback.forIssue(null), 80f, 8)
        val structured = WorkoutAnalysisAggregator.aggregate("SQUAT", listOf(rep))
        val analyses = listOf(RepAnalysis(1, 80f, "기존 관찰"))
        val raw = StructuredReportCodec.encode(analyses, structured)
        assertEquals(structured, StructuredReportCodec.decode(raw))
        assertEquals(analyses, RepAnalysisCodec.decode(raw))
        assertFalse(raw.contains("email")); assertFalse(raw.contains("landmarks")); assertFalse(raw.contains("userId"))
    }
}
