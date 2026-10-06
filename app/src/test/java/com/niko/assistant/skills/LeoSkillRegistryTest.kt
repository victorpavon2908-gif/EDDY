package com.niko.assistant.skills

import com.niko.assistant.brain.AssistantCommand
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LeoSkillRegistryTest {
    @Test fun declaredAdaptersRouteDisjointCommands() {
        val registry = LeoSkillRegistry(); LeoBuiltInSkills.install(registry) { null }
        assertEquals("phone", registry.select(AssistantCommand.SetTorch(true))?.descriptor?.name)
        assertEquals("memory", registry.select(AssistantCommand.MemorySummary)?.descriptor?.name)
        assertEquals("generated_tool", registry.select(AssistantCommand.GenerateTool("billar"))?.descriptor?.name)
        assertEquals("ui_automation", registry.select(AssistantCommand.AutomateUi("bajá"))?.descriptor?.name)
        assertNull(registry.select(AssistantCommand.ClearMemory)); assertNull(registry.select(AssistantCommand.Unknown("exec")))
    }
    @Test fun registeredExecutorReceivesTypedCommand() = runBlocking {
        val registry = LeoSkillRegistry(); var seen: AssistantCommand? = null
        LeoBuiltInSkills.install(registry) { seen = it; LeoSkillResult("ok", true) }
        registry.execute(AssistantCommand.BatteryStatus); assertEquals(AssistantCommand.BatteryStatus, seen)
    }
    @Test fun modelCannotApproveSensitiveSkill() = runBlocking {
        var executed = false; val registry = LeoSkillRegistry()
        registry.register(object : LeoSkill {
            override val descriptor = LeoSkillDescriptor("delete", "delete", setOf("delete"), emptySet(), LeoRisk.IRREVERSIBLE)
            override fun available() = true
            override fun accepts(command: AssistantCommand) = true
            override suspend fun execute(command: AssistantCommand): LeoSkillResult { executed = true; return LeoSkillResult("deleted", true) }
        })
        assertFalse(registry.execute(AssistantCommand.ClearMemory)!!.success); assertFalse(executed)
    }
}
