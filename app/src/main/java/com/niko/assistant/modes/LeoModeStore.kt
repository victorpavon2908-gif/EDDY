package com.niko.assistant.modes

import android.content.Context
import android.content.SharedPreferences

/** Separate preferences: switching modes never rewrites learned behavior or model permissions. */
object LeoModeStore {
    const val KEY = "active_mode"
    fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("leo_operating_modes", Context.MODE_PRIVATE)
    fun read(context: Context): LeoMode = fromPreferences(preferences(context))
    private fun fromPreferences(prefs: SharedPreferences) = LeoMode.fromId(prefs.getString(KEY, null))
    fun set(context: Context, mode: LeoMode) { preferences(context).edit().putString(KEY, mode.id).apply() }
}
