package io.github.eslamasabry.oc.android.platform

import android.app.Activity
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tailscale handoff (plan 11 §14.6, ANDROID-PLATFORM-34): one package, explicit evidence only, fails closed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidTailscaleAppTest {
    private val app = RuntimeEnvironment.getApplication()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val pm = shadowOf(app.packageManager)

    private fun install(enabled: Boolean = true, launchable: Boolean = true) {
        val info =
            PackageInfo().apply {
                packageName = AndroidTailscaleApp.PACKAGE
                applicationInfo = ApplicationInfo().apply {
                    packageName = AndroidTailscaleApp.PACKAGE
                    this.enabled = enabled
                }
            }
        pm.installPackage(info)
        if (launchable) {
            val launcher = ComponentName(AndroidTailscaleApp.PACKAGE, "com.tailscale.ipn.MainActivity")
            pm.addActivityIfNotPresent(launcher)
            pm.addIntentFilterForActivity(
                launcher,
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            )
        }
    }

    @Test
    fun `B-SEC-174 the native bridge maps only explicit package evidence - missing, then installed`() {
        val tailscale = AndroidTailscaleApp(app) { activity }
        assertEquals(TailscaleAppState.Missing, tailscale.state())

        install(enabled = true)
        assertEquals(TailscaleAppState.Installed, tailscale.state())
    }

    @Test
    fun `B-SEC-174 a disabled Tailscale reads as unavailable, not installed`() {
        install(enabled = false)
        assertEquals(TailscaleAppState.Unavailable, AndroidTailscaleApp(app) { activity }.state())
    }

    @Test
    fun `B-SEC-174 a package manager that throws reads as unavailable, never installed`() {
        val broken =
            object : ContextWrapper(app) {
                override fun getPackageManager(): PackageManager = throw IllegalStateException("binder died")
            }
        val tailscale = AndroidTailscaleApp(broken) { null }
        assertEquals(TailscaleAppState.Unavailable, tailscale.state())
        assertFalse(tailscale.open())
    }

    @Test
    fun `S2-PLATFORM open launches only the Tailscale launch intent and reports false when there is none`() {
        val tailscale = AndroidTailscaleApp(app) { activity }
        assertFalse(tailscale.open()) // not installed
        assertNull(shadowOf(activity).nextStartedActivity)

        install()
        assertTrue(tailscale.open())
        val started = assertNotNull(shadowOf(activity).nextStartedActivity)
        assertEquals(AndroidTailscaleApp.PACKAGE, started.component?.packageName ?: started.`package`)
        assertNull(started.data) // no tailnet state, no address, nothing from a caller
    }

    @Test
    fun `S2-PLATFORM open without a visible activity starts Tailscale in a new task`() {
        install()
        assertTrue(AndroidTailscaleApp(app) { null }.open())
        val started = assertNotNull(shadowOf(app).nextStartedActivity)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
