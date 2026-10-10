package com.niko.assistant.ai

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class LeoWebOnlyClientTest {
    @Test fun publicResearchNeverReceivesPrivateMemoryOrRetiredKey() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("eddy_ai_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("groq_api_key", "retired-secret").commit()
        val queries = mutableListOf<String>()
        val client = NikoAiClient(context, search = { query ->
            queries.add(query)
            NikoAiReply("No pude recuperar fuentes", false, emptyList())
        })
        client.reply("Cómo cocinar arroz", "Private family memory", true)
        assertEquals(listOf("Cómo cocinar arroz"), queries)
        assertFalse(prefs.contains("groq_api_key"))
        assertNull(client.reply("Hola", "private", false))
        assertNull(client.reply("Explicame eso sin internet", "private", true))
        assertEquals(1, queries.size)
    }
}
