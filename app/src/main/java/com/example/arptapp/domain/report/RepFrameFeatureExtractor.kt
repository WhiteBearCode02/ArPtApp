package com.example.arptapp.domain.report

import com.example.arptapp.data.model.Landmark
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.analyzer.JointAngleMeasurement
import com.example.arptapp.domain.analyzer.FormErrorAnalyzer
import kotlin.math.abs
import kotlin.math.atan2

/** Camera boundary: immediately discards coordinates; never stores or uploads landmarks. */
object RepFrameFeatureExtractor {
    fun extract(points: List<Landmark>, exercise: String, view: CameraView, aspect: Double,
                timestampMs: Long, representative: Double?, kneeValgus: Boolean = false,
                kneeObservation: FormErrorAnalyzer.KneeValgusObservation? = null): RepFrameFeatures {
        fun angle(vararg indices: Int) = JointAngleMeasurement.measure(points, indices.toList(),
            "rep_joint", view = view, imageAspectRatio = aspect).valueDeg
        // In side view keep only the side whose required joints have higher usable visibility.
        val leftVisible = view == CameraView.FRONT || (view == CameraView.SIDE &&
            com.example.arptapp.domain.analyzer.PoseAngleExtractor.visibleSideOffset(points, exercise) == 0)
        val rightVisible = view == CameraView.FRONT || (view == CameraView.SIDE && !leftVisible)
        val torsoOffset = if (leftVisible) 0 else 1
        val shoulder = points.getOrNull(11 + torsoOffset)
        val hip = points.getOrNull(23 + torsoOffset)
        val squat = exercise == "SQUAT"
        val lean = if (squat && view == CameraView.SIDE && shoulder != null && hip != null &&
            aspect.isFinite() && aspect > 0 && listOf(shoulder, hip).all {
                it.visibility.isFinite() && it.visibility >= 0.65f && it.x.isFinite() && it.y.isFinite()
            } && (shoulder.x != hip.x || shoulder.y != hip.y))
            Math.toDegrees(atan2(abs(shoulder.x - hip.x) * aspect, abs(shoulder.y - hip.y).toDouble())) else null
        val knees = if (exercise == "SQUAT" && view == CameraView.FRONT)
            kneeObservation ?: FormErrorAnalyzer.observeSquatKnees(points, view) else FormErrorAnalyzer.KneeValgusObservation()
        return RepFrameFeatures(timestampMs, exercise, view, representative,
            leftKnee = if (squat && leftVisible) angle(23, 25, 27) else null,
            rightKnee = if (squat && rightVisible) angle(24, 26, 28) else null,
            leftHip = if (squat && leftVisible && view == CameraView.SIDE) angle(11, 23, 25) else null,
            rightHip = if (squat && rightVisible && view == CameraView.SIDE) angle(12, 24, 26) else null,
            leftElbow = if (!squat && leftVisible) angle(11, 13, 15) else null,
            rightElbow = if (!squat && rightVisible) angle(12, 14, 16) else null,
            torsoLean = lean, kneeValgusObserved = view == CameraView.FRONT && kneeValgus,
            leftKneeValgusObserved = knees.leftObserved, rightKneeValgusObserved = knees.rightObserved,
            leftKneeValgusRatio = knees.leftRatio, rightKneeValgusRatio = knees.rightRatio,
            visibleSide = if (view != CameraView.SIDE) null else if (leftVisible) BodySide.LEFT else BodySide.RIGHT)
    }
}
