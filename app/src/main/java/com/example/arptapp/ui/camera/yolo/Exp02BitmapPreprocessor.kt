package com.example.arptapp.ui.camera.yolo

import android.graphics.Bitmap
import com.example.arptapp.domain.detection.Exp02FrameTransform
import com.example.arptapp.domain.detection.Exp02RgbFrame
import com.example.arptapp.domain.detection.Exp02RgbPreprocessor
import com.example.arptapp.domain.detection.YoloPreparedInput
import com.example.arptapp.domain.detection.YoloPreprocessor
import com.example.arptapp.domain.detection.YoloOpenCvLinearRgbPreprocessor

/** Worker-owned adapter. getPixels exposes logical ARGB, independent of Bitmap memory byte order. */
internal class YoloBitmapPreprocessor(
    private val delegate: YoloPreprocessor<Exp02RgbFrame> = YoloOpenCvLinearRgbPreprocessor()
) : YoloPreprocessor<Bitmap> {
    private var pixels = IntArray(0)
    override fun preprocess(frame: Bitmap): YoloPreparedInput {
        check(!frame.isRecycled)
        Exp02FrameTransform.create(frame.width, frame.height) // enforce bounds before allocating
        val size = frame.width * frame.height
        if (pixels.size != size) pixels = IntArray(size)
        frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
        return delegate.preprocess(Exp02RgbFrame(frame.width, frame.height, pixels))
    }
}

/** Keep historical EXP02 arithmetic/evidence unchanged; the new canonical adapter is EXP03-only. */
internal class Exp02BitmapPreprocessor : YoloPreprocessor<Bitmap> by YoloBitmapPreprocessor(Exp02RgbPreprocessor())
