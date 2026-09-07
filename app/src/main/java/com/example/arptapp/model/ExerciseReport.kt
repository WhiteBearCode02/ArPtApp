package com.example.arptapp.model

data class RepRecord(
    val repNumber: Int,
    val maxAngle: Double,
    val swayX: Float,
    val errorTags: List<String> = emptyList(),
    val eccentricDurationMs: Long = 0L,
    val concentricDurationMs: Long = 0L
)

data class SessionReport(
    val exerciseType: String,
    val totalReps: Int,
    val averageScore: Int,
    val feedbackMessage: String
)
