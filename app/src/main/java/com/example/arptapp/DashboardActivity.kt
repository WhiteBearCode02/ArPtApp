package com.example.arptapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.databinding.ActivityDashboardBinding
import com.example.arptapp.data.remote.SupabaseRepository
import com.example.arptapp.domain.analyzer.DTWCalculator
import com.example.arptapp.domain.analyzer.FormErrorAnalyzer
import com.example.arptapp.domain.analyzer.PoseAngleExtractor
import com.example.arptapp.domain.counter.ExerciseCounter
import com.example.arptapp.domain.classifier.ExerciseClassifier
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.data.model.Landmark
import com.example.arptapp.data.model.PoseData
import com.example.arptapp.data.model.toAngleSequence
import com.example.arptapp.data.repository.ExerciseRepository
import com.example.arptapp.viewmodel.MainViewModel
import com.example.arptapp.presentation.report.ReportNavigation
import com.example.arptapp.presentation.report.WorkoutReportData
import com.example.arptapp.ui.camera.CameraPipelineStage
import com.example.arptapp.ui.camera.MainPersonSelector
import com.example.arptapp.ui.camera.PoseCameraController
import com.example.arptapp.ui.camera.PoseFrameResult
import com.example.arptapp.ui.camera.LandmarkDetectionFrame
import com.example.arptapp.ui.camera.LandmarkExerciseDetector
import com.example.arptapp.domain.detection.*
import com.example.arptapp.ui.feedback.FeedbackManager
import com.example.arptapp.ui.dashboard.DashboardUiPolicy
import com.example.arptapp.ui.dashboard.PersonalReferenceDialog
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.util.*
import kotlinx.coroutines.launch
import kotlin.jvm.java

/**
 * 실시간 AI 기반 운동 자세 분석 대시보드
 *
 * 권한·세션 전환·센서·화면 표시를 연결하는 운동 화면.
 * 카메라/native 자원의 소유권은 [PoseCameraController], 대상 선택은 [MainPersonSelector],
 * 각도/오류/횟수/DTW 정책은 기존 domain 모듈에 위임한다. 카메라 콜백은 메인 스레드에서 받는다.
 */
class DashboardActivity : AppCompatActivity(), TextToSpeech.OnInitListener, SensorEventListener {

    companion object {
        private const val TAG = "DashboardActivity"
    }

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var cameraController: PoseCameraController
    private val mainViewModel: MainViewModel by viewModels()
    private val supabaseRepository = SupabaseRepository()
    private val poseAngleExtractor = PoseAngleExtractor
    private val exerciseCounter = ExerciseCounter()
    private val repAnalysisEngine = com.example.arptapp.domain.report.RepAnalysisEngine()
    private val fallbackExerciseClassifier = ExerciseClassifier()
    private val primaryExerciseDetector = LandmarkExerciseDetector(fallbackExerciseClassifier)
    private var yoloShadowSnapshot = YoloShadowSnapshot(ExerciseDetectionResult(DetectorStatus.INITIALIZING, DetectionSource.YOLO))
    private var posePeopleCount = 0
    private var shadowThermalStatus = -1
    private var lastShadowLogAt = 0L
    private val yoloSessionDiagnostics = if (BuildConfig.DEBUG)
        YoloSessionDiagnosticsBridge(monotonicMs = SystemClock::uptimeMillis) else null
    private var completedYoloDiagnosticsJson: String? = null
    private var tts: TextToSpeech? = null
    private lateinit var feedbackManager: FeedbackManager
    private lateinit var sensorManager: SensorManager
    private var lastAccelerationX: Float? = null
    private var currentMaxSwayX = 0f
    private var repFeedbackUntilMs = 0L
    private var repPriorityFeedback = ""

    private val currentRepAngles = mutableListOf<FloatArray>()
    private val currentRepErrorTags = mutableSetOf<String>()
    private val dtwCalculator = DTWCalculator()
    private lateinit var standardSquatSequence: List<FloatArray>

