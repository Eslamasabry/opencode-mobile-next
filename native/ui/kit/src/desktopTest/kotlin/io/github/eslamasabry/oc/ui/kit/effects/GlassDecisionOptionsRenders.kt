@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitConversation
import io.github.eslamasabry.oc.ui.kit.row.KitConversationRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test

/**
 * Owner 2026-11-13 on the decision panes (sheets, dialogs, menus). Morning: the page's rows read through. Midday: a
 * band at the rim and a soft inner rectangle. Afternoon, on the phone (Glass dark, Aurora drift): the "..." menu joined
 * to the bar was a flat near-black slab, the bar with it ("What did you do to the glass?"): holding the page's light
 * DOWN to 8 % drags the mean to black. Round 3 rule: the pane stays glass. The Aurora and the page's light and colour
 * come through as soft light; only fine DETAIL (rows of words, button edges) is compressed, around the LOCAL mean of
 * what is behind, never toward black or white ([LOCAL_HOLD]).
 *
 * Options (real kit content over Aurora drift, at its still moment, dark and light, 1080 px wide; one material to the
 * edge, the crafted 1 dp rim, saturation 1.6):
 * - now: this morning's material (12 dp, knee .36, the rim band), as the build line has it again;
 * - L1: blur 24 dp, the shipped tint, the local-contrast hold;
 * - L2: blur 16 dp, the tint +.10, the local-contrast hold.
 * The guard, per option, on the stripe scenes over the Aurora: the decision pane's mean luminance against the plain
 * bar's over the same patch (it must let light through), and the hidden-edge step of [ForegroundGhostTest] against the
 * aurora alone (detail must not read). Not a test: it runs only when `GLASS_OPTIONS_OUT` names a folder.
 */
class GlassDecisionOptionsRenders {
    private val options =
        listOf(
            Option("now", 0f, 0f, 0f, 0f, morning = true),
            Option("L1", 24f, 0f, FREE, SLOPE),
            Option("L2", 16f, .10f, FREE, SLOPE),
        )

