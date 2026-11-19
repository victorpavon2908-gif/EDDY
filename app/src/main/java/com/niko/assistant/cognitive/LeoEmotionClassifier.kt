package com.niko.assistant.cognitive

import com.niko.assistant.memory.MemoryLearning
import kotlin.math.exp
import kotlin.math.ln

/**
 * Small multinomial Naive Bayes model fitted to first-party Spanish & LatAm examples,
 * enhanced with lexical intensity estimation and idiomatic first-person affect patterns.
 * Session-only affect cue, NOT a diagnosis, pretrained LLM, or biographical fact.
 * Abstains on questions, negated emotions, third-person reports and poorly separated predictions.
 */
object LeoEmotionClassifier {
    enum class Emotion { JOY, SADNESS, EXCITEMENT, FRUSTRATION, ANGER, FEAR, SURPRISE,
        CONFUSION, SHAME, INSECURITY, CURIOSITY, BOREDOM, TIREDNESS, WORRY, AMUSEMENT,
        AFFECTION, PRIDE, SATISFACTION, DISAPPOINTMENT, NERVOUSNESS }

    data class Prediction(
        val emotion: Emotion,
        val score: Double,
        val intensity: Double = 0.7,
    )

    // Authored for LEO; clean Spanish + Nicaraguan/LatAm affective lexicon with low cross-class overlap.
    private val corpus = linkedMapOf(
        Emotion.JOY to "feliz contento contenta alegre alegría felicidad radiante genial dichoso dichosa encantado encantada fascinado fascinada tuani",
        Emotion.SADNESS to "triste triste tristeza desanimado desanimada llorando bajoneado bajoneada deprimido deprimida decaído decaída melancólico melancólica destrozado destrozada",
        Emotion.EXCITEMENT to "entusiasmado entusiasmada emocionado emocionada ilusionado ilusionada motivado motivada emoción adrenalina animado animada",
        Emotion.FRUSTRATION to "frustrado frustrada frustración atascado atascada impotencia harto harta bloqueado bloqueada desesperado desesperada impotente",
        Emotion.ANGER to "enojado enojada enfadado enfadada irritado irritada furioso furiosa molesto molesta rabia cólera coraje indignado indignada",
        Emotion.FEAR to "miedo miedo asustado asustada aterrado aterrada temor pánico espantado espantada horrorizado horrorizada",
        Emotion.SURPRISE to "sorprendido sorprendida asombrado asombrada impactado impactada increíble sorpresa asombro boquiabierto boquiabierta",
        Emotion.CONFUSION to "confundido confundida perdido perdida desconcertado desconcertada enredado enredada confusión desorientado desorientada",
        Emotion.SHAME to "vergüenza avergonzado avergonzada apenado apenada bochorno pena",
        Emotion.INSECURITY to "inseguro insegura incapaz insuficiente dudoso dudosa indeciso indecisa",
        Emotion.CURIOSITY to "curioso curiosa curiosidad intrigado intrigada interesado interesada",
        Emotion.BOREDOM to "aburrido aburrida aburrimiento tedio fastidiado fastidiada",
        Emotion.TIREDNESS to "cansado cansada agotado agotada exhausto exhausta sueño rendido rendida agotamiento reventado reventada desvelado desvelada",
        Emotion.WORRY to "preocupado preocupada preocupación angustiado angustiada estresado estresada agobiado agobiada estrés abrumado abrumada presionado presionada",
        Emotion.AMUSEMENT to "divertido divertida divertido divertida divertido risa risa gracioso graciosa carcajada",
        Emotion.AFFECTION to "cariñoso cariñosa cariño afecto amor agradecido agradecida aprecio gratitud",
        Emotion.PRIDE to "orgulloso orgullosa orgullo satisfechisimo",
        Emotion.SATISFACTION to "satisfecho satisfecha satisfecho satisfecha realizado realizada aliviado aliviada tranquilo tranquila alivio",
        Emotion.DISAPPOINTMENT to "decepcionado decepcionada decepción defraudado defraudada desilusionado desilusionada chasqueado chasqueada",
        Emotion.NERVOUSNESS to "nervioso nerviosa nervios nerviosismo inquieto inquieta ansioso ansiosa ansiedad tenso tensa",
    )

    private val counts = corpus.mapValues { (_, examples) -> MemoryLearning.key(examples).split(' ').groupingBy { it }.eachCount() }
    private val vocabulary = counts.values.flatMap { it.keys }.toSet()

