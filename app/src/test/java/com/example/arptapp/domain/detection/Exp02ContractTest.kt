package com.example.arptapp.domain.detection

import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.session.SessionExerciseLock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Exact-source Python fixtures use synthetic pixels/tensors only. These tests do NOT invoke a
 * native Interpreter or prove model accuracy. Resampled uint8 pixels permit ONE interpolation
 * byte of error; padding/native-size values, colours and BCHW placement have stricter checks.
 */
class Exp02ContractTest {
    private val fixture: JsonObject by lazy {
        val resource = requireNotNull(javaClass.classLoader?.getResourceAsStream("yolo/exp02-predict-golden.json"))
        resource.bufferedReader(Charsets.UTF_8).use { Json.parseToJsonElement(it.readText()).jsonObject }
    }
    private val tolerances get() = fixture.getValue("tolerances").jsonObject
    private val decoder = Exp02OutputDecoder()
    private fun JsonObject.integer(key: String) = getValue(key).jsonPrimitive.int
    private fun JsonObject.number(key: String) = getValue(key).jsonPrimitive.float
    private fun nativeBuffer() = ByteBuffer.allocateDirect(4 * 6 * 8400).order(ByteOrder.nativeOrder())
    private fun rawTensor(columns: JsonArray): ByteBuffer = nativeBuffer().also { buffer ->
        for (element in columns) {
            val column = element.jsonObject
            column.getValue("values").jsonArray.forEachIndexed { channel, value ->
                buffer.putFloat((channel * 8400 + column.integer("index")) * 4, value.jsonPrimitive.float)
            }
        }
    }
    private fun rawTensor(vararg rows: Pair<Int, FloatArray>): ByteBuffer = nativeBuffer().also { buffer ->
        for ((index, values) in rows) values.forEachIndexed { channel, value ->
            buffer.putFloat((channel * 8400 + index) * 4, value)
        }
    }
    private fun syntheticFrame(width: Int, height: Int): Exp02RgbFrame {
        val pixels = IntArray(width * height) { index ->
            val x = index % width; val y = index / width
            val r = (37 * x + 17 * y + 11) % 256
            val g = (13 * x + 29 * y + 53) % 256
            val b = (7 * x + 43 * y + 101) % 256
            (255 shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Exp02RgbFrame(width, height, pixels)
    }
    private fun assertBox(expected: JsonArray, actual: DetectionBox) {
        val epsilon = tolerances.number("bboxPixels")
        listOf(actual.left, actual.top, actual.right, actual.bottom).forEachIndexed { i, value ->
            assertEquals("bbox channel $i", expected[i].jsonPrimitive.float, value, epsilon)
        }
    }
    private fun assertBox(expected: DetectionBox, actual: DetectionBox, epsilon: Float = 1e-4f) {
        assertEquals(expected.left, actual.left, epsilon); assertEquals(expected.top, actual.top, epsilon)
        assertEquals(expected.right, actual.right, epsilon); assertEquals(expected.bottom, actual.bottom, epsilon)
    }
    private fun assertInvalid(action: () -> Unit) {
        try { action(); fail("Invalid input was accepted") }
        catch (_: IllegalArgumentException) { /* Explicit fail-closed contract. */ }
    }
    private fun verifyPreprocess(name: String) {
        val expected = fixture.getValue("preprocess").jsonArray.map { it.jsonObject }
            .single { it.getValue("name").jsonPrimitive.content == name }
        val prepared = Exp02RgbPreprocessor().preprocess(syntheticFrame(expected.integer("width"), expected.integer("height")))
        assertTrue(prepared.tensor.isDirect)
        assertEquals(ByteOrder.nativeOrder(), prepared.tensor.order())
        assertEquals(4 * 3 * 640 * 640, prepared.tensor.remaining())
        val transform = prepared.transform as Exp02FrameTransform
        assertEquals(expected.integer("resizedWidth"), transform.resizedWidth)
        assertEquals(expected.integer("resizedHeight"), transform.resizedHeight)
        assertEquals(expected.integer("padLeft"), transform.padX)
        assertEquals(expected.integer("padTop"), transform.padY)
        assertEquals(expected.integer("padRight"), 640 - transform.padX - transform.resizedWidth)
        assertEquals(expected.integer("padBottom"), 640 - transform.padY - transform.resizedHeight)
        assertEquals(expected.number("gainX"), transform.scaleX, 1e-6f)
        assertEquals(expected.number("gainY"), transform.scaleY, 1e-6f)
        for (sample in expected.getValue("samples").jsonArray) {
            val row = sample.jsonArray
            val index = row[0].jsonPrimitive.int
            val isExact = row[2].jsonPrimitive.boolean
            val epsilon = tolerances.number(if (isExact) "exactTensor" else "resampledTensor")
            assertEquals("$name tensor[$index] exact=$isExact", row[1].jsonPrimitive.float,
                prepared.tensor.getFloat(index * 4), epsilon)
        }
    }
    private fun verifyPostprocess(name: String) {
        val expected = fixture.getValue("postprocess").jsonArray.map { it.jsonObject }
            .single { it.getValue("name").jsonPrimitive.content == name }
        val transform = Exp02FrameTransform.create(expected.integer("width"), expected.integer("height"))
        val decoded = decoder.decode(rawTensor(expected.getValue("columns").jsonArray), transform)
        val actual = Exp02PostProcessor(expected.number("confidenceThreshold"), expected.number("iouThreshold")).process(decoded)
        val rows = expected.getValue("expected").jsonArray
        assertEquals(rows.size, actual.size)
        rows.forEachIndexed { index, row ->
            val detection = actual[index]; val reference = row.jsonObject
            assertEquals(YoloModelPolicy.classMapping.getValue(reference.integer("classId")), detection.exerciseType)
            assertEquals(DetectionSource.YOLO, detection.source)
            assertEquals(reference.number("confidence"), requireNotNull(detection.confidence), tolerances.number("confidence"))
            assertBox(reference.getValue("sourceBox").jsonArray, requireNotNull(detection.boundingBox))
        }
    }
    private fun candidate(index: Int = 0, classId: Int = 0, confidence: Float = .9f,
                          box: DetectionBox = DetectionBox(100f, 100f, 200f, 200f)) =
        YoloDecodedCandidate(index, classId, confidence, box)
    private fun decoded(vararg candidates: YoloDecodedCandidate) =
        YoloDecodedOutput(candidates.toList(), Exp02FrameTransform.create(640, 640))

    @Test fun goldenProvenanceIsExactVersionAndExplicitlyNotModelInference() {
        assertEquals("8.4.176", fixture.getValue("ultralyticsVersion").jsonPrimitive.content)
        assertEquals("15f4bbac5c69fd9a5a9395f5957a7ce561e3eb4620699a9c2ff11ed38ec7d34c",
            fixture.getValue("wheelSha256").jsonPrimitive.content)
        assertFalse(fixture.getValue("modelInferenceExecuted").jsonPrimitive.boolean)
        assertFalse(fixture.getValue("originalDatasetMediaUsed").jsonPrimitive.boolean)
        assertEquals(5, fixture.getValue("sourceSha256").jsonObject.size)
    }
    @Test fun landscapePreprocessingMatchesPythonWithinDocumentedUint8Tolerance() = verifyPreprocess("landscape")
    @Test fun portraitPreprocessingMatchesPythonWithinDocumentedUint8Tolerance() = verifyPreprocess("portrait")
    @Test fun squarePreprocessingMatchesPythonWithinDocumentedUint8Tolerance() = verifyPreprocess("square")
    @Test fun oddPaddingPreprocessingMatchesPythonWithinDocumentedUint8Tolerance() = verifyPreprocess("odd-padding")
    @Test fun downscalePreprocessingMatchesPythonWithinDocumentedUint8Tolerance() = verifyPreprocess("downscale")
    @Test fun nativeSizePreprocessingHasStrictPythonTensorEquality() = verifyPreprocess("native")

    @Test fun logicalRgbChannelsDoNotDependOnArgbRawByteOrderOrAlpha() {
        val tensor = Exp02RgbPreprocessor().preprocess(Exp02RgbFrame(1, 1, intArrayOf(0x12345678))).tensor
        assertEquals(0x34 / 255f, tensor.getFloat(0), 1e-6f)
        assertEquals(0x56 / 255f, tensor.getFloat(640 * 640 * 4), 1e-6f)
        assertEquals(0x78 / 255f, tensor.getFloat(2 * 640 * 640 * 4), 1e-6f)
        assertEquals(tensor.getFloat(0), tensor.getFloat((640 * 640 - 1) * 4), 1e-6f)
    }
    @Test fun normalizationPreservesZeroAndOneWithoutSecondScaling() {
        val pixels = IntArray(640 * 640) { -1 }; pixels[0] = 0xff000000.toInt()
        val tensor = Exp02RgbPreprocessor().preprocess(Exp02RgbFrame(640, 640, pixels)).tensor
        for (channel in 0..2) {
            assertEquals(0f, tensor.getFloat(channel * 640 * 640 * 4), 0f)
            assertEquals(1f, tensor.getFloat((channel * 640 * 640 + 1) * 4), 0f)
        }
    }
    @Test fun tensorIsDirectNativeOrderBchwAndStartsAtPositionZero() {
        val prepared = Exp02RgbPreprocessor().preprocess(syntheticFrame(3, 5))
        assertTrue(prepared.tensor.isDirect); assertEquals(0, prepared.tensor.position())
        assertEquals(4 * 3 * 640 * 640, prepared.tensor.capacity())
        assertEquals(ByteOrder.nativeOrder(), prepared.tensor.order())
    }
    @Test fun repeatedPreprocessingRewindsAndOverwritesWorkerOwnedTensor() {
        val preprocessor = Exp02RgbPreprocessor()
        preprocessor.preprocess(Exp02RgbFrame(1, 1, intArrayOf(-1))).tensor.position(100)
        val black = preprocessor.preprocess(Exp02RgbFrame(1, 1, intArrayOf(0xff000000.toInt())))
        assertEquals(0, black.tensor.position()); assertEquals(0f, black.tensor.getFloat(0), 0f)
    }
    @Test fun malformedFramesAreRejectedBeforeArrayAccess() {
        val preprocessor = Exp02RgbPreprocessor()
        assertInvalid { preprocessor.preprocess(Exp02RgbFrame(2, 2, intArrayOf(1))) }
        assertInvalid { Exp02FrameTransform.create(0, 100) }
        assertInvalid { Exp02FrameTransform.create(100, -1) }
        assertInvalid { Exp02FrameTransform.create(8193, 10) }
        assertInvalid { Exp02FrameTransform.create(8192, 8192) }
    }

    @Test fun transformUsesRoundedPerAxisGainsAndAsymmetricOddPadding() {
        val transform = Exp02FrameTransform.create(7, 13)
        assertEquals(345, transform.resizedWidth); assertEquals(640, transform.resizedHeight)
        assertEquals(147, transform.padX); assertEquals(0, transform.padY)
        assertEquals(148, 640 - transform.resizedWidth - transform.padX)
        assertNotEquals(transform.scaleX, transform.scaleY)
    }
    @Test fun transformMatchesPythonTiesToEvenRatherThanRoundToInt() {
        // 1 * 640 / 256 = 2.5 -> Python round=2, while roundToInt would incorrectly return 3.
        val transform = Exp02FrameTransform.create(256, 1)
        assertEquals(640, transform.resizedWidth); assertEquals(2, transform.resizedHeight)
        assertEquals(319, transform.padY)
    }
    @Test fun originalAndModelCoordinatesRoundTripForAllAspectRatios() {
        for ((w, h) in listOf(5 to 3, 3 to 5, 7 to 7, 7 to 13, 961 to 721)) {
            val transform = Exp02FrameTransform.create(w, h)
            val original = DetectionBox(w * .15f, h * .2f, w * .85f, h * .9f)
            assertBox(original, transform.modelToOriginal(transform.originalToModel(original)), .0002f)
        }
    }
    @Test fun inverseTransformClipsPaddingAndOutOfFrameCoordinates() {
        val transform = Exp02FrameTransform.create(7, 13)
        assertBox(DetectionBox(0f, 0f, 7f, 13f), transform.modelToOriginal(DetectionBox(-50f, -50f, 700f, 700f)))
    }

    @Test fun decoderMatchesExactSourceDenormalizedBoxesClassAndConfidence() {
        val decode = fixture.getValue("decode").jsonObject
        val actual = decoder.decode(rawTensor(decode.getValue("columns").jsonArray), Exp02FrameTransform.create(640, 640))
        val expected = decode.getValue("expected").jsonArray
        assertEquals(expected.size, actual.candidates.size)
        for (element in expected) {
            val row = element.jsonObject
            val candidate = actual.candidates.single { it.anchorIndex == row.integer("index") }
            assertEquals(row.integer("classId"), candidate.classId)
            assertEquals(row.number("confidence"), candidate.confidence, tolerances.number("confidence"))
            assertBox(row.getValue("modelBox").jsonArray, candidate.modelBox)
        }
    }
    @Test fun decoderUsesBestClassAndDoesNotMultiplyObjectnessOrApplySigmoidAgain() {
        val output = rawTensor(0 to floatArrayOf(.5f, .5f, .2f, .2f, .2f, .8f))
        val result = decoder.decode(output, Exp02FrameTransform.create(640, 640)).candidates.single()
        assertEquals(1, result.classId); assertEquals(.8f, result.confidence, 0f)
        assertBox(DetectionBox(256f, 256f, 384f, 384f), result.modelBox)
    }
    @Test fun tiedClassScoresChooseFirstClassAsTorchMaxDoes() {
        val output = rawTensor(8399 to floatArrayOf(.5f, .5f, .2f, .2f, .7f, .7f))
        assertEquals(0, decoder.decode(output, Exp02FrameTransform.create(640, 640)).candidates.single().classId)
    }
    @Test fun allZeroOutputHasNoCandidatesRatherThanInventingSquat() {
        assertTrue(decoder.decode(nativeBuffer(), Exp02FrameTransform.create(640, 640)).candidates.isEmpty())
    }
    @Test fun nonFiniteOrOutOfRangeScoresFailClosedIncludingOtherwiseEmptyAnchors() {
        for (score in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f)) {
            assertInvalid { decoder.decode(rawTensor(17 to floatArrayOf(0f, 0f, 0f, 0f, score, 0f)), Exp02FrameTransform.create(640, 640)) }
        }
    }
    @Test fun nonFiniteCoordinatesAndOverflowFailClosed() {
        for (value in listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.MAX_VALUE)) {
            assertInvalid { decoder.decode(rawTensor(0 to floatArrayOf(value, .5f, .2f, .2f, .9f, .1f)), Exp02FrameTransform.create(640, 640)) }
        }
    }
    @Test fun zeroAndNegativeWidthAreDiscardedRatherThanForcedIntoABox() {
        for (width in listOf(0f, -.1f)) assertTrue(decoder.decode(
            rawTensor(0 to floatArrayOf(.5f, .5f, width, .2f, .9f, .1f)), Exp02FrameTransform.create(640, 640)).candidates.isEmpty())
    }
    @Test fun wrongTensorByteCountAndByteOrderAreRejected() {
        assertInvalid { decoder.decode(ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()), Exp02FrameTransform.create(640, 640)) }
        val otherOrder = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        assertInvalid { decoder.decode(nativeBuffer().order(otherOrder), Exp02FrameTransform.create(640, 640)) }
        assertInvalid { decoder.decode(nativeBuffer().also { it.position(4) }, Exp02FrameTransform.create(640, 640)) }
    }

    @Test fun overlappingSameClassAndDifferentClassMatchPythonNms() = verifyPostprocess("same-class-overlap-and-different-class")
    @Test fun strictConfidenceThresholdMatchesPython() = verifyPostprocess("strict-confidence-threshold")
    @Test fun nmsRunsBeforeInverseTransformAndClippingAsInPython() = verifyPostprocess("nms-before-source-clipping")
    @Test fun oddPaddingRescaleAndSourceClippingMatchPython() = verifyPostprocess("odd-padding-rescale-and-clip")
    @Test fun noDetectionMatchesPythonWithoutForcedSupportedClass() = verifyPostprocess("no-detection")
    @Test fun nmsSuppressesOnlyWhenIouStrictlyExceedsThreshold() {
        val output = decoded(candidate(), candidate(1, confidence = .8f))
        assertEquals(2, Exp02PostProcessor(iouThreshold = 1f).process(output).size)
        assertEquals(1, Exp02PostProcessor(iouThreshold = .999f).process(output).size)
    }
    @Test fun confidenceThresholdIsStrictNotInclusive() {
        val output = decoded(candidate(confidence = .25f), candidate(1, confidence = .2501f,
            box = DetectionBox(300f, 300f, 350f, 350f)))
        val detection = Exp02PostProcessor(confidenceThreshold = .25f).process(output).single()
        assertEquals(.2501f, requireNotNull(detection.confidence), 0f)
    }
    @Test fun nmsKeepsOverlappingDifferentClasses() {
        val actual = Exp02PostProcessor().process(decoded(candidate(), candidate(1, 1, .85f)))
        assertEquals(listOf(ExerciseType.SQUAT, ExerciseType.SHOULDER_PRESS), actual.map { it.exerciseType })
    }
    @Test fun maxDetectionsCapsConfidenceSortedResults() {
        val output = decoded(candidate(0, confidence = .7f),
            candidate(1, confidence = .9f, box = DetectionBox(300f, 300f, 350f, 350f)),
            candidate(2, confidence = .8f, box = DetectionBox(500f, 500f, 550f, 550f)))
        assertEquals(listOf(.9f, .8f), Exp02PostProcessor(maxDetections = 2).process(output).map { it.confidence })
    }
    @Test fun equalConfidenceTiesAreDeterministicByAnchorButNotClaimedAsCrossRuntimeOrdering() {
        val higherAnchor = candidate(9, box = DetectionBox(110f, 100f, 210f, 200f))
        val lowerAnchor = candidate(1)
        assertBox(lowerAnchor.modelBox, requireNotNull(Exp02PostProcessor().process(decoded(higherAnchor, lowerAnchor)).single().boundingBox))
    }
    @Test fun degenerateAfterSourceClippingIsAnAdditionalSafetyAbstention() {
        assertTrue(Exp02PostProcessor().process(decoded(candidate(box = DetectionBox(-100f, -100f, -1f, -1f)))).isEmpty())
    }
    @Test fun malformedPostprocessCandidateFailsClosed() {
        for (bad in listOf(candidate(classId = 2), candidate(confidence = Float.NaN),
            candidate(confidence = 2f), candidate(box = DetectionBox(1f, 1f, 1f, 2f)))) {
            assertInvalid { Exp02PostProcessor().process(decoded(bad)) }
        }
    }
    @Test fun invalidPolicyCannotBeSilentlyAccepted() {
        assertInvalid { Exp02PostProcessor(confidenceThreshold = Float.NaN) }
        assertInvalid { Exp02PostProcessor(iouThreshold = -1f) }
        assertInvalid { Exp02PostProcessor(maxDetections = 0) }
        assertInvalid { Exp02PostProcessor(maxDetections = 301) }
    }
    @Test fun engineeringRawConfidenceIsNotEnoughForStableAcceptance() {
        val raw = Exp02PostProcessor().process(decoded(candidate(confidence = .2f))).single()
        val stabilizer = YoloExerciseStabilizer(YoloStabilizationPolicy(Exp02DetectionPolicy.APP_ENGINEERING_INITIAL_STABLE_CONFIDENCE))
        repeat(8) { assertEquals(ExerciseType.UNKNOWN, stabilizer.observe(raw, it * 250L)) }
    }
    @Test fun decodedShadowCandidateCannotMutateLandmarkLockedSession() {
        val lock = SessionExerciseLock(); assertTrue(lock.observe(ExerciseType.SQUAT))
        val shadow = Exp02PostProcessor().process(decoded(candidate(classId = 1))).single()
        val stabilizer = YoloExerciseStabilizer(YoloStabilizationPolicy(.6f))
        var stable = ExerciseType.UNKNOWN
        repeat(5) { stable = stabilizer.observe(shadow, it * 250L) }
        assertEquals(ExerciseType.SHOULDER_PRESS, stable)
        assertEquals(ExerciseType.SQUAT, lock.sessionExerciseType)
    }
}
