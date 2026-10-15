package com.example.arptapp.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.example.arptapp.domain.detection.YoloShadowSnapshot
import com.example.arptapp.ui.camera.yolo.DebugYoloShadowFactory
import java.util.concurrent.Executors

/** MediaPipe 입력과 동일한, 회전 정규화 후의 이미지 크기를 담은 일시적 결과. */
data class PoseFrameResult(
    val people: List<List<NormalizedLandmark>>,
    val timestampMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val isFrontFacing: Boolean
)

enum class CameraPipelineStage { MODEL_INITIALIZATION, CAMERA_BINDING, POSE_INFERENCE, FRAME_PROCESSING }

/**
 * CameraX와 MediaPipe의 자원 소유권/스레드 경계를 담당한다.
 *
 * Activity는 권한·운동 세션·화면 표시만 관리한다. 이 모듈은 프리뷰 바인딩, RGBA 변환,
 * 회전 정규화, 제출 간격, 모델 해제를 책임지며 결과/오류 콜백은 메인 스레드로 전달한다.
 * 영상·관절 좌표를 파일이나 서버에 저장하지 않는다. `close()`는 Activity 종료 시 호출한다.
 */
class PoseCameraController(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onResult: (PoseFrameResult) -> Unit,
    private val onError: (CameraPipelineStage, Throwable) -> Unit,
    private val onLensChanged: (isFrontFacing: Boolean, canSwitch: Boolean) -> Unit = { _, _ -> },
    private val onYoloShadowSnapshot: (YoloShadowSnapshot) -> Unit = {}
) : AutoCloseable {
    companion object {
        private const val TAG = "PoseCameraController"

        init {
            try {
                System.loadLibrary("mediapipe_tasks_vision_jni")
            } catch (error: UnsatisfiedLinkError) {
                Log.e(TAG, "MediaPipe JNI 라이브러리 로드 실패", error)
            }
        }
    }

    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val submissionPolicy = FrameSubmissionPolicy()
    private val frameTracker = CameraFrameTracker()
    // 분석 executor와 메인 스레드의 모델 close()가 동시에 native 자원을 사용하지 않게 한다.
    private val inferenceLock = Any()
    private var poseLandmarker: PoseLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null
    @Volatile private var closed = false
    @Volatile private var lensFacing = CameraSelector.LENS_FACING_BACK
    private val yoloShadow = DebugYoloShadowFactory.create(appContext, ::dispatchYoloShadowSnapshot)

    private fun dispatchYoloShadowSnapshot(snapshot: YoloShadowSnapshot) {
        mainExecutor.execute {
            if (!closed && yoloShadow?.isCurrent(snapshot) == true) onYoloShadowSnapshot(snapshot)
        }
    }

    /** Clears observational evidence only; never changes the landmark detector or workout lock. */
    fun invalidateShadowEvidence() { yoloShadow?.invalidate() }

    val isFrontFacing: Boolean
        get() = lensFacing == CameraSelector.LENS_FACING_FRONT

    /** 기존 lite 모델과 LIVE_STREAM 설정을 유지한다. YOLO 분류 결과에 종속되지 않는다. */
    fun initialize() {
        // Separate worker/timeout: YOLO absence/init failure never interrupts the MediaPipe pipeline.
        yoloShadow?.start()
        synchronized(inferenceLock) {
            if (closed || poseLandmarker != null) return
            try {
                val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build())
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener { result, inputImage ->
                        val token = frameTracker.consume(result.timestampMs())
                        if (token != null) {
                            val frame = PoseFrameResult(
                                people = result.landmarks(),
                                timestampMs = result.timestampMs(),
                                imageWidth = inputImage.width,
                                imageHeight = inputImage.height,
                                isFrontFacing = token.isFrontFacing
                            )
                            mainExecutor.execute {
                                if (!closed && frameTracker.isCurrent(token.generation)) onResult(frame)
                            }
                        }
                    }
                    .setErrorListener { error -> dispatchError(CameraPipelineStage.POSE_INFERENCE, error) }
                    .build()
                poseLandmarker = PoseLandmarker.createFromOptions(appContext, options)
            } catch (error: Exception) {
                dispatchError(CameraPipelineStage.MODEL_INITIALIZATION, error)
            } catch (error: UnsatisfiedLinkError) {
                dispatchError(CameraPipelineStage.MODEL_INITIALIZATION, error)
            }
        }
    }

    /** 카메라 권한이 승인된 뒤 메인 스레드에서 호출한다. 종료 후 완료되는 future는 무시한다. */
    fun start() {
        if (closed) return
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            if (closed) return@addListener
            try {
                cameraProvider = future.get()
                bindUseCases()
            } catch (error: Exception) {
                dispatchError(CameraPipelineStage.CAMERA_BINDING, error)
            }
        }, mainExecutor)
    }

    /** 운동/개인 기준 기록 중 전환 금지는 세션을 아는 Activity에서 판단한다. */
    fun switchCamera() {
        if (closed) return
        bindUseCases(if (isFrontFacing) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT)
    }

    private fun bindUseCases(requestedFacing: Int = lensFacing) {
        val provider = cameraProvider ?: return
        if (closed) return
        val hasBack: Boolean
        val hasFront: Boolean
        val selected: CameraLens
        try {
            hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            selected = CameraLensPolicy.select(
                if (requestedFacing == CameraSelector.LENS_FACING_BACK) CameraLens.BACK else CameraLens.FRONT,
                hasBack, hasFront) ?: error("사용 가능한 카메라가 없습니다.")
        } catch (error: Exception) {
            dispatchError(CameraPipelineStage.CAMERA_BINDING, error)
            return
        }
        val selectedFacing = if (selected == CameraLens.BACK) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        val selector = CameraSelector.Builder().requireLensFacing(selectedFacing).build()
        frameTracker.advance()
        yoloShadow?.invalidate()
        val generation = frameTracker.currentGeneration()
        val front = selected == CameraLens.FRONT
        val nextPreview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val nextAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(analysisExecutor) { image -> analyzeImage(image, generation, front) } }
        try {
            imageAnalyzer?.clearAnalyzer()
            provider.unbindAll()
            preview = nextPreview
            imageAnalyzer = nextAnalyzer
            provider.bindToLifecycle(lifecycleOwner, selector, nextPreview, nextAnalyzer)
            // Publish actual bound lens, not requested lens, so front-only fallback matches the UI/mirror.
            lensFacing = selectedFacing
            onLensChanged(front, hasBack && hasFront)
        } catch (error: Exception) {
            nextAnalyzer.clearAnalyzer()
            dispatchError(CameraPipelineStage.CAMERA_BINDING, error)
        }
    }

    private fun analyzeImage(imageProxy: ImageProxy, generation: Long, front: Boolean) {
        try {
            synchronized(inferenceLock) {
                if (closed || !frameTracker.isCurrent(generation)) return
                val landmarker = poseLandmarker ?: return
                val timestamp = SystemClock.uptimeMillis()
                if (!submissionPolicy.tryAccept(timestamp)) return
                val bitmap = imageProxy.toBitmap()
                val normalizedBitmap = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
                val image = BitmapImageBuilder(normalizedBitmap).build()
                if (!frameTracker.register(timestamp, generation, front)) return
                // detectAsync는 비동기 추론이다. 제출 직후 입력 bitmap을 recycle하면 안 된다.
                landmarker.detectAsync(image, timestamp)
                // NOT_VERIFIED/UNAVAILABLE returns before copying any bitmap or calling inference.
                // Admission makes one short owned copy; all resize/color/layout work and native
                // inference remain on the shadow worker. Never recycle/share MediaPipe's input.
                yoloShadow?.trySubmit(normalizedBitmap, timestamp)
            }
        } catch (error: Exception) {
            dispatchError(CameraPipelineStage.FRAME_PROCESSING, error)
        } finally {
            // 스로틀로 건너뛰거나 모델이 없거나 예외가 나도 프레임을 닫아 CameraX 정체를 방지한다.
            imageProxy.close()
        }
    }

    /** 이미지 자체를 회전시켜 추론과 OverlayView가 동일한 좌표계를 쓰게 한다. */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat(), bitmap.width / 2f, bitmap.height / 2f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    private fun dispatchError(stage: CameraPipelineStage, error: Throwable) {
        mainExecutor.execute { if (!closed) onError(stage, error) }
    }

    /**
     * 멱등 해제: 새 분석을 먼저 중지하고 진행 중 제출과 직렬화하여 모델을 닫는다.
     * 이미 메인 큐에 올라온 결과도 closed 검사로 폐기하므로 종료된 화면을 갱신하지 않는다.
     */
    override fun close() {
        if (closed) return
        closed = true
        yoloShadow?.close()
        frameTracker.advance()
        imageAnalyzer?.clearAnalyzer()
        preview?.let { cameraProvider?.unbind(it) }
        imageAnalyzer?.let { cameraProvider?.unbind(it) }
        analysisExecutor.shutdown()
        synchronized(inferenceLock) {
            poseLandmarker?.close()
            poseLandmarker = null
        }
        imageAnalyzer = null
        preview = null
        cameraProvider = null
    }
}
