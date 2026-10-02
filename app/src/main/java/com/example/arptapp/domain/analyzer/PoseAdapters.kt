package com.example.arptapp.domain.analyzer

import com.example.arptapp.data.model.Landmark
import com.example.arptapp.data.model.PoseData
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

internal fun List<NormalizedLandmark>.toAnalysisPose() = PoseData(System.currentTimeMillis(), map {
    Landmark(it.x(), it.y(), it.z(), it.visibility().orElse(0f))
}, emptyMap())
