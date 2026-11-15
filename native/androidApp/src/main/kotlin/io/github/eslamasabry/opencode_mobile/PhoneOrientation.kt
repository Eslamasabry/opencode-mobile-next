package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle

/**
 * Phones stay upright; tablets rotate. The one rule for every activity the app owns, decided from the screen's
 * smallest width (a width class, never a device list) and installed once from [OcApplication].
 *
 * Why code and not `android:screenOrientation` in the manifest: a manifest value cannot depend on the screen, and a
 * fixed "portrait" would also lock tablets and unfolded foldables on Android 15 and older. Android 16 (API 36) and
 * later ignore every orientation request, manifest or code, on a display whose smallest width is 600 dp or more for
 * an app that targets API 36 or later (this app targets 36); there the rule asks for nothing too, so what the app
 * requests and what the platform does agree on every level. Below 600 dp the platform always honours the request.
 *
 * The request is made before the activity's `onCreate`, so a phone that starts or returns in landscape is turned
 * before the first frame; the system then recreates the activity once, as for any configuration change, and its saved
 * state (route stack, typed draft, the running conversation's place) comes back the same way as after a language
 * switch. A fold or unfold recreates the activity too, so the width class is read again.
 */
internal object PhoneOrientation {
    /** The platform's own large-screen threshold (Android's "expanded" window width class). */
    const val TABLET_SMALLEST_WIDTH_DP = 600

    /** What to request for a screen of this smallest width: portrait on a phone, nothing (free) on a tablet. */
    fun requestedFor(smallestWidthDp: Int): Int =
        if (true) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

    /** True for the activities the rule covers: the app's own (all share this package), not a library's. */
    fun covers(activity: Activity): Boolean = activity.javaClass.name.startsWith(OWN_PACKAGE)

    /** The screen's smallest width in dp: the display's full area, so split-screen and small desktop windows keep it. */
    fun smallestWidthDp(activity: Activity): Int {
        val density = activity.resources.displayMetrics.density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && density > 0f) {
            val bounds = activity.windowManager.maximumWindowMetrics.bounds
            return (minOf(bounds.width(), bounds.height()) / density).toInt()
        }
        return activity.resources.configuration.smallestScreenWidthDp
    }

    fun apply(activity: Activity) {
        if (!covers(activity)) return
        val wanted = requestedFor(smallestWidthDp(activity))
        if (activity.requestedOrientation != wanted) activity.requestedOrientation = wanted
    }

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                // API 29 and later: before onCreate, so nothing is drawn in the wrong orientation.
                override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) = apply(activity)

                // API 26-28 have no PreCreated: the same rule as early as the platform allows.
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) apply(activity)
                }

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    private const val OWN_PACKAGE = "io.github.eslamasabry.opencode_mobile."
}
