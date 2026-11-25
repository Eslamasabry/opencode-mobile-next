@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
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
import io.github.eslamasabry.oc.ui.kit.chip.KitPhraseChips
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidJoined
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
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptListState
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertTrue

/**
 * Shared harness for the chat-words-on-glass pixel suites ([ChatWordsGlassTest] and [ChatWordsGlassChromeTest]),
 * split across two balanced test classes so Gradle forks execute the CPU lens renders in parallel.
 */
abstract class ChatWordsGlassHarness {
    /**
     * Desktop with the lens, as Android 13+ has it: a liquid pane draws what it reads blurred by its recipe's blur,
     * under its tint. No bend and no rim light (the desktop cannot show those).
     */
    protected class LensPlatform : KitPlatform by DesktopKitPlatform() {
        override val liquidGlass: Boolean = true

        override fun liquidPane(
            spec: KitLiquidPaneSpec,
            backdrop: KitLiquidBackdrop,
            shape: CornerBasedShape,
            transform: () -> Float,
        ): Modifier {
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
            val getter = KitLiquidBackdrop::class.java.methods.first {
                it.name.startsWith("getDraw") && it.parameterCount == 0
            }
            return getter.invoke(backdrop) as (DrawScope, LayoutCoordinates) -> Unit
        }
    }

