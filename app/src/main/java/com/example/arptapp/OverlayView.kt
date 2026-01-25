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
 * 현준 창업자님의 커스텀 DashboardActivity와 완벽히 호환되는 정밀 OverlayView입니다.
 * 좌표계 회전 불일치 문제를 수학적 오프셋 보정으로 해결합니다.
 */
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
        // 포인트 스타일 설정
        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = 12f
        pointPaint.style = Paint.Style.FILL

        // 스켈레톤 라인 스타일 설정
        linePaint.color = Color.CYAN
        linePaint.strokeWidth = 8f
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeCap = Paint.Cap.ROUND // 선의 끝을 둥글게 처리하여 가독성 향상
    }

    /**
     * 분석 결과와 해상도를 전달받아 캔버스를 갱신합니다.
     * [추론] 전달받은 width/height가 실제 뷰의 방향과 일치하도록 내부에서 검증 로직을 거칩니다.
     */
    fun setResults(
        poseLandmarkerResult: PoseLandmarkerResult,
        height: Int,
        width: Int,
        runningMode: RunningMode
    ) {
        results = poseLandmarkerResult

        // [공학적 보정] MediaPipe의 출력 좌표계와 뷰의 방향성을 동기화합니다.
        // DashboardActivity에서 회전된 값을 주더라도 여기서 한 번 더 안전장치를 둡니다.
        if (this.width < this.height && width > height) {
            this.imageWidth = height
            this.imageHeight = width
        } else {
            this.imageWidth = width
            this.imageHeight = height
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        results?.let { poseLandmarkerResult ->
            // [수학적 모델링] PreviewView의 Center-Crop 방식에 따른 배율 계산
            // 화면을 가득 채우기 위해 더 큰 배율을 선택합니다.
            scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)

            // 확대 시 화면 중앙을 유지하기 위한 오프셋(여백) 계산
            offsetX = (width - imageWidth * scaleFactor) / 2f
            offsetY = (height - imageHeight * scaleFactor) / 2f

            for (landmark in poseLandmarkerResult.landmarks()) {
                // 1. 모든 관절 포인트(Landmark) 그리기
                for (point in landmark) {
                    // [핵심] 전면 카메라 거울 반전(1 - x) + 스케일링 + 중앙 정렬 오프셋
                    // 사람이 가로로 있든 세로로 있든, MediaPipe가 회전 보정된 좌표를 주므로
                    // 이 공식이 일관되게 적용되어야 합니다.
                    val canvasX = (1 - point.x()) * imageWidth * scaleFactor + offsetX
                    val canvasY = point.y() * imageHeight * scaleFactor + offsetY

                    canvas.drawCircle(canvasX, canvasY, 10f, pointPaint)
                }

                // 2. 관절 사이의 연결선(Skeleton) 그리기
                drawSkeleton(canvas, landmark)
            }
        }
    }

    private fun drawSkeleton(
        canvas: Canvas,
        landmark: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>
    ) {
        // [주의] 인덱스 번호는 MediaPipe Pose Landmarker 표준 가이드를 준수합니다.
        drawLine(canvas, landmark[11], landmark[12]) // 어깨
        drawLine(canvas, landmark[12], landmark[14]) // 우측 팔
        drawLine(canvas, landmark[14], landmark[16])
        drawLine(canvas, landmark[11], landmark[13]) // 좌측 팔
        drawLine(canvas, landmark[13], landmark[15])
        drawLine(canvas, landmark[11], landmark[23]) // 몸통 상단-하단
        drawLine(canvas, landmark[12], landmark[24])
        drawLine(canvas, landmark[23], landmark[24]) // 골반 라인
        drawLine(canvas, landmark[24], landmark[26]) // 우측 다리
        drawLine(canvas, landmark[26], landmark[28])
        drawLine(canvas, landmark[23], landmark[25]) // 좌측 다리
        drawLine(canvas, landmark[25], landmark[27])
    }

    private fun drawLine(
        canvas: Canvas,
        start: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
        end: com.google.mediapipe.tasks.components.containers.NormalizedLandmark
    ) {
        // [수정] 모든 선 그리기 로직에 포인트와 동일한 좌표 변환 공식을 적용합니다.
        val startX = (1 - start.x()) * imageWidth * scaleFactor + offsetX
        val startY = start.y() * imageHeight * scaleFactor + offsetY
        val endX = (1 - end.x()) * imageWidth * scaleFactor + offsetX
        val endY = end.y() * imageHeight * scaleFactor + offsetY

        canvas.drawLine(startX, startY, endX, endY, linePaint)
    }
}