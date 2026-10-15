package com.example.arptapp.domain.detection

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

/** Logical Android ARGB integers, not raw Bitmap byte order; only a transient camera input. */
data class Exp02RgbFrame(val width: Int, val height: Int, val argb: IntArray)

/** Static LiteRT PREDICT LetterBox geometry from exact Ultralytics 8.4.176, not the historical val loader. */
class Exp02FrameTransform private constructor(
    override val originalWidth: Int, override val originalHeight: Int,
    val resizedWidth: Int, val resizedHeight: Int, val padX: Int, val padY: Int
) : YoloFrameTransform {
    override val modelWidth = 640
    override val modelHeight = 640
    // scale_boxes in this version uses each ROUNDED side, not the ideal minimum ratio.
    val scaleX: Float get() = resizedWidth.toFloat() / originalWidth
    val scaleY: Float get() = resizedHeight.toFloat() / originalHeight
    override fun originalToModel(box: DetectionBox) = DetectionBox(
        box.left * scaleX + padX, box.top * scaleY + padY,
        box.right * scaleX + padX, box.bottom * scaleY + padY)
    override fun modelToOriginal(box: DetectionBox) = DetectionBox(
        ((box.left - padX) / scaleX).coerceIn(0f, originalWidth.toFloat()),
        ((box.top - padY) / scaleY).coerceIn(0f, originalHeight.toFloat()),
        ((box.right - padX) / scaleX).coerceIn(0f, originalWidth.toFloat()),
        ((box.bottom - padY) / scaleY).coerceIn(0f, originalHeight.toFloat()))
    companion object {
        fun create(width: Int, height: Int): Exp02FrameTransform {
            require(width in 1..8192 && height in 1..8192 && width.toLong() * height <= 16_777_216)
            val ratio = min(640.0 / width, 640.0 / height)
            // kotlin.math.round and Python round both use ties-to-even (do not use roundToInt).
            val w = round(width * ratio).toInt(); val h = round(height * ratio).toInt()
            require(w in 1..640 && h in 1..640)
            return Exp02FrameTransform(width, height, w, h,
                round((640 - w) / 2.0 - .1).toInt(), round((640 - h) / 2.0 - .1).toInt())
        }
    }
}

/**
 * Aspect-preserving, centered 114 pad, logical RGB, float32 BCHW /255.
 * Half-pixel bilinear interpolation is independently implemented: OpenCV INTER_LINEAR can differ by
 * ONE uint8 channel level due to its fixed-point/SIMD rounding. Golden tests bound this difference
 * for resampled pixels only; padding and non-resized pixels have a strict 1e-6 tolerance.
 * The direct tensor is worker-owned and borrowed only until the next preprocess() call.
 */
class Exp02RgbPreprocessor : YoloPreprocessor<Exp02RgbFrame> {
    // Unavailable/model-missing paths must not allocate a 4.9 MB tensor just to assemble ports.
    private val tensor by lazy(LazyThreadSafetyMode.NONE) {
        ByteBuffer.allocateDirect(4 * 3 * 640 * 640).order(ByteOrder.nativeOrder())
    }
    override fun preprocess(frame: Exp02RgbFrame): YoloPreparedInput {
        val transform = Exp02FrameTransform.create(frame.width, frame.height)
        require(frame.argb.size == frame.width * frame.height)
        tensor.clear()
        repeat(3 * 640 * 640) { tensor.putFloat(114f / 255f) }
        val x0 = IntArray(transform.resizedWidth); val x1 = IntArray(x0.size); val xf = DoubleArray(x0.size)
        for (x in x0.indices) {
            val position = ((x + .5) * frame.width / x0.size - .5).coerceIn(0.0, frame.width - 1.0)
            x0[x] = floor(position).toInt(); x1[x] = (x0[x] + 1).coerceAtMost(frame.width - 1); xf[x] = position - x0[x]
        }
        for (y in 0 until transform.resizedHeight) {
            val position = ((y + .5) * frame.height / transform.resizedHeight - .5).coerceIn(0.0, frame.height - 1.0)
            val y0 = floor(position).toInt(); val y1 = (y0 + 1).coerceAtMost(frame.height - 1); val fractionY = position - y0
            for (x in x0.indices) {
                val a = frame.argb[y0 * frame.width + x0[x]]; val b = frame.argb[y0 * frame.width + x1[x]]
                val c = frame.argb[y1 * frame.width + x0[x]]; val d = frame.argb[y1 * frame.width + x1[x]]
                for (channel in 0..2) {
                    val shift = 16 - 8 * channel
                    fun value(pixel: Int) = (pixel ushr shift and 255).toDouble()
                    val top = value(a) * (1 - xf[x]) + value(b) * xf[x]
                    val bottom = value(c) * (1 - xf[x]) + value(d) * xf[x]
                    val byte = round(top * (1 - fractionY) + bottom * fractionY).toInt().coerceIn(0, 255)
                    val index = channel * 640 * 640 + (y + transform.padY) * 640 + x + transform.padX
                    tensor.putFloat(index * 4, byte / 255f)
                }
            }
        }
        tensor.rewind()
        return YoloPreparedInput(tensor, transform)
    }
}
