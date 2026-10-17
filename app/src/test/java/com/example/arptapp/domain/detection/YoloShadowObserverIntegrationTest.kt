package com.example.arptapp.domain.detection

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** JVM fake runtime only; these tests do not claim Android native inference or actual-model Golden. */
class YoloShadowObserverIntegrationTest {
    private fun ready(timed: Boolean = false) = ExerciseDetectionResult(DetectorStatus.READY, DetectionSource.YOLO,
        selected = if (timed) ExerciseDetection(ExerciseType.SQUAT, .8f, DetectionSource.YOLO) else null,
        timings = if (timed) DetectionTimings(1, 2, 3, 6) else null,
        contractVerification = ExportContractVerification.VERIFIED, shadowRuntimeEnabled = true)
    private fun stabilizer() = YoloExerciseStabilizer(YoloStabilizationPolicy(.5f, windowSize = 1, minimumAgreeing = 1))
    private fun result(queue: LinkedBlockingQueue<YoloShadowSnapshot>) = requireNotNull(queue.poll(5, TimeUnit.SECONDS))
    private class Detector(private val initializeAction: () -> ExerciseDetectionResult,
                           private val detectAction: () -> ExerciseDetectionResult) : ExerciseDetector<Int> {
        override fun initialize() = initializeAction()
        override fun detect(frame: Int) = detectAction()
        override fun close() = Unit
    }

    @Test fun missingModelCountsSeenFramesButNeverCopiesAdmitsOrRunsNative() {
        val bridge = YoloSessionDiagnosticsBridge(monotonicMs = { 1500 })
        bridge.begin(10_000, 1000, CameraView.SIDE)
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>(); val copies = AtomicInteger(); val detects = AtomicInteger()
        val runner = YoloShadowRunner(Detector({ ExerciseDetectionResult(DetectorStatus.MODEL_MISSING, DetectionSource.YOLO) },
            { detects.incrementAndGet(); ready(true) }), stabilizer(), { copies.incrementAndGet(); it }, {}, queue::offer, observer = bridge)
        try {
            runner.start(); assertEquals(DetectorStatus.MODEL_MISSING, result(queue).result.status)
            repeat(5) { assertFalse(runner.trySubmit(it, 1000L + it * 250)) }
            val report = requireNotNull(bridge.finish(11_000, 2000, ExerciseType.SQUAT, -1))
            assertEquals("MODEL_MISSING", report.modelStatus); assertEquals(5L, report.totalFramesSeen)
            assertEquals(0L, report.admittedFrames); assertEquals(0L, report.processedFrames)
            assertEquals(0, copies.get()); assertEquals(0, detects.get())
        } finally { runner.close() }
    }

    @Test fun runnerKeepsAdmissionContextUnknownEvenWhenPrimaryLocksBeforeCompletion() {
        val bridge = YoloSessionDiagnosticsBridge(monotonicMs = { 1500 })
        bridge.begin(10_000, 1000, CameraView.SIDE)
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>()
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val runner = YoloShadowRunner(Detector({ ready() }, {
            entered.countDown(); finish.await(5, TimeUnit.SECONDS); ready(true)
        }), stabilizer(), { it }, {}, queue::offer, inferenceTimeoutMs = 10_000, observer = bridge)
        try {
            runner.start(); result(queue)
            assertTrue(runner.trySubmit(1, 1000)); assertTrue(entered.await(5, TimeUnit.SECONDS))
            bridge.updateContext(ExerciseType.SQUAT, 1)
            finish.countDown(); result(queue)
            val report = requireNotNull(bridge.finish(11_000, 2000, ExerciseType.SQUAT, 1))
            assertEquals(1L, report.processedFrames); assertEquals(1L, report.unknownSessionFrames)
            assertEquals(0L, report.matchingStableFrames); assertNull(report.knownMatchRate)
        } finally { finish.countDown(); runner.close() }
    }

