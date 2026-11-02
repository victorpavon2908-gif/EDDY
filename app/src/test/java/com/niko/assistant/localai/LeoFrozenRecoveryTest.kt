package com.niko.assistant.localai

import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LeoFrozenRecoveryTest {
    @Test fun structurallyReadableCandidateStillNeedsTheExpectedReleaseDigest() {
        val file = Files.createTempFile("leo-recovery", ".sqlite").toFile()
        fun hash(): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        try {
            file.writeText("abc")
            val expected = hash()
            assertTrue(LeoFrozenRecovery.matches(file, 3, expected) { hash() })
            file.writeText("abd")
            assertFalse(LeoFrozenRecovery.matches(file, 3, expected) { hash() })
            assertFalse(LeoFrozenRecovery.matches(file, 4, expected) { error("Wrong size should not be hashed") })
            assertFalse(LeoFrozenRecovery.matches(file, 3, "not-a-digest") { error("Untrusted digest") })
        } finally { file.delete() }
    }
}
