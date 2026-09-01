package com.eddy.assistant.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class EddyMemoryArchiveTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun isolateDatabase() {
        // Robolectric can reuse this application's singleton across test methods.
        // Each scenario needs a fresh archive, including its migration marker.
        val db = EddyMemoryArchive.get(context).writableDatabase
        listOf("turns", "notes", "lessons", "legacy_backup", "metadata").forEach { db.delete(it, null, null) }
        context.getSharedPreferences("eddy_memory", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun closeDatabase() { EddyMemoryArchive.get(context).close() }

    @Test fun migratesExistingDataOnceAndPersistsAcrossDatabaseReopen() {
        val turn = JSONObject().put("role", "user").put("text", "Antes de la migración").put("timestamp", 1L)
        val lesson = JSONObject().put("question", "mi bebida").put("answer", "café")
        context.getSharedPreferences("eddy_memory", Context.MODE_PRIVATE).edit()
            .putString("conversation_turns_v2", JSONArray().put(turn).toString())
            .putString("explicit_notes_v1", JSONArray().put("me gusta el café").toString())
            .putString("personal_lessons_v1", JSONArray().put(lesson).toString()).commit()
        val memory = EddyMemory(context)
        memory.rememberUserTurn("Me llamo Manuel")
        assertEquals("café", memory.personalReply("mi bebida"))
        EddyMemoryArchive.get(context).close()
        val reopened = EddyMemory(context)
        assertEquals("café", reopened.personalReply("mi bebida"))
        assertEquals(2L, count("turns"))
        assertEquals(1L, count("notes"))
        assertEquals(1L, count("lessons"))
    }

    @Test fun oldConversationSurvivesTheRollingPromptWindow() {
        val memory = EddyMemory(context)
        repeat(170) { memory.rememberUserTurn("Turno $it") }
        assertEquals(170L, count("turns"))
        val recent = context.getSharedPreferences("eddy_memory", Context.MODE_PRIVATE).getString("conversation_turns_v2", "[]")
        assertEquals(140, JSONArray(recent).length())
    }

    @Test fun damagedLegacySectionIsPreservedWithoutBlockingValidMemories() {
        val archive = EddyMemoryArchive.get(context)
        val damaged = "[{unfinished"
        val lesson = JSONObject().put("question", "mi bebida").put("answer", "café")
        archive.importLegacy(damaged, "[42,\"me gusta el café\"]", JSONArray().put(12).put(lesson).toString())
        archive.appendTurn("user", "Sigo aquí", 1L)
        assertEquals("café", archive.answer("mi bebida"))
        assertEquals(1L, count("turns"))
        assertEquals(1L, count("notes"))
        archive.readableDatabase.rawQuery("SELECT json FROM legacy_backup WHERE key='turns'", null).use {
            assertTrue(it.moveToFirst())
            assertEquals(damaged, it.getString(0))
        }
    }

    @Test fun earlyPersonalAnswersSurviveMoreThanFortyNewLessons() {
        val memory = EddyMemory(context)
        repeat(50) { memory.learnExplicitly("cuando te pregunte dato numero $it, responde valor $it") }
        EddyMemoryArchive.get(context).close()
        assertEquals("valor 0", EddyMemory(context).personalReply("dato numero 0"))
        assertEquals(50L, count("lessons"))
    }

    @Test fun explicitClearRemovesArchiveAndCannotReimportLegacyMemories() {
        val memory = EddyMemory(context)
        memory.rememberUserTurn("Me llamo Manuel")
        memory.learnExplicitly("recordá que me gusta el café")
        memory.learnExplicitly("cuando te pregunte mi bebida, responde café")
        memory.clearAll()
        EddyMemoryArchive.get(context).close()
        assertNull(EddyMemory(context).personalReply("mi bebida"))
        assertEquals(0L, count("turns"))
        assertEquals(0L, count("notes"))
        assertEquals(0L, count("lessons"))
        assertEquals(0L, count("legacy_backup"))
    }

    private fun count(table: String): Long = EddyMemoryArchive.get(context).readableDatabase
        .rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getLong(0) }
}
