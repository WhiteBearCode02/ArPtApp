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

    // [추가] 화면 크기에 맞게 좌표를 늘리거나 줄이는 배율 변수
    private var scaleFactor: Float = 1f

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
     */
    fun setResults(
        poseLandmarkerResult: PoseLandmarkerResult,
        height: Int,
        width: Int,
        runningMode: RunningMode
    ) {
        results = poseLandmarkerResult
        // [핵심] 세로 모드에서는 이미지의 높이와 너비가 화면상에서 반전되어 인지되어야 할 때가 있습니다.
        this.imageHeight = height
        this.imageWidth = width

        // UI 스레드에서 화면을 다시 그리도록 요청
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        results?.let { poseLandmarkerResult ->
            // [추가] 화면 크기와 원본 이미지 크기 비율을 계산하여 스케일을 조정합니다.
            // 뼈대가 화면 밖으로 삐져나가지 않고 몸 위에 정확히 위치하게 만듭니다.
            scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)

            for (landmark in poseLandmarkerResult.landmarks()) {
                // 1. 관절 포인트 그리기
                for (point in landmark) {
                    // [수정] 전면 카메라 거울 효과를 위해 x 좌표를 반전(1 - point.x()) 시킵니다.
                    // 계산된 scaleFactor를 곱하여 실제 화면 픽셀 위치로 변환합니다.
                    val canvasX = (1 - point.x()) * imageWidth * scaleFactor
                    val canvasY = point.y() * imageHeight * scaleFactor

                    canvas.drawCircle(canvasX, canvasY, 10f, pointPaint)
                }

                // 2. 주요 관절 연결선(Skeleton) 그리기
                // [수정] 스케일 팩터를 전달하여 선의 위치도 보정합니다.
                drawSkeleton(canvas, landmark)
            }
        }
    }

    /**
     * 특정 관절들을 연결하여 뼈대를 형성합니다.
     */
    private fun drawSkeleton(
        canvas: Canvas,
        landmark: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>
    ) {
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

    /**
     * [수정] 단일 선을 그릴 때도 전면 카메라 반전과 스케일을 적용합니다.
     */
    private fun drawLine(
        canvas: Canvas,
        start: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
        end: com.google.mediapipe.tasks.components.containers.NormalizedLandmark
    ) {
        val startX = (1 - start.x()) * imageWidth * scaleFactor
        val startY = start.y() * imageHeight * scaleFactor
        val endX = (1 - end.x()) * imageWidth * scaleFactor
        val endY = end.y() * imageHeight * scaleFactor

        canvas.drawLine(startX, startY, endX, endY, linePaint)
    }
}