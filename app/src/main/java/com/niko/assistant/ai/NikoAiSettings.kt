package com.niko.assistant.ai

import com.niko.assistant.compat.UpgradeIdentity

import android.content.Context

/** Provider credentials are separate from memory and never embedded in source. */
object NikoAiSettings {
    private const val PREFS = UpgradeIdentity.aiPreferences
    private const val KEY_GROQ_API_KEY = "groq_api_key"
    private const val KEY_GROQ_MODEL = "groq_model"

    fun personality(context: Context): NikoPersonality = NikoPersonality.fromStored(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("personality", null),
    )
    fun companionInitiative(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("companion_initiative", false)
    fun setCompanionInitiative(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("companion_initiative", enabled).apply()
    }
    fun localFirst(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("local_first", true)
    fun autoResearch(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("auto_research", true)
    fun adaptiveLearning(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("adaptive_learning", true)
    fun saveBehavior(context: Context, personality: NikoPersonality, localFirst: Boolean, autoResearch: Boolean, learning: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("personality", personality.name).putBoolean("local_first", localFirst)
            .putBoolean("auto_research", autoResearch).putBoolean("adaptive_learning", learning).apply()
    }

    /** Retire only provider credentials; preferences and all memory keep their existing identity. */
    fun retireCloudCredentials(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_GROQ_API_KEY).remove(KEY_GROQ_MODEL).apply()
    }
}
