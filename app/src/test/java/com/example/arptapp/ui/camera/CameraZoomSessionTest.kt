package com.example.arptapp.ui.camera

import org.junit.Assert.*
import org.junit.Test

class CameraZoomSessionTest {
    private class Port : CameraZoomCommand {
        val targets = mutableListOf<Float>()
        val completions = mutableListOf<(Boolean) -> Unit>()
        override fun submit(ratio: Float, completed: (Boolean) -> Unit) { targets += ratio; completions += completed }
    }
    private val observed = mutableListOf<CameraZoomState>()
    private val session = CameraZoomSession(observed::add)
    private fun ready(port: Port = Port(), min: Float = .5f, max: Float = 8f): Pair<Long, Port> {
        val binding = session.bind(port)
        session.update(binding, CameraZoomPolicy.fromCamera(1f, min, max))
        return binding to port
    }

    @Test fun noBindingAndAfterCloseCannotSubmit() {
        assertFalse(session.scaleZoom(2f)); assertFalse(session.resetZoom()); assertFalse(session.setZoomRatio(2f))
        val (_, port) = ready(); session.close(); session.close()
        assertFalse(session.scaleZoom(2f)); assertFalse(session.setZoomRatio(2f)); assertFalse(session.zoomState.available)
        session.bind(port); assertFalse(session.setZoomRatio(2f)); assertTrue(port.targets.isEmpty())
    }
    @Test fun observerIsOnlySourceOfVisibleRatio() {
        val (binding, port) = ready()
        assertTrue(session.scaleZoom(2f)); assertEquals(listOf(2f), port.targets)
        assertEquals(1f, session.zoomState.currentZoomRatio, 0f)
        session.update(binding, CameraZoomPolicy.fromCamera(2f, .5f, 8f))
        assertEquals(2f, session.zoomState.currentZoomRatio, 0f)
    }
    @Test fun rapidPinchAccumulatesPendingTargetNotStaleObserverValue() {
        val (_, port) = ready()
        session.scaleZoom(2f); session.scaleZoom(2f)
        port.completions[0](false) // Older superseded operation cannot clear the newest target.
        session.scaleZoom(2f)
        assertEquals(listOf(2f, 4f, 8f), port.targets)
    }
    @Test fun successfulCompletionBeforeObserverDoesNotLoseAccumulatedTarget() {
        val (_, port) = ready()
        session.scaleZoom(2f); port.completions.single()(true); session.scaleZoom(2f)
        assertEquals(listOf(2f, 4f), port.targets)
    }
    @Test fun latestFailureClearsPendingWithoutFakingObservedRatio() {
        val (_, port) = ready()
        session.scaleZoom(2f); port.completions.single()(false); session.scaleZoom(2f)
        assertEquals(listOf(2f, 2f), port.targets)
        assertEquals(1f, session.zoomState.currentZoomRatio, 0f)
    }
    @Test fun resetIsClampedAndDoesNotChangeTheDisplayedRatioUntilObserved() {
        val (binding, port) = ready()
        session.update(binding, CameraZoomPolicy.fromCamera(4f, .5f, 8f))
        assertTrue(session.resetZoom()); assertEquals(1f, port.targets.last(), 0f)
        assertEquals(4f, session.zoomState.currentZoomRatio, 0f)
    }
    @Test fun frontRearRebindingRejectsStaleObserverAndCompletion() {
        val (rear, oldPort) = ready()
        session.scaleZoom(8f)
        val front = Port(); val current = session.bind(front)
        assertFalse(session.zoomState.available)
        session.update(current, CameraZoomPolicy.fromCamera(1f, 1f, 1.5f))
        oldPort.completions.single()(true)
        session.update(rear, CameraZoomPolicy.fromCamera(8f, .5f, 8f))
        assertEquals(1.5f, session.zoomState.maxZoomRatio, 0f)
        assertTrue(session.scaleZoom(10f)); assertEquals(listOf(1.5f), front.targets)
        assertEquals(listOf(8f), oldPort.targets)
    }
    @Test fun sameLensRebindStartsAtNewCameraDefaultNotPreviousTarget() {
        val (_, old) = ready(); session.scaleZoom(4f)
        val replacement = Port(); val token = session.bind(replacement)
        session.update(token, CameraZoomPolicy.fromCamera(2f, .5f, 8f))
        assertEquals(listOf(1f), replacement.targets)
        assertEquals(listOf(4f), old.targets)
    }
    @Test fun fixedFrontCameraDoesNotIssueRedundantZoomCommands() {
        val (_, port) = ready(min = 1f, max = 1f)
        repeat(10) { assertFalse(session.scaleZoom(10f)); assertFalse(session.resetZoom()) }
        assertTrue(port.targets.isEmpty()); assertFalse(session.zoomState.canZoom)
    }
    @Test fun pauseOrDestroyedOwnerCannotSubmitAndResumeCanReset() {
        var started = false
        val controlled = CameraZoomSession({}, { started })
        val port = Port(); val token = controlled.bind(port)
        controlled.update(token, CameraZoomPolicy.fromCamera(2f, 1f, 4f))
        assertFalse(controlled.setZoomRatio(3f)); assertTrue(port.targets.isEmpty())
        started = true
        controlled.update(token, CameraZoomPolicy.fromCamera(2f, 1f, 4f))
        assertEquals(listOf(1f), port.targets)
    }
    @Test fun synchronousCameraFailureIsIsolated() {
        val token = session.bind(CameraZoomCommand { _, _ -> error("camera closed") })
        session.update(token, CameraZoomPolicy.fromCamera(1f, 1f, 4f))
        assertFalse(session.setZoomRatio(2f)); assertEquals(1f, session.zoomState.currentZoomRatio, 0f)
    }
    @Test fun duplicateStateAndDuplicateTargetsDoNotChurnUiOrNativeRequests() {
        val (token, port) = ready()
        val size = observed.size
        repeat(20) { session.update(token, CameraZoomPolicy.fromCamera(1f, .5f, 8f)); session.scaleZoom(1f) }
        assertEquals(size, observed.size); assertTrue(port.targets.isEmpty())
    }
}
