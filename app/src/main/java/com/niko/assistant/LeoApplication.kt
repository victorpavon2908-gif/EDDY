package com.niko.assistant

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

class LeoApplication : Application(), Application.ActivityLifecycleCallbacks {
    override fun onCreate() { super.onCreate(); registerActivityLifecycleCallbacks(this) }
    override fun onActivityResumed(activity: Activity) { foregroundActivity = WeakReference(activity) }
    override fun onActivityPaused(activity: Activity) {
        if (foregroundActivity?.get() === activity) foregroundActivity = null
    }
    override fun onActivityDestroyed(activity: Activity) { onActivityPaused(activity) }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit

    companion object { internal var foregroundActivity: WeakReference<Activity>? = null }
}
