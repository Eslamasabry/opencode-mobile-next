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
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.hapticConstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(listOf(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.LONG_PRESS), view.played)
    }

    @Test
    fun `haptic constants follow the API level`() {
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, hapticConstant(KitHaptic.Commit, 37))
        assertEquals(HapticFeedbackConstants.CONFIRM, hapticConstant(KitHaptic.Done, 30))
        assertNull(hapticConstant(KitHaptic.Done, 29))
        assertEquals(HapticFeedbackConstants.SEGMENT_TICK, hapticConstant(KitHaptic.Select, 34))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, hapticConstant(KitHaptic.Select, 33))
        assertEquals(HapticFeedbackConstants.TOGGLE_ON, hapticConstant(KitHaptic.ToggleOn, 34))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, hapticConstant(KitHaptic.ToggleOff, 33))
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
}
