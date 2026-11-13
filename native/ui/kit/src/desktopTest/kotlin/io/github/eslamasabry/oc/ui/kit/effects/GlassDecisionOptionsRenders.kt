@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * Owner 2026-11-13 on the decision panes (sheets, dialogs, menus), "What is this shitty glass?": the page's rows read
 * through the old pane; the 11-13 pane looked like a plastic tray (a band at the rim, then a soft inner rectangle). The
 * first options (one material to the edge) left the rows behind a light menu as grey smudges: the hold only pulled
 * light DOWN past a knee, so dark text behind a light pane passed at its own tone. Round 2, two options, both one
 * material to the edge with the crafted 1 dp rim:
 * - A2: blur 24 dp, the shipped tint, a TWO-WAY hold;
 * - C2: blur 16 dp, the tint +.15, the same two-way hold.
 * The two-way hold: the content's luminance is pulled toward the page ground's own (the pane's base over an empty page)
 * from above AND below: within [FREE] of the base it passes, beyond it only [SLOPE] of the rest is kept; the colour that
 * is left is limited to [CHROMA] as before. Drawn through a desktop stand-in of Android's pane (colour controls, blur on
 * a padded layer as the library pads it, the hold, the tint, the rim) on real kit content: a chat reply with a code block
 * and a permission card behind a sheet with the permission's details, and a conversation list behind a row's context
 * menu; Glass dark and light, 1080 px wide. Measured with the pane's own words left out: blurred by 8 px, how far any
 * patch of the pane's inside differs from its mean luminance (target about 2-3 %); and the stripe scenes'
 * hidden-edge steps of [ForegroundGhostTest]. Not a test: it runs only when `GLASS_OPTIONS_OUT` names a folder.
 */
class GlassDecisionOptionsRenders {
    private val options =
        listOf(
            Option("A2", 24f, 0f, FREE, SLOPE),
            Option("C2", 16f, .15f, FREE, SLOPE),
        )

    private class OptionPlatform(val option: Option, val seen: MutableList<String>) : KitPlatform by DesktopKitPlatform() {
        override val liquidGlass: Boolean = true

