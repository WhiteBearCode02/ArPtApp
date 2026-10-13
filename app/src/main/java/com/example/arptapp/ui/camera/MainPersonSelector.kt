package com.example.arptapp.ui.camera

/**
 * 화면에서 가장 큰 바운딩 박스를 가진 사람을 선택하는 카메라 표시 정책.
 *
 * 좌표 접근 함수를 받으므로 MediaPipe/Android 없이 선택 정책을 검증할 수 있다.
 * 면적은 '가장 가까운 사용자'의 휴리스틱일 뿐 신원 인식이나 거울 제거를 보장하지 않는다.
 * 동일 면적이면 기존 결과 순서의 첫 사람을 유지한다.
 */
object MainPersonSelector {
    fun <T> select(
        candidates: List<List<T>>,
        x: (T) -> Float,
        y: (T) -> Float
    ): List<T>? {
        var selected: List<T>? = null
        var largestArea = -1f
        for (candidate in candidates) {
            val area = boundingBoxArea(candidate, x, y) ?: continue
            if (area > largestArea) {
                largestArea = area
                selected = candidate
            }
        }
        return selected
    }

    /** 비어 있거나 유효 좌표가 없는 결과는 측정 대기 상태로 처리한다. */
    private fun <T> boundingBoxArea(points: List<T>, x: (T) -> Float, y: (T) -> Float): Float? {
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (point in points) {
            val pointX = x(point)
            val pointY = y(point)
            if (!pointX.isFinite() || !pointY.isFinite()) continue
            minX = minOf(minX, pointX)
            maxX = maxOf(maxX, pointX)
            minY = minOf(minY, pointY)
            maxY = maxOf(maxY, pointY)
        }
        if (!minX.isFinite()) return null
        return ((maxX - minX) * (maxY - minY)).takeIf { it.isFinite() }
    }
}
