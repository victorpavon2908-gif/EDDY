package com.niko.assistant.localai

import android.content.Context
import org.robolectric.RuntimeEnvironment
import com.niko.assistant.compat.UpgradeIdentity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class NikoVoiceProfileTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var profile: NikoVoiceProfile
    private val owner = floatArrayOf(1f, 0f, 0f)

    @Before fun reset() { profile = NikoVoiceProfile(context); profile.reset() }

    @Test fun ambientVectorsCannotEnrollOrEnableFiltering() {
        repeat(5) { assertFalse(profile.enroll(owner)) }
        assertFalse(profile.isEnrolled)
        assertFalse(profile.ownerOnly)
    }
    @Test fun registrationRequiresFourConsistentSamplesAndIsSharedWithService() {
        profile.beginEnrollment()
        val serviceProfile = NikoVoiceProfile(context)
        repeat(3) { assertTrue(serviceProfile.enroll(owner)) }
        assertFalse(profile.ownerOnly)
        assertFalse(serviceProfile.enroll(floatArrayOf(0f, 1f, 0f)))
        assertEquals(3, profile.enrollmentCount)
        assertTrue(serviceProfile.enroll(owner))
        assertTrue(profile.ownerOnly)
        assertFalse(profile.enrollmentActive)
        assertFalse(profile.accepts(listOf(floatArrayOf(0f, 1f, 0f))))
        assertTrue(profile.accepts(listOf(owner)))
        assertFalse(serviceProfile.enroll(floatArrayOf(0f, 1f, 0f)))
        assertTrue(profile.accepts(listOf(owner)))
    }
    @Test fun cancellingReplacementPreservesConfirmedVoiceAndResetRemovesIt() {
        profile.beginEnrollment()
        repeat(4) { profile.enroll(owner) }
        profile.beginEnrollment()
        profile.enroll(floatArrayOf(0f, 1f, 0f))
        profile.cancelEnrollment()
        assertTrue(profile.ownerOnly)
        assertTrue(profile.accepts(listOf(owner)))
        profile.setOwnerOnly(false)
        assertFalse(profile.ownerOnly)
        profile.reset()
        assertFalse(profile.isEnrolled)
    }
    @Test fun oldAutomaticLearningDoesNotCountAsExplicitEnrollment() {
        context.getSharedPreferences(UpgradeIdentity.voiceProfilePreferences, Context.MODE_PRIVATE)
            .edit().putInt("owner_samples", 100).apply()
        assertFalse(profile.isEnrolled)
        assertFalse(profile.ownerOnly)
    }
}
