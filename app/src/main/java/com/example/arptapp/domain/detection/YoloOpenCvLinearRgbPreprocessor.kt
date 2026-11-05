package com.example.arptapp.domain.detection

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.round

/**
 * EXP03 uint8 INTER_LINEAR parity, derived from OpenCV 4.13.0 resize.cpp (BSD-3-Clause).
 * Source/attribution and limits: THIRD_PARTY_NOTICES.md and EXP03 preprocessing validation docs.
 *
 * This is NOT ideal Double bilinear and NOT INTER_LINEAR_EXACT: OpenCV first stores half-pixel
 * coordinates as Float, rounds weights to 11-bit coefficients, preserves integer horizontal sums,
 * then uses the uint8 specialization's staged >>4 / multiply-high >>16 / rounded >>2 reduction.
 * Scalar and SIMD forms of that specialization have the SAME integer arithmetic. Do not replace
 * it with a single final round, floor, or floating-point multiply-add.
 *
 * Only resampled pixel arithmetic changes. The existing bounded geometry, 114 pad, RGB/BCHW,
 * FLOAT32 /255 and model-space box transform remain unchanged. EXP02 keeps its original class.
 * Worker-owned tensor is allocated lazily and borrowed only until the next preprocess call.
 */
class YoloOpenCvLinearRgbPreprocessor : YoloPreprocessor<Exp02RgbFrame> {
    private val tensor by lazy(LazyThreadSafetyMode.NONE) {
        ByteBuffer.allocateDirect(4 * 3 * 640 * 640).order(ByteOrder.nativeOrder())
    }
    override fun preprocess(frame: Exp02RgbFrame): YoloPreparedInput {
        val transform = Exp02FrameTransform.create(frame.width, frame.height)
        require(frame.argb.size == frame.width * frame.height)
        tensor.clear()
        repeat(3 * 640 * 640) { tensor.putFloat(114f / 255f) }
        val width = transform.resizedWidth
        val height = transform.resizedHeight
        // OpenCV computes inverse of dst/src, then casts each coordinate to FLOAT before floor.
        val scaleX = 1.0 / (width.toDouble() / frame.width)
        val scaleY = 1.0 / (height.toDouble() / frame.height)
        val x0 = IntArray(width); val x1 = IntArray(width)
        val a0 = IntArray(width); val a1 = IntArray(width)
        for (x in 0 until width) {
            val coordinate = ((x + .5) * scaleX - .5).toFloat()
            var source = floor(coordinate).toInt()
            var fraction = coordinate - source
            if (source < 0) { source = 0; fraction = 0f }
            if (source >= frame.width - 1) { source = frame.width - 1; fraction = 0f }
            x0[x] = source; x1[x] = (source + 1).coerceAtMost(frame.width - 1)
            a0[x] = coefficient(1f - fraction); a1[x] = coefficient(fraction)
        }
        for (y in 0 until height) {
            val coordinate = ((y + .5) * scaleY - .5).toFloat()
            val source = floor(coordinate).toInt()
            // Unlike X, Y weights are NOT zeroed at an edge. The source rows are replicated.
            val fraction = coordinate - source
            val b0 = coefficient(1f - fraction); val b1 = coefficient(fraction)
            val row0 = source.coerceIn(0, frame.height - 1) * frame.width
            val row1 = (source + 1).coerceIn(0, frame.height - 1) * frame.width
            for (x in 0 until width) {
                val p00 = frame.argb[row0 + x0[x]]; val p01 = frame.argb[row0 + x1[x]]
                val p10 = frame.argb[row1 + x0[x]]; val p11 = frame.argb[row1 + x1[x]]
                for (channel in 0..2) {
                    val shift = 16 - channel * 8
                    val h0 = ((p00 ushr shift) and 255) * a0[x] + ((p01 ushr shift) and 255) * a1[x]
                    val h1 = ((p10 ushr shift) and 255) * a0[x] + ((p11 ushr shift) and 255) * a1[x]
                    // All products fit Int: 2048 * (255 * 2048 >> 4) <= 66,846,720.
                    val value = ((((b0 * (h0 shr 4)) shr 16) + ((b1 * (h1 shr 4)) shr 16) + 2) shr 2)
                    check(value in 0..255)
                    val index = channel * 640 * 640 + (y + transform.padY) * 640 + x + transform.padX
                    tensor.putFloat(index * 4, value / 255f)
                }
            }
        }
        tensor.rewind()
        return YoloPreparedInput(tensor, transform)
    }
    // cvRound/saturate_cast<short> on these nonnegative coefficients: ties to even, no saturation.
    private fun coefficient(weight: Float): Int = round(weight * 2048f).toInt()
}
