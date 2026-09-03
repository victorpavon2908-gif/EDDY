package com.niko.assistant.ui.robot

import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RobotMotion(val clip: String) {
    WAVE("Wave"), JUMP("Jump"), DANCE("Dance"), SPIN("Spin");

    companion object {
        fun parse(input: String): RobotMotion? {
            val text = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ").trim()
                .replace(Regex("^(?:leo|niko|nico)\\s+"), "")
                .replace(Regex("^(?:por favor |podes |quiero que )"), "")
                .replace(Regex(" (?:por favor|porfa)$"), "")
            return when (text) {
                "baila", "baila para mi", "bailes", "hace un baile", "haz un baile" -> DANCE
                "salta", "brinca", "da un salto", "pegate un salto", "saltes" -> JUMP
                "gira", "date una vuelta", "da una vuelta", "gires" -> SPIN
                "saluda", "saludame", "mueve los brazos", "move los brazos", "levanta la mano" -> WAVE
                else -> null
            }
        }
    }
}

/** Visual requests contain no audio or UI references; the renderer exists only while visible. */
object RobotMotionBus {
    data class Request(val motion: RobotMotion?, val id: Long = System.nanoTime())
    private val mutable = MutableStateFlow<Request?>(null)
    val requests = mutable.asStateFlow()
    fun perform(motion: RobotMotion) { mutable.value = Request(motion) }
    fun clear() { mutable.value = Request(null) }
}

enum class RobotActivity(val clip: String) { IDLE("Idle"), LISTENING("Listen"), THINKING("Think"), SPEAKING("Talk") }

/** One animation at a time; state changes fade between poses instead of stacking all clips. */
internal class RobotMotionDirector(private val duration: (String) -> Float) {
    data class Frame(val clip: String, val seconds: Float, val previous: String?, val previousSeconds: Float, val blend: Float)
    private var activity = RobotActivity.IDLE
    private var enabled = true
    private var reducedMotion = false
    private var requested: RobotMotion? = null
    private var requestStarted = 0.0
    private var current = "Idle"
    private var started = 0.0
    private var previous: String? = null
    private var previousSeconds = 0f
    val hasMotion get() = requested != null
    fun cancelMotion() { requested = null }

    fun setActivity(value: RobotActivity, enabled: Boolean, reducedMotion: Boolean) {
        if ((value != activity && value in setOf(RobotActivity.LISTENING, RobotActivity.THINKING)) || !enabled) requested = null
        activity = value
        this.enabled = enabled
        this.reducedMotion = reducedMotion
    }

    fun perform(motion: RobotMotion, now: Double) {
        if (!enabled || reducedMotion || activity == RobotActivity.LISTENING) return
        requested = motion
        requestStarted = now
        // Restart repeated requests too.
        if (current == motion.clip) started = now
    }

    fun frame(now: Double): Frame {
        requested?.let { if (now - requestStarted >= duration(it.clip)) requested = null }
        val target = if (!enabled || reducedMotion) "Idle" else requested?.clip ?: activity.clip
        if (target != current) {
            previous = current
            previousSeconds = ((now - started).coerceAtLeast(0.0) % duration(current).coerceAtLeast(0.01f)).toFloat()
            current = target
            started = now
        }
        val elapsed = (now - started).coerceAtLeast(0.0)
        val time = if (reducedMotion || !enabled) 0f else (elapsed % duration(current).coerceAtLeast(0.01f)).toFloat()
        val blend = (elapsed / 0.28).toFloat().coerceIn(0f, 1f)
        return Frame(current, time, previous.takeIf { blend < 1 && enabled && !reducedMotion }, previousSeconds, blend)
    }
}
