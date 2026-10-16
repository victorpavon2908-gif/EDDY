package com.niko.assistant.cognitive

import com.niko.assistant.memory.MemoryLearning
import kotlin.math.exp
import kotlin.math.ln

/** Small multinomial Naive Bayes model fitted to first-party Spanish examples.
 * Session-only affect cue, NOT a diagnosis, pretrained LLM, or biographical fact.
 * Abstains on questions, negation, third-person reports and poorly separated predictions.
 */
object LeoEmotionClassifier {
    enum class Emotion { JOY, SADNESS, EXCITEMENT, FRUSTRATION, ANGER, FEAR, SURPRISE,
        CONFUSION, SHAME, INSECURITY, CURIOSITY, BOREDOM, TIREDNESS, WORRY, AMUSEMENT,
        AFFECTION, PRIDE, SATISFACTION, DISAPPOINTMENT, NERVOUSNESS }
    data class Prediction(val emotion: Emotion, val score: Double)
    // Authored for LEO; intentionally tiny pilot corpus. No external model or license assumption.
    private val corpus = linkedMapOf(
        Emotion.JOY to "feliz contento contenta alegre alegría felicidad",
        Emotion.SADNESS to "triste triste tristeza desanimado desanimada llorando",
        Emotion.EXCITEMENT to "entusiasmado entusiasmada emocionado emocionada ilusionado ilusionada",
        Emotion.FRUSTRATION to "frustrado frustrada frustración atascado atascada impotencia",
        Emotion.ANGER to "enojado enojada enfadado enfadada irritado irritada furioso furiosa",
        Emotion.FEAR to "miedo miedo asustado asustada aterrado aterrada temor",
        Emotion.SURPRISE to "sorprendido sorprendida asombrado asombrada impactado impactada",
        Emotion.CONFUSION to "confundido confundida perdido perdida desconcertado desconcertada",
        Emotion.SHAME to "vergüenza avergonzado avergonzada apenado apenada",
        Emotion.INSECURITY to "inseguro insegura incapaz insuficiente dudoso dudosa",
        Emotion.CURIOSITY to "curioso curiosa curiosidad intrigado intrigada",
        Emotion.BOREDOM to "aburrido aburrida aburrimiento tedio",
        Emotion.TIREDNESS to "cansado cansada agotado agotada exhausto exhausta sueño",
        Emotion.WORRY to "preocupado preocupada preocupación angustiado angustiada",
        Emotion.AMUSEMENT to "divertido divertida divertido divertida divertido risa risa",
        Emotion.AFFECTION to "cariñoso cariñosa cariño afecto amor",
        Emotion.PRIDE to "orgulloso orgullosa orgullo",
        Emotion.SATISFACTION to "satisfecho satisfecha satisfecho satisfecha realizado realizada",
        Emotion.DISAPPOINTMENT to "decepcionado decepcionada decepción defraudado defraudada",
        Emotion.NERVOUSNESS to "nervioso nerviosa nervios nerviosismo inquieto inquieta",
    )
    private val counts = corpus.mapValues { (_, examples) -> MemoryLearning.key(examples).split(' ').groupingBy { it }.eachCount() }
    private val vocabulary = counts.values.flatMap { it.keys }.toSet()

    fun classify(input: String): Prediction? {
        if ('?' in input || '¿' in input || '"' in input || '«' in input) return null
        val text = MemoryLearning.key(input).removePrefix("leo ")
        if (!Regex("^(?:estoy|me siento|tengo|siento) ").containsMatchIn(text) ||
            Regex("\\b(no|nunca|pero|aunque|sin)\\b").containsMatchIn(text)) return null
        val features = text.split(' ').filter { it in vocabulary }.take(24)
        if (features.isEmpty()) return null
        val scores = counts.map { (label, words) ->
            label to features.sumOf { ln(((words[it] ?: 0) + 1.0) / (words.values.sum() + vocabulary.size)) }
        }.sortedByDescending { it.second }
        val margin = scores[0].second - scores[1].second
        if (margin < 0.5) return null
        // Relative model score, deliberately NOT exposed as calibrated human emotion confidence.
        val score = 1.0 / scores.sumOf { exp(it.second - scores[0].second) }
        return Prediction(scores[0].first, score)
    }

    fun needsGentleResponse(input: String): Boolean = classify(input)?.emotion in setOf(
        Emotion.SADNESS, Emotion.FRUSTRATION, Emotion.ANGER, Emotion.FEAR, Emotion.CONFUSION,
        Emotion.SHAME, Emotion.INSECURITY, Emotion.TIREDNESS, Emotion.WORRY,
        Emotion.DISAPPOINTMENT, Emotion.NERVOUSNESS,
    )
}
