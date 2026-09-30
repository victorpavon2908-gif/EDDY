package com.niko.assistant.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Latest request owns the UI, even if an old provider ignores cancellation. Main-thread API. */
class MusicSearchSession(
    private val scope: CoroutineScope,
    private val search: suspend (String) -> List<MusicResult> = MusicSearch::search,
) {
    data class State(val results: List<MusicResult> = emptyList(), val loading: Boolean = false, val message: String = "")
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var job: Job? = null

    fun submit(query: String) {
        val term = query.trim().take(200)
        if (term.isEmpty()) return
        val request = ++generation
        job?.cancel()
        mutable.value = State(loading = true)
        job = scope.launch {
            try {
                val results = search(term)
                if (generation == request) mutable.value = State(results = results,
                    message = if (results.isEmpty()) "No encontré vistas previas. Podés buscar en YouTube o Spotify." else "")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == request) mutable.value = State(message = "No pude buscar ahora. Revisá Internet o abrí uno de los servicios.")
            } finally {
                if (generation == request) mutable.value = mutable.value.copy(loading = false)
            }
        }
    }

    fun cancel() {
        ++generation
        job?.cancel()
        job = null
        mutable.value = mutable.value.copy(loading = false)
    }
}
