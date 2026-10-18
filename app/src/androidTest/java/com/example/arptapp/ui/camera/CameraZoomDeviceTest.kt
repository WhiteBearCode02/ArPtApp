package com.example.arptapp.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.app.KeyguardManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.R
import com.example.arptapp.ui.dashboard.CameraZoomUi
import com.example.arptapp.domain.detection.ExerciseDetectionResult
import com.example.arptapp.domain.detection.DetectorStatus
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No DB/account access, fixture inserts, screenshots or frame writes. Only scalar test receipts.
 * Pipeline callbacks prove liveness, NOT exercise accuracy, visible skeleton alignment or rep count.
 */
@RunWith(AndroidJUnit4::class)
class CameraZoomDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun requireUnlocked() = assumeTrue("unlock device before camera/window tests",
        !instrumentation.targetContext.getSystemService(KeyguardManager::class.java).isDeviceLocked)

    @Test fun twoFingerPinchDoesNotConsumeButtonsOrOneFingerAndCancelsCleanly() {
        requireUnlocked()
        ActivityScenario.launch(CameraZoomVerificationActivity::class.java).use { scenario ->
            lateinit var root: CameraZoomGestureLayout
            lateinit var button: Button
            lateinit var scroll: NestedScrollView
            var scales = 0
            var clicks = 0
            scenario.onActivity { activity ->
                root = CameraZoomGestureLayout(activity).apply {
                    zoomAvailable = true
                    onScaleFactor = { scales++ }
                }
                button = Button(activity).apply { setOnClickListener { clicks++ } }
                scroll = NestedScrollView(activity).apply {
                    id = R.id.scrollDashboardContent
                    // Empty FrameLayout has no intrinsic height under ScrollView's UNSPECIFIED
                    // measurement. Give this fixture real minimum content, not only LayoutParams.
                    addView(FrameLayout(activity).apply { minimumHeight = 6000 },
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6000))
                }
                root.addView(scroll, FrameLayout.LayoutParams(-1, -1).apply { topMargin = 200 })
                root.addView(button, FrameLayout.LayoutParams(400, 180))
                root.protectedViews = listOf(button)
                activity.setContentView(root)
            }
            instrumentation.waitForIdleSync()
            val laidOut = AtomicBoolean()
            await("gesture host layout") { scenario.onActivity { laidOut.set(root.height > 600) }; laidOut.get() }
            scenario.onActivity {
                assertTrue(root.height > 600)
                assertTrue("fixture must actually be scrollable", scroll.getChildAt(0).height > scroll.height)
                val x = root.width / 2f
                val y = root.height / 2f
                stream(root, x, y, pinch = false)
                assertEquals("one finger must never scale", 0, scales)
            }
            // Inject a real one-finger swipe through the Window, including normal event timing.
            onView(withId(R.id.scrollDashboardContent)).perform(swipeUp())
            scenario.onActivity {
                val x = root.width / 2f
                val y = root.height / 2f
                assertTrue("one-finger vertical scroll still works beneath chrome", scroll.scrollY > 0)
                assertEquals(0, scales)
                scroll.scrollTo(0, 0)
                stream(root, x, y, pinch = true)
                assertTrue("two-finger MOVE must reach ScaleGestureDetector", scales > 0)
                assertEquals("remaining finger must not click a button", 0, clicks)
                val previous = scales
                stream(root, 120f, 90f, pinch = true)
                assertEquals("pinch starting on a protected control must not scale", previous, scales)
                stream(root, 120f, 90f, pinch = false)
                root.zoomAvailable = false
                stream(root, x, y, pinch = true)
                assertEquals("unsupported camera must not scale", previous, scales)
                root.cancelGesture()
            }
            // View posts performClick after UP; do not assert inside that same main-thread turn.
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals("protected button keeps normal tap dispatch", 1, clicks) }
        }
    }

    @Test fun rearFrontZoomResetAndRebindKeepActualAnalysisPipelineAlive() {
        requireUnlocked()
        assumeTrue("grant CAMERA in the app first; tests never grant it silently",
            instrumentation.targetContext.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        ActivityScenario.launch(CameraZoomVerificationActivity::class.java).use { scenario ->
            val zoom = AtomicReference(CameraZoomState())
            val poseFrames = AtomicInteger()
            val frontPoseFrames = AtomicInteger()
            val shadowFrames = AtomicInteger()
            val shadowState = AtomicReference<ExerciseDetectionResult?>()
            val failures = CopyOnWriteArrayList<String>()
            val canSwitch = AtomicReference(false)
            lateinit var controller: PoseCameraController
            var ui: CameraZoomUi? = null
            scenario.onActivity { activity ->
                val b = activity.binding
                controller = PoseCameraController(activity, activity, b.viewFinder,
                    onResult = { frame ->
                        poseFrames.incrementAndGet()
                        if (frame.isFrontFacing) frontPoseFrames.incrementAndGet()
                    },
                    onError = { stage, _ -> failures.add(stage.name) },
                    onLensChanged = { _, switch -> canSwitch.set(switch) },
                    onYoloShadowSnapshot = { snapshot ->
                        shadowState.set(snapshot.result)
                        if (snapshot.result.timings != null) shadowFrames.incrementAndGet()
                    },
                    onZoomChanged = { zoom.set(it); ui?.render(it) })
                ui = CameraZoomUi(b.root, b.btnZoomReset, controller, listOf(
                    b.layoutCameraHeader, b.layoutDetectedExercise, b.layoutSetupOptions,
                    b.layoutWorkoutSummary, b.yoloDiagnosticsPanel, b.layoutWorkoutFooter,
                    b.layoutCameraActions, b.btnZoomReset))
                controller.initialize()
                controller.start()
            }
            try {
                await("rear bind") { zoom.get().available }
                await("Shadow initialization result") { shadowState.get()?.status?.let { it != DetectorStatus.INITIALIZING } == true }
                val rear = zoom.get()
                exerciseZoom(scenario, controller, zoom, "rear")
                val poseBefore = poseFrames.get()
                val shadowBefore = shadowFrames.get()
                scenario.onActivity { controller.setZoomRatio(1.3f) }
                await("zoomed MediaPipe callbacks") { poseFrames.get() >= poseBefore + 3 }
                // Existing private provisioned model is optional; absence must keep fallback alive.
                if (shadowState.get()?.predictionAllowed == true) await("zoomed Shadow callbacks") { shadowFrames.get() > shadowBefore }
                receipt("rear min=${rear.minZoomRatio} max=${rear.maxZoomRatio}; zoomed pose callbacks=PASS; shadow status=${shadowState.get()?.status}; shadow callbacks=${shadowFrames.get()}")
                if (canSwitch.get()) {
                    scenario.onActivity { controller.switchCamera() }
                    await("front bind") { zoom.get().available && controller.isFrontFacing }
                    exerciseZoom(scenario, controller, zoom, "front")
                    val frontBefore = frontPoseFrames.get()
                    scenario.onActivity { controller.setZoomRatio(1.3f) }
                    await("zoomed front MediaPipe callbacks") { frontPoseFrames.get() >= frontBefore + 3 }
                    scenario.onActivity { controller.switchCamera() }
                    await("rear rebind default") { zoom.get().available && !controller.isFrontFacing && zoom.get().currentZoomRatio == 1f }
                }
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.onActivity { assertFalse("paused camera rejects zoom", controller.scaleZoom(1.1f)) }
                val beforeResume = poseFrames.get()
                scenario.moveToState(Lifecycle.State.RESUMED)
                await("new callbacks after resume") { poseFrames.get() >= beforeResume + 3 }
                assertTrue("pipeline errors: $failures", failures.isEmpty())
            } finally {
                scenario.onActivity { ui?.close(); controller.close(); assertFalse(controller.scaleZoom(2f)) }
            }
        }
    }

    @Test fun realPortraitLandscapeLayoutsKeepChipSeparateFromHeaderFooterAndCta() {
        requireUnlocked()
        ActivityScenario.launch(CameraZoomVerificationActivity::class.java).use { scenario ->
            for ((orientation, configuration) in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE)) {
                scenario.onActivity { it.requestedOrientation = orientation }
                val ready = AtomicBoolean()
                await("orientation layout $configuration") {
                    scenario.onActivity { ready.set(it.resources.configuration.orientation == configuration && it.binding.btnZoomReset.width > 0) }
                    ready.get()
                }
                scenario.onActivity {
                    val b = it.binding
                    val chip = Rect()
                    assertTrue("ratio chip visible in actual layout", b.btnZoomReset.getGlobalVisibleRect(chip))
                    for (view in listOf(b.layoutCameraHeader, b.layoutWorkoutFooter, b.layoutCameraActions)) {
                        val protected = Rect()
                        if (view.getGlobalVisibleRect(protected)) assertFalse("no overlap with ${view.id}", Rect.intersects(chip, protected))
                    }
                    assertTrue(b.btnZoomReset.height >= (48 * it.resources.displayMetrics.density).toInt())
                    assertTrue(b.btnZoomReset.contentDescription.isNotBlank())
                }
                receipt("layout orientation=$configuration; visible chip/header/footer/CTA separation=PASS")
            }
        }
    }

    private fun exerciseZoom(scenario: ActivityScenario<CameraZoomVerificationActivity>, control: CameraZoomControl,
        observed: AtomicReference<CameraZoomState>, lens: String) {
        val start = observed.get()
        assertEquals(1f, start.currentZoomRatio, .001f)
        val middle = (start.maxZoomRatio.coerceAtMost(2f)).coerceAtLeast(start.minZoomRatio)
        scenario.onActivity { control.setZoomRatio(middle) }
        await("$lens enlarge") { kotlin.math.abs(observed.get().currentZoomRatio - middle) < .001f }
        scenario.onActivity { control.setZoomRatio(Float.MAX_VALUE) }
        await("$lens max clamp") { observed.get().currentZoomRatio == start.maxZoomRatio }
        scenario.onActivity { control.setZoomRatio(.001f) }
        await("$lens min clamp") { observed.get().currentZoomRatio == start.minZoomRatio }
        scenario.onActivity { control.resetZoom() }
        await("$lens reset") { kotlin.math.abs(observed.get().currentZoomRatio - 1f) < .001f }
        receipt("$lens min=${start.minZoomRatio} max=${start.maxZoomRatio}; enlarge/min/max/reset=PASS")
    }

    private fun await(label: String, condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < until) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        fail("timeout: $label")
    }

    private fun receipt(message: String) {
        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "ZOOM: $message\n") })
    }

    /** Real Android dispatch through attached views. Motion events never leave this test process. */
    private fun stream(root: CameraZoomGestureLayout, x: Float, y: Float, pinch: Boolean) {
        val down = SystemClock.uptimeMillis()
        var tick = 0L
        fun event(action: Int, points: List<Pair<Float, Float>>) {
            tick += 32
            val properties = Array(points.size) { index -> MotionEvent.PointerProperties().apply {
                id = index; toolType = MotionEvent.TOOL_TYPE_FINGER
            } }
            val coords = Array(points.size) { index -> MotionEvent.PointerCoords().apply {
                this.x = points[index].first; this.y = points[index].second; pressure = 1f; size = .1f
            } }
            val e = MotionEvent.obtain(down, down + tick, action, points.size, properties, coords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { root.dispatchTouchEvent(e) } finally { e.recycle() }
        }
        val first = x - if (pinch) 70f else 0f
        event(MotionEvent.ACTION_DOWN, listOf(first to y))
        if (pinch) {
            event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(first to y, (x + 70f) to y))
            for (span in listOf(90f, 120f, 160f, 210f))
                event(MotionEvent.ACTION_MOVE, listOf((x - span) to y, (x + span) to y))
            event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf((x - 210f) to y, (x + 210f) to y))
        }
        event(MotionEvent.ACTION_UP, listOf(first to y))
    }
}
