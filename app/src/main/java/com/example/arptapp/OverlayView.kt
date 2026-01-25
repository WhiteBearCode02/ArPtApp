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
 *
 * [방법1] 프레임이 이미 정규화(0도)되어 들어오므로
 * 단순한 좌표 변환만 수행합니다.
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
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    /**
     * 랜드마크 데이터 및 해상도 정보 설정
     *
     * [방법1] 프레임이 이미 정규화되었으므로
     * imageWidth, imageHeight는 항상 정규화된 크기입니다.
     * (예: 전면 270도 회전 프레임 → 480x640이 된 상태)
     */
    fun setResults(
        landmarks: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int,
        isFrontCamera: Boolean
    ) {
        this.landmarks = landmarks

        // [방법1] 축 스왑 로직 제거
        // 프레임이 이미 0도로 정규화되었으므로
        // imageWidth, imageHeight를 그대로 사용
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.isFrontCamera = isFrontCamera

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        landmarks?.let { points ->
            // Center-Crop 배율 및 오프셋 계산
            scaleFactor = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
            offsetX = (width - imageWidth * scaleFactor) / 2f
            offsetY = (height - imageHeight * scaleFactor) / 2f

            // 각 포인트 그리기
            for (point in points) {
                canvas.drawCircle(getCanvasX(point.x()), getCanvasY(point.y()), 12f, pointPaint)
            }

            // 스켈레톤 연결선 그리기
            drawSkeleton(canvas, points)
        }
    }

    /**
     * 정규화된 X 좌표를 Canvas X 좌표로 변환
     *
     * [방법1] 프레임이 이미 정규화되었으므로
     * 전면 카메라 거울 반전만 적용
     */
    private fun getCanvasX(x: Float): Float {
        return if (isFrontCamera) {
            // 전면 카메라: 좌우 반전 (1 - x)
            (1 - x) * imageWidth * scaleFactor + offsetX
        } else {
            // 후면 카메라: 원본 (x)
            x * imageWidth * scaleFactor + offsetX
        }
    }

    /**
     * 정규화된 Y 좌표를 Canvas Y 좌표로 변환
     */
    private fun getCanvasY(y: Float): Float {
        return y * imageHeight * scaleFactor + offsetY
    }

    /**
     * 스켈레톤 연결선 그리기
     *
     * MediaPipe 33개 랜드마크 중 필요한 부분만 연결
     */
    private fun drawSkeleton(canvas: Canvas, points: List<NormalizedLandmark>) {
        // 상체(어깨~손가락)와 하체(힙~발) 연결
        val connections = listOf(
            // 상체: 어깨 - 팔 - 손
            Pair(11, 12), Pair(12, 14), Pair(14, 16), Pair(11, 13), Pair(13, 15),
            // 하체: 힙 - 다리 - 발
            Pair(11, 23), Pair(12, 24), Pair(23, 24),
            Pair(24, 26), Pair(26, 28), Pair(23, 25), Pair(25, 27)
        )

        // 각 연결선 그리기
        for (conn in connections) {
            if (conn.first < points.size && conn.second < points.size) {
                val start = points[conn.first]
                val end = points[conn.second]

                canvas.drawLine(
                    getCanvasX(start.x()), getCanvasY(start.y()),
                    getCanvasX(end.x()), getCanvasY(end.y()),
                    linePaint
                )
            }
        }
    }
}