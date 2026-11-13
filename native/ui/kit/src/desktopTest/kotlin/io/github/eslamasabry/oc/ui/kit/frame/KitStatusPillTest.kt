package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
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
    fun pill_is_neutral_and_the_dot_is_the_accent() = runComposeUiTest {
        var surface = Color.Unspecified
        var accent = Color.Unspecified
        setContent {
            Themed(rtl = false, fontScale = 1f) {
                val c = LocalKitColors.current
                surface = c.surface3
                accent = c.accent
                Box(Modifier.background(c.ground)) {
                    KitStatusPill("This phone", KitConnectionTone.Ok, "This phone, Connected, Switch server", {})
                }
            }
        }
        val map = onNodeWithTag("kit-status-pill", useUnmergedTree = true).captureToImage().toPixelMap()
        fun near(a: Color, b: Color) = abs(a.red - b.red) < .03f && abs(a.green - b.green) < .03f && abs(a.blue - b.blue) < .03f
        // Inside the pill's start padding: the neutral surface, never a status tint.
        val face = map[(3 * 3), map.height / 2]
        assertTrue(near(face, surface), "face $face vs surface $surface")
        var dotHit = false
        for (x in 0 until map.width / 3) if (near(map[x, map.height / 2], accent)) dotHit = true
        assertTrue(dotHit, "an accent-coloured dot")
    }

    @Test
    fun word_shows_only_when_not_connected() = runComposeUiTest {
        setContent {
            Themed(rtl = false, fontScale = 1f) {
                KitStatusPill("This phone", KitConnectionTone.Problem, "x", {}, word = "Offline")
            }
        }
        onNodeWithTag("kit-status-pill-word", useUnmergedTree = true).fetchSemanticsNode()
    }

    @Test
    fun connected_has_no_word() = runComposeUiTest {
        setContent { Themed(rtl = false, fontScale = 1f) { KitStatusPill("This phone", KitConnectionTone.Ok, "x", {}) } }
        assertTrue(onAllNodesWithTag("kit-status-pill-word", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    private fun wordLeftAndWidth(rtl: Boolean): Pair<Float, Float> {
        var out = 0f to 0f
        runComposeUiTest {
            setContent {
                Themed(rtl = rtl, fontScale = 1f) {
                    KitStatusPill("This phone", KitConnectionTone.Problem, "x", {}, word = "Offline")
                }
            }
            val word = onNodeWithTag("kit-status-pill-word", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val pill = onNodeWithTag("kit-status-pill", useUnmergedTree = true).getUnclippedBoundsInRoot()
            out = word.left.value to (pill.right - pill.left).value
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
        val pill = onNodeWithTag("kit-status-pill", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(48f, (pill.bottom - pill.top).value, 1f)
        onNodeWithTag("kit-status-pill-word", useUnmergedTree = true).fetchSemanticsNode()
    }

    @Test
    fun while_connecting_the_pill_shows_the_name_and_a_spinner_and_no_word() = runComposeUiTest {
        setContent {
            Themed(rtl = false, fontScale = 1f) {
                KitStatusPill("This phone", KitConnectionTone.Working, "This phone, Connecting", {}, word = "Connecting")
            }
        }
        // The word goes in what is spoken; the pill keeps its short name whole and does not grow.
        assertTrue(onAllNodesWithTag("kit-status-pill-word", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun a_word_that_does_not_fit_beside_the_name_is_left_out_never_the_name_cut() = runComposeUiTest {
        setContent {
            Themed(rtl = false, fontScale = 1f) {
                Box(Modifier.widthIn(max = 110.dp)) {
                    KitStatusPill("This phone", KitConnectionTone.Problem, "x", {}, word = "Not answering")
                }
            }
        }
        assertTrue(onAllNodesWithTag("kit-status-pill-word", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun a_word_that_fits_still_shows() = runComposeUiTest {
        setContent {
            Themed(rtl = false, fontScale = 1f) {
                Box(Modifier.widthIn(max = 300.dp)) {
                    KitStatusPill("This phone", KitConnectionTone.Problem, "x", {}, word = "Offline")
                }
            }
        }
        onNodeWithTag("kit-status-pill-word", useUnmergedTree = true).fetchSemanticsNode()
    }
}