    // 운동 카운팅 상태
    private var repetitionCount = 0
    private var startTime: Long = 0
    private var isExercising = false
    private var detectedExerciseType = ExerciseType.UNKNOWN
    private val sessionExerciseType get() = mainViewModel.sessionExerciseType
    private var cameraCanSwitch = false
    private var lensSwitchRequested = false
    private var cameraView = com.example.arptapp.domain.analyzer.CameraView.UNKNOWN
    private val referenceRepository by lazy { com.example.arptapp.data.preferences.PersonalMotionReferenceRepository(this) }
    private val calibrationFrames = mutableListOf<List<Float>>()
    private var calibrationExercise: ExerciseType? = null
    private var referenceChoiceDialog: AlertDialog? = null
    private var yoloDiagnosticsExpanded = false
    private val performanceMonitor = com.example.arptapp.domain.analyzer.FramePerformanceMonitor()
    private var lastDiagnosticsAt = 0L
    private var personalSequence: List<FloatArray> = emptyList()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraController.start()
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupDashboardInsets()
        setupYoloDiagnostics()
        renderYoloShadowDiagnostic()

        tts = TextToSpeech(this, this)
        feedbackManager = FeedbackManager(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        standardSquatSequence = ExerciseRepository(this)
            .loadStandardPose("SQUAT")
            ?.toAngleSequence()
            .orEmpty()
        cameraController = PoseCameraController(
            context = this,
            lifecycleOwner = this,
            previewView = binding.viewFinder,
            onResult = ::onPoseFrame,
            onError = ::onCameraPipelineError,
            onYoloShadowSnapshot = { snapshot ->
                if (BuildConfig.DEBUG) {
                    yoloShadowSnapshot = snapshot
                    renderYoloShadowDiagnostic()
                }
            },
            yoloShadowObserver = yoloSessionDiagnostics ?: NoYoloShadowObserver,
            onLensChanged = { front, canSwitch ->
                cameraCanSwitch = canSwitch
                val text = getString(if (front) R.string.camera_lens_front else R.string.camera_lens_back)
                binding.tvCameraLens.text = text
                binding.btnSwitchCamera.contentDescription = "$text · 카메라 전환"
                applyDashboardControlState()
                if (lensSwitchRequested) Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                lensSwitchRequested = false
            }
        )
        binding.btnSwitchCamera.isEnabled = false
        cameraController.initialize()
        checkCameraPermission()
        setupButtons()
        applyDashboardControlState()
    }

