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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One foreground request, cancelled on timeout/stop. Coordinates never leave this helper. */
class NearbyGymLocationHelper(context: Context) {
    enum class Outcome { READY, PERMISSION_REQUIRED, DISABLED, TIMED_OUT, UNAVAILABLE }

    private val appContext = context.applicationContext
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(appContext) }

    fun hasPermission(): Boolean = NearbyGymPolicy.hasPermission(
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION),
        isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    )

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Check both permissions here, and handle revocation during the request.
    suspend fun confirmCurrentLocation(): Outcome {
        if (!hasPermission()) return Outcome.PERMISSION_REQUIRED
        return try {
            val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return Outcome.UNAVAILABLE
            if (!LocationManagerCompat.isLocationEnabled(manager)) return Outcome.DISABLED
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) !=
                ConnectionResult.SUCCESS
            ) return Outcome.UNAVAILABLE

            withTimeoutOrNull(NearbyGymPolicy.LOCATION_TIMEOUT_MILLIS) {
                if (fusedClient.lastLocation.awaitResult()?.isUsable() == true) {
                    Outcome.READY
                } else {
                    val cancellation = CancellationTokenSource()
                    try {
                        val current = fusedClient.getCurrentLocation(
                            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                            cancellation.token
                        ).awaitResult()
                        if (current?.isUsable() == true) Outcome.READY else Outcome.UNAVAILABLE
                    } finally {
                        cancellation.cancel()
                    }
                }
            } ?: Outcome.TIMED_OUT
        } catch (_: SecurityException) {
            Outcome.PERMISSION_REQUIRED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Never expose service errors or coordinates through UI/logs.
            Outcome.UNAVAILABLE
        }
    }

    private fun Location.isUsable(): Boolean = NearbyGymPolicy.isUsableLocation(
        latitude, longitude, elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos()
    )

    // Avoid adding kotlinx-coroutines-play-services for two one-shot Tasks.
    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        addOnCanceledListener {
            if (continuation.isActive) continuation.resumeWithException(
                IllegalStateException("Location provider cancelled its task")
            )
        }
    }
}
