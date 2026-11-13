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
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test

/**
 * Owner 2026-11-13 on the decision panes (sheets, dialogs, menus), "What is this shitty glass?": the page's rows read
 * through the old pane; the 11-13 pane looked like a plastic tray (a band at the rim where the page showed held less,
 * then a soft inner rectangle where the hold began). Options for the owner to pick from, each ONE material to the edge
 * (no band, no second shape), the crafted 1 dp rim light kept:
 * - A: uniform frost to the edge, the shipped tint, the light held (knee .08, slope .08, colour .10), no lens;
 * - B: A with a 3 dp lens at the edge (it shows up to 6 dp of what lies just beyond the edge);
 * - C: A with a denser tint (+.15) and less blur (calmer, more solid).
 * Drawn through a desktop stand-in of Android's pane (colour controls, blur on a padded layer as the library pads it,
 * the hold, the tint, the rim), on real kit content: a chat reply with a code block and a permission card behind a sheet
 * with the permission's details, and a conversation list behind a row's context menu; Glass dark and light, 1080 px wide.
 * Also, on the stripe scenes of [ForegroundGhostTest], how far a hidden edge steps through each blur and tint.
 * Not a test: it runs only when `GLASS_OPTIONS_OUT` names a folder.
 */
class GlassDecisionOptionsRenders {
    private val options =
        listOf(
            Option("now", 40f, 0f, rimBand = true),
            Option("A", 24f, 0f),
            Option("B", 24f, 0f, lensDp = 3f, shiftDp = 6f),
            Option("C", 16f, .15f),
        )

    private class OptionPlatform(val option: Option, val seen: MutableList<String>) : KitPlatform by DesktopKitPlatform() {
        override val liquidGlass: Boolean = true

        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
            val s =
                spec.copy(
                    recipe = spec.recipe.copy(blurDp = option.blurDp),
                    fill = spec.fill.copy(alpha = min(1f, spec.fill.alpha + option.tintPlus)),
                    fillStart = spec.fillStart.copy(alpha = min(1f, spec.fillStart.alpha + option.tintPlus)),
                )
            seen += "fill %.2f, start %.2f, blur %.0f dp, knee %.2f slope %.2f colour %.2f".format(
                s.fill.alpha, s.fillStart.alpha, s.recipe.blurDp, s.recipe.hold.kneeAt, s.recipe.hold.kneeSlope, s.recipe.hold.chroma,
            )
            return optionStandIn(s, backdrop, shape, this, option)
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
    private fun RealScene(kind: Real) {
        val page = rememberKitLiquidSource()
        val colors = LocalKitColors.current
        CompositionLocalProvider(LocalKitLiquidPage provides page) {
            Box(Modifier.size(W.dp, REAL_H.dp).background(colors.ground).testTag("scene")) {
                Box(Modifier.fillMaxSize().kitLiquidSource(page)) { if (kind == Real.Sheet) ChatPage() else ListPage() }
                if (kind == Real.Sheet) {
                    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    val face = kitLiquidSheetFace(shape, { 0f })
                    Box(Modifier.offset(0.dp, (REAL_H - SHEET_H).dp).size(W.dp, SHEET_H.dp).then(face ?: Modifier.background(Color.Magenta))) {
                        CompositionLocalProvider(LocalKitOnLiquidGlass provides true) { SheetDetails() }
                    }
                } else {
                    val shape = RoundedCornerShape(20.dp)
                    val face = kitLiquidMenuFace(shape, { 0f })
                    Box(Modifier.offset(120.dp, 250.dp).size(240.dp, 210.dp).then(face ?: Modifier.background(Color.Magenta))) {
                        CompositionLocalProvider(LocalKitOnLiquidGlass provides true) { MenuItems() }
                    }
                }
            }
        }
    }

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

