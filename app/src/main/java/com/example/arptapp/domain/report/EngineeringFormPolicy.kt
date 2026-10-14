package com.example.arptapp.domain.report

/** All bands are ENGINEERING_INITIAL display priorities, NOT clinical severity/risk. */
object EngineeringFormPolicy {
    const val SOURCE = "ENGINEERING_INITIAL"
    const val MIN_VISIBILITY = 0.65f
    const val KNEE_VALGUS_RATIO = 0.08
    const val ASYMMETRY_DEG = 15.0
    const val LEAN_DEG = 20.0
    const val MIN_EVENT_FRAMES = 3
    // Preserve detection thresholds; only the display priority varies with excess magnitude.
    fun asymmetrySeverity(value: Double) = band(value, ASYMMETRY_DEG, 25.0, 35.0)
    fun leanSeverity(value: Double) = band(value, LEAN_DEG, 30.0, 40.0)
    fun valgusSeverity(value: Double?) = if (value == null) 1 else band(value, KNEE_VALGUS_RATIO, 0.16, 0.24)
    private fun band(value: Double, threshold: Double, medium: Double, high: Double): Int = when {
        !value.isFinite() || value <= threshold -> 0
        value > high -> 3
        value > medium -> 2
        else -> 1
    }
}
