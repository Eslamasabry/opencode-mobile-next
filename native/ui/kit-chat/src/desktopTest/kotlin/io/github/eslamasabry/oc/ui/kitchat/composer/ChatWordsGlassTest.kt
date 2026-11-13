@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidChrome
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitReportBottomSlab
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpEdge
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenFloat
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitTranscriptList
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Words on the chat's glass (device, Android 15, Glass dark, 2026-11-11): the message box's placeholder ("Steer while it
 * works…", "Ask OpenCode…") had bright transcript lines running through it, and the "messages waiting to send" line
 * under the bar must never have transcript text behind it. The transcript fades out as it passes under the message box,
 * full at the box's top edge and gone by the inner end of the lens rim, so the rim still bends real content while the
 * words sit on nothing bright. Every measure compares a scene whose rows are bright with the same scene whose rows draw
 * nothing: same layout, same jump pill, same join; only what lies behind the glass differs.
 */
class ChatWordsGlassTest {
    /**
     * Desktop with the lens, as Android 13+ has it: a liquid pane draws what it reads blurred by its recipe's blur,
     * under its tint. No bend and no rim light (the desktop cannot show those).
     */
    private class LensPlatform : KitPlatform by DesktopKitPlatform() {
        override val liquidGlass: Boolean = true

        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
            val at = arrayOfNulls<LayoutCoordinates>(1)
            val draw = backdropDraw(backdrop)
            return Modifier.onGloballyPositioned { at[0] = it }
                .clip(shape)
                .drawWithCache {
                    val layer = obtainGraphicsLayer()
                    val r = spec.recipe.blurDp.dp.toPx()
                    onDrawBehind {
                        val c = at[0] ?: return@onDrawBehind
                        layer.renderEffect = BlurEffect(r, r)
                        layer.record { draw(this, c) }
                        drawLayer(layer)
                        drawRect(spec.fill)
                    }
                }
        }

        /** The backdrop's draw is kit-internal; a test outside the kit reaches it through its JVM getter. */
        @Suppress("UNCHECKED_CAST")
        private fun backdropDraw(backdrop: KitLiquidBackdrop): (DrawScope, LayoutCoordinates) -> Unit {
            val getter = KitLiquidBackdrop::class.java.methods.first { it.name.startsWith("getDraw") && it.parameterCount == 0 }
            return getter.invoke(backdrop) as (DrawScope, LayoutCoordinates) -> Unit
        }
    }

    private class Shot(
        val image: PixelMap,
        val box: DpRect,
        val placeholder: DpRect,
        val field: DpRect,
        val status: DpRect?,
        val pill: DpRect?,
        val bar: DpRect,
        /** The "Needs your decision" label above the box (a scene with a card), else null. */
        val label: DpRect?,
        /** The phrase chip above the box (a scene with chips), else null. */
        val chip: DpRect?,
        val rows: List<DpRect>,
        val list: String,
        /** Whether the bottom block saw the screen's own recording (the frame handed it nothing else). */
        val bottomSawSource: Boolean,
        /** Whether the bottom block saw any recording at all. */
        val bottomSawSome: Boolean = false,
        /** The whole bottom block, its padding inside. */
        val block: DpRect? = null,
        /** The jump pill's face (its glass), when it shows. */
        val pillFace: DpRect? = null,
        /** The "N earlier messages" chip under the bar (a scene with it), else null. */
        val earlier: DpRect? = null,
        /** How many phrase rows are laid out. */
        val chipRows: Int = 0,
        /** How many panes are drawn as one joined body of glass (the box and the pill). */
        val joined: Int = 0,
    )

    private fun dark(style: Style, look: GlassLookId = GlassLookId.Glossy) =
        KitAppearance(style = style, mode = AppearanceMode.Dark, glassLook = look)

    /**
     * A chat frame: a bar, optionally the "waiting to send" line under it, the transcript ([bright] white rows, or rows
     * that draw nothing) scrolled well away from the newest so it runs under the bar and the message box, and the box.
     */
    @Composable
    private fun Scene(
        bright: Boolean,
        notice: Boolean,
        seen: Array<Any?>,
        lazy: Array<LazyListState?>,
        statusDp: Int,
        card: Boolean = false,
        chips: Boolean = false,
        outlined: Boolean = false,
        earlier: Boolean = false,
        tall: Boolean = false,
    ) {
        CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = statusDp.dp, bottom = NAV_DP.dp)) {
            Box(Modifier.size(W.dp, H.dp).testTag("scene")) {
                val source = rememberKitFrostState()
                val state = rememberTranscriptListState()
                lazy[0] = state.lazy
                seen[1] = source
                KitScreen(
                    page = true,
                    overlayChrome = true,
                    frost = source,
                    topBar = { Box(Modifier.fillMaxWidth().height(BAR_DP.dp).testTag("bar")) },
                    status =
                        if (!notice) null
                        else
                            KitStatus(
                                KitStatusKind.Info,
                                "1 message is waiting to send.",
                                id = "chat-pending-sends",
                                tone = StatusTone.Attention,
                                action = KitLineAction("Review", {}),
                            ),
                    bottom = {
                        seen[0] = LocalKitLiquidChrome.current
                        KitReportBottomSlab(kitComposerSlab())
                        // A request card's label stands above the box inside the same block (ChatScreen): a bar of
                        // the card's size with nothing of its own behind it.
                        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
                        if (chips)
                            io.github.eslamasabry.oc.ui.kit.chip.KitPhraseChips(
                                listOf("say hi"), "Phrases", { "Insert $it" }, { "Options for $it" }, "Hide", {}, {},
                                Modifier.testTag("chat-phrase-chips"),
                            )
                        if (card) Box(Modifier.fillMaxWidth().height(LABEL_DP.dp).testTag("decision-label"))
                        KitComposerBox(
                            running = false,
                            field = {
                                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("field"), contentAlignment = Alignment.CenterStart) {
                                    KitText(
                                        "Ask OpenCode…",
                                        Modifier.testTag("placeholder"),
                                        role = TypeRole.Body,
                                        tone = TextTone.Tertiary,
                                        kind = KitTextKind.Frame,
                                    )
                                }
                            },
                            leading = { KitComposerAdd("Tools", onClick = {}, open = false) },
                            trailing = { KitComposerSend("Send", onClick = {}) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        }
                    },
                    edges = KitScreenEdges.Bleed,
                ) {
                    Box(Modifier.fillMaxSize().kitFrostSource(source)) {
                        KitTranscriptList(state, "row-0", "Jump to latest", KitMotionLevel.Off) {
                            items((0 until ROWS).map { "row-$it" }, key = { it }) { key ->
                                Box(
                                    Modifier.fillMaxWidth()
                                        .height(if (outlined || tall) CODE_DP.dp else ROW_DP.dp)
                                        .then(
                                            when {
                                                !bright -> Modifier
                                                // A code block: its outline only, the words inside it left out.
                                                outlined ->
                                                    Modifier.padding(horizontal = 12.dp)
                                                        .border(1.dp, Color.White, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                                else -> Modifier.background(Color.White)
                                            }
                                        )
                                        .testTag(key)
                                )
                            }
                        }
                        if (earlier)
                            io.github.eslamasabry.oc.ui.kit.chip.KitChip(
                                "40 earlier messages",
                                onClick = {},
                                modifier =
                                    Modifier.align(Alignment.TopCenter)
                                        .padding(top = 8.dp + io.github.eslamasabry.oc.ui.kit.frame.kitScreenChromePadding().calculateTopPadding())
                                        .kitScreenFloat(KitJumpEdge.Top)
                                        .testTag("earlier"),
                            )
                    }
                }
            }
        }
    }

    private fun shot(
        appearance: KitAppearance,
        platform: KitPlatform,
        bright: Boolean,
        notice: Boolean = false,
        statusDp: Int = STATUS_DP,
        card: Boolean = false,
        chips: Boolean = false,
        outlined: Boolean = false,
        earlier: Boolean = false,
        scrollTo: Int = SCROLLED_TO,
        /** Rows as tall as a code block, filled (with [bright]). */
        tall: Boolean = false,
        /** How far past [scrollTo]'s resting place the list is drawn, dp (see [shot]'s body). */
        nudgeDp: Int = 0,
    ): Shot {
        lateinit var shot: Shot
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            val seen = arrayOfNulls<Any?>(2)
            val lazy = arrayOfNulls<LazyListState>(1)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(appearance = appearance, platform = platform, localeTag = "en", stillFrame = true) {
                        Scene(bright, notice, seen, lazy, statusDp, card, chips, outlined, earlier, tall)
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("transcript-list").performScrollToIndex(scrollTo)
            waitForIdle()
            // A person reading history drags the list, which stops it following the newest message; a scroll from code
            // keeps following, so the phrase row folding (the block's height changing) would pin it back to the newest.
            // The finger moves 120 dp toward older messages and rests before it lifts, so no fling carries the list on.
            if (chips && scrollTo > 0)
                onNodeWithTag("transcript-list").performTouchInput {
                    down(center)
                    repeat(12) { moveBy(Offset(0f, 10f * D)) }
                    advanceEventTime(200)
                    up()
                }
            waitForIdle()
            // Near the newest message (not away from it): the newest row drawn [nudgeDp] past its resting place, so its
            // lower part lies under the bottom block.
            if (nudgeDp > 0) {
                runOnIdle { lazy[0]!!.requestScrollToItem(scrollTo, (nudgeDp * D).roundToInt()) }
                waitForIdle()
            }
            // The phrase row folding, the pill rising out of the box, the page's edges easing to their new place.
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            val l = lazy[0]!!
            val visible = l.layoutInfo.visibleItemsInfo.map { it.key }
            val info = l.layoutInfo
            val list =
                "first=${l.firstVisibleItemIndex}/${l.firstVisibleItemScrollOffset} visible=$visible " +
                    "viewport=${info.viewportStartOffset}..${info.viewportEndOffset} pad=${info.beforeContentPadding}/${info.afterContentPadding} " +
                    "back=${l.canScrollBackward} fwd=${l.canScrollForward}"
            fun bounds(tag: String) = onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
            fun boundsOrNull(tag: String) = runCatching { bounds(tag) }.getOrNull()
            shot =
                Shot(
                    image = onNodeWithTag("scene").captureToImage().toPixelMap(),
                    box = bounds("kit-composer-box"),
                    placeholder = bounds("placeholder"),
                    field = bounds("field"),
                    status = if (notice) bounds("kit-screen-status") else null,
                    pill = boundsOrNull("jump-to-latest"),
                    bar = bounds("bar"),
                    label = if (card) bounds("decision-label") else null,
                    chip = if (chips) boundsOrNull("chat-phrase-chips") else null,
                    rows = visible.map { bounds(it.toString()) },
                    list = list,
                    bottomSawSource = seen[0] === seen[1],
                    bottomSawSome = seen[0] != null,
                    block = bounds("kit-screen-bottom"),
                    pillFace = boundsOrNull("kit-jump-pill-veiled"),
                    earlier = if (earlier) bounds("earlier") else null,
                    chipRows = onAllNodesWithTag("chat-phrase-chips", useUnmergedTree = true).fetchSemanticsNodes().size,
                    joined =
                        onAllNodes(SemanticsMatcher.keyIsDefined(io.github.eslamasabry.oc.ui.kit.effects.KitLiquidJoined), useUnmergedTree = true)
                            .fetchSemanticsNodes().size,
                )
        }
        return shot
    }

    private fun luma(c: Color): Float = (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue) * 255f

    /** A coarse picture of where [a] differs from [b] (8 dp cells, digit = difference / 26), for a failure message. */
    private fun map(a: PixelMap, b: PixelMap, left: Float, top: Float, right: Float, bottom: Float): String =
        (top.toInt() until bottom.toInt() step 8).joinToString("\n") { y ->
            "%4d ".format(y) + (left.toInt() until right.toInt() step 8).joinToString("") { x ->
                diff(a, b, x.toFloat(), y.toFloat(), x + 8f, y + 8f).second.let { (it / 26f).toInt().coerceAtMost(9).toString() }
            }
        }

    /** The mean and the largest brightness difference (of 255) between [a] and [b] over [left, top, right, bottom] dp. */
    private fun diff(a: PixelMap, b: PixelMap, left: Float, top: Float, right: Float, bottom: Float): Pair<Float, Float> {
        var sum = 0f
        var max = 0f
        var n = 0
        for (y in (top * D).roundToInt() until (bottom * D).roundToInt()) for (x in (left * D).roundToInt() until (right * D).roundToInt()) {
            if (x !in 0 until a.width || y !in 0 until a.height) continue
            val d = abs(luma(a[x, y]) - luma(b[x, y]))
            sum += d
            max = maxOf(max, d)
            n++
        }
        return (if (n == 0) 0f else sum / n) to max
    }

    /**
     * Owner 2026-11-14 ("Wtfff": "Jump to latest" sat on a code block's words and bent them; a smear of bent transcript
     * along the message box's top edge): reading history, nothing of the transcript shows in the bottom block (the box
     * and its rim included) or behind the jump pill, at any scroll offset; it fades out above them over 16 dp in steps.
     */
    @Test
    fun `B-CHAT-COL-01 nothing of the transcript shows in the bottom block or behind the jump pill, and it fades out above them`() {
        for (at in listOf(SCROLLED_TO, SCROLLED_TO + 7)) {
            val bright = shot(dark(Style.Glass), LensPlatform(), bright = true, scrollTo = at)
            val none = shot(dark(Style.Glass), LensPlatform(), bright = false, scrollTo = at)
            val block = bright.block!!
            val face = assertNotNull(bright.pillFace, "scrolled to $at: the jump pill shows")
            assertTrue(bright.rows.any { it.bottom > block.top + 40.dp }, "scrolled to $at: rows lie under the block: ${bright.rows.map { it.top..it.bottom }} block $block")
            // The whole block, its rim and its margins.
            val (blockMean, blockMax) = diff(bright.image, none.image, 0f, block.top.value, W.toFloat(), H.toFloat())
            println("scrolled to $at: block mean $blockMean max $blockMax over $block, pill $face")
            assertTrue(
                blockMean <= WORDS_MEAN && blockMax <= WORDS_MAX,
                "scrolled to $at: the rows show in the bottom block: mean $blockMean, max $blockMax\n" + map(bright.image, none.image, 0f, block.top.value - 40f, W.toFloat(), H.toFloat()),
            )
            // The pill's face: nothing of a row behind it, through its glass or around it.
            val (pillMean, pillMax) = diff(bright.image, none.image, face.left.value - 4f, face.top.value - 4f, face.right.value + 4f, face.bottom.value)
            assertTrue(pillMean <= WORDS_MEAN && pillMax <= WORDS_MAX, "scrolled to $at: the rows show behind the jump pill: mean $pillMean, max $pillMax")
            // Above them, beside the pill: whole, then a soft fade in steps, then gone before the pill's top.
            val gone = minOf(block.top.value, face.top.value)
            assertFadesOut(bright.image, none.image, x = 8f, from = gone - 40f, to = gone, what = "scrolled to $at")
        }
    }

    /**
     * Down column [x] dp from [from] to [to] dp the bright scene goes from whole to nothing over a soft fade: whole at
     * the start, nothing by [to], at least four levels between, no hard step.
     */
    private fun assertFadesOut(a: PixelMap, b: PixelMap, x: Float, from: Float, to: Float, what: String) {
        val px = (x * D).roundToInt()
        val ramp = ((from * D).roundToInt()..(to * D).roundToInt()).map { y -> abs(luma(a[px, y]) - luma(b[px, y])) }
        val full = ramp.first()
        println("$what: fade ${ramp.map { it.toInt() }}")
        assertTrue(full >= 150f, "$what: the rows are whole $from dp: $full")
        assertTrue(ramp.last() <= WORDS_MAX, "$what: nothing of the rows at $to dp: ${ramp.last()}")
        assertTrue(ramp.zipWithNext().all { (p, q) -> q <= p + 2f }, "$what: the rows go without coming back: ${ramp.map { it.toInt() }}")
        val levels = ramp.filter { it > WORDS_MAX && it < full * .9f }.map { (it / 8f).toInt() }.distinct()
        assertTrue(levels.size >= 4, "$what: a soft fade, not a cut: ${ramp.map { it.toInt() }}")
        assertTrue(ramp.zipWithNext().maxOf { (p, q) -> abs(q - p) } <= full / 6f, "$what: a hard step in the fade: ${ramp.map { it.toInt() }}")
    }

    /** Owner 2026-11-14: the code block was cut at its words but its outline ran on. A clearing hides everything. */
    @Test
    fun `B-CHAT-COL-02 a code block passing the bottom edge loses its outline with its words`() {
        val bright = shot(dark(Style.Glass), LensPlatform(), bright = true, outlined = true)
        val none = shot(dark(Style.Glass), LensPlatform(), bright = false, outlined = true)
        val block = bright.block!!
        assertTrue(bright.rows.any { it.top < block.top && it.bottom > block.top }, "a code block crosses the block's top: ${bright.rows.map { it.top..it.bottom }} block $block")
        // Its two side lines, from the block's top down.
        for (x in listOf(12f, 13f, W - 13f, W - 12f)) {
            val (mean, max) = diff(bright.image, none.image, x - 1f, block.top.value, x + 1f, H.toFloat())
            assertTrue(mean <= WORDS_MEAN && max <= WORDS_MAX, "the code block's outline runs on at $x dp under the block: mean $mean, max $max")
        }
        // Above the fade it is there.
        val (above, _) = diff(bright.image, none.image, 11f, block.top.value - 120f, 14f, block.top.value - 60f)
        assertTrue(above >= 20f, "the code block's outline shows above the bottom block: $above")
    }

    /**
     * Owner 2026-11-14 ("Why did you unlink jump to latest from the composer it was good already!!"): reading history
     * with a phrase row above the box, the row folds away and the pill is a drop of the message box, one body of glass;
     * at the newest message the chips are back and the pill is gone.
     */
    @Test
    fun `B-CHAT-COL-03 reading history the phrase row folds away and the pill joins the box, at the newest message the chips return`() {
        val away = shot(dark(Style.Glass), LensPlatform(), bright = true, chips = true)
        println("away: chips ${away.chipRows} joined ${away.joined} pill ${away.pill} face ${away.pillFace} box ${away.box}")
        assertEquals(0, away.chipRows, "no phrase row is laid out while reading history")
        assertEquals(2, away.joined, "the box and the pill are one body of glass")
        val face = assertNotNull(away.pillFace)
        assertTrue(face.bottom > away.box.top, "the pill rests sunk into the box: face $face box ${away.box}")
        val latest = shot(dark(Style.Glass), LensPlatform(), bright = true, chips = true, scrollTo = 0)
        println("latest: chips ${latest.chipRows} joined ${latest.joined} pill ${latest.pillFace}")
        assertEquals(1, latest.chipRows, "the phrase row is back at the newest message")
        assertNull(latest.pillFace, "no pill at the newest message")
    }

    /**
     * A decision waiting above the box: the pill stays clear of the card, not joined, in the faded zone above the block,
     * with nothing of the transcript behind it.
     */
    @Test
    fun `B-CHAT-COL-04 with a request card above the box the pill stands apart over no words`() {
        val bright = shot(dark(Style.Glass), LensPlatform(), bright = true, card = true)
        val none = shot(dark(Style.Glass), LensPlatform(), bright = false, card = true)
        val face = assertNotNull(bright.pillFace, "the pill shows")
        assertEquals(0, bright.joined, "the pill is not joined to the box under the card")
        assertTrue(face.bottom <= bright.block!!.top, "the pill sits above the block: $face block ${bright.block}")
        val (mean, max) = diff(bright.image, none.image, face.left.value - 4f, face.top.value - 4f, face.right.value + 4f, face.bottom.value + 4f)
        println("pill over card: mean $mean max $max at $face, none ${none.pillFace}, block ${bright.block}, ${bright.list}")
        if (mean > WORDS_MEAN || max > WORDS_MAX) {
            // Where it differs: per 2 dp band, the largest difference, where, and each scene's brightness there.
            var y = face.top.value - 8f
            while (y < face.bottom.value + 8f) {
                var best = 0f
                var bx = 0f
                var x = face.left.value - 16f
                while (x < face.right.value + 16f) {
                    val d = diff(bright.image, none.image, x, y, x + 1f, y + 2f).second
                    if (d > best) {
                        best = d
                        bx = x
                    }
                    x += 1f
                }
                val px = (bx * D).roundToInt()
                val py = (y * D).roundToInt()
                println("diag y=$y max=${best.toInt()} at x=$bx bright=${luma(bright.image[px, py]).toInt()} none=${luma(none.image[px, py]).toInt()}")
                y += 2f
            }
            val plainBright = shot(dark(Style.Glass), DesktopKitPlatform(), bright = true, card = true)
            val plainNone = shot(dark(Style.Glass), DesktopKitPlatform(), bright = false, card = true)
            val pf = plainBright.pillFace
            if (pf != null) {
                val (pm, px) = diff(plainBright.image, plainNone.image, pf.left.value - 4f, pf.top.value - 4f, pf.right.value + 4f, pf.bottom.value + 4f)
                println("diag no lens: mean $pm max $px at $pf none ${plainNone.pillFace}")
            }
        }
        assertTrue(
            mean <= WORDS_MEAN && max <= WORDS_MAX,
            "the rows show behind the pill: mean $mean, max $max (none: ${none.pillFace})\n" +
                map(bright.image, none.image, face.left.value - 16f, face.top.value - 24f, face.right.value + 16f, bright.block!!.top.value + 8f),
        )
        assertFadesOut(bright.image, none.image, x = 8f, from = face.top.value - 40f, to = face.top.value, what = "over the card")
    }

    /** Owner 2026-11-14: "40 earlier messages" sat on a line of the transcript. */
    @Test
    fun `B-CHAT-COL-05 nothing of the transcript shows behind N earlier messages, and it fades in below it`() {
        val bright = shot(dark(Style.Glass), LensPlatform(), bright = true, earlier = true)
        val none = shot(dark(Style.Glass), LensPlatform(), bright = false, earlier = true)
        val chip = bright.earlier!!
        val (mean, max) = diff(bright.image, none.image, 0f, 0f, W.toFloat(), chip.bottom.value)
        println("earlier: mean $mean max $max at $chip, bar ${bright.bar}")
        assertTrue(mean <= WORDS_MEAN && max <= WORDS_MAX, "the rows show above or behind the earlier-messages chip: mean $mean, max $max\n" + map(bright.image, none.image, 0f, 0f, W.toFloat(), chip.bottom.value + 24f))
        // Below it the rows come back over 12 dp, as below the bar: read the fade upward.
        val px = (8f * D).roundToInt()
        val ramp = ((chip.bottom.value * D).roundToInt()..((chip.bottom.value + 16f) * D).roundToInt()).map { y -> abs(luma(bright.image[px, y]) - luma(none.image[px, y])) }
        println("earlier fade ${ramp.map { it.toInt() }}")
        assertTrue(ramp.first() <= WORDS_MAX && ramp.last() >= 150f, "the rows fade in below the chip: ${ramp.map { it.toInt() }}")
        assertTrue(ramp.filter { it > WORDS_MAX && it < ramp.last() * .9f }.map { (it / 8f).toInt() }.distinct().size >= 4, "a soft fade: ${ramp.map { it.toInt() }}")
    }

    @Test
    fun `a bright row never shows behind the Needs your decision label above the box, and the pill stays above the card`() {
        val bright = shot(dark(Style.Glass), LensPlatform(), bright = true, card = true)
        val none = shot(dark(Style.Glass), LensPlatform(), bright = false, card = true)
        val label = bright.label!!
        assertTrue(bright.rows.any { it.top < label.bottom && it.bottom > label.top }, "rows run behind the label: ${bright.rows.map { it.top..it.bottom }} label $label")
        val (mean, max) = diff(bright.image, none.image, label.left.value, label.top.value, label.right.value, label.bottom.value)
        println("label: mean $mean max $max over $label, pill ${bright.pill}, box ${bright.box}")
        assertTrue(mean <= NOTICE_MEAN && max <= NOTICE_MAX, "bright rows show behind the label: mean $mean, max $max of 255")
        // The words on the box are as clear as without a card.
        val (wordsMean, wordsMax) =
            diff(bright.image, none.image, bright.field.left.value, bright.placeholder.top.value, bright.field.right.value, bright.placeholder.bottom.value)
        assertTrue(wordsMean <= WORDS_MEAN && wordsMax <= WORDS_MAX, "bright rows show behind the placeholder: mean $wordsMean, max $wordsMax")
        // The jump pill sits above the card (where the layout puts it), not joined onto the box's top under the label.
        bright.pill?.let { assertTrue(it.bottom <= label.top + 1.dp, "the pill ${it} lies below the label's top ${label.top}") }
    }

    /**
     * The phrase row shows only near the newest message (reading history it folds away, B-CHAT-COL-03), so the scene is
     * there: a tall newest message drawn 120 dp past its resting place (not away from the newest, the chips stay), its
     * lower part under the chips. Was scrolled to the fourth row, which is already away from the newest: the row had
     * folded and the scene had no chips to measure.
     */
    @Test
    fun `a bright row never shows behind the phrase chips above the box`() {
        val bright = shot(dark(Style.Glass), LensPlatform(), bright = true, chips = true, scrollTo = 0, tall = true, nudgeDp = 120)
        val none = shot(dark(Style.Glass), LensPlatform(), bright = false, chips = true, scrollTo = 0, tall = true, nudgeDp = 120)
        val chip = assertNotNull(bright.chip, "near the newest message the phrase row shows: ${bright.list}")
        assertTrue(chip.top < bright.box.top, "the chips lie above the box: $chip box ${bright.box}")
        assertTrue(
            bright.rows.any { it.top < chip.top && it.bottom > chip.bottom },
            "the newest row runs under the chips: ${bright.rows.map { it.top..it.bottom }} chips $chip (${bright.list})",
        )
        // The chip's own face lies at the row's start; its label is at its middle.
        val (mean, max) = diff(bright.image, none.image, chip.left.value + 6f, chip.top.value + 4f, chip.left.value + 60f, chip.bottom.value - 8f)
        println("chips: mean $mean max $max over $chip")
        assertTrue(mean <= WORDS_MEAN && max <= WORDS_MAX, "bright rows show behind the phrase chip: mean $mean, max $max of 255")
    }

    /**
     * Owner 2026-11-14 ("Wtfff": transcript text ran up under the bar and showed inside its lower edge as a blurred band):
     * nothing of the transcript shows under the status bar or the bar, its rim included; it fades in over 12 dp below.
     * Was: "...and still shows in the bar's rim".
     */
    @Test
    fun `a bright row never shows under the status bar or the chat bar, its rim included`() {
        val bright = shot(dark(Style.Glass), LensPlatform(), bright = true)
        val none = shot(dark(Style.Glass), LensPlatform(), bright = false)
        val bar = bright.bar
        assertTrue(bright.rows.any { it.top < bar.top }, "rows run under the bar: ${bright.rows.map { it.top..it.bottom }} bar $bar")
        val (mean, max) = diff(bright.image, none.image, 0f, 0f, W.toFloat(), bar.bottom.value)
        println("bar: mean $mean max $max, bar $bar")
        assertTrue(mean <= WORDS_MEAN && max <= WORDS_MAX, "bright rows show under the chrome: mean $mean, max $max of 255\n" + map(bright.image, none.image, 0f, 0f, bar.right.value, bar.bottom.value + 16f))
    }

    @Test
    fun `a bright row never shows inside the waiting to send line under the bar`() {
        // With the page under the status bar (24 dp inset) and without (a window whose status bar the system draws: no
        // top inset, as on the 2026-11-11 emulator, where the line was crossed by sharp transcript text).
        for (statusDp in listOf(STATUS_DP, 0)) {
            val bright = shot(dark(Style.Glass), LensPlatform(), bright = true, notice = true, statusDp = statusDp)
            val none = shot(dark(Style.Glass), LensPlatform(), bright = false, notice = true, statusDp = statusDp)
            val line = bright.status!!
            assertTrue(line.bottom - line.top > 8.dp, "status $statusDp dp: the line shows: $line")
            assertTrue(bright.rows.any { it.top < line.bottom }, "status $statusDp dp: rows scrolled under the line: ${bright.rows.map { it.top..it.bottom }} line $line")
            val (mean, max) = diff(bright.image, none.image, line.left.value, line.top.value, line.right.value, line.bottom.value)
            println("notice (status $statusDp dp): mean $mean max $max over $line")
            assertTrue(mean <= NOTICE_MEAN && max <= NOTICE_MAX, "status $statusDp dp: bright rows show inside the notice line: mean $mean, max $max of 255")
        }
    }

    @Test
    fun `the transcript's resting place, scroll range and the jump pill's place are the same with and without the lens`() {
        // Liquid Glass fades the transcript under the box; the Frosted look (no lens) does not. Both lay the transcript
        // out alike. (The box itself sits differently in the two looks, as it did before the fade: the lens's slab keeps
        // the bar's 6 dp side margin. Its place under the lens is printed to compare across commits.)
        val lens = shot(dark(Style.Glass), LensPlatform(), bright = true)
        val frosted = shot(dark(Style.Glass, GlassLookId.Frosted), LensPlatform(), bright = true)
        println("lens:    ${lens.list} pill ${lens.pill} box ${lens.box}")
        println("frosted: ${frosted.list} pill ${frosted.pill} box ${frosted.box}")
        assertEquals(frosted.list, lens.list, "the transcript's scroll state")
        assertEquals(frosted.rows, lens.rows, "the rows' places")
        assertEquals(frosted.pill, lens.pill, "the jump pill's place")
    }

    /**
     * Owner 2026-11-14: a clearing hides everything it covers. In every style the bottom block's glass (and the pill's)
     * sees the screen's recording cleared as the page shows it, never the whole recording. Was: "outside liquid Glass
     * the bottom block sees the screen's own recording".
     */
    @Test
    fun `in every style the bottom block sees the recording cleared as the page shows it`() {
        val cases =
            Style.entries.map { "$it" to (dark(it) to (DesktopKitPlatform() as KitPlatform)) } +
                listOf(
                    "Glass, Frosted look" to (dark(Style.Glass, GlassLookId.Frosted) to (LensPlatform() as KitPlatform)),
                    "Glass, lens" to (dark(Style.Glass) to (LensPlatform() as KitPlatform)),
                )
        for ((what, case) in cases) {
            val s = shot(case.first, case.second, bright = true, notice = true)
            assertTrue(s.bottomSawSome && !s.bottomSawSource, "$what: the bottom block saw the whole recording")
        }
    }

    /**
     * Before/after renders (off unless WORDS_GLASS_OUT names a folder; WORDS_GLASS_TAG names the files): the chat on a
     * white-text transcript scrolled under the message box and under the "waiting to send" line, liquid Glass dark (the
     * desktop's lens is a blur only), and the same small scene in every look that must not change.
     */
    @Test
    fun `words on glass renders`() {
        val out = System.getenv("WORDS_GLASS_OUT")?.let(::File) ?: return
        val tag = System.getenv("WORDS_GLASS_TAG") ?: "render"
        out.mkdirs()
        // The looks that must not change, pixel for pixel.
        val still =
            Style.entries.filter { it != Style.Glass }.map { it.name.lowercase() to (dark(it) to (DesktopKitPlatform() as KitPlatform)) } +
                listOf(
                    "glass-nolens" to (dark(Style.Glass) to (DesktopKitPlatform() as KitPlatform)),
                    "glass-frosted" to (dark(Style.Glass, GlassLookId.Frosted) to (LensPlatform() as KitPlatform)),
                    "glass-lens" to (dark(Style.Glass) to (LensPlatform() as KitPlatform)),
                )
        for ((name, case) in still) {
            val s = shot(case.first, case.second, bright = true, notice = true)
            ImageIO.write(s.image.toImage(), "png", File(out, "$tag-small-$name.png"))
        }
        // The phone-sized picture.
        runDesktopComposeUiTest((PW * PD).toInt(), (PH * PD).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(PD)) {
                    KitTheme(
                        KitAppearance(style = Style.Glass, mode = AppearanceMode.Dark, liveBackground = true, liveStyle = "auroraImage"),
                        LensPlatform(),
                        "en",
                        stillFrame = true,
                    ) { PhoneScene() }
                }
            }
            waitForIdle()
            onNodeWithTag("transcript-list").performScrollToIndex(8)
            waitForIdle()
            ImageIO.write(onNodeWithTag("scene").captureToImage().toAwtImage(), "png", File(out, "render-words-chat-$tag.png"))
        }
    }

    /**
     * Before/after renders of the top bar and of the "Needs your decision" label (off unless WORDS_GLASS_OUT names a
     * folder; WORDS_GLASS_TAG is "before" or "after"): the small scene, white rows scrolled under the bar and under a
     * card's label above the message box, liquid Glass dark.
     */
    @Test
    fun `words on glass renders - top bar and the decision label`() {
        val out = System.getenv("WORDS_GLASS_OUT")?.let(::File) ?: return
        val tag = System.getenv("WORDS_GLASS_TAG") ?: "render"
        out.mkdirs()
        val top = shot(dark(Style.Glass), LensPlatform(), bright = true)
        ImageIO.write(top.image.toImage(), "png", File(out, "render-words-top-chat-$tag.png"))
        val card = shot(dark(Style.Glass), LensPlatform(), bright = true, card = true)
        ImageIO.write(card.image.toImage(), "png", File(out, "render-words-needs-decision-$tag.png"))
    }

    private fun PixelMap.toImage(): java.awt.image.BufferedImage {
        val img = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) for (x in 0 until width) {
            val c = this[x, y]
            val argb =
                ((c.alpha * 255).roundToInt() shl 24) or ((c.red * 255).roundToInt() shl 16) or
                    ((c.green * 255).roundToInt() shl 8) or (c.blue * 255).roundToInt()
            img.setRGB(x, y, argb)
        }
        return img
    }

    private val paragraphs =
        (0 until 30).map { i ->
            "Line ${30 - i}: the build finished and every check passed; the next step reads the log, compares the timings " +
                "with yesterday's and writes what changed into the notes."
        }

    @Composable
    private fun PhoneScene() {
        CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = 28.dp, bottom = 20.dp)) {
            Box(Modifier.size(PW.dp, PH.dp).testTag("scene")) {
                val source: KitFrostState = rememberKitFrostState()
                val state = rememberTranscriptListState()
                KitScreen(
                    page = true,
                    overlayChrome = true,
                    frost = source,
                    topBar = {
                        KitTopBar("create a file hello.txt with one line", exit = KitTopBarExit.Back, exitLabel = "Back", onExit = {}, backBesideTitle = true)
                    },
                    status =
                        KitStatus(
                            KitStatusKind.Info,
                            "1 message is waiting to send.",
                            id = "chat-pending-sends",
                            tone = StatusTone.Attention,
                            action = KitLineAction("Review", {}),
                        ),
                    bottom = {
                        KitReportBottomSlab(kitComposerSlab())
                        KitComposerBox(
                            running = false,
                            field = { KitText("Ask OpenCode…", role = TypeRole.Body, tone = TextTone.Tertiary, kind = KitTextKind.Frame) },
                            leading = { KitComposerAdd("Tools", onClick = {}, open = false) },
                            trailing = { KitComposerSend("Send", onClick = {}) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    edges = KitScreenEdges.Bleed,
                ) {
                    Box(Modifier.fillMaxSize().kitFrostSource(source)) {
                        KitTranscriptList(state, paragraphs.first(), "Jump to latest", KitMotionLevel.Off) {
                            items(paragraphs, key = { it }) {
                                KitText(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), role = TypeRole.Body, tone = TextTone.Primary)
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val W = 240
        const val H = 480
        const val D = 2f
        const val STATUS_DP = 24
        const val NAV_DP = 16
        const val BAR_DP = 56
        const val ROW_DP = 40
        const val LABEL_DP = 64

        /** A code block's height in the outlined scene. */
        const val CODE_DP = 160

        /** The status edge's dissolve reaches this far under the bar's top (24 dp inset plus its blur's reach). */
        const val STATUS_EDGE_DP = 10f
        const val ROWS = 60
        const val SCROLLED_TO = 20

        /** Phone-sized pictures. */
        const val PW = 412
        const val PH = 880
        const val PD = 2.5f

        /** Behind the words: at most this much brightness of 255 from the rows, on average and at any one pixel. */
        const val WORDS_MEAN = 2f
        const val WORDS_MAX = 8f

        /** Inside the notice line: nothing of the rows. */
        const val NOTICE_MEAN = 1f
        const val NOTICE_MAX = 4f
    }
}
