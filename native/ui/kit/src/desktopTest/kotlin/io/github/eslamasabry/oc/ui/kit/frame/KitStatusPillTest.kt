package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The tonal server pill: status tint, a word only when not connected, RTL and 1.3x text. */
@OptIn(ExperimentalTestApi::class)
class KitStatusPillTest {
    @Composable
    private fun Themed(rtl: Boolean, fontScale: Float, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalDensity provides Density(3f, fontScale),
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                stillFrame = true,
                localeTag = if (rtl) "ar" else "en",
            ) { content() }
        }
    }

    @Test
    fun tint_is_the_status_colour_at_low_alpha() = runComposeUiTest {
        var expected = Color.Unspecified
        setContent {
            Themed(rtl = false, fontScale = 1f) {
                val c = LocalKitColors.current
                expected = c.success.copy(alpha = KIT_STATUS_PILL_TINT).compositeOver(c.ground)
                Box(Modifier.background(c.ground)) {
                    KitStatusPill("This phone", KitConnectionTone.Ok, "This phone, Connected, Switch server", {})
                }
            }
        }
        val map = onNodeWithTag("kit-status-pill").captureToImage().toPixelMap()
        // Inside the pill's start padding: background only, no dot, no words.
        val got = map[(3 * 3), map.height / 2]
        assertTrue(abs(got.red - expected.red) < .02f && abs(got.green - expected.green) < .02f && abs(got.blue - expected.blue) < .02f, "tint $got vs $expected")
    }

    @Test
    fun word_shows_only_when_not_connected() = runComposeUiTest {
        setContent {
            Themed(rtl = false, fontScale = 1f) {
                KitStatusPill("This phone", KitConnectionTone.Problem, "x", {}, word = "Offline")
            }
        }
        onNodeWithTag("kit-status-pill-word").assertExists()
    }

    @Test
    fun connected_has_no_word() = runComposeUiTest {
        setContent { Themed(rtl = false, fontScale = 1f) { KitStatusPill("This phone", KitConnectionTone.Ok, "x", {}) } }
        onNodeWithTag("kit-status-pill-word").assertDoesNotExist()
    }

    private fun wordLeftAndWidth(rtl: Boolean): Pair<Float, Float> {
        var out = 0f to 0f
        runComposeUiTest {
            setContent {
                Themed(rtl = rtl, fontScale = 1f) {
                    KitStatusPill("This phone", KitConnectionTone.Problem, "x", {}, word = "Offline")
                }
            }
            val word = onNodeWithTag("kit-status-pill-word").getUnclippedBoundsInRoot()
            val pill = onNodeWithTag("kit-status-pill").getUnclippedBoundsInRoot()
            out = word.left.value to pill.width.value
        }
        return out
    }

    @Test
    fun mirrors_in_rtl() {
        val (ltrX, width) = wordLeftAndWidth(rtl = false)
        val (rtlX, _) = wordLeftAndWidth(rtl = true)
        assertTrue(ltrX > width / 2, "word in the end half in LTR: $ltrX of $width")
        assertTrue(rtlX < width / 2, "word in the end (left) half in RTL: $rtlX of $width")
    }

    @Test
    fun stays_inside_its_target_at_1_3x_text() = runComposeUiTest {
        setContent {
            Themed(rtl = false, fontScale = 1.3f) {
                KitStatusPill("This phone", KitConnectionTone.Problem, "x", {}, word = "Offline")
            }
        }
        val pill = onNodeWithTag("kit-status-pill").getUnclippedBoundsInRoot()
        assertEquals(48f, pill.height.value, 1f)
        onNodeWithTag("kit-status-pill-word").assertExists()
    }
}
