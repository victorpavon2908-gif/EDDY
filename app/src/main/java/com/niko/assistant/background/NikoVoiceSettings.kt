package com.niko.assistant.background

import com.niko.assistant.compat.UpgradeIdentity

import android.content.Context

/** Shared by Settings, the foreground notification and service restarts. */
object NikoVoiceSettings {
    fun enabled(context: Context): Boolean = prefs(context).getBoolean("assistant_enabled", true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("assistant_enabled", enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(UpgradeIdentity.controlPreferences, Context.MODE_PRIVATE)
}
