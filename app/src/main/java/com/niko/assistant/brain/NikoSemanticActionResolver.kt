package com.niko.assistant.brain

import com.niko.assistant.devicecontrol.NikoUiTaskPolicy

/**
 * Compatibility facade around [LeoStructuredPlanner].
 *
 * Phase 2 adds one local optimization: when the user explicitly opens an app/settings
 * and then asks to manipulate that visible UI, keep the opening action local and hand
 * the remaining goal to the visual agent. This avoids misrouting an in-app "buscá" as
 * an Internet search and works offline for the first step.
 */
class NikoSemanticActionResolver(
    private val brain: LocalBrain,
    nowMillis: () -> Long = System::currentTimeMillis,
    structuredCompletion: suspend (String) -> String?,
) {
    private val planner = LeoStructuredPlanner(brain, nowMillis, structuredCompletion)

    suspend fun resolveMany(text: String): List<AssistantCommand> =
        compoundAppUiPlan(text) ?: planner.resolveMany(text)

    suspend fun plan(text: String): LeoPlanDecision {
        val compound = compoundAppUiPlan(text)
        return if (compound != null) compoundDecision(compound) else planner.plan(text)
    }

    suspend fun plan(input: LeoPlannerInput): LeoPlanDecision {
        val compound = compoundAppUiPlan(input.utterance)
        return if (compound != null) compoundDecision(compound) else planner.plan(input)
    }

    fun parseDsl(value: String): List<AssistantCommand> = planner.parseDsl(value)

    private fun compoundAppUiPlan(text: String): List<AssistantCommand>? {
        val match = COMPOUND_SPLIT.find(text.trim()) ?: return null
        val firstText = text.substring(0, match.range.first).trim(' ', ',', ';')
        val uiTask = text.substring(match.range.last + 1).trim(' ', ',', ';')
        if (firstText.isBlank() || uiTask.isBlank()) return null
        if (!NikoUiTaskPolicy.looksLikeExplicitUiTask(uiTask)) return null

        val firstCommands = brain.understandMany(firstText)
        if (firstCommands.size != 1) return null
        val first = firstCommands.single()
        if (!opensInteractiveContext(first)) return null
        return listOf(first, AssistantCommand.AutomateUi(uiTask))
    }

    private fun opensInteractiveContext(command: AssistantCommand): Boolean = when (command) {
        is AssistantCommand.OpenApp,
        is AssistantCommand.OpenAppByName,
        is AssistantCommand.OpenSystemPanel,
        AssistantCommand.OpenAiSettings,
        AssistantCommand.OpenSmartHomeSettings -> true
        else -> false
    }

    private fun compoundDecision(commands: List<AssistantCommand>) = LeoPlanDecision(
        commands = commands,
        source = LeoPlanSource.LOCAL,
        confidence = 0.99,
        risk = LeoPlanRisk.MEDIUM,
        requiresConfirmation = false,
        reason = "apertura local seguida de navegación visual segura",
    )

    private companion object {
        val COMPOUND_SPLIT = Regex(
            "(?i)\\s*(?:,|;|\\by\\s+(?:luego\\s+)?|\\bdespu[eé]s\\b|\\bluego\\b)\\s*(?=(?:busc[aá]|buscar|naveg[aá]|navegar|toc[aá]|toca|presion[aá]|presiona|seleccion[aá]|selecciona|escrib[ií]|escribe|desliz[aá]|desliza|sub[ií]|sube|baj[aá]|baja|entr[aá]|entra|and[aá]|anda)\\b)",
        )
    }
}
