package com.example.arptapp.ui.gym

import java.net.URLEncoder

/** Official /search supports query + appname, NOT coordinates. Never invent lat/lng options. */
object NearbyGymNavigation {
    const val NAVER_MAP_PACKAGE = "com.nhn.android.nmap"
    private const val QUERY = "헬스장"

    data class Target(val url: String, val packageName: String? = null)
    enum class Result { APP, WEB, UNAVAILABLE }

    fun targets(applicationId: String): List<Target> {
        val query = encode(QUERY)
        return listOf(
            Target("nmap://search?query=$query&appname=${encode(applicationId)}", NAVER_MAP_PACKAGE),
            Target("https://map.naver.com/p/search/$query")
        )
    }

    fun open(applicationId: String, launch: (Target) -> Boolean): Result {
        val targets = targets(applicationId)
        return when {
            launch(targets[0]) -> Result.APP
            launch(targets[1]) -> Result.WEB
            else -> Result.UNAVAILABLE
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
