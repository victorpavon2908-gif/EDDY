package com.niko.assistant.ui.generated

import android.content.Context

internal object GeneratedToolStore {
    private const val PREFS = "leo_generated_tool"
    private const val KEY_SPEC = "spec"

    fun save(context: Context, spec: GeneratedToolSpec) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SPEC, spec.toJson())
            .apply()
    }

    fun read(context: Context): GeneratedToolSpec? {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SPEC, null)
            ?: return null
        return GeneratedToolSpec.parse(raw)
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SPEC)
            .apply()
    }
}
