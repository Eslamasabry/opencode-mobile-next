@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.background
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
import androidx.compose.ui.test.performScrollToIndex
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
        /** The bottom block (padding included). */
        val block: DpRect,
        val list: String,
        /** Whether the bottom block saw the screen's own recording (the frame handed it nothing else). */
        val bottomSawSource: Boolean,
    )

    private fun dark(style: Style, look: GlassLookId = GlassLookId.Glossy) =
        KitAppearance(style = style, mode = AppearanceMode.Dark, glassLook = look)

    /**
     * A chat frame: a bar, optionally the "waiting to send" line under it, the transcript ([bright] white rows, or rows
     * that draw nothing) scrolled well away from the newest so it runs under the bar and the message box, and the box.
     */
    @Composable
    private fun Scene(bright: Boolean, notice: Boolean, seen: Array<Any?>, lazy: Array<LazyListState?>, statusDp: Int, card: Boolean = false, chips: Boolean = false) {
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
                                        .height(ROW_DP.dp)
                                        .then(if (bright) Modifier.background(Color.White) else Modifier)
                                        .testTag(key)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun shot(appearance: KitAppearance, platform: KitPlatform, bright: Boolean, notice: Boolean = false, statusDp: Int = STATUS_DP, card: Boolean = false, chips: Boolean = false, scrollTo: Int = SCROLLED_TO): Shot {
        lateinit var shot: Shot
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            val seen = arrayOfNulls<Any?>(2)
            val lazy = arrayOfNulls<LazyListState>(1)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(appearance = appearance, platform = platform, localeTag = "en", stillFrame = true) {
                        Scene(bright, notice, seen, lazy, statusDp, card, chips)
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("transcript-list").performScrollToIndex(scrollTo)
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
                    chip = if (chips) bounds("chat-phrase-chips") else null,
                    rows = visible.map { bounds(it.toString()) },
                    block = bounds("kit-screen-bottom"),
                    list = list,
                    bottomSawSource = seen[0] === seen[1],
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

    @Test
    fun `a bright row under the message box adds nothing where its words sit and still shows at its top edge`() {
        val bright = shot(dark(Style.Glass), LensPlatform(), bright = true)
        val none = shot(dark(Style.Glass), LensPlatform(), bright = false)
        val box = bright.box
        // The rows really run under the box (the transcript is scrolled away, so nothing rests above it).
        assertTrue(bright.rows.any { it.bottom > box.top + 40.dp }, "rows under the box: ${bright.rows.map { it.top..it.bottom }} box $box")
        // Where the placeholder and the typed words sit: the words' own line, across the whole field.
        val (wordsMean, wordsMax) =
            diff(bright.image, none.image, bright.field.left.value, bright.placeholder.top.value, bright.field.right.value, bright.placeholder.bottom.value)
        println("words: mean $wordsMean max $wordsMax over ${bright.field.left}..${bright.field.right} x ${bright.placeholder.top}..${bright.placeholder.bottom}, box $box")
        assertTrue(wordsMean <= WORDS_MEAN && wordsMax <= WORDS_MAX, "bright rows show behind the placeholder: mean $wordsMean, max $wordsMax of 255")
        // At the box's top edge, beside the jump pill: the glass still bends real content.
        val left = box.left.value + 12f
        val right = box.left.value + (box.right - box.left).value / 4f
        val (edgeMean, _) = diff(bright.image, none.image, left, box.top.value + 1f, right, box.top.value + 5f)
        println("top edge: mean $edgeMean")
        assertTrue(edgeMean >= EDGE_MEAN, "the box's top edge shows nothing of the rows behind it: mean $edgeMean of 255")
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

    @Test
    fun `a bright row never shows behind the phrase chips above the box`() {
        val bright = shot(dark(Style.Glass), LensPlatform(), bright = true, chips = true)
        val none = shot(dark(Style.Glass), LensPlatform(), bright = false, chips = true)
        val chip = bright.chip!!
        assertTrue(chip.top < bright.box.top, "the chips lie above the box: $chip box ${bright.box}")
        // The chip's own face lies at the row's start; its label is at its middle.
        val (mean, max) = diff(bright.image, none.image, chip.left.value + 6f, chip.top.value + 4f, chip.left.value + 60f, chip.bottom.value - 8f)
        println("chips: mean $mean max $max over $chip")
        assertTrue(mean <= WORDS_MEAN && max <= WORDS_MAX, "bright rows show behind the phrase chip: mean $mean, max $max of 255")
    }

    /** The jump pill's label band: the middle of its face, where "Jump to latest" is written. */
    private fun labelBand(pill: DpRect): List<Float> {
        val cx = (pill.left.value + pill.right.value) / 2f
        val cy = (pill.top.value + pill.bottom.value) / 2f
        return listOf(cx - 15f, cy - 6f, cx + 15f, cy + 6f)
    }

    @Test
    fun `a bright row never shows behind the jump pill's label with phrase chips above the box`() {
        val bright = shot(dark(Style.Glass), LensPlatform(), bright = true, chips = true)
        val none = shot(dark(Style.Glass), LensPlatform(), bright = false, chips = true)
        val pill = bright.pill
        assertTrue(pill != null, "the pill shows (scrolled away from the newest)")
        assertTrue(bright.rows.any { it.top < pill!!.bottom && it.bottom > pill.top }, "rows run behind the pill: ${bright.rows.map { it.top..it.bottom }} pill $pill")
        val b = labelBand(pill!!)
        val (mean, max) = diff(bright.image, none.image, b[0], b[1], b[2], b[3])
        println("pill label (chips): mean $mean max $max over $b, pill $pill")
        assertTrue(mean <= WORDS_MEAN && max <= WORDS_MAX, "bright rows show behind the pill's label: mean $mean, max $max of 255")
    }

    @Test
    fun `with chips the body is cleared where the pill stands and whole above the block when the pill is hidden`() {
        // A strip at the left, beside the pill, 20 to 40 dp above the block's top: under the pill's height (48 dp).
        fun strip(s: Shot, n: Shot) = diff(s.image, n.image, 0f, s.block.top.value - 40f, 30f, s.block.top.value - 20f)
        val shown = shot(dark(Style.Glass), LensPlatform(), bright = true, chips = true)
        val shownNone = shot(dark(Style.Glass), LensPlatform(), bright = false, chips = true)
        val hidden = shot(dark(Style.Glass), LensPlatform(), bright = true, chips = true, scrollTo = 1)
        val hiddenNone = shot(dark(Style.Glass), LensPlatform(), bright = false, chips = true, scrollTo = 1)
        assertTrue(hidden.pill == null, "the pill is hidden near the newest: ${hidden.pill}")
        val (onMean, _) = strip(shown, shownNone)
        val (offMean, _) = strip(hidden, hiddenNone)
        println("body strip beside pill: shown mean $onMean, hidden mean $offMean")
        assertTrue(onMean <= WORDS_MEAN, "the body is not cleared where the pill stands: mean $onMean of 255")
        assertTrue(offMean >= EDGE_MEAN, "with the pill hidden the body's fade moved: mean $offMean of 255")
    }

    @Test
    fun `without chips the pill joins the box and the body is whole beside it`() {
        val bright = shot(dark(Style.Glass), LensPlatform(), bright = true)
        val none = shot(dark(Style.Glass), LensPlatform(), bright = false)
        assertTrue(bright.pill != null, "the pill shows")
        val (mean, _) = diff(bright.image, none.image, 0f, bright.block.top.value - 40f, 30f, bright.block.top.value - 20f)
        println("body strip beside pill (no chips): mean $mean")
        assertTrue(mean >= EDGE_MEAN, "not stacked: the body's fade moved: mean $mean of 255")
    }

    @Test
    fun `a bright row never shows behind the chat bar's title, and still shows in the bar's rim`() {
        val bright = shot(dark(Style.Glass), LensPlatform(), bright = true)
        val none = shot(dark(Style.Glass), LensPlatform(), bright = false)
        val bar = bright.bar
        // The bar's pane lies 4 dp in from the bar's top and bottom; its rim is 22 dp (40 % of its 48 dp is 19 dp).
        val paneBottom = bar.bottom.value - 4f
        val rim = minOf(22f, 0.4f * (bar.bottom - bar.top).value)
        val clearTo = paneBottom - rim - 1f
        assertTrue(bright.rows.any { it.top < bar.top }, "rows run under the bar: ${bright.rows.map { it.top..it.bottom }} bar $bar")
        // The bar's title line, below the status edge (which keeps its blur of the page: chat edge A).
        val (mean, max) = diff(bright.image, none.image, bar.left.value + 30f, bar.top.value + STATUS_EDGE_DP, bar.right.value - 30f, clearTo)
        println("bar words: mean $mean max $max over 0..$clearTo, bar $bar")
        assertTrue(mean <= WORDS_MEAN && max <= WORDS_MAX, "bright rows show behind the bar's title: mean $mean, max $max of 255\n" + map(bright.image, none.image, 0f, 0f, bar.right.value, bar.bottom.value))
        // In the rim above the bar's bottom edge the glass still bends real rows.
        val (edge, _) = diff(bright.image, none.image, bar.left.value + 40f, paneBottom - 8f, bar.right.value - 40f, paneBottom - 3f)
        println("bar rim: mean $edge")
        assertTrue(edge >= EDGE_MEAN, "the bar's rim shows nothing of the rows: mean $edge of 255")
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

    @Test
    fun `outside liquid Glass the bottom block sees the screen's own recording`() {
        val cases =
            Style.entries.filter { it != Style.Glass }.map { "$it" to (dark(it) to (DesktopKitPlatform() as KitPlatform)) } +
                listOf(
                    "Glass, no lens on the platform" to (dark(Style.Glass) to (DesktopKitPlatform() as KitPlatform)),
                    "Glass, Frosted look" to (dark(Style.Glass, GlassLookId.Frosted) to (LensPlatform() as KitPlatform)),
                )
        for ((what, case) in cases) {
            val s = shot(case.first, case.second, bright = true, notice = true)
            assertTrue(s.bottomSawSource, "$what: the bottom block saw something other than the screen's recording")
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

        /** At the box's top edge the rows still show through the glass: at least this much on average. */
        const val EDGE_MEAN = 20f

        /** Inside the notice line: nothing of the rows. */
        const val NOTICE_MEAN = 1f
        const val NOTICE_MAX = 4f
    }
}