        /** The page ground's colour, set by the scene: the two-way hold's base. */
        var ground: Color = Color.Black

        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
            val s =
                spec.copy(
                    recipe = spec.recipe.copy(blurDp = option.blurDp),
                    fill = spec.fill.copy(alpha = min(1f, spec.fill.alpha + option.tintPlus)),
                    fillStart = spec.fillStart.copy(alpha = min(1f, spec.fillStart.alpha + option.tintPlus)),
                )
            seen += "fill %.2f, blur %.0f dp, two-way hold: free %.3f, slope %.2f, colour %.2f".format(s.fill.alpha, s.recipe.blurDp, option.free, option.slope, CHROMA)
            return twoWayStandIn(s, backdrop, shape, this, option) { ground }
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
    private fun RealScene(kind: Real, platform: OptionPlatform, words: Boolean = true) {
        val page = rememberKitLiquidSource()
        val colors = LocalKitColors.current
        platform.ground = colors.ground
        CompositionLocalProvider(LocalKitLiquidPage provides page) {
            Box(Modifier.size(W.dp, REAL_H.dp).background(colors.ground).testTag("scene")) {
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

    /** The pane's box in dp: left, top, right, bottom. */
    private fun paneBox(kind: Real): FloatArray =
        if (kind == Real.Sheet) floatArrayOf(0f, REAL_H - SHEET_H, W, REAL_H) else floatArrayOf(120f, 250f, 360f, 460f)

    private fun render(height: Float, dark: Boolean, platform: KitPlatform, content: @Composable () -> Unit): ImageBitmap {
        lateinit var out: ImageBitmap
        runDesktopComposeUiTest((W * D).roundToInt(), (height * D).roundToInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(
                        appearance = KitAppearance(style = Style.Glass, mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light),
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
            val img = render(REAL_H, dark, platform) { RealScene(kind, platform) }
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
            val bareOn = OptionPlatform(option, mutableListOf())
            val bare = render(REAL_H, dark, bareOn) { RealScene(kind, bareOn, words = false) }.toPixelMap()
            val (spread, x, y) = patchSpread(bare, box)
            notes.append("$name: ${seen.distinct().joinToString("; ")}; without its words, blurred 8 px: the worst patch is %.1f %% from the pane's mean, at (%d, %d) px\n".format(spread, x, y))
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

    // ---- the hard test: stripes and a button, as ForegroundGhostTest draws them ----

    private enum class Kind { Menu, Sheet }

    private enum class Content { Empty, Button, Rows }

    private class Place(val x: Float, val y: Float, val w: Float, val h: Float) {
        val cx get() = x + w / 2f
        val cy get() = y + h / 2f
    }

    private fun place(kind: Kind): Place = if (kind == Kind.Menu) Place(60f, 140f, 260f, 190f) else Place(0f, 300f, W, 260f)

    @Composable
    private fun StripeScene(kind: Kind, content: Content, platform: OptionPlatform) {
        val page = rememberKitLiquidSource()
        val colors = LocalKitColors.current
        platform.ground = colors.ground
        CompositionLocalProvider(LocalKitLiquidPage provides page) {
            Box(Modifier.size(W.dp, STRIPE_H.dp).background(colors.ground).testTag("scene")) {
                val p = place(kind)
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
                val face = if (kind == Kind.Menu) kitLiquidMenuFace(shape, { 0f }) else kitLiquidSheetFace(shape, { 0f })
                Box(Modifier.offset(p.x.dp, p.y.dp).size(p.w.dp, p.h.dp).then(face ?: Modifier.background(Color.Magenta)))
            }
        }
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
    fun `decision options on stripes - how far a hidden edge steps, and the worst patch`() {
        val out = System.getenv("GLASS_OPTIONS_OUT")?.let(::File) ?: return
        out.mkdirs()
        val sweep = options + Option("C2 slope .10", 16f, .15f, FREE, .10f) + Option("A2 at 16 dp, shipped tint", 16f, 0f, FREE, SLOPE)
        val table = StringBuilder("option | pane | mode | rows step light/colour | button step light/colour | worst patch rows / button (%, blurred 8 px) (limits 12 / 18; ~2-3 %)\n")
        for (option in sweep) for (kind in Kind.entries) for (dark in listOf(true, false)) {
            fun shot(content: Content): PixelMap {
                val platform = OptionPlatform(option, mutableListOf())
                return render(STRIPE_H, dark, platform) { StripeScene(kind, content, platform) }.toPixelMap()
            }
            val empty = shot(Content.Empty)
            val rowsImg = shot(Content.Rows)
            val buttonImg = shot(Content.Button)
            val rows = ghost(rowsImg, empty, kind)
            val button = ghost(buttonImg, empty, kind)
            val p = place(kind)
            val box = floatArrayOf(p.x, p.y, p.x + p.w, p.y + p.h)
            table.append(
                "%s | %s | %s | %.1f / %.1f | %.1f / %.1f | %.1f / %.1f\n".format(
                    option.name, kind, if (dark) "dark" else "light", rows.first, rows.second, button.first, button.second,
                    patchSpread(rowsImg, box).first, patchSpread(buttonImg, box).first,
                ),
            )
        }
        File(out, "options-stripes.txt").writeText(table.toString())
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

        /** The two-way hold: the luminance band round the base that passes as it is, and the share kept beyond it. */
        const val FREE = .012f
        const val SLOPE = .05f

        /** The most colour that is left (as LiquidEdge.HOLD_CHROMA). */
        const val CHROMA = .10f

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

/** One option: the decision pane's blur, its tint's extra alpha, and its two-way hold (free band, slope). */
private data class Option(val name: String, val blurDp: Float, val tintPlus: Float, val free: Float, val slope: Float)

/**
 * The decision pane as Android would draw it under [o]: the backdrop recorded on a layer padded by the blur, as the
 * library pads it, so the blur never clamps at the pane's own edge; colour controls, blur and the two-way hold in one
 * pass ([TWO_WAY_HOLD]), the same everywhere inside the outline; then the tint and the 1 dp rim light.
 */
private fun twoWayStandIn(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, platform: KitPlatform, o: Option, ground: () -> Color): Modifier {
    val at = arrayOfNulls<LayoutCoordinates>(1)
    val program = platform.liveShaders?.compile(TWO_WAY_HOLD)
    val rim = KitRimShader.programFor(platform.liveShaders)
    return Modifier.onGloballyPositioned { at[0] = it }
        .clip(shape)
        .drawWithCache {
            val layer = obtainGraphicsLayer()
            val r = spec.recipe.blurDp.dp.toPx()
            val pad = ceil(r + 2f)
            onDrawBehind {
                val c = at[0] ?: return@onDrawBehind
                val p = program ?: return@onDrawBehind
                val recipe = spec.recipe
                val g = ground()
                // The base: the ground after the colour controls (a grey ground: saturation leaves it, contrast and lift move it).
                val gl = .2126f * g.red + .7152f * g.green + .0722f * g.blue
                val base = (recipe.contrast * gl + (.5f - .5f * recipe.contrast + recipe.brightness)).coerceIn(0f, 1f)
                p.float4("cc", recipe.saturation, recipe.contrast, recipe.brightness, 0f)
                p.float4("hold", base, o.free, o.slope, .10f)
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

/**
 * The two-way hold, after the blur: the colour controls, then the luminance pulled toward `hold.x` (the base) from above
 * and from below (within `hold.y` it passes; beyond it `hold.z` of the rest is kept), moved as a shift of all three
 * channels so the hue stays; then the chroma left eased to `hold.w`, as LiquidEdge.heldRgb limits it.
 */
private const val TWO_WAY_HOLD: String =
    """
uniform shader content;
uniform float4 cc;
uniform float4 hold;

half4 main(float2 q) {
    float4 c = float4(content.eval(q));
    float a = max(c.a, 0.0001);
    float3 rgb = c.rgb / a;
    float3 w = float3(0.2126, 0.7152, 0.0722);
    float l = dot(rgb, w);
    rgb = clamp(cc.y * (l + cc.x * (rgb - l)) + (0.5 - 0.5 * cc.y + cc.z), 0.0, 1.0);
    float lum = dot(rgb, w);
    float d = lum - hold.x;
    float ad = abs(d);
    float held = ad > hold.y ? hold.y + (ad - hold.y) * hold.z : ad;
    rgb = rgb + (hold.x + sign(d) * held - lum);
    float mx = max(rgb.r, max(rgb.g, rgb.b));
    float mn = min(rgb.r, min(rgb.g, rgb.b));
    float chroma = mx - mn;
    float easy = 0.4 * hold.w;
    float f = (hold.w > 0.0 && chroma > easy) ? (easy + (hold.w - easy) * (1.0 - exp(-(chroma - easy) / (hold.w - easy)))) / chroma : 1.0;
    float g = dot(rgb, w);
    rgb = clamp(g + (rgb - g) * f, 0.0, 1.0);
    return half4(float4(rgb * c.a, c.a));
}
"""
