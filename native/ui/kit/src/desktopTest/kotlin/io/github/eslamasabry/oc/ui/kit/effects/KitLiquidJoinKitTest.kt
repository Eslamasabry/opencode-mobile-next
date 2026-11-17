@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderCompiler
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import kotlin.test.Test

/**
 * Joined glass in the kit (docs/designs/liquid-merge-one-body.md section 4, "Kit tests"): a menu opened from a control
 * on the bar's liquid capsule is drawn as one body with it, both panes by the joined pane; when either cannot be
 * liquid (the Frosted look, no lens pass on the platform) both stay the library's panes, and nothing joins once the menu
 * has gone.
 */
class KitLiquidJoinKitTest {
    private val glass = KitAppearance(style = Style.Glass)

    private val joinedPane = androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(KitLiquidJoined)

    /** A platform with the lens and, when [joins], desktop's join shader and lens pass. */
    private fun platform(joins: Boolean): FakeKitPlatform =
        FakeKitPlatform(
            liquidGlass = true,
            liveShaders = if (joins) LiveShaderCompiler(DesktopLiveShader::compile) else null,
            joinEffect = if (joins) ({ p, b -> DesktopKitPlatform().liquidJoinEffect(p, b) }) else null,
        )

    @Composable
    private fun Scene(open: Boolean, onClose: () -> Unit) {
        val source = rememberKitFrostState()
        KitScreen(
            page = true,
            overlayChrome = true,
            frost = source,
            topBar = {
                Box(Modifier.fillMaxWidth().height(56.dp)) {
                    KitMenu(
                        expanded = open,
                        items = listOf(KitMenuItem("Find", onSelect = {}), KitMenuItem("Rename", onSelect = {}), KitMenuItem("Fork", onSelect = {})),
                        onDismissRequest = onClose,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                    ) { KitText("More") }
                }
            },
            edges = KitScreenEdges.Bleed,
        ) {
            Column(Modifier.fillMaxSize().kitFrostSource(source)) {
                repeat(24) { i -> Box(Modifier.fillMaxWidth().height(30.dp).background(if (i % 2 == 0) Color(0xFF3355FF) else Color(0xFFFFCC22))) }
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.joined(): Int =
        onAllNodes(joinedPane, useUnmergedTree = true).fetchSemanticsNodes().size

    /**
     * Steps the TEST clock in jumps of [STEP_MS], at most [BUDGET_MS] of simulated time in all, until [expected] panes are
     * joined, and returns the count it ended on (the caller asserts it, so a join that never comes or never goes still
     * fails, as an assertion and not as a timeout).
     *
     * Why the budget: what costs time is the animation the clock plays, not the number of calls. Measured on CI, an
     * animating frame of joined glass costs about 0.4 s in software, which is 15 to 25 s of wall time per simulated
     * second (runs 44087366721 and 44100715286: a 250 ms jump took 6.3 s, a 2.3 s jump over 30 s). The join is composed
     * within the panel's 120 ms wait and a joined pane is gone once the 400 ms close hold ends, so each phase needs well
     * under [BUDGET_MS]. Worst case by construction: open plus close, 2 x 0.8 s x 25 s = 40 s, under the 60 s limit. The old
     * test played 2 x 1 s after waiting for idle on an auto-advancing clock (about 150 frames) and passed alone only.
     * Motion stays Full: with motion Off the join is not drawn at all (the panes this test counts are not there).
     */
    private fun androidx.compose.ui.test.ComposeUiTest.settleTo(expected: Int, budgetMs: Long = BUDGET_MS): Int {
        var spent = 0L
        while (spent < budgetMs) {
            if (joined() == expected) return expected
            mainClock.advanceTimeBy(STEP_MS, ignoreFrameDuration = true)
            waitForIdle()
            spent += STEP_MS
        }
        return joined()
    }

    private fun joinedPanes(appearance: KitAppearance, joins: Boolean, open: Boolean): Int {
        var count = -1
        runDesktopComposeUiTest(412, 800) {
            mainClock.autoAdvance = false
            var shown by mutableStateOf(false)
            setContent { KitTheme(appearance = appearance, platform = platform(joins), localeTag = "en") { Scene(shown) { shown = false } } }
            waitForIdle()
            shown = open
            waitForIdle()
            // Where nothing is to join, wait the same time so a late join would show; where it joins, stop when it has.
            count = settleTo(if (open && joins && appearance.glassLook != GlassLookId.Frosted) 2 else -1)
        }
        return count
    }

    @Test
    fun `liquid join - a menu from the bar's control and the bar are one body of glass`() {
        kotlin.test.assertEquals(2, joinedPanes(glass, joins = true, open = true), "the bar's side and the menu's side")
    }

    @Test
    fun `liquid join - with no menu open the bar is the library's pane`() {
        kotlin.test.assertEquals(0, joinedPanes(glass, joins = true, open = false))
    }

    @Test
    fun `liquid join - a partner that is frost keeps both panes the library's`() {
        kotlin.test.assertEquals(0, joinedPanes(glass.copy(glassLook = GlassLookId.Frosted), joins = true, open = true), "the Frosted look")
    }

    @Test
    fun `liquid join - a platform without the lens pass never joins`() {
        kotlin.test.assertEquals(0, joinedPanes(glass, joins = false, open = true))
    }

    @Test
    fun `liquid join - nothing joins once the menu has closed`() = runDesktopComposeUiTest(412, 800) {
        mainClock.autoAdvance = false
        var shown by mutableStateOf(false)
        setContent { KitTheme(appearance = glass, platform = platform(true), localeTag = "en") { Scene(shown) { shown = false } } }
        shown = true
        waitForIdle()
        kotlin.test.assertEquals(2, settleTo(2), "open: the bar's side and the menu's side")
        shown = false
        waitForIdle()
        kotlin.test.assertEquals(0, settleTo(0), "closed: nothing joins")
        onAllNodes(joinedPane, useUnmergedTree = true).assertCountEquals(0)
    }

    /** A chat-like page whose bottom block is one liquid slab (the body "bottom"), and the jump pill over the body. */
    @Composable
    private fun PillScene(pill: Boolean) {
        val source = rememberKitFrostState()
        KitScreen(
            page = true,
            overlayChrome = true,
            frost = source,
            topBar = { Box(Modifier.fillMaxWidth().height(56.dp)) },
            bottom = {
                io.github.eslamasabry.oc.ui.kit.frame.KitReportBottomSlab(true)
                val shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
                val body = rememberKitLiquidJoinBody("bottom")
                val face = kitLiquidFace(io.github.eslamasabry.oc.ui.design.effects.LiquidRole.Composer, LocalKitLiquidChrome.current, shape, join = body)
                Box(Modifier.fillMaxWidth().height(104.dp).kitLiquidJoinRest(body, shape).then(face ?: Modifier.background(Color.Gray)))
            },
            edges = KitScreenEdges.Bleed,
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().kitFrostSource(source)) {
                    repeat(24) { i -> Box(Modifier.fillMaxWidth().height(30.dp).background(if (i % 2 == 0) Color(0xFF3355FF) else Color(0xFFFFCC22))) }
                }
                io.github.eslamasabry.oc.ui.kit.frame.KitJumpPill(
                    "Jump to latest",
                    {},
                    visible = pill,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp),
                    backdrop = source,
                )
            }
        }
    }

