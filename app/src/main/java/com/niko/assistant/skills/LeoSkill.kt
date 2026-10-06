package com.niko.assistant.skills

import com.niko.assistant.brain.AssistantCommand
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class LeoRisk { READ_ONLY, LOW_RISK, USER_VISIBLE, SENSITIVE, IRREVERSIBLE }
data class LeoSkillDescriptor(val name: String, val description: String, val intents: Set<String>,
    val parameters: Set<String>, val risk: LeoRisk, val permissions: Set<String> = emptySet(),
    val cancellable: Boolean = true)
data class LeoSkillResult(val message: String, val success: Boolean, val verified: Boolean = false)

interface LeoSkill {
    val descriptor: LeoSkillDescriptor
    fun accepts(command: AssistantCommand): Boolean
    fun available(): Boolean
    suspend fun execute(command: AssistantCommand): LeoSkillResult?
}

/** Only registered, locally supplied implementations execute; no model-provided code or reflection. */
class LeoSkillRegistry {
    private val skills = linkedMapOf<String, LeoSkill>()
    fun register(skill: LeoSkill) { require(skill.descriptor.name !in skills); skills[skill.descriptor.name] = skill }
    fun discover(): List<LeoSkillDescriptor> = skills.values.filter { it.available() }.map { it.descriptor }
    fun select(command: AssistantCommand): LeoSkill? = skills.values.filter { it.available() && it.accepts(command) }.singleOrNull()
    suspend fun execute(command: AssistantCommand): LeoSkillResult? {
        currentCoroutineContext().ensureActive()
        val skill = select(command) ?: return null
        // Sensitive skills need a dedicated, parameter-bound confirmation adapter, never a model flag.
        if (skill.descriptor.risk >= LeoRisk.SENSITIVE) return LeoSkillResult("Esa acción requiere confirmación específica.", false)
        val result = skill.execute(command)
        currentCoroutineContext().ensureActive()
        return result
    }
}

/** Adapters preserve the existing Android execution code; declarations are disjoint by command type. */
object LeoBuiltInSkills {
    fun install(registry: LeoSkillRegistry, execute: suspend (AssistantCommand) -> LeoSkillResult?) {
        fun add(name: String, description: String, risk: LeoRisk, parameters: Set<String>,
            accepts: (AssistantCommand) -> Boolean) {
            registry.register(object : LeoSkill {
                override val descriptor = LeoSkillDescriptor(name, description, setOf(name), parameters, risk)
                override fun accepts(command: AssistantCommand) = accepts(command)
                // Android permission/app availability remains checked by the existing executor.
                override fun available() = true
                override suspend fun execute(command: AssistantCommand) = execute(command)
            })
        }
        add("memory", "Consultar memoria personal local", LeoRisk.READ_ONLY, emptySet()) { it == AssistantCommand.MemorySummary }
        add("generated_tool", "Crear componentes declarativos permitidos", LeoRisk.USER_VISIBLE, setOf("request")) { it is AssistantCommand.GenerateTool }
        add("ui_automation", "Observar y ejecutar pasos de Accesibilidad permitidos", LeoRisk.USER_VISIBLE, setOf("task")) { it is AssistantCommand.AutomateUi }
        add("phone", "Acciones Android validadas; mensajes quedan preparados para revisión", LeoRisk.USER_VISIBLE, setOf("typed AssistantCommand")) {
            it !is AssistantCommand.Unknown && it !is AssistantCommand.SearchWeb && it !is AssistantCommand.GenerateTool &&
                it !is AssistantCommand.AutomateUi && it != AssistantCommand.MemorySummary && it != AssistantCommand.ClearMemory
        }
    }
}
