package com.niko.assistant.localai

import java.io.File

/** Recovery must compare with the expected release, never bless a candidate's own hash. */
internal object LeoFrozenRecovery {
    fun matches(candidate: File, expectedBytes: Long, expectedSha256: String, hash: (File) -> String): Boolean =
        expectedBytes > 0 && expectedSha256.matches(Regex("[a-fA-F0-9]{64}")) &&
            candidate.isFile && candidate.length() == expectedBytes && hash(candidate).equals(expectedSha256, true)
}
