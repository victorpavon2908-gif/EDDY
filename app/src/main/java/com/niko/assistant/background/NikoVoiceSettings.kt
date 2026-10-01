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

    /**
     * Protección de arranque: después de una actualización que pueda afectar el motor
     * nativo de voz, LEO arranca con la escucha desactivada una sola vez. Esto evita
     * que un fallo JNI/ONNX del servicio de voz impida abrir toda la aplicación.
     */
    fun ensureCrashSafeBoot(context: Context): Boolean {
        val preferences = prefs(context)
        val applied = preferences.getInt(KEY_SAFE_BOOT_VERSION, 0)
        if (applied >= SAFE_BOOT_VERSION) return false
        preferences.edit()
            .putBoolean("assistant_enabled", false)
            .putInt(KEY_SAFE_BOOT_VERSION, SAFE_BOOT_VERSION)
            .apply()
        return true
    }

    fun ensureIsolatedVoiceMigration(context: Context) {
        val preferences = prefs(context)
        if (preferences.getInt(KEY_ISOLATED_VOICE_MIGRATION, 0) >= ISOLATED_VOICE_MIGRATION_VERSION) return
        preferences.edit()
            .putBoolean("assistant_enabled", true)
            .putInt(KEY_ISOLATED_VOICE_MIGRATION, ISOLATED_VOICE_MIGRATION_VERSION)
            .apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("assistant_enabled", enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(UpgradeIdentity.controlPreferences, Context.MODE_PRIVATE)

    private const val KEY_SAFE_BOOT_VERSION = "voice_safe_boot_version"
    private const val SAFE_BOOT_VERSION = 1
    private const val KEY_ISOLATED_VOICE_MIGRATION = "isolated_voice_migration"
    private const val ISOLATED_VOICE_MIGRATION_VERSION = 1
}
