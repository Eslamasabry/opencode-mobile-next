@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Audit 2 (2026-11-14), every page but the conversation: a hard clip about 60 px above the screen's bottom cut the last
 * row's letters, headings were sliced at the bar's lower edge, "Performance" ran into Report a problem's footer, AI
 * Team's line was sliced above its fixed button, and under Glass the page's words showed blurred under the bar and in
 * the status bar. One rule in the kit frame for every page in every style: content never ends in a hard line.
 *
 * Over the frame's variants (a plain page, a fixed button, a pinned foot, a scrolling heading, Settings' floating bar, a
 * body that reports nothing) × Solid, Glass (with the lens, so the bar floats) and Brutal, with tall content of
 * unbroken white rows:
 * 1. no row of pixels where the content jumps from more than half to nothing, at the chrome's edge or the footer's;
 * 2. scrolled, nothing of the content under the status bar or the bar;
 * 3. at rest the first and last rows are whole, and at the end the last row is whole.
 */
class PageEdgesTest {
    private enum class Variant { Plain, FixedButton, PinnedFoot, Heading, Floating, Unreported }

    /** Desktop with the lens and a blur, as in KitFloatingBarTest: the floating bar floats here under Glass. */
    private class LensPlatform(private val base: FakeKitPlatform = FakeKitPlatform(frost = true, liquidGlass = true)) :
        KitPlatform by base {
        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
            val at = arrayOfNulls<LayoutCoordinates>(1)
            return Modifier.onGloballyPositioned { at[0] = it }
                .clip(shape)
                .drawWithCache {
                    val layer = obtainGraphicsLayer()
                    val r = 6.dp.toPx()
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

    private val styles = listOf(Style.Solid, Style.Glass, Style.Brutal)

    @Composable
    private fun Rows(count: Int, paint: Boolean) {
        repeat(count) { i ->
            Box(Modifier.fillMaxWidth().height(ROW_DP.dp).then(if (paint) Modifier.background(Color.White) else Modifier).testTag("row-$i"))
        }
    }

    @Composable
    private fun Scene(variant: Variant, rows: Int, paint: Boolean, hold: (ScrollState) -> Unit) {
        CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = STATUS_DP.dp, bottom = NAV_DP.dp)) {
            Box(Modifier.size(W.dp, H.dp).testTag("scene")) {
                KitScreen(
                    page = true,
                    topBar = { Box(Modifier.fillMaxWidth().height(BAR_DP.dp).testTag("bar")) },
                    header = if (variant == Variant.Heading) ({ Box(Modifier.fillMaxWidth().height(HEADING_DP.dp)) }) else null,
                    bottom = if (variant == Variant.FixedButton) ({ Box(Modifier.fillMaxWidth().height(FOOT_DP.dp).testTag("foot")) }) else null,
                    floatingBar = if (variant == Variant.Floating || variant == Variant.PinnedFoot) KitFloatingBar() else null,
                    edges = KitScreenEdges.Bleed,
                ) {
                    val scroll = rememberScrollState()
                    hold(scroll)
                    when (variant) {
                        Variant.PinnedFoot ->
                            KitPinnedFootBody(scroll = scroll, foot = { Box(Modifier.fillMaxWidth().height(FOOT_DP.dp).testTag("foot")) }) { Rows(rows, paint) }
                        Variant.Unreported -> Column(Modifier.fillMaxSize().verticalScroll(scroll)) { Rows(rows, paint) }
                        else -> {
                            KitReportPageScroll(scroll)
                            Column(Modifier.fillMaxSize().verticalScroll(scroll)) { Rows(rows, paint) }
                        }
                    }
                }
            }
        }
    }

    private class Shot(val image: PixelMap, val bar: DpRect, val foot: DpRect?, val first: DpRect?, val last: DpRect?)

    private enum class At { Rest, Middle, End }

