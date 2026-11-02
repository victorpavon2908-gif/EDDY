package com.niko.assistant.modes

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class LeoModeStoreTest {
    @Test fun selectionSurvivesRereadAndDoesNotChangePermissionsOrPreferences() {
        val context = RuntimeEnvironment.getApplication()
        val settings = context.getSharedPreferences("eddy_ai_settings", android.content.Context.MODE_PRIVATE)
        settings.edit().putBoolean("auto_research", false).putBoolean("local_first", false).commit()
        val before = settings.all.toMap()
        for (mode in LeoMode.entries) {
            LeoModeStore.set(context, mode)
            assertEquals(mode, LeoModeStore.read(context))
            assertEquals(before, settings.all)
        }
        LeoModeStore.preferences(context).edit().putString(LeoModeStore.KEY, "removed-mode").commit()
        assertEquals(LeoMode.NORMAL, LeoModeStore.read(context))
    }
}
