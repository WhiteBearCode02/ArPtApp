package com.example.arptapp.domain.report

/** Bounded: at most one summary per (type, side, phase), not one object per error frame. */
object FormEventAggregator {
    fun collect(samples: List<RepFrameFeatures>, phases: List<MovementPhase?>, type: FormErrorType,
                side: BodySide? = null, severity: (RepFrameFeatures) -> Int): List<FormErrorEvent> = buildList {
        MovementPhase.entries.forEach { phase ->
            var runStart = -1; var peak = 0; var accepted = 0; var firstValid = -1; var eventPeak = 0
            fun flush(end: Int) {
                if (runStart >= 0 && end - runStart >= EngineeringFormPolicy.MIN_EVENT_FRAMES) {
                    if (firstValid < 0) firstValid = runStart
                    accepted += end - runStart; eventPeak = maxOf(eventPeak, peak)
                }
                runStart = -1; peak = 0
            }
            samples.indices.forEach { i ->
                val level = if (phases[i] == phase) severity(samples[i]) else 0
                if (level > 0) { if (runStart < 0) runStart = i; peak = maxOf(peak, level) }
                else flush(i)
            }
            flush(samples.size)
            if (accepted > 0) add(FormErrorEvent(type, phase, side, eventPeak,
                samples[firstValid].timestampMs - samples.first().timestampMs, accepted))
        }
    }
}
