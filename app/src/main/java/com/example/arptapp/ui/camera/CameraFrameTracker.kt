package com.example.arptapp.ui.camera

/** 제출 시점의 렌즈/세대. 전환 후 늦게 도착하는 추론 결과를 새 렌즈 좌표로 표시하지 않는다. */
data class CameraFrameToken(val generation: Long, val isFrontFacing: Boolean)

/**
 * 비동기 추론의 제출/결과 대응 API. MediaPipe는 프레임을 건너뛸 수 있어 대기 항목을 32개로 제한한다.
 * 영상이나 관절 좌표 없이 타임스탬프/렌즈 정보만 보관하고 카메라 전환/종료 시 모두 폐기한다.
 */
class CameraFrameTracker(private val capacity: Int = 32) {
    init { require(capacity > 0) }
    private var generation = 0L
    private val pending = linkedMapOf<Long, CameraFrameToken>()

    @Synchronized fun currentGeneration(): Long = generation
    @Synchronized fun isCurrent(value: Long): Boolean = value == generation
    @Synchronized fun advance() {
        generation++
        pending.clear()
    }
    @Synchronized fun register(timestamp: Long, expectedGeneration: Long, front: Boolean): Boolean {
        if (expectedGeneration != generation) return false
        pending[timestamp] = CameraFrameToken(generation, front)
        while (pending.size > capacity) pending.remove(pending.keys.first())
        return true
    }
    @Synchronized fun consume(timestamp: Long): CameraFrameToken? = pending.remove(timestamp)
}
