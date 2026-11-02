package com.niko.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/** Small transport boundary for recovery tests; production always uses Android's TTS engine. */
internal interface LeoSpeechEngine {
    data class Selection(val engine: String?, val name: String, val locale: String)
    fun prepare(savedVoice: String?, savedEngine: String?): Selection?
    fun listen(listener: UtteranceProgressListener): Boolean
    fun setProsody(speed: Float, pitch: Float): Boolean
    fun speak(text: String, queueMode: Int, id: String): Int
    fun stop()
    fun shutdown()
}

internal class AndroidLeoSpeechEngine(context: Context, private val requestedEngine: String?, onInit: (Int) -> Unit) : LeoSpeechEngine {
    private val tts = TextToSpeech(context, { onInit(it) }, requestedEngine)
    override fun prepare(savedVoice: String?, savedEngine: String?): LeoSpeechEngine.Selection? {
        val engine = requestedEngine?.takeIf { name -> tts.engines.any { it.name == name } } ?: tts.defaultEngine
        val voices = tts.voices.orEmpty()
        val candidates = voices.map {
            OfflineVoiceSelector.Candidate(it.name, it.locale.language, it.locale.country, it.quality,
                it.latency, it.isNetworkConnectionRequired, it.features.orEmpty())
        }
        for (candidate in OfflineVoiceSelector.ranked(candidates, savedVoice.takeIf { savedEngine == engine })) {
            val voice = voices.first { it.name == candidate.name }
            if (tts.setVoice(voice) == TextToSpeech.SUCCESS)
                return LeoSpeechEngine.Selection(engine, voice.name, voice.locale.toLanguageTag())
        }
        return null
    }
    override fun listen(listener: UtteranceProgressListener) = tts.setOnUtteranceProgressListener(listener) == TextToSpeech.SUCCESS
    override fun setProsody(speed: Float, pitch: Float) = tts.setSpeechRate(speed) == TextToSpeech.SUCCESS && tts.setPitch(pitch) == TextToSpeech.SUCCESS
    override fun speak(text: String, queueMode: Int, id: String) = tts.speak(text, queueMode, null, id)
    override fun stop() { tts.stop() }
    override fun shutdown() { tts.shutdown() }
}
