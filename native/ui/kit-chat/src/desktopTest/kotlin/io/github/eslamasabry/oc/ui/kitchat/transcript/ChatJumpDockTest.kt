@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarScope
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.turn.KitTurnLiveTone
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-CHAT-540..546 (owner decision O2 = D, 2026-11-24; O4 = the first pixel): "Jump to latest" is a docked row in
 * the live line's place, in the tree exactly while the person does not follow the newest end, over no text. The
 * scene is [ChatFloatingEdgesTest]'s (the header capsule, the message box, 40 rows); [Scene.live] puts a running
 * reply's live words at the row's start. The list's state is hoisted, so a test turns following off and on by hand.
 */
class ChatJumpDockTest {
    private data class Scene(
        val floating: Boolean = true,
        val rtl: Boolean = false,
        val fontScale: Float = 1f,
        val dark: Boolean = true,
        val motion: MotionLevel = MotionLevel.Off,
        /** A reply runs: the row repeats its live words at its start. */
        val live: Boolean = false,
    )

    private val rows =
        (0 until 40).map { i ->
            if (i == 0) "Row zero the newest line of the conversation"
            else "Row $i: the build finished and every check passed, then the notes were written."
        }

    /** The scene's list state, hoisted out of the composition. */
    private lateinit var list: TranscriptListState

    private fun ComposeUiTest.show(scene: Scene) {
        setContent { Themed(scene) { Page(scene) } }
        waitForIdle()
    }

    @Composable
    private fun Themed(scene: Scene, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalDensity provides Density(1f, scene.fontScale),
            LocalLayoutDirection provides if (scene.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            KitTheme(
                KitAppearance(
                    mode = if (scene.dark) AppearanceMode.Dark else AppearanceMode.Light,
                    motion = scene.motion,
                ),
                DesktopKitPlatform(),
                if (scene.rtl) "ar" else "en",
                stillFrame = true,
            ) {
                Inseted(content)
            }
        }
    }

