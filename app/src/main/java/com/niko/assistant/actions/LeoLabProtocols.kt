package com.niko.assistant.actions

import com.niko.assistant.memory.MemoryLearning
import java.util.Locale

/**
 * Centro de Protocolos Biónicos Encadenados y Conciencia de Sensores Físicos («Smart Lab» de LEO).
 *
 * Permite activar protocolos completos con una sola orden de voz (Estudio, Nocturno, Emergencia,
 * Laboratorio) o crear protocolos personalizados, además de vigilar sensores físicos del teléfono
 * (luz ambiental, temperatura de batería, acelerómetro/caídas y nivel de energía) de forma 100 % local.
 */
class LeoLabProtocols {

    enum class StepType {
        SET_DND,
        SET_VOLUME,
        SET_BRIGHTNESS,
        TORCH,
        OPEN_TOOL,
        SMART_HOME,
        RUN_DIAGNOSTICS,
        CUSTOM_NOTE,
    }

    data class ProtocolStep(
        val type: StepType,
        val target: String,
        val value: String,
    )

    data class LabProtocol(
        val id: String,
        val name: String,
        val triggers: Set<String>,
        val steps: List<ProtocolStep>,
        val response: String,
    )

    data class ProtocolExecution(
        val protocol: LabProtocol,
        val executedSteps: List<ProtocolStep>,
        val message: String,
    )

    data class SensorSnapshot(
        val ambientLux: Float = 120f,
        val batteryTempC: Float = 31.0f,
        val batteryPercent: Int = 85,
        val accelMagnitudeG: Float = 1.0f,
        val wifiConnected: Boolean = true,
    )

    data class SensorAlert(
        val code: String,
        val severity: String,
        val proactiveMessage: String,
        val suggestedProtocolId: String? = null,
    )

    private val protocols = linkedMapOf<String, LabProtocol>()
    var lastExecutedProtocolId: String? = null
        private set

    init {
        registerBuiltInProtocols()
    }

    @Synchronized
    fun handleCommand(input: String): ProtocolExecution? {
        val clean = input.trim()
        val key = MemoryLearning.key(clean).removePrefix("leo ").trim()

        // Crear protocolo personalizado: "creá el protocolo cine con: apagar luz, silencio, brillo 20"
        val customMatch = Regex("^(?:crea|crear|guarda|programa) el protocolo ([a-z0-9 ]+?) con (.+)$").matchEntire(key)
        if (customMatch != null) {
            val rawName = customMatch.groupValues[1].trim()
            val rawSteps = customMatch.groupValues[2].split(Regex("(?:,|\\s+y\\s+)")).map { it.trim() }.filter { it.isNotBlank() }
            if (rawName.isNotBlank() && rawSteps.isNotEmpty()) {
                val steps = rawSteps.map { stepText ->
                    when {
                        "linterna" in stepText && "apaga" in stepText -> ProtocolStep(StepType.TORCH, "flashlight", "OFF")
                        "linterna" in stepText -> ProtocolStep(StepType.TORCH, "flashlight", "ON")
                        "silencio" in stepText || "no molestar" in stepText -> ProtocolStep(StepType.SET_DND, "system", "ON")
                        "luz" in stepText && "apaga" in stepText -> ProtocolStep(StepType.SMART_HOME, "light.living_room", "OFF")
                        "luz" in stepText -> ProtocolStep(StepType.SMART_HOME, "light.living_room", "ON")
                        else -> ProtocolStep(StepType.CUSTOM_NOTE, "task", stepText)
                    }
                }
                val id = "custom_${rawName.replace(' ', '_')}"
                val created = LabProtocol(
                    id = id,
                    name = "Protocolo ${rawName.replaceFirstChar { it.titlecase(Locale.ROOT) }}",
                    triggers = setOf("protocolo $rawName", "activa protocolo $rawName", "ejecuta protocolo $rawName"),
                    steps = steps,
                    response = "Protocolo ${rawName.replaceFirstChar { it.titlecase(Locale.ROOT) }} configurado y ejecutado con ${steps.size} acciones encadenadas.",
                )
                protocols[id] = created
                lastExecutedProtocolId = id
                return ProtocolExecution(created, steps, created.response)
            }
        }

        // Activar protocolo existente
        val matched = protocols.values.firstOrNull { proto ->
            proto.triggers.any { trigger -> key == trigger || key.endsWith(" $trigger") || key.startsWith("$trigger ") }
        } ?: return null

        lastExecutedProtocolId = matched.id
        return ProtocolExecution(
            protocol = matched,
            executedSteps = matched.steps,
            message = matched.response,
        )
    }

