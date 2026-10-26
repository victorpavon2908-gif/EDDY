package com.niko.assistant.cognitive

import com.niko.assistant.ai.LeoExpression
import com.niko.assistant.cognitive.LeoEmotionClassifier.Emotion

/**
 * Corazón emocional de LEO (100% local, sin modelos externos).
 *
 * Mantiene resonancia empática de sesión (valencia, activación y trayectoria afectiva)
 * para acompañar al usuario con calidez humana, detectar recuperación emocional o fatiga
 * acumulada y modular el tono de respuesta sin guardar estados emocionales como hechos
 * biográficos permanentes.
 */
class LeoEmotionalHeart(private val maxHistory: Int = 12) {

    enum class Trajectory {
        INITIAL,
        RECOVERY,
        PERSISTENT_DISTRESS,
        SUSTAINED_POSITIVITY,
        SHIFT_TO_CONCERN,
        STEADY,
    }

    data class AffectiveState(
        val emotion: Emotion,
        val valence: Double,
        val arousal: Double,
        val trajectory: Trajectory,
        val empatheticPrefix: String,
        val standaloneReply: String,
        val expression: LeoExpression,
    )

    private val history = ArrayDeque<Emotion>()

    @Synchronized
    fun observe(input: String, userName: String? = null): AffectiveState? {
        val prediction = LeoEmotionClassifier.classify(input) ?: return null
        val current = prediction.emotion
        val previous = history.lastOrNull()
        if (history.size >= maxHistory) history.removeFirst()
        history.addLast(current)

        val (valence, arousal) = coordinates(current)
        val trajectory = computeTrajectory(previous, current)
        val nameCue = userName?.trim()?.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()
        val prefix = buildPrefix(current, trajectory, nameCue)
        val standalone = buildStandalone(current, trajectory, nameCue)
        val expression = mapExpression(current)
        return AffectiveState(current, valence, arousal, trajectory, prefix, standalone, expression)
    }

    /**
     * Genera una respuesta empática directa cuando el usuario expresa únicamente su estado
     * emocional (p. ej. "LEO, me siento muy frustrado" o "Estoy súper feliz hoy").
     */
    fun directReply(input: String, userName: String? = null): AffectiveState? {
        val clean = input.trim()
        val tail = clean.substringAfter(',', "").substringAfter(';', "").substringAfter('.', "").trim()
        if (tail.isNotBlank() && ('?' in tail || '¿' in tail || tail.split(' ').size >= 3)) {
            return null
        }
        return observe(clean, userName)
    }

    /**
     * Modula una respuesta informativa o técnica añadiendo un prefacio empático breve
     * cuando el usuario inició su consulta expresando frustración, cansancio, miedo o alegría.
     */
    fun modulateReply(input: String, baseReply: String, userName: String? = null): String {
        if (baseReply.isBlank()) return baseReply
        val state = observe(input, userName) ?: return baseReply
        if (baseReply.startsWith(state.empatheticPrefix)) return baseReply
        return "${state.empatheticPrefix} $baseReply"
    }

    @Synchronized
    fun currentMoodSummary(): String {
        val last = history.lastOrNull() ?: return "Neutral y atento"
        val (valence, _) = coordinates(last)
        return when {
            valence >= 0.5 -> "Entusiasta y positivo (${last.name})"
            valence <= -0.4 -> "Empático y sereno de apoyo (${last.name})"
            else -> "Equilibrado y atento (${last.name})"
        }
    }

    @Synchronized
    fun clearSession() {
        history.clear()
    }

    private fun computeTrajectory(previous: Emotion?, current: Emotion): Trajectory {
        if (previous == null) return Trajectory.INITIAL
        val prevValence = coordinates(previous).first
        val currValence = coordinates(current).first
        return when {
            prevValence < -0.2 && currValence > 0.3 -> Trajectory.RECOVERY
            prevValence < -0.2 && currValence < -0.2 -> Trajectory.PERSISTENT_DISTRESS
            prevValence > 0.3 && currValence > 0.3 -> Trajectory.SUSTAINED_POSITIVITY
            prevValence > 0.2 && currValence < -0.2 -> Trajectory.SHIFT_TO_CONCERN
            else -> Trajectory.STEADY
        }
    }

    private fun coordinates(emotion: Emotion): Pair<Double, Double> = when (emotion) {
        Emotion.JOY -> 0.85 to 0.65
        Emotion.EXCITEMENT -> 0.90 to 0.88
        Emotion.PRIDE -> 0.80 to 0.70
        Emotion.SATISFACTION -> 0.75 to 0.35
        Emotion.AFFECTION -> 0.85 to 0.45
        Emotion.AMUSEMENT -> 0.75 to 0.70
        Emotion.CURIOSITY -> 0.45 to 0.60
        Emotion.SURPRISE -> 0.30 to 0.85
        Emotion.BOREDOM -> -0.25 to 0.15
        Emotion.TIREDNESS -> -0.40 to 0.15
        Emotion.CONFUSION -> -0.35 to 0.55
        Emotion.INSECURITY -> -0.55 to 0.50
        Emotion.SHAME -> -0.60 to 0.55
        Emotion.NERVOUSNESS -> -0.50 to 0.80
        Emotion.WORRY -> -0.60 to 0.75
        Emotion.DISAPPOINTMENT -> -0.65 to 0.35
        Emotion.FRUSTRATION -> -0.70 to 0.80
        Emotion.SADNESS -> -0.80 to 0.25
        Emotion.FEAR -> -0.80 to 0.85
        Emotion.ANGER -> -0.85 to 0.90
    }

