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
 * A text action (a tertiary [KitButton]) reads as an action in every style, dark and light: it has a visible touch
 * shape (a faint capsule), its words are not the body text's colour, they read at 4.5:1 or better on that capsule, and
 * the capsule stays quiet (never a second primary's solid fill). Measured on the render at 2.625 px/dp.
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
            val b = onNodeWithTag("action", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val image = onAllNodes(isRoot())[0].captureToImage().toPixelMap()
            val what = "$style ${if (dark) "dark" else "light"}"
            val y = ((b.top + b.bottom) / 2f).toInt()
            // Inside the capsule, before the words (the action keeps 12 dp from its ends).
            val capsule = image[(b.left + 5f * D).toInt(), y]
            if (levels(capsule, ground) < 3f) out += "$what: no touch shape (the capsule is the ground's colour: $capsule)"
            if (contrast(capsule, ground) > 1.6) out += "$what: the capsule is as strong as a filled button (${"%.2f".format(contrast(capsule, ground))}:1 on the ground)"
            // The words: the pixel that stands out most from the capsule, between its ends.
            var ink = capsule
            for (x in (b.left + 12f * D).toInt() until (b.right - 12f * D).toInt()) for (yy in (b.top + 4f * D).toInt() until (b.bottom - 4f * D).toInt()) {
                val p = image[x, yy]
                if (contrast(p, capsule) > contrast(ink, capsule)) ink = p
            }
            val ratio = contrast(ink, capsule)
            if (ratio < 4.5) out += "$what: the words read at ${"%.2f".format(ratio)}:1 on their capsule"
            if (levels(ink, text1) < 12f) out += "$what: the words are the body text's colour ($ink vs $text1), so the action reads as a line"
        }
        return out
    }

    private fun check(dark: Boolean) {
        val all = Style.entries.flatMap { problems(it, dark) }
        assertTrue(all.isEmpty(), "A text action does not read as an action:\n" + all.joinToString("\n"))
    }

    @Test fun `dark - a text action has a quiet touch shape and readable words apart from the body text, in every style`() = check(dark = true)

    @Test fun `light - a text action has a quiet touch shape and readable words apart from the body text, in every style`() = check(dark = false)

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
