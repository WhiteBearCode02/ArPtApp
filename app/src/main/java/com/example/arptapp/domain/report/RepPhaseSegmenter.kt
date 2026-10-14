package com.example.arptapp.domain.report

/** Exercise-specific observed direction, independent of counter completion policy. */
data class RepPhaseSegmentation(val phases: List<MovementPhase?>, val durations: PhaseDurations)

object RepPhaseSegmenter {
    private const val TURN_BAND_DEG = 5.0
    private const val MIN_TRAVEL_DEG = 10.0
    fun segment(samples: List<RepFrameFeatures>, partial: Boolean): RepPhaseSegmentation =
        if (samples.first().exerciseType == "SHOULDER_PRESS") press(samples, partial) else squat(samples, partial)

    private fun squat(s: List<RepFrameFeatures>, partial: Boolean): RepPhaseSegmentation {
        val minimumIndex = s.indices.minBy { s[it].representativeAngle!! }
        val minimum = s[minimumIndex].representativeAngle!!
        // Use only the contiguous turning interval; two separate visits cannot invent one long bottom.
        var first = minimumIndex; var last = minimumIndex
        while (first > 0 && s[first - 1].representativeAngle!! <= minimum + TURN_BAND_DEG) first--
        while (last < s.lastIndex && s[last + 1].representativeAngle!! <= minimum + TURN_BAND_DEG) last++
        val descent = !partial && first > 0 && s.first().representativeAngle!! - minimum >= MIN_TRAVEL_DEG
        val ascent = last < s.lastIndex && s.last().representativeAngle!! - minimum >= MIN_TRAVEL_DEG
        return RepPhaseSegmentation(s.indices.map { i -> when {
            i < first -> if (descent) MovementPhase.DESCENT else null
            i <= last -> MovementPhase.BOTTOM
            else -> if (ascent) MovementPhase.ASCENT else null
        } }, PhaseDurations(
            if (descent) s[first].timestampMs - s.first().timestampMs else null,
            if (last > first) s[last].timestampMs - s[first].timestampMs else null,
            if (ascent) s.last().timestampMs - s[last].timestampMs else null))
    }

    private fun press(s: List<RepFrameFeatures>, partial: Boolean): RepPhaseSegmentation {
        var bottom = 0
        for (i in 1..s.lastIndex) {
            if (s[i].representativeAngle!! < s[bottom].representativeAngle!!) bottom = i
            if (s[i].representativeAngle!! - s[bottom].representativeAngle!! >= MIN_TRAVEL_DEG) break
        }
        val minimum = s[bottom].representativeAngle!!
        var bottomStart = bottom; var bottomEnd = bottom
        while (bottomStart > 0 && s[bottomStart - 1].representativeAngle!! <= minimum + TURN_BAND_DEG) bottomStart--
        while (bottomEnd < s.lastIndex && s[bottomEnd + 1].representativeAngle!! <= minimum + TURN_BAND_DEG) bottomEnd++
        var top = bottomEnd
        for (i in bottomEnd..s.lastIndex) {
            if (s[i].representativeAngle!! > s[top].representativeAngle!!) top = i
            if (s[top].representativeAngle!! - s[i].representativeAngle!! >= MIN_TRAVEL_DEG) break
        }
        val maximum = s[top].representativeAngle!!
        var topStart = top; var topEnd = top
        while (topStart > bottomEnd && s[topStart - 1].representativeAngle!! >= maximum - TURN_BAND_DEG) topStart--
        while (topEnd < s.lastIndex && s[topEnd + 1].representativeAngle!! >= maximum - TURN_BAND_DEG) topEnd++
        val ascent = topStart > bottomEnd && maximum - minimum >= MIN_TRAVEL_DEG
        val descent = topEnd < s.lastIndex && maximum - s.last().representativeAngle!! >= MIN_TRAVEL_DEG
        // A failed lift followed by a second lift is not one monotonic observed phase.
        // Keep the counter result, but abstain from attributing errors to guessed phases.
        if (topEnd < s.lastIndex && s.drop(topEnd + 1).any { it.representativeAngle!! <= maximum - MIN_TRAVEL_DEG } &&
            s.last().representativeAngle!! >= maximum - TURN_BAND_DEG) {
            return RepPhaseSegmentation(List(s.size) { null }, PhaseDurations())
        }
        // The real counter completes at TOP, so live descent is normally null. A supplied full-cycle
        // fixture can contain the return; pre-bottom movement belongs to the previous rep, not this one.
        return RepPhaseSegmentation(s.indices.map { i -> when {
            i < bottomStart -> null
            i <= bottomEnd -> MovementPhase.BOTTOM
            i <= topStart -> if (ascent) MovementPhase.ASCENT else null
            i <= topEnd -> null // top hold has no compatible stored phase; do not call it BOTTOM
            else -> if (descent) MovementPhase.DESCENT else null
        } }, PhaseDurations(
            if (descent) s.last().timestampMs - s[topEnd].timestampMs else null,
            if (!partial && bottomEnd > bottomStart) s[bottomEnd].timestampMs - s[bottomStart].timestampMs else null,
            if (ascent) s[topStart].timestampMs - s[bottomEnd].timestampMs else null))
    }
}
