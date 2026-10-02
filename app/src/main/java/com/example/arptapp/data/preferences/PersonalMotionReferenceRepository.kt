package com.example.arptapp.data.preferences

import android.content.Context
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.analyzer.PersonalMotionReference
import com.google.gson.Gson

/** Private, account-scoped on-device storage. Raw camera images are never retained. */
class PersonalMotionReferenceRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("personal_motion_references", Context.MODE_PRIVATE)
    private val gson = Gson()
    fun load(userId: String, exercise: String, view: CameraView): PersonalMotionReference? {
        if (userId.isBlank()) return null
        return runCatching {
            gson.fromJson(preferences.getString(key(userId, exercise, view), null), PersonalMotionReference::class.java)
                ?.takeIf { it.isValid() && it.exerciseType == exercise && it.cameraView == view }
        }.getOrNull()
    }
    fun save(userId: String, reference: PersonalMotionReference) {
        require(userId.isNotBlank() && reference.isValid())
        preferences.edit().putString(key(userId, reference.exerciseType, reference.cameraView), gson.toJson(reference)).apply()
    }
    fun remove(userId: String, exercise: String, view: CameraView) {
        if (userId.isNotBlank()) preferences.edit().remove(key(userId, exercise, view)).apply()
    }
    private fun key(userId: String, exercise: String, view: CameraView) = "v1_${userId}_${exercise}_${view.name}"
}
