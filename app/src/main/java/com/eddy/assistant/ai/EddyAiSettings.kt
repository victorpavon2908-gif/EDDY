package com.eddy.assistant.ai

import android.content.Context
import com.eddy.assistant.BuildConfig

object EddyAiSettings {
    private const val PREFS = "eddy_ai_settings"
    private const val KEY_BASE_URL = "base_url"

    fun baseUrl(context: Context): String {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, "")
            .orEmpty()
            .trim()
        return stored.ifBlank { BuildConfig.EDDY_AI_BASE_URL.trim() }
    }

    fun saveBaseUrl(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, value.trim().trimEnd('/'))
            .apply()
    }

    fun clearBaseUrl(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_BASE_URL)
            .apply()
    }
}
