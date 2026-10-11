package com.niko.assistant.ui.generated

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class GeneratedToolStoreTest {
    @Test fun invalidCandidatePreservesActiveVersionAndRetriesPreserveRollback() {
        val context = RuntimeEnvironment.getApplication() as Context
        GeneratedToolStore.clear(context)
        val first = GeneratedToolSpec("Contador", components = listOf(GeneratedToolComponent("count", "counter", "Cuenta")))
        val second = GeneratedToolSpec("Lista", components = listOf(GeneratedToolComponent("list", "list", "Lista")))
        assertTrue(GeneratedToolStore.save(context, first))
        assertFalse(GeneratedToolStore.save(context, first.copy(components = listOf(GeneratedToolComponent("evil", "shell", "Ejecutar")))))
        assertEquals("Contador", GeneratedToolStore.read(context)?.title)
        assertTrue(GeneratedToolStore.save(context, second))
        assertTrue(GeneratedToolStore.save(context, second))
        assertTrue(GeneratedToolStore.canRollback(context))
        assertTrue(GeneratedToolStore.rollback(context))
        assertEquals("Contador", GeneratedToolStore.read(context)?.title)
        assertFalse(GeneratedToolStore.rollback(context))
        GeneratedToolStore.clear(context)
        assertNull(GeneratedToolStore.read(context))
    }

    @Test fun corruptCurrentVersionRecoversPreviousPersistedSpec() {
        val context = RuntimeEnvironment.getApplication() as Context
        GeneratedToolStore.clear(context)
        val valid = GeneratedToolSpec("Anterior", components = listOf(GeneratedToolComponent("count", "counter", "Cuenta")))
        assertTrue(GeneratedToolStore.save(context, valid))
        assertTrue(GeneratedToolStore.save(context, valid.copy(title = "Nueva")))
        context.getSharedPreferences("leo_generated_tool", Context.MODE_PRIVATE).edit().putString("spec", "corrupt").commit()
        assertEquals("Anterior", GeneratedToolStore.read(context)?.title)
        assertFalse(GeneratedToolStore.canRollback(context))
    }
    @Test fun explicitEditAddsARealComponentPreservesOthersAndCanBeUndone() = kotlinx.coroutines.runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        GeneratedToolStore.clear(context)
        val first = GeneratedToolSpec("Trabajo", components = listOf(GeneratedToolComponent("count", "counter", "Piezas")))
        GeneratedToolStore.save(context, first)
        val planner = com.niko.assistant.ai.GeneratedToolPlanner(context)
        val edited = planner.generate("agregá un temporizador a la herramienta")!!
        assertEquals(first.components.single(), edited.components.first())
        assertEquals("countdown", edited.components.last().type)
        assertTrue(GeneratedToolStore.save(context, edited))
        assertEquals(edited, planner.generate("agregá un temporizador a la herramienta"))
        assertNull(planner.generate("agregá un ejecutor de codigo a la herramienta"))
        assertTrue(GeneratedToolStore.rollback(context))
        assertEquals(1, GeneratedToolStore.read(context)!!.components.size)
    }
}
