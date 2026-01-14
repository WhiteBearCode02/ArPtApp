package com.example.arptapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * AI가 분석한 관절 좌표를 화면에 실시간으로 그리는 커스텀 뷰입니다.
 * - CameraX의 분석 해상도와 실제 View의 해상도를 매핑하여 좌표를 보정합니다.
 */
class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private val pointPaint = Paint()
    private val linePaint = Paint()

    // 카메라 원본 해상도 저장용 변수
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        // 관절 포인트를 그릴 붓 설정
        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = 12f
        pointPaint.style = Paint.Style.FILL

        // 관절 사이의 연결선을 그릴 붓 설정
        linePaint.color = Color.CYAN
        linePaint.strokeWidth = 8f
    }

    /**
     * CameraActivity로부터 분석 결과와 해상도 정보를 전달받습니다.
     * [수정] CameraActivity의 호출 규격(4개 인자)에 맞게 파라미터를 확장했습니다.
     */
    fun setResults(
        poseLandmarkerResult: PoseLandmarkerResult,
        height: Int,
        width: Int,
        runningMode: RunningMode
    ) {
        results = poseLandmarkerResult
        this.imageHeight = height
        this.imageWidth = width

        // UI 스레드에서 화면을 다시 그리도록 요청
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        results?.let { poseLandmarkerResult ->
            // 화면 크기 대비 카메라 영상 크기의 비율 계산 (Scaling Factor)
            val scaleX = width.toFloat() / imageWidth
            val scaleY = height.toFloat() / imageHeight

            for (landmark in poseLandmarkerResult.landmarks()) {
                // 1. 관절 포인트 그리기
                for (point in landmark) {
                    val x = point.x() * imageWidth * scaleX
                    val y = point.y() * imageHeight * scaleY
                    canvas.drawCircle(x, y, 8f, pointPaint)
                }

                // 2. 주요 관절 연결선(Skeleton) 그리기
                drawSkeleton(canvas, landmark, scaleX, scaleY)
            }
        }
    }

    /**
     * 특정 관절들을 연결하여 뼈대를 형성합니다.
     * [수정] 보정된 scaleX, scaleY 값을 적용하여 선이 정확한 위치에 그려지게 합니다.
     */
    private fun drawSkeleton(
        canvas: Canvas,
        landmark: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        scaleX: Float,
        scaleY: Float
    ) {
        // 어깨 연결 (11 - 12)
        drawLine(canvas, landmark[11], landmark[12], scaleX, scaleY)
        // 오른쪽 팔 (12 - 14 - 16)
        drawLine(canvas, landmark[12], landmark[14], scaleX, scaleY)
        drawLine(canvas, landmark[14], landmark[16], scaleX, scaleY)
        // 왼쪽 팔 (11 - 13 - 15)
        drawLine(canvas, landmark[11], landmark[13], scaleX, scaleY)
        drawLine(canvas, landmark[13], landmark[15], scaleX, scaleY)
        // 몸통 연결 (11 - 23, 12 - 24, 23 - 24)
        drawLine(canvas, landmark[11], landmark[23], scaleX, scaleY)
        drawLine(canvas, landmark[12], landmark[24], scaleX, scaleY)
        drawLine(canvas, landmark[23], landmark[24], scaleX, scaleY)
        // 오른쪽 다리 (24 - 26 - 28)
        drawLine(canvas, landmark[24], landmark[26], scaleX, scaleY)
        drawLine(canvas, landmark[26], landmark[28], scaleX, scaleY)
        // 왼쪽 다리 (23 - 25 - 27)
        drawLine(canvas, landmark[23], landmark[25], scaleX, scaleY)
        drawLine(canvas, landmark[25], landmark[27], scaleX, scaleY)
    }

    private fun drawLine(
        canvas: Canvas,
        start: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
        end: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
        scaleX: Float,
        scaleY: Float
    ) {
        canvas.drawLine(
            start.x() * imageWidth * scaleX, start.y() * imageHeight * scaleY,
            end.x() * imageWidth * scaleX, end.y() * imageHeight * scaleY,
            linePaint
        )
    }
}