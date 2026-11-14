@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.PaneRimScenes
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidJoined
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidChrome
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidFace
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidJoinRest
import io.github.eslamasabry.oc.ui.kit.effects.rememberKitLiquidJoinBody
import io.github.eslamasabry.oc.ui.kit.effects.rememberKitLiquidJoinDrop
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.foundation.KitLayerCycles
import io.github.eslamasabry.oc.ui.kit.foundation.KitRuleBroken
import io.github.eslamasabry.oc.ui.kit.foundation.kitDrawLayer
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.kitLayerThread
import io.github.eslamasabry.oc.ui.kit.foundation.kitRecord
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderCompiler
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Device 2026-11-14 (Glass, a long conversation): the first scroll up killed the app, a native stack overflow on the
 * RenderThread (RenderNode::prepareTreeImpl calling itself 500 frames deep). "Jump to latest" came up joined to the
 * message box, and the joined pill drew its side of the glass from the box's picture: the page's recording of the
 * transcript, which holds the pill itself. The page's layer drew the pill's, which drew the box's copy of the page, which
 * drew the page's layer: a cycle of render nodes. A desktop renderer does not crash on it, so the kit's own guard
 * ([KitLayerCycles]) is what fails here.
 */
class KitLayerCycleTest {
    @AfterTest
    fun reset() {
        KitLayerCycles.strict = false
    }

    private fun platform(): FakeKitPlatform =
        FakeKitPlatform(
            liquidGlass = true,
            liveShaders = LiveShaderCompiler(DesktopLiveShader::compile),
            joinEffect = { p, b -> DesktopKitPlatform().liquidJoinEffect(p, b) },
        )

    /** Draws [block] once in a small canvas, with [GraphicsLayer]s it can record and draw; returns what it threw, if anything. */
    private fun drawOnce(strict: Boolean, block: androidx.compose.ui.graphics.drawscope.DrawScope.(GraphicsLayer, GraphicsLayer) -> Unit): Throwable? {
        var thrown: Throwable? = null
        runComposeUiTest {
            setContent {
                val a = rememberGraphicsLayer()
                val b = rememberGraphicsLayer()
                Canvas(Modifier.size(20.dp)) {
                    KitLayerCycles.strict = strict
                    try {
                        block(this, a, b)
                    } catch (e: KitRuleBroken) {
                        thrown = e
                    }
                }
            }
            waitForIdle()
        }
        return thrown
    }

    @Test
    fun `B-KIT-LAYER-CYCLE-01 a layer drawn inside its own recording, at once or through a layer it draws, trips the guard`() {
        val direct = drawOnce(strict = true) { a, _ -> kitRecord(a) { kitDrawLayer(a) } }
        assertNotNull(direct, "a layer drawn inside its own recording")
        val through =
            drawOnce(strict = true) { a, b ->
                kitRecord(b) { kitDrawLayer(a) }
                // a now draws b, which drew a.
                kitRecord(a) { kitDrawLayer(b) }
            }
        assertNotNull(through, "a layer drawn inside the recording of a layer it draws")
        val fine =
            drawOnce(strict = true) { a, b ->
                kitRecord(a) { drawRect(Color.Red) }
                kitRecord(b) { kitDrawLayer(a) }
                kitDrawLayer(b)
            }
        assertNull(fine, "a layer drawn into another that it does not draw is no cycle")
    }

    @Test
    fun `B-KIT-LAYER-CYCLE-02 a release build leaves the cyclic draw out instead of throwing`() {
        var kept: List<GraphicsLayer>? = null
        var b0: GraphicsLayer? = null
        val thrown =
            drawOnce(strict = false) { a, b ->
                b0 = b
                kitRecord(b) { kitDrawLayer(a) }
                kitRecord(a) { kitDrawLayer(b) }
                kept = kitLayerThread().drew[a]
            }
        assertNull(thrown)
        assertTrue(kept.orEmpty().none { it === b0 }, "the draw that closed the cycle is left out of the recording")
    }

