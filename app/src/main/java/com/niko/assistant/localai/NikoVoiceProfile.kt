package com.niko.assistant.localai

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import com.niko.assistant.compat.UpgradeIdentity
import com.niko.assistant.voice.LeoVoiceDiagnostics
import com.niko.assistant.voice.OwnerVoicePolicy
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Explicit local enrollment. Ambient speech never changes the confirmed voice vectors. */
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

    /** Best match against the centroid plus the four explicit enrollment templates when available. */
    fun score(embedding: FloatArray): Float = references()
        .maxOfOrNull { OwnerVoicePolicy.similarity(it, embedding) }
        ?: 0f

    fun accepts(segments: List<FloatArray>): Boolean {
        if (!ownerOnly) {
            LeoVoiceDiagnostics.recordOwnerMatch(0f, accepted = true, enabled = false)
            return true
        }
        val refs = references()
        if (refs.isEmpty()) {
            LeoVoiceDiagnostics.recordOwnerMatch(0f, accepted = false, enabled = true)
            return false
        }
        val match = OwnerVoicePolicy.evaluate(refs, segments)
        LeoVoiceDiagnostics.recordOwnerMatch(match.confidence, match.accepted, enabled = true)
        return match.accepted
    }

    fun setOwnerOnly(enabled: Boolean) { prefs.edit().putBoolean(KEY_ENABLED, enabled).apply() }

    fun beginEnrollment() = synchronized(enrollmentLock) {
        clearPending()
        enrollmentDeadline = SystemClock.elapsedRealtime() + 180_000L
    }

    fun cancelEnrollment() = synchronized(enrollmentLock) { clearPending() }

    /** A restart cancels registration; only explicit enrollment can replace the confirmed profile. */
    fun enroll(embedding: FloatArray): Boolean = synchronized(enrollmentLock) {
        if (!enrollmentActive || !OwnerVoicePolicy.valid(embedding)) return false
        val normalized = OwnerVoicePolicy.normalized(embedding)
        if (!OwnerVoicePolicy.enrollmentConsistent(pending, normalized)) return false
        pending += normalized
        if (pending.size >= OwnerVoicePolicy.ENROLLMENT_SAMPLES) {
            val samples = pending.map { it.copyOf() }
            val average = FloatArray(normalized.size) { index ->
                samples.sumOf { it[index].toDouble() }.toFloat() / samples.size
            }
            prefs.edit()
                .putString(KEY_VECTOR, encode(OwnerVoicePolicy.normalized(average)))
                .putString(KEY_TEMPLATES, samples.joinToString(TEMPLATE_SEPARATOR) { encode(it) })
                .putInt(KEY_COUNT, samples.size)
                .putBoolean(KEY_CONFIRMED, true)
                .putBoolean(KEY_ENABLED, true)
                .apply()
            clearPending()
        }
        true
    }

    fun reset() = synchronized(enrollmentLock) {
        clearPending()
        prefs.edit().clear().apply()
    }

    private fun references(): List<FloatArray> {
        val center = centroid() ?: return emptyList()
        val templates = prefs.getString(KEY_TEMPLATES, null)
            ?.split(TEMPLATE_SEPARATOR)
            .orEmpty()
            .mapNotNull(::decode)
            .filter { it.size == center.size }
        return if (templates.isEmpty()) listOf(center) else templates + center
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
        private const val KEY_TEMPLATES = "owner_templates_v2"
        private const val KEY_COUNT = "owner_samples"
        private const val KEY_CONFIRMED = "explicit_enrollment_v1"
        private const val KEY_ENABLED = "prefer_owner_voice"
        private const val TEMPLATE_SEPARATOR = "|"
        private val enrollmentLock = Any()
        private var enrollmentDeadline = 0L
        private val pending = mutableListOf<FloatArray>()
        private fun clearPending() { enrollmentDeadline = 0L; pending.clear() }
    }
}
