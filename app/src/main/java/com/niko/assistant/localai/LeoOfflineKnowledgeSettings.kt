package com.niko.assistant.localai

import android.content.Context
import com.niko.assistant.compat.UpgradeIdentity

/** Consent covers optional knowledge downloads/resumption, never voice or existing data. */
object LeoOfflineKnowledgeSettings {
    private const val KEY = "offline_knowledge_download_allowed"
    fun downloadAllowed(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(UpgradeIdentity.aiPreferences, Context.MODE_PRIVATE).getBoolean(KEY, false)
    fun allowDownload(context: Context, allowed: Boolean) {
        context.applicationContext.getSharedPreferences(UpgradeIdentity.aiPreferences, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, allowed).apply()
    }
}
