package com.example.arptapp.ui.gym

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Android 기본 위치 API의 일회성 foreground 어댑터. UI에는 좌표가 아닌 성공/실패만 반환한다.
 * 기존 2분 캐시/15초 제한을 유지하며 화면 정지·성공·실패·timeout에 모든 요청을 해제한다.
 * API 26~29 호환은 기존 AndroidX Core가 담당한다. Google Play services를 사용하지 않는다.
 */
class NearbyGymLocationHelper(context: Context) {
    enum class Outcome { READY, PERMISSION_REQUIRED, DISABLED, TIMED_OUT, UNAVAILABLE }

    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)

    fun hasPermission(): Boolean = NearbyGymPolicy.hasPermission(
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION),
        isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    )

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // 명시적 검사와 요청 중 권한 철회에 대한 SecurityException 처리를 함께 수행한다.
    suspend fun confirmCurrentLocation(): Outcome {
        if (!hasPermission()) return Outcome.PERMISSION_REQUIRED
        return try {
            val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return Outcome.UNAVAILABLE
            if (!LocationManagerCompat.isLocationEnabled(manager)) return Outcome.DISABLED
            val providers = NearbyGymProviderPolicy.select(
                isGranted(Manifest.permission.ACCESS_FINE_LOCATION),
                isGranted(Manifest.permission.ACCESS_COARSE_LOCATION),
                manager.getProviders(true)
            )
            if (providers.isEmpty()) return Outcome.UNAVAILABLE

            withTimeoutOrNull(NearbyGymPolicy.LOCATION_TIMEOUT_MILLIS) {
                val cached = providers.any { provider ->
                    try {
                        manager.getLastKnownLocation(provider)?.isUsable() == true
                    } catch (_: IllegalArgumentException) {
                        false // 조회 도중 제공자가 제거돼도 다른 제공자를 확인한다.
                    }
                }
                if (cached) Outcome.READY else awaitCurrentLocation(manager, providers)
            } ?: Outcome.TIMED_OUT
        } catch (_: SecurityException) {
            Outcome.PERMISSION_REQUIRED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 위치/OS 오류 원문을 로그·UI로 노출하지 않는다.
            Outcome.UNAVAILABLE
        }
    }

    private fun Location.isUsable(): Boolean = NearbyGymPolicy.isUsableLocation(
        latitude, longitude, elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos()
    )

    @SuppressLint("MissingPermission")
    private suspend fun awaitCurrentLocation(manager: LocationManager, providers: List<String>): Outcome {
        // 신호 목록을 먼저 확정하므로 등록 중 취소돼도 뒤늦게 시작할 요청까지 취소된 상태다.
        val requests = providers.associateWith { CancellationSignal() }
        val completion = NearbyGymLocationCompletion(providers.toSet())
        fun cancelRequests() {
            requests.values.forEach { signal ->
                try { signal.cancel() } catch (_: Exception) { /* 권한 철회/OS 해제 오류로 화면을 종료하지 않는다. */ }
            }
        }
        return try {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation {
                    completion.cancel()
                    cancelRequests()
                }
                fun complete(provider: String, usable: Boolean, denied: Boolean = false) {
                    val result = completion.complete(provider, usable, denied) ?: return
                    val outcome = when (result) {
                        NearbyGymLocationCompletion.Result.READY -> Outcome.READY
                        NearbyGymLocationCompletion.Result.PERMISSION_REQUIRED -> Outcome.PERMISSION_REQUIRED
                        NearbyGymLocationCompletion.Result.UNAVAILABLE -> Outcome.UNAVAILABLE
                    }
                    if (continuation.isActive) continuation.resume(outcome)
                    cancelRequests()
                }
                requests.forEach { (provider, signal) ->
                    if (continuation.isActive && !completion.isFinished()) {
                        try {
                            // GPS가 느려도 network 결과를 기다릴 수 있게 병렬 요청하되 최초 유효 결과만 사용한다.
                            LocationManagerCompat.getCurrentLocation(manager, provider, signal, mainExecutor) { fix ->
                                complete(provider, fix?.isUsable() == true)
                            }
                        } catch (_: SecurityException) {
                            complete(provider, usable = false, denied = true)
                        } catch (_: Exception) {
                            complete(provider, usable = false)
                        }
                    }
                }
            }
        } finally {
            completion.cancel()
            cancelRequests()
        }
    }
}
