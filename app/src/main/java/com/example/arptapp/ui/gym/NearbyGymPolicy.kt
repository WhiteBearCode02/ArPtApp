package com.example.arptapp.ui.gym

/** Pure decisions shared by the foreground controller and its JVM tests. */
object NearbyGymPolicy {
    const val LOCATION_TIMEOUT_MILLIS = 15_000L
    const val MAX_LOCATION_AGE_NANOS = 120_000_000_000L

    fun hasPermission(fine: Boolean, coarse: Boolean): Boolean = fine || coarse

    fun isUsableLocation(
        latitude: Double,
        longitude: Double,
        fixElapsedNanos: Long,
        nowElapsedNanos: Long
    ): Boolean = latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0 &&
        fixElapsedNanos > 0 && nowElapsedNanos >= fixElapsedNanos &&
        nowElapsedNanos - fixElapsedNanos <= MAX_LOCATION_AGE_NANOS
}

/** No location or durable state. Re-entering Home never starts a search. */
class NearbyGymRequestGate {
    enum class Stage { IDLE, WAITING_PERMISSION, LOCATING }

    var stage: Stage = Stage.IDLE
        private set

    fun begin(hasPermission: Boolean): Boolean {
        if (stage != Stage.IDLE) return false
        stage = if (hasPermission) Stage.LOCATING else Stage.WAITING_PERMISSION
        return true
    }

    fun onPermissionResult(granted: Boolean): Boolean {
        // Ignore a restored permission callback after activity recreation. The button remains usable.
        if (stage != Stage.WAITING_PERMISSION) return false
        stage = if (granted) Stage.LOCATING else Stage.IDLE
        return granted
    }

    fun finish() { stage = Stage.IDLE }

    fun cancelLocation() {
        if (stage == Stage.LOCATING) finish()
    }
}
