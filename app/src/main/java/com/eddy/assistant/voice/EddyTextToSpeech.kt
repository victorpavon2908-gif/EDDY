package com.eddy.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class EddyTextToSpeech(
    context: Context,
    private val onReady: (Boolean) -> Unit = {},
    private val onSpeakingChanged: (Boolean) -> Unit = {},
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val nicaraguaSpanish = Locale("es", "NI")
            val result = tts.setLanguage(nicaraguaSpanish)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.language = Locale("es", "ES")
            }

            tts.setSpeechRate(1.06f)
            tts.setPitch(0.96f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    notifySpeaking(true)
                }

                override fun onDone(utteranceId: String?) {
                    notifySpeaking(false)
                }

                @Deprecated("Deprecated in Android API")
                override fun onError(utteranceId: String?) {
                    notifySpeaking(false)
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    notifySpeaking(false)
                }
            })
        }
        onReady(ready)
    }

    private fun notifySpeaking(value: Boolean) {
        mainHandler.post { onSpeakingChanged(value) }
    }

    fun speak(text: String) {
        if (!ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "eddy_reply_${System.nanoTime()}")
    }

    fun stop() {
        tts.stop()
        notifySpeaking(false)
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
        notifySpeaking(false)
    }
}
