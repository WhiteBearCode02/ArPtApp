package com.example.arptapp.presentation.report

import org.junit.Assert.*
import org.junit.Test

class RepMetricExpansionStateTest {
    @Test fun `metrics start collapsed independently`() {
        val state = RepMetricExpansionState()
        assertFalse(state.isExpanded(1))
        assertFalse(state.isExpanded(3))
    }

    @Test fun `first toggle expands and second toggle collapses`() {
        val state = RepMetricExpansionState()
        assertTrue(state.toggle(3))
        assertTrue(state.isExpanded(3))
        assertFalse(state.toggle(3))
        assertFalse(state.isExpanded(3))
    }

    @Test fun `expanding one card does not expand or collapse another`() {
        val state = RepMetricExpansionState()
        state.toggle(3)
        assertFalse(state.isExpanded(1))
        state.toggle(1)
        state.toggle(3)
        assertTrue(state.isExpanded(1))
        assertFalse(state.isExpanded(3))
    }

    @Test fun `new screen instance does not inherit other screen state`() {
        val original = RepMetricExpansionState()
        original.toggle(3)
        assertFalse(RepMetricExpansionState().isExpanded(3))
    }
}
