package com.example.arptapp.ui.dashboard

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.arptapp.R
import com.example.arptapp.data.YoloSessionDiagnosticsCodec
import com.example.arptapp.databinding.ActivityReportBinding
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.detection.*
import com.example.arptapp.presentation.report.YoloSessionDiagnosticsRenderer
import com.example.arptapp.ui.camera.CameraZoomVerificationActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic in-memory observations only: no camera, authentication, DB, images or saved sessions. */
@RunWith(AndroidJUnit4::class)
class YoloDiagnosticsHelpDeviceTest {
    @Test fun liveOverviewHelpAndTechnicalSectionsAreIndependentAndResetWhenClosed() {
        ActivityScenario.launch(CameraZoomVerificationActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val panel = activity.binding.yoloDiagnosticsPanel
                panel.updateComparison(DetectorComparison(YoloShadowSnapshot(ExerciseDetectionResult(
                    DetectorStatus.MODEL_MISSING, DetectionSource.YOLO)), ExerciseType.SQUAT,
                    ExerciseType.UNKNOWN, CameraView.SIDE, 1, -1))
                panel.updateDiagnostics("TECHNICAL_FIXTURE_ONLY")
                panel.setExpanded(true)
                assertTrue(panel.findViewById<TextView>(R.id.tvYoloStatus).text.contains("모델이 없어요"))
                assertEquals(View.GONE, panel.findViewById<View>(R.id.tvYoloDiagnostics).visibility)
                assertEquals(View.GONE, panel.findViewById<View>(R.id.tvYoloGuide).visibility)
                panel.findViewById<View>(R.id.btnYoloGuide).performClick()
                assertEquals(View.VISIBLE, panel.findViewById<View>(R.id.tvYoloGuide).visibility)
                assertTrue(panel.findViewById<TextView>(R.id.tvYoloGuide).text.contains("자세 정확도가 90점이라는 뜻은 아닙니다"))
                panel.findViewById<View>(R.id.btnYoloTechnical).performClick()
                assertEquals(View.VISIBLE, panel.findViewById<View>(R.id.tvYoloDiagnostics).visibility)
                panel.setExpanded(false); panel.setExpanded(true)
                assertEquals(View.GONE, panel.findViewById<View>(R.id.tvYoloDiagnostics).visibility)
                assertEquals(View.GONE, panel.findViewById<View>(R.id.tvYoloGuide).visibility)
            }
        }
    }

    @Test fun savedReportDefaultsToReadableGroupsAndHidesTechnicalDetailsOnRebind() {
        ActivityScenario.launch(CameraZoomVerificationActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val binding = ActivityReportBinding.inflate(activity.layoutInflater)
                activity.setContentView(binding.root)
                val renderer = YoloSessionDiagnosticsRenderer(activity.layoutInflater)
                val json = YoloSessionDiagnosticsCodec.encode(YoloSessionDiagnostics(
                    modelStatus = "MODEL_MISSING", contractStatus = "NOT_VERIFIED", predictionEnabled = false,
                    startedAt = 1, endedAt = 2))
                assertNotNull(json)
                renderer.render(binding, json)
                assertEquals(View.GONE, binding.yoloShadowDetails.visibility)
                binding.btnToggleYoloShadow.performClick()
                assertEquals(4, binding.yoloShadowMetricGroups.childCount)
                assertEquals(View.GONE, binding.yoloShadowTechnicalGroups.visibility)
                assertEquals(0, binding.yoloShadowTechnicalGroups.childCount)
                binding.yoloShadowHelp.btnYoloGuide.performClick()
                assertEquals(View.VISIBLE, binding.yoloShadowHelp.tvYoloGuide.visibility)
                binding.btnYoloReportTechnical.performClick()
                assertEquals(View.VISIBLE, binding.yoloShadowTechnicalGroups.visibility)
                assertTrue(binding.yoloShadowTechnicalGroups.childCount > 0)
                renderer.render(binding, json)
                assertEquals(View.GONE, binding.yoloShadowDetails.visibility)
                assertEquals(View.GONE, binding.yoloShadowTechnicalGroups.visibility)
                assertEquals(View.GONE, binding.yoloShadowHelp.tvYoloGuide.visibility)
                renderer.render(binding, null)
                assertEquals(View.GONE, binding.yoloShadowSection.visibility)
            }
        }
    }
}
