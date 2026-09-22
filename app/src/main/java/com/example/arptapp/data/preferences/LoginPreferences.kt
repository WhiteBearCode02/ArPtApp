package com.example.arptapp.data.preferences

import android.content.Context

/** Stores only the user's session-retention choice, never credentials or tokens. */
class LoginPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("login_preferences", Context.MODE_PRIVATE)

    var rememberLogin: Boolean
        get() = preferences.getBoolean("remember_login", false)
        set(value) { preferences.edit().putBoolean("remember_login", value).apply() }

    var pendingGoogleRememberLogin: Boolean?
        get() = if (preferences.contains("pending_google_remember_login")) {
            preferences.getBoolean("pending_google_remember_login", false)
        } else null
        set(value) {
            preferences.edit().apply {
                if (value == null) remove("pending_google_remember_login")
                else putBoolean("pending_google_remember_login", value)
            }.apply()
        }
}
