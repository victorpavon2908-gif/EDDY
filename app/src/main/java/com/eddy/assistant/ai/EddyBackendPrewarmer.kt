package com.eddy.assistant.ai

import android.content.Context

/**
 * Legacy compatibility shim.
 *
 * EDDY now talks directly to GroqCloud from the Android app, so there is no Render
 * backend to wake up and no `/health` cold-start request to perform.
 *
 * Kept temporarily as a no-op so older voice-flow call sites remain source
 * compatible while the direct-Groq migration settles.
 */
object EddyBackendPrewarmer {
    fun wake(@Suppress("UNUSED_PARAMETER") context: Context) = Unit
}
