@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.action.KitFab
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.frame.KitConnectionTone
import io.github.eslamasabry.oc.ui.kit.frame.KitFloatingBar
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitStatusPill
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitStartBox
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Device pass 2026-11-13 (Soft): chip shadows were cut into hard-edged boxes. Behind the home's "This phone" pill a
 * capsule with crisp edges (its 48 dp tap target clipped the visible pill's shadow), behind the chips beside it (the
 * line's clip at its bare start), the round + without any shadow (its circular clip), and the New conversation field's
 * glow ending in a hard band under the header (the page's scroller clipped it). Rendered as on the phone (2.625 px/dp),
 * the home's second row inside a floating bar ([KitScreen] with [KitFloatingBar]) and the FAB: around every face no
 * straight edge of shadow (a step of many levels running straight) exists, and the shadow still shows past the face's
 * end. Brutal's hard offset block, which needs the same room, still shows past the end.
 */
class SoftShadowRoomTest {
    private class Faces(val image: androidx.compose.ui.graphics.PixelMap, val faces: List<Rect>, val ground: Color)

    private fun home(style: Style, dark: Boolean): Faces {
        var result: Faces? = null
        runDesktopComposeUiTest(W, H) {
            var chipFace = 0f
            var pillFace = 0f
            var ground = Color.Unspecified
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(
                        KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off, style = style),
                        FakeKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        val tokens = LocalKitTokens.current
                        chipFace = (tokens.chipHeight + if (LocalKitStyle.current.spec.tokens.track) Space.S1 else 0) * D
                        pillFace = tokens.chipHeight * D
                        ground = LocalKitColors.current.ground.copy(alpha = 1f)
                        CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = 24.dp, bottom = 16.dp)) {
                            Box(Modifier.fillMaxSize()) {
                                KitScreen(
                                    page = true,
                                    topBar = { Box(Modifier.fillMaxWidth().height(56.dp)) { KitText("Conversations") } },
                                    floatingBar =
                                        KitFloatingBar(
                                            scrollsElsewhere = true,
                                            below = {
                                                // The home's second row: the server pill, then the filters running on
                                                // from beside it (bleed), as ChatsScreen's HeaderRow lays them.
                                                Row(Modifier.fillMaxWidth().padding(start = tokens.gutter.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    KitStatusPill("This phone", KitConnectionTone.Ok, "This phone, connected", onClick = {})
                                                    Spacer(Modifier.width(Space.S4.dp))
                                                    Box(Modifier.weight(1f)) {
                                                        KitFilterChips(bleed = true) {
                                                            KitChip("All projects", kind = KitChipKind.Action, opensMenu = true, onClick = {})
                                                            KitChip("Running", kind = KitChipKind.Action, selected = false, onClick = {})
                                                        }
                                                    }
                                                }
                                            },
                                        ),
                                    edges = KitScreenEdges.Bleed,
                                ) {
                                    Box(Modifier.fillMaxSize()) {
                                        KitFab("New conversation", KitIcons.add, onClick = {}, modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            fun face(node: SemanticsNodeInteraction, faceHeight: Float): Rect {
                val b = node.fetchSemanticsNode().boundsInRoot
                val mid = (b.top + b.bottom) / 2f
                return Rect(b.left, mid - faceHeight / 2f, b.right, mid + faceHeight / 2f)
            }
            val pill = face(onNode(androidx.compose.ui.test.hasTestTag("kit-status-pill"), useUnmergedTree = true), pillFace)
            val projects = face(onNode(hasContentDescription("All projects"), useUnmergedTree = true), chipFace)
            val running = face(onNode(hasContentDescription("Running"), useUnmergedTree = true), chipFace)
            val fab = onNode(androidx.compose.ui.test.hasTestTag("kit-fab"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            result = Faces(onAllNodes(isRoot())[0].captureToImage().toPixelMap(), listOf(pill, projects, running, fab), ground)
        }
        return result!!
    }

    private fun noCuts(style: Style, dark: Boolean) {
        val r = home(style, dark)
        val cuts = ShadowEdges.cuts(r.image, r.faces, D)
        assertTrue(cuts.isEmpty(), "$style ${if (dark) "dark" else "light"}: a shadow is cut into a box (faces: pill, All projects, Running, +):\n" + cuts.joinToString("\n"))
    }

    /** The shadow (Soft's dark one, Brutal's block) shows just past each face's end, a little below its middle. */
    private fun showsPastEnd(style: Style, dark: Boolean) {
        val r = home(style, dark)
        val names = listOf("This phone", "All projects", "Running", "+")
        val missing =
            r.faces.mapIndexedNotNull { i, f ->
                val x = (f.right + 2f * D).toInt()
                val y = (f.center.y + 4f * D).toInt()
                val d = ShadowEdges.differs(r.image, x, y, r.ground)
                if (d < 4f) "${names[i]}: no shadow 2 dp past its end (${"%.1f".format(d)} levels from the ground)" else null
            }
        assertTrue(missing.isEmpty(), "$style ${if (dark) "dark" else "light"}: a shadow is cut off at a face's end:\n" + missing.joinToString("\n"))
    }

    @Test fun `Soft dark - no chip, pill or button shadow on the home's second row is cut into a box`() = noCuts(Style.Soft, dark = true)

    @Test fun `Soft light - no chip, pill or button shadow on the home's second row is cut into a box`() = noCuts(Style.Soft, dark = false)

    @Test fun `Soft dark - each shadow reaches past its part's end`() = showsPastEnd(Style.Soft, dark = true)

    @Test fun `Soft light - each shadow reaches past its part's end`() = showsPastEnd(Style.Soft, dark = false)

    @Test fun `Brutal dark - the hard offset block shows past each part's end`() = showsPastEnd(Style.Brutal, dark = true)

    @Test fun `Brutal light - the hard offset block shows past each part's end`() = showsPastEnd(Style.Brutal, dark = false)

    /**
     * New conversation in Soft: the start box is the first part of a page scroller with the page's 12 dp before it.
     * Its glow fades out before the scroller's top edge, so no band shows there.
     */
    @Test
    fun `Soft dark - the start box's glow ends before a page scroller's top edge`() {
        runDesktopComposeUiTest(W, H) {
            var ground = Color.Unspecified
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off, style = Style.Soft), FakeKitPlatform(), "en", stillFrame = true) {
                        ground = LocalKitColors.current.ground.copy(alpha = 1f)
                        Box(Modifier.fillMaxSize().background(ground)) {
                            Column(
                                Modifier.padding(top = 120.dp).size((W / D).dp, 400.dp).testTag("scroller")
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = Space.S3.dp),
                            ) {
                                KitStartBox(rememberTextFieldState(), "What would you like to build?", Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val top = onNode(androidx.compose.ui.test.hasTestTag("scroller")).fetchSemanticsNode().boundsInRoot.top.toInt()
            val image = onAllNodes(isRoot())[0].captureToImage().toPixelMap()
            // Just inside the scroller's top edge, across the box: as near the ground as just outside it.
            val worst = (60 until W - 60 step 10).maxOf { x -> ShadowEdges.differs(image, x, top + 1, ground) }
            assertTrue(worst < 3f, "the glow reaches the scroller's top edge: ${"%.1f".format(worst)} levels from the ground there")
        }
    }

    private companion object {
        const val W = 1080
        const val H = 1400
        const val D = 2.625f
    }
}