    @Test fun lateNativeCompletionAfterSessionRestartDoesNotPolluteNewSession() {
        val bridge = YoloSessionDiagnosticsBridge(monotonicMs = { 2500 })
        bridge.begin(10_000, 1000, CameraView.SIDE); bridge.updateContext(ExerciseType.SQUAT, 1)
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>()
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val runner = YoloShadowRunner(Detector({ ready() }, {
            entered.countDown(); finish.await(5, TimeUnit.SECONDS); ready(true)
        }), stabilizer(), { it }, {}, queue::offer, inferenceTimeoutMs = 10_000, observer = bridge)
        try {
            runner.start(); result(queue)
            assertTrue(runner.trySubmit(1, 1000)); assertTrue(entered.await(5, TimeUnit.SECONDS))
            bridge.finish(11_000, 2000, ExerciseType.SQUAT, 1)
            bridge.begin(20_000, 2000, CameraView.FRONT)
            finish.countDown(); result(queue)
            val report = requireNotNull(bridge.finish(21_000, 3000, ExerciseType.SQUAT, 1))
            assertEquals(0L, report.totalFramesSeen); assertEquals(0L, report.admittedFrames); assertEquals(0L, report.processedFrames)
        } finally { finish.countDown(); runner.close() }
    }

    @Test fun initializationAndInvalidationStayStatusOnlyWhileRealCompletionsGiveFiniteFps() {
        val bridge = YoloSessionDiagnosticsBridge(monotonicMs = { 1500 })
        bridge.begin(10_000, 1000, CameraView.SIDE); bridge.updateContext(ExerciseType.SQUAT, 1)
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>(); val worker = Executors.newSingleThreadExecutor()
        val runner = YoloShadowRunner(Detector({ ready() }, { ready(true) }), stabilizer(), { it }, {}, queue::offer,
            worker = worker, observer = bridge)
        try {
            runner.start(); result(queue); worker.submit {}.get(5, TimeUnit.SECONDS)
            runner.invalidate(); result(queue)
            assertEquals(0L, requireNotNull(bridge.current(10_500, 1500)).processedFrames)
            repeat(2) { index ->
                assertTrue(runner.trySubmit(index, 1000L + index * 250)); result(queue)
                worker.submit {}.get(5, TimeUnit.SECONDS)
            }
            val report = requireNotNull(bridge.finish(11_000, 2000, ExerciseType.SQUAT, 1))
            assertEquals(2L, report.totalFramesSeen); assertEquals(2L, report.admittedFrames); assertEquals(2L, report.processedFrames)
            assertEquals(2.0, report.averageProcessedFps!!, .00001); assertTrue(report.averageProcessedFps!!.isFinite())
            assertEquals(2L, report.matchingStableFrames)
        } finally { runner.close() }
    }

    @Test fun observerCallbackExceptionsDoNotLeakFrameOwnershipOrBreakNextAdmission() {
        val context = YoloShadowObservationContext(1, ExerciseType.SQUAT, 0)
        val observer = object : YoloShadowObserver {
            override fun captureContext() = context
            override fun onFrameSeen(context: YoloShadowObservationContext) { throw IllegalStateException("seen") }
            override fun onAdmission(context: YoloShadowObservationContext) { throw IllegalStateException("admitted") }
            override fun onResult(context: YoloShadowObservationContext?, snapshot: YoloShadowSnapshot, isFrame: Boolean) {
                throw IllegalStateException("result")
            }
        }
        assertObserverFailureDoesNotBreakRunner(observer)
    }

    @Test fun observerContextCaptureExceptionDoesNotBlockInferenceOrFrameGate() {
        val observer = object : YoloShadowObserver {
            override fun captureContext(): YoloShadowObservationContext? { throw IllegalStateException("capture") }
        }
        assertObserverFailureDoesNotBreakRunner(observer)
    }

    private fun assertObserverFailureDoesNotBreakRunner(observer: YoloShadowObserver) {
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>(); val worker = Executors.newSingleThreadExecutor()
        val released = AtomicInteger(); val detected = AtomicInteger(); val fallbackCalls = AtomicInteger()
        val runner = YoloShadowRunner(Detector({ ready() }, { detected.incrementAndGet(); ready(true) }), stabilizer(),
            { it }, { released.incrementAndGet() }, queue::offer, worker = worker, observer = observer)
        try {
            runner.start(); assertEquals(DetectorStatus.READY, result(queue).result.status)
            worker.submit {}.get(5, TimeUnit.SECONDS)
            repeat(2) { index ->
                fallbackCalls.incrementAndGet() // Independent primary caller continues regardless of diagnostics failure.
                assertTrue(runner.trySubmit(index, 1000L + index * 250))
                assertEquals(ExerciseType.SQUAT, result(queue).result.exerciseType)
                worker.submit {}.get(5, TimeUnit.SECONDS)
            }
            assertEquals(2, detected.get()); assertEquals(2, released.get()); assertEquals(2, fallbackCalls.get())
        } finally { runner.close() }
    }
}
