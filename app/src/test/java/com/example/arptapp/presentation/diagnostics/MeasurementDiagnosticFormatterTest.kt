package com.example.arptapp.presentation.diagnostics

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import org.junit.Assert.*
import org.junit.Test

class MeasurementDiagnosticFormatterTest {
    @Test fun allSevenDisplayActualPolicyThresholdsAndRoutingIdentity() {
        val thresholds = listOf("90.0", "70.0", "70.0", "100.0", "90.0", "90.0", "15.0")
        ExerciseType.supported.forEachIndexed { i, type ->
            val text = MeasurementDiagnosticFormatter.format(MeasurementDiagnosticSnapshot(type.name, "SQUAT",
                CameraView.FRONT, false, null, 800, 2, "WAIT_BASELINE", "LOW_VISIBILITY", .4f))
            assertTrue(text.contains("선택/session: ${type.name}"))
            assertTrue(text.contains(thresholds[i]))
            assertTrue(text.contains("800 ms / 1000 ms"))
            assertTrue(text.contains("LOW_VISIBILITY"))
            assertTrue(text.contains("미측정"))
            assertFalse(text.contains("NaN"))
        }
    }
}
