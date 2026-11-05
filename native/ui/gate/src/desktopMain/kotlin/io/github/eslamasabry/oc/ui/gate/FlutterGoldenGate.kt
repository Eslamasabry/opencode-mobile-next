package io.github.eslamasabry.oc.ui.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitPixelRatio
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

/** Which Flutter checkout a golden comes from (standard §2.2). */
enum class GateRef(val dir: String) { Product("8302de537"), Glass("1bbdc3f9c") }

/**
 * The golden-diff gate v2 (standard §8.2): render a native kit part with the inputs of a named Flutter golden and
 * measure how far it is from the picture, on the ink box and on the largest eroded cluster.
 *
 * Name grammar (Flutter's `kitGalleryName`): `<shot>[_ar][_text2][_<W>x<H>]`; `_ar` is locale `ar`, RTL;
 * `_text2` is font scale 2; `_<W>x<H>` is the window (default 412 x 915).
 *
 * Window and density per golden: an explicit `window`/`density` argument to [check], else the golden's line in
 * `looks-gate/flutter/<ref>/render.tsv` (written by `rb_gate.py fetch` from the golden's PNG size and its Flutter
 * test's capture `pixelRatio`, or an explicit `WxH@D` column in NN.gate.tsv), else the name at density 1. A shot at
 * density D renders at W x H dp into a (W·D) x (H·D) px picture, like Flutter's `toImage(pixelRatio: D)`. The 6th
 * render.tsv column is the test view's device pixel ratio (3 in the kit gallery, which captures at 1x), provided as
 * `LocalKitPixelRatio` so a one-physical-pixel hairline is a third of a picture pixel, as in the golden.
 * Output, under `looks-out/gate/`:
 * `<shot>_<mode>-{flutter,native,diff}.png` and `rows/<shot>_<mode>.tsv`.
 */
@OptIn(ExperimentalTestApi::class)
object FlutterGoldenGate {
    enum class Frame { Part, Screen }

    const val CHANNEL_SLACK: Int = 24 // NV: per-channel anti-aliasing tolerance fixed by standard §8.2

    private val window = Pair(412, 915) // F: test/goldens/kit/kit_gallery.dart:109
    private const val TEXT2 = 2f // NV: `_text2` goldens use font scale 2, standard §8.2
    private const val DETAIL = 160 // NV: characters of a failure detail kept in the matrix file
    private const val CAP_W = 1600 // NV: cap-measuring window, standard §8.2
    private const val CAP_H = 1000 // NV: cap-measuring window, standard §8.2
    private const val TOUCH_MIN = 47.5 // NV: smallest touch side in dp that passes, standard §8.2
    private const val TOUCH_NONE = 48.0 // NV: a part with no clickable node counts as a full-size target
    private const val STRIDE = 3 // NV: pixel stride of the blank-render probe
    private const val PERCENT = 100.0 // NV: percent
    private const val PROBE = 2 // NV: the ground colour is sampled at (2,2), standard §8.2
    private const val SETTLE_MS = 1_200L // NV: settle time before capture, standard §8.2
    private val bodyCap = 720 // F: test/goldens/kit/kit_gallery.dart:769
    private val inkPad = 4 // NV: ink box padding in px (standard §8.2)

    private val root: File = File(System.getProperty("user.dir"), "../../..").canonicalFile
    private val flutterDir: File = File(System.getProperty("oc.flutterGoldens") ?: File(root, "looks-gate/flutter").path)
    private val out: File = File(root, "looks-out/gate").also { it.mkdirs() }
    private val nameRx = Regex("""^(.*?)(_ar)?(_text2)?(?:_(\d+)x(\d+))?$""")
    private fun pct(d: Double): String = String.format(Locale.ROOT, "%.2f", d) // NV: two decimals, standard §8.2

    /** What a name asks for. */
    internal class Axes(val rtl: Boolean, val scale: Float, val w: Int, val h: Int, val d: Float = 1f, val ratio: Float? = null)

