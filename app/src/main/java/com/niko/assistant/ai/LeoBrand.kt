package com.niko.assistant.ai

/**
 * Identidad pública canónica del asistente.
 *
 * Los nombres de paquetes/clases Niko* se conservan únicamente como compatibilidad binaria
 * y de actualización. Ningún texto que llega al usuario debe depender de esos nombres internos.
 */
object LeoBrand {
    const val DISPLAY_NAME = "Leo"
    const val BRAND_NAME = "LEO"
    const val WAKE_WORD = "leo"
    const val DEVELOPER_NAME = "Víctor Pavón"

    private val retiredIdentity = Regex("(?i)\\b(?:niko|nico|nikko)\\b")

    /** Migra texto viejo o persistido para que la identidad anterior nunca reaparezca en UI/TTS. */
    fun publicText(value: String): String = retiredIdentity.replace(value, DISPLAY_NAME)
}
