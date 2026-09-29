package com.niko.assistant.background

import com.niko.assistant.compat.UpgradeIdentity

import android.content.Context
import com.niko.assistant.startup.LeoFirstRunSetup

/** Shared by Settings, the foreground notification and service restarts. */
object NikoVoiceSettings {
    /**
     * Android puede intentar revivir el servicio fuera de MainActivity. La marca de
     * preparación evita que eso arranque a LEO antes de terminar la instalación inicial.
     */
    fun enabled(context: Context): Boolean =
        userEnabled(context) && LeoFirstRunSetup.isMarkedReady(context)

    fun userEnabled(context: Context): Boolean = prefs(context).getBoolean("assistant_enabled", true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("assistant_enabled", enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(UpgradeIdentity.controlPreferences, Context.MODE_PRIVATE)
}
