package com.example.arptapp.ui.feedback

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import java.util.Locale

class FeedbackManager(context: Context) : TextToSpeech.OnInitListener {
    private val vibrator = requireNotNull(ContextCompat.getSystemService(context, Vibrator::class.java))
    private val textToSpeech = TextToSpeech(context.applicationContext, this)
    private var isTtsReady = false
    private var lastAnnouncedRep = 0
    fun resetSession() { lastAnnouncedRep = 0 }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = textToSpeech.setLanguage(Locale.KOREAN) != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun announceRep(repNumber: Int, priorityFeedback: String? = null) {
        if (repNumber <= lastAnnouncedRep) return
        lastAnnouncedRep = repNumber
        vibrate()
        if (isTtsReady) {
            textToSpeech.speak(
                com.example.arptapp.domain.report.ExerciseSpeechFormatter.repCompleted(repNumber, priorityFeedback),
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
        vibrator.vibrate(VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
