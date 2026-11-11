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
        val rows: List<DpRect>,
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
    private fun Scene(bright: Boolean, notice: Boolean, seen: Array<Any?>, lazy: Array<LazyListState?>, statusDp: Int) {
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

    private fun shot(appearance: KitAppearance, platform: KitPlatform, bright: Boolean, notice: Boolean = false, statusDp: Int = STATUS_DP): Shot {
        lateinit var shot: Shot
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            val seen = arrayOfNulls<Any?>(2)
            val lazy = arrayOfNulls<LazyListState>(1)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(appearance = appearance, platform = platform, localeTag = "en", stillFrame = true) {
                        Scene(bright, notice, seen, lazy, statusDp)
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("transcript-list").performScrollToIndex(SCROLLED_TO)
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
                    rows = visible.map { bounds(it.toString()) },
                    list = list,
                    bottomSawSource = seen[0] === seen[1],
                )
        }
        return shot
    }

    private fun luma(c: Color): Float = (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue) * 255f

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
