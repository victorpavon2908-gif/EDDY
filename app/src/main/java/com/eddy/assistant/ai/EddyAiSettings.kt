package com.eddy.assistant.ai

import android.content.Context

/** Settings for direct Gemini access from the APK.
 * The key is stored in the app's private SharedPreferences and is never committed to Git.
 * This is appropriate for the current personal pilot; a distributed APK should use a
 * server/token broker because secrets embedded on a client device can be extracted.
 */
object EddyAiSettings {
    private const val PREFS = "eddy_ai_settings"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_GEMINI_MODEL = "gemini_model"
    const val DEFAULT_MODEL = "gemini-3.7-flash"

    fun apiKey(context: Context): String = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_GEMINI_API_KEY, "")
        .orEmpty()
        .trim()

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
