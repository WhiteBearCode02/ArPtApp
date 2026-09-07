package com.example.arptapp.utils

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel

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

    fun close() {
        interpreter.close()
    }
}