package com.niko.assistant.memory

import com.niko.assistant.compat.UpgradeIdentity

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.niko.assistant.ai.ConversationContext
import com.niko.assistant.ai.ConversationTurn
import com.niko.assistant.brain.AssistantCommand
import com.niko.assistant.brain.SupportedApp
import com.niko.assistant.voice.NikoEmotionEngine
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class NikoMemory(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(UpgradeIdentity.memoryPreferences, Context.MODE_PRIVATE)

    private val archive by lazy {
        NikoMemoryArchive.get(appContext).also {
            it.importLegacy(
                prefs.getString(KEY_TURNS, "[]").orEmpty().ifBlank { "[]" },
                prefs.getString("explicit_notes_v1", "[]").orEmpty().ifBlank { "[]" },
                prefs.getString("personal_lessons_v1", "[]").orEmpty().ifBlank { "[]" },
            )
        }
    }
    private val longTerm by lazy { NikoLongTermMemory(archive) }

    fun rememberUtterance(text: String) = rememberUserTurn(text)

    fun rememberUserTurn(text: String) {
        val cleaned = text.trim().take(8_000)
        if (cleaned.isBlank()) return
        appendTurn("user", cleaned)
        learnFacts(cleaned)
        longTerm.observeUserTurn(cleaned)
        prefs.edit().putInt(KEY_TOTAL_INTERACTIONS, prefs.getInt(KEY_TOTAL_INTERACTIONS, 0) + 1).apply()
    }

    fun rememberAssistantTurn(text: String) {
        val cleaned = text.trim().take(8_000)
        if (cleaned.isBlank()) return
        appendTurn("assistant", cleaned)
    }

    fun rememberLearnedAnswer(question: String, answer: String, webDerived: Boolean) {
        val key = normalizeKnowledgeKey(question)
        val cleanAnswer = answer.trim()
        if (key.length < 3 || cleanAnswer.isBlank()) return
        val entries = readLearnedAnswers().toMutableList()
        entries.removeAll { it.key == key }
        entries += LearnedAnswer(key, cleanAnswer.take(MAX_LEARNED_ANSWER_CHARS), webDerived, System.currentTimeMillis())
        while (entries.size > MAX_LEARNED_ANSWERS) entries.removeAt(0)
        saveLearnedAnswers(entries)
    }

    fun recallLearnedAnswer(question: String): String? {
        val key = normalizeKnowledgeKey(question)
        if (key.length < 3) return null
        val now = System.currentTimeMillis()
        var changed = false
        val valid = readLearnedAnswers().filter { item ->
            val ttl = if (item.webDerived) WEB_ANSWER_TTL_MS else GENERAL_ANSWER_TTL_MS
            val keep = item.timestamp > 0L && now - item.timestamp <= ttl
            if (!keep) changed = true
            keep
        }
        if (changed) saveLearnedAnswers(valid)
        valid.firstOrNull { it.key == key }?.let { return it.answer }
        return valid.map { it to tokenSimilarity(key, it.key) }.maxByOrNull { it.second }
            ?.takeIf { it.second >= LEARNED_MATCH_THRESHOLD }?.first?.answer
    }

    fun rememberCommand(command: AssistantCommand) {
        val key = commandKey(command) ?: return
        increment("command_$key")
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        increment("command_${key}_hour_$hour")
    }

    /** Procedural memory is written only after the action returned a successful spoken result. */
    fun rememberCompletedCommand(command: AssistantCommand, spokenResult: String) {
        val key = commandKey(command) ?: return
        if (command is AssistantCommand.Unknown || command == AssistantCommand.ClearMemory) return
        val description = commandDescription(command)
        if (description.isBlank()) return
        longTerm.rememberProcedure(
            key,
            "Acción aprendida: $description. Resultado reciente: ${spokenResult.trim().take(220)}",
        )
    }

    fun describeLearnedPatterns(): String {
        val total = prefs.getInt(KEY_TOTAL_INTERACTIONS, 0)
        if (total == 0) return "Todavía estoy empezando a conocerte. Hablá conmigo y voy recordando localmente lo importante para ayudarte mejor."
        val facts = learnedFacts()
        val factText = facts.entries.take(4).joinToString("; ") { (key, value) -> "${factLabel(key)} $value" }
        val favoriteAction = actionCandidates().map { (key, label) -> prefs.getInt("command_$key", 0) to label }
            .maxByOrNull { it.first }?.takeIf { it.first > 0 }
        val learnedCount = archive.lessonCount()
        val semanticCount = longTerm.count()
        return buildString {
            append("He registrado $total interacciones con vos.")
            if (factText.isNotBlank()) append(" Recuerdo que $factText.")
            if (favoriteAction != null) append(" Lo que más me has pedido hasta ahora es ${favoriteAction.second}.")
            if (learnedCount > 0) append(" Me enseñaste $learnedCount respuestas personales.")
            if (semanticCount > 0) append(" Mantengo $semanticCount recuerdos relevantes organizados localmente.")
            readNotes().takeLast(3).takeIf { it.isNotEmpty() }?.let { append(" Me pediste recordar: ${it.joinToString("; ")}.") }
            append(" Esta memoria se guarda en tu teléfono.")
        }
    }

    fun historyForAi(currentMessage: String): List<ConversationTurn> = ConversationContext.history(
        readTurns().map { ConversationTurn(it.role, it.text) }, currentMessage,
    )

    fun contextForAi(includeDialogue: Boolean = true, currentMessage: String = ""): String {
        val facts = learnedFacts().entries.joinToString("\n") { (key, value) -> "${factLabel(key)} $value" }
            .take(if (includeDialogue) 260 else 1_200)
        val notes = readNotes().takeLast(6).asReversed().joinToString("\n")
            .take(if (includeDialogue) 180 else 3_000)
        val relevant = longTerm.context(currentMessage, if (includeDialogue) 6 else 9)
            .take(if (includeDialogue) 1_500 else 3_500)
        val tone = NikoEmotionEngine.latest(appContext)
        val toneBlock = tone?.let { "Tono acústico aproximado: ${it.tone.label}. No es una emoción confirmada." }
            ?: "Sin señal acústica reciente."
        val dialogue = if (includeDialogue) {
            ConversationContext.history(historyForAi(currentMessage), "", 250)
                .joinToString("\n") { "${it.role}: ${it.text}" }
        } else ""
        return buildString {
            appendLine("TONO LOCAL:")
            appendLine(toneBlock)
            appendLine("MEMORIA CORE:")
            appendLine(facts)
            appendLine("MEMORIA RELEVANTE:")
            appendLine(relevant)
            appendLine("NOTAS PEDIDAS POR EL USUARIO:")
            appendLine(notes)
            appendLine("DIÁLOGO RECIENTE:")
            append(dialogue)
        }.trim()
    }

    /** Teaching is explicit and answers are recalled exactly, never fuzzy-matched into actions. */
    fun learnExplicitly(text: String): String? {
        MemoryLearning.lesson(text)?.let { lesson ->
            archive.rememberLesson(lesson)
            longTerm.rememberExplicitNote("${lesson.question}: ${lesson.answer}")
            return "Aprendido. Cuando me preguntés ${lesson.question}, responderé: ${lesson.answer}"
        }
        MemoryLearning.note(text)?.let { note ->
            archive.rememberNote(note)
            longTerm.rememberExplicitNote(note)
            return "Lo recordaré: $note"
        }
        return null
    }

    fun personalReply(text: String): String? {
        val key = MemoryLearning.key(text)
        archive.answer(text)?.let { return it }
        val fact = when (key) {
            "como me llamo", "cual es mi nombre" -> "name"
            "que me gusta" -> "likes"
            "que no me gusta" -> "dislikes"
            "donde vivo" -> "lives"
            "que prefiero" -> "prefers"
            "que estudio" -> "studies"
            else -> null
        } ?: return null
        return learnedFacts()[fact]?.let { "Recuerdo que ${factLabel(fact)} $it." }
            ?: "Todavía no tengo ese dato. Podés enseñármelo diciendo recordá que, seguido del dato."
    }

    private fun readNotes(): List<String> = archive.recentNotes()

    fun shouldScheduleProactive(command: AssistantCommand): Boolean {
        val key = commandKey(command) ?: return false
        if (!isProactiveEligible(command) || prefs.getBoolean("proactive_scheduled_$key", false)) return false
        val totalForCommand = prefs.getInt("command_$key", 0)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val sameHourCount = prefs.getInt("command_${key}_hour_$hour", 0)
        return totalForCommand >= 3 && sameHourCount >= 2
    }

    fun markProactiveScheduled(command: AssistantCommand) {
        val key = commandKey(command) ?: return
        prefs.edit().putBoolean("proactive_scheduled_$key", true).apply()
    }

    fun proactiveMessage(command: AssistantCommand): String? = when (command) {
        is AssistantCommand.OpenApp -> "Sos de abrir ${command.app.displayName} a esta hora. NIKO está listo si lo ocupás."
        AssistantCommand.OpenCamera -> "Sos de usar la cámara a esta hora. Aquí estoy por si la ocupás."
        is AssistantCommand.OpenMaps -> "Sos de consultar mapas a esta hora. Puedo ayudarte con una ruta cuando querás."
        else -> null
    }

    fun clearAll() {
        cancelProactiveSchedules()
        archive.clearMemory()
        prefs.edit().clear().apply()
    }

    private fun readLearnedAnswers(): List<LearnedAnswer> {
        val raw = prefs.getString(KEY_LEARNED_ANSWERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                LearnedAnswer(
                    item.optString("key"),
                    item.optString("answer"),
                    item.optBoolean("web", false),
                    item.optLong("timestamp", 0L),
                )
            }.filter { it.key.isNotBlank() && it.answer.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun saveLearnedAnswers(entries: List<LearnedAnswer>) {
        val array = JSONArray()
        entries.forEach { item ->
            array.put(
                JSONObject()
                    .put("key", item.key)
                    .put("answer", item.answer)
                    .put("web", item.webDerived)
                    .put("timestamp", item.timestamp),
            )
        }
        prefs.edit().putString(KEY_LEARNED_ANSWERS, array.toString()).apply()
    }

    private fun normalizeKnowledgeKey(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("(?i)\\b(?:niko|nico|por favor|porfa|haceme el favor|hazme el favor)\\b"), " ")
            .replace(Regex("[^a-z0-9ñ ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokenSimilarity(left: String, right: String): Double {
        if (left == right) return 1.0
        val a = left.split(' ').filter { it.length > 1 }.toSet()
        val b = right.split(' ').filter { it.length > 1 }.toSet()
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size.toDouble().coerceAtLeast(1.0)
    }

    private fun cancelProactiveSchedules() {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val requestCodes = buildList {
            SupportedApp.entries.forEach { add(1_000 + it.ordinal) }
            add(2_001)
            add(2_002)
        }
        requestCodes.forEach { requestCode ->
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                UpgradeIdentity.proactiveReceiver(appContext),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun appendTurn(role: String, text: String) {
        archive.appendTurn(role, text, System.currentTimeMillis())
        val current = readTurns().toMutableList()
        current.add(MemoryTurn(role, text, System.currentTimeMillis()))
        while (current.size > MAX_TURNS) current.removeAt(0)
        val array = JSONArray()
        current.forEach {
            array.put(JSONObject().put("role", it.role).put("text", it.text).put("timestamp", it.timestamp))
        }
        prefs.edit().putString(KEY_TURNS, array.toString()).apply()
    }

    private fun readTurns(): List<MemoryTurn> {
        val raw = prefs.getString(KEY_TURNS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                MemoryTurn(
                    item.optString("role", "user"),
                    item.optString("text", "").take(8_000),
                    item.optLong("timestamp", 0L),
                )
            }.filter { it.text.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun learnFacts(text: String) {
        val updates = MemoryLearning.facts(text)
        if (updates.isEmpty()) return
        val editor = prefs.edit()
        updates.forEach { (key, value) -> editor.putString("fact_$key", value) }
        // A correction such as "no me gusta el café" must not retain the opposite memory.
        updates["dislikes"]?.let { dislike ->
            if (MemoryLearning.key(prefs.getString("fact_likes", "").orEmpty()) == MemoryLearning.key(dislike)) {
                editor.remove("fact_likes")
            }
        }
        updates["likes"]?.let { like ->
            if (MemoryLearning.key(prefs.getString("fact_dislikes", "").orEmpty()) == MemoryLearning.key(like)) {
                editor.remove("fact_dislikes")
            }
        }
        editor.apply()
    }

    private fun learnedFacts(): Map<String, String> {
        val keys = listOf("name", "likes", "dislikes", "prefers", "lives", "work", "studies")
        return buildMap {
            keys.forEach { key ->
                prefs.getString("fact_$key", null)?.takeIf { it.isNotBlank() }?.let { put(key, it) }
            }
        }
    }

    private fun factLabel(key: String): String = when (key) {
        "name" -> "tu nombre es"
        "likes" -> "te gusta"
        "dislikes" -> "no te gusta"
        "prefers" -> "preferís"
        "lives" -> "vivís en"
        "work" -> "trabajás en/como"
        "studies" -> "estudiás"
        else -> key
    }

    private fun actionCandidates(): List<Pair<String, String>> = buildList {
        SupportedApp.entries.forEach { add("app_${it.name}" to "abrir ${it.displayName}") }
        add("app_dynamic" to "abrir aplicaciones")
        add("camera" to "usar la cámara")
        add("maps" to "usar mapas")
        add("alarm" to "crear alarmas")
        add("timer" to "crear temporizadores")
        add("whatsapp" to "usar WhatsApp")
        add("spotify" to "poner música en Spotify")
        add("torch" to "usar la linterna")
        add("smart_home" to "controlar la casa inteligente")
    }

    private fun commandDescription(command: AssistantCommand): String = when (command) {
        is AssistantCommand.OpenApp -> "abrir ${command.app.displayName}"
        is AssistantCommand.OpenAppByName -> "abrir la aplicación ${command.name}"
        AssistantCommand.OpenCamera -> "abrir la cámara"
        is AssistantCommand.Dial -> "abrir una llamada al número solicitado"
        is AssistantCommand.ComposeMessage -> "preparar un mensaje"
        is AssistantCommand.WhatsAppMessage -> "preparar un mensaje de WhatsApp"
        is AssistantCommand.PlaySpotify -> "buscar y reproducir ${command.query} en Spotify"
        is AssistantCommand.SetAlarm -> "crear una alarma"
        is AssistantCommand.SetTimer -> "crear un temporizador"
        is AssistantCommand.OpenMaps -> "abrir mapas para ${command.query}"
        is AssistantCommand.ShareText -> "compartir texto"
        is AssistantCommand.SetTorch -> if (command.enabled) "encender la linterna" else "apagar la linterna"
        is AssistantCommand.SetVolume, is AssistantCommand.AdjustVolume -> "ajustar el volumen"
        is AssistantCommand.SetBrightness -> "ajustar el brillo"
        is AssistantCommand.OpenSystemPanel -> "abrir un panel del sistema"
        is AssistantCommand.NavigateDevice -> "navegar por el teléfono"
        is AssistantCommand.AutomateUi -> "manejar la pantalla: ${command.task.take(80)}"
        AssistantCommand.BatteryStatus -> "consultar batería"
        is AssistantCommand.Vibrate -> "hacer vibrar el teléfono"
        is AssistantCommand.SmartHomeControl -> "controlar ${command.target}"
        AssistantCommand.OpenSmartHomeSettings -> "abrir ajustes de casa inteligente"
        AssistantCommand.OpenAiSettings -> "abrir ajustes de IA"
        AssistantCommand.TellTime -> "decir la hora"
        AssistantCommand.Greeting -> "saludar"
        AssistantCommand.MemorySummary -> "resumir la memoria local"
        AssistantCommand.ClearMemory -> ""
        is AssistantCommand.SearchWeb -> "buscar ${command.query} en Internet"
        is AssistantCommand.Unknown -> ""
    }

    private fun commandKey(command: AssistantCommand): String? = when (command) {
        is AssistantCommand.OpenApp -> "app_${command.app.name}"
        is AssistantCommand.OpenAppByName -> "app_dynamic"
        AssistantCommand.OpenCamera -> "camera"
        AssistantCommand.TellTime -> "time"
        AssistantCommand.Greeting -> "greeting"
        AssistantCommand.MemorySummary -> "memory_summary"
        AssistantCommand.ClearMemory -> "clear_memory"
        is AssistantCommand.Dial -> "dial"
        is AssistantCommand.ComposeMessage -> "message"
        is AssistantCommand.WhatsAppMessage -> "whatsapp"
        is AssistantCommand.PlaySpotify -> "spotify"
        is AssistantCommand.SetAlarm -> "alarm"
        is AssistantCommand.SetTimer -> "timer"
        is AssistantCommand.OpenMaps -> "maps"
        is AssistantCommand.SearchWeb -> "web_search"
        is AssistantCommand.ShareText -> "share"
        is AssistantCommand.SetTorch -> "torch"
        is AssistantCommand.SetVolume, is AssistantCommand.AdjustVolume -> "volume"
        is AssistantCommand.SetBrightness -> "brightness"
        is AssistantCommand.OpenSystemPanel -> "system_panel"
        is AssistantCommand.NavigateDevice -> "device_navigation"
        is AssistantCommand.AutomateUi -> "ui_automation"
        AssistantCommand.BatteryStatus -> "battery"
        is AssistantCommand.Vibrate -> "vibrate"
        is AssistantCommand.SmartHomeControl -> "smart_home"
        AssistantCommand.OpenSmartHomeSettings -> "smart_home_settings"
        AssistantCommand.OpenAiSettings -> "ai_settings"
        is AssistantCommand.Unknown -> "conversation"
    }

    private fun isProactiveEligible(command: AssistantCommand): Boolean =
        command is AssistantCommand.OpenApp || command == AssistantCommand.OpenCamera || command is AssistantCommand.OpenMaps

    private fun increment(key: String) {
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    private data class MemoryTurn(val role: String, val text: String, val timestamp: Long)
    private data class LearnedAnswer(val key: String, val answer: String, val webDerived: Boolean, val timestamp: Long)

    companion object {
        private const val KEY_TURNS = "conversation_turns_v2"
        private const val KEY_TOTAL_INTERACTIONS = "total_interactions"
        private const val KEY_LEARNED_ANSWERS = "learned_answers_v1"
        private const val MAX_TURNS = 140
        private const val MAX_LEARNED_ANSWERS = 80
        private const val MAX_LEARNED_ANSWER_CHARS = 2_000
        private const val WEB_ANSWER_TTL_MS = 6L * 60L * 60L * 1_000L
        private const val GENERAL_ANSWER_TTL_MS = 30L * 24L * 60L * 60L * 1_000L
        private const val LEARNED_MATCH_THRESHOLD = 0.88
    }
}
