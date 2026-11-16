@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.choice.KitAgentChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitAgentGridItem
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitSideSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoBar
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import io.github.eslamasabry.oc.ui.kit.state.KitSummaryCard
import io.github.eslamasabry.oc.ui.kit.teaching.KitTipBubble
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * glass-truth 2026-11-16, item 1: parts that painted their own outer face kept their Solid fill under Glass (an opaque
 * card among the glass). Measured on the part's own pixels: under Solid most of the face is exactly the part's own fill
 * (nothing changed there); under Glass almost none of it is, because the face is the resolver's glass pane.
 */
class KitOwnFaceGlassTest {
    private class Part(val name: String, val tag: String, val fill: (KitColors) -> Color, val content: @Composable () -> Unit)

    private val parts =
        listOf(
            Part("undo bar", "kit-undo", { it.surface2 }) { KitUndoBar(KitUndoOffer("u", "Moved"), onUndo = {}, onTimeout = {}) },
            Part("side sheet", "kit-side-sheet", { it.surface1 }) { KitSideSheet(visible = true, onDismissRequest = {}, widthDp = 200) {} },
            Part("summary card", "kit-summary-card", { it.surface2 }) { KitSummaryCard(kicker = "Needs you", title = "Allow?") },
            Part("tip", "kit-tip", { it.surface2 }) { KitTipBubble("Hold a row to pin it.") },
            Part("agent choice", "own-face", { it.surface1 }) {
                Box(Modifier.size(300.dp, 120.dp).testTag("own-face")) { KitAgentChoice(KitAgentGridItem("Claude", KitIcons.terminal, {}), wide = true) }
            },
        )

    /** The share of the tagged node's pixels that are the part's own fill (within one step a channel). */
    private fun ownShare(style: Style, part: Part): Float {
        var share = 0f
        runDesktopComposeUiTest(420, 900) {
            var fill = Color.Unspecified
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        KitAppearance(style = style, mode = AppearanceMode.Dark, motion = MotionLevel.Full),
                        FakeKitPlatform(frost = true),
                        "en",
                        stillFrame = true,
                    ) {
                        fill = part.fill(LocalKitColors.current)
                        Box(Modifier.fillMaxSize()) { part.content() }
                    }
                }
            }
            waitForIdle()
            val img = onNodeWithTag(part.tag, useUnmergedTree = true).captureToImage().toPixelMap()
            var same = 0
            for (y in 0 until img.height) for (x in 0 until img.width) {
                val c = img[x, y]
                if (abs(c.red - fill.red) < .006f && abs(c.green - fill.green) < .006f && abs(c.blue - fill.blue) < .006f) same++
            }
            share = same.toFloat() / (img.width * img.height).coerceAtLeast(1)
        }
        return share
    }

    @Test
    fun `GLASS-TRUTH-1 under Glass a part's outer face is the style's glass, under Solid its own fill as before`() {
        val failures =
            parts.mapNotNull { part ->
                val solid = ownShare(Style.Solid, part)
                val glass = ownShare(Style.Glass, part)
                println("GLASS-TRUTH-1 ${part.name}: own fill under Solid %.2f, under Glass %.2f".format(solid, glass))
                when {
                    solid < .5f -> "${part.name}: Solid no longer paints its own fill (%.2f)".format(solid)
                    glass > .15f -> "${part.name}: Glass still paints the Solid fill (%.2f)".format(glass)
                    else -> null
                }
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
