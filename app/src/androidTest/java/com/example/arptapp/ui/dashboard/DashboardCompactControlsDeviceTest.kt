package com.example.arptapp.ui.dashboard

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.R
import com.example.arptapp.databinding.ActivityDashboardBinding
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.ui.camera.CameraZoomVerificationActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android view measurement at controlled size/font configs; no camera/account/storage access.
 * Separate from a human visual review and from actual rep or calibration recording validation.
 */
@RunWith(AndroidJUnit4::class)
class DashboardCompactControlsDeviceTest {
    @Test fun normalNarrowLandscapeAndLargeTextKeepSettingsAboveCountWithoutClipping() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for ((width, height, font) in listOf(Triple(360, 800, 1f), Triple(640, 360, 1f),
                Triple(240, 640, 1.8f), Triple(480, 320, 1.8f))) {
                val config = Configuration(instrumentation.targetContext.resources.configuration).apply {
                    orientation = if (width > height) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                    fontScale = font
                    screenWidthDp = width; screenHeightDp = height
                }
                val context = ContextThemeWrapper(instrumentation.targetContext.createConfigurationContext(config), R.style.Theme_ArPtApp)
                val b = ActivityDashboardBinding.inflate(LayoutInflater.from(context))
                val scale = context.resources.displayMetrics.density
                fun measure() {
                    b.root.measure(View.MeasureSpec.makeMeasureSpec((width * scale).toInt(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec((height * scale).toInt(), View.MeasureSpec.EXACTLY))
                    b.root.layout(0, 0, b.root.measuredWidth, b.root.measuredHeight)
                }
                b.btnCameraView.text = context.getString(R.string.dashboard_camera_view_compact_value,
                    context.getString(R.string.personal_reference_front))
                measure()
                if (width == 360) assertEquals(LinearLayout.HORIZONTAL, b.layoutSetupOptions.orientation)
                val visibleHeight = b.layoutWorkoutFooter.measuredHeight
                val settingsHeight = b.layoutSetupOptions.measuredHeight
                assertTrue(b.layoutCountAndDiagnostics.top >= b.layoutSetupOptions.bottom)
                for (button in listOf(b.btnCameraView, b.btnPersonalReference)) {
                    assertTrue(button.height >= (48 * scale).toInt())
                    assertTrue(button.width >= (48 * scale).toInt())
                    assertTrue(button.right <= b.layoutSetupOptions.width)
                }
                // Existing visibility policy removes the entire row including its margins.
                b.layoutSetupOptions.visibility = if (DashboardUiPolicy.displayState(true, false).showSetup) View.VISIBLE else View.GONE
                measure()
                assertEquals(View.GONE, b.layoutSetupOptions.visibility)
                assertTrue(visibleHeight - b.layoutWorkoutFooter.measuredHeight >= settingsHeight)
                b.layoutSetupOptions.visibility = View.VISIBLE
                b.btnCameraView.text = "방향 · 측면 촬영 상태를 수동으로 변경합니다"
                b.btnPersonalReference.text = "내 기준 기록 · 숄더 프레스 자세 기준 저장"
                measure()
                assertEquals("long labels wrap into a column", LinearLayout.VERTICAL, b.layoutSetupOptions.orientation)
                assertTrue(b.btnPersonalReference.top >= b.btnCameraView.bottom)
                for (button in listOf(b.btnCameraView, b.btnPersonalReference)) assertFullLabel(button)
                b.yoloDiagnosticsPanel.setExpanded(true)
                measure()
                assertTrue(b.layoutCountAndDiagnostics.top >= b.layoutSetupOptions.bottom)
                assertTrue(b.layoutCameraActions.top >= b.scrollDashboardContent.bottom)
            }
        }
    }

    @Test fun referenceDialogKeepsVerticalChoicesAndCancelWithoutSavingAnything() {
        ActivityScenario.launch(CameraZoomVerificationActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var selected: ExerciseType? = null
                val dialog = requireNotNull(PersonalReferenceDialog.show(activity, CameraView.FRONT) { selected = it })
                val options = requireNotNull(dialog.findViewById<LinearLayout>(R.id.referenceOptions))
                assertEquals(2, options.childCount)
                assertEquals(LinearLayout.VERTICAL, options.orientation)
                options.getChildAt(0).performClick()
                assertEquals(ExerciseType.SQUAT, selected)
                assertFalse(dialog.isShowing)
                selected = null
                val cancelled = requireNotNull(PersonalReferenceDialog.show(activity, CameraView.SIDE) { selected = it })
                requireNotNull(cancelled.findViewById<View>(R.id.btnCancelReference)).performClick()
                assertNull(selected)
                assertFalse(cancelled.isShowing)
            }
        }
    }

    private fun assertFullLabel(button: TextView) {
        val text = requireNotNull(button.layout)
        assertEquals(button.text.length, text.getLineEnd(text.lineCount - 1))
        for (line in 0 until text.lineCount) assertEquals("no ellipsis", 0, text.getEllipsisCount(line))
        assertTrue(text.height <= button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
    }
}