    private class OptionPlatform(val option: Option, val seen: MutableList<String>) :
        KitPlatform by io.github.eslamasabry.oc.ui.kit.FakeKitPlatform(
            frost = true,
            liquidGlass = true,
            liveShaders = io.github.eslamasabry.oc.ui.kit.live.LiveShaderCompiler(io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader::compile),
            joinEffect = { p, b -> DesktopKitPlatform().liquidJoinEffect(p, b) },
        ) {
        override val liquidGlass: Boolean = true

        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
            // Only the decision panes (the roles with a hold: sheets, menus) take the option; a bar keeps its own.
            if (option.morning || !spec.recipe.hold.knee) return kitLiquidPlainStandIn(spec, backdrop, shape, this)
            val s =
                spec.copy(
                    recipe = spec.recipe.copy(blurDp = option.blurDp),
                    fill = spec.fill.copy(alpha = min(1f, spec.fill.alpha + option.tintPlus)),
                    fillStart = spec.fillStart.copy(alpha = min(1f, spec.fillStart.alpha + option.tintPlus)),
                )
            seen += "fill %.2f, blur %.0f dp, local hold: mean over %.0f dp, free %.3f, slope %.2f".format(s.fill.alpha, s.recipe.blurDp, MEAN_DP, option.free, option.slope)
            return localHoldStandIn(s, backdrop, shape, this, option)
        }
    }

    // ---- real content ----

    @Composable
    private fun ChatPage() {
        val c = LocalKitColors.current
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.align(Alignment.End).background(c.surface2, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                KitText("The upload test fails on CI about one run in five. Can you find out why?")
            }
            KitText(
                "The test waits a fixed 200 ms for the upload to finish, but on the CI machine the fake server answers " +
                    "later under load. I replaced the sleep with a wait on the upload's own completion signal.",
            )
            Column(Modifier.fillMaxWidth().background(c.surface1, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KitText("UploadTest.kt", role = TypeRole.Caption, tone = TextTone.Secondary)
                for (line in CODE) KitText(line, role = TypeRole.Mono, maxLines = 1)
            }
            KitText("Then I ran the test 50 times in a row; it passed every time. One step needs your answer:")
            Column(Modifier.fillMaxWidth().background(c.surface1, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KitText("Run a command", role = TypeRole.RowTitle)
                KitText("./gradlew :app:test --tests UploadTest", role = TypeRole.Mono)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KitButton("Allow", {}, role = KitButtonRole.Primary)
                    KitButton("Don't allow", {}, role = KitButtonRole.Secondary)
                }
            }
            KitText("Once it runs I will report the timings and close the loop on the flaky label.")
        }
    }

    @Composable
    private fun SheetDetails() {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KitText("Run this command?", role = TypeRole.Title)
            KitText("It runs the project's upload test once, on this computer. It changes no files.", tone = TextTone.Secondary)
            KitText("./gradlew :app:test --tests UploadTest", role = TypeRole.Mono)
            KitText("Asked by the build agent, 2 minutes ago", role = TypeRole.Caption, tone = TextTone.Tertiary)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KitButton("Copy what it asks to run", {}, role = KitButtonRole.Secondary)
            }
        }
    }

    @Composable
    private fun ListPage() {
        Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
            for ((title, project, time) in ROWS) {
                KitConversationRow(KitConversation(title, project, time, null), onClick = {})
            }
        }
    }

    @Composable
    private fun MenuItems() {
        Column(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
            for (label in listOf("Pin conversation", "Rename", "Move to project", "Delete conversation")) {
                KitText(label, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), role = TypeRole.RowTitle)
            }
        }
    }

    private enum class Real { Sheet, Menu }

    @Composable
    private fun RealScene(kind: Real, words: Boolean = true) {
        val page = rememberKitLiquidSource()
        CompositionLocalProvider(LocalKitLiquidPage provides page) {
            Box(Modifier.size(W.dp, REAL_H.dp).testTag("scene")) {
                // The pane inside the live background (as a page's sheet host is), so it sees the Aurora behind the page.
                io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().kitLiquidSource(page)) { if (kind == Real.Sheet) ChatPage() else ListPage() }
                        val box = paneBox(kind)
                        val shape = if (kind == Real.Sheet) RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) else RoundedCornerShape(20.dp)
                        val face = if (kind == Real.Sheet) kitLiquidSheetFace(shape, { 0f }) else kitLiquidMenuFace(shape, { 0f })
                        Box(Modifier.offset(box[0].dp, box[1].dp).size((box[2] - box[0]).dp, (box[3] - box[1]).dp).then(face ?: Modifier.background(Color.Magenta))) {
                            if (words) CompositionLocalProvider(LocalKitOnLiquidGlass provides true) { if (kind == Real.Sheet) SheetDetails() else MenuItems() }
                        }
                    }
                }
            }
        }
    }

    /** The pane's box in dp: left, top, right, bottom. */
    private fun paneBox(kind: Real): FloatArray =
        if (kind == Real.Sheet) floatArrayOf(0f, REAL_H - SHEET_H, W, REAL_H) else floatArrayOf(120f, 250f, 360f, 460f)

    private fun render(height: Float, dark: Boolean, platform: KitPlatform, content: @Composable () -> Unit): ImageBitmap {
        lateinit var out: ImageBitmap
        runDesktopComposeUiTest((W * D).roundToInt(), (height * D).roundToInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(
                        appearance = KitAppearance(style = Style.Glass, mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, liveBackground = true, liveStyle = "aurora"),
                        platform = platform,
                        localeTag = "en",
                        stillFrame = true,
                        content = content,
                    )
                }
            }
            waitForIdle()
            out = onNodeWithTag("scene").captureToImage()
        }
        return out
    }

    private fun lum(c: Color): Float = .2126f * c.red + .7152f * c.green + .0722f * c.blue

    /**
     * The pane's inside (all but its outer [INSET_DP]) blurred by [BLUR_PX] (a box blur run three times, about a Gaussian):
     * the largest distance of any patch from the mean luminance, in % of full light, and where it is (px).
     */
    private fun patchSpread(img: PixelMap, box: FloatArray): Triple<Float, Int, Int> {
        val l = ((box[0] + INSET_DP) * D).roundToInt()
        val t = ((box[1] + INSET_DP) * D).roundToInt()
        val r = (min(box[2] * D, img.width.toFloat()) - INSET_DP * D).roundToInt()
        val b = (min(box[3] * D, img.height.toFloat()) - INSET_DP * D).roundToInt()
        val w = r - l
        val h = b - t
        var a = FloatArray(w * h) { i -> lum(img[l + i % w, t + i / w]) }
        val k = BLUR_PX / 2
        repeat(3) {
            val tmp = FloatArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                var sum = 0f
                var n = 0
                for (dx in -k..k) { val xx = x + dx; if (xx in 0 until w) { sum += a[y * w + xx]; n++ } }
                tmp[y * w + x] = sum / n
            }
            val out = FloatArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                var sum = 0f
                var n = 0
                for (dy in -k..k) { val yy = y + dy; if (yy in 0 until h) { sum += tmp[yy * w + x]; n++ } }
                out[y * w + x] = sum / n
            }
            a = out
        }
        val mean = a.average().toFloat()
        var worst = 0f
        var at = 0
        for (i in a.indices) { val d = abs(a[i] - mean); if (d > worst) { worst = d; at = i } }
        return Triple(worst * 100f, l + at % w, t + at / w)
    }

    @Test
    fun `decision options on real content`() {
        val out = System.getenv("GLASS_OPTIONS_OUT")?.let(::File) ?: return
        out.mkdirs()
        val notes = StringBuilder()
        for (option in options) for (kind in Real.entries) for (dark in listOf(true, false)) {
            val seen = mutableListOf<String>()
            val platform = OptionPlatform(option, seen)
            val img = render(REAL_H, dark, platform) { RealScene(kind) }
            val mode = if (dark) "dark" else "light"
            val name = "opt-${option.name}-${if (kind == Real.Sheet) "chat-sheet" else "list-menu"}-$mode"
            val awt = img.toAwtImage()
            ImageIO.write(awt, "png", File(out, "$name.png"))
            val box = paneBox(kind)
            if (kind == Real.Menu) {
                // The menu's end edge and its bottom-end corner, four times.
                val px = { v: Float -> (v * D).roundToInt() }
                ImageIO.write(zoom(awt, px(box[2] - 60f), px(box[1] + 40f), px(70f), px(110f)), "png", File(out, "$name-end-edge-4x.png"))
                ImageIO.write(zoom(awt, px(box[2] - 60f), px(box[3] - 60f), px(70f), px(70f)), "png", File(out, "$name-bottom-end-4x.png"))
            }
            val bare = render(REAL_H, dark, OptionPlatform(option, mutableListOf())) { RealScene(kind, words = false) }.toPixelMap()
            val (spread, x, y) = patchSpread(bare, box)
            notes.append("$name: ${seen.distinct().joinToString("; ").ifEmpty { "this morning's material" }}; without its words, blurred 8 px: the worst patch is %.1f %% from the pane's mean, at (%d, %d) px\n".format(spread, x, y))
        }
        File(out, "options-notes.txt").writeText(notes.toString())
    }

    private fun zoom(img: java.awt.image.BufferedImage, x: Int, y: Int, w: Int, h: Int): java.awt.image.BufferedImage {
        val sx = x.coerceIn(0, img.width - w)
        val sy = y.coerceIn(0, img.height - h)
        val outImg = java.awt.image.BufferedImage(w * 4, h * 4, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = outImg.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.drawImage(img, 0, 0, w * 4, h * 4, sx, sy, sx + w, sy + h, null)
        g.dispose()
        return outImg
    }

    /**
     * The owner's phone, 2026-11-13: Glass dark over Aurora drift, a chat scrolled so a table and its text sit behind the
     * top bar, the "..." menu open and joined to the bar. Drawn with the kit's real screen, bar and menu (the joined body
     * is the build line's join shader: options for the plain panes do not reach it).
     */
    @Test
    fun `the owner's scene - the chat's menu joined to the bar over Aurora drift`() {
        val out = System.getenv("GLASS_OPTIONS_OUT")?.let(::File) ?: return
        out.mkdirs()
        val items =
            listOf("Find", "Rename conversation", "Fork conversation", "Copy transcript", "Go to", "More").map {
                io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem(it, onSelect = {})
            }
        androidx.compose.ui.test.v2.runSkikoComposeUiTest(Size(1080f, 2400f), testTimeout = kotlin.time.Duration.parse("6m")) {
            io.github.eslamasabry.oc.ui.kit.PaneRimScenes.settle(this) {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(
                        KitAppearance(mode = AppearanceMode.Dark, style = Style.Glass, liveBackground = true, liveStyle = "aurora"),
                        OptionPlatform(options.first(), mutableListOf()),
                        "en",
                        stillFrame = true,
                    ) {
                        io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground(Modifier.fillMaxSize()) {
                            val source = io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState()
                            io.github.eslamasabry.oc.ui.kit.frame.KitScreen(
                                page = true,
                                overlayChrome = true,
                                frost = source,
                                topBar = {
                                    Box(Modifier.fillMaxWidth().height(72.dp)) {
                                        KitText("Can you run this on your Ubuntu?", Modifier.align(Alignment.CenterStart).padding(start = 56.dp), role = TypeRole.RowTitle, maxLines = 1)
                                        io.github.eslamasabry.oc.ui.kit.modal.KitMenu(
                                            expanded = true,
                                            items = items,
                                            onDismissRequest = {},
                                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                                        ) { KitText("...") }
                                    }
                                },
                                edges = io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges.Bleed,
                            ) {
                                Column(
                                    Modifier.fillMaxSize().kitFrostSource(source).padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    for ((name, stars) in TABLE) {
                                        Row(Modifier.fillMaxWidth().background(LocalKitColors.current.surface1).padding(14.dp)) {
                                            KitText(name, Modifier.weight(1f), role = TypeRole.Mono, maxLines = 1)
                                            KitText(stars, role = TypeRole.Body)
                                        }
                                    }
                                    KitText("A couple of important caveats before you run any of these forks.")
                                    KitText("1. The original author withdrew the project. Running one is your call.")
                                    KitText("2. It is a third-party client for a short-drama service whose terms it does not control.")
                                    KitText("3. It is a desktop app, not a web server: building it here needs a full toolchain.")
                                }
                            }
                        }
                    }
                }
            }
            ImageIO.write(onAllNodes(androidx.compose.ui.test.isRoot())[0].captureToImage().toAwtImage(), "png", File(out, "owner-scene-joined-menu-aurora-dark-now.png"))
        }
    }

    // ---- the hard test: stripes and a button, as ForegroundGhostTest draws them ----

    private enum class Kind { Menu, Sheet }

    private enum class Content { Empty, Button, Rows }

    private class Place(val x: Float, val y: Float, val w: Float, val h: Float) {
        val cx get() = x + w / 2f
        val cy get() = y + h / 2f
    }

    private fun place(kind: Kind): Place = if (kind == Kind.Menu) Place(60f, 140f, 260f, 190f) else Place(0f, 300f, W, 260f)

    /** [bar]: the plain bar's face (LiquidRole.Bar) over the same patch instead of the decision pane. */
    @Composable
    private fun StripeScene(kind: Kind, content: Content, bar: Boolean = false) {
        val page = rememberKitLiquidSource()
        val colors = LocalKitColors.current
        CompositionLocalProvider(LocalKitLiquidPage provides page) {
            Box(Modifier.size(W.dp, STRIPE_H.dp).testTag("scene")) {
                val p = place(kind)
                io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground(Modifier.fillMaxSize()) {
                  Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize().kitLiquidSource(page).drawBehind {
                            val d = density
                            when (content) {
                                Content.Empty -> Unit
                                Content.Button -> drawRoundRect(colors.accent, Offset((p.cx - 80f) * d, (p.cy - 24f) * d), Size(160f * d, 48f * d), CornerRadius(24f * d))
                                Content.Rows ->
                                    for (line in -3..3) {
                                        var x = 8f
                                        val y = p.cy + line * 24f
                                        while (x < W - 8f) {
                                            drawRect(colors.text1, Offset(x * d, (y - 7f) * d), Size(2.2f * d, 14f * d))
                                            x += 5f
                                        }
                                    }
                            }
                        },
                    )
                    val shape = RoundedCornerShape(28.dp)
                    val face =
                        when {
                            bar -> kitLiquidFace(io.github.eslamasabry.oc.ui.design.effects.LiquidRole.Bar, page, shape)
                            kind == Kind.Menu -> kitLiquidMenuFace(shape, { 0f })
                            else -> kitLiquidSheetFace(shape, { 0f })
                        }
                    Box(Modifier.offset(p.x.dp, p.y.dp).size(p.w.dp, p.h.dp).then(face ?: Modifier.background(Color.Magenta)))
                  }
                }
            }
        }
    }

    private fun meanLum(img: PixelMap, kind: Kind): Float {
        val p = place(kind)
        var sum = 0.0
        var n = 0
        for (y in ((p.y + INSET_DP + 20f) * D).roundToInt() until ((p.y + p.h - INSET_DP - 20f) * D).roundToInt() step 3) {
            for (x in ((p.x + INSET_DP + 20f) * D).roundToInt() until ((p.x + p.w - INSET_DP - 20f) * D).roundToInt() step 3) {
                sum += lum(img[x, y]); n++
            }
        }
        return (sum / n).toFloat()
    }

    private fun ghost(img: PixelMap, empty: PixelMap, kind: Kind): Pair<Float, Float> {
        val p = place(kind)
        val step = (STEP_DP * D).roundToInt()
        val l = ((p.x + INSET_DP) * D).roundToInt()
        val t = ((p.y + INSET_DP) * D).roundToInt()
        val r = ((p.x + p.w - INSET_DP) * D).roundToInt()
        val b = ((p.y + p.h - INSET_DP) * D).roundToInt()
        fun diff(x: Int, y: Int): FloatArray {
            val a = img[x, y]
            val e = empty[x, y]
            return floatArrayOf((a.red - e.red) * 255f, (a.green - e.green) * 255f, (a.blue - e.blue) * 255f)
        }
        var luma = 0f
        var colour = 0f
        for (y in t until b step 2) for (x in l until r step 2) {
            val here = diff(x, y)
            for ((nx, ny) in listOf(x + step to y, x to y + step)) {
                if (nx >= r || ny >= b) continue
                val there = diff(nx, ny)
                luma = max(luma, abs(0.2126f * (here[0] - there[0]) + 0.7152f * (here[1] - there[1]) + 0.0722f * (here[2] - there[2])))
                colour = max(colour, sqrt((0 until 3).sumOf { ((here[it] - there[it]) * (here[it] - there[it])).toDouble() }.toFloat()))
            }
        }
        return luma to colour
    }

    @Test
    fun `decision options on stripes over the Aurora - the guard`() {
        val out = System.getenv("GLASS_OPTIONS_OUT")?.let(::File) ?: return
        out.mkdirs()
        val table =
            StringBuilder(
                "option | pane | mode | pane mean vs plain bar mean (luminance, band .08) | rows step light/colour | button step light/colour (limits 12 / 18) | guard\n",
            )
        for (option in options) for (kind in Kind.entries) for (dark in listOf(true, false)) {
            fun shot(content: Content, bar: Boolean = false): PixelMap = render(STRIPE_H, dark, OptionPlatform(option, mutableListOf())) { StripeScene(kind, content, bar) }.toPixelMap()
            val empty = shot(Content.Empty)
            val barMean = meanLum(shot(Content.Empty, bar = true), kind)
            val paneMean = meanLum(empty, kind)
            val rows = ghost(shot(Content.Rows), empty, kind)
            val button = ghost(shot(Content.Button), empty, kind)
            val lets = abs(paneMean - barMean) <= MEAN_BAND
            val hides = rows.first <= 12f && rows.second <= 18f && button.first <= 12f && button.second <= 18f
            table.append(
                "%s | %s | %s | %.3f vs %.3f | %.1f / %.1f | %.1f / %.1f | %s\n".format(
                    option.name, kind, if (dark) "dark" else "light", paneMean, barMean, rows.first, rows.second, button.first, button.second,
                    when {
                        lets && hides -> "PASS"
                        !lets && !hides -> "FAIL: too dark/light AND detail reads"
                        !lets -> "FAIL: the pane does not let the light through"
                        else -> "FAIL: detail reads through"
                    },
                ),
            )
        }
        File(out, "options-guard.txt").writeText(table.toString())
    }

    private companion object {
        const val D = 2.625f
        const val W = 1080f / D
        const val REAL_H = 800f
        const val SHEET_H = 330f
        const val STRIPE_H = 640f
        const val STEP_DP = 12f

        /** The pane's inside measured: everything but the outer 8 dp (the rim light and its anti-aliasing). */
        const val INSET_DP = 8f

        /** The blur the patch measure takes (px), as the review asked. */
        const val BLUR_PX = 8

        /** The local hold: the detail (RGB distance from the local mean, 0-1) that passes as it is, and the share kept beyond. */
        const val FREE = .015f
        const val SLOPE = .12f

        /** How far the local mean reaches (dp): about two lines of words, less than a card. */
        const val MEAN_DP = 36f

        /** The guard's band: a decision pane's mean luminance within this of the plain bar's over the same patch. */
        const val MEAN_BAND = .08f

        val TABLE =
            listOf("zxsos/HGhubX" to "932", "hichuv/HGhub" to "96", "bashirian777/HGhub" to "66", "stlin256/HGhub-Harmony" to "20", "vs2pk0/HGhub" to "16")

        val CODE =
            listOf(
                "@Test fun uploadFinishes() = runTest {",
                "    val upload = client.upload(file)",
                "    upload.completion.await()",
                "    assertEquals(Done, upload.state)",
                "}",
            )

        val ROWS =
            listOf(
                Triple("Fix the flaky upload test", "mobile-app", "2m"),
                Triple("Draft the release notes for 2.2", "mobile-app", "18m"),
                Triple("Why does the build cache keep growing", "build-tools", "1h"),
                Triple("Rename the settings groups", "mobile-app", "3h"),
                Triple("Add retries to the sync worker", "server", "5h"),
                Triple("Translate the new strings", "mobile-app", "Yesterday"),
                Triple("Profile the chat scroll on old phones", "mobile-app", "Yesterday"),
                Triple("Clean up unused icons", "design", "Mon"),
                Triple("Write the migration guide", "docs", "Sun"),
                Triple("Check the widget on small launchers", "mobile-app", "Sat"),
            )
    }
}

