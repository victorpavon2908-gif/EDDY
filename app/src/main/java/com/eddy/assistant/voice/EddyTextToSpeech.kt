package com.eddy.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class EddyTextToSpeech(
    context: Context,
    private val onReady: (Boolean) -> Unit = {},
) : TextToSpeech.OnInitListener {

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
        }
        onReady(ready)
    }

    fun speak(text: String) {
        if (!ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "eddy_reply")
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
