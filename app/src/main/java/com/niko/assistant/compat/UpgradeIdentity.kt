package com.niko.assistant.compat

import android.content.Context
import android.content.Intent

/** Private upgrade contracts. These names keep installed data and Android grants intact. */
object UpgradeIdentity {
    const val affectPreferences = "eddy_affect_local"
    const val outputVoicePreferences = "eddy_voice_output"
    const val voiceProfilePreferences = "eddy_voice_profile_v1"
    const val notesPreferences = "eddy_notes"
    const val memoryPreferences = "eddy_memory"
    const val memoryDatabase = "eddy_memory_archive.db"
    const val controlPreferences = "eddy_control"
    const val uiPreferences = "eddy_ui_mode"
    const val aiPreferences = "eddy_ai_settings"
    const val smartHomePreferences = "eddy_smart_home"
    const val bubblePreferences = "eddy_bubble_prefs"
    const val assistantChannel = "eddy_assistant_channel"
    const val wakeChannel = "eddy_wake_channel"
    const val modelDirectory = "eddy-local-ai"
    const val evolutionDirectory = "eddy-evolution"
    const val skillsDirectory = "eddy-skills"
    const val keywordModelId = "kws-eddy-zh-en-2025-v3"
    const val modelMarker = ".eddy-model-id"
    const val proactiveChannel = "eddy_proactive"
    const val proactiveMessageExtra = "eddy_message"
    const val proactiveIdExtra = "eddy_notification_id"

    const val ACTION_STOP = "com.eddy.assistant.action.STOP"

    const val ACTION_SHOW_BUBBLE = "com.eddy.assistant.action.SHOW_BUBBLE"

    const val ACTION_HIDE_BUBBLE = "com.eddy.assistant.action.HIDE_BUBBLE"

    const val ACTION_REFRESH_BUBBLE = "com.eddy.assistant.action.REFRESH_BUBBLE"

    fun assistantService(context: Context): Intent = Intent().setClassName(context, "com.eddy.assistant.background.EddyAssistantService")
    fun wakeActivity(context: Context): Intent = Intent().setClassName(context, "com.eddy.assistant.EddyWakeActivity")
    fun proactiveReceiver(context: Context): Intent = Intent().setClassName(context, "com.eddy.assistant.proactive.EddyProactiveReceiver")
    fun reminderText(text: String): String = text.replace(Regex("(?i)\\beddy\\b"), "Niko")
}