/** One option: the decision pane's blur, its tint's extra alpha, its local hold (free, slope); [morning]: the build line's. */
private data class Option(val name: String, val blurDp: Float, val tintPlus: Float, val free: Float, val slope: Float, val morning: Boolean = false)

/**
 * The decision pane as Android would draw it under [o]: the backdrop recorded on a layer padded by the blur and the
 * local mean's reach (the library pads its layer by the blur), so neither clamps at the pane's own edge; colour
 * controls, blur and [LOCAL_HOLD] in one pass, the same everywhere inside the outline; then the tint and the 1 dp rim.
 */
private fun localHoldStandIn(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, platform: KitPlatform, o: Option): Modifier {
    val at = arrayOfNulls<LayoutCoordinates>(1)
    val program = platform.liveShaders?.compile(LOCAL_HOLD)
    val rim = KitRimShader.programFor(platform.liveShaders)
    return Modifier.onGloballyPositioned { at[0] = it }
        .clip(shape)
        .drawWithCache {
            val layer = obtainGraphicsLayer()
            val r = spec.recipe.blurDp.dp.toPx()
            val reach = MEAN_REACH_DP.dp.toPx()
            val pad = ceil(r + reach + 2f)
            onDrawBehind {
                val c = at[0] ?: return@onDrawBehind
                val p = program ?: return@onDrawBehind
                val recipe = spec.recipe
                p.float4("cc", recipe.saturation, recipe.contrast, recipe.brightness, 0f)
                p.float4("hold", reach, o.free, o.slope, 0f)
                layer.renderEffect = platform.liquidJoinEffect(p, r)
                val w = size.width.roundToInt() + 2 * pad.toInt()
                val h = size.height.roundToInt() + 2 * pad.toInt()
                layer.record(Density(density, fontScale), layoutDirection, IntSize(w, h)) { translate(pad, pad) { backdrop.draw(this, c) } }
                translate(-pad, -pad) { drawLayer(layer) }
                drawLiquidTint(spec, shape)
                drawKitRim(spec, shape, rim)
            }
        }
}