    private val HIGH_INTENSIFIERS = setOf("muy", "mucho", "mucha", "demasiado", "demasiada", "super", "extremadamente", "totalmente", "re", "bastante", "realmente", "tan")
    private val LOW_INTENSIFIERS = setOf("algo", "un", "poco", "poquito", "medio")
    private val DEGREE_MODIFIERS = HIGH_INTENSIFIERS + LOW_INTENSIFIERS

    private val NEGATED_WELLBEING = listOf(
        Regex("^(?:no estoy bien|no me siento bien|no ando bien)$") to (Emotion.SADNESS to 0.80),
        Regex("^(?:no aguanto(?: mas)?|ya no aguanto(?: mas)?|no doy mas|ya no doy mas|ya no puedo mas)$") to (Emotion.TIREDNESS to 0.92),
        Regex("^(?:no me sale nada|nada me sale bien)$") to (Emotion.FRUSTRATION to 0.86),
    )

    fun classify(input: String): Prediction? {
        if (input.length > 2000 || input.trimStart().startsWith("¿") ||
            input.any { it in "\"«»“”" }) return null
        val hadExclamation = '!' in input || '¡' in input
        // Only the first asserted clause describes the speaker. A later task or
        // another person's feelings must not contribute features to this cue.
        val clause = input.replace(Regex("(?i)^\\s*leo\\b[\\s,:]*"), "").substringBefore(',').substringBefore(';').substringBefore('.')
            .substringBefore('!').substringBefore('\n')
        if ('?' in clause || '¿' in clause) return null
        val text = MemoryLearning.key(clause).removePrefix("leo ").trim()
        if (text.isBlank()) return null

        // Explicit negated-wellbeing first-person idioms (before generic negation abstention).
        for ((pattern, outcome) in NEGATED_WELLBEING) {
            if (pattern.matches(text)) {
                return Prediction(outcome.first, 0.88, (outcome.second + if (hadExclamation) 0.06 else 0.0).coerceAtMost(1.0))
            }
        }

        if (Regex("\\b(no|nunca|pero|aunque|sin|si|quizas|tal vez)\\b").containsMatchIn(text)) return null

        val state = Regex("^(?:estoy|ando|sigo|ya estoy|me encuentro|me siento|tengo|siento|me da|que|vaya|me tiene|me tienen) (.+)$")
            .matchEntire(text)?.groupValues?.get(1) ?: return null

        val rawWords = state.split(' ').filter(String::isNotBlank)
        val leadingModifiers = rawWords.takeWhile { it in DEGREE_MODIFIERS }
        val words = rawWords.dropWhile { it in DEGREE_MODIFIERS }
        if (words.firstOrNull() !in vocabulary) return null
        val features = words.takeWhile { it in vocabulary || it in setOf("y", "muy", "algo", "un", "poco", "bastante", "super") }
            .filter { it in vocabulary }.take(24)
        if (features.isEmpty()) return null
        val scores = counts.map { (label, wordsMap) ->
            label to features.sumOf { ln(((wordsMap[it] ?: 0) + 1.0) / (wordsMap.values.sum() + vocabulary.size)) }
        }.sortedByDescending { it.second }
        val margin = scores[0].second - scores[1].second
        if (margin < 0.5) return null
        // Relative model score, deliberately NOT exposed as calibrated human emotion confidence.
        val score = 1.0 / scores.sumOf { exp(it.second - scores[0].second) }
        val intensity = estimateIntensity(leadingModifiers, features.size, hadExclamation)
        return Prediction(scores[0].first, score, intensity)
    }

    private fun estimateIntensity(modifiers: List<String>, featureCount: Int, exclamation: Boolean): Double {
        var value = when {
            modifiers.any { it in HIGH_INTENSIFIERS } -> 0.90
            modifiers.any { it in LOW_INTENSIFIERS } -> 0.48
            else -> 0.70
        }
        if (featureCount >= 2) value += 0.06
        if (exclamation) value += 0.08
        return value.coerceIn(0.35, 1.0)
    }

    fun needsGentleResponse(input: String): Boolean = classify(input)?.emotion in setOf(
        Emotion.SADNESS, Emotion.FRUSTRATION, Emotion.ANGER, Emotion.FEAR, Emotion.CONFUSION,
        Emotion.SHAME, Emotion.INSECURITY, Emotion.TIREDNESS, Emotion.WORRY,
        Emotion.DISAPPOINTMENT, Emotion.NERVOUSNESS,
    )
}
