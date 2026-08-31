package com.eddy.assistant.memory

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.eddy.assistant.brain.AssistantCommand
import com.eddy.assistant.brain.SupportedApp
import com.eddy.assistant.proactive.EddyProactiveReceiver
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class EddyMemory(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("eddy_memory", Context.MODE_PRIVATE)

    fun rememberUtterance(text: String) = rememberUserTurn(text)

    fun rememberUserTurn(text: String) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return
        appendTurn("user", cleaned)
        learnFacts(cleaned)
        prefs.edit()
            .putInt(KEY_TOTAL_INTERACTIONS, prefs.getInt(KEY_TOTAL_INTERACTIONS, 0) + 1)
            .apply()
    }

    fun rememberAssistantTurn(text: String) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return
        appendTurn("assistant", cleaned)
    }

    /** Guarda localmente una respuesta obtenida por IA para no volver a consumir API. */
    fun rememberLearnedAnswer(question: String, answer: String, webDerived: Boolean) {
        val key = normalizeKnowledgeKey(question)
        val cleanAnswer = answer.trim()
        if (key.length < 3 || cleanAnswer.isBlank()) return

        val entries = readLearnedAnswers().toMutableList()
        entries.removeAll { it.key == key }
        entries += LearnedAnswer(
            key = key,
            answer = cleanAnswer.take(MAX_LEARNED_ANSWER_CHARS),
            webDerived = webDerived,
            timestamp = System.currentTimeMillis(),
        )
        while (entries.size > MAX_LEARNED_ANSWERS) entries.removeAt(0)
        saveLearnedAnswers(entries)
    }

    /**
     * Busca primero en el conocimiento guardado en el teléfono. Las respuestas derivadas
     * de web caducan rápido; las respuestas generales duran más tiempo.
     */
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

        // Exacto primero; después una similitud conservadora para variaciones pequeñas.
        valid.firstOrNull { it.key == key }?.let { return it.answer }
        return valid
            .map { it to tokenSimilarity(key, it.key) }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= LEARNED_MATCH_THRESHOLD }
            ?.first
            ?.answer
    }

    fun rememberCommand(command: AssistantCommand) {
        val key = commandKey(command) ?: return
        increment("command_$key")
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        increment("command_${key}_hour_$hour")
    }

    fun describeLearnedPatterns(): String {
        val total = prefs.getInt(KEY_TOTAL_INTERACTIONS, 0)
        if (total == 0) {
            return "Todavía estoy empezando a conocerte. Hablá conmigo y voy recordando localmente lo importante para ayudarte mejor."
        }

        val facts = learnedFacts()
        val factText = facts.entries
            .take(4)
            .joinToString("; ") { (key, value) -> "${factLabel(key)} $value" }

        val favoriteAction = actionCandidates()
            .map { (key, label) -> prefs.getInt("command_$key", 0) to label }
            .maxByOrNull { it.first }
            ?.takeIf { it.first > 0 }
        val learnedCount = readLearnedAnswers().size

        return buildString {
            append("He registrado $total interacciones con vos.")
            if (factText.isNotBlank()) append(" Recuerdo que $factText.")
            if (favoriteAction != null) append(" Lo que más me has pedido hasta ahora es ${favoriteAction.second}.")
            if (learnedCount > 0) append(" También guardé $learnedCount respuestas reutilizables para depender menos de Internet.")
            append(" Esta memoria se guarda en tu teléfono.")
        }
    }

    fun contextForAi(): String {
        val facts = learnedFacts()
        val factBlock = if (facts.isEmpty()) {
            "Sin datos personales aprendidos todavía."
        } else {
            facts.entries.joinToString("\n") { (key, value) -> "- ${factLabel(key)} $value" }
        }

        val recent = readTurns().takeLast(14)
        val conversationBlock = if (recent.isEmpty()) {
            "Sin conversación previa."
        } else {
            recent.joinToString("\n") { turn ->
                val label = if (turn.role == "user") "Usuario" else "EDDY"
                "$label: ${turn.text}"
            }
        }

        return """
            MEMORIA APRENDIDA:
            $factBlock

            CONVERSACIÓN RECIENTE:
            $conversationBlock
        """.trimIndent()
    }

    fun shouldScheduleProactive(command: AssistantCommand): Boolean {
        val key = commandKey(command) ?: return false
        if (!isProactiveEligible(command)) return false
        if (prefs.getBoolean("proactive_scheduled_$key", false)) return false

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
        is AssistantCommand.OpenApp -> "Sos de abrir ${command.app.displayName} a esta hora. EDDY está listo si lo ocupás."
        AssistantCommand.OpenCamera -> "Sos de usar la cámara a esta hora. Aquí estoy por si la ocupás."
        is AssistantCommand.OpenMaps -> "Sos de consultar mapas a esta hora. Puedo ayudarte con una ruta cuando querás."
        else -> null
    }

    fun clearAll() {
        cancelProactiveSchedules()
        prefs.edit().clear().apply()
    }

    private fun readLearnedAnswers(): List<LearnedAnswer> {
        val raw = prefs.getString(KEY_LEARNED_ANSWERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                LearnedAnswer(
                    key = item.optString("key"),
                    answer = item.optString("answer"),
                    webDerived = item.optBoolean("web", false),
                    timestamp = item.optLong("timestamp", 0L),
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
        return decomposed
            .replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("(?i)\\b(?:eddy|eddi|eddie|edy|edi|por favor|porfa|haceme el favor|hazme el favor)\\b"), " ")
            .replace(Regex("[^a-z0-9ñ ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokenSimilarity(left: String, right: String): Double {
        if (left == right) return 1.0
        val a = left.split(' ').filter { it.length > 1 }.toSet()
        val b = right.split(' ').filter { it.length > 1 }.toSet()
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble().coerceAtLeast(1.0)
        return intersection / union
    }

    private fun cancelProactiveSchedules() {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val requestCodes = buildList {
            SupportedApp.entries.forEach { app -> add(1_000 + app.ordinal) }
            add(2_001)
            add(2_002)
        }

        requestCodes.forEach { requestCode ->
            val intent = Intent(appContext, EddyProactiveReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun appendTurn(role: String, text: String) {
        val current = readTurns().toMutableList()
        current.add(MemoryTurn(role, text, System.currentTimeMillis()))
        while (current.size > MAX_TURNS) current.removeAt(0)

        val array = JSONArray()
        current.forEach { turn ->
            array.put(
                JSONObject()
                    .put("role", turn.role)
                    .put("text", turn.text)
                    .put("timestamp", turn.timestamp),
            )
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
                    role = item.optString("role", "user"),
                    text = item.optString("text", ""),
                    timestamp = item.optLong("timestamp", 0L),
                )
            }.filter { it.text.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun learnFacts(text: String) {
        learn("name", text, """(?i)\b(?:me llamo|mi nombre es)\s+([^,.!?]{1,60})""")
        learn("likes", text, """(?i)\bme gusta(?:n)?\s+([^.!?]{1,100})""")
        learn("prefers", text, """(?i)\bprefiero\s+([^.!?]{1,100})""")
        learn("lives", text, """(?i)\bvivo en\s+([^.!?]{1,80})""")
        learn("work", text, """(?i)\btrabajo (?:en|como)\s+([^.!?]{1,100})""")
        learn("studies", text, """(?i)\bestudio\s+([^.!?]{1,100})""")
    }

    private fun learn(key: String, text: String, pattern: String) {
        val value = Regex(pattern)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.take(120)
            .orEmpty()
        if (value.isNotBlank()) prefs.edit().putString("fact_$key", value).apply()
    }

    private fun learnedFacts(): Map<String, String> {
        val keys = listOf("name", "likes", "prefers", "lives", "work", "studies")
        return buildMap {
            keys.forEach { key ->
                prefs.getString("fact_$key", null)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { put(key, it) }
            }
        }
    }

    private fun factLabel(key: String): String = when (key) {
        "name" -> "tu nombre es"
        "likes" -> "te gusta"
        "prefers" -> "preferís"
        "lives" -> "vivís en"
        "work" -> "trabajás en/como"
        "studies" -> "estudiás"
        else -> key
    }

    private fun actionCandidates(): List<Pair<String, String>> = buildList {
        SupportedApp.entries.forEach { app -> add("app_${app.name}" to "abrir ${app.displayName}") }
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
        is AssistantCommand.SetVolume,
        is AssistantCommand.AdjustVolume -> "volume"
        is AssistantCommand.SetBrightness -> "brightness"
        is AssistantCommand.OpenSystemPanel -> "system_panel"
        AssistantCommand.BatteryStatus -> "battery"
        is AssistantCommand.Vibrate -> "vibrate"
        is AssistantCommand.SmartHomeControl -> "smart_home"
        AssistantCommand.OpenSmartHomeSettings -> "smart_home_settings"
        AssistantCommand.OpenAiSettings -> "ai_settings"
        is AssistantCommand.Unknown -> "conversation"
    }

    private fun isProactiveEligible(command: AssistantCommand): Boolean = when (command) {
        is AssistantCommand.OpenApp,
        AssistantCommand.OpenCamera,
        is AssistantCommand.OpenMaps -> true
        else -> false
    }

    private fun increment(key: String) {
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    private data class MemoryTurn(
        val role: String,
        val text: String,
        val timestamp: Long,
    )

    private data class LearnedAnswer(
        val key: String,
        val answer: String,
        val webDerived: Boolean,
        val timestamp: Long,
    )

    companion object {
        private const val KEY_TURNS = "conversation_turns_v2"
        private const val KEY_TOTAL_INTERACTIONS = "total_interactions"
        private const val KEY_LEARNED_ANSWERS = "learned_answers_v1"
        private const val MAX_TURNS = 100
        private const val MAX_LEARNED_ANSWERS = 80
        private const val MAX_LEARNED_ANSWER_CHARS = 2_000
        private const val WEB_ANSWER_TTL_MS = 6L * 60L * 60L * 1_000L
        private const val GENERAL_ANSWER_TTL_MS = 30L * 24L * 60L * 60L * 1_000L
        private const val LEARNED_MATCH_THRESHOLD = 0.88
    }
}
