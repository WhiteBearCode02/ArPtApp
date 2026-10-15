package com.example.arptapp.domain.analyzer

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2
import com.example.arptapp.data.model.Landmark
import com.example.arptapp.domain.report.EngineeringFormPolicy

object FormErrorAnalyzer {
    data class KneeValgusObservation(val leftRatio: Double? = null, val rightRatio: Double? = null) {
        val leftObserved: Boolean get() = leftRatio?.let { it > EngineeringFormPolicy.KNEE_VALGUS_RATIO } == true
        val rightObserved: Boolean get() = rightRatio?.let { it > EngineeringFormPolicy.KNEE_VALGUS_RATIO } == true
    }
    data class Assessment(val evaluable: Boolean, val errors: List<String>, val reason: String? = null,
                          val kneeValgus: KneeValgusObservation? = null)
    const val ERROR_KNEE_VALGUS = "ERROR_KNEE_VALGUS"
    const val ERROR_FORWARD_LEAN = "ERROR_FORWARD_LEAN"

    private const val MIN_VISIBILITY = 0.65f
    private const val VALGUS_RATIO_THRESHOLD = 0.08f
    private const val FORWARD_LEAN_DEGREES = 20.0

    fun assessSquat(landmarks: List<NormalizedLandmark>, view: CameraView, imageAspectRatio: Double = 1.0): Assessment {
        if (view == CameraView.UNKNOWN) return Assessment(false, emptyList(), "촬영 방향을 선택하면 자세 코칭을 확인할 수 있습니다")
        val knees = observeKneeValgus(landmarks, view)
        if (view == CameraView.SIDE) {
            val points = landmarks.map { Landmark(it.x(), it.y(), it.z(), it.visibility().orElse(0f)) }
            val offset = PoseAngleExtractor.visibleSideOffset(points, "SQUAT")
            val requiredSide = listOf(11, 23, 25, 27).map { it + offset }
            if (!imageAspectRatio.isFinite() || imageAspectRatio <= 0 || requiredSide.any { id -> points.getOrNull(id)?.let {
                    it.visibility.isFinite() && it.visibility >= MIN_VISIBILITY && it.x.isFinite() && it.y.isFinite() && it.z.isFinite()
                } != true }) return Assessment(false, emptyList(), "코칭 측정 대기 · 보이는 쪽 관절이 가려져 있습니다")
            val shoulder = points[11 + offset]; val hip = points[23 + offset]
            if (shoulder.x == hip.x && shoulder.y == hip.y) return Assessment(false, emptyList(), "몸통을 측정할 수 없습니다")
            val lean = Math.toDegrees(atan2(abs(shoulder.x - hip.x) * imageAspectRatio, abs(shoulder.y - hip.y).toDouble()))
            return Assessment(true, if (lean > FORWARD_LEAN_DEGREES) listOf(ERROR_FORWARD_LEAN) else emptyList())
        }
        val required = if (view == CameraView.FRONT) intArrayOf(23, 24, 25, 26, 27, 28) else intArrayOf(11, 12, 23, 24)
        if (required.any { it >= landmarks.size || !landmarks[it].visibility().orElse(0f).isFinite() || landmarks[it].visibility().orElse(0f) < MIN_VISIBILITY ||
                !landmarks[it].x().isFinite() || !landmarks[it].y().isFinite() }) {
            return Assessment(false, emptyList(), "코칭 측정 대기 · 필요한 관절이 가려져 있습니다", knees)
        }
        val errors = when (view) {
            CameraView.FRONT -> if (knees.leftObserved || knees.rightObserved) listOf(ERROR_KNEE_VALGUS) else emptyList()
            CameraView.SIDE -> if (hasForwardLean(landmarks[11], landmarks[23]) || hasForwardLean(landmarks[12], landmarks[24])) listOf(ERROR_FORWARD_LEAN) else emptyList()
            else -> emptyList()
        }
        return Assessment(true, errors, kneeValgus = knees)
    }

    /** Anatomical LEFT=23/25/27, RIGHT=24/26/28, never screen-left after mirroring. */
    fun observeKneeValgus(landmarks: List<NormalizedLandmark>, view: CameraView): KneeValgusObservation =
        observe(landmarks, view) { p -> Landmark(p.x(), p.y(), p.z(), p.visibility().orElse(0f)) }

    /** Pure scalar/quality API for the extractor and JVM fixtures. null side is NOT_EVALUATED. */
    fun observeSquatKnees(points: List<Landmark>, view: CameraView): KneeValgusObservation = observe(points, view) { it }

    private fun <T> observe(points: List<T>, view: CameraView, point: (T) -> Landmark): KneeValgusObservation {
        if (view != CameraView.FRONT) return KneeValgusObservation()
        fun ratio(hipIndex: Int, kneeIndex: Int, ankleIndex: Int): Double? {
            if (listOf(hipIndex, kneeIndex, ankleIndex).any { it !in points.indices }) return null
            val hip = point(points[hipIndex]); val knee = point(points[kneeIndex]); val ankle = point(points[ankleIndex])
            if (listOf(hip, knee, ankle).any { !it.visibility.isFinite() || it.visibility < MIN_VISIBILITY ||
                    !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() }) return null
            // Existing projected inward-offset heuristic is preserved, not a clinical valgus angle.
            val center = (hip.x + ankle.x) / 2f
            val width = abs(hip.x - ankle.x).coerceAtLeast(0.1f)
            val inward = if (hip.x < ankle.x) knee.x - center else center - knee.x
            return (inward / width).toDouble().takeIf { it.isFinite() }
        }
        return KneeValgusObservation(ratio(23, 25, 27), ratio(24, 26, 28))
    }

    fun analyzeSquat(landmarks: List<NormalizedLandmark>): List<String> {
        if (landmarks.size < 29) return emptyList()
        val required = intArrayOf(11, 12, 23, 24, 25, 26, 27, 28)
        if (required.any { landmarks[it].visibility().orElse(0f) < MIN_VISIBILITY }) {
            return emptyList()
        }

        val errors = mutableListOf<String>()
        if (hasKneeValgus(landmarks, 23, 25, 27) || hasKneeValgus(landmarks, 24, 26, 28)) {
            errors += ERROR_KNEE_VALGUS
        }
        if (hasForwardLean(landmarks[11], landmarks[23]) || hasForwardLean(landmarks[12], landmarks[24])) {
            errors += ERROR_FORWARD_LEAN
        }
        return errors
    }

    private fun hasKneeValgus(
        landmarks: List<NormalizedLandmark>,
        hipIndex: Int,
        kneeIndex: Int,
        ankleIndex: Int
    ): Boolean {
        val hip = landmarks[hipIndex]
        val knee = landmarks[kneeIndex]
        val ankle = landmarks[ankleIndex]
        val centerX = (hip.x() + ankle.x()) / 2f
        val legWidth = abs(hip.x() - ankle.x()).coerceAtLeast(0.1f)
        val inwardOffset = if (hip.x() < ankle.x()) knee.x() - centerX else centerX - knee.x()
        return inwardOffset / legWidth > VALGUS_RATIO_THRESHOLD
    }

    private fun hasForwardLean(shoulder: NormalizedLandmark, hip: NormalizedLandmark): Boolean {
        val dx = abs(shoulder.x() - hip.x()).toDouble()
        val dy = abs(shoulder.y() - hip.y()).toDouble()
        if (dx == 0.0 && dy == 0.0) return false
        val angleFromVertical = Math.toDegrees(atan2(dx, dy))
        return angleFromVertical > FORWARD_LEAN_DEGREES
    }
}
