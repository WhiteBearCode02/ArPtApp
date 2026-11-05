package com.example.arptapp.domain.detection

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.roundToInt

/** Complete synthetic output hashes from actual cv2 4.13.0, not model/media/contract evidence. */
@RunWith(Parameterized::class)
class YoloOpenCvLinearPreprocessorTest(private val vector: Vector) {
    data class Vector(val id: String, val width: Int, val height: Int, val pattern: String, val seed: Int,
        val resizedWidth: Int, val resizedHeight: Int, val padX: Int, val padY: Int,
        val resizedRgbSha256: String, val inputTensorSha256: String, val probes: List<List<Int>>,
        val defaultVsUnoptimizedMismatches: Int) {
        override fun toString() = id
    }
    private data class Fixture(val schemaVersion: Int, val cases: List<Vector>)
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0}") fun vectors(): List<Array<Any>> {
            val json = requireNotNull(YoloOpenCvLinearPreprocessorTest::class.java.getResourceAsStream("/exp03-opencv-4.13-linear.json"))
                .bufferedReader().use { it.readText() }
            val fixture = Gson().fromJson(json, Fixture::class.java)
            require(fixture.schemaVersion == 1 && fixture.cases.size == 64)
            return fixture.cases.map { arrayOf(it) }
        }
        fun argb(width: Int, height: Int, pattern: String, seed: Int): IntArray = IntArray(width * height) { index ->
            val x = index % width; val y = index / width
            fun channel(c: Int): Int = when (pattern) {
                "gradient" -> (37 * x + 17 * y + 53 * c + 11) and 255
                "checkerboard" -> ((x + y + c) % 2) * 255
                "edge" -> ((if (x < width / 2) 17 + 41 * c else 241 - 37 * c) + y % 3) and 255
                "counter_random" -> {
                    var value = ((index * 3 + c) xor seed)
                    value = (value xor (value ushr 16)) * 0x7feb352d
                    value = (value xor (value ushr 15)) * 0x846ca68b.toInt()
                    (value xor (value ushr 16)) and 255
                }
                else -> error("Unknown deterministic pattern")
            }
            (0xff shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
        }
        fun hash(buffer: ByteBuffer) = MessageDigest.getInstance("SHA-256").apply { update(buffer.asReadOnlyBuffer()) }
            .digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    @Test fun everyResizedRgbByteAndEveryTensorBitMatchesActualOpenCv() {
        val prepared = YoloOpenCvLinearRgbPreprocessor().preprocess(Exp02RgbFrame(vector.width, vector.height,
            argb(vector.width, vector.height, vector.pattern, vector.seed)))
        val transform = prepared.transform as Exp02FrameTransform
        assertEquals(vector.resizedWidth, transform.resizedWidth); assertEquals(vector.resizedHeight, transform.resizedHeight)
        assertEquals(vector.padX, transform.padX); assertEquals(vector.padY, transform.padY)
        val rgb = ByteArray(vector.resizedWidth * vector.resizedHeight * 3) { index ->
            val pixel = index / 3; val x = pixel % vector.resizedWidth; val y = pixel / vector.resizedWidth
            (prepared.tensor.getFloat(4 * ((index % 3) * 640 * 640 + (y + vector.padY) * 640 + x + vector.padX)) * 255).roundToInt().toByte()
        }
        vector.probes.forEach { assertEquals("${vector.id} probe ${it[0]}", it[1], rgb[it[0]].toInt() and 255) }
        assertEquals("${vector.id} resized RGB", vector.resizedRgbSha256, hash(ByteBuffer.wrap(rgb)))
        assertEquals("${vector.id} full FLOAT32 BCHW /255", vector.inputTensorSha256, hash(prepared.tensor))
        assertEquals("Original OpenCV optimization diagnostic", 0, vector.defaultVsUnoptimizedMismatches)
    }
}
