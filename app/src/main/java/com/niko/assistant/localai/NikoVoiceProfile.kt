package com.niko.assistant.localai

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import com.niko.assistant.compat.UpgradeIdentity
import com.niko.assistant.voice.LeoVoiceDiagnostics
import com.niko.assistant.voice.OwnerVoicePolicy
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Explicit local enrollment. Ambient speech never changes the confirmed voice vector. */
class NikoVoiceProfile(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(UpgradeIdentity.voiceProfilePreferences, Context.MODE_PRIVATE)
    val sampleCount: Int get() = prefs.getInt(KEY_COUNT, 0)
    val isEnrolled: Boolean get() = prefs.getBoolean(KEY_CONFIRMED, false) && centroid() != null
    val ownerOnly: Boolean get() = isEnrolled && prefs.getBoolean(KEY_ENABLED, true)
    val enrollmentActive: Boolean get() = synchronized(enrollmentLock) {
        val now = SystemClock.elapsedRealtime()
        if (enrollmentDeadline > 0L && now >= enrollmentDeadline) clearPending()
        enrollmentDeadline > now
    }
    val enrollmentCount: Int get() = synchronized(enrollmentLock) { pending.size }

    fun hasProfile(): Boolean = isEnrolled
    fun score(embedding: FloatArray): Float = centroid()?.let { OwnerVoicePolicy.similarity(it, embedding) } ?: 0f
    fun accepts(segments: List<FloatArray>): Boolean {
        if (!ownerOnly) {
            LeoVoiceDiagnostics.recordOwnerMatch(0f, accepted = true, enabled = false)
            return true
        }
        val center = centroid() ?: run {
            LeoVoiceDiagnostics.recordOwnerMatch(0f, accepted = false, enabled = true)
            return false
        }
        val scores = segments.map { OwnerVoicePolicy.similarity(center, it) }
        val accepted = segments.isNotEmpty() && scores.all { it >= OwnerVoicePolicy.ACCEPTANCE_THRESHOLD }
        LeoVoiceDiagnostics.recordOwnerMatch(scores.minOrNull() ?: 0f, accepted, enabled = true)
        return accepted
    }

    fun setOwnerOnly(enabled: Boolean) { prefs.edit().putBoolean(KEY_ENABLED, enabled).apply() }

    fun beginEnrollment() = synchronized(enrollmentLock) {
        clearPending()
        enrollmentDeadline = SystemClock.elapsedRealtime() + 180_000L
    }

    fun cancelEnrollment() = synchronized(enrollmentLock) { clearPending() }

    /** A restart cancels registration; old automatically learned profiles are never trusted. */
    fun enroll(embedding: FloatArray): Boolean = synchronized(enrollmentLock) {
        if (!enrollmentActive || !OwnerVoicePolicy.valid(embedding)) return false
        if (pending.any { OwnerVoicePolicy.similarity(it, embedding) < 0.68f }) return false
        pending += OwnerVoicePolicy.normalized(embedding)
        if (pending.size >= OwnerVoicePolicy.ENROLLMENT_SAMPLES) {
            val average = FloatArray(embedding.size) { index -> pending.sumOf { it[index].toDouble() }.toFloat() / pending.size }
            prefs.edit().putString(KEY_VECTOR, encode(OwnerVoicePolicy.normalized(average)))
                .putInt(KEY_COUNT, pending.size).putBoolean(KEY_CONFIRMED, true).putBoolean(KEY_ENABLED, true).apply()
            clearPending()
        }
        true
    }

    fun reset() = synchronized(enrollmentLock) {
        clearPending()
        prefs.edit().clear().apply()
    }

    private fun centroid(): FloatArray? = prefs.getString(KEY_VECTOR, null)?.let(::decode)
    private fun encode(values: FloatArray): String {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(buffer::putFloat)
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }
    private fun decode(raw: String): FloatArray? = runCatching {
        val bytes = Base64.decode(raw, Base64.NO_WRAP)
        if (bytes.isEmpty() || bytes.size % 4 != 0) return@runCatching null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        FloatArray(bytes.size / 4) { buffer.float }.takeIf(OwnerVoicePolicy::valid)
    }.getOrNull()

    companion object {
        private const val KEY_VECTOR = "owner_centroid"
        private const val KEY_COUNT = "owner_samples"
        private const val KEY_CONFIRMED = "explicit_enrollment_v1"
        private const val KEY_ENABLED = "prefer_owner_voice"
        private val enrollmentLock = Any()
        private var enrollmentDeadline = 0L
        private val pending = mutableListOf<FloatArray>()
        private fun clearPending() { enrollmentDeadline = 0L; pending.clear() }
    }
}
