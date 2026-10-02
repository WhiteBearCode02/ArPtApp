package com.example.arptapp.domain.analyzer

data class FramePerformance(val processedFps: Double, val averageLatencyMs: Double, val samples: Int)

/** Bounded in-memory diagnostics; no analytics endpoint or camera/user data. */
class FramePerformanceMonitor {
    private val frames = ArrayDeque<Pair<Long, Long>>()
    fun record(frameTimestampMs: Long, completedAtMs: Long): FramePerformance {
        if (completedAtMs >= frameTimestampMs) frames.addLast(completedAtMs to (completedAtMs - frameTimestampMs))
        while (frames.size > 60) frames.removeFirst()
        val span = if (frames.size > 1) frames.last().first - frames.first().first else 0L
        return FramePerformance(if (span > 0) (frames.size - 1) * 1000.0 / span else 0.0,
            if (frames.isEmpty()) 0.0 else frames.map { it.second }.average(), frames.size)
    }
}
