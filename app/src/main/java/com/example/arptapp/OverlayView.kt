package com.example.arptapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

/**
 * 실시간 포즈 스켈레톤 오버레이 뷰
 */
class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var landmarks: List<NormalizedLandmark>? = null
    private var isFrontCamera = true

    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var scaleFactor: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    private val pointPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 12f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    // === 랜드마크 데이터 및 해상도 정보 설정 === [cite: 2026-01-15]
    fun setResults(
        landmarks: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int,
        isFrontCamera: Boolean
    ) {
        // [로그 추가] Log.d("태그", "내용") 형식으로 출력합니다.
        android.util.Log.d("ARPT_DEBUG", "--------------------------------------")
        android.util.Log.d("ARPT_DEBUG", "1. 전달받은 이미지 크기: ${imageWidth}x${imageHeight}")
        android.util.Log.d("ARPT_DEBUG", "2. 실제 OverlayView 크기: ${this.width}x${this.height}")

        this.landmarks = landmarks
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.isFrontCamera = isFrontCamera
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        landmarks?.let { points ->
            // [추론] 화면 배율 및 오프셋 계산 (Center-Crop 보정) [cite: 2026-01-15]
            scaleFactor = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
            offsetX = (width - imageWidth * scaleFactor) / 2f
            offsetY = (height - imageHeight * scaleFactor) / 2f

            for (point in points) {
                canvas.drawCircle(getCanvasX(point.x()), getCanvasY(point.y()), 10f, pointPaint)
            }

            drawSkeleton(canvas, points)
        }
    }

    // === 좌표 변환 (미러링 및 스케일 적용) === [cite: 2025-10-30]
    private fun getCanvasX(x: Float): Float {
        return if (isFrontCamera) (1 - x) * imageWidth * scaleFactor + offsetX
        else x * imageWidth * scaleFactor + offsetX
    }

    private fun getCanvasY(y: Float): Float {
        return y * imageHeight * scaleFactor + offsetY
    }

    // === 스켈레톤 연결선 그리기 === [cite: 2026-01-15]
    private fun drawSkeleton(canvas: Canvas, points: List<NormalizedLandmark>) {
        val connections = listOf(
            Pair(11, 12), Pair(12, 14), Pair(14, 16), Pair(11, 13), Pair(13, 15),
            Pair(11, 23), Pair(12, 24), Pair(23, 24), Pair(24, 26), Pair(26, 28),
            Pair(23, 25), Pair(25, 27)
        )
        for (conn in connections) {
            if (conn.first < points.size && conn.second < points.size) {
                val start = points[conn.first]
                val end = points[conn.second]
                canvas.drawLine(getCanvasX(start.x()), getCanvasY(start.y()),
                    getCanvasX(end.x()), getCanvasY(end.y()), linePaint)
            }
        }
    }
}