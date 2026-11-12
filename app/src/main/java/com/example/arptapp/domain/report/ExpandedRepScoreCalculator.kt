package com.example.arptapp.domain.report

/** ENGINEERING_INITIAL comparison only. Components without measured evidence leave the denominator. */
internal object ExpandedRepScoreCalculator {
    fun calculate(rep: StructuredRepAnalysis, comparisonRom: Double): RepScoreBreakdown {
        val measurable = rep.cameraView in listOf("FRONT", "SIDE") && rep.observedFrames >= 3
        val j = rep.jointMetrics
        fun value(v: Double?) = v?.takeIf { it.isFinite() && it in 0.0..180.0 }
        val roms = if (rep.exerciseType == "SIDE_BEND") listOfNotNull(value(j.peakLateralFlexionDeg)) else
            listOfNotNull(value(j.leftElbowRomDeg), value(j.rightElbowRomDeg))
        val rom = roms.takeIf { measurable && it.isNotEmpty() }?.average()?.div(comparisonRom)
        val difference = when (rep.exerciseType) {
            "BICEP_CURL" -> rep.asymmetryMetrics.contralateralRomDifferenceDeg
            "SIDE_BEND" -> rep.asymmetryMetrics.lateralRangeDifferenceDeg
            else -> rep.asymmetryMetrics.elbowAngleDifferenceDeg
        }
        val symmetry = if (measurable && rep.cameraView == "FRONT") value(difference)?.let { 1 - it / RepScorePolicy.SYMMETRY_FULL_LOSS_DEG } else null
        val variations = when (rep.exerciseType) {
            "SIDE_BEND" -> listOfNotNull(value(j.returnToNeutralDeviationDeg))
            "PUSH_UP" -> listOfNotNull(value(j.bodyLineDeviationDeg))
            else -> listOfNotNull(value(j.upperArmDriftLeftDeg), value(j.upperArmDriftRightDeg), value(j.torsoInclinationChangeDeg))
        }
        val stability = variations.takeIf { measurable && it.isNotEmpty() }?.maxOrNull()?.let {
            1 - it / com.example.arptapp.domain.analyzer.ExerciseMotionPolicy.STABILITY_FULL_LOSS_DEG
        }
        val phases = if (measurable && !rep.partialCapture && rep.phaseModelVersion >= 2 &&
            rep.phaseDurations.ascentMs?.let { it > 0 } == true &&
            (rep.exerciseType == "TRICEPS_EXTENSION" || rep.phaseDurations.descentMs?.let { it > 0 } == true)) 1.0 else null
        fun part(c: RepScoreComponent, weight: Float, ratio: Double?) = ComponentScore(c, weight,
            ratio?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)?.let { (it * weight).toFloat() })
        val penalties = if (rom != null) rep.errorEvents.filter { it.occurrenceFrames >= 3 && it.severity in 1..3 }
            .groupBy { it.type }.map { (type, events) -> IssuePenalty(type, minOf(RepScorePolicy.ISSUE_PENALTY_CAP, events.maxOf { it.severity }.toFloat())) } else emptyList()
        val parts = listOf(part(RepScoreComponent.RANGE_OF_MOTION, RepScorePolicy.ROM_WEIGHT, rom),
            part(RepScoreComponent.SYMMETRY, RepScorePolicy.SYMMETRY_WEIGHT, symmetry), part(RepScoreComponent.STABILITY, RepScorePolicy.STABILITY_WEIGHT, stability),
            part(RepScoreComponent.PHASE_COMPLETENESS, RepScorePolicy.PHASE_WEIGHT, phases),
            ComponentScore(RepScoreComponent.ERROR_EVENTS, RepScorePolicy.ERROR_WEIGHT, if (rom != null) (RepScorePolicy.ERROR_WEIGHT - penalties.sumOf { it.points.toDouble() }.toFloat()).coerceAtLeast(0f) else null))
        val weight = parts.filter { it.earned != null }.sumOf { it.weight.toDouble() }.toFloat()
        val earned = parts.sumOf { it.earned?.toDouble() ?: 0.0 }.toFloat()
        return RepScoreBreakdown(policyVersion = 2, components = parts, availableWeight = weight, earned = earned,
            normalizedScore = if (rom != null && weight > 0) (kotlin.math.round(earned / weight * 1000) / 10).coerceIn(0f, 100f) else null,
            penalties = penalties)
    }
}