    protected class DpArea(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    protected class FadeProbe(
        val x: Float,
        val from: Float,
        val to: Float,
        val what: String,
    )

    protected class RowOptions(
        val outlined: Boolean = false,
        val scrollTo: Int = SCROLLED_TO,
        val tall: Boolean = false,
        val nudgeDp: Int = 0,
    )

    protected class SceneOptions(
        val bright: Boolean,
        val notice: Boolean = false,
        val statusDp: Int = STATUS_DP,
        val card: Boolean = false,
        val chips: Boolean = false,
        val rows: RowOptions = RowOptions(),
    )

    protected class ShotChrome(
        val box: DpRect,
        val placeholder: DpRect,
        val field: DpRect,
        val status: DpRect?,
        val pill: DpRect?,
        val bar: DpRect,
    )

    protected class ShotExtras(
        val label: DpRect?,
        val chip: DpRect?,
        val block: DpRect?,
        val pillFace: DpRect?,
        val chipRows: Int,
        val joined: Int,
    )

    protected class Shot(
        val image: PixelMap,
        val chrome: ShotChrome,
        val extras: ShotExtras,
        val rows: List<DpRect>,
        val list: String,
        val recording: Pair<Boolean, Boolean>,
    ) {
        val box: DpRect get() = chrome.box
        val placeholder: DpRect get() = chrome.placeholder
        val field: DpRect get() = chrome.field
        val status: DpRect? get() = chrome.status
        val pill: DpRect? get() = chrome.pill
        val bar: DpRect get() = chrome.bar
        val label: DpRect? get() = extras.label
        val chip: DpRect? get() = extras.chip
        val block: DpRect? get() = extras.block
        val pillFace: DpRect? get() = extras.pillFace
        val chipRows: Int get() = extras.chipRows
        val joined: Int get() = extras.joined
        val bottomSawSource: Boolean get() = recording.first
        val bottomSawSome: Boolean get() = recording.second
    }

    protected fun dark(style: Style, look: GlassLookId = GlassLookId.Glossy) =
        KitAppearance(style = style, mode = AppearanceMode.Dark, glassLook = look)

    @Composable
    private fun SceneBottom(opts: SceneOptions, seen: Array<Any?>) {
        seen[0] = LocalKitLiquidChrome.current
        KitReportBottomSlab(kitComposerSlab())
        // A request card's label stands above the box inside the same block (ChatScreen): a bar of
        // the card's size with nothing of its own behind it.
        Column(Modifier.fillMaxWidth()) {
            if (opts.chips) {
                KitPhraseChips(
                    listOf("say hi"), "Phrases", { "Insert $it" }, { "Options for $it" }, "Hide", {}, {},
                    Modifier.testTag("chat-phrase-chips"),
                )
            }
            if (opts.card) Box(Modifier.fillMaxWidth().height(LABEL_DP.dp).testTag("decision-label"))
            KitComposerBox(
                running = false,
                field = {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("field"),
                        contentAlignment = Alignment.CenterStart,
                    ) {
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
    }

    @Composable
    private fun SceneTranscript(opts: SceneOptions, source: KitFrostState, state: TranscriptListState) {
        Box(Modifier.fillMaxSize().kitFrostSource(source)) {
            KitTranscriptList(state, "row-0", "Jump to latest", KitMotionLevel.Off) {
                items((0 until ROWS).map { "row-$it" }, key = { it }) { key ->
                    val fillMod =
                        when {
                            !opts.bright -> Modifier
                            // A code block: its outline only, the words inside it left out.
                            opts.rows.outlined ->
                                Modifier.padding(horizontal = 12.dp)
                                    .border(1.dp, Color.White, RoundedCornerShape(12.dp))
                            else -> Modifier.background(Color.White)
                        }
                    Box(
                        Modifier.fillMaxWidth()
                            .height(if (opts.rows.outlined || opts.rows.tall) CODE_DP.dp else ROW_DP.dp)
                            .then(fillMod)
                            .testTag(key)
                    )
                }
            }
        }
    }

    /**
     * A chat frame: a bar, optionally the "waiting to send" line under it, the transcript (bright white rows, or rows
     * that draw nothing) scrolled well away from the newest so it runs under the bar and the message box, and the box.
     */
    @Composable
    private fun Scene(opts: SceneOptions, seen: Array<Any?>, lazy: Array<LazyListState?>) {
        val insets = WindowInsets(top = opts.statusDp.dp, bottom = NAV_DP.dp)
        CompositionLocalProvider(LocalKitScreenInsets provides insets) {
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
                        if (!opts.notice) null
                        else
                            KitStatus(
                                KitStatusKind.Info,
                                "1 message is waiting to send.",
                                id = "chat-pending-sends",
                                tone = StatusTone.Attention,
                                action = KitLineAction("Review", {}),
                            ),
                    bottom = { SceneBottom(opts, seen) },
                    edges = KitScreenEdges.Bleed,
                ) {
                    SceneTranscript(opts, source, state)
                }
            }
        }
    }

    private fun ComposeUiTest.captureShot(
        opts: SceneOptions,
        seen: Array<Any?>,
        l: LazyListState,
    ): Shot {
        val visible = l.layoutInfo.visibleItemsInfo.map { it.key }
        val info = l.layoutInfo
        val list =
            "first=${l.firstVisibleItemIndex}/${l.firstVisibleItemScrollOffset} visible=$visible " +
                "viewport=${info.viewportStartOffset}..${info.viewportEndOffset} " +
                "pad=${info.beforeContentPadding}/${info.afterContentPadding} " +
                "back=${l.canScrollBackward} fwd=${l.canScrollForward}"
        fun bounds(tag: String) = onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
        fun boundsOrNull(tag: String) = runCatching { bounds(tag) }.getOrNull()
        val chrome =
            ShotChrome(
                box = bounds("kit-composer-box"),
                placeholder = bounds("placeholder"),
                field = bounds("field"),
                status = if (opts.notice) bounds("kit-screen-status") else null,
                pill = boundsOrNull("jump-to-latest"),
                bar = bounds("bar"),
            )
        val joinedMatcher = SemanticsMatcher.keyIsDefined(KitLiquidJoined)
        val extras =
            ShotExtras(
                label = if (opts.card) bounds("decision-label") else null,
                chip = if (opts.chips) boundsOrNull("chat-phrase-chips") else null,
                block = bounds("kit-screen-bottom"),
                pillFace = boundsOrNull("kit-jump-pill-veiled"),
                chipRows = onAllNodesWithTag("chat-phrase-chips", useUnmergedTree = true).fetchSemanticsNodes().size,
                joined = onAllNodes(joinedMatcher, useUnmergedTree = true).fetchSemanticsNodes().size,
            )
        return Shot(
            image = onNodeWithTag("scene").captureToImage().toPixelMap(),
            chrome = chrome,
            extras = extras,
            rows = visible.map { bounds(it.toString()) },
            list = list,
            recording = (seen[0] === seen[1]) to (seen[0] != null),
        )
    }

    protected fun shot(
        appearance: KitAppearance,
        platform: KitPlatform,
        opts: SceneOptions,
    ): Shot {
        lateinit var shot: Shot
        // The joined box and pill run the join shader on the CPU every frame; a slow runner took over a minute
        // (runDesktopComposeUiTest keeps that minute whatever it is told, PaneRimScenes.SCENE_TIMEOUT).
        runSkikoComposeUiTest(androidx.compose.ui.geometry.Size(W * D, H * D), testTimeout = SCENE_TIMEOUT) {
            val seen = arrayOfNulls<Any?>(2)
            val lazy = arrayOfNulls<LazyListState>(1)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(appearance = appearance, platform = platform, localeTag = "en", stillFrame = true) {
                        Scene(opts, seen, lazy)
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("transcript-list").performScrollToIndex(opts.rows.scrollTo)
            waitForIdle()
            // Reading history drags the list so it stops following the newest message; the finger moves 120 dp
            // toward older messages and rests before lifting so no fling carries the list on.
            if (opts.chips && opts.rows.scrollTo > 0) {
                onNodeWithTag("transcript-list").performTouchInput {
                    down(center)
                    repeat(12) { moveBy(Offset(0f, 10f * D)) }
                    advanceEventTime(200)
                    up()
                }
            }
            waitForIdle()
            if (opts.rows.nudgeDp > 0) {
                runOnIdle { lazy[0]!!.requestScrollToItem(opts.rows.scrollTo, (opts.rows.nudgeDp * D).roundToInt()) }
                waitForIdle()
            }
            // The phrase row folding, the pill rising out of the box, the page's edges easing to their new place.
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            shot = captureShot(opts, seen, lazy[0]!!)
        }
        return shot
    }

    private fun luma(c: Color): Float = (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue) * 255f

    /** A coarse picture of where [a] differs from [b] (8 dp cells, digit = difference / 26), for a failure message. */
    protected fun map(a: PixelMap, b: PixelMap, area: DpArea): String =
        (area.top.toInt() until area.bottom.toInt() step 8).joinToString("\n") { y ->
            "%4d ".format(y) + (area.left.toInt() until area.right.toInt() step 8).joinToString("") { x ->
                val cell = DpArea(x.toFloat(), y.toFloat(), x + 8f, y + 8f)
                diff(a, b, cell).second.let { (it / 26f).toInt().coerceAtMost(9).toString() }
            }
        }

    /** The mean and largest brightness difference (of 255) between [a] and [b] over [area] dp. */
    protected fun diff(a: PixelMap, b: PixelMap, area: DpArea): Pair<Float, Float> {
        var sum = 0f
        var max = 0f
        var n = 0
        val ys = (area.top * D).roundToInt() until (area.bottom * D).roundToInt()
        val xs = (area.left * D).roundToInt() until (area.right * D).roundToInt()
        for (y in ys) for (x in xs) {
            if (x !in 0 until a.width || y !in 0 until a.height) continue
            val d = abs(luma(a[x, y]) - luma(b[x, y]))
            sum += d
            max = maxOf(max, d)
            n++
        }
        return (if (n == 0) 0f else sum / n) to max
    }

    /**
     * Down column [probe.x] dp from [probe.from] to [probe.to] dp the bright scene goes from whole to nothing over a
     * soft fade: whole at the start, nothing by [probe.to], at least four levels between, no hard step.
     */
    protected fun assertFadesOut(a: PixelMap, b: PixelMap, probe: FadeProbe) {
        val px = (probe.x * D).roundToInt()
        val ys = (probe.from * D).roundToInt()..(probe.to * D).roundToInt()
        val ramp = ys.map { y -> abs(luma(a[px, y]) - luma(b[px, y])) }
        val full = ramp.first()
        val ints = ramp.map { it.toInt() }
        println("${probe.what}: fade $ints")
        assertTrue(full >= 150f, "${probe.what}: the rows are whole ${probe.from} dp: $full")
        assertTrue(ramp.last() <= WORDS_MAX, "${probe.what}: nothing of the rows at ${probe.to} dp: ${ramp.last()}")
        assertTrue(
            ramp.zipWithNext().all { (p, q) -> q <= p + 2f },
            "${probe.what}: the rows go without coming back: $ints",
        )
        val levels = ramp.filter { it > WORDS_MAX && it < full * .9f }.map { (it / 8f).toInt() }.distinct()
        assertTrue(levels.size >= 4, "${probe.what}: a soft fade, not a cut: $ints")
        assertTrue(
            ramp.zipWithNext().maxOf { (p, q) -> abs(q - p) } <= full / 6f,
            "${probe.what}: a hard step in the fade: $ints",
        )
    }

    protected fun PixelMap.toImage(): java.awt.image.BufferedImage {
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
            "Line ${30 - i}: the build finished and every check passed; the next step reads the log, " +
                "compares the timings with yesterday's and writes what changed into the notes."
        }

    @Composable
    private fun PhoneBottom() {
        KitReportBottomSlab(kitComposerSlab())
        KitComposerBox(
            running = false,
            field = {
                KitText(
                    "Ask OpenCode…",
                    role = TypeRole.Body,
                    tone = TextTone.Tertiary,
                    kind = KitTextKind.Frame,
                )
            },
            leading = { KitComposerAdd("Tools", onClick = {}, open = false) },
            trailing = { KitComposerSend("Send", onClick = {}) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Composable
    protected fun PhoneScene() {
        val insets = WindowInsets(top = 28.dp, bottom = 20.dp)
        CompositionLocalProvider(LocalKitScreenInsets provides insets) {
            Box(Modifier.size(PW.dp, PH.dp).testTag("scene")) {
                val source: KitFrostState = rememberKitFrostState()
                val state = rememberTranscriptListState()
                KitScreen(
                    page = true,
                    overlayChrome = true,
                    frost = source,
                    topBar = {
                        KitTopBar(
                            "create a file hello.txt with one line",
                            exit = KitTopBarExit.Back,
                            exitLabel = "Back",
                            onExit = {},
                            backBesideTitle = true,
                        )
                    },
                    status =
                        KitStatus(
                            KitStatusKind.Info,
                            "1 message is waiting to send.",
                            id = "chat-pending-sends",
                            tone = StatusTone.Attention,
                            action = KitLineAction("Review", {}),
                        ),
                    bottom = { PhoneBottom() },
                    edges = KitScreenEdges.Bleed,
                ) {
                    Box(Modifier.fillMaxSize().kitFrostSource(source)) {
                        KitTranscriptList(state, paragraphs.first(), "Jump to latest", KitMotionLevel.Off) {
                            items(paragraphs, key = { it }) {
                                KitText(
                                    it,
                                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    role = TypeRole.Body,
                                    tone = TextTone.Primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    protected companion object {
        /** How long one scene may take to draw and settle (see [shot]). */
        val SCENE_TIMEOUT: kotlin.time.Duration = kotlin.time.Duration.parse("5m")

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
