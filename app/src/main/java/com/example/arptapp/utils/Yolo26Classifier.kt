package com.example.arptapp.utils

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.roundToInt

class Yolo26Classifier(context: Context) {
    private val interpreter: Interpreter

    init {
        val assetFileDescriptor = context.assets.openFd("yolo11n-pose_int8.tflite")
        val modelBuffer = FileInputStream(assetFileDescriptor.fileDescriptor).use { inputStream ->
            inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )
        }
        assetFileDescriptor.close()

        interpreter = Interpreter(modelBuffer)
    }

    fun classify(bitmap: Bitmap): String {
        val inputTensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor.shape()
        if (inputShape.size != 4) return "IDLE"

        val height = inputShape[1]
        val width = inputShape[2]
        val channels = inputShape[3]
        if (channels != 3) return "IDLE"

        val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val input = ByteBuffer.allocateDirect(inputTensor.numBytes()).order(ByteOrder.nativeOrder())
        val pixels = IntArray(width * height)
        resized.getPixels(pixels, 0, width, 0, 0, width, height)
        val quantization = inputTensor.quantizationParams()

        pixels.forEach { pixel ->
            val channels = intArrayOf(
                pixel shr 16 and 0xff,
                pixel shr 8 and 0xff,
                pixel and 0xff
            )
            channels.forEach { channel ->
                if (inputTensor.dataType() == org.tensorflow.lite.DataType.FLOAT32) {
                    input.putFloat(channel / 255f)
                } else {
                    val quantized = (channel / 255f / quantization.scale + quantization.zeroPoint)
                        .roundToInt()
                        .coerceIn(-128, 127)
                    input.put(quantized.toByte())
                }
            }
        }
        if (resized !== bitmap) resized.recycle()

        val outputTensor = interpreter.getOutputTensor(0)
        val output = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder())
        interpreter.run(input, output)
        output.rewind()
        val scores = readScores(output, outputTensor)
        if (scores.size < 3) return "IDLE"

        val bestIndex = scores.indices.maxByOrNull { scores[it] } ?: return "IDLE"
        val bestScore = scores[bestIndex]
        if (bestScore < CONFIDENCE_THRESHOLD) return "IDLE"
        return LABELS.getOrElse(bestIndex) { "IDLE" }
    }

    private fun readScores(buffer: ByteBuffer, tensor: org.tensorflow.lite.Tensor): FloatArray {
        val scores = FloatArray(tensor.numElements())
        val quantization = tensor.quantizationParams()
        for (index in scores.indices) {
            scores[index] = when (tensor.dataType()) {
                org.tensorflow.lite.DataType.FLOAT32 -> buffer.getFloat()
                org.tensorflow.lite.DataType.INT8 ->
                    (buffer.get().toFloat() - quantization.zeroPoint) * quantization.scale
                org.tensorflow.lite.DataType.UINT8 ->
                    ((buffer.get().toInt() and 0xff) - quantization.zeroPoint) * quantization.scale
                else -> 0f
            }
        }
        return scores
    }

    fun close() {
        interpreter.close()
    }

    private companion object {
        const val CONFIDENCE_THRESHOLD = 0.45f
        val LABELS = arrayOf("SQUAT", "SHOULDER_PRESS", "IDLE")
    }
}