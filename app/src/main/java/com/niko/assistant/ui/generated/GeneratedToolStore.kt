package com.niko.assistant.ui.generated

import android.content.Context

/** Versioned declarative activation. Never loads Kotlin, JS, shell or downloaded bytecode. */
internal object GeneratedToolStore {
    private const val PREFS = "leo_generated_tool"
    private const val KEY_SPEC = "spec" // Keep existing installations readable.
    private const val KEY_PREVIOUS = "previous_spec"
    private const val MAX_BYTES = 100_000

    @Synchronized fun save(context: Context, spec: GeneratedToolSpec): Boolean {
        val raw = spec.toJson()
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return false
        val validated = GeneratedToolSpec.parse(raw)?.toJson() ?: return false
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_SPEC, null)
        if (current == validated) return true // A retry must not erase the rollback version.
        val previous = current?.takeIf { GeneratedToolSpec.parse(it) != null }
            ?: prefs.getString(KEY_PREVIOUS, null)?.takeIf { GeneratedToolSpec.parse(it) != null }
        // One durable transaction publishes the candidate and preserves its predecessor.
        val committed = prefs.edit().putString(KEY_PREVIOUS, previous).putString(KEY_SPEC, validated).commit()
        if (!committed) {
            prefs.edit().putString(KEY_SPEC, current).commit()
            return false
        }
        return prefs.getString(KEY_SPEC, null) == validated
    }

    @Synchronized fun read(context: Context): GeneratedToolSpec? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_SPEC, null)?.let(GeneratedToolSpec::parse)
        if (current != null) return current
        // Recover an interrupted/corrupted current version without treating it as a new update.
        val previous = prefs.getString(KEY_PREVIOUS, null)?.let(GeneratedToolSpec::parse) ?: return null
        return previous.takeIf { prefs.edit().putString(KEY_SPEC, it.toJson()).remove(KEY_PREVIOUS).commit() }
    }

    @Synchronized fun canRollback(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PREVIOUS, null)
        ?.let(GeneratedToolSpec::parse) != null

    @Synchronized fun rollback(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString(KEY_PREVIOUS, null)?.let(GeneratedToolSpec::parse) ?: return false
        return prefs.edit().putString(KEY_SPEC, previous.toJson()).remove(KEY_PREVIOUS).commit()
    }

    @Synchronized fun clear(context: Context) {
        check(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_SPEC).remove(KEY_PREVIOUS).commit()) { "No pude borrar la herramienta guardada" }
    }
}
