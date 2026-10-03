package com.example.arptapp.ui.gym

import android.Manifest
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.R
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Wires Home's small card without adding network, storage, or location work to onCreate. */
class NearbyGymController(
    private val activity: AppCompatActivity,
    private val button: MaterialButton,
    private val progress: View,
    private val status: TextView
) : DefaultLifecycleObserver {
    private val gate = NearbyGymRequestGate()
    private val locationHelper = NearbyGymLocationHelper(activity)
    private val navigator = NearbyGymNavigator(activity)
    private var locationJob: Job? = null
    private var requestGeneration = 0
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val wasWaiting = gate.stage == NearbyGymRequestGate.Stage.WAITING_PERMISSION
        if (gate.onPermissionResult(locationHelper.hasPermission())) {
            confirmAndNavigate()
        } else if (wasWaiting) {
            render()
            showMessage(R.string.nearby_gym_permission)
        }
    }

    init {
        activity.lifecycle.addObserver(this)
        button.setOnClickListener {
            if (!canUpdateUi()) return@setOnClickListener
            if (!gate.begin(locationHelper.hasPermission())) return@setOnClickListener
            render()
            if (gate.stage == NearbyGymRequestGate.Stage.WAITING_PERMISSION) {
                permissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            } else {
                confirmAndNavigate()
            }
        }
    }

    private fun confirmAndNavigate() {
        val generation = ++requestGeneration
        render()
        locationJob = activity.lifecycleScope.launch(start = CoroutineStart.LAZY) {
            try {
                val outcome = locationHelper.confirmCurrentLocation()
                if (!canUpdateUi()) return@launch
                when (outcome) {
                    NearbyGymLocationHelper.Outcome.READY -> {
                        val result = navigator.openSearch()
                        showMessage(if (result == NearbyGymNavigation.Result.UNAVAILABLE)
                            R.string.nearby_gym_map_unavailable else R.string.nearby_gym_map_hint)
                    }
                    NearbyGymLocationHelper.Outcome.PERMISSION_REQUIRED -> showMessage(R.string.nearby_gym_permission)
                    NearbyGymLocationHelper.Outcome.DISABLED -> showMessage(R.string.nearby_gym_location_disabled)
                    NearbyGymLocationHelper.Outcome.TIMED_OUT -> showMessage(R.string.nearby_gym_location_timeout)
                    NearbyGymLocationHelper.Outcome.UNAVAILABLE -> showMessage(R.string.nearby_gym_location_unavailable)
                }
            } finally {
                // A cancelled old request must not reset a newer request after Home resumes.
                if (generation == requestGeneration) {
                    gate.finish()
                    if (canUpdateUi()) render()
                }
            }
        }.also { it.start() }
    }

    private fun canUpdateUi(): Boolean = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun render() {
        val locating = gate.stage == NearbyGymRequestGate.Stage.LOCATING
        button.isEnabled = gate.stage == NearbyGymRequestGate.Stage.IDLE
        button.setText(if (locating) R.string.nearby_gym_loading else R.string.nearby_gym_find)
        progress.visibility = if (locating) View.VISIBLE else View.GONE
    }

    private fun showMessage(message: Int) {
        if (!canUpdateUi()) return
        status.setText(message)
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    override fun onStart(owner: LifecycleOwner) { render() }

    override fun onStop(owner: LifecycleOwner) {
        requestGeneration++
        locationJob?.cancel()
        locationJob = null
        gate.cancelLocation()
    }
}
