package com.example.arptapp.model

/** A single completed repetition's quality measurements. */
data class RepRecord(
    val maxBendAngle: Float,
    val swayX: Float
)

/** Summary produced when an exercise session ends. */
data class SessionReport(
    val exerciseType: String,
    val totalReps: Int,
    val averageScore: Float,
    val feedback: String
)
