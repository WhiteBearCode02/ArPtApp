package com.example.arptapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private val pointPaint = Paint()
    private val linePaint = Paint()

    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var scaleFactor: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    init {
        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = 12f
        pointPaint.style = Paint.Style.FILL
        linePaint.color = Color.CYAN
        linePaint.strokeWidth = 8f
    }

    fun setResults(
        poseLandmarkerResult: PoseLandmarkerResult,
        height: Int,
        width: Int,
        runningMode: RunningMode
    ) {
        results = poseLandmarkerResult
        this.imageHeight = height
        this.imageWidth = width
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        results?.let { poseLandmarkerResult ->
            // 화면 크기와 이미지 크기 비율에 따라 스케일을 계산합니다 (Center Crop 방식)
            scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)

            // 중앙 정렬을 위한 오프셋을 계산합니다.
            offsetX = (width - imageWidth * scaleFactor) / 2f
            offsetY = (height - imageHeight * scaleFactor) / 2f

            for (landmark in poseLandmarkerResult.landmarks()) {
                for (point in landmark) {
                    // 전면 카메라 좌우 반전(1-point.x)과 스케일, 오프셋을 모두 적용합니다.
                    val canvasX = (1 - point.x()) * imageWidth * scaleFactor + offsetX
                    val canvasY = point.y() * imageHeight * scaleFactor + offsetY

                    canvas.drawCircle(canvasX, canvasY, 10f, pointPaint)
                }
                drawSkeleton(canvas, landmark)
            }
        }
    }

    private fun drawSkeleton(
        canvas: Canvas,
        landmark: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>
    ) {
        // 어깨, 팔, 몸통, 다리 연결
        drawLine(canvas, landmark[11], landmark[12])
        drawLine(canvas, landmark[12], landmark[14])
        drawLine(canvas, landmark[14], landmark[16])
        drawLine(canvas, landmark[11], landmark[13])
        drawLine(canvas, landmark[13], landmark[15])
        drawLine(canvas, landmark[11], landmark[23])
        drawLine(canvas, landmark[12], landmark[24])
        drawLine(canvas, landmark[23], landmark[24])
        drawLine(canvas, landmark[24], landmark[26])
        drawLine(canvas, landmark[26], landmark[28])
        drawLine(canvas, landmark[23], landmark[25])
        drawLine(canvas, landmark[25], landmark[27])
    }

    private fun drawLine(
        canvas: Canvas,
        start: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
        end: com.google.mediapipe.tasks.components.containers.NormalizedLandmark
    ) {
        val startX = (1 - start.x()) * imageWidth * scaleFactor + offsetX
        val startY = start.y() * imageHeight * scaleFactor + offsetY
        val endX = (1 - end.x()) * imageWidth * scaleFactor + offsetX
        val endY = end.y() * imageHeight * scaleFactor + offsetY

        canvas.drawLine(startX, startY, endX, endY, linePaint)
    }
}