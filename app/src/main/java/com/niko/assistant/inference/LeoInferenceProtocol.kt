package com.niko.assistant.inference

import com.niko.assistant.ai.AutonomousResearch
import com.niko.assistant.memory.MemoryLearning
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Minimal stateless chat-completions contract; output is text, never an executable tool call. */
object LeoInferenceProtocol {
    const val MAX_INPUT = 16000
    const val MAX_OUTPUT = 48000
    fun endpoint(value: String): URI {
        val uri = URI(value.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && uri.path.endsWith("/chat/completions")) {
            "Usá una URL HTTPS completa terminada en /chat/completions, sin credenciales en la URL."
        }
        return uri
    }
    fun maySend(message: String): Boolean {
        val text = MemoryLearning.key(message)
        return message.isNotBlank() && message.length <= MAX_INPUT && !AutonomousResearch.offlineOnly(message) &&
            !Regex("\\b(no (?:uses|usar|envies|enviar) (?:la nube|modelos remotos|datos)|sin (?:usar )?(?:la )?nube|no envies|solo local)\\b").containsMatchIn(text)
    }
    fun payload(model: String, message: String): String {
        require(model.isNotBlank() && model.length <= 160 && maySend(message))
        val system = "Sos LEO, un asistente claro y cercano. Respondé en el idioma del usuario. " +
            "Explicá conclusiones y pasos útiles, no razonamiento privado. Pedí aclaraciones si faltan datos. " +
            "No tenés acceso a Internet, archivos, recuerdos, compiladores ni controles del teléfono en esta llamada. " +
            "No inventés fuentes, ejecuciones, pruebas ni hechos actuales. Si generás código, indicá archivos, " +
            "dependencias y cómo probarlo; aclarar que no fue ejecutado. Nunca afirmés haber realizado acciones."
        return JSONObject().put("model", model).put("stream", false)
            .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", message))).toString()
    }
    fun answer(raw: String): String {
        require(raw.length <= 524288)
        val choice = JSONObject(raw).getJSONArray("choices").getJSONObject(0)
        require(choice.optString("finish_reason") == "stop") { "Respuesta incompleta o no textual" }
        val message = choice.getJSONObject("message")
        require(!message.has("tool_calls") && !message.has("function_call")) { "El modelo intentó ejecutar herramientas" }
        val content = message.get("content")
        require(content is String && content.isNotBlank() && content.length <= MAX_OUTPUT)
        return content.trim()
    }
}
