package com.eddy.assistant.ai

import android.content.Context

/**
 * Settings for direct Gemini access from the APK.
 *
 * PERSONAL BUILD OPTION:
 * For a private/local build you can paste a Gemini key in EMBEDDED_GEMINI_API_KEY.
 * Do NOT commit a real key to GitHub and do NOT distribute an APK containing it.
 *
 * If EMBEDDED_GEMINI_API_KEY is left as the placeholder, EDDY falls back to the
 * key saved from the in-app Gemini settings screen.
 */
object EddyAiSettings {
    private const val PREFS = "eddy_ai_settings"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_GEMINI_MODEL = "gemini_model"

    // PERSONAL/LOCAL BUILD ONLY.
    // Replace ONLY the text between the quotes on your PC, for example:
    // private const val EMBEDDED_GEMINI_API_KEY = "AQ.xxxxxxxxxxxxxxxxx"
    // Never push the edited file containing a real key to GitHub.
    private const val EMBEDDED_GEMINI_API_KEY = "PEGAR_API_KEY_GEMINI_AQUI"

    const val DEFAULT_MODEL = "gemini-3.7-flash"

    fun apiKey(context: Context): String {
        val embedded = EMBEDDED_GEMINI_API_KEY.trim()
        if (embedded.isNotBlank() && embedded != "PEGAR_API_KEY_GEMINI_AQUI") {
            return embedded
        }

        return context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_GEMINI_API_KEY, "")
            .orEmpty()
            .trim()
    }

    fun model(context: Context): String = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_GEMINI_MODEL, DEFAULT_MODEL)
        .orEmpty()
        .trim()
        .ifBlank { DEFAULT_MODEL }

    fun saveGemini(context: Context, apiKey: String, model: String = DEFAULT_MODEL) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GEMINI_API_KEY, apiKey.trim())
            .putString(KEY_GEMINI_MODEL, model.trim().ifBlank { DEFAULT_MODEL })
            .apply()
    }

    fun clearGemini(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_GEMINI_API_KEY)
            .remove(KEY_GEMINI_MODEL)
            .apply()
    }
}
