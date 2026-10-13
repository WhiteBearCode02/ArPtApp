package com.example.arptapp.ui.gym

import org.junit.Assert.*
import org.junit.Test

class NearbyGymProviderPolicyTest {
    @Test fun noPermissionSelectsNoProvider() {
        assertTrue(NearbyGymProviderPolicy.select(false, false, listOf("gps", "network")).isEmpty())
    }
    @Test fun approximatePermissionNeverRequestsGps() {
        assertEquals(listOf("network"), NearbyGymProviderPolicy.select(false, true, listOf("gps", "network")))
        assertTrue(NearbyGymProviderPolicy.select(false, true, listOf("gps")).isEmpty())
    }
    @Test fun finePermissionSupportsGpsOnlyDeviceWithoutGoogleServices() {
        assertEquals(listOf("gps"), NearbyGymProviderPolicy.select(true, false, listOf("gps")))
    }
    @Test fun enabledProvidersAreDeduplicatedAndPassiveIsNotAnActiveRequest() {
        assertEquals(listOf("network", "gps"),
            NearbyGymProviderPolicy.select(true, true, listOf("gps", "network", "network", "passive", "unknown")))
        assertTrue(NearbyGymProviderPolicy.select(true, true, listOf("passive")).isEmpty())
    }
    @Test fun firstUsableResultWinsOnlyOnce() {
        val state = NearbyGymLocationCompletion(setOf("gps", "network"))
        assertEquals(NearbyGymLocationCompletion.Result.READY, state.complete("network", true))
        assertNull(state.complete("gps", true))
        assertTrue(state.isFinished())
    }
    @Test fun oneFailedProviderDoesNotDiscardTheOther() {
        val state = NearbyGymLocationCompletion(setOf("gps", "network"))
        assertNull(state.complete("gps", false))
        assertEquals(NearbyGymLocationCompletion.Result.READY, state.complete("network", true))
    }
    @Test fun allNullOrInvalidLocationsReturnUnavailable() {
        val state = NearbyGymLocationCompletion(setOf("gps", "network"))
        assertNull(state.complete("gps", false))
        assertEquals(NearbyGymLocationCompletion.Result.UNAVAILABLE, state.complete("network", false))
    }
    @Test fun permissionRevocationIsReportedWhenNoOtherProviderSucceeds() {
        val state = NearbyGymLocationCompletion(setOf("gps", "network"))
        assertNull(state.complete("gps", false, denied = true))
        assertEquals(NearbyGymLocationCompletion.Result.PERMISSION_REQUIRED, state.complete("network", false))
    }
    @Test fun duplicateOrUnknownCallbacksCannotFinishAnotherProvider() {
        val state = NearbyGymLocationCompletion(setOf("gps", "network"))
        assertNull(state.complete("unknown", true))
        assertNull(state.complete("gps", false))
        assertNull(state.complete("gps", false))
        assertFalse(state.isFinished())
        assertEquals(NearbyGymLocationCompletion.Result.READY, state.complete("network", true))
    }
    @Test fun stopOrTimeoutIgnoresLateCallbacks() {
        val state = NearbyGymLocationCompletion(setOf("gps"))
        state.cancel()
        assertNull(state.complete("gps", true))
        assertTrue(state.isFinished())
    }
}