    /**
     * The chat's frame in Glass, as on the device: the page records its body ([frost]); inside it a list that records
     * itself for the pill, the pill (or a bare drop of the box's glass), and content under the bar (the top dissolve on);
     * the bottom block one liquid pane joined as the body "bottom".
     */
    @Composable
    private fun ConversationFrame(frost: KitFrostState, pill: @Composable (list: KitFrostState) -> Unit) {
        KitScreen(
            page = true,
            overlayChrome = true,
            frost = frost,
            topBar = { Box(Modifier.fillMaxWidth().height(56.dp)) { KitText("Explain this folder") } },
            bottom = { MessageBoxPane() },
            edges = KitScreenEdges.Bleed,
        ) {
            Box(Modifier.fillMaxSize().kitFrostSource(frost)) {
                val list = rememberKitFrostState()
                KitReportScrolledUnder { true }
                Column(Modifier.fillMaxSize().kitFrostSource(list).kitScreenListEdges()) {
                    repeat(24) { i -> Box(Modifier.fillMaxWidth().height(40.dp).background(if (i % 2 == 0) Color(0xFF8090A0) else Color(0xFF304050))) }
                }
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = kitScreenChromePadding().calculateBottomPadding())) { pill(list) }
            }
        }
    }

    @Composable
    private fun MessageBoxPane() {
        KitReportBottomSlab(true)
        val body = rememberKitLiquidJoinBody("bottom")
        val shape = RoundedCornerShape(26.dp)
        val face = kitLiquidFace(LiquidRole.Composer, LocalKitLiquidChrome.current, shape, join = body)
        Box(
            Modifier.fillMaxWidth()
                .height(72.dp)
                .then(if (face != null) Modifier.kitLiquidJoinRest(body, shape) else Modifier)
                .then(face ?: Modifier)
                .testTag("box")
        )
    }

    /** A bare drop of the box's glass in the page's picture, reading its own picture or the box's. */
    @Composable
    private fun BareDrop(list: KitFrostState, ownPicture: Boolean) {
        val drop = rememberKitLiquidJoinDrop("bottom", ownPicture = ownPicture)
        val shape = RoundedCornerShape(16.dp)
        val face = kitLiquidFace(LiquidRole.Pill, list, shape, join = drop)
        Box(Modifier.size(140.dp, 32.dp).then(if (face != null) Modifier.kitLiquidJoinRest(drop, shape) else Modifier).then(face ?: Modifier))
    }

    /** Runs [frames] frames of the conversation frame; returns the joined panes it drew and what it threw. */
    private fun conversation(pill: @Composable (list: KitFrostState) -> Unit): Pair<Int, Throwable?> {
        var joined = 0
        var thrown: Throwable? = null
        try {
            runSkikoComposeUiTest(Size(412f, 860f), testTimeout = PaneRimScenes.SCENE_TIMEOUT) {
                mainClock.autoAdvance = false
                setContent {
                    KitTheme(appearance = KitAppearance(style = Style.Glass), platform = platform(), localeTag = "en", stillFrame = true) {
                        ConversationFrame(rememberKitFrostState(), pill)
                    }
                }
                repeat(FRAMES) {
                    mainClock.advanceTimeBy(64)
                    waitForIdle()
                }
                joined = onAllNodes(SemanticsMatcher.keyIsDefined(KitLiquidJoined), useUnmergedTree = true).fetchSemanticsNodes().size
            }
        } catch (e: Throwable) {
            thrown = e
        }
        return joined to thrown
    }

    private fun Throwable.brokenRule(): KitRuleBroken? = generateSequence(this) { it.cause }.filterIsInstance<KitRuleBroken>().firstOrNull()

    @Test
    fun `B-KIT-LAYER-CYCLE-03 a drop that lies in the page's picture and draws from the box's copy of it trips the guard`() {
        val (_, thrown) = conversation { BareDrop(it, ownPicture = false) }
        assertNotNull(thrown?.brokenRule(), "the wave-one join (the pill drawing the box's picture) is a cycle: $thrown")
    }

    @Test
    fun `B-KIT-LAYER-CYCLE-04 the Glass conversation scrolled under the bar with Jump to latest joined to the box records without a cycle`() {
        val (bare, bareThrown) = conversation { BareDrop(it, ownPicture = true) }
        assertNull(bareThrown, "a drop reading its own picture")
        assertEquals(2, bare, "the drop and the box are one body")
        val (joined, thrown) = conversation { list -> KitJumpPill("Jump to latest", {}, visible = true, backdrop = list) }
        assertNull(thrown, "the joined jump pill")
        assertEquals(2, joined, "Jump to latest and the message box are one body of glass")
    }

    private companion object {
        const val FRAMES = 10
    }
}
