package com.niko.assistant.learning

import com.niko.assistant.memory.EddyTransformerEmbedder
import com.niko.assistant.memory.MemoryLearning
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Motor autodidacta soberano de LEO (100% local, sin depender de GPT, Gemini ni otros modelos).
 *
 * Permite que LEO:
 * 1. Aprenda reglas directas enseñadas por el usuario ("cuando te diga X respondeme Y", "aprendé que X significa Y").
 * 2. Construya un perfil de afinidad temática y vocabulario frecuente del usuario.
 * 3. Ejecute ciclos de auto-entrenamiento supervisado (`selfTrainCycle`) sobre `OnlineIntentNetwork`
 *    con replay buffer y validación de no-regresión (`LeoAdaptiveTrainer`).
 */
class LeoAutodidactEngine(
    private val network: OnlineIntentNetwork = OnlineIntentNetwork.pretrained(),
) {
    data class TaughtRule(
        val trigger: String,
        val normalizedTrigger: String,
        val response: String,
        val uses: Int = 0,
        val createdAtMs: Long = System.currentTimeMillis(),
    )

    data class SelfTrainingSummary(
        val epochsRun: Int,
        val examplesConsolidated: Int,
        val acceptedUpdates: Int,
        val networkObservations: Long,
        val taughtRulesCount: Int,
        val topTopics: List<String>,
    )

    private val taughtRules = linkedMapOf<String, TaughtRule>()
    private val topicCounts = linkedMapOf<String, Int>()
    private val pendingTrainingQueue = ArrayDeque<Pair<String, LearnedIntent>>()
    var selfTrainingCycles: Int = 0
        private set
    var acceptedUpdatesCount: Int = 0
        private set

    /**
     * Procesa instrucciones explícitas de enseñanza o consultas sobre lo aprendido.
     * Retorna una respuesta directa si el mensaje fue una enseñanza o si coincide con una regla aprendida.
     */
    @Synchronized
    fun interact(input: String): String? {
        val clean = input.trim()
        if (clean.isBlank()) return null

        // Registrar afinidad temática del usuario de forma pasiva y segura
        observeUserTopics(clean)

        val key = MemoryLearning.key(clean).removePrefix("leo ").trim()

        // 1. Consulta sobre estado de auto-entrenamiento y aprendizaje
        if (key in setOf(
                "como va tu entrenamiento",
                "entrenate",
                "entrenate solo",
                "autoentrenate",
                "que has aprendido de mi",
                "mostrame tu entrenamiento",
            )
        ) {
            val summary = runSelfTrainingCycle()
            return buildStatusMessage(summary)
        }

        // 2. Olvidar una regla enseñada
        Regex("^(?:olvida|borra) la regla (?:de |sobre )?(.+)$").matchEntire(key)?.groupValues?.getOrNull(1)?.let { rawTarget ->
            val targetKey = MemoryLearning.key(rawTarget.trim('"', '\'', '«', '»'))
            if (taughtRules.remove(targetKey) != null) {
                return "Olvidé la regla personalizada para «$rawTarget»."
            }
        }

        // 3. Enseñanza explícita: "aprendé que cuando te diga X me respondás Y" / "enseñate que X es Y"
        parseTeaching(clean)?.let { (trigger, response) ->
            if (!AdaptiveLearningPolicy.canPersistLiteral(trigger) || !AdaptiveLearningPolicy.canPersistLiteral(response)) {
                return "Por seguridad no guardo contraseñas, tokens ni datos sensibles en mi aprendizaje."
            }
            val normTrigger = MemoryLearning.key(trigger)
            if (normTrigger.length < 2 || response.isBlank()) return null
            taughtRules[normTrigger] = TaughtRule(
                trigger = trigger.trim(),
                normalizedTrigger = normTrigger,
                response = response.trim(),
            )
            enqueueExample(trigger, LearnedIntent.CONVERSATION)
            runSelfTrainingCycle()
            return "Aprendido: cuando me digás «${trigger.trim()}», te responderé «${response.trim()}»."
        }

        // 4. Coincidencia exacta o semántica alta con una regla enseñada por el usuario
        matchTaughtRule(clean)?.let { matched ->
            taughtRules[matched.normalizedTrigger] = matched.copy(uses = matched.uses + 1)
            return matched.response
        }

        return null
    }

    /**
     * Registra un ejemplo confirmado para el próximo ciclo de auto-entrenamiento.
     */
    @Synchronized
    fun enqueueExample(utterance: String, intent: LearnedIntent) {
        val safe = AdaptiveLearningPolicy.example(utterance) ?: return
        if (pendingTrainingQueue.size >= MAX_QUEUE_SIZE) pendingTrainingQueue.removeFirst()
        pendingTrainingQueue.addLast(safe to intent)
    }

    /**
     * Ejecuta un ciclo de auto-entrenamiento autónomo verificando que ninguna clase
     * del clasificador neuronal sufra regresión antes de promover los nuevos pesos.
     */
    @Synchronized
    fun runSelfTrainingCycle(): SelfTrainingSummary {
        var accepted = 0
        var consolidated = 0
        val batch = mutableListOf<Pair<String, LearnedIntent>>()
        while (pendingTrainingQueue.isNotEmpty() && batch.size < 8) {
            batch.add(pendingTrainingQueue.removeFirst())
        }

        if (batch.isNotEmpty()) {
            for (chunk in batch.chunked(2)) {
                val proposal = LeoAdaptiveTrainer.propose(network, chunk)
                if (proposal.accepted) {
                    chunk.forEach { (text, label) -> network.learn(text, label) }
                    accepted++
                    acceptedUpdatesCount++
                    consolidated += chunk.size
                }
            }
        } else {
            // Refuerzo autónomo sobre el corpus base sin degradar pesos
            val ensured = network.ensureSeeded()
            if (ensured) accepted++
        }
        selfTrainingCycles++
        return SelfTrainingSummary(
            epochsRun = selfTrainingCycles,
            examplesConsolidated = consolidated,
            acceptedUpdates = acceptedUpdatesCount,
            networkObservations = network.observations,
            taughtRulesCount = taughtRules.size,
            topTopics = topUserTopics(4),
        )
    }

    @Synchronized
    fun predictIntent(utterance: String): OnlineIntentNetwork.Prediction = network.predict(utterance)

    @Synchronized
    fun topUserTopics(limit: Int = 5): List<String> =
        topicCounts.entries.sortedByDescending { it.value }.take(limit).map { it.key }

    @Synchronized
    fun rules(): List<TaughtRule> = taughtRules.values.toList()

    @Synchronized
    fun exportJson(): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        fun b64(s: String): String = enc.encodeToString(s.toByteArray(StandardCharsets.UTF_8))
        val rulesBlock = taughtRules.values.joinToString(";") { r ->
            listOf(b64(r.trigger), b64(r.normalizedTrigger), b64(r.response), r.uses.toString(), r.createdAtMs.toString()).joinToString(":")
        }
        val topicsBlock = topicCounts.entries.joinToString(";") { (k, v) -> "${b64(k)}:$v" }
        return "LEOAUTO1|$selfTrainingCycles|$acceptedUpdatesCount|$rulesBlock|$topicsBlock"
    }

    @Synchronized
    fun importJson(json: String) {
        val clean = json.trim()
        if (!clean.startsWith("LEOAUTO1|")) return
        val dec = Base64.getUrlDecoder()
        fun unb64(s: String): String = if (s.isEmpty()) "" else String(dec.decode(s), StandardCharsets.UTF_8)
        val parts = clean.split('|', limit = 5)
        if (parts.size < 5) return
        selfTrainingCycles = parts[1].toIntOrNull() ?: selfTrainingCycles
        acceptedUpdatesCount = parts[2].toIntOrNull() ?: acceptedUpdatesCount
        if (parts[3].isNotBlank()) {
            for (entry in parts[3].split(';')) {
                val fields = entry.split(':', limit = 5)
                if (fields.size == 5) {
                    val trigger = unb64(fields[0])
                    val response = unb64(fields[2])
                    if (AdaptiveLearningPolicy.canPersistLiteral(trigger) && AdaptiveLearningPolicy.canPersistLiteral(response)) {
                        val norm = MemoryLearning.key(trigger)
                        if (norm.isNotBlank() && response.isNotBlank()) {
                            taughtRules[norm] = TaughtRule(
                                trigger = trigger,
                                normalizedTrigger = norm,
                                response = response,
                                uses = fields[3].toIntOrNull() ?: 0,
                                createdAtMs = fields[4].toLongOrNull() ?: System.currentTimeMillis(),
                            )
                        }
                    }
                }
            }
        }
        if (parts[4].isNotBlank()) {
            for (entry in parts[4].split(';')) {
                val kv = entry.split(':', limit = 2)
                if (kv.size == 2) {
                    topicCounts[unb64(kv[0])] = kv[1].toIntOrNull() ?: 1
                }
            }
        }
    }

    private fun matchTaughtRule(input: String): TaughtRule? {
        val key = MemoryLearning.key(input).removePrefix("leo ").trim()
        taughtRules[key]?.let { return it }
        if (taughtRules.isEmpty() || key.split(' ').size < 2) return null
        val embedder = EddyTransformerEmbedder.INSTANCE
        var best: TaughtRule? = null
        var bestScore = 0.0
        for (rule in taughtRules.values) {
            val sim = embedder.semanticSimilarity(key, rule.normalizedTrigger)
            val tokenOverlap = lexicalOverlap(key, rule.normalizedTrigger)
            val combined = sim * 0.6 + tokenOverlap * 0.4
            if (combined > bestScore && combined >= 0.82 && tokenOverlap >= 0.65) {
                bestScore = combined
                best = rule
            }
        }
        return best
    }

    private fun lexicalOverlap(a: String, b: String): Double {
        val setA = a.split(' ').filter { it.length >= 2 }.toSet()
        val setB = b.split(' ').filter { it.length >= 2 }.toSet()
        if (setA.isEmpty() || setB.isEmpty()) return 0.0
        return setA.intersect(setB).size.toDouble() / setA.union(setB).size.toDouble()
    }

    private fun parseTeaching(input: String): Pair<String, String>? {
        if ('?' in input || '¿' in input) return null
        val clean = input.trim().replace(Regex("(?i)^(?:leo|niko|nico)[,\\s:;-]*"), "")
        val patterns = listOf(
            Regex("(?is)^(?:aprende|aprendé|ensenate|enseñate|recorda|recordá|recuerda)\\s+que\\s+cuando\\s+(?:yo\\s+)?(?:te\\s+)?diga\\s+[\"«']?(.+?)[\"»']?\\s+(?:me\\s+)?(?:respondas|respondás|digas|digás|contestes|contestés)\\s+[\"«']?(.+?)[\"»']?[.!]*$"),
            Regex("(?is)^cuando\\s+(?:yo\\s+)?(?:te\\s+)?diga\\s+[\"«']?(.+?)[\"»']?\\s*,?\\s*(?:respondeme|respóndeme|responde|decime|dime)\\s+[\"«']?(.+?)[\"»']?[.!]*$"),
        )
        for (pattern in patterns) {
            pattern.matchEntire(clean)?.let { match ->
                val trigger = match.groupValues[1].trim()
                val response = match.groupValues[2].trim()
                if (trigger.isNotBlank() && response.isNotBlank()) return trigger to response
            }
        }
        return null
    }

    private fun observeUserTopics(input: String) {
        if (!AdaptiveLearningPolicy.canPersistLiteral(input)) return
        val tokens = MemoryLearning.key(input).split(' ').filter { it.length >= 4 }
        for (token in tokens) {
            val category = TOPIC_KEYWORDS[token] ?: continue
            topicCounts[category] = (topicCounts[category] ?: 0) + 1
        }
    }

    private fun buildStatusMessage(summary: SelfTrainingSummary): String {
        val topicsText = if (summary.topTopics.isEmpty()) "en construcción"
        else summary.topTopics.joinToString(", ")
        return "Auto-entrenamiento local activo (100% en tu dispositivo, sin modelos externos): " +
            "${summary.networkObservations} observaciones neuronales, " +
            "${summary.taughtRulesCount} reglas personalizadas aprendidas de vos, " +
            "${summary.acceptedUpdates} actualizaciones verificadas sin regresión y " +
            "temas frecuentes detectados: $topicsText."
    }

    companion object {
        private const val MAX_QUEUE_SIZE = 32
        private val TOPIC_KEYWORDS = mapOf(
            "kotlin" to "programación Kotlin",
            "python" to "programación Python",
            "typescript" to "desarrollo TypeScript/Web",
            "javascript" to "desarrollo TypeScript/Web",
            "codigo" to "ingeniería de software",
            "sql" to "bases de datos",
            "sqlite" to "bases de datos",
            "musica" to "música y audio",
            "cancion" to "música y audio",
            "matematica" to "matemáticas y cálculo",
            "ecuacion" to "matemáticas y cálculo",
            "raiz" to "matemáticas y cálculo",
            "nicaragua" to "Nicaragua y cultura local",
            "managua" to "Nicaragua y cultura local",
            "deporte" to "deportes y salud",
            "futbol" to "deportes y salud",
            "ejercicio" to "deportes y salud",
            "finanzas" to "finanzas y productividad",
            "presupuesto" to "finanzas y productividad",
            "tarea" to "finanzas y productividad",
        )
    }
}