    /** The scene's 360 by 780 box and the phone's status and gesture bars the harness stands in for. */
    @Composable
    private fun Inseted(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalKitScreenInsets provides WindowInsets(top = STATUS_DP.dp, bottom = NAV_DP.dp),
        ) {
            Box(Modifier.size(W.dp, H.dp).testTag("scene")) { content() }
        }
    }

    /** The page: a header capsule over the transcript, the message box over it. */
    @Composable
    private fun Page(scene: Scene) {
        val source = rememberKitFrostState()
        list = rememberTranscriptListState()
        KitScreen(
            page = true,
            overlayChrome = true,
            frost = source,
            floating = scene.floating,
            topBar = { Bar() },
            bottom = { Composer() },
            edges = KitScreenEdges.Bleed,
        ) {
            Box(Modifier.fillMaxSize().kitFrostSource(source)) { Rows(scene) }
        }
    }

    @Composable
    private fun Rows(scene: Scene) {
        KitTranscriptList(
            state = list,
            newestKey = rows.first(),
            jumpLabel = JUMP,
            motion = KitMotionLevel.Off,
            dockLive = if (scene.live) DOCK_LIVE else null,
        ) {
            items(rows.indices.toList(), key = { it }) { i ->
                KitText(rows[i], Modifier.padding(horizontal = 16.dp, vertical = 8.dp), role = TypeRole.Body)
            }
        }
    }

    @Composable
    private fun Bar() {
        KitTopBar(
            "Tidy the README",
            scope = {
                KitTopBarScope(
                    "demo-shop",
                    "This phone",
                    "Files of demo-shop",
                    onProject = {},
                    branch = "Git",
                    onBranch = {},
                )
            },
            exit = KitTopBarExit.Back,
            exitLabel = "Back",
            onExit = {},
            backBesideTitle = true,
        )
    }

    @Composable
    private fun Composer() {
        KitComposerBox(
            running = false,
            stacked = true,
            hasLeading = true,
            field = {
                KitText("Ask OpenCode…", role = TypeRole.Body, tone = TextTone.Tertiary, kind = KitTextKind.Frame)
            },
            leading = { KitComposerAdd("Tools", onClick = {}, open = false) },
            trailing = { KitComposerSend("Send", onClick = {}) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }

    private fun ComposeUiTest.bounds(tag: String): Rect =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.boundsOfText(text: String): Rect =
        onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.rowShown(): Boolean =
        onAllNodesWithTag("jump-to-latest", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The list at its twelfth row. A scroll from code keeps following, so the test sets following off by hand. */
    private fun ComposeUiTest.scrollAway() {
        onNodeWithTag("transcript-list").performScrollToIndex(12)
        runOnUiThread { list.following = false }
        waitForIdle()
    }

    private fun run(scene: Scene, body: ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(W, H) { show(scene); body() }

    /** The arrow after the label: the row's gap, then the small glyph on the label's bottom line. */
    private fun arrow(label: Rect, rtl: Boolean): Rect {
        val gap = Space.S2.dp.value
        val glyph = KitIconSize.Small.dp.toFloat()
        val left = if (rtl) label.left - gap - glyph else label.right + gap
        return Rect(left, label.bottom - glyph, left + glyph, label.bottom)
    }

    private fun lum(c: Color): Float {
        fun f(v: Float): Float {
            val even = ((v + 0.055f) / 1.055f).toDouble().pow(2.4)
            return if (v <= 0.03928f) v / 12.92f else even.toFloat()
        }
        return 0.2126f * f(c.red) + 0.7152f * f(c.green) + 0.0722f * f(c.blue)
    }

    /** Whether the pixel (x, y) lies in one of the rects, each widened by [INK_PAD]. */
    private fun onInk(x: Int, y: Int, ink: List<Rect>): Boolean =
        ink.any { r ->
            x >= r.left - INK_PAD && x <= r.right + INK_PAD && y >= r.top - INK_PAD && y <= r.bottom + INK_PAD
        }

    /**
     * Every pixel of the row's rect outside [ink] is the ground the row sits on (read at 2 dp in the same pixel
     * row): no text lies under the row.
     */
    private fun assertOverGround(image: PixelMap, row: Rect, ink: List<Rect>, what: String) {
        var y = ceil(row.top).toInt()
        while (y < row.bottom.toInt()) {
            val ground = lum(image[GROUND_X, y])
            var x = ceil(row.left).toInt()
            while (x < row.right.toInt()) {
                val off = abs(lum(image[x, y]) - ground)
                val cleared = !onInk(x, y, ink)
                if (cleared) assertTrue(off <= 0.02f, "$what: the pixel ($x, $y) is ${image[x, y]} off $ground")
                x += 1
            }
            y += 1
        }
    }

    private fun SemanticsNode.words(): String = config[SemanticsProperties.Text].joinToString(" ") { it.text }

    /** The top of every text node, by its words (the same words may sit on more than one node). */
    private fun ComposeUiTest.tops(): Map<String, List<Float>> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .groupBy { it.words() }
            .mapValues { (_, nodes) -> nodes.map { it.boundsInRoot.top }.sorted() }

    /** Each text node is where it was at rest ([rest]), within half a pixel. */
    private fun ComposeUiTest.assertTops(rest: Map<String, List<Float>>, moment: String) {
        val now = tops()
        for ((words, was) in rest) for (top in was) {
            val nowTops = now[words].orEmpty()
            assertTrue(nowTops.any { abs(it - top) <= 0.5f }, "$moment: \"$words\" moved from $top to $nowTops")
        }
    }

    /** The words drawn in the row: their text nodes. */
    private fun ComposeUiTest.rowWords(): List<SemanticsNode> {
        val text = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
        return onAllNodes(hasAnyAncestor(hasTestTag("jump-to-latest")) and text, useUnmergedTree = true)
            .fetchSemanticsNodes()
    }

    private fun inside(inner: Rect, outer: Rect): Boolean =
        inner.left >= outer.left - 0.5f &&
            inner.right <= outer.right + 0.5f &&
            inner.top >= outer.top - 0.5f &&
            inner.bottom <= outer.bottom + 0.5f

    /** Floating and solid, dark and light, left to right and right to left, text 1.0 and 1.3, with a reply running. */
    private fun everyLook(): List<Scene> =
        listOf(true, false).flatMap { floating ->
            listOf(true, false).flatMap { dark ->
                listOf(false, true).flatMap { rtl ->
                    listOf(1f, 1.3f).map { scale ->
                        Scene(floating = floating, dark = dark, rtl = rtl, fontScale = scale, live = true)
                    }
                }
            }
        }

    @Test
    fun `B-CHAT-540 the jump row appears at the first pixel of the person's drag`() =
        run(Scene()) {
            mainClock.autoAdvance = false
            // The finger moves toward the older rows (they come down from the top) and stays down.
            var slop = Float.NaN
            onNodeWithTag("transcript-list").performTouchInput {
                slop = viewConfiguration.touchSlop
                down(center)
                moveBy(Offset(0f, 2.dp.toPx()))
            }
            mainClock.advanceTimeByFrame()
            val why =
                "following = ${list.following}, the touch slop is $slop px, " +
                    "the list is at ${list.lazy.firstVisibleItemIndex}/${list.lazy.firstVisibleItemScrollOffset}"
            assertTrue(rowShown(), "the row is not in the tree one frame into the drag: $why")
        }

    @Test
    fun `B-CHAT-541 scrolled up with no reply the row is full width, at least 44 dp tall, words at the trailing end`() {
        for (rtl in listOf(false, true)) run(Scene(rtl = rtl)) {
            scrollAway()
            val row = bounds("jump-to-latest")
            val label = boundsOfText(JUMP)
            assertTrue(row.width >= W - 1f, "the row is ${row.width} dp wide (rtl = $rtl)")
            assertTrue(row.height >= 44f, "the row is ${row.height} dp tall (rtl = $rtl)")
            val atTheEnd = if (rtl) label.center.x < W / 2f else label.center.x > W / 2f
            assertTrue(atTheEnd, "the words are at x = ${label.center.x} (rtl = $rtl)")
        }
    }

    @Test
    fun `B-CHAT-542 the jump row never lies on a line of text`() {
        for (scene in everyLook()) run(scene) {
            scrollAway()
            val label = boundsOfText(JUMP)
            val ink = listOf(label, arrow(label, scene.rtl), bounds("transcript-dock-live"))
            val image = onNodeWithTag("scene").captureToImage().toPixelMap()
            assertOverGround(image, bounds("jump-to-latest"), ink, "$scene")
        }
    }

    @Test
    fun `B-CHAT-543 nothing above the row moves when it appears or goes`() =
        run(Scene(motion = MotionLevel.Full)) {
            mainClock.autoAdvance = false
            val rest = tops()
            mainClock.advanceTimeByFrame()
            assertTops(rest, "at rest")
            runOnUiThread { list.following = false }
            repeat(30) { frame ->
                mainClock.advanceTimeByFrame()
                assertTops(rest, "frame $frame of the row appearing")
                assertTrue(rowShown(), "the row is not in the tree in frame $frame of its appearing")
            }
            runOnUiThread { list.following = true }
            repeat(30) { frame ->
                mainClock.advanceTimeByFrame()
                assertTops(rest, "frame $frame of the row leaving")
                assertFalse(rowShown(), "the row is still in the tree in frame $frame of its leaving")
            }
        }

    @Test
    fun `B-CHAT-544 a tap anywhere on the row follows the newest line`() {
        for (live in listOf(false, true)) run(Scene(live = live)) {
            scrollAway()
            // A quarter of the way along the row from its start edge: the row spans the whole width.
            onNodeWithTag("jump-to-latest").performTouchInput { click(Offset(W / 4f, height / 2f)) }
            waitForIdle()
            assertTrue(list.following, "following after the tap (live = $live)")
            val newest = boundsOfText(rows.first())
            assertTrue(
                newest.bottom <= bounds("kit-composer-box").top,
                "the newest line is not back above the box: $newest",
            )
        }
    }

    @Test
    fun `B-CHAT-545 at 360 dp, text 1_3 and in Arabic the row's words are whole`() =
        run(Scene(fontScale = 1.3f, rtl = true, live = true)) {
            scrollAway()
            val row = bounds("jump-to-latest")
            val words = rowWords()
            assertTrue(words.isNotEmpty(), "the row shows no words")
            for (node in words) {
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                assertTrue(layouts.none { it.hasVisualOverflow }, "\"${node.words()}\" is cut: $layouts")
                val place = node.boundsInRoot
                assertTrue(inside(place, row), "\"${node.words()}\" lies at $place, outside the row $row")
            }
        }

    @Test
    fun `B-CHAT-546 with a reply running, the scrolled row carries the live words at its start, the jump at its end`() {
        for (rtl in listOf(false, true)) run(Scene(live = true, rtl = rtl)) {
            scrollAway()
            val live = bounds("transcript-dock-live")
            val label = boundsOfText(JUMP)
            val dy = abs(live.center.y - label.center.y)
            assertTrue(dy <= 2f, "the live words' centre is at y = ${live.center.y}, the label's at ${label.center.y}")
            val beforeTheLabel = if (rtl) live.left >= label.right else live.right <= label.left
            assertTrue(beforeTheLabel, "the live words are at $live and the label at $label (rtl = $rtl)")
        }
    }

    private companion object {
        const val W = 360
        const val H = 780
        const val STATUS_DP = 28
        const val NAV_DP = 20

        /** Where the ground is read in each pixel row of the row's rect: 2 dp in, inside the row's own padding. */
        const val GROUND_X = 2

        /** How far round the label, the arrow and the live words their pixels are left alone, dp. */
        const val INK_PAD = 2f

        const val JUMP = "Jump to latest"

        val DOCK_LIVE = KitTranscriptDockLive("Working…", KitTurnLiveTone.Working, "Working…", false)
    }
}
