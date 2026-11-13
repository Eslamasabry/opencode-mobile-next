package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.choice.KitStyleOptionCard
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidChrome
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidFace
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderCompiler
import io.github.eslamasabry.oc.ui.kit.modal.KitAlert
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * The glass panes whose rim the owner saw broken on the phone (2026-11-13), as real kit parts with their real recipes:
 * the message box's glass (the kit's Composer pane, its shape and role), a context menu opened from a row, the bar and
 * the menu out of it (one joined body), a dialog panel, a chosen style card and a chosen segment. Shared by
 * [PaneRimFloorTest] and [PaneRimRenders]. The liquid panes are drawn by the kit's desktop stand-ins, which draw the
 * rim with the shader text Android's library draws it with.
 */
internal object PaneRimScenes {
    class Case(
        val name: String,
        /** The pane to measure: its bounds from the picture's nodes. */
        val pane: (PaneFinder) -> Rect,
        /** Rays kept (a joined pane's free side only). */
        val only: (Rect) -> (Float, Float) -> Boolean = { { _, _ -> true } },
        val content: @Composable () -> Unit,
    )

    fun interface PaneFinder {
        fun bounds(matcher: SemanticsMatcher): Rect
    }

    private val items = listOf(KitMenuItem("Pin conversation", onSelect = {}), KitMenuItem("Rename", onSelect = {}), KitMenuItem("Delete conversation", onSelect = {}))

    val cases =
        listOf(
            Case("message box glass", { it.bounds(hasTestTag(BOX)) }) { Page(bottom = { MessageBox() }) {} },
            Case("context menu", { it.bounds(hasTestTag("kit-menu")) }) {
                Page {
                    Box(Modifier.fillMaxWidth().padding(top = 120.dp, start = 16.dp)) {
                        KitMenu(expanded = true, items = items, onDismissRequest = {}) { KitText("A conversation") }
                    }
                }
            },
            Case(
                "joined bar and menu",
                { it.bounds(hasTestTag("kit-menu")) },
                // The menu's free side: below its middle (its top is one body with the bar).
                only = { box -> { _, y -> y > box.center.y } },
            ) {
                Page(
                    top = {
                        Box(Modifier.fillMaxWidth().height(56.dp)) {
                            KitMenu(expanded = true, items = items, onDismissRequest = {}, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) {
                                KitText("More")
                            }
                        }
                    },
                ) {}
            },
            Case("dialog panel", { it.bounds(hasTestTag("kit-dialog")) }) {
                Page { KitAlert(visible = true, title = "Rename conversation", onDismissRequest = {}, body = "The new name shows in the list.") }
            },
            Case("chosen style card", { it.bounds(hasText("Glass") and hasClickAction()) }) {
                Page {
                    Row(Modifier.padding(16.dp).padding(top = 80.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KitStyleOptionCard(Style.Solid, "Solid", false, {}, Modifier.weight(1f))
                        KitStyleOptionCard(Style.Glass, "Glass", true, {}, Modifier.weight(1f))
                        KitStyleOptionCard(Style.Soft, "Soft", false, {}, Modifier.weight(1f))
                    }
                }
            },
            Case("chosen segment", { it.bounds(isSelected()) }) {
                Page {
                    Column(Modifier.padding(16.dp).padding(top = 80.dp)) {
                        KitSegmented(Corners.entries.map { KitSegment(it, it.name) }, Corners.entries.first(), "Corners", onChange = {})
                    }
                }
            },
        )

    @Composable
    private fun Page(top: @Composable () -> Unit = { Box(Modifier.fillMaxWidth().height(56.dp)) }, bottom: (@Composable () -> Unit)? = null, body: @Composable () -> Unit) {
        val source = rememberKitFrostState()
        KitScreen(
            page = true,
            overlayChrome = true,
            frost = source,
            topBar = top,
            bottom = bottom,
            edges = KitScreenEdges.Bleed,
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().kitFrostSource(source)) {}
                body()
            }
        }
    }

    /** The message box's glass: the kit's Composer pane over the chrome's recording, its shape and role. */
    @Composable
    private fun MessageBox() {
        val shape = RoundedCornerShape(28.dp)
        val face = kitLiquidFace(LiquidRole.Composer, LocalKitLiquidChrome.current, shape)
        // The tag on the pane's own box, inside the air round it: the measured box is the glass, not the glass and its air.
        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Box(Modifier.fillMaxWidth().height(104.dp).testTag(BOX).then(face ?: Modifier))
        }
    }

    @Composable
    fun Scene(aurora: Boolean, dark: Boolean = true, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
            KitTheme(
                KitAppearance(
                    mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                    // Motion off, not a still frame: a still-frame host never paints the aurora (KitLiveBackground), so
                    // "over Aurora" would be the plain ground; motion off holds the picture still and every part at rest.
                    motion = MotionLevel.Off,
                    style = Style.Glass,
                    liveBackground = aurora,
                    liveStyle = "auroraImage",
                ),
                FakeKitPlatform(
                    frost = true,
                    liquidGlass = true,
                    liveShaders = LiveShaderCompiler(DesktopLiveShader::compile),
                    joinEffect = { p, b -> DesktopKitPlatform().liquidJoinEffect(p, b) },
                ),
                "en",
            ) {
                if (aurora) KitLiveBackground(Modifier.fillMaxSize()) { content() }
                else Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground.copy(alpha = 1f))) { content() }
            }
        }
    }

    /**
     * Shows [content] and lets it settle on the test clock, without waiting for the clock to run on its own: a scene with
     * a running animation (a live picture, a frame-driven sampler) is never idle while the clock advances itself.
     */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    fun settle(test: androidx.compose.ui.test.ComposeUiTest, content: @Composable () -> Unit) {
        test.mainClock.autoAdvance = false
        test.setContent(content)
        repeat(SETTLE_STEPS) {
            test.mainClock.advanceTimeBy(SETTLE_STEP_MS)
            test.waitForIdle()
        }
    }

    private const val SETTLE_STEPS = 12
    private const val SETTLE_STEP_MS = 125L

    /** The pane's whole box: a part's tag may sit inside its own padding, so the widest box its modifiers lay out. */
    fun paneBox(node: androidx.compose.ui.semantics.SemanticsNode): Rect =
        node.layoutInfo.getModifierInfo().map { it.coordinates.boundsInRoot() }.plus(node.boundsInRoot).maxBy { it.width * it.height }

    /**
     * How far the page's light spreads (0-255, brightest less darkest) on a coarse grid down its middle column, between
     * the bar and the message box: the flat ground spreads by a step or two, the Aurora by tens.
     */
    fun groundSpread(image: androidx.compose.ui.graphics.PixelMap): Float {
        val lum =
            (6..14).flatMap { row -> listOf(.08f, .5f, .92f).map { col -> image[(image.width * col).toInt(), image.height * row / 20] } }.map {
                (.2126f * it.red + .7152f * it.green + .0722f * it.blue) * 255f
            }
        return lum.max() - lum.min()
    }

    /** The least spread [groundSpread] takes for a page with the Aurora drawn on it. */
    const val AURORA_SPREAD: Float = 12f

    const val WIDTH = 1080
    const val HEIGHT = 1800
    const val DENSITY = 2.625f
    const val BOX = "rim-floor-message-box"
}
