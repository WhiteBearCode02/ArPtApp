package com.example.arptapp.model

data class RepRecord(
    val repNumber: Int,
    val maxAngle: Double,
    val swayX: Float
)

data class SessionReport(
    val exerciseType: String,
    val totalReps: Int,
    val averageScore: Int,
    val feedbackMessage: String
)