    @Test
    fun `decision options on real content`() {
        val out = System.getenv("GLASS_OPTIONS_OUT")?.let(::File) ?: return
        out.mkdirs()
        val notes = StringBuilder()
        for (option in options) for (kind in Real.entries) for (dark in listOf(true, false)) {
            val seen = mutableListOf<String>()
            val img = render(REAL_H, dark, OptionPlatform(option, seen)) { RealScene(kind) }
            val mode = if (dark) "dark" else "light"
            val name = "opt-${option.name}-${if (kind == Real.Sheet) "chat-sheet" else "list-menu"}-$mode"
            ImageIO.write(img.toAwtImage(), "png", File(out, "$name.png"))
            notes.append("$name: ${seen.distinct().joinToString("; ")}; lens ${option.lensDp} dp band, ${option.shiftDp} dp shift; rim band ${option.rimBand}\n")
        }
        File(out, "options-notes.txt").writeText(notes.toString())
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
    private fun StripeScene(kind: Kind, content: Content) {
        val page = rememberKitLiquidSource()
        val colors = LocalKitColors.current
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

    private fun linear(v: Float): Double = if (v <= .04045f) v / 12.92 else ((v + .055) / 1.055).pow(2.4)

    private fun relative(c: Color): Double = .2126 * linear(c.red) + .7152 * linear(c.green) + .0722 * linear(c.blue)

    private fun worstContrast(img: PixelMap, kind: Kind, ink: Color): Double {
        val p = place(kind)
        var least = 21.0
        for (y in ((p.y + INSET_DP) * D).roundToInt() until ((p.y + p.h - INSET_DP) * D).roundToInt() step 3) {
            for (x in ((p.x + INSET_DP) * D).roundToInt() until ((p.x + p.w - INSET_DP) * D).roundToInt() step 3) {
                val a = relative(ink)
                val b = relative(img[x, y])
                least = min(least, (max(a, b) + .05) / (min(a, b) + .05))
            }
        }
        return least
    }

    @Test
    fun `decision options on stripes - how far a hidden edge steps per blur and tint`() {
        val out = System.getenv("GLASS_OPTIONS_OUT")?.let(::File) ?: return
        out.mkdirs()
        val sweep =
            listOf(12f, 16f, 20f, 24f, 32f).map { Option("blur ${it.toInt()} tint +0", it, 0f) } +
                listOf(12f, 16f).map { Option("blur ${it.toInt()} tint +.15", it, .15f) } +
                Option("now (40, rim band)", 40f, 0f, rimBand = true) + Option("B lens 3/6 at 24", 24f, 0f, 3f, 6f)
        val table = StringBuilder("option | pane | mode | rows step light/colour | button step light/colour | first ink worst contrast over the button (limits 12 / 18, 4.5)\n")
        for (option in sweep) for (kind in Kind.entries) for (dark in listOf(true, false)) {
            var ink = Color.Unspecified
            fun shot(content: Content): PixelMap =
                render(STRIPE_H, dark, OptionPlatform(option, mutableListOf())) {
                    ink = LocalKitColors.current.text1
                    StripeScene(kind, content)
                }.toPixelMap()
            val empty = shot(Content.Empty)
            val rows = ghost(shot(Content.Rows), empty, kind)
            val buttonImg = shot(Content.Button)
            val button = ghost(buttonImg, empty, kind)
            val words = worstContrast(buttonImg, kind, ink)
            val ok = rows.first <= 12f && rows.second <= 18f && button.first <= 12f && button.second <= 18f && words >= 4.5
            table.append(
                "%s | %s | %s | %.1f / %.1f | %.1f / %.1f | %.2f | %s\n".format(
                    option.name, kind, if (dark) "dark" else "light", rows.first, rows.second, button.first, button.second, words, if (ok) "ok" else "OVER",
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

/** One option: the decision pane's blur, its tint's extra alpha, its lens (band and shift, dp), and a rim band. */
private data class Option(val name: String, val blurDp: Float, val tintPlus: Float, val lensDp: Float = 0f, val shiftDp: Float = 0f, val rimBand: Boolean = false)

/**
 * The decision pane as Android would draw it under [option]: the backdrop recorded on a layer padded by the blur (and
 * the lens's reach) as the library pads it, so the blur never clamps at the pane's own edge; colour controls, blur, an
 * optional lens of [Option.lensDp] (showing up to [Option.shiftDp] of what lies beyond the edge), the hold (to the edge,
 * or eased in over the rim when [Option.rimBand]); then the tint and the 1 dp rim light.
 */
private fun optionStandIn(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, platform: KitPlatform, o: Option): Modifier {
    val at = arrayOfNulls<LayoutCoordinates>(1)
    val program = platform.liveShaders?.compile(LENSED_HOLD)
    val rim = KitRimShader.programFor(platform.liveShaders)
    return Modifier.onGloballyPositioned { at[0] = it }
        .clip(shape)
        .drawWithCache {
            val layer = obtainGraphicsLayer()
            val r = spec.recipe.blurDp.dp.toPx()
            val band = o.lensDp.dp.toPx()
            val shift = o.shiftDp.dp.toPx()
            val pad = ceil(r + shift + 2f)
            onDrawBehind {
                val c = at[0] ?: return@onDrawBehind
                val p = program ?: return@onDrawBehind
                val radius = maxOf(shape.topStart.toPx(size, this), shape.topEnd.toPx(size, this), shape.bottomStart.toPx(size, this), shape.bottomEnd.toPx(size, this))
                val colour = floatArrayOf(spec.recipe.saturation, spec.recipe.contrast, spec.recipe.brightness)
                val u = LiquidHoldUniforms.of(spec, size.width, size.height, pad, pad, radius, density, colour)
                val held = if (o.rimBand) u else LiquidHoldUniforms(u.knee, u.hold, u.left, u.top, u.halfWidth, u.halfHeight, u.radius, 0f, u.saturation, u.contrast, u.brightness)
                held.set(p)
                p.float2("lens", band, shift)
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
 * [LiquidHoldShader] with a lens in front of it: inside a band of `lens.x` px from the outline, the content is read from
 * further out along the outline's normal, up to `lens.y` px at the edge, on a circle's profile (the library's lens shape,
 * narrowed to the band).
 */
private val LENSED_HOLD: String =
    LiquidHoldShader.SOURCE
        .replace(
            "half4 main(float2 q) {",
            """
uniform float2 lens;

float2 lensed(float2 q) {
    if (lens.x <= 0.0) return q;
    float k = clamp(corner.x, 0.0, min(box.z, box.w));
    float2 p = q - (box.xy + box.zw);
    float2 d = abs(p) - box.zw + k;
    float depth = -(length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - k);
    if (depth >= lens.x || depth < 0.0) return q;
    float2 n;
    if (d.x > 0.0 && d.y > 0.0) {
        n = sign(p) * normalize(d);
    } else if (d.x > d.y) {
        n = float2(sign(p.x), 0.0);
    } else {
        n = float2(0.0, sign(p.y));
    }
    float t = 1.0 - depth / lens.x;
    float bend = 1.0 - sqrt(max(1.0 - t * t, 0.0));
    return q + n * lens.y * bend;
}

half4 main(float2 q) {""",
        )
        .replace("float4 c = float4(content.eval(q));", "float4 c = float4(content.eval(lensed(q)));")
