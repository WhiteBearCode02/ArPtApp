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
import com.example.arptapp.ui.feedback.FeedbackManager
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
    private val fallbackExerciseClassifier = ExerciseClassifier()
    private var tts: TextToSpeech? = null
    private lateinit var feedbackManager: FeedbackManager
    private lateinit var sensorManager: SensorManager
    private var lastAccelerationX: Float? = null
    private var currentMaxSwayX = 0f

    private val currentRepAngles = mutableListOf<FloatArray>()
    private val currentRepErrorTags = mutableSetOf<String>()
    private val dtwCalculator = DTWCalculator()
    private lateinit var standardSquatSequence: List<FloatArray>

    // 운동 카운팅 상태
    private var repetitionCount = 0
    private var startTime: Long = 0
    private var isExercising = false
    private var currentExerciseType = ExerciseType.UNKNOWN
    private var cameraView = com.example.arptapp.domain.analyzer.CameraView.UNKNOWN
    private val referenceRepository by lazy { com.example.arptapp.data.preferences.PersonalMotionReferenceRepository(this) }
    private val calibrationFrames = mutableListOf<List<Float>>()
    private var calibrationExercise: ExerciseType? = null
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
            onError = ::onCameraPipelineError
        )
        cameraController.initialize()
        checkCameraPermission()
        setupButtons()
    }

    private fun setupButtons() {
        binding.btnPersonalReference.setOnClickListener { togglePersonalReference() }
        binding.btnPersonalReference.setOnLongClickListener {
            val userId = com.example.arptapp.data.remote.AuthSessionStore.current?.userId.orEmpty()
            AlertDialog.Builder(this).setTitle("개인 기준 동작 삭제")
                .setMessage("현재 촬영 방향의 스쿼트·숄더프레스 개인 기준을 이 기기에서 삭제할까요?")
                .setPositiveButton("삭제") { _, _ ->
                    listOf("SQUAT", "SHOULDER_PRESS").forEach { referenceRepository.remove(userId, it, cameraView) }
                    personalSequence = emptyList()
                }.setNegativeButton("취소", null).show()
            true
        }
        binding.btnCameraView.setOnClickListener {
            cameraView = when (cameraView) {
                com.example.arptapp.domain.analyzer.CameraView.UNKNOWN -> com.example.arptapp.domain.analyzer.CameraView.FRONT
                com.example.arptapp.domain.analyzer.CameraView.FRONT -> com.example.arptapp.domain.analyzer.CameraView.SIDE
                else -> com.example.arptapp.domain.analyzer.CameraView.UNKNOWN
            }
            binding.btnCameraView.text = when (cameraView) {
                com.example.arptapp.domain.analyzer.CameraView.FRONT -> "촬영: 정면"
                com.example.arptapp.domain.analyzer.CameraView.SIDE -> "촬영: 측면"
                else -> "촬영 방향 선택"
            }
            invalidatePendingAnalysis()
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
        cameraController.switchCamera()
        Toast.makeText(
            this,
            if (cameraController.isFrontFacing) "전면 카메라" else "후면 카메라",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun togglePersonalReference() {
        if (isExercising) return
        val userId = com.example.arptapp.data.remote.AuthSessionStore.current?.userId.orEmpty()
        if (userId.isBlank() || cameraView == com.example.arptapp.domain.analyzer.CameraView.UNKNOWN) {
            Toast.makeText(this, "로그인 후 촬영 방향을 먼저 선택해 주세요", Toast.LENGTH_SHORT).show()
            return
        }
        val capturing = calibrationExercise
        if (capturing != null) {
            val reference = com.example.arptapp.domain.analyzer.PersonalMotionReference(capturing.name, cameraView, calibrationFrames.toList())
            if (reference.isValid()) {
                referenceRepository.save(userId, reference)
                Toast.makeText(this, "개인 비교 기준을 기기에 저장했습니다", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "유효한 움직임 데이터가 부족합니다. 다시 기록해 주세요", Toast.LENGTH_LONG).show()
            calibrationExercise = null
            calibrationFrames.clear()
            binding.btnPersonalReference.text = "내 기준 기록"
            binding.btnCameraView.isEnabled = true
            binding.btnStartExercise.isEnabled = true
            return
        }
        AlertDialog.Builder(this).setTitle("개인 비교 기준 기록")
            .setMessage("종목을 선택하고 전신이 보이도록 한 번의 동작을 천천히 수행한 뒤 저장을 누르세요. 관절 각도만 이 기기에 저장하며 영상은 저장하지 않습니다. 내 동작과의 비교 기준이며 올바른 자세 인증은 아닙니다. 길게 누르면 삭제할 수 있습니다.")
            .setPositiveButton("스쿼트") { _, _ -> beginReference(ExerciseType.SQUAT) }
            .setNeutralButton("숄더프레스") { _, _ -> beginReference(ExerciseType.SHOULDER_PRESS) }
            .setNegativeButton("취소", null).show()
    }

    private fun beginReference(type: ExerciseType) {
        calibrationFrames.clear()
        calibrationExercise = type
        binding.btnPersonalReference.text = "기준 저장"
        binding.btnCameraView.isEnabled = false
        binding.btnStartExercise.isEnabled = false
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
        isExercising = true
        mainViewModel.resetSession()
        startTime = System.currentTimeMillis()
        repetitionCount = 0
        currentRepAngles.clear()
        currentRepErrorTags.clear()
        currentMaxSwayX = 0f
        lastAccelerationX = null

        exerciseCounter.resetSession()
        fallbackExerciseClassifier.reset()
        currentExerciseType = ExerciseType.UNKNOWN
        personalSequence = emptyList()
        binding.btnCameraView.isEnabled = false
        binding.btnPersonalReference.isEnabled = false
        binding.tvCount.text = "0"
        updateDetectedExerciseUi(ExerciseType.UNKNOWN)

        binding.tvDashboardTitle.text = "운동 중"
        binding.btnStartExercise.visibility = View.GONE
        binding.btnEndExercise.visibility = View.VISIBLE

        speakOut("운동을 시작합니다")
    }

    private fun endExercise() {
        isExercising = false
        val report = mainViewModel.generateFinalReport(currentExerciseType.name)
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

        showSessionReport(report) {
            openResultActivity(report, elapsedTime)
        }
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
            exerciseType = currentExerciseType.displayName,
            totalCount = report.totalReps,
            durationSeconds = elapsedTime,
            analysesJson = com.example.arptapp.data.RepAnalysisCodec.encode(
                mainViewModel.getRepAnalyses(report.exerciseType)
            ),
            averageScore = report.averageScore.toFloat(),
            feedbackMessage = report.feedbackMessage
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
            binding.tvPerformance.text = String.format(
                Locale.KOREA,
                "처리 %.1f FPS · %.0f ms · 발열 단계 %s",
                stats.processedFps,
                stats.averageLatencyMs,
                if (thermal < 0) "미지원" else thermal.toString()
            )
        }
        val mainPersonLandmarks = MainPersonSelector.select(frame.people, { it.x() }, { it.y() })
        if (mainPersonLandmarks != null) {
            captureReferenceFrame(mainPersonLandmarks)
            updateCurrentExerciseType(fallbackExerciseClassifier.detectExercise(mainPersonLandmarks))
            if (isExercising) {
                processLandmarks(mainPersonLandmarks, frame.imageWidth.toDouble() / frame.imageHeight)
            }
            updateOverlay(mainPersonLandmarks, frame)
        } else {
            if (calibrationExercise != null) {
                calibrationFrames.clear()
                binding.tvPoseFeedback.text = "관절을 다시 찾는 중 · 기준 동작을 처음부터 반복해 주세요"
            }
            invalidatePendingAnalysis()
            binding.tvPoseFeedback.text = "측정 대기 · 전신이 화면에 들어오도록 위치를 조정해 주세요"
            updateCurrentExerciseType(ExerciseType.UNKNOWN)
            binding.overlayView.clearResults()
        }
    }

    /** 오류의 기술 단계는 컨트롤러가 구분하고, 사용자 문구/표시는 화면이 결정한다. */
    private fun onCameraPipelineError(stage: CameraPipelineStage, error: Throwable) {
        if (isFinishing || isDestroyed) return
        Log.e(TAG, "카메라 파이프라인 오류: $stage", error)
        when (stage) {
            CameraPipelineStage.MODEL_INITIALIZATION ->
                binding.tvDetectedExercise.text = "자세 인식 모델을 불러오지 못했습니다"
            CameraPipelineStage.CAMERA_BINDING ->
                Toast.makeText(this, "카메라 연결 실패", Toast.LENGTH_SHORT).show()
            CameraPipelineStage.POSE_INFERENCE -> {
                binding.tvDetectedExercise.text = "자세 인식을 다시 시도하는 중"
                binding.overlayView.clearResults()
            }
            CameraPipelineStage.FRAME_PROCESSING -> Unit
        }
    }

    /** 관절 유실/촬영 방향 변경 시 미완성 회차를 폐기해 서로 다른 동작을 합산하지 않는다. */
    private fun invalidatePendingAnalysis() {
        exerciseCounter.invalidatePendingRep()
        currentRepAngles.clear()
        currentRepErrorTags.clear()
    }

    /**
     * 측정 가능 여부 → 코칭 → 회차 카운팅 → 회차 기록의 순서를 유지한다.
     * 측정 불가능한 프레임은 회차를 중단하며, DTW 입력은 최대 300프레임으로 제한한다.
     */
    private fun processLandmarks(landmarks: List<NormalizedLandmark>, aspectRatio: Double) {
        val measurement = poseAngleExtractor.measurePose(currentExerciseType.name, landmarks.toPoseData(), cameraView, aspectRatio)
        val assessment = if (currentExerciseType == ExerciseType.SQUAT && measurement.isEvaluable) {
            FormErrorAnalyzer.assessSquat(landmarks, cameraView)
        } else null
        val errors = assessment?.errors.orEmpty()
        val feedback = com.example.arptapp.domain.analyzer.CoachingFeedbackFactory.create(measurement, errors).let {
            if (assessment != null && !assessment.evaluable) it.copy(message = assessment.reason ?: "코칭 측정 대기") else it
        }
        binding.overlayView.setFeedback(feedback)
        binding.tvPoseFeedback.text = feedback.message
        if (!measurement.isEvaluable) {
            invalidatePendingAnalysis()
            return
        }
        // 긴 정지 구간에도 DTW 메모리/계산량이 무한히 증가하지 않도록 최근 프레임만 보관한다.
        if (currentRepAngles.size >= 300) currentRepAngles.removeAt(0)
        when (currentExerciseType) {
            ExerciseType.SQUAT -> {
                PoseAngleExtractor.extractSquatAngles(landmarks)?.let(currentRepAngles::add)
                currentRepErrorTags += errors
            }
            ExerciseType.SHOULDER_PRESS -> PoseAngleExtractor.extractShoulderPressAngles(landmarks)
                ?.let(currentRepAngles::add)
            else -> Unit
        }

        val exerciseType = currentExerciseType.name
        val currentAngle = measurement.valueDeg ?: return

        if (exerciseCounter.processAngle(exerciseType, currentAngle, SystemClock.uptimeMillis())) {
            repetitionCount = exerciseCounter.getRepCount()
            binding.tvCount.text = repetitionCount.toString()
            feedbackManager.announceRep(repetitionCount)

            val poseScore = if (currentExerciseType == ExerciseType.SQUAT || personalSequence.isNotEmpty()) scoreCurrentRep() else {
                currentRepAngles.clear()
                null
            }

            mainViewModel.addRepRecord(
                repNumber = repetitionCount,
                angle = exerciseCounter.getCurrentMaxAngle(),
                sway = currentMaxSwayX,
                errorTags = currentRepErrorTags.toList(),
                eccentricDurationMs = exerciseCounter.getLastEccentricDurationMs(),
                concentricDurationMs = exerciseCounter.getLastConcentricDurationMs(),
                poseScore = poseScore
            )
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

    private fun scoreCurrentRep(): Float? {
        val reference = personalSequence.ifEmpty { if (currentExerciseType == ExerciseType.SQUAT) standardSquatSequence else emptyList() }
        if (reference.isEmpty() || currentRepAngles.isEmpty()) {
            currentRepAngles.clear()
            return null
        }

        val score = dtwCalculator.calculateAverageScore(
            userSequence = currentRepAngles,
            standardSequence = reference,
            weights = dtwCalculator.getExerciseWeights(currentExerciseType.name)
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
        // 하나의 리포트에 종목이 섞이지 않도록 운동 중 최초 확정 종목을 고정한다.
        if (isExercising && currentExerciseType != ExerciseType.UNKNOWN) {
            updateDetectedExerciseUi(currentExerciseType)
            return
        }
        updateDetectedExerciseUi(nextExerciseType)
        if (nextExerciseType == ExerciseType.LUNGE) {
            binding.tvDetectedExercise.text = "런지 감지 · 현재 횟수 분석은 스쿼트·숄더프레스를 지원합니다"
            return
        }
        if (nextExerciseType == ExerciseType.UNKNOWN || nextExerciseType == currentExerciseType) return

        currentExerciseType = nextExerciseType
        val userId = com.example.arptapp.data.remote.AuthSessionStore.current?.userId.orEmpty()
        personalSequence = referenceRepository.load(userId, nextExerciseType.name, cameraView)?.sequence().orEmpty()
        repetitionCount = 0
        currentRepAngles.clear()
        currentRepErrorTags.clear()
        binding.tvCount.text = "0"
        binding.tvCountUnit.text = nextExerciseType.displayName
        if (isExercising) {
            binding.tvDashboardTitle.text = "${nextExerciseType.displayName} 운동 중"
        }
    }

    private fun updateDetectedExerciseUi(exerciseType: ExerciseType) {
        val displayName = if (exerciseType == ExerciseType.IDLE) {
            ExerciseType.UNKNOWN.displayName
        } else {
            exerciseType.displayName
        }
        binding.tvDetectedExercise.text = "인식 중: $displayName"
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