    /**
     * Evalúa el estado físico de los sensores del dispositivo y genera alertas proactivas al estilo Eddy.
     */
    fun evaluateEnvironment(snapshot: SensorSnapshot): List<SensorAlert> {
        val alerts = mutableListOf<SensorAlert>()
        if (snapshot.accelMagnitudeG >= 3.2f) {
            alerts += SensorAlert(
                code = "IMPACT_DETECTED",
                severity = "HIGH",
                proactiveMessage = String.format(
                    Locale.US,
                    "¡Ey! Mis acelerómetros acaban de detectar un golpe o caída fuerte (%.1f G). ¿Estás bien o activo el protocolo de emergencia?",
                    snapshot.accelMagnitudeG,
                ),
                suggestedProtocolId = "emergency",
            )
        }
        if (snapshot.batteryTempC >= 41.5f) {
            alerts += SensorAlert(
                code = "THERMAL_WARNING",
                severity = "HIGH",
                proactiveMessage = String.format(
                    Locale.US,
                    "Alerta térmica del laboratorio: la batería llegó a %.1f °C. Reduzco carga de fondo para enfriar el sistema.",
                    snapshot.batteryTempC,
                ),
            )
        }
        if (snapshot.ambientLux in 0f..3.8f) {
            alerts += SensorAlert(
                code = "LOW_AMBIENT_LIGHT",
                severity = "INFO",
                proactiveMessage = String.format(
                    Locale.US,
                    "Detecto oscuridad en el entorno (%.1f lux). ¿Querés que encienda la linterna o active el Protocolo Nocturno?",
                    snapshot.ambientLux,
                ),
                suggestedProtocolId = "night",
            )
        }
        if (snapshot.batteryPercent in 1..12) {
            alerts += SensorAlert(
                code = "CRITICAL_BATTERY",
                severity = "MEDIUM",
                proactiveMessage = "Energía del sistema al ${snapshot.batteryPercent} %. Te sugiero activar el Protocolo Nocturno para ahorrar batería.",
                suggestedProtocolId = "night",
            )
        }
        return alerts
    }

    @Synchronized
    fun availableProtocols(): List<LabProtocol> = protocols.values.toList()

    private fun registerBuiltInProtocols() {
        protocols["study"] = LabProtocol(
            id = "study",
            name = "Protocolo Estudio y Enfoque",
            triggers = setOf(
                "protocolo estudio",
                "activa el protocolo estudio",
                "activa protocolo estudio",
                "protocolo concentracion",
                "modo laboratorio estudio",
            ),
            steps = listOf(
                ProtocolStep(StepType.SET_DND, "system", "ON"),
                ProtocolStep(StepType.SET_VOLUME, "media", "25"),
                ProtocolStep(StepType.SMART_HOME, "light.desk", "ON"),
                ProtocolStep(StepType.OPEN_TOOL, "ui", "NOTES"),
            ),
            response = "[PROTOCOLO ESTUDIO ACTIVO] No molestar activado, volumen al 25 %, luz de escritorio encendida y bloc de notas listo.",
        )

        protocols["night"] = LabProtocol(
            id = "night",
            name = "Protocolo Nocturno",
            triggers = setOf(
                "protocolo nocturno",
                "activa el protocolo nocturno",
                "activa protocolo nocturno",
                "protocolo descanso",
                "protocolo dormir",
            ),
            steps = listOf(
                ProtocolStep(StepType.SET_BRIGHTNESS, "screen", "10"),
                ProtocolStep(StepType.SET_DND, "system", "ON"),
                ProtocolStep(StepType.TORCH, "flashlight", "OFF"),
                ProtocolStep(StepType.SMART_HOME, "light.living_room", "OFF"),
                ProtocolStep(StepType.SMART_HOME, "lock.front_door", "LOCK"),
            ),
            response = "[PROTOCOLO NOCTURNO ACTIVO] Brillo al 10 %, silencio activado, luces apagadas y cerradura principal asegurada.",
        )

        protocols["emergency"] = LabProtocol(
            id = "emergency",
            name = "Protocolo Emergencia",
            triggers = setOf(
                "protocolo emergencia",
                "activa protocolo emergencia",
                "alerta roja",
                "protocolo seguridad",
            ),
            steps = listOf(
                ProtocolStep(StepType.TORCH, "flashlight", "ON"),
                ProtocolStep(StepType.SET_BRIGHTNESS, "screen", "100"),
                ProtocolStep(StepType.SET_VOLUME, "alarm", "100"),
                ProtocolStep(StepType.SMART_HOME, "lock.front_door", "LOCK"),
            ),
            response = "[ALERTA ROJA · PROTOCOLO EMERGENCIA] Linterna encendida, brillo y volumen al 100 % y accesos asegurados.",
        )

        protocols["lab"] = LabProtocol(
            id = "lab",
            name = "Protocolo Diagnóstico de Laboratorio",
            triggers = setOf(
                "protocolo laboratorio",
                "activa protocolo laboratorio",
                "estado del laboratorio",
                "diagnostico bionico",
            ),
            steps = listOf(
                ProtocolStep(StepType.RUN_DIAGNOSTICS, "voice_and_sensors", "FULL"),
                ProtocolStep(StepType.OPEN_TOOL, "ui", "VOICE_DIAGNOSTICS"),
            ),
            response = "[DIAGNÓSTICO DE LABORATORIO] Sensores acústicos, memoria vectorial RAG y red neuronal local operando al 100 % sin conexión a la nube.",
        )
    }
}
