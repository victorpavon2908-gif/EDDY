package com.niko.assistant.media

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CaptureGateTest {
    @Test fun microphoneHasOneOwner() = runBlocking {
        val a = Any(); val b = Any()
        var resumed = 0
        CaptureGate.pauseVoice = { true }
        CaptureGate.resumeVoice = { resumed++ }
        try {
            assertTrue(CaptureGate.acquire(a))
            assertFalse(CaptureGate.acquire(b))
            CaptureGate.release(b)
            assertTrue(CaptureGate.held)
            CaptureGate.release(a)
            assertFalse(CaptureGate.held)
            assertEquals(1, resumed)
        } finally { CaptureGate.release(a); CaptureGate.pauseVoice = null; CaptureGate.resumeVoice = null }
    }
    @Test fun failedPauseDoesNotStrandOwnership() = runBlocking {
        val token = Any()
        CaptureGate.pauseVoice = { false }
        try { assertFalse(CaptureGate.acquire(token)); assertFalse(CaptureGate.held) }
        finally { CaptureGate.release(token); CaptureGate.pauseVoice = null }
    }
    @Test fun cancelledAcquisitionReleasesOwnership() = runBlocking {
        val token = Any()
        val entered = CompletableDeferred<Unit>()
        CaptureGate.pauseVoice = { entered.complete(Unit); awaitCancellation() }
        try {
            val job = launch { CaptureGate.acquire(token) }
            entered.await()
            job.cancelAndJoin()
            assertFalse(CaptureGate.held)
        } finally { CaptureGate.release(token); CaptureGate.pauseVoice = null }
    }
}
