package com.niko.assistant.ai

import android.content.Context

/**
 * Legacy compatibility shim.
 *
 * LEO uses native web retrieval from Android, so there is no Render
 * backend to wake up and no `/health` cold-start request to perform.
 *
 * Kept temporarily as a no-op so older voice-flow call sites remain source
 * compatible with existing lifecycle callers.
 */
object NikoBackendPrewarmer {
    fun wake(@Suppress("UNUSED_PARAMETER") context: Context) = Unit
}
