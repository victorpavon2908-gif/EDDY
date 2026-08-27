package com.eddy.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
            configureMasculineNicaraguanSpanishVoice()
            tts.setSpeechRate(0.98f)
            tts.setPitch(0.82f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = notifySpeaking(true)
                override fun onDone(utteranceId: String) = notifySpeaking(false)

                @Deprecated("Deprecated in Android API")
                override fun onError(utteranceId: String) = notifySpeaking(false)

                override fun onStop(utteranceId: String, interrupted: Boolean) = notifySpeaking(false)
            })
        }
        onReady(ready)
    }

    private fun configureMasculineNicaraguanSpanishVoice() {
        val nicaraguaSpanish = Locale("es", "NI")
        val languageResult = tts.setLanguage(nicaraguaSpanish)

        val spanishVoices = runCatching { tts.voices.orEmpty() }
            .getOrDefault(emptySet())
            .filter { it.locale.language.equals("es", ignoreCase = true) }

        val preferred = spanishVoices
            .sortedWith(
                compareBy<Voice>(
                    { masculineVoiceRank(it) },
                    { voiceCountryRank(it.locale.country) },
                    { if (it.isNetworkConnectionRequired) 1 else 0 },
                    { it.latency },
                ),
            )
            .firstOrNull()

        if (preferred != null) {
            runCatching { tts.voice = preferred }
            return
        }

        if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            val fallbacks = listOf(
                Locale("es", "US"),
                Locale("es", "MX"),
                Locale("es", "CR"),
                Locale("es", "CO"),
                Locale("es", "ES"),
            )
            for (locale in fallbacks) {
                val result = tts.setLanguage(locale)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) break
            }
        }
    }

    private fun masculineVoiceRank(voice: Voice): Int {
        val descriptor = buildString {
            append(voice.name.lowercase(Locale.ROOT))
            append(' ')
            append(voice.features.joinToString(" ").lowercase(Locale.ROOT))
        }
        return when {
            listOf("male", "masculino", "masculine", "hombre", "man").any(descriptor::contains) -> 0
            listOf("female", "femenino", "feminine", "mujer", "woman").any(descriptor::contains) -> 2
            else -> 1
        }
    }

    private fun voiceCountryRank(country: String): Int = when (country.uppercase(Locale.ROOT)) {
        "NI" -> 0
        "CR" -> 1
        "HN", "SV", "GT" -> 2
        "MX", "US" -> 3
        "CO", "VE", "PA" -> 4
        "AR", "UY", "CL", "PE", "EC", "BO", "PY", "DO", "PR" -> 5
        "ES" -> 6
        else -> 7
    }

    private fun notifySpeaking(value: Boolean) {
        mainHandler.post { onSpeakingChanged(value) }
    }

    fun speak(text: String): Boolean {
        if (!ready) return false
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "eddy_reply_${System.nanoTime()}")
        return result == TextToSpeech.SUCCESS
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
