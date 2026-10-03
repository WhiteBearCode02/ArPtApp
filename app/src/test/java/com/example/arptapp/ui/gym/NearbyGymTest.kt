package com.example.arptapp.ui.gym

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class NearbyGymTest {
    private val now = 500_000_000_000L

    @Test fun fineOrApproximatePermissionIsEnough() {
        assertTrue(NearbyGymPolicy.hasPermission(true, false))
        assertTrue(NearbyGymPolicy.hasPermission(false, true))
        assertTrue(NearbyGymPolicy.hasPermission(true, true))
        assertFalse(NearbyGymPolicy.hasPermission(false, false))
    }

    @Test fun homeEntryDoesNotRequestAnything() {
        assertEquals(NearbyGymRequestGate.Stage.IDLE, NearbyGymRequestGate().stage)
    }

    @Test fun missingPermissionWaitsForExplicitResult() {
        val gate = NearbyGymRequestGate()
        assertTrue(gate.begin(false))
        assertEquals(NearbyGymRequestGate.Stage.WAITING_PERMISSION, gate.stage)
        assertTrue(gate.onPermissionResult(true))
        assertEquals(NearbyGymRequestGate.Stage.LOCATING, gate.stage)
    }

    @Test fun deniedPermissionReturnsToUsableIdle() {
        val gate = NearbyGymRequestGate()
        gate.begin(false)
        assertFalse(gate.onPermissionResult(false))
        assertEquals(NearbyGymRequestGate.Stage.IDLE, gate.stage)
        assertTrue(gate.begin(false))
    }

    @Test fun duplicateClicksDoNotStartAnotherRequest() {
        val gate = NearbyGymRequestGate()
        assertTrue(gate.begin(true))
        assertFalse(gate.begin(true))
        assertFalse(gate.begin(false))
        gate.finish()
        assertTrue(gate.begin(true))
    }

    @Test fun waitingPermissionAlsoBlocksDuplicateClicks() {
        val gate = NearbyGymRequestGate()
        gate.begin(false)
        assertFalse(gate.begin(false))
        assertFalse(gate.begin(true))
    }

    @Test fun stopCancelsLocationButNotPendingPermissionDialog() {
        val gate = NearbyGymRequestGate()
        gate.begin(true)
        gate.cancelLocation()
        assertEquals(NearbyGymRequestGate.Stage.IDLE, gate.stage)
        gate.begin(false)
        gate.cancelLocation()
        assertEquals(NearbyGymRequestGate.Stage.WAITING_PERMISSION, gate.stage)
    }

    @Test fun activityRecreationDoesNotAutomaticallyLocate() {
        val recreated = NearbyGymRequestGate()
        assertFalse(recreated.onPermissionResult(true))
        assertEquals(NearbyGymRequestGate.Stage.IDLE, recreated.stage)
        assertTrue(recreated.begin(true))
    }

    @Test fun recentLocationIsUsable() {
        assertTrue(usable(37.5, 127.0, now - 1))
        assertTrue(usable(37.5, 127.0, now - NearbyGymPolicy.MAX_LOCATION_AGE_NANOS))
    }

    @Test fun oldOrInvalidTimestampsMustRequestCurrentLocation() {
        assertFalse(usable(37.5, 127.0, now - NearbyGymPolicy.MAX_LOCATION_AGE_NANOS - 1))
        assertFalse(usable(37.5, 127.0, 0))
        assertFalse(usable(37.5, 127.0, now + 1))
    }

    @Test fun invalidCoordinatesCannotBeUsed() {
        assertFalse(usable(Double.NaN, 127.0, now))
        assertFalse(usable(37.5, Double.POSITIVE_INFINITY, now))
        assertFalse(usable(91.0, 127.0, now))
        assertFalse(usable(37.5, -181.0, now))
    }

    @Test fun nativeLinkOnlyUsesOfficialSearchParameters() {
        val native = NearbyGymNavigation.targets("com.example.arptapp").first()
        val uri = URI(native.url)
        assertEquals("nmap", uri.scheme)
        assertEquals("search", uri.host)
        assertEquals(NearbyGymNavigation.NAVER_MAP_PACKAGE, native.packageName)
        val params = uri.rawQuery.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to URLDecoder.decode(parts[1], "UTF-8")
        }
        assertEquals(mapOf("query" to "헬스장", "appname" to "com.example.arptapp"), params)
    }

    @Test fun webLinkIsHttpsNaverSearchWithoutCoordinatesOrCredentials() {
        val web = NearbyGymNavigation.targets("com.example.arptapp")[1]
        val uri = URI(web.url)
        assertEquals("https", uri.scheme)
        assertEquals("map.naver.com", uri.host)
        assertEquals("/p/search/헬스장", uri.path)
        assertNull(uri.query)
        assertNull(uri.userInfo)
        assertNull(web.packageName)
    }

    @Test fun applicationIdentifierIsEncodedNotInjected() {
        val native = NearbyGymNavigation.targets("test&id=extra").first()
        assertTrue(native.url.endsWith("appname=test%26id%3Dextra"))
    }

    @Test fun installedAppIsPreferredAndNoWebOpened() {
        val attempts = mutableListOf<NearbyGymNavigation.Target>()
        assertEquals(NearbyGymNavigation.Result.APP, NearbyGymNavigation.open("app") {
            attempts += it
            true
        })
        assertEquals(1, attempts.size)
        assertEquals(NearbyGymNavigation.NAVER_MAP_PACKAGE, attempts.single().packageName)
    }

    @Test fun missingMapAppFallsBackToWeb() {
        val attempts = mutableListOf<NearbyGymNavigation.Target>()
        assertEquals(NearbyGymNavigation.Result.WEB, NearbyGymNavigation.open("app") {
            attempts += it
            it.packageName == null
        })
        assertEquals(2, attempts.size)
        assertEquals("https", URI(attempts.last().url).scheme)
    }

    @Test fun missingAppAndBrowserReturnsUnavailableWithoutRetryLoop() {
        var attempts = 0
        assertEquals(NearbyGymNavigation.Result.UNAVAILABLE, NearbyGymNavigation.open("app") {
            attempts++
            false
        })
        assertEquals(2, attempts)
    }

    private fun usable(lat: Double, lng: Double, time: Long): Boolean =
        NearbyGymPolicy.isUsableLocation(lat, lng, time, now)
}
