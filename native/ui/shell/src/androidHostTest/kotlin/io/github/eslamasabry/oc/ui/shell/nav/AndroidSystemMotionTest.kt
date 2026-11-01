package io.github.eslamasabry.oc.ui.shell.nav

import android.app.Application
import android.os.Looper
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Plan 08 §3.1: the system animator scale is read live, not once (the demo's bug). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidSystemMotionTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val resolver get() = app.contentResolver

    @Test
    fun `B-KIT-008 Remove animations reads as scale 0 and turns the transform off`() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val motion = AndroidSystemMotion(app)
        assertEquals(0f, motion.animatorScale.value)
        assertEquals(ShellMotionLevel.Off, motionLevelOf(motion.animatorScale.value))
    }

    @Test
    fun `B-KIT-008 a scale change while running reaches the shell`() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val motion = AndroidSystemMotion(app)
        assertEquals(1f, motion.animatorScale.value)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0f, motion.animatorScale.value)
    }
}
