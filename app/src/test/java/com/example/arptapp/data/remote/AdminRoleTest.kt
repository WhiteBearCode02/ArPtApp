package com.example.arptapp.data.remote

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminRoleTest {
    @Test
    fun acceptsOnlyAdminRoleFromAppMetadata() {
        assertTrue(hasAdminRole(buildJsonObject { put("role", "admin") }))
        assertTrue(hasAdminRole(buildJsonObject { put("role", "ADMIN") }))
        assertFalse(hasAdminRole(buildJsonObject { put("role", "user") }))
        assertFalse(hasAdminRole(buildJsonObject { put("role", "admin ") }))
        assertFalse(hasAdminRole(null))
    }
}
