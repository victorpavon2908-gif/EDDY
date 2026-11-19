package com.niko.assistant.cognitive

import com.niko.assistant.ai.LeoExpression
import com.niko.assistant.cognitive.LeoEmotionClassifier.Emotion

/**
 * Corazón emocional de LEO (100% local, sin modelos externos).
 *
 * Mantiene resonancia empática de sesión mediante un promedio exponencial (EMA) de
 * valencia, activación e intensidad afectiva, detectando recuperación emocional,
 * escalada, fatiga acumulada y modulando el tono sin guardar estados emocionales como
 * hechos biográficos permanentes.
 */
class LeoEmotionalHeart(private val maxHistory: Int = 12) {

    enum class Trajectory {
        INITIAL,
        RECOVERY,
        PERSISTENT_DISTRESS,
        ESCALATION,
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
        val intensity: Double = 0.7,
    )

    private val history = ArrayDeque<Emotion>()
    private var sessionValence: Double = 0.0
    private var sessionArousal: Double = 0.35
    private var observationCount: Int = 0

    @Synchronized
    fun observe(input: String, userName: String? = null): AffectiveState? {
        val prediction = LeoEmotionClassifier.classify(input) ?: return null
        val current = prediction.emotion
        val intensity = prediction.intensity
        val previous = history.lastOrNull()
        if (history.size >= maxHistory) history.removeFirst()
        history.addLast(current)
        val turnIndex = observationCount++

        val (rawValence, rawArousal) = coordinates(current)
        val valence = (rawValence * (0.75 + 0.25 * intensity)).coerceIn(-1.0, 1.0)
        val arousal = (rawArousal * (0.75 + 0.25 * intensity)).coerceIn(0.0, 1.0)
        val alpha = if (previous == null) 1.0 else 0.45
        sessionValence = sessionValence * (1.0 - alpha) + valence * alpha
        sessionArousal = sessionArousal * (1.0 - alpha) + arousal * alpha

        val trajectory = computeTrajectory(previous, current)
        val nameCue = userName?.trim()?.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()
        val prefix = buildPrefix(current, trajectory, nameCue, turnIndex)
        val standalone = buildStandalone(current, trajectory, nameCue, intensity)
        val expression = mapExpression(current)
        return AffectiveState(current, valence, arousal, trajectory, prefix, standalone, expression, intensity)
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

    val currentEmotion: Emotion?
        @Synchronized get() = history.lastOrNull()

    @Synchronized
    fun currentValence(): Double = sessionValence

    @Synchronized
    fun currentArousal(): Double = sessionArousal

    @Synchronized
    fun needsCalmVoice(): Boolean = sessionValence <= -0.30 || (sessionValence < 0.0 && sessionArousal >= 0.72)

    @Synchronized
    fun currentMoodSummary(): String {
        val last = history.lastOrNull() ?: return "Neutral y atento"
        return when {
            sessionValence >= 0.45 -> "Entusiasta y positivo (${last.name})"
            sessionValence <= -0.35 -> "Empático y sereno de apoyo (${last.name})"
            else -> "Equilibrado y atento (${last.name})"
        }
    }

    @Synchronized
    fun clearSession() {
        history.clear()
        sessionValence = 0.0
        sessionArousal = 0.35
        observationCount = 0
    }

    private fun computeTrajectory(previous: Emotion?, current: Emotion): Trajectory {
        if (previous == null) return Trajectory.INITIAL
        val (prevValence, prevArousal) = coordinates(previous)
        val (currValence, currArousal) = coordinates(current)
        return when {
            prevValence < -0.2 && currValence > 0.3 -> Trajectory.RECOVERY
            prevValence < -0.2 && currValence < prevValence - 0.12 && currArousal > prevArousal + 0.08 -> Trajectory.ESCALATION
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

    private fun pickVariant(turnIndex: Int, vararg options: String): String =
        options[turnIndex % options.size]

    private fun buildPrefix(emotion: Emotion, trajectory: Trajectory, nameCue: String, turnIndex: Int): String {
        if (trajectory == Trajectory.RECOVERY) {
            return pickVariant(
                turnIndex,
                "Qué alegría notar que ya te sentís mejor$nameCue.",
                "Me encanta ver ese cambio de ánimo$nameCue.",
            )
        }
        if (trajectory == Trajectory.ESCALATION) {
            return "Bajemos un cambio$nameCue; respiremos un segundo y ataquemos esto en partes pequeñas."
        }
        return when (emotion) {
            Emotion.FRUSTRATION -> pickVariant(
                turnIndex,
                "Tranquilo$nameCue, vamos paso a paso para destrabarlo.",
                "Sin presión$nameCue, lo desarmamos juntos y sale.",
            )
            Emotion.TIREDNESS -> pickVariant(
                turnIndex,
                "Tomalo con calma$nameCue; yo me encargo de resumirlo fácil.",
                "Te lo dejo directo al grano para que descansés$nameCue.",
            )
            Emotion.WORRY, Emotion.NERVOUSNESS -> pickVariant(
                turnIndex,
                "Respirá tranquilo$nameCue, revisemos esto en orden.",
                "Vamos en calma y por partes$nameCue, acá estoy.",
            )
            Emotion.SADNESS, Emotion.DISAPPOINTMENT -> pickVariant(
                turnIndex,
                "Te acompaño en esto$nameCue; acá estoy con vos.",
                "Estoy con vos$nameCue, vayamos a tu ritmo.",
            )
            Emotion.CONFUSION -> "No te preocupés$nameCue, te lo explico claro y desde cero."
            Emotion.FEAR, Emotion.INSECURITY, Emotion.SHAME -> "Con calma$nameCue, estamos en confianza y lo resolvemos juntos."
            Emotion.ANGER -> "Entiendo tu molestia$nameCue; vayamos directo a la solución."
            Emotion.JOY, Emotion.EXCITEMENT, Emotion.PRIDE, Emotion.SATISFACTION -> pickVariant(
                turnIndex,
                "¡Qué buena vibra$nameCue!",
                "¡Me encanta esa energía$nameCue!",
            )
            Emotion.CURIOSITY -> "Excelente pregunta$nameCue."
            else -> "Acá estoy con vos$nameCue."
        }
    }

    private fun buildStandalone(emotion: Emotion, trajectory: Trajectory, nameCue: String, intensity: Double): String {
        val transitionNote = when (trajectory) {
            Trajectory.RECOVERY -> " Me alegra muchísimo ver que pasaste ese mal rato y ahora te sentís mejor."
            Trajectory.ESCALATION -> " Siento que esto te está pesando cada vez más; hagamos una pausa breve y resolvamos solo una cosa a la vez."
            Trajectory.PERSISTENT_DISTRESS -> " Noto que venís cargando con esto; si querés hacemos una pausa o lo dividimos en pasos chiquitos."
            else -> ""
        }
        val highIntensityCue = if (intensity >= 0.88 && coordinates(emotion).first <= -0.5) " Te escucho con total atención." else ""
        return when (emotion) {
            Emotion.JOY -> "¡Me alegra muchísimo escucharlo$nameCue!$transitionNote Disfrutá ese momento. ¿Qué hacemos ahora?"
            Emotion.EXCITEMENT -> "¡Esa energía contagia$nameCue!$transitionNote Contame en qué proyecto o idea nos metemos."
            Emotion.PRIDE -> "¡Tenés toda la razón de sentirte orgulloso$nameCue!$transitionNote Cada logro cuenta."
            Emotion.SATISFACTION -> "Qué buena sensación de tranquilidad y deber cumplido$nameCue.$transitionNote"
            Emotion.AFFECTION -> "Yo también te aprecio mucho$nameCue. Siempre estoy listo para ayudarte y aprender de vos."
            Emotion.AMUSEMENT -> "¡Jajaja, qué bueno que estés pasando un buen rato$nameCue!$transitionNote"
            Emotion.CURIOSITY -> "La curiosidad mueve todo$nameCue. Decime qué tema querés que investiguemos o desarmemos."
            Emotion.SURPRISE -> "¡Vaya sorpresa$nameCue! Contame qué pasó."
            Emotion.TIREDNESS -> "Se vale descansar$nameCue.$transitionNote$highIntensityCue Si necesitás terminar algo urgente, decime y te lo dejo resuelto en corto."
            Emotion.FRUSTRATION -> "Es totalmente normal frustrarse cuando algo se traba$nameCue.$transitionNote$highIntensityCue Pasame el problema y lo resolvemos parte por parte."
            Emotion.SADNESS -> "Lamento que te sintás así$nameCue.$transitionNote$highIntensityCue Acá estoy para acompañarte o ayudarte a despejar la mente cuando querrás."
            Emotion.WORRY, Emotion.NERVOUSNESS -> "Entiendo que estés con esa presión$nameCue.$transitionNote$highIntensityCue Enfoquémonos solo en el primer paso que sí podés controlar hoy."
            Emotion.CONFUSION -> "No pasa nada por sentirse perdido al principio$nameCue.$transitionNote Decime qué parte te confunde y la aclaramos sencillo."
            Emotion.ANGER -> "Te escucho$nameCue.$transitionNote$highIntensityCue Tomate un respiro y decime si querés que solucionemos algo puntual."
            Emotion.FEAR, Emotion.INSECURITY, Emotion.SHAME -> "No seas duro con vos mismo$nameCue.$transitionNote$highIntensityCue Equivocarse o dudar es parte de aprender; contá conmigo."
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
