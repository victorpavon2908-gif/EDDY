package com.niko.assistant.inference

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A failed or cancelled probe must not replace the working provider or its permissions. */
object LeoInferenceActivation {
    suspend fun activate(probe: suspend () -> LeoInferenceClient.Result, persist: suspend () -> Unit): LeoInferenceClient.Result {
        val result = probe()
        currentCoroutineContext().ensureActive()
        if (result.answer.isNullOrBlank()) return result.copy(answer = null,
            failure = result.failure ?: LeoInferenceClient.Failure.RESPONSE)
        persist()
        return result
    }
}
