package com.niko.assistant.learning

import android.content.Context
import com.niko.assistant.ai.NikoAiReply
import com.niko.assistant.ai.NikoWebSource
import com.niko.assistant.compat.UpgradeIdentity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class LeoLearningJournalTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Before fun clear() { context.getSharedPreferences(UpgradeIdentity.memoryPreferences, 0).edit().clear().commit() }

    @Test fun feedbackSurvivesRestartButSilenceNeverConfirmsAnAnswer() {
        LeoLearningJournal(context).record("Cómo funciona la gravedad", "Una explicación pendiente de evaluar", 100)
        val restored = LeoLearningJournal(context)
        assertEquals(0, restored.stats().useful)
        assertEquals("REJECTED", restored.feedback(false, 101)?.feedback)
        assertEquals(1, LeoLearningJournal(context).stats().rejected)
        assertNull(restored.feedback(true, 1_000_000))
        assertNull(LeoLearningJournal.assessment("sí"))
        assertNull(LeoLearningJournal.assessment("la página dice eso está mal"))
        assertEquals(false, LeoLearningJournal.assessment("LEO, eso está mal"))
    }

    @Test fun rejectedKnowledgeCannotBeRelearnedFromSameAnswer() {
        val knowledge = NikoKnowledgeStore(context)
        val reply = NikoAiReply("Explicación investigada que el usuario posteriormente rechaza.", true,
            listOf(NikoWebSource("A", "https://a.example/topic"), NikoWebSource("B", "https://b.example/topic")))
        knowledge.learn("Cómo funciona la gravedad", reply)
        assertFalse(knowledge.recall("Cómo funciona la gravedad")!!.verified)
        NikoKnowledgeStore(context).reject("Cómo funciona la gravedad", reply.text)
        knowledge.learn("Explicame la gravedad", reply)
        assertEquals(0, knowledge.size())
        assertTrue(knowledge.isRejected(reply.text))
        knowledge.learn("Cómo funciona la gravedad", reply.copy(text = "Una nueva explicación que debe ser evaluada por separado."))
        assertEquals(1, knowledge.size())
    }

    @Test fun privacyBoundedHistoryAndMemoryDeletion() {
        val journal = LeoLearningJournal(context)
        journal.record("mi contraseña es abc123", "No almacenar")
        assertEquals(0, journal.stats().interactions)
        repeat(140) { journal.record("Pregunta de aprendizaje", "Respuesta pendiente", it.toLong()) }
        assertEquals(128, journal.stats().interactions)
        journal.recordTraining(true)
        journal.recordTraining(false)
        assertEquals(1, journal.stats().acceptedUpdates)
        // Same preference identity used by NikoMemory.clearAll.
        context.getSharedPreferences(UpgradeIdentity.memoryPreferences, 0).edit().clear().commit()
        assertEquals(0, LeoLearningJournal(context).stats().interactions)
        assertEquals(0, journal.stats().acceptedUpdates)
    }
}
