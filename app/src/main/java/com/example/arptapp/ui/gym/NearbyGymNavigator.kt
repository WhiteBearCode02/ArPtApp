package com.example.arptapp.ui.gym

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

class NearbyGymNavigator(private val context: Context) {
    fun openSearch(): NearbyGymNavigation.Result = NearbyGymNavigation.open(context.packageName) { target ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                target.packageName?.let { setPackage(it) }
            })
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
