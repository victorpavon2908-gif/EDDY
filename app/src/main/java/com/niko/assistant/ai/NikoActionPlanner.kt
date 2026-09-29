package com.niko.assistant.ai

import android.content.Context

/**
 * Compatibility wrapper kept for callers that still reference NikoActionPlanner.
 *
 * NIKO no longer sends planning requests to a Render/backend `/plan` endpoint.
 * Natural-language reasoning is handled by the direct GroqCloud client while the
 * actual phone actions remain constrained by LocalBrain/ActionExecutor.
 */
class NikoActionPlanner(@Suppress("UNUSED_PARAMETER") context: Context) {
    suspend fun plan(
        @Suppress("UNUSED_PARAMETER") message: String,
        @Suppress("UNUSED_PARAMETER") memoryContext: String,
    ): NikoActionPlan? = null
}

data class PlannedAction(
    val type: String,
    val args: Map<String, String>,
)

data class NikoActionPlan(
    val reply: String,
    val actions: List<PlannedAction>,
    val needsConfirmation: Boolean,
)
