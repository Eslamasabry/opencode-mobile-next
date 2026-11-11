@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidClaim
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.action.KitFab
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBudget
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidBudget
import io.github.eslamasabry.oc.ui.kit.effects.kitGlassSource
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidFace
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Glass in the kit, step 4 (docs/designs/glass-in-the-kit/STEP4-PLAN.md): with [KitFloatingBar] the body of a page runs
 * under a floating liquid bar. At rest nothing moves from where it was; scrolled, the content passes under the pills,
 * the capsule and the status bar; without the lens (every other style, reduced motion, the Frosted look, no lens, a
 * short window) the page draws exactly as it did without the option.
 */
class KitFloatingBarTest {
    /** Desktop with the lens and a blur: liquid panes draw their backdrop blurred, as Android's lens does (ChatEdgeATest). */
    private class LensPlatform(private val base: FakeKitPlatform = FakeKitPlatform(frost = true, liquidGlass = true)) :
        KitPlatform by base {
        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
            val at = arrayOfNulls<LayoutCoordinates>(1)
            return Modifier.onGloballyPositioned { at[0] = it }
                .clip(shape)
                .drawWithCache {
                    val layer = obtainGraphicsLayer()
                    val r = LENS_BLUR_DP.dp.toPx()
                    onDrawBehind {
                        val c = at[0] ?: return@onDrawBehind
                        layer.renderEffect = BlurEffect(r, r)
                        layer.record { backdrop.draw(this, c) }
                        drawLayer(layer)
                        drawRect(spec.fill)
                    }
                }
        }
    }

    /** The two pages that use the option: the Conversations home and a Settings page. */
    private enum class Page { Home, Settings }

    /** What a scene was asked to show beyond the rows. */
    private data class Extras(
        val refreshing: Boolean = false,
        val fab: Boolean = false,
        val statusPart: Boolean = false,
        val field: Boolean = false,
        val clickRows: Boolean = false,
        val rows: Int = ROWS,
        val fourChips: Boolean = false,
    )

    /** What the test reads back from a scene. */
    private class Seen {
        var ground: Color = Color.Unspecified
        var budget: KitLiquidBudget? = null
        var scroll: ScrollState? = null
        val clicks = mutableListOf<Int>()
        val focus = FocusRequester()
    }

    /**
     * A page: the bar (a large title, [BAR_DP] tall at least) and on Home the three pills (a status pill and two chips)
     * over [ROWS] bright rows; Home's list is recorded by the feature (its own `KitFrostState`, as the home's list is for
     * its +), Settings' body by the kit. [floating] false lays the page out as it was before the option.
     */
    @Composable
    private fun Scene(page: Page, floating: Boolean, seen: Seen, extras: Extras) {
        CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = STATUS_DP.dp, bottom = NAV_DP.dp)) {
            Box(Modifier.size(W.dp, LocalKitWindow.current.heightDp.dp).testTag("scene")) {
                seen.ground = LocalKitColors.current.ground
                val source = rememberKitFrostState()
                val bar: @Composable () -> Unit = {
                    Box(Modifier.fillMaxWidth().heightIn(min = BAR_DP.dp).padding(horizontal = 16.dp).testTag("bar"), contentAlignment = Alignment.CenterStart) {
                        KitText("Conversations", role = TypeRole.LargeTitle, maxLines = 2)
                    }
                }
                val pills: @Composable () -> Unit = {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp).testTag("pills"), verticalAlignment = Alignment.CenterVertically) {
                        KitStatusPill("This phone", KitConnectionTone.Ok, "This phone, connected", {}, Modifier.testTag("pill-status"))
                        KitChip("All projects", Modifier.testTag("pill-projects"), kind = KitChipKind.Action, opensMenu = true, onClick = {})
                        KitChip("Running", Modifier.testTag("pill-running"), kind = KitChipKind.Action, onClick = {})
                        if (extras.fourChips) KitChip("All sources", kind = KitChipKind.Action, opensMenu = true, onClick = {})
                    }
                }
                val home = page == Page.Home
                val refresh = if (extras.refreshing) KitScreenRefresh(refreshing = true, onRefresh = {}) else null
                KitScreen(
                    page = true,
                    topBar = if (home && !floating) null else bar,
                    header = if (home && !floating) ({ bar(); pills() }) else null,
                    floatingBar =
                        when {
                            !floating -> null
                            home -> KitFloatingBar(scrollsElsewhere = true, source = source, below = pills)
                            else -> KitFloatingBar()
                        },
                    refresh = refresh,
                    edges = KitScreenEdges.Bleed,
                ) {
                    seen.budget = LocalKitLiquidBudget.current
                    Box(Modifier.fillMaxSize()) {
                        if (extras.field) {
                            val scroll = rememberScrollState()
                            seen.scroll = scroll
                            Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("list")) {
                                repeat(extras.rows) { i ->
                                    if (i == FIELD_AT)
                                        Box(Modifier.fillMaxWidth().height(ROW_DP.dp).background(Color.Red).focusRequester(seen.focus).focusable().testTag("field"))
                                    else BrightRow(i)
                                }
                            }
                        } else {
                            val list = rememberLazyListState()
                            LazyColumn(
                                state = list,
                                modifier = Modifier.fillMaxSize().then(if (home) Modifier.kitGlassSource(source) else Modifier).testTag("list"),
                            ) {
                                items(extras.rows) { i -> BrightRow(i, if (extras.clickRows) ({ seen.clicks += i }) else null) }
                            }
                        }
                        if (extras.fab)
                            KitFab("New conversation", KitIcons.add, {}, Modifier.align(Alignment.BottomEnd).padding(16.dp), backdrop = source)
                        // A fourth kind of part over the list: a status line (the Undo bar's role) asking for the lens.
                        if (extras.statusPart) {
                            val face = kitLiquidFace(LiquidRole.Status, source, RoundedCornerShape(16.dp))
                            Box(Modifier.align(Alignment.BottomStart).padding(16.dp).size(120.dp, 40.dp).then(face ?: Modifier))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun BrightRow(i: Int, onClick: (() -> Unit)? = null) {
        Box(
            Modifier.fillMaxWidth().height(ROW_DP.dp).padding(bottom = GAP_DP.dp).background(Color.White)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .testTag("row-$i")
        )
    }

    private class Shot(val image: PixelMap, val bounds: Map<String, DpRect?>)

    private val tags = listOf("bar", "pills", "pill-status", "pill-projects", "pill-running", "row-0", "kit-liquid-bar", "kit-refresh", "field")

    private fun shot(
        page: Page,
        floating: Boolean,
        appearance: KitAppearance = dark(Style.Glass),
        platform: KitPlatform = LensPlatform(),
        extras: Extras = Extras(),
        heightDp: Float = H.toFloat(),
        fontScale: Float = 1f,
        locale: String = "en",
        act: ComposeUiTest.(Seen) -> Unit = {},
        check: (Seen) -> Unit = {},
    ): Shot {
        lateinit var shot: Shot
        val seen = Seen()
        runDesktopComposeUiTest((W * D).toInt(), (heightDp * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D, fontScale)) {
                    KitTheme(appearance = appearance, platform = platform, localeTag = locale, stillFrame = true) {
                        CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(W.toFloat(), heightDp)) {
                            Scene(page, floating, seen, extras)
                        }
                    }
                }
            }
            waitForIdle()
            act(seen)
            waitForIdle()
            val bounds = tags.associateWith { tag -> runCatching { onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot() }.getOrNull() }
            shot = Shot(onNodeWithTag("scene").captureToImage().toPixelMap(), bounds)
            check(seen)
        }
        return shot
    }

    /** A slow drag of [dyDp] (negative: the content moves up), held still before the finger lifts, so nothing flings. */
    private fun ComposeUiTest.drag(dyDp: Float) {
        onNodeWithTag("scene").performTouchInput {
            val total = dyDp * D
            down(Offset(width / 2f, if (total < 0) height * 0.8f else height * 0.25f))
            moveBy(Offset(0f, viewConfiguration.touchSlop * sign(total)))
            repeat(STEPS) {
                advanceEventTime(16)
                moveBy(Offset(0f, total / STEPS))
            }
            advanceEventTime(200)
            up()
        }
        waitForIdle()
    }

    private fun dark(style: Style, look: GlassLookId = GlassLookId.Glossy, motion: MotionLevel = MotionLevel.Full) =
        KitAppearance(style = style, mode = AppearanceMode.Dark, glassLook = look, motion = motion)

    private fun luma(c: Color): Float = (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue) * 255f

    /** Brightness down column [xDp] from [fromDp] to [toDp]: the mean and the steepest row-to-row step. */
    private fun column(image: PixelMap, xDp: Float, fromDp: Float, toDp: Float): Pair<Float, Float> {
        val x = (xDp * D).roundToInt().coerceIn(0, image.width - 1)
        val ys = ((fromDp * D).roundToInt() until (toDp * D).roundToInt()).filter { it in 0 until image.height }
        val l = ys.map { luma(image[x, it]) }
        return l.average().toFloat() to (l.zipWithNext { a, b -> abs(a - b) }.maxOrNull() ?: 0f)
    }

    /** Writes [image] to FLOAT_DEBUG_DIR when it is set, to look at a failure. */
    private fun debugPng(image: PixelMap, name: String) {
        val dir = System.getenv("FLOAT_DEBUG_DIR")?.let { File(it) } ?: return
        dir.mkdirs()
        val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) for (x in 0 until image.width) out.setRGB(x, y, image[x, y].toArgb())
        ImageIO.write(out, "png", File(dir, "$name.png"))
    }

    /** The steps over 24 down a column, as "y dp: from → to", for a failure message. */
    private fun stepsAt(image: PixelMap, xDp: Float, fromDp: Float, toDp: Float): String {
        val x = (xDp * D).roundToInt()
        val ys = ((fromDp * D).roundToInt() until (toDp * D).roundToInt())
        return ys.zipWithNext().filter { (a, b) -> abs(luma(image[x, a]) - luma(image[x, b])) > BLUR_STEP }
            .joinToString { (a, b) -> "${a / D}: ${luma(image[x, a]).toInt()}→${luma(image[x, b]).toInt()}" }
    }

    /** The pane's room under its second row: Home has one, Settings none. */
    private fun roomBelow(page: Page): Float = if (page == Page.Home) FloatingPaneRow.ROOM_BELOW_DP.toFloat() else 0f

    /** The bottom of the floating chrome: the pills on Home, the bar on Settings. */
    private fun chromeBottom(s: Shot, page: Page) = (if (page == Page.Home) s.bounds["pills"] else s.bounds["bar"])!!.bottom.value

    private fun assertSamePixels(a: PixelMap, b: PixelMap, what: String) {
        assertEquals(a.width to a.height, b.width to b.height, "$what: size")
        var diff = 0
        var first: Pair<Int, Int>? = null
        for (y in 0 until a.height) for (x in 0 until a.width) if (a[x, y] != b[x, y]) {
            diff++
            if (first == null) first = x to y
        }
        assertEquals(0, diff, "$what: $diff pixels differ with the option, the first at $first")
    }

    @Test
    fun `B-KIT-FLOAT-001 at rest the first row rests right below the bar and pills, where it rested before`() {
        for (page in Page.entries) {
            val float = shot(page, floating = true)
            val before = shot(page, floating = false, platform = FakeKitPlatform())
            val row = float.bounds["row-0"]!!
            val bottom = chromeBottom(float, page)
            // The loading line's room (it always keeps its 2 dp) lies between them, as before.
            // Home's pane keeps room under its chips row (FloatingPaneRow.ROOM_BELOW_DP, the owner's first phone look):
            // the first row rests that much lower than without the option.
            val room = roomBelow(page)
            assertTrue(row.top.value >= bottom + room - .5f, "$page: the first row $row starts under the chrome ending at $bottom")
            assertTrue(row.top.value <= bottom + room + LOADING_BAR_DP + .5f, "$page: the first row $row rests right below the chrome ending at $bottom")
            assertTrue(abs(row.top.value - (before.bounds["row-0"]!!.top.value + room)) <= .5f, "$page: rests where it did plus the pane's room, ${before.bounds["row-0"]} then, $row now")
            assertTrue(float.bounds["kit-liquid-bar"] != null, "$page: the bar is a liquid capsule")
            // One pane holds the bar and (Home) the chips row: from the bar's top to the chrome's foot, 4 dp in.
            val capsule = float.bounds["kit-liquid-bar"]!!
            val bar = float.bounds["bar"]!!
            assertTrue(abs(capsule.top.value - (bar.top.value + 4f)) <= .5f, "$page: the pane $capsule starts 4 dp into the bar $bar")
            assertTrue(abs(capsule.bottom.value - (bottom + room - 4f)) <= .5f, "$page: the pane $capsule ends 4 dp above the chrome's foot $bottom and its room $room")
        }
    }

    @Test
    fun `B-KIT-FLOAT-002 scrolled, the content passes under the bar and is blurred and cleared under the status bar`() {
        for (page in Page.entries) {
            var ground = Color.Unspecified
            // About 120 dp past the spacer's resting height (Home's pane keeps its room under the chips too).
            val s = shot(page, floating = true, act = { drag(-120f - roomBelow(page)) }, check = { ground = it.ground })
            val g = luma(ground)
            // Under the status bar, above where the edge's dissolve starts: blur only, no sharp row edge.
            val (statusMean, statusStep) = column(s.image, W / 2f, 1f, STATUS_DP * STATUS_EDGE_SOLID_SHARE - 1f)
            assertTrue(statusMean >= g + 20f, "$page: content under the status bar, mean $statusMean over ground $g")
            assertTrue(statusStep <= BLUR_STEP, "$page: cleared to a blur under the status bar, a step of $statusStep")
            // Inside the pane, beside the title past its words: the rows show through its glass, blurred.
            val capsule = s.bounds["kit-liquid-bar"]!!
            val bar = s.bounds["bar"]!!
            debugPng(s.image, "scrolled-$page")
            val inside = capsule.right.value - 24f
            val (capsuleMean, capsuleStep) = column(s.image, inside, capsule.top.value + 8f, bar.bottom.value - 8f)
            println("$page pane $capsule, column at $inside: " + stepsAt(s.image, inside, capsule.top.value + 8f, bar.bottom.value - 8f))
            assertTrue(capsuleMean >= g + 15f, "$page: content behind the capsule, mean $capsuleMean over ground $g")
            assertTrue(capsuleStep <= BLUR_STEP, "$page: blurred behind the capsule, a step of $capsuleStep")
            // Beside the pane (its 6 dp margin), below the status edge's blur and above the pane's bottom edge: nothing of
            // the page shows there but its ground (one body: content shows above that edge only through the glass).
            val sideFrom = STATUS_DP + STATUS_EDGE_REACH_DP + 1f
            val (sideMean, sideStep) = column(s.image, 2f, sideFrom, capsule.bottom.value - 1f)
            assertTrue(abs(sideMean - g) <= 3f && sideStep <= 3f, "$page: cleared beside the pane, mean $sideMean over ground $g, step $sideStep: " + stepsAt(s.image, 2f, sideFrom, capsule.bottom.value - 1f))
            // Below the pane's bottom edge the rows run on sharp: they really pass under it.
            val (_, belowStep) = column(s.image, 2f, capsule.bottom.value + 1f, capsule.bottom.value + 80f)
            assertTrue(belowStep >= 100f, "$page: the rows run on below the pane, steepest step $belowStep")
            println("$page scrolled: status mean $statusMean step $statusStep, capsule mean $capsuleMean step $capsuleStep, ground $g")
        }
    }

    @Test
    fun `B-KIT-FLOAT-003 dragging back down brings the first row back to its resting place`() {
        for (page in Page.entries) {
            val rest = shot(page, floating = true).bounds["row-0"]!!
            val back = shot(page, floating = true, act = { drag(-200f); drag(400f) }).bounds["row-0"]!!
            assertTrue(abs(back.top.value - rest.top.value) <= .5f, "$page: back at rest $back, was $rest")
        }
    }

    @Test
    fun `B-KIT-FLOAT-004 the refresh disc comes out below the floating bar, not under it`() {
        // The disc moves by a layer translation, which a node's bounds do not show: find it as the pixels that differ
        // between the page refreshing and the page at rest, with no rows to hide it.
        val on = shot(Page.Home, floating = true, extras = Extras(refreshing = true, rows = 0))
        val off = shot(Page.Home, floating = true, extras = Extras(rows = 0))
        var top = Int.MAX_VALUE
        for (y in 0 until on.image.height) for (x in 0 until on.image.width) if (on.image[x, y] != off.image[x, y]) top = minOf(top, y)
        assertTrue(top != Int.MAX_VALUE, "the refreshing disc shows")
        val bottom = chromeBottom(on, Page.Home)
        assertTrue(top / D >= bottom - .5f, "the refreshing disc's top at ${top / D} dp lies below the pills ending at $bottom")
    }

    @Test
    fun `B-KIT-FLOAT-005 home's lens budget is the bar's one pane and the +, whatever chips sit on the pane`() {
        shot(
            Page.Home,
            floating = true,
            extras = Extras(fab = true, fourChips = true),
            check = { seen ->
                val budget = seen.budget!!
                val claims = budget.claims.toList()
                val granted = budget.granted
                // Four chips and the status pill sit on the pane as controls: they ask for no lens.
                assertEquals(listOf(LiquidRole.Action, LiquidRole.Bar), claims.map { it.role }.sortedBy { it.rank }, "claims")
                assertTrue(claims.all { it.key in granted }, "both granted")
            },
        )
        // As LiquidEdge counts it: the list's recording is the feature's (free), so the page spends 2 of its 5.
        val claims = listOf(LiquidRole.Bar, LiquidRole.Action).mapIndexed { i, role -> LiquidClaim(i, role, source = null, order = i) }
        assertEquals(setOf(0, 1), LiquidEdge.grant(claims))
    }

    @Test
    fun `B-KIT-FLOAT-006 every other style and Glass without a lens draw the same pixels with and without the option`() {
        val cases =
            Style.entries.filter { it != Style.Glass }.map { "$it" to (dark(it) to (FakeKitPlatform() as KitPlatform)) } +
                listOf(
                    "Glass, no lens" to (dark(Style.Glass) to FakeKitPlatform(frost = true)),
                    "Glass, reduced motion" to (dark(Style.Glass, motion = MotionLevel.Off) to LensPlatform()),
                    "Glass, Frosted look" to (dark(Style.Glass, GlassLookId.Frosted) to LensPlatform()),
                )
        for ((what, look) in cases) for (page in Page.entries) {
            val with = shot(page, floating = true, appearance = look.first, platform = look.second)
            val without = shot(page, floating = false, appearance = look.first, platform = look.second)
            assertSamePixels(without.image, with.image, "$what, $page")
        }
    }

    @Test
    fun `B-KIT-FLOAT-007 a short window keeps the page as it was`() {
        for (page in Page.entries) {
            val with = shot(page, floating = true, heightDp = SHORT_H)
            val without = shot(page, floating = false, heightDp = SHORT_H)
            assertTrue(with.bounds["kit-liquid-bar"] == null, "$page: no floating capsule on a short window")
            assertSamePixels(without.image, with.image, "short window, $page")
        }
    }

    @Test
    fun `B-KIT-FLOAT-008 right to left the pills run from the right and the capsule stays centred`() {
        val ltr = shot(Page.Home, floating = true)
        val rtl = shot(Page.Home, floating = true, locale = "ar")
        val status = rtl.bounds["pill-status"]!!
        val projects = rtl.bounds["pill-projects"]!!
        assertTrue(status.left > projects.left, "RTL: the status pill $status leads from the right, before $projects")
        val capsule = rtl.bounds["kit-liquid-bar"]!!
        assertTrue(abs(capsule.left.value - (W - capsule.right.value)) <= .5f, "RTL: the capsule $capsule sits centred")
        assertTrue(abs(rtl.bounds["row-0"]!!.top.value - ltr.bounds["row-0"]!!.top.value) <= .5f, "RTL: the first row rests where it does LTR")
    }

    @Test
    fun `B-KIT-FLOAT-009 at text size 2 the bar and pills grow, stay apart and the first row still rests below them`() {
        for (page in Page.entries) {
            val s = shot(page, floating = true, fontScale = 2f, heightDp = TALL_H)
            val capsule = s.bounds["kit-liquid-bar"]!!
            val bar = s.bounds["bar"]!!
            val bottom = chromeBottom(s, page)
            val row = s.bounds["row-0"]!!
            assertTrue(capsule.top >= bar.top - .5.dp && capsule.top <= bar.top + 4.5.dp, "$page: the pane $capsule starts at the bar $bar")
            assertTrue(capsule.bottom.value >= bottom - 4.5f, "$page: the pane $capsule reaches the chrome's foot $bottom")
            val room = roomBelow(page)
            assertTrue(row.top.value >= bottom + room - .5f && row.top.value <= bottom + room + LOADING_BAR_DP + .5f, "$page: the first row $row rests right below $bottom and the pane's room $room")
            // The pinned chrome keeps most of the window for the body.
            println("$page at text size 2: the floating chrome ends at $bottom of $TALL_H dp")
            assertTrue(bottom <= TALL_H * .4f, "$page: the floating chrome ends at $bottom of $TALL_H dp")
        }
    }

    @Test
    fun `B-KIT-FLOAT-010 a part brought into view on a floating page rests below the bar, never under it`() {
        val s =
            shot(
                Page.Settings,
                floating = true,
                extras = Extras(field = true),
                act = { seen ->
                    // Scroll the spacer away and on, then put the field just under the status bar, behind the bar.
                    drag(-200f)
                    val field = onNodeWithTag("field").getBoundsInRoot()
                    val target = STATUS_DP + 8f
                    runOnIdle { seen.scroll!!.dispatchRawDelta((field.top.value - target) * D) }
                    waitForIdle()
                    val placed = onNodeWithTag("field").getBoundsInRoot()
                    assertTrue(abs(placed.top.value - target) <= 1f, "the field was placed under the bar at $placed")
                    runOnIdle { seen.focus.requestFocus() }
                },
            )
        val field = s.bounds["field"]!!
        val bar = s.bounds["bar"]!!
        assertTrue(field.top >= bar.bottom - .5.dp, "the focused field $field rests below the bar $bar")
        assertTrue(field.bottom.value <= H, "the focused field $field stays in the window")
    }

    @Test
    fun `B-KIT-FLOAT-012 a tap on the floating chrome never reaches a row scrolled under it`() {
        shot(
            Page.Home,
            floating = true,
            extras = Extras(clickRows = true),
            act = { seen ->
                drag(-120f)
                val capsule = onNodeWithTag("kit-liquid-bar", useUnmergedTree = true).getBoundsInRoot()
                val pills = onNodeWithTag("pills", useUnmergedTree = true).getBoundsInRoot()
                onNodeWithTag("scene").performTouchInput {
                    // The capsule's middle (the title's empty end) and the gutter beside the pills: rows lie under both.
                    click(Offset(capsule.right.toPx() - 24.dp.toPx(), ((capsule.top + capsule.bottom) / 2).toPx()))
                    click(Offset(4.dp.toPx(), ((pills.top + pills.bottom) / 2).toPx()))
                }
                waitForIdle()
                assertEquals(emptyList(), seen.clicks.toList(), "no row under the chrome was tapped")
                onNodeWithTag("scene").performTouchInput { click(Offset(width / 2f, height * 0.75f)) }
                waitForIdle()
                assertEquals(1, seen.clicks.size, "a row below the chrome still takes its tap")
            },
        )
    }

    @Test
    fun `B-KIT-FLOAT-011 a part brought into view under the bar brings the whole spacer back`() {
        // Chrome 160 px tall, the header 160 px scrolled away: a part 60 px down lies under the chrome, so all of the
        // header comes back; a part below the chrome, or a header already at rest, changes nothing.
        assertEquals(160f, floatingBringBack(top = 60f, chromePx = 160, offset = -160f))
        assertEquals(0f, floatingBringBack(top = 170f, chromePx = 160, offset = -160f))
        assertEquals(0f, floatingBringBack(top = 60f, chromePx = 160, offset = 0f))
        assertEquals(40f, floatingBringBack(top = -10f, chromePx = 160, offset = -40f))
    }

    private companion object {
        const val W = 360
        const val H = 640
        const val SHORT_H = 400f
        const val TALL_H = 800f
        const val D = 2f
        const val STATUS_DP = 24
        const val NAV_DP = 16
        const val BAR_DP = 56
        const val ROW_DP = 48
        const val GAP_DP = 8
        const val ROWS = 40
        const val FIELD_AT = 14
        const val STEPS = 20
        const val LENS_BLUR_DP = 6f

        /** The loading line's room, kept even when it shows nothing (KitLoadingBar). */
        const val LOADING_BAR_DP = 2f

        /** The most brightness may step between two rows where the content is blurred (a sharp row edge is ~200). */
        const val BLUR_STEP = 24f
    }
}