    /** The scene at rest, dragged to the middle and scrolled to its end, in that order. */
    private fun shots(variant: Variant, style: Style, rows: Int, paint: Boolean): Map<At, Shot> {
        val out = mutableMapOf<At, Shot>()
        lateinit var scroll: ScrollState
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(
                        appearance = KitAppearance(style = style, mode = AppearanceMode.Dark),
                        platform = if (style == Style.Glass) LensPlatform() else FakeKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(W.toFloat(), H.toFloat())) {
                            Scene(variant, rows, paint) { scroll = it }
                        }
                    }
                }
            }
            waitForIdle()
            out[At.Rest] = capture(rows)
            drag(-MIDDLE_DP)
            out[At.Middle] = capture(rows)
            runBlocking { scroll.scrollTo(scroll.maxValue) }
            waitForIdle()
            out[At.End] = capture(rows)
        }
        return out
    }

    private fun ComposeUiTest.capture(rows: Int): Shot {
        fun bounds(tag: String) = runCatching { onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot() }.getOrNull()
        return Shot(onNodeWithTag("scene").captureToImage().toPixelMap(), bounds("bar")!!, bounds("foot"), bounds("row-0"), bounds("row-${rows - 1}"))
    }

    /** A slow drag of [dyDp] (negative: the content moves up), held before the finger lifts, so nothing flings. */
    private fun ComposeUiTest.drag(dyDp: Float) {
        onNodeWithTag("scene").performTouchInput {
            val total = dyDp * D
            down(Offset(width / 2f, height * 0.7f))
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

    private fun luma(c: Color): Float = (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue) * 255f

    /** How much of the white content shows at pixel row [y] (0 none … 1 whole), down the column at [xDp]. */
    private fun share(s: Shot, none: Shot, y: Int, xDp: Float = W / 2f): Float {
        val x = (xDp * D).roundToInt()
        val n = luma(none.image[x, y])
        val span = (255f - n).coerceAtLeast(1f)
        return (abs(luma(s.image[x, y]) - n) / span).coerceIn(0f, 1f)
    }

    /** The rows (dp) where the content goes from more than half to nothing within one pixel, either way. */
    private fun cuts(s: Shot, none: Shot, fromDp: Float, toDp: Float, downOnly: Boolean): List<Float> {
        val ys = ((fromDp * D).roundToInt() until (toDp * D).roundToInt() - 1).filter { it >= 0 && it + 1 < s.image.height }
        return ys.filter { y ->
            val a = share(s, none, y)
            val b = share(s, none, y + 1)
            (a > HALF && b < GONE) || (!downOnly && a < GONE && b > HALF)
        }.map { it / D }
    }

    private fun wholeRows(s: Shot, none: Shot, rect: DpRect): Float {
        val ys = ((rect.top.value + .5f) * D).roundToInt() until ((rect.bottom.value - .5f) * D).roundToInt()
        return ys.minOf { share(s, none, it) }
    }

    /** Where the chrome ends: the bar's bottom, or the floating pane's (4 dp into the bar) under Glass. */
    private fun chromeBottom(s: Shot, variant: Variant, style: Style): Float =
        if (style == Style.Glass && (variant == Variant.Floating || variant == Variant.PinnedFoot)) s.bar.bottom.value - 4f else s.bar.bottom.value

    @Test
    fun `PAGE-EDGES-01 content never ends in a hard line at the chrome or a footer, at rest or scrolled`() {
        val failures = mutableListOf<String>()
        for (variant in Variant.entries) for (style in styles) {
            val bright = shots(variant, style, ROWS, paint = true)
            val none = shots(variant, style, ROWS, paint = false)
            for (at in At.entries) {
                val s = bright.getValue(at)
                val n = none.getValue(at)
                // Scrolled, the content may neither start nor end in a cut anywhere; at rest it starts right below the
                // chrome (its first row's own top edge), so only its end is checked there.
                val fromDp = chromeBottom(s, variant, style) - 2f
                val found = cuts(s, n, fromDp, H.toFloat(), downOnly = at == At.Rest)
                // A body that reports nothing keeps its end as it was: only its top edge is the frame's to guess.
                val shown = if (variant == Variant.Unreported) found.filter { it < H / 2f } else found
                if (shown.isNotEmpty()) failures += "$variant, $style, $at: a hard line at ${shown.take(4)} dp (chrome ends ${chromeBottom(s, variant, style)}, foot ${s.foot})"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `PAGE-EDGES-02 scrolled, nothing of the content lies under the status bar or the bar`() {
        val failures = mutableListOf<String>()
        for (variant in Variant.entries) for (style in styles) {
            val s = shots(variant, style, ROWS, paint = true).getValue(At.Middle)
            val n = shots(variant, style, ROWS, paint = false).getValue(At.Middle)
            val bottom = chromeBottom(s, variant, style)
            var worst = 0f
            for (y in 0 until (bottom * D).roundToInt()) for (xDp in listOf(4f, W / 2f, W - 4f)) worst = maxOf(worst, share(s, n, y, xDp))
            if (worst > GONE) failures += "$variant, $style: content shows ${"%.2f".format(worst)} under the chrome ending at $bottom dp"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `PAGE-EDGES-03 at rest the first and last rows are whole, and at the end the last row is whole`() {
        val failures = mutableListOf<String>()
        for (variant in Variant.entries) for (style in styles) {
            // A page that fits: both its rows whole at rest.
            val short = shots(variant, style, SHORT_ROWS, paint = true).getValue(At.Rest)
            val shortNone = shots(variant, style, SHORT_ROWS, paint = false).getValue(At.Rest)
            val firstShort = wholeRows(short, shortNone, short.first!!)
            val lastShort = wholeRows(short, shortNone, short.last!!)
            if (firstShort < WHOLE || lastShort < WHOLE) failures += "$variant, $style: a page that fits is faded at rest (first $firstShort, last $lastShort)"
            // A long page: its first row whole at rest, its last whole at its end.
            if (variant == Variant.Unreported) continue
            val long = shots(variant, style, ROWS, paint = true)
            val longNone = shots(variant, style, ROWS, paint = false)
            val first = wholeRows(long.getValue(At.Rest), longNone.getValue(At.Rest), long.getValue(At.Rest).first!!)
            val end = long.getValue(At.End)
            val last = wholeRows(end, longNone.getValue(At.End), end.last!!)
            if (first < WHOLE) failures += "$variant, $style: the first row is faded at rest ($first)"
            if (last < WHOLE) failures += "$variant, $style: the last row is faded at the end ($last)"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private companion object {
        const val W = 360
        const val H = 640
        const val D = 2f
        const val STATUS_DP = 24
        const val NAV_DP = 16
        const val BAR_DP = 56
        const val HEADING_DP = 40
        const val FOOT_DP = 56
        const val ROW_DP = 48
        const val ROWS = 40
        const val SHORT_ROWS = 3
        const val MIDDLE_DP = 300f
        const val STEPS = 20

        const val HALF = .5f
        const val GONE = .04f
        const val WHOLE = .97f
    }
}
