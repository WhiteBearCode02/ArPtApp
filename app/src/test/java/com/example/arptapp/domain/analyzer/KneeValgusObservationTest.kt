package com.example.arptapp.domain.analyzer

import com.example.arptapp.data.model.Landmark
import com.example.arptapp.domain.report.RepFrameFeatureExtractor
import org.junit.Assert.*
import org.junit.Test
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.util.Optional

class KneeValgusObservationTest {
    private fun points(left: Boolean, right: Boolean) = MutableList(33) { Landmark(0f, 0f, 0f, 1f) }.apply {
        this[23] = Landmark(0.3f, 0.2f, 0f, 1f); this[25] = Landmark(if (left) 0.44f else 0.40f, 0.5f, 0f, 1f); this[27] = Landmark(0.5f, 0.8f, 0f, 1f)
        this[24] = Landmark(0.7f, 0.2f, 0f, 1f); this[26] = Landmark(if (right) 0.56f else 0.60f, 0.5f, 0f, 1f); this[28] = Landmark(0.5f, 0.8f, 0f, 1f)
    }
    @Test fun leftOnly() { val o = FormErrorAnalyzer.observeSquatKnees(points(true, false), CameraView.FRONT); assertTrue(o.leftObserved); assertFalse(o.rightObserved) }
    @Test fun rightOnly() { val o = FormErrorAnalyzer.observeSquatKnees(points(false, true), CameraView.FRONT); assertFalse(o.leftObserved); assertTrue(o.rightObserved) }
    @Test fun both() { val o = FormErrorAnalyzer.observeSquatKnees(points(true, true), CameraView.FRONT); assertTrue(o.leftObserved); assertTrue(o.rightObserved) }
    @Test fun neither() { val o = FormErrorAnalyzer.observeSquatKnees(points(false, false), CameraView.FRONT); assertFalse(o.leftObserved); assertFalse(o.rightObserved) }
    @Test fun sideAndUnknownAreNotEvaluated() {
        listOf(CameraView.SIDE, CameraView.UNKNOWN).forEach { v ->
            val o = FormErrorAnalyzer.observeSquatKnees(points(true, true), v)
            assertNull(o.leftRatio); assertNull(o.rightRatio); assertFalse(o.leftObserved); assertFalse(o.rightObserved)
        }
    }
    @Test fun occlusionDoesNotSuppressTheOtherUsableSideOrBecomeNormal() {
        val p = points(true, true); p[25] = p[25].copy(visibility = 0.2f)
        val o = FormErrorAnalyzer.observeSquatKnees(p, CameraView.FRONT)
        assertNull(o.leftRatio); assertTrue(o.rightObserved)
    }
    @Test fun nonFiniteAndMissingCoordinatesDoNotProduceRatios() {
        assertNull(FormErrorAnalyzer.observeSquatKnees(emptyList(), CameraView.FRONT).leftRatio)
        val p = points(true, true); p[25] = p[25].copy(x = Float.NaN); p[26] = p[26].copy(visibility = Float.NaN)
        val o = FormErrorAnalyzer.observeSquatKnees(p, CameraView.FRONT); assertNull(o.leftRatio); assertNull(o.rightRatio)
    }
    @Test fun horizontalMirrorDoesNotExchangeAnatomicalLabels() {
        val p = points(true, false).map { it.copy(x = 1f - it.x) }
        val o = FormErrorAnalyzer.observeSquatKnees(p, CameraView.FRONT)
        assertTrue(o.leftObserved); assertFalse(o.rightObserved)
    }
    @Test fun extractorPreservesOnlyTransientSidedRatios() {
        val f = RepFrameFeatureExtractor.extract(points(false, true), "SQUAT", CameraView.FRONT, 1.0, 10000, 120.0)
        assertFalse(f.leftKneeValgusObserved); assertTrue(f.rightKneeValgusObserved)
        assertEquals(0.2, f.rightKneeValgusRatio!!, 0.001)
    }
    @Test fun existingAssessmentApiRetainsAggregateTagAndExposesSideDetails() {
        val native = points(false, true).map { NormalizedLandmark.create(it.x, it.y, it.z, Optional.of(it.visibility), Optional.of(1f)) }
        val result = FormErrorAnalyzer.assessSquat(native, CameraView.FRONT)
        assertTrue(result.evaluable); assertEquals(listOf(FormErrorAnalyzer.ERROR_KNEE_VALGUS), result.errors)
        assertFalse(result.kneeValgus!!.leftObserved); assertTrue(result.kneeValgus.rightObserved)
    }
}