    private fun pillPanes(appearance: KitAppearance, joins: Boolean, pill: Boolean): Int {
        var count = -1
        runDesktopComposeUiTest(412, 800) {
            setContent { KitTheme(appearance = appearance, platform = platform(joins), localeTag = "en") { PillScene(pill) } }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            count = onAllNodes(joinedPane, useUnmergedTree = true).fetchSemanticsNodes().size
        }
        return count
    }

    @Test
    fun `liquid join - the jump pill and the message box are one body of glass`() {
        kotlin.test.assertEquals(2, pillPanes(glass, joins = true, pill = true), "the box's side and the pill's side")
    }

    @Test
    fun `liquid join - with no pill shown, or no lens pass, the box is the library's pane`() {
        kotlin.test.assertEquals(0, pillPanes(glass, joins = true, pill = false), "no pill")
        kotlin.test.assertEquals(0, pillPanes(glass, joins = false, pill = true), "no lens pass")
        kotlin.test.assertEquals(0, pillPanes(glass.copy(glassLook = GlassLookId.Frosted), joins = true, pill = true), "the Frosted look")
    }

    @Test
    fun `liquid join - the joined pill rests sunk a quarter of its height into the box`() = runDesktopComposeUiTest(412, 800) {
        setContent { KitTheme(appearance = glass, platform = platform(true), localeTag = "en") { PillScene(true) } }
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        val pill = onNodeWithTag("kit-jump-pill-veiled", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        val box = onAllNodes(joinedPane, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInWindow }.maxBy { it.height }
        val sink = io.github.eslamasabry.oc.ui.design.effects.LiquidJoin.PILL_SINK_DP * density.density
        kotlin.test.assertEquals(box.top + sink, pill.bottom, 1.5f, "the pill's foot ${pill.bottom} vs the box's top ${box.top}")
    }

    @Test
    fun `liquid join material - a second line on glass is brighter than on the page, a first line unchanged`() = runDesktopComposeUiTest(100, 100) {
        val seen = mutableMapOf<String, Color>()
        setContent {
            KitTheme(appearance = glass, platform = platform(true), localeTag = "en") {
                val colors = io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current
                seen["text1"] = colors.text1
                seen["text2"] = colors.text2
                seen["page"] = io.github.eslamasabry.oc.ui.kit.kitToneOnGlass(io.github.eslamasabry.oc.ui.design.type.TextTone.Secondary)
                androidx.compose.runtime.CompositionLocalProvider(LocalKitOnLiquidGlass provides true) {
                    seen["glass"] = io.github.eslamasabry.oc.ui.kit.kitToneOnGlass(io.github.eslamasabry.oc.ui.design.type.TextTone.Secondary)
                    seen["glass1"] = io.github.eslamasabry.oc.ui.kit.kitToneOnGlass(io.github.eslamasabry.oc.ui.design.type.TextTone.Primary)
                }
            }
        }
        waitForIdle()
        kotlin.test.assertEquals(seen.getValue("text2"), seen.getValue("page"), "on the page: text2")
        kotlin.test.assertEquals(seen.getValue("text1"), seen.getValue("glass1"), "a first line is unchanged on glass")
        fun lum(c: Color) = .2126f * c.red + .7152f * c.green + .0722f * c.blue
        val toward = kotlin.math.abs(lum(seen.getValue("glass")) - lum(seen.getValue("text1")))
        val before = kotlin.math.abs(lum(seen.getValue("text2")) - lum(seen.getValue("text1")))
        kotlin.test.assertTrue(toward < before * .5f, "the second line on glass is most of the way to the first line's ink")
    }

    private companion object {
        /** One jump of the test clock, ms (a few frames at 60 Hz). */
        const val STEP_MS = 100L

        /** Simulated time a phase may take: past the 120 ms wait for the join and the 400 ms close hold, with room. */
        const val BUDGET_MS = 800L
    }
}
