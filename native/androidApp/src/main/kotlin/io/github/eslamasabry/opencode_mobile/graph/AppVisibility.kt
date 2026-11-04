package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.pace.Visibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A started activity remains visible through an inactive dialog; no started activities means
 * hidden.
 */
@SingleIn(AppScope::class)
@Inject
class AppVisibility(application: Application) : Application.ActivityLifecycleCallbacks {
    private val mutable = MutableStateFlow(Visibility.HiddenIdle)
    val state: StateFlow<Visibility> = mutable
    private val started = mutableSetOf<Activity>()

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        started += activity
        mutable.value = Visibility.Foreground
    }

    override fun onActivityStopped(activity: Activity) {
        started -= activity
        if (started.isEmpty()) mutable.value = Visibility.HiddenIdle
    }

    override fun onActivityDestroyed(activity: Activity) {
        started -= activity
        if (started.isEmpty()) mutable.value = Visibility.HiddenIdle
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
