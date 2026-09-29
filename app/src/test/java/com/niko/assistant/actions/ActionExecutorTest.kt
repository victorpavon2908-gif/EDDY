package com.niko.assistant.actions

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import com.niko.assistant.LeoApplication
import com.niko.assistant.brain.SupportedApp
import java.lang.ref.WeakReference
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ActionExecutorTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @After fun clearVisibility() { LeoApplication.foregroundActivity = null }

    private fun register(intent: Intent, packageName: String) {
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                name = "$packageName.Main"; this.packageName = packageName; exported = true; enabled = true
                applicationInfo = ApplicationInfo().apply { this.packageName = packageName; enabled = true }
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, info)
    }

    @Test fun opensBusinessAndPreparesTheExactDictationWhenStandardWhatsAppIsAbsent() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        LeoApplication.foregroundActivity = WeakReference(activity)
        val pkg = "com.whatsapp.w4b"
        register(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), pkg)
        val executor = ActionExecutor(context)
        assertTrue(executor.openApp(SupportedApp.WHATSAPP).success)
        assertEquals(pkg, shadowOf(activity).nextStartedActivity.component?.packageName)
        assertTrue(executor.whatsappMessage("88887777", "Voy a León & regreso").success)
        val prepared = shadowOf(activity).nextStartedActivity
        assertEquals(pkg, prepared.`package`)
        assertEquals("/50588887777", prepared.data?.path)
        assertEquals("Voy a León & regreso", prepared.data?.getQueryParameter("text"))
    }

    @Test fun backgroundRequestHasAnActionableNotificationInsteadOfSilentSuccess() {
        LeoApplication.foregroundActivity = null
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com/"))
        register(intent, "example.browser")
        val result = AndroidActionLauncher.launch(context, intent, "Abierto", "Falló")
        assertTrue(result.spokenMessage.contains("notificación"))
        val notification = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertNotNull(notification.contentIntent)
    }
}
