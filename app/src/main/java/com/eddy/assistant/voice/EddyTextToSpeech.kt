package com.eddy.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.text.Normalizer
import java.util.Locale

/**
 * Voz del sistema con perfil latino/nicaragüense.
 *
 * Android no garantiza que exista una voz "es-NI" en todos los teléfonos. Por eso EDDY
 * prioriza Nicaragua y Centroamérica, luego voces latinas de EE. UU./México, y deja España
 * como último recurso. La selección también evita voces excesivamente robóticas cuando el
 * motor expone calidad/latencia.
 */
class EddyTextToSpeech(
    context: Context,
    private val onReady: (Boolean) -> Unit = {},
    private val onSpeakingChanged: (Boolean) -> Unit = {},
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    @Volatile private var currentUtterance: String? = null
    @Volatile private var currentPrefix: String? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            configureNicaraguanLatinVoice()
            // Un poco más ágil y grave que la voz Android por defecto: se siente más conversacional.
            tts.setSpeechRate(1.03f)
            tts.setPitch(0.92f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) { if (utteranceId == currentUtterance) notifySpeaking(true) }
                override fun onDone(utteranceId: String) { if (utteranceId == currentUtterance) notifySpeaking(false) }
                @Deprecated("Deprecated in Android API")
                override fun onError(utteranceId: String) { if (currentPrefix?.let { utteranceId.startsWith(it) } == true) notifySpeaking(false) }
                override fun onStop(utteranceId: String, interrupted: Boolean) { if (utteranceId == currentUtterance) notifySpeaking(false) }
            })
        }
        onReady(ready)
    }

    private fun spanishLocale(country: String): Locale = Locale.Builder()
        .setLanguage("es")
        .setRegion(country)
        .build()

    private fun configureNicaraguanLatinVoice() {
        // Pedimos es-NI primero para que el motor pueda cargar sus datos/voz si los tiene.
        val nicaraguaSpanish = spanishLocale("NI")
        val languageResult = tts.setLanguage(nicaraguaSpanish)

        val spanishVoices = runCatching { tts.voices.orEmpty() }
            .getOrDefault(emptySet())
            .filter { it.locale.language.equals("es", ignoreCase = true) }

        val preferred = spanishVoices.sortedWith(
            compareBy<Voice> { if (it.isNetworkConnectionRequired) 1 else 0 }
                .thenBy { voiceCountryRank(it.locale.country) }
                .thenBy { naturalVoiceRank(it) }
                .thenByDescending { it.quality }
                .thenBy { it.latency },
        ).firstOrNull()

        if (preferred != null) {
            runCatching { tts.voice = preferred }
            return
        }

        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Centroamérica primero; es-US suele ser latino en Google TTS y funciona bien como fallback.
            val fallbacks = listOf("CR", "HN", "SV", "GT", "US", "MX", "CO", "ES").map(::spanishLocale)
            for (locale in fallbacks) {
                val result = tts.setLanguage(locale)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) break
            }
        }
    }

    private fun naturalVoiceRank(voice: Voice): Int {
        val descriptor = buildString {
            append(voice.name.lowercase(Locale.ROOT)); append(' ')
            append(voice.features.joinToString(" ").lowercase(Locale.ROOT))
        }
        return when {
            listOf("natural", "neural", "wavenet", "premium", "enhanced", "studio").any(descriptor::contains) -> 0
            listOf("male", "masculino", "masculine", "hombre", "man").any(descriptor::contains) -> 1
            listOf("female", "femenino", "feminine", "mujer", "woman").any(descriptor::contains) -> 3
            else -> 2
        }
    }

    private fun voiceCountryRank(country: String): Int = when (country.uppercase(Locale.ROOT)) {
        "NI" -> 0
        "CR", "HN", "SV", "GT" -> 1
        "US", "MX" -> 2
        "CO", "VE", "PA", "DO", "PR" -> 3
        "AR", "UY", "CL", "PE", "EC", "BO", "PY" -> 4
        "ES" -> 8
        else -> 6
    }

    /**
     * Ajustes mínimos de pronunciación para que frases comunes suenen naturales en español nica.
     * No cambia el significado de la respuesta ni inventa muletillas.
     */
    private fun prepareForNicaraguanSpeech(text: String): String {
        var value = text.trim()
        if (value.isBlank()) return value

        value = value
            .replace(Regex("(?i)\\bwi[ -]?fi\\b"), "uái fái")
            .replace(Regex("(?i)\\bwhats ?app\\b"), "guátsap")
            .replace(Regex("(?i)\\byoutube\\b"), "yútub")
            .replace(Regex("(?i)\\bspotify\\b"), "espótifai")
            .replace(Regex("(?i)\\bbluetooth\\b"), "blútuz")
            .replace(Regex("(?i)\\bOK\\b"), "okey")

        // Evita pausas sintéticas largas y conserva la entonación natural de frases cortas.
        value = value
            .replace(Regex("\\s+"), " ")
            .replace("…", ".")
            .trim()

        return Normalizer.normalize(value, Normalizer.Form.NFC)
    }

    private fun notifySpeaking(value: Boolean) { mainHandler.post { onSpeakingChanged(value) } }

    fun speak(text: String): Boolean {
        if (!ready) return false
        val spoken = prepareForNicaraguanSpeech(text)
        if (spoken.isBlank()) return false
        val chunks = spoken.chunked(TextToSpeech.getMaxSpeechInputLength() - 1)
        val id = "eddy_reply_${System.nanoTime()}"
        currentPrefix = id
        currentUtterance = "${id}_${chunks.lastIndex}"
        for ((index, chunk) in chunks.withIndex()) {
            val result = tts.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "${id}_$index")
            if (result != TextToSpeech.SUCCESS) { stop(); return false }
        }
        return true
    }

    fun stop() { currentPrefix = null; currentUtterance = null; tts.stop(); notifySpeaking(false) }
    fun shutdown() { ready = false; currentPrefix = null; currentUtterance = null; tts.stop(); tts.shutdown(); notifySpeaking(false) }
}
