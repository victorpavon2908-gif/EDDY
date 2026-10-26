package com.niko.assistant.cognitive

import com.niko.assistant.actions.LeoLabProtocols
import com.niko.assistant.agent.TurnController
import com.niko.assistant.ai.NikoAiReply
import com.niko.assistant.devicecontrol.LeoLiveVisionAnalyzer
import com.niko.assistant.devicecontrol.LeoUiAction
import com.niko.assistant.devicecontrol.LeoUiSnapshot
import com.niko.assistant.devicecontrol.LeoUiStep
import com.niko.assistant.devicecontrol.LeoUiStepPlanner
import com.niko.assistant.devicecontrol.NikoUiTaskPolicy
import com.niko.assistant.learning.LeoAutodidactEngine
import com.niko.assistant.memory.LeoEncryptedSync
import com.niko.assistant.memory.LeoEpisodicTimeline
import com.niko.assistant.memory.LeoLocalRagEngine
import com.niko.assistant.voice.LeoAcousticWakeScorer
import com.niko.assistant.voice.LeoDuplexPolicy
import com.niko.assistant.voice.LeoHardwareBenchmark
import com.niko.assistant.voice.TranscriptQuality
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoSovereignUpgradesTest {

    private class MemoryStoreMap : LeoPersonalMemory.Store {
        val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String) { values[key] = value }
        override fun delete(key: String) { values.remove(key) }
    }

    @Test
    fun emotionalHeartTracksMultiTurnTrajectoryAndModulatesReplies() {
        val heart = LeoEmotionalHeart()
        val first = heart.observe("me siento muy triste y bajoneado hoy")
        assertNotNull(first)
        assertTrue(first!!.valence < 0.0)
        assertEquals(LeoEmotionalHeart.Trajectory.INITIAL, first.trajectory)

        val second = heart.observe("sigo estresado y preocupado por el trabajo")
        assertNotNull(second)
        assertEquals(LeoEmotionalHeart.Trajectory.PERSISTENT_DISTRESS, second!!.trajectory)
        val modulated = heart.modulateReply("me siento agobiado", "Aquí tenés el resumen de tus tareas.")
        assertTrue(modulated.contains("Aquí tenés el resumen"))
        assertTrue(modulated.length > "Aquí tenés el resumen de tus tareas.".length)

        val recovery = heart.observe("ya estoy muy feliz y contento con el resultado")
        assertNotNull(recovery)
        assertEquals(LeoEmotionalHeart.Trajectory.RECOVERY, recovery!!.trajectory)
        assertTrue(recovery.standaloneReply.contains("pasaste ese mal rato"))
    }

    @Test
    fun personalMemorySupportsProjectStudyPetAndNaturalIntroduction() {
        val store = MemoryStoreMap()
        val memory = LeoPersonalMemory(store)

        val intro = memory.reply("Leo, me llamo Mario")
        assertNotNull(intro)
        assertEquals("Mario", store.values["name"])
        assertEquals("Tu nombre es Mario.", memory.reply("¿Cómo me llamo?"))

        memory.reply("mi proyecto actual es LEO Soberano")
        assertEquals("Tu proyecto actual es LEO Soberano.", memory.reply("¿En qué proyecto estoy?"))

        memory.reply("mi mascota es Balto")
        assertEquals("Tu mascota es Balto.", memory.reply("¿Cuál es mi mascota?"))
    }

    @Test
    fun autodidactEngineLearnsRulesRunsSelfTrainingAndExportsJson() = runBlocking {
        val autodidact = LeoAutodidactEngine()
        val learned = autodidact.interact("Leo, aprendé que cuando te diga modo enfoque me respondás activando concentración total")
        assertNotNull(learned)
        assertTrue(learned!!.contains("Aprendido"))

        val triggered = autodidact.interact("Leo, modo enfoque")
        assertEquals("activando concentración total", triggered)

        val report = autodidact.runSelfTrainingCycle()
        assertTrue(report.epochsRun >= 1)
        assertEquals(1, report.taughtRulesCount)

        val exported = autodidact.exportJson()
        val restored = LeoAutodidactEngine()
        restored.importJson(exported)
        assertEquals("activando concentración total", restored.interact("modo enfoque"))

        val forgotten = restored.interact("olvida la regla de modo enfoque")
        assertNotNull(forgotten)
        assertTrue(forgotten!!.contains("Olvidé"))
        assertNull(restored.interact("modo enfoque"))
    }

    @Test
    fun localMultimodalRagIndexesDocumentsAndAnswersWithCitations() = runBlocking {
        val rag = LeoLocalRagEngine()
        val ingestReply = rag.handleCommand(
            "guardá en mis documentos el PDF Manual Fotovoltaico: El regulador MPPT optimiza la carga de las baterías de litio a 48 voltios. El inversor híbrido entrega 220 voltios de corriente alterna."
        )
        assertNotNull(ingestReply)
        assertTrue(ingestReply!!.contains("Manual Fotovoltaico"))

        val queryReply = rag.handleCommand("¿Qué dice en mis documentos sobre el regulador MPPT y las baterías?")
        assertNotNull(queryReply)
        assertTrue(queryReply!!.contains("48 voltios"))
        assertTrue(queryReply.contains("Manual Fotovoltaico"))

        // Also verify integration through LeoNeuralCore
        val core = LeoNeuralCore(TurnController(), rag = rag)
        val coreReply = core.reply(
            LeoNeuralCore.Request("buscá en mis documentos inversor híbrido", true, false, false),
            object : LeoNeuralCore.Knowledge {
                override suspend fun local(): String? = "fallback"
                override suspend fun research(requireSources: Boolean) = null
                override suspend fun fallback() = "fallback"
            }
        )
        assertTrue(coreReply.text.contains("220 voltios"))
    }

    @Test
    fun encryptedSyncRoundtripsAndRejectsWrongPassphraseOrTampering() {
        val payload = LeoEncryptedSync.SyncPayload(
            preferences = mapOf("name" to "Mario", "project" to "LEO"),
            autodidactJson = """{"rules":[]}""",
            ragDocumentsJson = """{"docs":[]}""",
            exportedAtMs = 1760000000000L,
        )
        val bundle = LeoEncryptedSync.encrypt(payload, "ClaveSoberana2026!")
        assertTrue(bundle.startsWith("LEOSYNC1."))

        val decrypted = LeoEncryptedSync.decrypt(bundle, "ClaveSoberana2026!")
        assertEquals("Mario", decrypted.preferences["name"])
        assertEquals("LEO", decrypted.preferences["project"])
        assertEquals(1760000000000L, decrypted.exportedAtMs)

        // Wrong passphrase must fail
        val wrongPass = runCatching {
            LeoEncryptedSync.decrypt(bundle, "ClaveIncorrecta999")
        }.isFailure
        assertTrue(wrongPass)

        // Tampered ciphertext must fail GCM tag verification
        val tampered = bundle.dropLast(4) + "AAAA"
        val tamperedResult = runCatching {
            LeoEncryptedSync.decrypt(tampered, "ClaveSoberana2026!")
        }.isFailure
        assertTrue(tamperedResult)
    }

    @Test
    fun acousticWakeScorerDetectsTwoSyllableVocalWakeAndRejectsSilence() {
        val sampleRate = 16_000
        val silence = ShortArray(sampleRate) { 0 }
        val silenceScore = LeoAcousticWakeScorer.score(silence, sampleRate)
        assertFalse(silenceScore.detected)
        assertEquals("silence_or_low_snr", silenceScore.reason)

        // Simulate a 480 ms two-syllable vocal utterance ("Le-o") at 220 Hz + 440 Hz with a brief dip
        val wakeSamples = ShortArray(sampleRate) { i ->
            val timeSec = i.toDouble() / sampleRate
            val env = when {
                timeSec in 0.10..0.28 -> 0.55 // "Le-"
                timeSec in 0.28..0.34 -> 0.08 // inter-syllable dip
                timeSec in 0.34..0.52 -> 0.50 // "-o"
                else -> 0.002
            }
            val wave = 0.65 * sin(2.0 * PI * 220.0 * timeSec) + 0.35 * sin(2.0 * PI * 440.0 * timeSec)
            (env * wave * 18000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        val wakeScore = LeoAcousticWakeScorer.score(wakeSamples, sampleRate)
        assertTrue("Expected acoustic wake match but got $wakeScore", wakeScore.detected)
        assertTrue(wakeScore.syllablePeaks >= 2)
    }

    @Test
    fun hardwareBenchmarkEvaluatesProtocolAndRecommendsVoiceTuning() {
        val ideal = LeoHardwareBenchmark.BenchmarkMetrics(
            wakeLatencyP95Ms = 420L,
            falseWakesPerHour = 0.0,
            bargeInStopLatencyMs = 140L,
            uiFrameP95Ms = 11.4,
            batteryDrainPercentPerHour = 3.8,
            echoRejectionDb = 24.5,
        )
        val report = LeoHardwareBenchmark.evaluate(ideal)
        assertTrue(report.passedAll)
        assertEquals(100, report.scorePercent)
        assertTrue(LeoHardwareBenchmark.toCsvRow("Pixel 8", ideal).endsWith(",PASS"))

        val noisySlow = LeoHardwareBenchmark.BenchmarkMetrics(
            wakeLatencyP95Ms = 880L,
            falseWakesPerHour = 1.5,
            bargeInStopLatencyMs = 210L,
            uiFrameP95Ms = 14.0,
            batteryDrainPercentPerHour = 4.2,
            echoRejectionDb = 14.0,
        )
        val noisyReport = LeoHardwareBenchmark.evaluate(noisySlow)
        assertFalse(noisyReport.passedAll)
        assertTrue(noisyReport.recommendedTuning.keywordScore > 1.50f)
    }

    @Test
    fun fullDuplexAcousticEchoAndAsrRepairAndChainedScreenPlannerWorkSafely() {
        assertTrue(LeoDuplexPolicy.canInterrupt("espera un momento", "Estoy leyendo el artículo"))
        assertTrue(LeoDuplexPolicy.canInterrupt("cambiando de tema", "Estoy leyendo el artículo"))
        assertTrue(LeoDuplexPolicy.isAcousticEcho(micRms = 0.04f, playbackRms = 0.05f, spectralSimilarity = 0.85f))
        assertFalse(LeoDuplexPolicy.canInterruptFullDuplex("espera", "texto", micRms = 0.04f, playbackRms = 0.05f, spectralSimilarity = 0.85f))

        assertEquals("Oye Leo abrí WhatsApp y YouTube", TranscriptQuality.repairAsrTranscript("oye lio abrí wasap y yutu"))

        // Safe confirmation policy for UI actions
        val safeNav = NikoUiTaskPolicy.evaluateWithConfirmation("tocá Buscar")
        assertTrue(safeNav.allowed)
        assertFalse(safeNav.requiresConfirmation)

        val confirmNeeded = NikoUiTaskPolicy.evaluateWithConfirmation("borrá el campo de búsqueda")
        assertFalse(confirmNeeded.allowed)
        assertTrue(confirmNeeded.requiresConfirmation)
        assertTrue(NikoUiTaskPolicy.evaluateWithConfirmation("borrá el campo de búsqueda", confirmedByUser = true).allowed)

        // Autonomous chained step planner
        val planner = LeoUiStepPlanner()
        val snap = LeoUiSnapshot(
            packageName = "com.example.maps",
            tree = """
                Package: com.example.maps
                [node_1] ImageButton [desc="Buscar", clickable]
                [node_2] EditText [text="Buscar dirección", editable]
                [node_3] Button [text="Pagar", clickable]
            """.trimIndent(),
            nodeCount = 3,
            snapshotId = 1L,
            uiRevision = 1L,
            signature = "sig-1",
        )
        val task = "tocá Buscar y luego escribí Managua"
        val step1 = planner.planLocalChainStep(task, snap, history = "")
        assertEquals(LeoUiStep.Do(LeoUiAction.CLICK, "node_1"), step1)

        val step2 = planner.planLocalChainStep(task, snap, history = "CLICK|node_1")
        assertEquals(LeoUiStep.Do(LeoUiAction.TYPE, "node_2", "Managua"), step2)

        val step3 = planner.planLocalChainStep(task, snap, history = "CLICK|node_1\nTYPE|node_2|Managua")
        assertTrue(step3 is LeoUiStep.Done)
    }

    @Test
    fun eddyPersonaLabProtocolsLiveVisionAndEpisodicTimelineWorkTogether() = runBlocking {
        val core = LeoNeuralCore(TurnController())
        val dummyKnowledge = object : LeoNeuralCore.Knowledge {
            override suspend fun local(): String? = "respuesta local"
            override suspend fun research(requireSources: Boolean): NikoAiReply? =
                NikoAiReply("Los paneles solares bifaciales incrementan la captación un 15 por ciento.", true, emptyList())
            override suspend fun fallback() = "fallback"
        }

        // 1. Eddy Persona switch & styling
        val missionSwitch = core.reply(LeoNeuralCore.Request("Leo, activa modo misión", true, false, false), dummyKnowledge)
        assertTrue(missionSwitch.text.contains("CENTRO DE MANDO LEO"))
        assertEquals(LeoEddyPersona.PersonaMode.MISSION_CONTROL, core.persona.mode)

        val eddySwitch = core.reply(LeoNeuralCore.Request("activa modo eddy", true, false, false), dummyKnowledge)
        assertTrue(eddySwitch.text.contains("Modo Eddy activado"))
        assertEquals("Pan comido, Mario: Calculado.", core.persona.styleReply("Calculado.", userName = "Mario", humorAllowed = true, distressed = false))
        assertEquals("Calculado.", core.persona.styleReply("Calculado.", userName = "Mario", humorAllowed = false, distressed = false))
        assertEquals("Calculado.", core.persona.styleReply("Calculado.", userName = "Mario", humorAllowed = true, distressed = true))

        // 2. Lab Protocols & Sensor Awareness
        val nightReply = core.reply(LeoNeuralCore.Request("Leo, protocolo nocturno", true, false, false), dummyKnowledge)
        assertTrue(nightReply.text.contains("PROTOCOLO NOCTURNO ACTIVO"))
        assertEquals("night", core.protocols.lastExecutedProtocolId)

        val alerts = core.protocols.evaluateEnvironment(
            LeoLabProtocols.SensorSnapshot(ambientLux = 1.2f, batteryTempC = 43.0f, batteryPercent = 9, accelMagnitudeG = 3.8f)
        )
        assertEquals(4, alerts.size)
        assertTrue(alerts.any { it.code == "IMPACT_DETECTED" })
        assertTrue(alerts.any { it.code == "THERMAL_WARNING" })

        // 3. Live Vision ("Ojos de Eddy") & OCR indexing into RAG
        core.vision.updateFrame(
            LeoLiveVisionAnalyzer.CameraFrameObservation(
                meanLuminance = 140f,
                ocrBlocks = listOf("Voltaje nominal: 48V DC", "Corriente máxima: 60 amperios"),
                detectedObjects = listOf("inversor solar", "tablero eléctrico"),
                facesCount = 1,
                timestampMs = 12345L,
            )
        )
        val sceneReply = core.reply(LeoNeuralCore.Request("¿Qué estás viendo?", true, false, false), dummyKnowledge)
        assertTrue(sceneReply.text.contains("1 persona"))
        assertTrue(sceneReply.text.contains("inversor solar"))
        assertTrue(sceneReply.text.contains("48V DC"))

        val scanReply = core.reply(LeoNeuralCore.Request("escaneá este documento", true, false, false), dummyKnowledge)
        assertTrue(scanReply.text.contains("Escaneé e indexé"))
        val ragLookup = core.rag.answer("¿Cuál es la corriente máxima del tablero?")
        assertNotNull(ragLookup)
        assertTrue(ragLookup!!.contains("60 amperios"))

        // 4. Continuous Knowledge Distillation & Episodic Timeline
        core.timeline.distillKnowledge("paneles bifaciales", "Los paneles solares bifaciales incrementan la captación un 15 por ciento.")
        val timelineReply = core.reply(LeoNeuralCore.Request("¿Qué hicimos hoy?", true, false, false), dummyKnowledge)
        assertTrue(timelineReply.text.contains("Protocolo Nocturno"))
        assertTrue(timelineReply.text.contains("paneles bifaciales"))

        val serialized = core.timeline.exportSerialized()
        val restoredTimeline = LeoEpisodicTimeline()
        restoredTimeline.importSerialized(serialized)
        assertEquals(1, restoredTimeline.distilledFactsCount)
        assertTrue(restoredTimeline.recentEpisodes().isNotEmpty())
    }
}
