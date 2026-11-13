@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Device pass 2026-11-13: "Send again" after a dropped reply looked like a heading (big text1 words under a grey line).
 * A text action (a tertiary [KitButton]) reads as an action the native way, in every style, dark and light: words in
 * the accent's readable ink, 4.5:1 or better on the ground and apart from the body text's colour, with no face drawn at
 * rest outside the words' own box, and a face (the press layer) while it is pressed. Measured on the render at
 * 2.625 px/dp.
 */
class KitTertiaryActionLookTest {
    private fun problems(style: Style, dark: Boolean): List<String> {
        val out = mutableListOf<String>()
        runDesktopComposeUiTest(W, H) {
            var ground = Color.Unspecified
            var text1 = Color.Unspecified
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(
                        KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off, style = style),
                        FakeKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        val c = LocalKitColors.current
                        ground = c.ground.copy(alpha = 1f)
                        text1 = c.text1.compositeOver(ground)
                        Box(Modifier.fillMaxSize().background(ground).padding(32.dp)) {
                            KitButton("Send again", {}, Modifier.testTag("action"), role = KitButtonRole.Tertiary)
                        }
                    }
                }
            }
            waitForIdle()
            val what = "$style ${if (dark) "dark" else "light"}"
            val node = onNodeWithTag("action", useUnmergedTree = true)
            val b = node.fetchSemanticsNode().boundsInRoot
            val words = onNodeWithText("Send again", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val rest = onAllNodes(isRoot())[0].captureToImage().toPixelMap()
            // At rest: nothing drawn in the action's box outside its words' own box (no capsule, no plate).
            val slack = D + 1f
            var face = 0
            for (x in b.left.toInt() until b.right.toInt()) for (y in b.top.toInt() until b.bottom.toInt()) {
                val inWords = x >= words.left - slack && x <= words.right + slack && y >= words.top - slack && y <= words.bottom + slack
                if (!inWords && levels(rest[x, y], ground) >= 3f) face++
            }
            if (face > 0) out += "$what: $face px of a face drawn at rest outside the words"
            // The words: the pixel that stands out most from the ground inside their box.
            var ink = ground
            for (x in words.left.toInt() until words.right.toInt()) for (y in words.top.toInt() until words.bottom.toInt()) {
                val p = rest[x, y]
                if (contrast(p, ground) > contrast(ink, ground)) ink = p
            }
            val ratio = contrast(ink, ground)
            if (ratio < 4.5) out += "$what: the words read at ${"%.2f".format(ratio)}:1 on the ground"
            if (levels(ink, text1) < 12f) out += "$what: the words are the body text's colour ($ink vs $text1), so the action reads as a line"
            // Pressed: the press layer is its touch shape, at the box's start, clear of the words.
            node.performTouchInput { down(center) }
            mainClock.advanceTimeBy(100)
            waitForIdle()
            val pressed = onAllNodes(isRoot())[0].captureToImage().toPixelMap()
            val probe = pressed[(b.left + 3f * D).toInt(), ((b.top + b.bottom) / 2f).toInt()]
            if (levels(probe, ground) < 3f) out += "$what: no face while pressed ($probe on $ground)"
            node.performTouchInput { up() }
        }
        return out
    }

    private fun check(dark: Boolean) {
        val all = Style.entries.flatMap { problems(it, dark) }
        assertTrue(all.isEmpty(), "A text action does not read as an action:\n" + all.joinToString("\n"))
    }

    @Test fun `dark - a text action is readable accent words with no face at rest and a face while pressed, in every style`() = check(dark = true)

    @Test fun `light - a text action is readable accent words with no face at rest and a face while pressed, in every style`() = check(dark = false)

    private fun levels(a: Color, b: Color): Float = max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue))) * 255f

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + .05
        val lb = b.luminance() + .05
        return max(la, lb) / min(la, lb)
    }

    private companion object {
        const val W = 600
        const val H = 300
        const val D = 2.625f
    }
}