    /** Keep the camera full-bleed; only interactive chrome avoids bars/cutouts in both orientations. */
    private fun setupDashboardInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val chrome = binding.dashboardChrome
        val left = chrome.paddingLeft; val top = chrome.paddingTop
        val right = chrome.paddingRight; val bottom = chrome.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(chrome) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom)
            insets // Do not consume: Preview/Overlay keep their existing full-frame coordinate space.
        }
        ViewCompat.requestApplyInsets(chrome)
    }

    /** Presentation-only projection: never resets or locks a workout, calibration or detector. */
    private fun applyDashboardControlState() {
        val state = DashboardUiPolicy.buttonState(isExercising, calibrationExercise != null)
        val display = DashboardUiPolicy.displayState(isExercising, calibrationExercise != null)
        binding.layoutSetupOptions.visibility = if (display.showSetup) View.VISIBLE else View.GONE
        binding.layoutWorkoutSummary.visibility = if (display.showWorkoutSummary) View.VISIBLE else View.GONE
        binding.btnCameraView.isEnabled = state.cameraViewEnabled
        binding.btnPersonalReference.isEnabled = state.personalReferenceEnabled
        binding.btnStartExercise.isEnabled = state.startExerciseEnabled
        binding.btnSwitchCamera.isEnabled = cameraCanSwitch && state.cameraViewEnabled
        listOf(binding.btnCameraView, binding.btnPersonalReference, binding.btnStartExercise, binding.btnSwitchCamera)
            .forEach { it.alpha = if (it.isEnabled) 1f else .5f }
        renderWorkoutSummary()
    }

    /** A truthful UI projection: a captured exercise is not necessarily the reference used now. */
    private fun renderWorkoutSummary() {
        if (!isExercising) return
        val direction = getString(when (cameraView) {
            com.example.arptapp.domain.analyzer.CameraView.FRONT -> R.string.personal_reference_front
            com.example.arptapp.domain.analyzer.CameraView.SIDE -> R.string.personal_reference_side
            else -> R.string.dashboard_summary_unknown_direction
        })
        val directionText = getString(R.string.dashboard_summary_direction, direction)
        val referenceText = when {
            sessionExerciseType == ExerciseType.UNKNOWN -> getString(R.string.dashboard_summary_reference_pending)
            personalSequence.isNotEmpty() -> getString(R.string.dashboard_summary_reference_active, sessionExerciseType.displayName)
            else -> getString(R.string.dashboard_summary_reference_none)
        }
        if (binding.tvWorkoutDirection.text != directionText) binding.tvWorkoutDirection.text = directionText
        if (binding.tvWorkoutReference.text != referenceText) binding.tvWorkoutReference.text = referenceText
    }

    private fun setupYoloDiagnostics() {
        binding.btnYoloDiagnosticsToggle.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        setYoloDiagnosticsExpanded(false)
        binding.btnYoloDiagnosticsToggle.setOnClickListener {
            setYoloDiagnosticsExpanded(!yoloDiagnosticsExpanded)
        }
    }

    private fun setYoloDiagnosticsExpanded(expanded: Boolean) {
        yoloDiagnosticsExpanded = BuildConfig.DEBUG && expanded
        binding.yoloDiagnosticsPanel.setExpanded(yoloDiagnosticsExpanded)
        binding.btnYoloDiagnosticsToggle.text = getString(if (yoloDiagnosticsExpanded)
            R.string.dashboard_yolo_hide else R.string.dashboard_yolo_view)
        ViewCompat.setStateDescription(binding.btnYoloDiagnosticsToggle, getString(if (yoloDiagnosticsExpanded)
            R.string.dashboard_yolo_expanded_state else R.string.dashboard_yolo_collapsed_state))
    }

    private fun setupButtons() {
        binding.btnPersonalReference.setOnClickListener { togglePersonalReference() }
        binding.btnPersonalReference.setOnLongClickListener {
            if (isExercising) return@setOnLongClickListener true
            val userId = com.example.arptapp.data.remote.AuthSessionStore.current?.userId.orEmpty()
            if (!canUsePersonalReference(userId)) return@setOnLongClickListener true
            val selectedView = cameraView
            AlertDialog.Builder(this).setTitle("개인 기준 동작 삭제")
                .setMessage("현재 촬영 방향의 스쿼트·숄더프레스 개인 기준을 이 기기에서 삭제할까요?")
                .setPositiveButton("삭제") { _, _ ->
                    if (isExercising || cameraView != selectedView ||
                        com.example.arptapp.data.remote.AuthSessionStore.current?.userId != userId) return@setPositiveButton
                    listOf("SQUAT", "SHOULDER_PRESS").forEach { referenceRepository.remove(userId, it, selectedView) }
                    personalSequence = emptyList()
                }.setNegativeButton("취소", null).show()
            true
        }
        binding.btnCameraView.setOnClickListener {
            if (!DashboardUiPolicy.buttonState(isExercising, calibrationExercise != null).cameraViewEnabled)
                return@setOnClickListener
            cameraView = when (cameraView) {
                com.example.arptapp.domain.analyzer.CameraView.UNKNOWN -> com.example.arptapp.domain.analyzer.CameraView.FRONT
                com.example.arptapp.domain.analyzer.CameraView.FRONT -> com.example.arptapp.domain.analyzer.CameraView.SIDE
                else -> com.example.arptapp.domain.analyzer.CameraView.UNKNOWN
            }
            binding.btnCameraView.text = when (cameraView) {
                com.example.arptapp.domain.analyzer.CameraView.FRONT -> getString(R.string.personal_reference_camera_view_value,
                    getString(R.string.personal_reference_front))
                com.example.arptapp.domain.analyzer.CameraView.SIDE -> getString(R.string.personal_reference_camera_view_value,
                    getString(R.string.personal_reference_side))
                else -> getString(R.string.dashboard_camera_view)
            }
            invalidatePendingAnalysis()
            cameraController.invalidateShadowEvidence()
            fallbackExerciseClassifier.reset()
            detectedExerciseType = ExerciseType.UNKNOWN
            updateDetectedExerciseUi(ExerciseType.UNKNOWN)
        }
        // 닫기 버튼
        binding.btnClose.setOnClickListener {
            finish()
        }

        // 카메라 전환 버튼
        binding.btnSwitchCamera.setOnClickListener {
            switchCamera()
        }

        // 운동 시작 버튼
        binding.btnStartExercise.setOnClickListener {
            if (calibrationExercise != null) return@setOnClickListener
            startExercise()
        }

        // 운동 종료 버튼
        binding.btnEndExercise.setOnClickListener {
            endExercise()
        }
    }

    private fun switchCamera() {
        if (isExercising || calibrationExercise != null) {
            Toast.makeText(this, "운동 또는 기준 기록을 마친 뒤 카메라를 전환해 주세요", Toast.LENGTH_SHORT).show()
            return
        }
        if (!cameraCanSwitch) return
        lensSwitchRequested = true
        cameraController.switchCamera()
        invalidatePendingAnalysis()
        fallbackExerciseClassifier.reset()
    }

    private fun togglePersonalReference() {
        if (isExercising) return
        val userId = com.example.arptapp.data.remote.AuthSessionStore.current?.userId.orEmpty()
        if (!canUsePersonalReference(userId)) return
        val capturing = calibrationExercise
        if (capturing != null) {
            val reference = com.example.arptapp.domain.analyzer.PersonalMotionReference(capturing.name, cameraView, calibrationFrames.toList())
            if (reference.isValid()) {
                referenceRepository.save(userId, reference)
                Toast.makeText(this, "개인 비교 기준을 기기에 저장했습니다", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "유효한 움직임 데이터가 부족합니다. 다시 기록해 주세요", Toast.LENGTH_LONG).show()
            calibrationExercise = null
            calibrationFrames.clear()
            binding.btnPersonalReference.text = getString(R.string.dashboard_personal_reference)
            applyDashboardControlState()
            return
        }
        if (referenceChoiceDialog?.isShowing == true) return
        val selectedView = cameraView
        referenceChoiceDialog = PersonalReferenceDialog.show(this, selectedView) { type ->
            if (cameraView != selectedView ||
                com.example.arptapp.data.remote.AuthSessionStore.current?.userId != userId) return@show
            beginReference(type)
        }?.also { dialog -> dialog.setOnDismissListener { referenceChoiceDialog = null } }
    }

    /** Account/view admission only. The original local-only capture and save implementation stays intact. */
    private fun canUsePersonalReference(userId: String): Boolean {
        if (cameraView == com.example.arptapp.domain.analyzer.CameraView.UNKNOWN) {
            Toast.makeText(this, R.string.personal_reference_choose_view_first, Toast.LENGTH_SHORT).show()
            return false
        }
        if (userId.isBlank()) {
            Toast.makeText(this, R.string.personal_reference_login_required, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun beginReference(type: ExerciseType) {
        if (isFinishing || isDestroyed || !DashboardUiPolicy.canBeginReference(type, cameraView,
                isExercising, calibrationExercise != null)) return
        val userId = com.example.arptapp.data.remote.AuthSessionStore.current?.userId.orEmpty()
        if (!canUsePersonalReference(userId)) return
        calibrationFrames.clear()
        calibrationExercise = type
        binding.btnPersonalReference.text = getString(R.string.personal_reference_save)
        applyDashboardControlState()
    }

    private fun captureReferenceFrame(landmarks: List<NormalizedLandmark>) {
        val type = calibrationExercise ?: return
        val angles = when (type) {
            ExerciseType.SQUAT -> PoseAngleExtractor.extractSquatAngles(landmarks)
            ExerciseType.SHOULDER_PRESS -> PoseAngleExtractor.extractShoulderPressAngles(landmarks)
            else -> null
        }
        if (angles == null || angles.any { !it.isFinite() }) {
            calibrationFrames.clear()
            binding.tvPoseFeedback.text = "관절이 가려졌습니다 · 동작을 처음부터 다시 기록해 주세요"
            return
        }
        if (calibrationFrames.size < 300) calibrationFrames.add(angles.toList())
        binding.tvPoseFeedback.text = "${type.displayName} 기준 기록 ${calibrationFrames.size}/300 · 한 동작을 마치고 기준 저장을 누르세요"
    }

    private fun startExercise() {
        if (!DashboardUiPolicy.buttonState(isExercising, calibrationExercise != null).startExerciseEnabled) return
        if (cameraView == com.example.arptapp.domain.analyzer.CameraView.UNKNOWN) {
            Toast.makeText(this, "촬영 방향을 먼저 선택해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        isExercising = true
        setYoloDiagnosticsExpanded(false) // Start with the camera and workout information, not debug details.
        mainViewModel.resetSession()
        completedYoloDiagnosticsJson = null
        yoloSessionDiagnostics?.begin(System.currentTimeMillis(), SystemClock.uptimeMillis(), cameraView)
        cameraController.invalidateShadowEvidence()
        repAnalysisEngine.reset()
        repFeedbackUntilMs = 0L
        startTime = System.currentTimeMillis()
        repetitionCount = 0
        currentRepAngles.clear()
        currentRepErrorTags.clear()
        currentMaxSwayX = 0f
        lastAccelerationX = null

        exerciseCounter.resetSession()
        fallbackExerciseClassifier.reset()
        detectedExerciseType = ExerciseType.UNKNOWN
        personalSequence = emptyList()
        applyDashboardControlState()
        binding.tvCount.text = "0"
        binding.tvCountUnit.text = getString(R.string.dashboard_count_waiting)
        updateDetectedExerciseUi(ExerciseType.UNKNOWN)

        binding.tvDashboardTitle.text = "운동 중"
        binding.btnStartExercise.visibility = View.GONE
        binding.btnEndExercise.visibility = View.VISIBLE

        speakOut("운동을 시작합니다")
    }

    private fun endExercise() {
        if (!isExercising) return
        isExercising = false
        // Freeze scalar diagnostics before the confirmation dialog or any new camera work.
        // This payload is local-only and is never added to the existing Supabase report upload.
        completedYoloDiagnosticsJson = yoloSessionDiagnostics?.finish(System.currentTimeMillis(),
            SystemClock.uptimeMillis(), sessionExerciseType, shadowThermalStatus)
            ?.let(com.example.arptapp.data.YoloSessionDiagnosticsCodec::encode)
        val report = mainViewModel.generateLockedSessionReport()
        if (report == null) {
            restorePreparationUi()
            AlertDialog.Builder(this).setTitle("분석 데이터 확인")
                .setMessage(getString(if (sessionExerciseType == ExerciseType.UNKNOWN)
                    R.string.exercise_type_unconfirmed else R.string.exercise_analysis_insufficient))
                .setPositiveButton("확인", null).show()
            return
        }
        val elapsedTime = if (startTime > 0L) (System.currentTimeMillis() - startTime) / 1000 else 0L
        val records = mainViewModel.getRepRecords()
        val analyses = mainViewModel.getRepAnalyses(report.exerciseType)
        val burnedCalories = report.totalReps * 0.5

        lifecycleScope.launch {
            supabaseRepository.uploadSession(
                report = report,
                records = records,
                analyses = analyses,
                durationSeconds = elapsedTime,
                burnedCalories = burnedCalories
            )
                // 서버 예외 본문에는 계정/리포트 정보가 포함될 수 있어 원문을 로그에 남기지 않는다.
                .onFailure { Log.w(TAG, "Supabase 리포트 업로드 실패") }
        }

        // Closing the report with Back/outside must not strand an idle screen on a disabled End
        // action. Restore only UI; the completed report and session data remain unchanged.
        restorePreparationUi()
        showSessionReport(report) {
            openResultActivity(report, elapsedTime)
        }
    }

    private fun restorePreparationUi() {
        binding.btnStartExercise.visibility = View.VISIBLE
        binding.btnEndExercise.visibility = View.GONE
        applyDashboardControlState()
        binding.tvDashboardTitle.text = getString(R.string.exercise_ready)
    }

    private fun showSessionReport(report: com.example.arptapp.model.SessionReport, onConfirmed: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("운동 리포트")
            .setMessage(
                "운동: ${report.exerciseType}\n" +
                    "총 반복: ${report.totalReps}회\n" +
                    "평균 점수: ${report.averageScore}점\n\n" +
                    report.feedbackMessage
            )
            .setPositiveButton("확인") { _, _ -> onConfirmed() }
            .show()
    }

    private fun openResultActivity(report: com.example.arptapp.model.SessionReport, elapsedTime: Long) {

        // 결과/기록/상세 화면이 같은 키·단위·구버전 읽기 계약을 사용한다.
        val data = WorkoutReportData.create(
            exerciseType = sessionExerciseType.displayName,
            totalCount = report.totalReps,
            durationSeconds = elapsedTime,
            analysesJson = com.example.arptapp.data.StructuredReportCodec.encode(
                mainViewModel.getRepAnalyses(report.exerciseType), mainViewModel.structuredReport(report.exerciseType)
            ),
            averageScore = report.averageScore.toFloat(),
            feedbackMessage = report.feedbackMessage,
            yoloDiagnosticsJson = completedYoloDiagnosticsJson
        )
        startActivity(ReportNavigation.resultIntent(this, data))
        finish()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.KOREAN
        }
    }

    private fun speakOut(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    /** 회전 정규화된 결과만 받아 도메인 분석과 화면 표시를 연결한다. */
    private fun onPoseFrame(frame: PoseFrameResult) {
        if (isFinishing || isDestroyed) return
        val now = SystemClock.uptimeMillis()
        val stats = performanceMonitor.record(frame.timestampMs, now)
        if (now - lastDiagnosticsAt >= 1000L) {
            lastDiagnosticsAt = now
            val thermal = if (android.os.Build.VERSION.SDK_INT >= 29) {
                (getSystemService(POWER_SERVICE) as android.os.PowerManager).currentThermalStatus
            } else -1
            shadowThermalStatus = thermal
            binding.yoloDiagnosticsPanel.updatePerformance(String.format(
                Locale.KOREA,
                "관절 처리 %.1f FPS · %.0f ms · 발열 단계 %s",
                stats.processedFps,
                stats.averageLatencyMs,
                if (thermal < 0) "미지원" else thermal.toString()
            ))
        }
        val mainPersonLandmarks = MainPersonSelector.select(frame.people, { it.x() }, { it.y() })
        if (mainPersonLandmarks != null) {
            captureReferenceFrame(mainPersonLandmarks)
            // ONLY the existing landmark classifier can feed the workout lock. YOLO is observational.
            updateCurrentExerciseType(primaryExerciseDetector.detect(LandmarkDetectionFrame(mainPersonLandmarks, cameraView,
                frame.imageWidth.toDouble() / frame.imageHeight)).exerciseType)
            if (isExercising) {
                processLandmarks(mainPersonLandmarks, frame.imageWidth.toDouble() / frame.imageHeight, frame.timestampMs)
            }
            updateOverlay(mainPersonLandmarks, frame)
        } else {
            if (calibrationExercise != null) {
                calibrationFrames.clear()
                binding.tvPoseFeedback.text = "관절을 다시 찾는 중 · 기준 동작을 처음부터 반복해 주세요"
            }
            invalidatePendingAnalysis()
            fallbackExerciseClassifier.reset()
            binding.tvPoseFeedback.text = "측정 대기 · 전신이 화면에 들어오도록 위치를 조정해 주세요"
            updateCurrentExerciseType(ExerciseType.UNKNOWN)
            binding.overlayView.clearResults()
        }
        posePeopleCount = frame.people.size
        // Primary lock remains authoritative; shadow callbacks only read this admission context.
        yoloSessionDiagnostics?.updateContext(sessionExerciseType, shadowThermalStatus)
        renderYoloShadowDiagnostic()
    }

    /** Debug-only comparison has no API into MainViewModel, counter, scoring or upload. */
    private fun renderYoloShadowDiagnostic() {
        if (!BuildConfig.DEBUG) return
        val liveStats = yoloSessionDiagnostics?.current(System.currentTimeMillis(), SystemClock.uptimeMillis())
        val text = DetectorComparisonFormatter.format(DetectorComparison(yoloShadowSnapshot, detectedExerciseType,
            sessionExerciseType, cameraView, posePeopleCount, shadowThermalStatus, liveStats?.averageProcessedFps))
        binding.yoloDiagnosticsPanel.updateDiagnostics(text)
        val now = SystemClock.uptimeMillis()
        if (now - lastShadowLogAt >= 1000L) {
            lastShadowLogAt = now
            Log.d("YoloShadow", text)
        }
    }

    /** 오류의 기술 단계는 컨트롤러가 구분하고, 사용자 문구/표시는 화면이 결정한다. */
    private fun onCameraPipelineError(stage: CameraPipelineStage, error: Throwable) {
        if (isFinishing || isDestroyed) return
        Log.e(TAG, "카메라 파이프라인 오류: $stage", error)
        when (stage) {
            CameraPipelineStage.MODEL_INITIALIZATION ->
                binding.tvDetectedExercise.text = "자세 인식 모델을 불러오지 못했습니다"
            CameraPipelineStage.CAMERA_BINDING -> {
                lensSwitchRequested = false
                binding.tvCameraLens.text = getString(R.string.camera_lens_failed)
                Toast.makeText(this, "카메라 연결 실패", Toast.LENGTH_SHORT).show()
            }
            CameraPipelineStage.POSE_INFERENCE -> {
                binding.tvDetectedExercise.text = "자세 인식을 다시 시도하는 중"
                binding.overlayView.clearResults()
            }
            CameraPipelineStage.FRAME_PROCESSING -> Unit
        }
    }

    /** 관절 유실/촬영 방향 변경 시 미완성 회차를 폐기해 서로 다른 동작을 합산하지 않는다. */
    private fun invalidatePendingAnalysis() {
        repAnalysisEngine.reset()
        exerciseCounter.invalidatePendingRep()
        currentRepAngles.clear()
        currentRepErrorTags.clear()
    }

    /**
     * 측정 가능 여부 → 코칭 → 회차 카운팅 → 회차 기록의 순서를 유지한다.
     * 측정 불가능한 프레임은 회차를 중단하며, DTW 입력은 최대 300프레임으로 제한한다.
     */
    private fun processLandmarks(landmarks: List<NormalizedLandmark>, aspectRatio: Double, frameTimestampMs: Long) {
        if (sessionExerciseType !in com.example.arptapp.domain.session.SessionExerciseLock.supported) return
        val poseData = landmarks.toPoseData()
        val measurement = poseAngleExtractor.measurePose(sessionExerciseType.name, poseData, cameraView, aspectRatio)
        val assessment = if (sessionExerciseType == ExerciseType.SQUAT && measurement.isEvaluable) {
            FormErrorAnalyzer.assessSquat(landmarks, cameraView, aspectRatio)
        } else null
        val errors = assessment?.errors.orEmpty()
        val feedback = com.example.arptapp.domain.analyzer.CoachingFeedbackFactory.create(measurement, errors).let {
            if (assessment != null && !assessment.evaluable) it.copy(message = assessment.reason ?: "코칭 측정 대기") else it
        }
        binding.overlayView.setFeedback(feedback)
        binding.tvPoseFeedback.text = if (measurement.isEvaluable && SystemClock.uptimeMillis() < repFeedbackUntilMs)
            repPriorityFeedback else feedback.message
        if (!measurement.isEvaluable) {
            invalidatePendingAnalysis()
            return
        }
        // 긴 정지 구간에도 DTW 메모리/계산량이 무한히 증가하지 않도록 최근 프레임만 보관한다.
        if (currentRepAngles.size >= 300) currentRepAngles.removeAt(0)
        when (sessionExerciseType) {
            ExerciseType.SQUAT -> {
                PoseAngleExtractor.extractSquatAngles(landmarks)?.let(currentRepAngles::add)
                currentRepErrorTags += errors
            }
            ExerciseType.SHOULDER_PRESS -> PoseAngleExtractor.extractShoulderPressAngles(landmarks)
                ?.let(currentRepAngles::add)
            else -> Unit
        }

        val exerciseType = sessionExerciseType.name
        val currentAngle = measurement.valueDeg ?: return
        // Submission timeline, not delayed UI callback time. Only relative times leave the engine.
        val timestamp = frameTimestampMs
        repAnalysisEngine.observe(com.example.arptapp.domain.report.RepFrameFeatureExtractor.extract(
            poseData.landmarks, exerciseType, cameraView, aspectRatio, timestamp, currentAngle,
            kneeObservation = assessment?.kneeValgus))
        if (exerciseCounter.processAngle(exerciseType, currentAngle, timestamp)) {
            repetitionCount = exerciseCounter.getRepCount()
            binding.tvCount.text = repetitionCount.toString()

            val referenceSimilarity = if (sessionExerciseType == ExerciseType.SQUAT || personalSequence.isNotEmpty()) calculateReferenceSimilarity() else {
                currentRepAngles.clear()
                null
            }

            // Structural metrics must exist BEFORE calculating the displayed local score.
            val measured = repAnalysisEngine.complete(repetitionCount)
            val record = com.example.arptapp.model.RepRecord(
                repNumber = repetitionCount,
                maxAngle = exerciseCounter.getCurrentMaxAngle(),
                swayX = currentMaxSwayX,
                errorTags = currentRepErrorTags.toList(),
                eccentricDurationMs = exerciseCounter.getLastEccentricDurationMs(),
                concentricDurationMs = exerciseCounter.getLastConcentricDurationMs(),
                poseScore = referenceSimilarity
            )
            val structured = mainViewModel.addCompletedRep(record, measured, cameraView.name, referenceSimilarity)
            structured?.let {
                if (BuildConfig.DEBUG) Log.d("RepAnalysis", com.example.arptapp.domain.report.RepDiagnosticFormatter.format(
                    it, sessionExerciseType.name, detectedExerciseType.name, currentAngle))
                repPriorityFeedback = it.localPriorityFeedback
                repFeedbackUntilMs = SystemClock.uptimeMillis() + 2500
                binding.tvPoseFeedback.text = it.localPriorityFeedback
            }
            feedbackManager.announceRep(repetitionCount, structured?.localPriorityFeedback)
            currentMaxSwayX = 0f
            currentRepErrorTags.clear()

        }
    }

    private fun List<NormalizedLandmark>.toPoseData(): PoseData = PoseData(
        timestamp = System.currentTimeMillis(),
        landmarks = map { landmark ->
            Landmark(
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
                visibility = landmark.visibility().orElse(0f)
            )
        },
        angles = emptyMap()
    )

    private fun calculateReferenceSimilarity(): Float? {
        // The bundled standard is not view-matched; only use it for FRONT, never as SIDE ground truth.
        val reference = personalSequence.ifEmpty { if (sessionExerciseType == ExerciseType.SQUAT &&
            cameraView == com.example.arptapp.domain.analyzer.CameraView.FRONT) standardSquatSequence else emptyList() }
        if (reference.isEmpty() || currentRepAngles.isEmpty()) {
            currentRepAngles.clear()
            return null
        }

        val score = dtwCalculator.calculateAverageScore(
            userSequence = currentRepAngles,
            standardSequence = reference,
            weights = dtwCalculator.getExerciseWeights(sessionExerciseType.name)
        )
        currentRepAngles.clear()
        return score.takeIf { it.isFinite() && it in 0f..100f }
    }

    /**
     * 카메라 컨트롤러가 정규화한 크기를 그대로 사용한다. 여기서 재회전하면 관절이 어긋난다.
     */
    private fun updateOverlay(
        landmarks: List<NormalizedLandmark>,
        frame: PoseFrameResult
    ) {
        binding.overlayView.setResults(
            landmarks,
            frame.imageWidth,
            frame.imageHeight,
            frame.isFrontFacing
        )
    }

    private fun updateCurrentExerciseType(nextExerciseType: ExerciseType) {
        detectedExerciseType = nextExerciseType
        updateDetectedExerciseUi(nextExerciseType)
        if (!isExercising || !mainViewModel.observeDetectedExercise(nextExerciseType)) return
        val userId = com.example.arptapp.data.remote.AuthSessionStore.current?.userId.orEmpty()
        personalSequence = referenceRepository.load(userId, sessionExerciseType.name, cameraView)?.sequence().orEmpty()
        binding.tvCountUnit.text = sessionExerciseType.displayName
        binding.tvDashboardTitle.text = "${sessionExerciseType.displayName} 운동 중"
        updateDetectedExerciseUi(nextExerciseType)
    }

    private fun updateDetectedExerciseUi(exerciseType: ExerciseType) {
        val displayName = if (exerciseType == ExerciseType.IDLE) {
            ExerciseType.UNKNOWN.displayName
        } else {
            exerciseType.displayName
        }
        binding.tvDetectedExercise.text = if (isExercising && sessionExerciseType != ExerciseType.UNKNOWN)
            "세션: ${sessionExerciseType.displayName} · 감지: $displayName" else "인식 중: $displayName"
        renderWorkoutSummary()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            cameraController.start()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        referenceChoiceDialog?.dismiss()
        referenceChoiceDialog = null
        yoloSessionDiagnostics?.finish(System.currentTimeMillis(), SystemClock.uptimeMillis(), sessionExerciseType, shadowThermalStatus)
        // 모델/카메라를 먼저 닫아 native 결과가 종료 중인 화면에 도착하는 것을 차단한다.
        if (::cameraController.isInitialized) cameraController.close()
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        feedbackManager.release()
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isExercising || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val currentX = event.values[0]
        lastAccelerationX?.let { currentMaxSwayX = maxOf(currentMaxSwayX, kotlin.math.abs(currentX - it)) }
        lastAccelerationX = currentX
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
