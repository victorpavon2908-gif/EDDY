package com.niko.assistant.inference

import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class LeoInferenceSettingsTest {
    private fun settings() = LeoInferenceSettings(RuntimeEnvironment.getApplication())
    @Before fun reset() { settings().clear() }
    @Test fun doesNotEnableItselfAndRequiresConsent() {
        assertNull(settings().read())
        settings().save("https://example.com/v1/chat/completions", "model", "", false)
        assertNull(settings().read())
    }
    @Test fun consentPersistsAndRevocationStopsAccess() {
        settings().save("https://example.com/v1/chat/completions", "model", "", true)
        assertEquals("model", settings().read()!!.model)
        settings().disable()
        assertNull(settings().read())
        settings().clear()
        assertEquals("", settings().endpoint)
    }
    @Test fun reactivationCannotReviveAnOldAuthorization() {
        settings().save("https://example.com/v1/chat/completions", "model", "", true, true)
        val previous = settings().read()!!
        assertTrue(settings().isCurrent(previous))
        settings().clear()
        settings().save("https://example.com/v1/chat/completions", "model", "", true, true)
        assertFalse(settings().isCurrent(previous))
        assertTrue(settings().isCurrent(settings().read()!!))
    }
    @Test fun researchRequiresSeparateConsent() {
        settings().save("https://example.com/v1/chat/completions", "model", "", true)
        assertFalse(settings().read()!!.research)
        settings().save("https://example.com/v1/chat/completions", "model", "", true, true)
        assertTrue(settings().read()!!.research)
        settings().disable()
        assertFalse(settings().researchAllowed)
    }
    @Test fun invalidConfigurationCannotReplaceExistingOne() {
        settings().save("https://example.com/v1/chat/completions", "model", "", true)
        assertTrue(runCatching { settings().save("http://example.com", "other", "secret", true) }.isFailure)
        assertEquals("model", settings().read()!!.model)
        assertFalse(LeoInferenceSettings.Config("https://example.com", "model", "private-key").toString().contains("private-key"))
    }
    @Test fun historyHasIndependentRevocableConsent() {
        settings().save("https://example.com/v1/chat/completions", "model", "", true, true)
        assertFalse(settings().read()!!.history)
        settings().save("https://example.com/v1/chat/completions", "model", "", true, false, true)
        val authorized = settings().read()!!
        assertTrue(authorized.history)
        assertFalse(authorized.research)
        settings().save("https://example.com/v1/chat/completions", "model", "", true)
        assertFalse(settings().read()!!.history)
        assertFalse(settings().isCurrent(authorized))
    }
}
