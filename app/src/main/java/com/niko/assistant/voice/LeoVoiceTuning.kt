package com.niko.assistant.voice

import android.content.Context

object LeoVoiceTuning {
    private const val PREFS = "leo_voice_tuning_v1"
    @Volatile private var profile = LeoVoiceTuningProfile()
    @Volatile private var appContext: Context? = null

    fun configure(context: Context) {
        appContext = context.applicationContext
        profile = read(context)
    }

    fun current(): LeoVoiceTuningProfile = profile

    fun apply(value: LeoVoiceTuningProfile) {
        val safe = value.sanitized()
        profile = safe
        val context = appContext ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("keyword_score", safe.keywordScore)
            .putFloat("keyword_threshold", safe.keywordThreshold)
            .putInt("trailing_blanks", safe.trailingBlanks)
            .putInt("min_passive_ms", safe.minPassiveSpeechMs)
            .putInt("max_passive_ms", safe.maxPassiveSpeechMs)
            .putLong("probe_cooldown_ms", safe.passiveProbeCooldownMs)
            .putFloat("passive_min_rms", safe.passiveMinimumUsefulRms)
            .putFloat("active_min_rms", safe.activeMinimumUsefulRms)
            .putFloat("passive_target_rms", safe.passiveTargetRms)
            .putFloat("active_target_rms", safe.activeTargetRms)
            .putFloat("passive_max_gain", safe.passiveMaxGain)
            .putFloat("active_max_gain", safe.activeMaxGain)
            .putInt("pre_roll_ms", safe.preRollMs)
            .apply()
    }

    fun reset() {
        profile = LeoVoiceTuningProfile()
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
    }

    private fun read(context: Context): LeoVoiceTuningProfile {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaults = LeoVoiceTuningProfile()
        return LeoVoiceTuningProfile(
            keywordScore = prefs.getFloat("keyword_score", defaults.keywordScore),
            keywordThreshold = prefs.getFloat("keyword_threshold", defaults.keywordThreshold),
            trailingBlanks = prefs.getInt("trailing_blanks", defaults.trailingBlanks),
            minPassiveSpeechMs = prefs.getInt("min_passive_ms", defaults.minPassiveSpeechMs),
            maxPassiveSpeechMs = prefs.getInt("max_passive_ms", defaults.maxPassiveSpeechMs),
            passiveProbeCooldownMs = prefs.getLong("probe_cooldown_ms", defaults.passiveProbeCooldownMs),
            passiveMinimumUsefulRms = prefs.getFloat("passive_min_rms", defaults.passiveMinimumUsefulRms),
            activeMinimumUsefulRms = prefs.getFloat("active_min_rms", defaults.activeMinimumUsefulRms),
            passiveTargetRms = prefs.getFloat("passive_target_rms", defaults.passiveTargetRms),
            activeTargetRms = prefs.getFloat("active_target_rms", defaults.activeTargetRms),
            passiveMaxGain = prefs.getFloat("passive_max_gain", defaults.passiveMaxGain),
            activeMaxGain = prefs.getFloat("active_max_gain", defaults.activeMaxGain),
            preRollMs = prefs.getInt("pre_roll_ms", defaults.preRollMs),
        ).sanitized()
    }
}
