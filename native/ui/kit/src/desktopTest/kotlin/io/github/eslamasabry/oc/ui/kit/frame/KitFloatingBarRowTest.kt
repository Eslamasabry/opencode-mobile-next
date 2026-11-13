@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.FilterChipsRules
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec
import io.github.eslamasabry.oc.ui.kit.effects.LIQUID_BAR_RADIUS
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The floating pane's second row after the owner's first phone look (2026-11-11): five chips beside the status pill at
 * 360 dp. The sideways line clips at the pane's side, rests its chips on the gutter inside it and fades over
 * [FloatingPaneRow.FADE_DP] at an end with more past it, never at an end with nothing more; no chip is drawn whole across
 * the pane's rounded corner; and the pane keeps as much room under the row as above the title row.
 */
class KitFloatingBarRowTest {
    /** Desktop with the lens: the pane draws what it reads blurred under its tint (KitFloatingBarTest's stand-in). */
    private class LensPlatform(private val base: FakeKitPlatform = FakeKitPlatform(frost = true, liquidGlass = true)) :
        KitPlatform by base {
        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
            val at = arrayOfNulls<LayoutCoordinates>(1)
            return Modifier.onGloballyPositioned { at[0] = it }
                .clip(shape)
                .drawWithCache {
                    val layer = obtainGraphicsLayer()
                    onDrawBehind {
                        val c = at[0] ?: return@onDrawBehind
                        layer.renderEffect = BlurEffect(8f, 8f)
                        layer.record { backdrop.draw(this, c) }
                        drawLayer(layer)
                        drawRect(spec.fill)
                    }
                }
        }
    }

    @Composable
    private fun Scene(chipsShown: Boolean) {
        CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = 24.dp, bottom = 16.dp)) {
            Box(Modifier.size(W.dp, H.dp).testTag("scene")) {
                val source = rememberKitFrostState()
                val chip = if (chipsShown) Modifier else Modifier.alpha(0f)
                KitScreen(
                    page = true,
                    topBar = {
                        KitPageHeading("Conversations", Modifier.padding(top = Space.S2.dp), actions = {
                            KitIconButton(KitIcons.search, "Search", {})
                            KitIconButton(KitIcons.settings, "Settings", {})
                        })
                    },
                    floatingBar =
                        KitFloatingBar(scrollsElsewhere = true, source = source, below = {
                            // The home's row (ChatsScreen HeaderRow): the pill on the gutter, a divider, the chips.
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp).testTag("row"), verticalAlignment = Alignment.CenterVertically) {
                                KitStatusPill("Phone agents", KitConnectionTone.Ok, "Phone agents, connected", {})
                                Spacer(Modifier.width(Space.S2.dp))
                                Box(Modifier.height(24.dp)) { KitDivider(vertical = true) }
                                Spacer(Modifier.width(Space.S2.dp))
                                Box(Modifier.weight(1f)) {
                                    KitFilterChips(bleed = true) {
                                        CHIPS.forEachIndexed { i, label ->
                                            KitChip(label, chip.testTag("chip-$i"), kind = KitChipKind.Action, opensMenu = i < 3, onClick = {})
                                        }
                                    }
                                }
                            }
                        }),
                    edges = KitScreenEdges.Bleed,
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
    }

    private class Shot(val image: PixelMap, val bounds: Map<String, DpRect>)

    private fun shot(chipsShown: Boolean, toEnd: Boolean): Shot {
        lateinit var shot: Shot
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D, 1f)) {
                    KitTheme(
                        appearance = KitAppearance(style = Style.Glass, mode = AppearanceMode.Dark, glassLook = GlassLookId.Glossy, motion = MotionLevel.Full),
                        platform = LensPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(W.toFloat(), H.toFloat())) { Scene(chipsShown) }
                    }
                }
            }
            waitForIdle()
            if (toEnd) scrollChips()
            waitForIdle()
            val tags = listOf("kit-liquid-bar", "kit-filter-chips", "row") + CHIPS.indices.map { "chip-$it" }
            val bounds = tags.associateWith { onNodeWithTag(it, useUnmergedTree = true).getBoundsInRoot() } +
                ("search" to onNodeWithContentDescription("Search", useUnmergedTree = true).getBoundsInRoot())
            shot = Shot(onNodeWithTag("scene").captureToImage().toPixelMap(), bounds)
        }
        return shot
    }

    /** Scrolls the chips' line to its end, as far as it goes. */
    private fun ComposeUiTest.scrollChips() {
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(10_000f, 0f) }
    }

    /** How much a band of columns [fromDp, toDp) across the chips' faces differs between two shots (summed channel steps). */
    private fun diff(a: Shot, b: Shot, fromDp: Float, toDp: Float, faceTop: Float, faceBottom: Float): Float {
        var sum = 0f
        for (x in (fromDp * D).roundToInt() until (toDp * D).roundToInt()) for (y in (faceTop * D).roundToInt() until (faceBottom * D).roundToInt()) {
            val p = a.image[x, y]
            val q = b.image[x, y]
            sum += abs(p.red - q.red) + abs(p.green - q.green) + abs(p.blue - q.blue)
        }
        return sum
    }

    /** Whether ([x], [y]) lies inside the pane: its rounded rectangle of [LIQUID_BAR_RADIUS]. */
    private fun insidePane(pane: DpRect, x: Float, y: Float): Boolean {
        val r = LIQUID_BAR_RADIUS.toFloat()
        val l = pane.left.value
        val t = pane.top.value
        val rt = pane.right.value
        val b = pane.bottom.value
        if (x < l || x > rt || y < t || y > b) return false
        val cx = x.coerceIn(l + r, rt - r)
        val cy = y.coerceIn(t + r, b - r)
        val dx = x - cx
        val dy = y - cy
        return sqrt(dx * dx + dy * dy) <= r + .01f
    }

    @Test
    fun `B-KIT-FLOAT-020 five chips on the floating pane fade inside its side and are never cut by its corner`() {
        val rest = shot(chipsShown = true, toEnd = false)
        val restBare = shot(chipsShown = false, toEnd = false)
        val end = shot(chipsShown = true, toEnd = true)
        val endBare = shot(chipsShown = false, toEnd = true)
        val pane = rest.bounds["kit-liquid-bar"]!!
        val line = rest.bounds["kit-filter-chips"]!!
        val fade = FloatingPaneRow.FADE_DP.toFloat()
        val inset = FloatingPaneRow.contentInsetDp(16).toFloat()
        // The line clips at the pane's side: nothing of it draws beside the pane.
        assertTrue(abs(line.right.value - pane.right.value) <= .5f, "the line $line ends at the pane's side $pane")
        // The chips' faces: their 48 dp targets less the face's room above and below (32 dp faces).
        val face0 = rest.bounds["chip-0"]!!
        val faceTop = face0.top.value + 8f
        val faceBottom = face0.bottom.value - 8f
        // At rest more lies past the end. Review 2026-11-13 #18: the last chip shown is whole, or a chip runs under the
        // end edge with at least MIN_PEEK_DP of it showing (never a 1-3 letter stump), its outer third there cleared.
        // (A node's bounds are clipped to the line, so a chip running past its end ends on it.)
        val cut = CHIPS.indices.map { rest.bounds["chip-$it"]!! }.firstOrNull { it.left.value < line.right.value - 1f && it.right.value >= line.right.value - .5f }
        if (cut != null)
            assertTrue(cut.width.value >= FilterChipsRules.MIN_PEEK_DP - 1f, "a chip cut at rest shows enough to name it: $cut in $line")
        val endCleared = diff(rest, restBare, line.right.value - fade / 3f + .5f, line.right.value - .5f, faceTop, faceBottom)
        assertTrue(endCleared < 1f, "at rest the end that has more to show is faded out at its edge: $endCleared")
        val endFading = diff(rest, restBare, line.right.value - fade + 1f, line.right.value - fade / 3f, faceTop, faceBottom)
        if (cut != null) assertTrue(endFading > 0f, "at rest the chip at the end thins out inside the pane rather than vanishing early: $endFading")
        // Nothing more before the start: no fade there, the first chip shows whole from the line's start.
        val startShown = diff(rest, restBare, line.left.value, line.left.value + fade / 3f, faceTop, faceBottom)
        assertTrue(startShown > 0f, "at rest the start has nothing more to show and is not faded: $startShown")
        assertTrue(abs(face0.left.value - line.left.value) <= .5f, "at rest the first chip starts the line: $face0 in $line")
        // Scrolled to the end: the last chip rests the gutter in from the bar's side (the content inset), the start fades
        // and the end does not.
        val last = end.bounds["chip-${CHIPS.size - 1}"]!!
        val endLine = end.bounds["kit-filter-chips"]!!
        assertTrue(abs(endLine.right.value - last.right.value - inset) <= .5f, "scrolled to the end the last chip $last rests $inset dp inside the line $endLine")
        val startCleared = diff(end, endBare, endLine.left.value + .5f, endLine.left.value + fade / 3f - .5f, faceTop, faceBottom)
        assertTrue(startCleared < 1f, "scrolled, the start that has more to show is faded out at its edge: $startCleared")
        // The last chip's end against the end of the chip before it (the same kind, mid-line, never faded): a chip's
        // rim is lit from its top start, so only like edges compare.
        val before = end.bounds["chip-${CHIPS.size - 2}"]!!
        val lastRight = diff(end, endBare, last.right.value - 6f, last.right.value, faceTop, faceBottom)
        val lastLeft = diff(end, endBare, before.right.value - 6f, before.right.value, faceTop, faceBottom)
        assertTrue(lastRight >= lastLeft * .75f, "scrolled to the end nothing fades the last chip's end: $lastRight against the chip before it $lastLeft")
        // No chip face is drawn un-faded across the pane's outline: every face point left whole (inside the line, before
        // the fade's outer third) lies inside the pane's rounded rectangle.
        for (s in listOf(rest, end)) {
            val l = s.bounds["kit-filter-chips"]!!
            for (i in CHIPS.indices) {
                val c = s.bounds["chip-$i"]!!
                val from = maxOf(c.left.value, l.left.value)
                val to = minOf(c.right.value, l.right.value - fade / 3f)
                if (from >= to) continue
                for (x in listOf(from, to)) for (y in listOf(c.top.value + 8f, c.bottom.value - 8f))
                    assertTrue(insidePane(pane, x, y), "chip $i's face point ($x, $y) lies inside the pane $pane")
            }
        }
        // As much room under the row inside the pane as above the title row (the 48 dp Search target and the 48 dp row).
        val above = rest.bounds["search"]!!.top.value - pane.top.value
        val below = pane.bottom.value - rest.bounds["row"]!!.bottom.value
        assertTrue(abs(above - below) <= .5f, "the pane keeps $below dp under the row and $above dp above the title row")
        println("floating row: pane $pane, line $line, room above $above below $below, end cleared $endCleared fading $endFading, start $startShown; scrolled: last $last, start cleared $startCleared, last end $lastRight start $lastLeft")
    }

    private companion object {
        const val W = 360
        const val H = 640
        const val D = 2f
        val CHIPS = listOf("All projects", "All connections", "All sources", "Running", "Needs you")
    }
}
