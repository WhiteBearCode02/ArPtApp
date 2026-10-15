package com.example.arptapp.ui.camera.yolo

import android.graphics.Bitmap
import com.example.arptapp.domain.detection.Exp02FrameTransform
import com.example.arptapp.domain.detection.Exp02RgbFrame
import com.example.arptapp.domain.detection.Exp02RgbPreprocessor
import com.example.arptapp.domain.detection.YoloPreparedInput
import com.example.arptapp.domain.detection.YoloPreprocessor

/** Worker-owned adapter. getPixels exposes logical ARGB, independent of Bitmap memory byte order. */
internal class Exp02BitmapPreprocessor : YoloPreprocessor<Bitmap> {
    private val delegate = Exp02RgbPreprocessor()
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
