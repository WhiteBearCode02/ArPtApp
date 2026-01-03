package com.example.arptapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * AI가 분석한 관절 좌표를 화면에 실시간으로 그리는 커스텀 뷰입니다.
 */
class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private val pointPaint = Paint()
    private val linePaint = Paint()

    init {
        // 관절 포인트를 그릴 붓 설정
        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = 12f
        pointPaint.style = Paint.Style.FILL

        // 관절 사이의 연결선을 그릴 붓 설정
        linePaint.color = Color.CYAN
        linePaint.strokeWidth = 8f
    }

    // 데이터가 갱신될 때마다 화면을 다시 그리도록 요청
    fun setResults(poseLandmarkerResult: PoseLandmarkerResult) {
        results = poseLandmarkerResult
        invalidate() // onDraw()를 다시 호출함
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        results?.let { poseLandmarkerResult ->
            for (landmark in poseLandmarkerResult.landmarks()) {
                // 관절 포인트 그리기
                for (point in landmark) {
                    val x = point.x() * width
                    val y = point.y() * height
                    canvas.drawCircle(x, y, 8f, pointPaint)
                }

                // 주요 관절 연결선 그리기 (어깨, 팔꿈치, 골반, 무릎, 발목 등)
                drawSkeleton(canvas, landmark)
            }
        }
    }

    // 특정 관절들을 연결하여 뼈대를 형성함
    private fun drawSkeleton(canvas: Canvas, landmark: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>) {
        // 어깨 연결 (11 - 12)
        drawLine(canvas, landmark[11], landmark[12])
        // 오른쪽 팔 (12 - 14 - 16)
        drawLine(canvas, landmark[12], landmark[14])
        drawLine(canvas, landmark[14], landmark[16])
        // 왼쪽 팔 (11 - 13 - 15)
        drawLine(canvas, landmark[11], landmark[13])
        drawLine(canvas, landmark[13], landmark[15])
        // 몸통 연결 (11 - 23, 12 - 24, 23 - 24)
        drawLine(canvas, landmark[11], landmark[23])
        drawLine(canvas, landmark[12], landmark[24])
        drawLine(canvas, landmark[23], landmark[24])
        // 오른쪽 다리 (24 - 26 - 28)
        drawLine(canvas, landmark[24], landmark[26])
        drawLine(canvas, landmark[26], landmark[28])
        // 왼쪽 다리 (23 - 25 - 27)
        drawLine(canvas, landmark[23], landmark[25])
        drawLine(canvas, landmark[25], landmark[27])
    }

    private fun drawLine(canvas: Canvas, start: com.google.mediapipe.tasks.components.containers.NormalizedLandmark, end: com.google.mediapipe.tasks.components.containers.NormalizedLandmark) {
        canvas.drawLine(
            start.x() * width, start.y() * height,
            end.x() * width, end.y() * height,
            linePaint
        )
    }
}