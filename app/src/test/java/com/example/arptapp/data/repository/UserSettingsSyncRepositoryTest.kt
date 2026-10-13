package com.example.arptapp.data.repository

import com.example.arptapp.data.preferences.UserSettings
import com.example.arptapp.data.remote.CloudBodyProfile
import com.example.arptapp.data.remote.CloudReminderPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 네트워크 없이 실제 병합/조회/저장 경계를 검증한다. 원격 DB 검증을 대신하지는 않는다. */
class UserSettingsSyncRepositoryTest {
    private val initial = UserSettings("local", 170f, 65f, 30f, 20f, true, 20, 0)

    private class Fixture(initial: UserSettings) : UserSettingsStore, BodyProfileSource, ReminderPreferencesSource {
        val accounts = mutableMapOf("a" to initial, "b" to UserSettings("other", reminderHour = 7))
        val calls = mutableListOf<String>()
        var profileResult: Result<CloudBodyProfile?> = Result.success(null)
        var reminderResult: Result<CloudReminderPreferences?> = Result.success(CloudReminderPreferences(true, 20, 0))
        var seedResult: Result<Unit> = Result.success(Unit)
        var cancelProfile = false
        var seeded: UserSettings? = null
        val repository = UserSettingsSyncRepository(this, this, this)
        override suspend fun activateUser(userId: String) { calls += "activate:$userId" }
        override suspend fun getSettings(userId: String): UserSettings {
            calls += "local:$userId"
            return accounts.getValue(userId)
        }
        override suspend fun save(userId: String, settings: UserSettings) {
            calls += "save:$userId"
            accounts[userId] = settings
        }
        override suspend fun getProfile(userId: String): Result<CloudBodyProfile?> {
            calls += "profile:$userId"
            if (cancelProfile) throw CancellationException("screen destroyed")
            return profileResult
        }
        override suspend fun saveBodyProfile(userId: String, settings: UserSettings): Result<Unit> {
            error("Sync must not upload body values")
        }
        override suspend fun getPreferences(userId: String): Result<CloudReminderPreferences?> {
            calls += "reminder:$userId"
            return reminderResult
        }
        override suspend fun savePreferences(userId: String, settings: UserSettings): Result<Unit> {
            calls += "seed:$userId"
            seeded = settings
            return seedResult
        }
    }

    @Test fun mergesProfileAndReminderWithoutTouchingOtherAccount() = runBlocking {
        val fixture = Fixture(initial)
        fixture.profileResult = Result.success(CloudBodyProfile("cloud", 180f, null, null, 18f))
        fixture.reminderResult = Result.success(CloudReminderPreferences(false, 6, 30))
        val result = fixture.repository.synchronize("a")
        assertEquals(initial.copy(nickname = "cloud", heightCm = 180f, bodyFatPercentage = 18f,
            reminderEnabled = false, reminderHour = 6, reminderMinute = 30), result)
        assertEquals(result, fixture.accounts["a"])
        assertEquals("other", fixture.accounts.getValue("b").nickname)
        assertEquals(listOf("activate:a", "local:a", "profile:a", "reminder:a", "save:a"), fixture.calls)
    }

    @Test fun onlyMissingReminderRowSeedsRemoteDefaults() = runBlocking {
        val fixture = Fixture(initial)
        fixture.reminderResult = Result.success(null)
        assertEquals(initial, fixture.repository.synchronize("a"))
        assertEquals(initial, fixture.seeded)
        assertEquals(1, fixture.calls.count { it == "seed:a" })
    }

    @Test fun failedReminderLookupNeverWritesRemoteDefaults() = runBlocking {
        val fixture = Fixture(initial)
        fixture.reminderResult = Result.failure(IllegalStateException("network unavailable"))
        assertEquals(initial, fixture.repository.synchronize("a"))
        assertNull(fixture.seeded)
        assertFalse(fixture.calls.any { it.startsWith("seed:") })
    }

    @Test fun failedProfileLookupKeepsLocalBodyValues() = runBlocking {
        val fixture = Fixture(initial)
        fixture.profileResult = Result.failure(IllegalStateException("profile query failed"))
        assertEquals(initial, fixture.repository.synchronize("a"))
    }

    @Test fun failedSeedKeepsLocalSettingsAndDoesNotRetry() = runBlocking {
        val fixture = Fixture(initial)
        fixture.reminderResult = Result.success(null)
        fixture.seedResult = Result.failure(IllegalStateException("write failed"))
        assertEquals(initial, fixture.repository.synchronize("a"))
        assertEquals(1, fixture.calls.count { it.startsWith("seed:") })
    }

    @Test fun blankRemoteProfileAndInvalidTimeAreNormalized() = runBlocking {
        val fixture = Fixture(initial)
        fixture.profileResult = Result.success(CloudBodyProfile("", null, null, null, null))
        fixture.reminderResult = Result.success(CloudReminderPreferences(true, 50, -1))
        assertEquals(initial.copy(reminderHour = 23), fixture.repository.synchronize("a"))
    }

    @Test fun cancellationDoesNotStartFollowingQueryOrSave() = runBlocking {
        val fixture = Fixture(initial)
        fixture.cancelProfile = true
        try {
            fixture.repository.synchronize("a")
            fail("Cancellation must escape")
        } catch (_: CancellationException) {
            assertEquals(listOf("activate:a", "local:a", "profile:a"), fixture.calls)
            assertEquals(initial, fixture.accounts["a"])
        }
    }

    @Test fun blankAccountIsRejectedBeforeAnyIo() = runBlocking {
        val fixture = Fixture(initial)
        try {
            fixture.repository.synchronize(" ")
            fail("Blank account must be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(fixture.calls.isEmpty())
        }
    }
}
