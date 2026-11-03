package com.niko.assistant.inference

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A failed or cancelled probe must not replace the working provider or its permissions. */
object LeoInferenceActivation {
    const val PROBE = "Respondé solamente: LEO conectado"
    fun validProbe(answer: String?): Boolean = answer?.trim()?.trim('.', '!', '"', '«', '»')
        ?.trim()?.equals("LEO conectado", ignoreCase = true) == true
    suspend fun activate(probe: suspend () -> LeoInferenceClient.Result, persist: suspend () -> Unit): LeoInferenceClient.Result {
        val result = probe()
        currentCoroutineContext().ensureActive()
        if (!validProbe(result.answer)) return result.copy(answer = null,
            failure = result.failure ?: LeoInferenceClient.Failure.RESPONSE)
        persist()
        return result
    }
}