    private fun buildPrefix(emotion: Emotion, trajectory: Trajectory, nameCue: String): String {
        if (trajectory == Trajectory.RECOVERY) {
            return "Qué alegría notar que ya te sentís mejor$nameCue."
        }
        return when (emotion) {
            Emotion.FRUSTRATION -> "Tranquilo$nameCue, vamos paso a paso para destrabarlo."
            Emotion.TIREDNESS -> "Tomalo con calma$nameCue; yo me encargo de resumirlo fácil."
            Emotion.WORRY, Emotion.NERVOUSNESS -> "Respirá tranquilo$nameCue, revisemos esto en orden."
            Emotion.SADNESS, Emotion.DISAPPOINTMENT -> "Te acompaño en esto$nameCue; acá estoy con vos."
            Emotion.CONFUSION -> "No te preocupés$nameCue, te lo explico claro y desde cero."
            Emotion.FEAR, Emotion.INSECURITY, Emotion.SHAME -> "Con calma$nameCue, estamos en confianza y lo resolvemos juntos."
            Emotion.ANGER -> "Entiendo tu molestia$nameCue; vayamos directo a la solución."
            Emotion.JOY, Emotion.EXCITEMENT, Emotion.PRIDE, Emotion.SATISFACTION -> "¡Qué buena vibra$nameCue!"
            Emotion.CURIOSITY -> "Excelente pregunta$nameCue."
            else -> "Acá estoy con vos$nameCue."
        }
    }

    private fun buildStandalone(emotion: Emotion, trajectory: Trajectory, nameCue: String): String {
        val transitionNote = when (trajectory) {
            Trajectory.RECOVERY -> " Me alegra muchísimo ver que pasaste ese mal rato y ahora te sentís mejor."
            Trajectory.PERSISTENT_DISTRESS -> " Noto que venís cargando con esto; si querés hacemos una pausa o lo dividimos en pasos chiquitos."
            else -> ""
        }
        return when (emotion) {
            Emotion.JOY -> "¡Me alegra muchísimo escucharlo$nameCue!$transitionNote Disfrutá ese momento. ¿Qué hacemos ahora?"
            Emotion.EXCITEMENT -> "¡Esa energía contagia$nameCue!$transitionNote Contame en qué proyecto o idea nos metemos."
            Emotion.PRIDE -> "¡Tenés toda la razón de sentirte orgulloso$nameCue!$transitionNote Cada logro cuenta."
            Emotion.SATISFACTION -> "Qué buena sensación de tranquilidad y deber cumplido$nameCue.$transitionNote"
            Emotion.AFFECTION -> "Yo también te aprecio mucho$nameCue. Siempre estoy listo para ayudarte y aprender de vos."
            Emotion.AMUSEMENT -> "¡Jajaja, qué bueno que estés pasando un buen rato$nameCue!$transitionNote"
            Emotion.CURIOSITY -> "La curiosidad mueve todo$nameCue. Decime qué tema querés que investiguemos o desarmemos."
            Emotion.SURPRISE -> "¡Vaya sorpresa$nameCue! Contame qué pasó."
            Emotion.TIREDNESS -> "Se vale descansar$nameCue.$transitionNote Si necesitás terminar algo urgente, decime y te lo dejo resuelto en corto."
            Emotion.FRUSTRATION -> "Es totalmente normal frustrarse cuando algo se traba$nameCue.$transitionNote Pasame el problema y lo resolvemos parte por parte."
            Emotion.SADNESS -> "Lamento que te sintás así$nameCue.$transitionNote Acá estoy para acompañarte o ayudarte a despejar la mente cuando querrás."
            Emotion.WORRY, Emotion.NERVOUSNESS -> "Entiendo que estés con esa presión$nameCue.$transitionNote Enfoquémonos solo en el primer paso que sí podés controlar hoy."
            Emotion.CONFUSION -> "No pasa nada por sentirse perdido al principio$nameCue.$transitionNote Decime qué parte te confunde y la aclaramos sencillo."
            Emotion.ANGER -> "Te escucho$nameCue.$transitionNote Tomate un respiro y decime si querés que solucionemos algo puntual."
            Emotion.FEAR, Emotion.INSECURITY, Emotion.SHAME -> "No seas duro con vos mismo$nameCue.$transitionNote Equivocarse o dudar es parte de aprender; contá conmigo."
            Emotion.DISAPPOINTMENT -> "Entiendo la decepción$nameCue.$transitionNote A veces las cosas no salen a la primera, pero podemos buscar otra ruta."
            Emotion.BOREDOM -> "Si estás aburrido$nameCue, podemos crear un juego nuevo, explorar un tema curioso o armar una herramienta."
        }
    }

    private fun mapExpression(emotion: Emotion): LeoExpression = when (emotion) {
        Emotion.JOY, Emotion.EXCITEMENT, Emotion.PRIDE, Emotion.SATISFACTION, Emotion.AFFECTION -> LeoExpression.HAPPY
        Emotion.AMUSEMENT -> LeoExpression.LAUGH
        Emotion.SURPRISE -> LeoExpression.SURPRISE
        Emotion.FEAR, Emotion.NERVOUSNESS, Emotion.WORRY -> LeoExpression.FEAR
        Emotion.SADNESS, Emotion.DISAPPOINTMENT, Emotion.TIREDNESS -> LeoExpression.SAD
        Emotion.ANGER, Emotion.FRUSTRATION -> LeoExpression.ANGRY
        else -> LeoExpression.NEUTRAL
    }
}
