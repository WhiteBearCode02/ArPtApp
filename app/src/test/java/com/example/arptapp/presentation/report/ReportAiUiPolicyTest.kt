package com.example.arptapp.presentation.report

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportAiUiPolicyTest {
    @Test fun `disabled policy never enables requests for any screen state`() {
        listOf(false, true).forEach { hasReps ->
            listOf(false, true).forEach { loading ->
                listOf(false, true).forEach { completed ->
                    assertFalse(ReportAiUiPolicy.canRequest(false, hasReps, loading, completed))
                }
            }
        }
    }

    @Test fun `explicit opt in still requires measured reps and an idle request`() {
        assertFalse(ReportAiUiPolicy.canRequest(true, false, false, false))
        assertFalse(ReportAiUiPolicy.canRequest(true, true, true, false))
        assertFalse(ReportAiUiPolicy.canRequest(true, true, false, true))
        assertTrue(ReportAiUiPolicy.canRequest(true, true, false, false))
    }
}
