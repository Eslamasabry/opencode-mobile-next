package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Full refresh rate (owner 2026-11-07: "rich animation and smooth work"). Many 90/120 Hz phones, the owner's ZTE/Nubia
 * among them, run an app at 60 Hz unless the app asks for more. The window asks for the display's fastest mode at the
 * current resolution; the system still lowers it for heat, battery saver or the person's own "smooth display" setting.
 *
 * Verify on a phone: `adb shell dumpsys display | grep -A3 "DisplayModeDirector"` lists an app-request vote for the
 * fast mode while the app is in front, and `adb shell dumpsys SurfaceFlinger | grep -i "refresh"` (or Developer options
 * › Show refresh rate) shows 90/120 instead of 60 while scrolling.
 */
internal object DisplayRefresh {
    /** One display mode, as plain numbers so the choice is unit-tested without a device. */
    data class Mode(val id: Int, val width: Int, val height: Int, val fps: Float)

    /**
     * The fastest mode with the same resolution as [current] (never a resolution switch, which would re-layout and
     * flash), or null when there is no mode list.
     */
    fun fastest(modes: List<Mode>, current: Mode): Mode? =
        modes
            .filter { it.width == current.width && it.height == current.height }
            .maxWithOrNull(compareBy<Mode> { it.fps }.thenBy { if (it.id == current.id) 1 else 0 })

    /** Asks [activity]'s window for the fastest mode now and again whenever the display's modes change (a fold). */
    fun follow(activity: Activity) {
        apply(activity)
        val displays = activity.getSystemService(DisplayManager::class.java) ?: return
        val listener =
            object : DisplayManager.DisplayListener {
                override fun onDisplayChanged(displayId: Int) {
                    if (!activity.isDestroyed && displayId == displayOf(activity)?.displayId) apply(activity)
                }

                override fun onDisplayAdded(displayId: Int) = Unit

                override fun onDisplayRemoved(displayId: Int) = Unit
            }
        displays.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    displays.unregisterDisplayListener(listener)
                }
            },
        )
    }

    private fun apply(activity: Activity) {
        val display = displayOf(activity) ?: return
        val current = display.mode.toMode()
        val best = fastest(display.supportedModes.map { it.toMode() }, current) ?: return
        val attrs = activity.window.attributes
        if (attrs.preferredDisplayModeId == best.id) return
        attrs.preferredDisplayModeId = best.id
        activity.window.attributes = attrs
    }

    private fun displayOf(activity: Activity): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display
        else @Suppress("DEPRECATION") activity.windowManager.defaultDisplay

    private fun Display.Mode.toMode() = Mode(modeId, physicalWidth, physicalHeight, refreshRate)
}
