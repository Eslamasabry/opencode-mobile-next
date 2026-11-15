package io.github.eslamasabry.oc.ui.kit

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.weight
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.hapticConstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P7, way 2 of 3: the Android half of the seam under Robolectric (JDK 21 launcher, SDK 37). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [KitRobolectric.SDK], qualifiers = KitRobolectric.PHONE)
class AndroidKitPlatformTest {
    @get:Rule val compose = createComposeRule()

    private class RecordingView(context: Context) : View(context) {
        val played = mutableListOf<Int>()

        override fun performHapticFeedback(feedbackConstant: Int): Boolean {
            played += feedbackConstant
            return true
        }
    }

    @Test
    fun `B-KIT-051 haptics reach the View with plan 06 constants, and plain taps stay silent`() {
        val view = RecordingView(ApplicationProvider.getApplicationContext())
        var sent = 0
        compose.setContent {
            KitTheme(appearance = KitAppearance(), platform = AndroidKitPlatform(view), localeTag = "en") {
                ButtonsScene(SceneWords.En, onSend = { sent++ })
            }
        }
        compose.onNodeWithText(SceneWords.En.send).performClick()
        compose.onNodeWithText(SceneWords.En.row).performClick()
        compose.onNodeWithText(SceneWords.En.row).performTouchInput { longClick() }
        assertEquals(1, sent)
        assertEquals(listOf(HapticFeedbackConstants.CONFIRM, HapticFeedbackConstants.LONG_PRESS), view.played)
    }

    @Test
    fun `haptic constants follow the API level`() {
        assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstant(KitHaptic.Commit, 37))
        assertEquals(HapticFeedbackConstants.CONFIRM, hapticConstant(KitHaptic.Send, 30))
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, hapticConstant(KitHaptic.Send, 29))
        assertEquals(HapticFeedbackConstants.CONFIRM, hapticConstant(KitHaptic.Done, 30))
        assertNull(hapticConstant(KitHaptic.Done, 29))
        assertEquals(HapticFeedbackConstants.SEGMENT_TICK, hapticConstant(KitHaptic.Select, 34))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, hapticConstant(KitHaptic.Select, 33))
        assertEquals(HapticFeedbackConstants.TOGGLE_ON, hapticConstant(KitHaptic.ToggleOn, 34))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, hapticConstant(KitHaptic.ToggleOff, 33))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, hapticConstant(KitHaptic.Attention, 37))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, hapticConstant(KitHaptic.Attention, 26))
    }

    /** How hard the platform plays each constant, lightest first (the strongest standard click is LONG_PRESS). */
    private fun feel(constant: Int): Int =
        when (constant) {
            HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.SEGMENT_TICK,
            HapticFeedbackConstants.TOGGLE_ON, HapticFeedbackConstants.TOGGLE_OFF -> 1
            HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.VIRTUAL_KEY -> 2
            HapticFeedbackConstants.CONFIRM -> 3
            HapticFeedbackConstants.LONG_PRESS -> 4
            else -> error("unranked haptic constant $constant")
        }

    @Test
    fun `B-402 the consequential role is stronger than Send and never the lightest on every API level`() {
        for (sdk in 26..37) {
            val commit = hapticConstant(KitHaptic.Commit, sdk)!!
            val send = hapticConstant(KitHaptic.Send, sdk)!!
            val select = hapticConstant(KitHaptic.Select, sdk)!!
            assertTrue(commit != send, "Commit differs from Send on API $sdk")
            assertTrue(feel(select) < feel(send), "Select is lighter than Send on API $sdk")
            assertTrue(feel(send) < feel(commit), "Send is lighter than Commit on API $sdk")
            assertTrue(feel(commit) > 1, "Commit is never the lightest on API $sdk")
        }
        for (sdk in 30..37) {
            assertEquals(HapticFeedbackConstants.CONFIRM, hapticConstant(KitHaptic.Send, sdk))
            assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstant(KitHaptic.Commit, sdk))
        }
    }

    @Test
    fun `B-402 the Android mapping respects the common strength order of the roles`() {
        for (sdk in 26..37) {
            val mapped = KitHaptic.entries.mapNotNull { k -> hapticConstant(k, sdk)?.let { k to feel(it) } }
            for ((a, fa) in mapped) for ((b, fb) in mapped) {
                if (a.weight < b.weight) assertTrue(fa <= fb, "$a must not feel stronger than $b on API $sdk")
            }
        }
    }

    @Test
    fun `B14 QA wave1 the window wears the app ground behind transparent system bars`() {
        val activity = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
        val ground = 0xFF0B0C0E.toInt()
        io.github.eslamasabry.oc.ui.kit.platform.applySystemBars(activity.window, darkContent = false, ground = ground)
        @Suppress("DEPRECATION")
        assertEquals(android.graphics.Color.TRANSPARENT, activity.window.statusBarColor)
        val background = activity.window.decorView.background as android.graphics.drawable.ColorDrawable
        assertEquals(ground, background.color)
    }

    @Test
    fun `kit_system_bars dart 24 leaves no divider over the navigation bar`() {
        val activity = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
        activity.window.navigationBarDividerColor = android.graphics.Color.RED
        io.github.eslamasabry.oc.ui.kit.platform.applySystemBars(activity.window, darkContent = true, ground = android.graphics.Color.WHITE)
        assertEquals(android.graphics.Color.TRANSPARENT, activity.window.navigationBarDividerColor)
        // From API 35 (edge-to-edge) the window ignores the navigation bar's colour and reads back transparent; the kit
        // paints the ground under the bar itself, so the colour is only the kit's to set below 35.
        if (android.os.Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            assertEquals(android.graphics.Color.WHITE, activity.window.navigationBarColor)
        }
    }

    @Test
    fun `a device whose thermal service refuses a listener still starts the platform`() {
        val view = RecordingView(ApplicationProvider.getApplicationContext())
        // Robolectric's PowerManager throws "Thermal status listener failed to set", as some real devices do.
        val platform = AndroidKitPlatform(view)
        assertEquals(false, platform.signals.value.severeHeat)
    }
}
