package com.example.arptapp.ui.camera

/**
 * CameraX 프레임 중 MediaPipe에 제출할 프레임만 고르는 순수 정책.
 *
 * 기본 66ms 간격(약 15 FPS)은 기존 대시보드 설정이다. 시스템 벽시계가 아닌
 * `SystemClock.uptimeMillis()`를 전달해야 LIVE_STREAM 타임스탬프의 단조 증가가 유지된다.
 * 카메라의 단일 분석 executor에서만 호출하며, 렌즈 전환 때 초기화하지 않는다.
 */
class FrameSubmissionPolicy(private val minimumIntervalMs: Long = 66L) {
    init {
        require(minimumIntervalMs > 0L) { "프레임 간격은 0보다 커야 합니다" }
    }

    private var lastSubmittedAtMs: Long? = null

    fun tryAccept(timestampMs: Long): Boolean {
        if (timestampMs < 0L) return false
        val previous = lastSubmittedAtMs
        if (previous != null && (timestampMs <= previous || timestampMs - previous < minimumIntervalMs)) {
            return false
        }
        lastSubmittedAtMs = timestampMs
        return true
    }
}
