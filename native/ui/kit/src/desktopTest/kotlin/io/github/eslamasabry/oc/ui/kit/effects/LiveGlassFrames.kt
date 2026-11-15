@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.GlassKnob
import io.github.eslamasabry.oc.ui.design.effects.GlassPaneKind
import io.github.eslamasabry.oc.ui.design.effects.GlassTuning
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
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
import kotlin.math.abs

/**
 * Live glass proof (reports/live-glass.md): the bar's ⋯ menu, joined to the bar, opened, closed and turned round mid-way,
 * frame by frame on the test clock, over syntax-coloured code and a bright picture under the bar's end, in dark Glass.
 * [Variant.Today] draws today's join; [Variant.Live] switches the Glass lab's "Lens through motion" on (the same screen,
 * the same data). Each frame is measured from its own pixels, so both variants are judged by one ruler:
 * - the bar away from the menu (its light and its sharpness): nothing moves there, so any change is the body's blur or
 *   resolution changing;
 * - the bar's end over the bright picture (the knee);
 * - the menu's middle (its rows) and the strip just inside its free side (the lens rim);
 * - where the glass is: its lowest and its leftmost changed pixel against the page with the menu closed.
 * The desktop draws the joined body through the real join shader; a lone bar (before the join and after it) is the
 * desktop's stand-in, not the library's lens, so the join and unjoin frames are not judged here.
 */
internal object LiveGlassFrames {
    enum class Variant(val key: String) {
        Today("today"),
        Live("live"),
    }

    /** One recorded frame: its time, whether the two panes were joined, its measures and (when kept) its picture. */
    class Frame(
        val index: Int,
        val ms: Long,
        val open: Boolean,
        val joined: Boolean,
        val barLight: Double,
        val barSharp: Double,
        val barEnd: Double,
        val menuMiddle: Double,
        val menuRim: Double,
        val columnSharp: Double,
        val glassBottomDp: Double,
        val glassLeftDp: Double,
        val costMs: Double,
        val image: ImageBitmap?,
        /** The glass is on its way (neither closed nor at rest), and its side is between the column and rest. */
        val midway: Boolean = false,
        val sideMidway: Boolean = false,
    )

    /** A scene: when the menu opens (true) or closes (false), ms from the first frame, how long it runs, and scrolling. */
    class Scene(val name: String, val events: List<Pair<Long, Boolean>>, val lengthMs: Long, val startsOpen: Boolean = false, val scrollDpPerFrame: Float = 0f)

    val scenes: List<Scene> =
        listOf(
            Scene("open", listOf(0L to true), 640),
            Scene("close", listOf(0L to false), 480, startsOpen = true),
            // Three turns: mid-drop (closed 120 ms after opening, reopened 100 ms later), mid-fill and mid-empty, and
            // mid-way up the column after a close from fully open.
            Scene("reverse", listOf(0L to true, 120L to false, 220L to true, 460L to false, 540L to true, 1100L to false, 1240L to true), 1800),
            // The same turns while the code scrolls under the glass.
            Scene("scroll", listOf(0L to true, 120L to false, 220L to true, 460L to false, 540L to true, 1100L to false, 1240L to true), 1800, scrollDpPerFrame = 3f),
        )

    const val WIDTH_DP = 412
    const val HEIGHT_DP = 440
    const val FRAME_MS = 16L

    private val items =
        listOf(KitMenuItem("Find", onSelect = {}), KitMenuItem("Rename conversation", onSelect = {}), KitMenuItem("Fork conversation", onSelect = {}), KitMenuItem("Copy transcript", onSelect = {}))

    fun appearance(variant: Variant): KitAppearance =
        KitAppearance(
            mode = AppearanceMode.Dark,
            style = Style.Glass,
            glassTuning =
                if (variant == Variant.Today) null
                else GlassTuning.Shipped.with(GlassPaneKind.Menu, true, GlassKnob.LiveMotion, 1f).with(GlassPaneKind.Menu, false, GlassKnob.LiveMotion, 1f).copy(inApp = true),
        )

