package com.niko.assistant.media

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MusicSearchSessionTest {
    @Test fun lateCancelledRequestCannotReplaceNewResultsOrSpinner() = runBlocking {
        val first = CompletableDeferred<List<MusicResult>>()
        val second = CompletableDeferred<List<MusicResult>>()
        val session = MusicSearchSession(this) { query ->
            withContext(NonCancellable) { if (query == "old") first.await() else second.await() }
        }
        session.submit("old"); yield()
        session.submit("new"); yield()
        first.complete(listOf(MusicResult("old", "", "", ""))); yield()
        assertTrue(session.state.value.loading)
        assertTrue(session.state.value.results.isEmpty())
        second.complete(listOf(MusicResult("new", "", "", ""))); yield()
        assertEquals("new", session.state.value.results.single().title)
        assertFalse(session.state.value.loading)
    }

    @Test fun leavingScreenCancelsAndClearsLoading() = runBlocking {
        val cancelled = CompletableDeferred<Unit>()
        val session = MusicSearchSession(this) {
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }
        session.submit("song"); yield()
        session.cancel()
        withTimeout(2000) { cancelled.await() }
        assertFalse(session.state.value.loading)
        assertEquals("", session.state.value.message)
    }
}
