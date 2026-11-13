@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Device pass 2026-11-13 (Soft, chat): behind the "Ask first" chip a darker box ended in a straight vertical edge. The
 * composer's status strip hugs its chips when the model chip shares the line, and its scroll fade's layer clipped the
 * chip's shadow at the strip's ends. Rendered as on the phone (2.625 px/dp): around the chip no straight edge of
 * shadow, and the shadow (Soft's, Brutal's offset block) still shows past the chip's end.
 */
class ComposerStripShadowTest {
    private class Shot(val image: androidx.compose.ui.graphics.PixelMap, val chip: Rect, val model: Rect, val ground: Color)

    private fun shot(style: Style, dark: Boolean): Shot {
        var out: Shot? = null
        runDesktopComposeUiTest(W, H) {
            var face = 0f
            var ground = Color.Unspecified
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(
                        KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off, style = style),
                        DesktopKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        face = (LocalKitTokens.current.chipHeight + if (LocalKitStyle.current.spec.tokens.track) Space.S1 else 0) * D
                        ground = LocalKitColors.current.ground.copy(alpha = 1f)
                        Box(Modifier.fillMaxSize().background(ground).padding(start = 16.dp, top = 80.dp)) {
                            KitComposerStatusStrip(
                                hasChips = true,
                                modifier = Modifier.width(380.dp),
                                model = { KitChip("Big Pickle · Free", kind = KitChipKind.Action, opensMenu = true, onClick = {}) },
                            ) {
                                KitChip("Ask first", kind = KitChipKind.Action, icon = KitIcons.check, onClick = {})
                            }
                        }
                    }
                }
            }
            waitForIdle()
            fun faceOf(label: String): Rect {
                val b = onNode(hasContentDescription(label), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val mid = (b.top + b.bottom) / 2f
                return Rect(b.left, mid - face / 2f, b.right, mid + face / 2f)
            }
            out = Shot(onAllNodes(isRoot())[0].captureToImage().toPixelMap(), faceOf("Ask first"), faceOf("Big Pickle · Free"), ground)
        }
        return out!!
    }

    private fun noCuts(style: Style, dark: Boolean) {
        val s = shot(style, dark)
        val cuts = ShadowEdges.cuts(s.image, listOf(s.chip, s.model), D)
        assertTrue(cuts.isEmpty(), "$style ${if (dark) "dark" else "light"}: the status chip's shadow is cut into a box (face 0 = Ask first):\n" + cuts.joinToString("\n"))
    }

    private fun showsPastEnd(style: Style, dark: Boolean) {
        val s = shot(style, dark)
        val d = ShadowEdges.differs(s.image, (s.chip.right + 2f * D).toInt(), (s.chip.center.y + 4f * D).toInt(), s.ground)
        assertTrue(d >= 4f, "$style ${if (dark) "dark" else "light"}: no shadow 2 dp past the status chip's end (${"%.1f".format(d)} levels from the ground)")
    }

    @Test fun `Soft dark - the status chip's shadow beside the model chip is whole`() = noCuts(Style.Soft, dark = true)

    @Test fun `Soft light - the status chip's shadow beside the model chip is whole`() = noCuts(Style.Soft, dark = false)

    @Test fun `Soft dark - the status chip's shadow reaches past its end`() = showsPastEnd(Style.Soft, dark = true)

    @Test fun `Brutal dark - the status chip's offset block shows past its end`() = showsPastEnd(Style.Brutal, dark = true)

    @Test fun `Brutal light - the status chip's offset block shows past its end`() = showsPastEnd(Style.Brutal, dark = false)

    private companion object {
        const val W = 1080
        const val H = 400
        const val D = 2.625f
    }
}