    /** Desktop with the join shader and Android's reduced backdrop resolution (half), so the resolution step shows. */
    fun platform(): FakeKitPlatform =
        FakeKitPlatform(
            frost = true,
            liquidGlass = true,
            liquidResolution = .5f,
            liveShaders = LiveShaderCompiler(DesktopLiveShader::compile),
            joinEffect = { p, b -> DesktopKitPlatform().liquidJoinEffect(p, b) },
        )

    @Composable
    fun Page(open: Boolean, list: LazyListState, onClose: () -> Unit) {
        val source = rememberKitFrostState()
        KitScreen(
            page = true,
            overlayChrome = true,
            frost = source,
            topBar = {
                Box(Modifier.fillMaxWidth().height(56.dp)) {
                    KitText("Parser fix", Modifier.align(Alignment.CenterStart).padding(start = 20.dp))
                    KitMenu(expanded = open, items = items, onDismissRequest = onClose, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) {
                        KitText("More", Modifier.padding(12.dp))
                    }
                }
            },
            edges = KitScreenEdges.Bleed,
        ) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().kitFrostSource(source)) {
                item {
                    // A bright picture under the bar's end and the menu's top, the code under the rest of the menu: the
                    // photo the knee and the words must hold, and detail for the lens to bend and a resolution to lose.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Box(
                            Modifier.size(200.dp, 110.dp)
                                .background(Brush.radialGradient(listOf(Color.White, Color(0xFFFFF2B0), Color(0xFFFFB13B), Color(0xFFE0603A), Color(0xFF3A6EA5))))
                        )
                    }
                }
                items(60) { i -> CodeLine(i) }
            }
        }
    }

    private val keyword = SpanStyle(color = Color(0xFFC792EA))
    private val string = SpanStyle(color = Color(0xFFC3E88D))
    private val number = SpanStyle(color = Color(0xFFF78C6C))
    private val name = SpanStyle(color = Color(0xFF82AAFF))
    private val plain = SpanStyle(color = Color(0xFFD6DEEB))

    private val lines: List<AnnotatedString> =
        listOf(
            code(keyword to "fun ", name to "parse", plain to "(line: String): Block? {"),
            code(keyword to "    val ", plain to "fence = ", string to "\"```\""),
            code(keyword to "    if ", plain to "(line.isBlank()) ", keyword to "return ", plain to "Block.Empty"),
            code(keyword to "    for ", plain to "(i ", keyword to "in ", number to "0", plain to " until line.length) {"),
            code(plain to "        ", name to "push", plain to "(line[i], depth + ", number to "1", plain to ")"),
            code(plain to "    }"),
            code(keyword to "    return ", name to "Block", plain to "(fence, ", string to "\"kotlin\"", plain to ", ", number to "42", plain to ")"),
            code(plain to "}"),
        )

    private fun code(vararg parts: Pair<SpanStyle, String>): AnnotatedString = buildAnnotatedString { parts.forEach { (s, t) -> withStyle(s) { append(t) } } }

    @Composable
    private fun CodeLine(i: Int) {
        BasicText(lines[i % lines.size], Modifier.padding(horizontal = 16.dp, vertical = 3.dp), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp))
    }

    /** The regions measured, in dp of the window (found once per run from the menu at rest). */
    private class Regions(val menu: Rect)

    /**
     * Runs [scene] in [variant] at [density] px per dp and returns its frames; [keepImages] keeps each frame's picture.
     * Before the first frame the menu is opened and closed once (the shaders compile, the menu's box at rest is found).
     */
    fun run(variant: Variant, scene: Scene, density: Float, keepImages: Boolean): List<Frame> {
        val out = mutableListOf<Frame>()
        val width = (WIDTH_DP * density).toInt()
        val height = (HEIGHT_DP * density).toInt()
        runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), testTimeout = kotlin.time.Duration.parse("20m")) {
            mainClock.autoAdvance = false
            val open: MutableState<Boolean> = mutableStateOf(false)
            var list: LazyListState? = null
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    KitTheme(appearance(variant), platform(), "en") {
                        val state = rememberLazyListState()
                        list = state
                        Page(open.value, state) { open.value = false }
                    }
                }
            }
            advance(this, 800)
            val closed = capture(this)
            open.value = true
            advance(this, 1200)
            val menu = menuBox(this, density)
            open.value = scene.startsOpen
            advance(this, 1200)
            val regions = Regions(menu)
            var t = 0L
            var index = 0
            val events = scene.events.toMutableList()
            while (t <= scene.lengthMs) {
                while (events.isNotEmpty() && events.first().first <= t) open.value = events.removeAt(0).second
                val started = System.nanoTime()
                if (index > 0) {
                    val scroll = scene.scrollDpPerFrame * density
                    if (scroll != 0f) runOnIdle { list?.dispatchRawDelta(scroll) }
                    mainClock.advanceTimeBy(FRAME_MS)
                }
                waitForIdle()
                val image = capture(this)
                val cost = (System.nanoTime() - started) / 1e6
                out += measure(index, t, open.value, image, closed, regions, density, cost, keepImages)
                index++
                t += FRAME_MS
            }
        }
        return out
    }

    private fun advance(test: ComposeUiTest, ms: Long) {
        var left = ms
        while (left > 0) {
            test.mainClock.advanceTimeBy(FRAME_MS)
            left -= FRAME_MS
        }
        test.waitForIdle()
    }

    private fun capture(test: ComposeUiTest): ImageBitmap = test.onAllNodes(isRoot())[0].captureToImage()

    /** The menu's box at rest, in dp of the window. */
    private fun menuBox(test: ComposeUiTest, density: Float): Rect {
        val node = test.onAllNodes(hasTestTag("kit-menu"), useUnmergedTree = true).fetchSemanticsNodes().first()
        val box = io.github.eslamasabry.oc.ui.kit.PaneRimScenes.paneBox(node)
        return Rect(box.left / density, box.top / density, box.right / density, box.bottom / density)
    }

    private fun measure(
        index: Int,
        ms: Long,
        open: Boolean,
        image: ImageBitmap,
        closed: ImageBitmap,
        regions: Regions,
        density: Float,
        cost: Double,
        keep: Boolean,
    ): Frame {
        val px = image.toPixelMap()
        val ref = closed.toPixelMap()
        val m = regions.menu
        fun r(l: Float, t: Float, rr: Float, b: Float) = Rect(l * density, t * density, rr * density, b * density)
        // The bar away from the menu: its start half, inside the capsule.
        val bar = r(24f, 12f, m.left - 40f, 44f)
        val end = r(m.right - 90f, 12f, m.right - 12f, 44f)
        val middle = r(m.left + 24f, m.top + 24f, m.right - 24f, m.bottom - 24f)
        val rim = r(m.left + 2f, m.top + 24f, m.left + 10f, m.bottom - 24f)
        // The column the menu falls as, over the code: glass from the first frames on, so a change of the body's
        // resolution, blur or rows shows there as soon as it happens (the menu's free side is glass only once filled).
        val column = r(m.right - 48f, m.top + 70f, m.right - 8f, m.bottom - 16f)
        val (bottom, left) = glass(px, ref, m, density)
        // Joined while the menu's glass shows below the bar (a closing menu drops its semantics, so not read from them).
        val joined = bottom > m.top + 8f
        return Frame(
            index = index,
            ms = ms,
            open = open,
            joined = joined,
            barLight = light(px, bar),
            barSharp = sharp(px, bar),
            barEnd = light(px, end),
            menuMiddle = light(px, middle),
            menuRim = light(px, rim),
            columnSharp = sharp(px, column),
            glassBottomDp = bottom,
            glassLeftDp = left,
            costMs = cost,
            image = if (keep) image else null,
            midway = joined && (bottom < m.bottom - 2f || left > m.left + 2f),
            sideMidway = joined && left > m.left + 2f && left < m.right - LiquidJoinColumnDp - 2f,
        )
    }

    private fun lum(c: Color): Double = .2126 * c.red + .7152 * c.green + .0722 * c.blue

    /** Mean luminance (0-255) in [box] (px). */
    fun light(px: PixelMap, box: Rect): Double {
        var sum = 0.0
        var n = 0
        for (y in box.top.toInt() until box.bottom.toInt().coerceAtMost(px.height)) for (x in box.left.toInt() until box.right.toInt().coerceAtMost(px.width)) {
            sum += lum(px[x, y])
            n++
        }
        return if (n == 0) 0.0 else sum / n * 255.0
    }

    /** Mean horizontal light gradient (0-255 per px) in [box]: what a lower backdrop resolution takes away. */
    fun sharp(px: PixelMap, box: Rect): Double {
        var sum = 0.0
        var n = 0
        for (y in box.top.toInt() until box.bottom.toInt().coerceAtMost(px.height)) for (x in box.left.toInt() + 1 until box.right.toInt().coerceAtMost(px.width)) {
            sum += abs(lum(px[x, y]) - lum(px[x - 1, y]))
            n++
        }
        return if (n == 0) 0.0 else sum / n * 255.0
    }

    /**
     * Where the menu's glass is (dp): its lowest and its leftmost pixel that differs from the page with the menu closed,
     * below the bar and inside the menu's box at rest widened by 24 dp; (bar bottom, menu right) when none.
     */
    private fun glass(px: PixelMap, ref: PixelMap, menu: Rect, density: Float): Pair<Double, Double> {
        val top = ((menu.top + 6f) * density).toInt()
        val bottomEnd = ((menu.bottom + 24f) * density).toInt().coerceAtMost(px.height)
        val leftEnd = ((menu.left - 24f) * density).toInt().coerceAtLeast(0)
        val rightEnd = (menu.right * density).toInt().coerceAtMost(px.width)
        var lowest = top
        var leftmost = rightEnd
        for (y in top until bottomEnd) for (x in leftEnd until rightEnd) {
            val a = px[x, y]
            val b = ref[x, y]
            val d = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
            if (d > .06f) {
                if (y > lowest) lowest = y
                if (x < leftmost) leftmost = x
            }
        }
        return (lowest / density.toDouble()) to (leftmost / density.toDouble())
    }

    private const val LiquidJoinColumnDp = 56f

    /**
     * Stalls: runs of [MIN_STALL] frames or more where the glass is on its way and does not move at all (both edges
     * still), and runs of [MIN_FILL_STALL] or more where the menu is opening, its side is between the column and rest
     * and the side does not move (the fill waiting). Returns (first frame, frames, kind).
     */
    fun stalls(frames: List<Frame>): List<Triple<Int, Int, String>> {
        val out = mutableListOf<Triple<Int, Int, String>>()
        fun runs(kind: String, least: Int, still: (Frame, Frame) -> Boolean) {
            var start = -1
            for (i in 1..frames.size) {
                val s = i < frames.size && still(frames[i - 1], frames[i])
                if (s && start < 0) start = i
                if (!s && start >= 0) {
                    if (i - start >= least) out += Triple(start, i - start, kind)
                    start = -1
                }
            }
        }
        runs("still", MIN_STALL) { a, b -> a.midway && b.midway && abs(a.glassBottomDp - b.glassBottomDp) < .5 && abs(a.glassLeftDp - b.glassLeftDp) < .5 }
        runs("fill waits", MIN_FILL_STALL) { a, b -> b.open && a.sideMidway && b.sideMidway && abs(a.glassLeftDp - b.glassLeftDp) < .5 }
        return out
    }

    private const val MIN_STALL = 3
    private const val MIN_FILL_STALL = 4

    /** Frames whose change jumps against both neighbours' changes by more than [tol]: a step. */
    fun steps(frames: List<Frame>, tol: Double, value: (Frame) -> Double): List<Pair<Int, Double>> {
        val v = frames.map(value)
        val d = (1 until v.size).map { v[it] - v[it - 1] }
        val out = mutableListOf<Pair<Int, Double>>()
        for (i in 1 until d.size - 1) {
            // A step between frames i and i+1, both joined (the desktop's lone bar is a stand-in).
            if (!frames[i].joined || !frames[i + 1].joined) continue
            if (abs(d[i]) > tol && abs(d[i]) > 3 * maxOf(abs(d[i - 1]), abs(d[i + 1]), 1e-6)) out += (i + 1) to d[i]
        }
        return out
    }
}
