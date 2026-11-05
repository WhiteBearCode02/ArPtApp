package com.example.arptapp.domain.detection

import org.junit.Assert.*
import org.junit.Test

class YoloOpenCvLinearBoundsTest {
    @Test fun invalidDimensionsAndMismatchedRasterFailBeforeTensorAllocation() {
        for (frame in listOf(Exp02RgbFrame(0, 640, intArrayOf()), Exp02RgbFrame(8193, 640, intArrayOf()),
            Exp02RgbFrame(Int.MAX_VALUE, Int.MAX_VALUE, intArrayOf()), Exp02RgbFrame(3, 2, intArrayOf()))) {
            assertTrue(runCatching { YoloOpenCvLinearRgbPreprocessor().preprocess(frame) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
    @Test fun workerOwnedTensorIsReusedAndNewRgbDoesNotRetainPreviousFrame() {
        val preprocessor = YoloOpenCvLinearRgbPreprocessor()
        val first = preprocessor.preprocess(Exp02RgbFrame(1, 1, intArrayOf(0xff112233.toInt())))
        val buffer = first.tensor
        val second = preprocessor.preprocess(Exp02RgbFrame(1, 1, intArrayOf(0xffaabbcc.toInt())))
        assertSame(buffer, second.tensor)
        assertEquals(170f / 255, second.tensor.getFloat(0), 0f)
        assertEquals(187f / 255, second.tensor.getFloat(4 * 640 * 640), 0f)
        assertEquals(204f / 255, second.tensor.getFloat(8 * 640 * 640), 0f)
        assertEquals(0, second.tensor.position())
    }
    @Test fun legacyExp02ArithmeticRemainsIndependent() {
        val frame = Exp02RgbFrame(17, 13, YoloOpenCvLinearPreprocessorTest.argb(17, 13, "gradient", 0))
        val legacy = Exp02RgbPreprocessor().preprocess(frame)
        val corrected = YoloOpenCvLinearRgbPreprocessor().preprocess(frame)
        assertNotEquals(YoloOpenCvLinearPreprocessorTest.hash(legacy.tensor), YoloOpenCvLinearPreprocessorTest.hash(corrected.tensor))
        val before = legacy.transform as Exp02FrameTransform
        val after = corrected.transform as Exp02FrameTransform
        assertEquals(before.padX, after.padX); assertEquals(before.padY, after.padY)
        assertEquals(before.resizedWidth, after.resizedWidth); assertEquals(before.resizedHeight, after.resizedHeight)
    }
}
