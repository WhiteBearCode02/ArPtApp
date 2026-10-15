package com.example.arptapp.domain.report

/** Whitelisted diagnostic fields only. Never stringify frame/pose/session/request objects. */
object RepDiagnosticFormatter {
    fun format(rep: StructuredRepAnalysis, sessionExerciseType: String = rep.exerciseType,
               detectedExerciseType: String = "UNKNOWN", representativeAngle: Double? = null): String = buildString {
        val exercise = rep.exerciseType.takeIf { it in listOf("SQUAT", "SHOULDER_PRESS") } ?: "UNKNOWN"
        val view = rep.cameraView.takeIf { it in listOf("FRONT", "SIDE", "UNKNOWN") } ?: "UNKNOWN"
        append("rep=${rep.repNumber} exercise=$exercise view=$view phaseModel=${rep.phaseModelVersion}")
        append(" partial=${rep.partialCapture} frames=${rep.observedFrames} durations=${rep.phaseDurations}")
        append(" joints=${rep.jointMetrics} asymmetry=${rep.asymmetryMetrics}")
        fun type(value: String) = value.takeIf { it in listOf("SQUAT", "SHOULDER_PRESS", "LUNGE", "UNKNOWN", "IDLE") } ?: "UNKNOWN"
        append(" sessionExerciseType=${type(sessionExerciseType)} detectedExerciseType=${type(detectedExerciseType)} cameraView=$view")
        append(" representativeAngle=${representativeAngle?.takeIf { it.isFinite() && it in 0.0..180.0 }} localRepScore=${rep.score}")
        rep.localScore?.components?.forEach { append(" scoreComponent=${it.component}:${it.earned}/${it.weight}") }
        rep.errorEvents.forEach { e ->
            val relative = e.timestampMs?.takeIf { it in 0..20_000 }
            append(" event=${e.type}/${e.side}/${e.phase} severity=${e.severity} frames=${e.occurrenceFrames} relativeMs=$relative")
        }
    }
}