/** The local mean's reach (dp), as the options' companion says. */
private const val MEAN_REACH_DP: Float = 36f

/**
 * The local-contrast hold, after the blur: the colour controls; then the local mean of what is behind (the point and two
 * rings of twelve samples, at half and all of `hold.x` px); then the pixel's distance from that mean (RGB, 0-1) kept
 * whole up to `hold.y` and only `hold.z` of the rest beyond, so rows of words and a button's edge sink into the light
 * round them while the light itself (the Aurora, the page's tone, a glow's colour) passes as it is.
 */
private const val LOCAL_HOLD: String =
    """
uniform shader content;
uniform float4 cc;
uniform float4 hold;

float3 controls(float2 p) {
    float4 c = float4(content.eval(p));
    float3 rgb = c.rgb / max(c.a, 0.0001);
    float l = dot(rgb, float3(0.2126, 0.7152, 0.0722));
    return clamp(cc.y * (l + cc.x * (rgb - l)) + (0.5 - 0.5 * cc.y + cc.z), 0.0, 1.0);
}

half4 main(float2 q) {
    float a = float(content.eval(q).a);
    float3 rgb = controls(q);
    float3 m = rgb;
    for (int i = 0; i < 12; i++) {
        float t = 6.2831853 * (float(i) + 0.5) / 12.0;
        float2 d = float2(cos(t), sin(t));
        m += controls(q + d * hold.x);
        m += controls(q + d * (hold.x * 0.5));
    }
    m /= 25.0;
    float3 dv = rgb - m;
    float len = length(dv);
    float held = len > hold.y ? hold.y + (len - hold.y) * hold.z : len;
    float3 outRgb = len > 0.0001 ? m + dv * (held / len) : rgb;
    return half4(float4(clamp(outRgb, 0.0, 1.0) * a, a));
}
"""
