package com.example.arptapp.domain.detection

import com.example.arptapp.domain.classifier.ExerciseType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class YoloShadowRunnerTest {
    private fun ready() = ExerciseDetectionResult(DetectorStatus.READY, DetectionSource.YOLO,
        ExerciseDetection(ExerciseType.SQUAT, .9f, DetectionSource.YOLO),
        contractVerification = ExportContractVerification.VERIFIED, shadowRuntimeEnabled = true)
    private class Detector(val initializeAction: () -> ExerciseDetectionResult,
                           val detectAction: () -> ExerciseDetectionResult) : ExerciseDetector<Int> {
        val closed = CountDownLatch(1)
        override fun initialize() = initializeAction()
        override fun detect(frame: Int) = detectAction()
        override fun close() { closed.countDown() }
    }
    private fun queueResult(queue: LinkedBlockingQueue<YoloShadowSnapshot>) = requireNotNull(queue.poll(5, TimeUnit.SECONDS))
    @Test fun fourFpsGateHasNoBacklogAndRejectsClockReversal() {
        val gate = ShadowFrameGate()
        assertTrue(gate.tryAcquire(1000))
        assertFalse(gate.tryAcquire(2000)) // no second in-flight task, even after interval
        gate.release()
        assertFalse(gate.tryAcquire(900))
        assertFalse(gate.tryAcquire(1249))
        assertTrue(gate.tryAcquire(1250))
        gate.release(); gate.disable()
        assertFalse(gate.tryAcquire(2000))
    }
    @Test fun unavailableContractDoesNotCopyFramesOrRunInference() {
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>()
        val copies = AtomicInteger(); val detects = AtomicInteger()
        val detector = Detector({ ExerciseDetectionResult(DetectorStatus.CONTRACT_NOT_VERIFIED, DetectionSource.YOLO) }, { detects.incrementAndGet(); ready() })
        val runner = YoloShadowRunner(detector, YoloExerciseStabilizer(YoloStabilizationPolicy(null)),
            { copies.incrementAndGet(); it }, {}, queue::offer)
        try {
            runner.start(); assertEquals(DetectorStatus.CONTRACT_NOT_VERIFIED, queueResult(queue).result.status)
            repeat(20) { assertFalse(runner.trySubmit(1, it * 250L)) }
            assertEquals(0, copies.get()); assertEquals(0, detects.get())
        } finally { runner.close() }
        assertTrue(detector.closed.await(5, TimeUnit.SECONDS))
    }
    @Test fun admittedFrameIsOwnedOnceAndSecondFrameIsSkipped() {
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>()
        val entered = CountDownLatch(1); val finish = CountDownLatch(1); val released = CountDownLatch(1)
        val copies = AtomicInteger(); val detects = AtomicInteger()
        val detector = Detector({ ready() }, { detects.incrementAndGet(); entered.countDown(); finish.await(5, TimeUnit.SECONDS); ready() })
        val runner = YoloShadowRunner(detector, YoloExerciseStabilizer(YoloStabilizationPolicy(.7f)),
            { copies.incrementAndGet(); it }, { released.countDown() }, queue::offer,
            initializationTimeoutMs = 10_000, inferenceTimeoutMs = 10_000)
        try {
            runner.start(); queueResult(queue)
            assertTrue(runner.trySubmit(1, 1000)); assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertFalse(runner.trySubmit(2, 2000))
            finish.countDown(); assertEquals(DetectorStatus.READY, queueResult(queue).result.status)
            assertTrue(released.await(5, TimeUnit.SECONDS))
            assertEquals(1, copies.get()); assertEquals(1, detects.get())
        } finally { finish.countDown(); runner.close() }
    }
    @Test fun initializationFinallyCannotReleaseAnAlreadyAdmittedFrameGate() {
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>()
        val firstReady = AtomicBoolean()
        val admitted = AtomicBoolean()
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val detector = Detector({ ready() }, { entered.countDown(); finish.await(5, TimeUnit.SECONDS); ready() })
        lateinit var runner: YoloShadowRunner<Int>
        runner = YoloShadowRunner(detector, YoloExerciseStabilizer(YoloStabilizationPolicy(.7f)),
            { it }, {}, { snapshot ->
                if (snapshot.result.status == DetectorStatus.READY && firstReady.compareAndSet(false, true))
                    admitted.set(runner.trySubmit(1, 1000))
                queue.offer(snapshot)
            }, initializationTimeoutMs = 10_000, inferenceTimeoutMs = 10_000)
        try {
            runner.start(); queueResult(queue)
            assertTrue(admitted.get()); assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertFalse("Initialization must not release another operation's gate", runner.trySubmit(2, 2000))
        } finally { finish.countDown(); runner.close() }
    }
    @Test fun timeoutDoesNotCloseNativeRuntimeConcurrentlyAndLateResultIsDiscarded() {
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>()
        val finish = CountDownLatch(1); val entered = CountDownLatch(1); val released = CountDownLatch(1)
        val detector = Detector({ ready() }, { entered.countDown(); finish.await(5, TimeUnit.SECONDS); ready() })
        val runner = YoloShadowRunner(detector, YoloExerciseStabilizer(YoloStabilizationPolicy(.7f)),
            { it }, { released.countDown() }, queue::offer, inferenceTimeoutMs = 50)
        try {
            runner.start(); queueResult(queue)
            runner.trySubmit(1, 1000); assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(DetectorStatus.TIMED_OUT, queueResult(queue).result.status)
            assertEquals(1L, detector.closed.count)
            assertFalse(runner.trySubmit(2, 2000))
            finish.countDown(); assertTrue(released.await(5, TimeUnit.SECONDS))
            assertTrue(detector.closed.await(5, TimeUnit.SECONDS))
            assertNull(queue.poll(100, TimeUnit.MILLISECONDS))
        } finally { finish.countDown(); runner.close() }
    }
    @Test fun initializationTimeoutDoesNotBlockFallbackCaller() {
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>()
        val finish = CountDownLatch(1)
        val detector = Detector({ finish.await(5, TimeUnit.SECONDS); ready() }, { ready() })
        val runner = YoloShadowRunner(detector, YoloExerciseStabilizer(YoloStabilizationPolicy(null)),
            { it }, {}, queue::offer, initializationTimeoutMs = 50)
        try {
            runner.start()
            assertFalse(runner.trySubmit(1, 1000))
            assertEquals(DetectorStatus.TIMED_OUT, queueResult(queue).result.status)
            assertEquals(1L, detector.closed.count)
        } finally { finish.countDown(); runner.close() }
    }
    @Test fun lensChangeDropsObsoleteFrameWithoutDroppingInitializationStatus() {
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>()
        val initFinish = CountDownLatch(1); val frameFinish = CountDownLatch(1); val entered = CountDownLatch(1); val released = CountDownLatch(1)
        val detector = Detector({ initFinish.await(5, TimeUnit.SECONDS); ready() },
            { entered.countDown(); frameFinish.await(5, TimeUnit.SECONDS); ready() })
        val runner = YoloShadowRunner(detector, YoloExerciseStabilizer(YoloStabilizationPolicy(.7f)),
            { it }, { released.countDown() }, queue::offer, initializationTimeoutMs = 10_000, inferenceTimeoutMs = 10_000)
        try {
            runner.start(); runner.invalidate(); initFinish.countDown()
            assertEquals(DetectorStatus.READY, queueResult(queue).result.status)
            assertTrue(runner.trySubmit(1, 1000)); assertTrue(entered.await(5, TimeUnit.SECONDS))
            runner.invalidate()
            assertEquals(ExerciseType.UNKNOWN, queueResult(queue).result.exerciseType)
            frameFinish.countDown()
            assertTrue(released.await(5, TimeUnit.SECONDS)); assertNull(queue.poll(100, TimeUnit.MILLISECONDS))
        } finally { initFinish.countDown(); frameFinish.countDown(); runner.close() }
    }
    @Test fun invalidationClearsStableEvidenceAndRejectsAnAlreadyQueuedOldUiResult() {
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>()
        val releases = LinkedBlockingQueue<Int>()
        val worker = Executors.newSingleThreadExecutor()
        val detector = Detector({ ready() }, { ready() })
        val runner = YoloShadowRunner(detector, YoloExerciseStabilizer(YoloStabilizationPolicy(.7f)),
            { it }, { releases.offer(it) }, queue::offer, worker = worker)
        try {
            runner.start(); queueResult(queue)
            worker.submit {}.get(5, TimeUnit.SECONDS)
            var old = YoloShadowSnapshot(ready())
            repeat(4) { index ->
                assertTrue(runner.trySubmit(index, 1000L + index * 250))
                old = queueResult(queue)
                assertNotNull(releases.poll(5, TimeUnit.SECONDS))
                worker.submit {}.get(5, TimeUnit.SECONDS)
            }
            assertEquals(ExerciseType.SQUAT, old.stableExercise)
            runner.invalidate()
            val cleared = queueResult(queue)
            assertEquals(DetectorStatus.READY, cleared.result.status)
            assertEquals(ExerciseType.UNKNOWN, cleared.result.exerciseType)
            assertEquals(ExerciseType.UNKNOWN, cleared.stableExercise)
            assertNull(cleared.result.timings)
            assertFalse(runner.isCurrent(old))
            assertTrue(runner.isCurrent(cleared))
            assertTrue(runner.trySubmit(5, 2000))
            assertEquals(ExerciseType.UNKNOWN, queueResult(queue).stableExercise)
        } finally { runner.close() }
    }
    @Test fun missingModelAvailabilityIsNotDroppedByLensInvalidation() {
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>()
        val detector = Detector({ ExerciseDetectionResult(DetectorStatus.MODEL_MISSING, DetectionSource.YOLO) }, { ready() })
        val runner = YoloShadowRunner(detector, YoloExerciseStabilizer(YoloStabilizationPolicy(.7f)),
            { it }, {}, queue::offer)
        try {
            runner.start()
            val unavailable = queueResult(queue)
            runner.invalidate()
            assertEquals(DetectorStatus.MODEL_MISSING, unavailable.result.status)
            assertTrue(runner.isCurrent(unavailable))
            assertFalse(runner.trySubmit(1, 1000))
            assertNull(queue.poll(100, TimeUnit.MILLISECONDS))
        } finally { runner.close() }
        assertFalse(runner.isCurrent(YoloShadowSnapshot(ready())))
    }
    @Test fun closeIsNonBlockingAndDoesNotPublishLateResult() {
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>()
        val finish = CountDownLatch(1); val entered = CountDownLatch(1); val released = CountDownLatch(1)
        val detector = Detector({ ready() }, { entered.countDown(); finish.await(5, TimeUnit.SECONDS); ready() })
        val runner = YoloShadowRunner(detector, YoloExerciseStabilizer(YoloStabilizationPolicy(.7f)),
            { it }, { released.countDown() }, queue::offer, inferenceTimeoutMs = 10_000)
        try {
            runner.start(); queueResult(queue)
            runner.trySubmit(1, 1000); assertTrue(entered.await(5, TimeUnit.SECONDS))
            runner.close(); runner.close()
            assertEquals(1L, detector.closed.count)
            finish.countDown(); assertTrue(released.await(5, TimeUnit.SECONDS))
            assertTrue(detector.closed.await(5, TimeUnit.SECONDS))
            assertNull(queue.poll(100, TimeUnit.MILLISECONDS))
        } finally { finish.countDown(); runner.close() }
    }
}
