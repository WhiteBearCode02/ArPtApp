package com.example.arptapp.ui.gym

/** OS 제공자 선택을 Android 없이 검증한다. coarse 전용이면 fine이 필요한 GPS를 요청하지 않는다. */
object NearbyGymProviderPolicy {
    fun select(fine: Boolean, coarse: Boolean, enabled: List<String>): List<String> {
        if (!NearbyGymPolicy.hasPermission(fine, coarse)) return emptyList()
        return buildList {
            if ("network" in enabled) add("network")
            if (fine && "gps" in enabled) add("gps")
        }
    }
}

/** 좌표 없이 제공자 완료 상태만 관리한다. 중복·취소 후 콜백과 두 제공자의 동시 성공을 무시한다. */
class NearbyGymLocationCompletion(private val providers: Set<String>) {
    enum class Result { READY, PERMISSION_REQUIRED, UNAVAILABLE }
    init { require(providers.isNotEmpty()) }
    private val completed = mutableSetOf<String>()
    private var permissionDenied = false
    private var finished = false

    @Synchronized fun isFinished(): Boolean = finished
    @Synchronized fun cancel() { finished = true }
    @Synchronized fun complete(provider: String, usable: Boolean, denied: Boolean = false): Result? {
        if (finished || provider !in providers || !completed.add(provider)) return null
        permissionDenied = permissionDenied || denied
        if (usable) {
            finished = true
            return Result.READY
        }
        if (completed.size == providers.size) {
            finished = true
            return if (permissionDenied) Result.PERMISSION_REQUIRED else Result.UNAVAILABLE
        }
        return null
    }
}
