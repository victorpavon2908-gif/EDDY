package com.niko.assistant.devicecontrol

import com.niko.assistant.memory.LeoLocalRagEngine
import com.niko.assistant.memory.MemoryLearning
import java.util.Locale

/**
 * «Ojos de Eddy» — Analizador Visual en Tiempo Real y Escáner OCR 100 % Local.
 *
 * Interpreta observaciones de la cámara del dispositivo (bloques de texto OCR, objetos
 * detectados en escena, conteo de rostros y luminosidad media) sin enviar imágenes a servidores
 * externos, e indexa documentos capturados directamente en `LeoLocalRagEngine`.
 */
class LeoLiveVisionAnalyzer(
    private val ragEngine: LeoLocalRagEngine? = null,
) {
    data class CameraFrameObservation(
        val width: Int = 1280,
        val height: Int = 720,
        val meanLuminance: Float = 128f,
        val ocrBlocks: List<String> = emptyList(),
        val detectedObjects: List<String> = emptyList(),
        val facesCount: Int = 0,
        val timestampMs: Long = System.currentTimeMillis(),
    )

    @Volatile
    private var latestObservation: CameraFrameObservation? = null

    fun updateFrame(observation: CameraFrameObservation) {
        latestObservation = observation
        publishLiveFrame(observation)
    }

    fun currentObservation(): CameraFrameObservation? = latestObservation ?: latestLiveFrame()

    fun handleVisualCommand(input: String, observation: CameraFrameObservation? = currentObservation()): String? {
        val key = MemoryLearning.key(input).removePrefix("leo ").removePrefix("eddy ").trim()
        val isVisionQuery = key in setOf(
            "que estas viendo",
            "que ves",
            "que ves en la camara",
            "describe lo que ves",
            "analiza la escena",
            "leeme este cartel",
            "leeme este documento",
            "que dice ahi",
            "hay alguien frente a la camara",
            "escanea este documento",
            "guarda lo que estas viendo",
            "podes ver",
            "puedes ver",
            "me ves",
            "me estas viendo",
        )
        if (!isVisionQuery) return null

        val frame = observation ?: return "Mis sensores ópticos están listos, pero la cámara no está abierta todavía. Decime «abrí la cámara» y apuntá hacia lo que querás que vea o lea."
        if (key in setOf("escanea este documento", "guarda lo que estas viendo")) {
            val fullText = frame.ocrBlocks.map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ")
            if (fullText.length < 4) {
                return "Veo la escena con mis sensores ópticos, pero no detecto texto suficientemente nítido para guardarlo como documento."
            }
            val title = "Captura Visual ${frame.timestampMs % 100000}"
            ragEngine?.ingestDocument(
                docId = "ocr_${frame.timestampMs}",
                title = title,
                content = fullText,
                modality = LeoLocalRagEngine.Modality.PHOTO_OCR,
            )
            return "Escaneé e indexé el texto visible como «$title» en tu base RAG local: «${fullText.take(180)}»."
        }

        return describeScene(frame, key)
    }

    fun describeScene(frame: CameraFrameObservation, queryKey: String = ""): String {
        val parts = mutableListOf<String>()
        val lighting = when {
            frame.meanLuminance < 35f -> "iluminación muy baja"
            frame.meanLuminance > 220f -> "iluminación muy brillante"
            else -> "buena iluminación"
        }

        if ("alguien" in queryKey || frame.facesCount > 0) {
            val faceDesc = when (frame.facesCount) {
                0 -> "No detecto personas frente a la cámara"
                1 -> "Veo a 1 persona frente a la cámara"
                else -> "Veo a ${frame.facesCount} personas frente a la cámara"
            }
            parts += "$faceDesc ($lighting)."
        }

        if (frame.detectedObjects.isNotEmpty()) {
            parts += "Objetos identificados: ${frame.detectedObjects.joinToString(", ")}."
        }

        val cleanText = frame.ocrBlocks.map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ")
        if (cleanText.isNotBlank()) {
            parts += "Texto visible: «${cleanText.take(260)}»."
        } else if ("leeme" in queryKey || "dice ahi" in queryKey) {
            parts += "No logro distinguir texto legible en este ángulo ($lighting)."
        }

        if (parts.isEmpty()) {
            return String.format(Locale.US, "Cámara activa (%dx%d, %s), sin objetos ni texto destacados en este instante.", frame.width, frame.height, lighting)
        }
        return parts.joinToString(" ")
    }

    companion object {
        @Volatile
        private var sharedLiveFrame: CameraFrameObservation? = null

        fun publishLiveFrame(frame: CameraFrameObservation) {
            sharedLiveFrame = frame
        }

        fun latestLiveFrame(): CameraFrameObservation? = sharedLiveFrame
    }
}