    internal fun axesOf(name: String): Axes {
        val m = nameRx.matchEntire(name)!!
        val (_, ar, text2, w, h) = m.destructured
        return Axes(rtl = ar.isNotEmpty(), scale = if (text2.isNotEmpty()) TEXT2 else 1f, w = w.toIntOrNull() ?: window.first, h = h.toIntOrNull() ?: window.second)
    }

    /** One line of `<ref>/render.tsv`: window in dp, picture density, and the Flutter view's device pixel ratio. */
    internal class Render(val w: Int, val h: Int, val d: Float, val ratio: Float?)

    /** shot -> its render, from `<ref>/render.tsv`, read once per ref. */
    private val renders = HashMap<GateRef, Map<String, Render>>()

    internal fun renderOf(shot: String, ref: GateRef): Render? = renders.getOrPut(ref) {
        val f = File(flutterDir, "${ref.dir}/render.tsv")
        if (!f.isFile) emptyMap()
        else f.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }.filter { it.size >= 4 } // NV: four columns
            .associate { it[0] to Render(it[1].toInt(), it[2].toInt(), it[3].toFloat(), it.getOrNull(5)?.toFloatOrNull()) } // NV: columns shot, w, h, density, how, view ratio
    }[shot]

    /**
     * The axes of one shot `<name>_<mode>`: [window], [density] and [pixelRatio] win, then render.tsv, then the name.
     * The pixel ratio is the Flutter test view's (`LocalKitPixelRatio`: hairlines and focus rings follow it).
     */
    internal fun axesOf(
        name: String,
        mode: String,
        ref: GateRef,
        textScale: Float?,
        window: Pair<Int, Int>?,
        density: Float?,
        pixelRatio: Float? = null,
    ): Axes {
        val named = axesOf(name)
        val listed = renderOf("${name}_$mode", ref)
        return Axes(
            rtl = named.rtl,
            scale = textScale ?: named.scale,
            w = window?.first ?: listed?.w ?: named.w,
            h = window?.second ?: listed?.h ?: named.h,
            d = density ?: listed?.d ?: 1f,
            ratio = pixelRatio ?: listed?.ratio,
        )
    }

    /**
     * Renders [content] once per mode as the Flutter golden `<name>_<mode>` and records the comparison. Throws only
     * when the golden file is missing or the content throws; thresholds are applied by gate_order.sh.
     */
    fun check(
        name: String,
        frame: Frame = Frame.Part,
        motion: MotionLevel = MotionLevel.Off,
        style: Style = Style.Solid,
        ref: GateRef = GateRef.Product,
        modes: List<String> = listOf("light", "dark"),
        wrap: @Composable (@Composable () -> Unit) -> Unit = { it() },
        textScale: Float? = null,
        window: Pair<Int, Int>? = null,
        density: Float? = null,
        pixelRatio: Float? = null,
        act: (androidx.compose.ui.test.ComposeUiTest.() -> Unit)? = null,
        content: @Composable () -> Unit,
    ): Map<String, Double> {
        val result = LinkedHashMap<String, Double>()
        for (mode in modes) {
            val axes = axesOf(name, mode, ref, textScale, window, density, pixelRatio)
            val native = render(axes, mode == "dark", motion, style, frame, wrap, content, act = act)
            result[mode] = compare("${name}_$mode", ref, native)
        }
        return result
    }

    /** One render of [content] at the window of [axes], returned as a bitmap. */
    internal fun render(
        axes: Axes,
        dark: Boolean,
        motion: MotionLevel,
        style: Style,
        frame: Frame,
        wrap: @Composable (@Composable () -> Unit) -> Unit,
        content: @Composable () -> Unit,
        scale: Float = axes.scale,
        rtl: Boolean = axes.rtl,
        onRoot: (androidx.compose.ui.test.ComposeUiTest.() -> Unit)? = null,
        act: (androidx.compose.ui.test.ComposeUiTest.() -> Unit)? = null,
    ): BufferedImage {
        val tag = if (rtl) "ar" else "en"
        Locale.setDefault(Locale.forLanguageTag(tag))
        var native: BufferedImage? = null
        runDesktopComposeUiTest(kotlin.math.round(axes.w * axes.d).toInt(), kotlin.math.round(axes.h * axes.d).toInt()) {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(axes.d, fontScale = scale),
                    LocalKitPixelRatio provides axes.ratio,
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    KitTheme(
                        appearance = KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = motion, style = style),
                        platform = DesktopKitPlatform(),
                        localeTag = tag,
                        stillFrame = true,
                    ) {
                        wrap {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                when (frame) {
                                    Frame.Screen -> content()
                                    Frame.Part ->
                                        Column(
                                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp), // F: test/goldens/kit/kit_gallery.dart:766,769
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) { Box(Modifier.widthIn(max = bodyCap.dp)) { content() } }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(SETTLE_MS)
            waitForIdle()
            if (act != null) {
                act(this) // the interaction of a hovered, pressed or focused shot
                waitForIdle()
                mainClock.advanceTimeBy(SETTLE_MS)
                waitForIdle()
            }
            onRoot?.invoke(this)
            native = onRoot().captureToImage().toAwtImage()
        }
        return native!!
    }

    /** The measures of one comparison (standard §8.2). */
    internal class Box(val x: Int, val y: Int, val w: Int, val h: Int) {
        fun has(px: Int, py: Int) = px >= x && px < x + w && py >= y && py < y + h
        override fun toString() = "$x,$y,$w,$h"
    }

    internal class Measure(val framePct: Double, val boxPct: Double, val blob: Int, val box: Box, val size: String)

    internal fun measure(flutter: BufferedImage, native: BufferedImage): Pair<Measure, BufferedImage> {
        val w = minOf(flutter.width, native.width)
        val h = minOf(flutter.height, native.height)
        val fl = IntArray(w * h) { flutter.getRGB(it % w, it / w) }
        val nt = IntArray(w * h) { native.getRGB(it % w, it / w) }
        val diff = BooleanArray(w * h) { differs(fl[it], nt[it]) }
        val gf = flutter.getRGB(PROBE, PROBE)
        val gn = native.getRGB(PROBE, PROBE)
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (i in 0 until w * h) {
            if (fl[i] != gf || nt[i] != gn) {
                val x = i % w; val y = i / w
                if (x < x0) x0 = x; if (x > x1) x1 = x
                if (y < y0) y0 = y; if (y > y1) y1 = y
            }
        }
        val box = if (x1 < 0) Box(0, 0, w, h)
        else {
            val bx = maxOf(0, x0 - inkPad); val by = maxOf(0, y0 - inkPad)
            Box(bx, by, minOf(w, x1 + inkPad + 1) - bx, minOf(h, y1 + inkPad + 1) - by)
        }
        var total = 0L; var inBox = 0L
        for (i in 0 until w * h) if (diff[i]) {
            total++
            val x = i % w; val y = i / w
            if (box.has(x, y)) inBox++
        }
        val same = flutter.width == native.width && flutter.height == native.height
        val size = if (same) "same-size" else "size ${native.width}x${native.height} vs ${flutter.width}x${flutter.height}"
        val measure = Measure(total * PERCENT / (w.toLong() * h), inBox * PERCENT / (box.w.toLong() * box.h), largestEroded(diff, w, h), box, size)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (i in 0 until w * h) {
            val b = nt[i]
            val g = ((b shr 16 and 0xFF) * 0.3 + (b shr 8 and 0xFF) * 0.59 + (b and 0xFF) * 0.11).toInt() / 3 + 40 // NV: dimmed grey of the native pixel
            img.setRGB(i % w, i / w, if (diff[i]) Color(230, 30, 30).rgb else Color(g, g, g).rgb) // NV: diff red and dim grey
        }
        val blue = Color(30, 90, 230).rgb // NV: ink box outline blue
        for (x in box.x until box.x + box.w) { img.setRGB(x, box.y, blue); img.setRGB(x, box.y + box.h - 1, blue) }
        for (y in box.y until box.y + box.h) { img.setRGB(box.x, y, blue); img.setRGB(box.x + box.w - 1, y, blue) }
        return measure to img
    }

    private fun differs(a: Int, b: Int): Boolean =
        (0..16 step 8).any { s -> kotlin.math.abs((a shr s and 0xFF) - (b shr s and 0xFF)) > CHANNEL_SLACK } // NV: RGB shifts 0, 8, 16

    /** Erode once (a pixel stays when it and all 8 neighbours differ), then the biggest 8-connected cluster. */
    private fun largestEroded(diff: BooleanArray, w: Int, h: Int): Int {
        val kept = BooleanArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            var all = true
            loop@ for (dy in -1..1) for (dx in -1..1) if (!diff[(y + dy) * w + x + dx]) { all = false; break@loop }
            kept[y * w + x] = all
        }
        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var best = 0
        for (s in 0 until w * h) {
            if (!kept[s] || seen[s]) continue
            var sp = 0; var n = 0
            stack[sp++] = s; seen[s] = true
            while (sp > 0) {
                val p = stack[--sp]; n++
                val px = p % w; val py = p / w
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = px + dx; val ny = py + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val q = ny * w + nx
                    if (kept[q] && !seen[q]) { seen[q] = true; stack[sp++] = q }
                }
            }
            if (n > best) best = n
        }
        return best
    }

    /** True when the light golden of [name] has been fetched (the proof cases of other orders skip otherwise). */
    fun hasGolden(name: String, ref: GateRef = GateRef.Product): Boolean = goldenFile("${name}_light", ref).isFile

    private fun goldenFile(shot: String, ref: GateRef): File {
        val own = File(flutterDir, "${ref.dir}/$shot.png")
        return if (own.isFile || ref != GateRef.Product) own else File(flutterDir, "$shot.png")
    }

    private fun compare(shot: String, ref: GateRef, native: BufferedImage): Double {
        val goldenFile = goldenFile(shot, ref)
        ImageIO.write(native, "png", File(out, "$shot-native.png"))
        check(goldenFile.isFile) { "missing Flutter golden $goldenFile" }
        val flutter = ImageIO.read(goldenFile)
        ImageIO.write(flutter, "png", File(out, "$shot-flutter.png"))
        val (m, img) = measure(flutter, native)
        ImageIO.write(img, "png", File(out, "$shot-diff.png"))
        File(out, "rows").mkdirs()
        File(out, "rows/$shot.tsv").writeText(
            "$shot\t${ref.dir}\t${pct(m.framePct)}\t${pct(m.boxPct)}\t${m.blob}\t${m.box}\t${m.size}\n",
        )
        return m.boxPct
    }

    // ---------------------------------------------------------------------------------------------- matrix

    /** dp caps by role, test/kit/kit_capacity.json (standard A5). */
    private val roleCaps: Map<String, Int> =
        "conversation=700 reading=720 list=960 sheet=640 confirm=560 dialog=480 popover=320 toast=480 state=440 lane=400 control=400 chip=320 glyph=96"
            .split(' ').associate { it.substringBefore('=') to it.substringAfter('=').toInt() }
    private val scales = "1.0 1.3 2.0".split(' ') // standard §9.1 text scales

    /**
     * The native-only axes for one part: every Style x light/dark x font scale 1.0/1.3/2.0 x LTR/RTL at the default
     * window, each cap class measured at 1600 x 1000 (light, Solid, 1.0, LTR), touch targets and labels.
     */
    fun matrix(
        part: String,
        caps: Map<String, String> = emptyMap(),
        frame: Frame = Frame.Part,
        wrap: @Composable (@Composable () -> Unit) -> Unit = { it() },
        content: @Composable () -> Unit,
    ) {
        val lines = ArrayList<String>()
        lines += "# styles: ${Style.entries.joinToString(",") { it.storageName }}"
        val base = Axes(false, 1f, window.first, window.second)
        for (style in Style.entries) for (mode in listOf("light", "dark")) for (scale in scales) for (rtl in listOf(false, true)) {
            val key = "${style.storageName}\t$mode\t$scale\t${if (rtl) "rtl" else "ltr"}"
            val verdict = try {
                val img = render(base, mode == "dark", MotionLevel.Off, style, frame, wrap, content, scale = scale.toFloat(), rtl = rtl)
                if (isBlank(img)) "FAIL\tempty composition" else "PASS\t-"
            } catch (t: Throwable) {
                "FAIL\t${(t.javaClass.simpleName + ": " + (t.message ?: "")).replace('\n', ' ').replace('\t', ' ').take(DETAIL)}"
            }
            lines += "render\t$part\t$key\t$verdict"
        }
        val wide = Axes(false, 1f, CAP_W, CAP_H)
        val painted = try {
            val img = render(wide, false, MotionLevel.Off, Style.Solid, frame, wrap, content)
            inkWidth(img)
        } catch (t: Throwable) {
            -1
        }
        for ((cls, role) in caps) {
            val cap = roleCaps[role]
            val ok = painted >= 0 && (cap == null || painted <= cap)
            lines += "cap\t$part\t$cls\t$role\t$painted\t${cap ?: "-"}\t${if (ok) "PASS" else "FAIL"}"
        }
        val (smallest, unlabelled) = touch(base, frame, wrap, content)
        val small = String.format(Locale.ROOT, "%.1f", smallest) // NV: one decimal
        lines += "target\t$part\t$small\t${if (smallest >= TOUCH_MIN) "PASS" else "FAIL"}"
        lines += "label\t$part\t$unlabelled\t${if (unlabelled == 0) "PASS" else "FAIL"}"
        File(out, "matrix").mkdirs()
        File(out, "matrix/$part.tsv").writeText(lines.joinToString("\n", postfix = "\n"))
    }

    private fun isBlank(img: BufferedImage): Boolean {
        val g = img.getRGB(PROBE, PROBE)
        for (y in 0 until img.height step STRIDE) for (x in 0 until img.width step STRIDE) if (img.getRGB(x, y) != g) return false
        return true
    }

    /** Ink width in dp (density 1): the columns that differ from the ground colour at (2,2). */
    private fun inkWidth(img: BufferedImage): Int {
        val g = img.getRGB(PROBE, PROBE)
        var lo = img.width; var hi = -1
        for (x in 0 until img.width) for (y in 0 until img.height) if (img.getRGB(x, y) != g) { if (x < lo) lo = x; if (x > hi) hi = x; break }
        return if (hi < 0) 0 else hi - lo + 1
    }

    /** (smallest touch side in dp among clickable nodes, number of clickable nodes without a text or description). */
    private fun touch(
        axes: Axes,
        frame: Frame,
        wrap: @Composable (@Composable () -> Unit) -> Unit,
        content: @Composable () -> Unit,
    ): Pair<Double, Int> {
        var smallest = Double.MAX_VALUE
        var unlabelled = 0
        try {
            render(axes, false, MotionLevel.Off, Style.Solid, frame, wrap, content, onRoot = {
                val nodes = onAllNodes(SemanticsMatcher("clickable") { SemanticsProperties.Role in it.config || androidx.compose.ui.semantics.SemanticsActions.OnClick in it.config }).fetchSemanticsNodes()
                for (n in nodes) {
                    val s = minOf(n.size.width, n.size.height).toDouble()
                    if (s < smallest) smallest = s
                    val cfg = n.config
                    val hasText = SemanticsProperties.Text in cfg || SemanticsProperties.ContentDescription in cfg ||
                        n.children.any { SemanticsProperties.Text in it.config || SemanticsProperties.ContentDescription in it.config }
                    if (!hasText) unlabelled++
                }
            })
        } catch (_: Throwable) {
            return 0.0 to 1
        }
        return (if (smallest == Double.MAX_VALUE) TOUCH_NONE else smallest) to unlabelled
    }
}
