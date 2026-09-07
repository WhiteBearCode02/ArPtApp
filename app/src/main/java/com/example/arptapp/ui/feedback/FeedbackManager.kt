package com.example.arptapp.ui.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import java.util.Locale

class FeedbackManager(context: Context) : TextToSpeech.OnInitListener {
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val textToSpeech = TextToSpeech(context.applicationContext, this)
    private var isTtsReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = textToSpeech.setLanguage(Locale.KOREAN) != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun announceRep(repNumber: Int) {
        vibrate()
        if (isTtsReady) {
            textToSpeech.speak(
                "${repNumber}회 완료!",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "rep-$repNumber"
            )
        }
    }

    fun release() {
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

    private fun vibrate() {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(80L)
        }
    }
}
