package com.example.arptapp.presentation.report

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.arptapp.data.remote.AuthSessionStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/** Protect already-open result/detail screens as well as the history list after account switching. */
object WorkoutReportAccess {
    fun bind(activity: AppCompatActivity, root: View, ownerId: String): Boolean {
        // Legacy navigation has no owner extra; current Room/new-workout paths always supply it.
        fun allowed() = ownerId.isBlank() || ownerId == AuthSessionStore.workoutAccount.value?.userId
        fun hideAndClose() { root.visibility = View.GONE; activity.finish() }
        if (!allowed()) { hideAndClose(); return false }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AuthSessionStore.workoutAccount.collect { account ->
                    if (ownerId.isNotBlank() && ownerId != account?.userId) hideAndClose()
                }
            }
        }
        return true
    }
}
